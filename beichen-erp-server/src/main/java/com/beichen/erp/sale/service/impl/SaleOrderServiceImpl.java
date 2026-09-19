package com.beichen.erp.sale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.common.SubjectType;
import com.beichen.erp.finance.entity.FinanceAccount;
import com.beichen.erp.finance.entity.FinanceReceipt;
import com.beichen.erp.finance.entity.FinanceReceiptItem;
import com.beichen.erp.finance.mapper.FinanceAccountMapper;
import com.beichen.erp.finance.service.FinanceReceiptService;
import com.beichen.erp.finance.service.ReceivableHelper;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import com.beichen.erp.sale.common.SettleType;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.sale.entity.SaleOrder;
import com.beichen.erp.sale.entity.SaleOrderItem;
import com.beichen.erp.sale.mapper.SaleOrderMapper;
import com.beichen.erp.sale.mapper.SaleOrderItemMapper;
import com.beichen.erp.sale.service.SaleOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleOrderServiceImpl implements SaleOrderService {

    private final SaleOrderMapper orderMapper;
    private final SaleOrderItemMapper itemMapper;
    private final CustomerMapper customerMapper;
    private final FinanceReceivableMapper receivableMapper;
    private final ReceivableHelper receivableHelper;
    private final WarehouseStockMapper stockMapper;
    private final ProductMapper productMapper;
    /** 2026-09-17（D2）：销售单审核即出库（扣库存），统一走库存服务写流水 */
    private final WarehouseStockService stockService;
    /**
     * 2026-09-18：现金结算（settle_type=CASH）→ 审核销售单时自动生成一张**草稿**收款单；
     * 真正收款仍由「审核收款单」完成 —— 核销/资金流水/账户余额一律走 finance 既有逻辑，账务口径不变。
     */
    private final FinanceReceiptService receiptService;
    /** 结算账户校验/回显用 */
    private final FinanceAccountMapper accountMapper;

    @Override
    public Page<Map<String, Object>> page(String status, Long customerId, String code,
                                          String startDate, String endDate, int pageNum, int pageSize) {
        LambdaQueryWrapper<SaleOrder> w = new LambdaQueryWrapper<SaleOrder>()
                .eq(status != null && !status.isBlank(), SaleOrder::getStatus, status)
                .eq(customerId != null, SaleOrder::getCustomerId, customerId)
                .like(code != null && !code.isBlank(), SaleOrder::getCode, code)
                // 单据日期区间过滤（与报损接口的 startDate/endDate 同写法；order_date 为 DATE，与 yyyy-MM-dd 串比较）
                .ge(startDate != null && !startDate.isBlank(), SaleOrder::getOrderDate, startDate)
                .le(endDate != null && !endDate.isBlank(), SaleOrder::getOrderDate, endDate)
                .orderByDesc(SaleOrder::getId);
        Page<SaleOrder> raw = orderMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 批量查询客户名称，消除 N+1
        List<Long> customerIds = raw.getRecords().stream().map(SaleOrder::getCustomerId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Customer> customerMap = customerIds.isEmpty() ? Collections.emptyMap()
                : customerMapper.selectBatchIds(customerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, c -> c, (a, b) -> a));
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("customerId", o.getCustomerId());
            m.put("warehouseId", o.getWarehouseId());
            m.put("orderDate", o.getOrderDate());
            m.put("status", o.getStatus());
            m.put("taxIncluded", o.getTaxIncluded());
            m.put("taxRate", o.getTaxRate());
            m.put("taxAmount", o.getTaxAmount());
            m.put("totalAmount", o.getTotalAmount());
            // 结算方式（2026-09-18 按单记）：列表「结算方式」列 = 账期 / 现金
            m.put("settleType", SettleType.normalize(o.getSettleType()).getCode());
            m.put("settleAccountId", o.getSettleAccountId());
            m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            Customer c = o.getCustomerId() != null ? customerMap.get(o.getCustomerId()) : null;
            m.put("customerName", c != null ? c.getName() : "");
            return m;
        }).toList());
        return res;
    }

    /**
     * 某客户**已审核**的销售单（供售后退货 / 换货关联选择）。
     * <p>口径与原 {@code SaleReturnServiceImpl.saleOrders} 完全一致（只回 id/code/orderDate/totalAmount），
     * 2026-09-19 期 3 上移到销售单模块后由退货页、换货页共用。</p>
     */
    @Override
    public List<Map<String, Object>> auditedOrdersOfCustomer(Long customerId) {
        if (customerId == null) return List.of();
        List<SaleOrder> list = orderMapper.selectList(new LambdaQueryWrapper<SaleOrder>()
                .eq(SaleOrder::getCustomerId, customerId)
                .eq(SaleOrder::getStatus, DocStatus.AUDITED.getCode())
                .orderByDesc(SaleOrder::getId));
        List<Map<String, Object>> res = new ArrayList<>();
        for (SaleOrder o : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("orderDate", o.getOrderDate());
            m.put("totalAmount", o.getTotalAmount());
            res.add(m);
        }
        return res;
    }

    @Override
    public SaleOrder getById(Long id) {
        SaleOrder o = orderMapper.selectById(id);
        // 结算账户名实时回显（详情页「结算方式 / 收款账户」）
        if (o != null && o.getSettleAccountId() != null) {
            FinanceAccount acc = accountMapper.selectById(o.getSettleAccountId());
            if (acc != null) o.setSettleAccountName(acc.getAccountName());
        }
        return o;
    }

    /**
     * 明细查询：批量关联 product 回填名称/规格/单位。
     * <p>sale_order_item 只存 product_id（不冗余名称），若直接返回原始行，前端需自行翻译；
     * 一旦前端字典未就绪就会显示空白。此处统一在后端回填，前端可直接展示。</p>
     */
    @Override
    public List<SaleOrderItem> getItems(Long orderId) {
        List<SaleOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleOrderItem>().eq(SaleOrderItem::getOrderId, orderId));
        if (items.isEmpty()) return items;
        fillProductInfo(items);
        return items;
    }

    /** 批量回填产品名称/规格/单位，消除 N+1 */
    private void fillProductInfo(List<SaleOrderItem> items) {
        List<Long> productIds = items.stream().map(SaleOrderItem::getProductId)
                .filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) return;
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        for (SaleOrderItem it : items) {
            Product p = it.getProductId() != null ? productMap.get(it.getProductId()) : null;
            it.setProductName(p != null && p.getName() != null ? p.getName() : "");
            it.setSku(p != null && p.getSku() != null ? p.getSku() : "");
            it.setUnit(p != null && p.getUnit() != null ? p.getUnit() : "");
        }
    }

    /** 税额拆分（单价含税口径）：打开含税时从含税总额中按税率拆出税额 = total × rate/(100+rate) */
    /**
     * 结算方式归一 + 校验（2026-09-18，**按单记**：同一客户有时现金、有时账期）。
     * <ul>
     *   <li>空值/非法 → CREDIT 账期（历史单据与老前端提交的默认行为完全不变）；</li>
     *   <li>CASH 现金 → 必须给出**可用**的结算账户（存在且未停用）；非现金一律清空账户。</li>
     * </ul>
     */
    private void normalizeSettle(SaleOrder order) {
        SettleType st = SettleType.normalize(order.getSettleType());
        order.setSettleType(st.getCode());
        if (st != SettleType.CASH) {
            order.setSettleAccountId(null);
            return;
        }
        if (order.getSettleAccountId() == null)
            throw new BusinessException("结算方式为「现金」时必须选择收款账户");
        FinanceAccount acc = accountMapper.selectById(order.getSettleAccountId());
        if (acc == null) throw new BusinessException("收款账户不存在：ID=" + order.getSettleAccountId());
        if (acc.getStatus() != null && acc.getStatus() == 0)
            throw new BusinessException("收款账户已停用，请重新选择：" + acc.getAccountName());
    }

    /**
     * 现金结算：生成收款单**并立即审核**（2026-09-18 用户口径：**现金 = 立刻到账、即结算**）。
     * <p>审核销售单时一步到位：核销本单应收 + 写资金流水 + 更新账户余额（全部复用 finance 既有逻辑，
     * 账务口径不变）；收款单落库即为「已审核」，备注标注为系统自动收款。</p>
     * <p>幂等：同一销售单已有**未作废**的收款单（草稿或已审核）时不再生成 —— 反审核会把自动收款单
     * 冲正并作废，重新审核才会再生成一张，不会重复挂账。</p>
     */
    private void createCashReceipt(SaleOrder order, FinanceReceivable fr) {
        boolean existValid = receiptService.findBySource(SourceBillType.SALE_ORDER.getCode(), order.getId()).stream()
                .anyMatch(r -> !DocStatus.CANCELLED.getCode().equals(r.getStatus()));
        if (existValid) return;
        FinanceReceipt r = new FinanceReceipt();
        r.setSubjectType(SubjectType.CUSTOMER.getCode());
        r.setCustomerId(order.getCustomerId());
        r.setAccountId(order.getSettleAccountId());
        r.setReceiptDate(order.getOrderDate() != null ? order.getOrderDate() : LocalDate.now());
        r.setSourceBillType(SourceBillType.SALE_ORDER.getCode());
        r.setSourceBillNo(order.getCode());
        r.setSourceId(order.getId());
        r.setRemark("现金结算·系统自动收款（销售单 " + order.getCode() + "）");
        FinanceReceiptItem it = new FinanceReceiptItem();
        it.setReceivableId(fr.getId());
        it.setReceivableBillNo(order.getCode());
        it.setThisAmount(order.getTotalAmount());
        receiptService.create(r, List.of(it));
        // 立刻到账：紧接着审核该收款单（核销应收 + 资金流水 + 账户余额）。create 未回填 id 时按来源反查兜底。
        Long rid = r.getId();
        if (rid == null) {
            rid = receiptService.findBySource(SourceBillType.SALE_ORDER.getCode(), order.getId()).stream()
                    .filter(x -> !DocStatus.CANCELLED.getCode().equals(x.getStatus()))
                    .map(FinanceReceipt::getId).findFirst().orElse(null);
        }
        if (rid == null) throw new BusinessException("现金结算收款单生成失败（销售单 " + order.getCode() + "）");
        receiptService.audit(rid);
    }

    private BigDecimal calcTaxAmount(BigDecimal total, Integer taxIncluded, BigDecimal taxRate) {
        if (!Integer.valueOf(1).equals(taxIncluded) || taxRate == null || taxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = taxRate.divide(new BigDecimal("100"), 6, BigDecimal.ROUND_HALF_UP);
        return total.multiply(rate).divide(BigDecimal.ONE.add(rate), 2, BigDecimal.ROUND_HALF_UP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(SaleOrder order, List<SaleOrderItem> items) {
        if (order.getCustomerId() == null) throw new BusinessException("客户不能为空");
        normalizeSettle(order);
        order.setCode(generateCode());
        order.setStatus(DocStatus.DRAFT.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        BigDecimal total = BigDecimal.ZERO;
        orderMapper.insert(order);
        for (SaleOrderItem it : items) {
            it.setId(null);
            it.setOrderId(order.getId());
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal p = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(q.multiply(p));
            total = total.add(it.getAmount());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        SaleOrder u = new SaleOrder();
        u.setId(order.getId());
        u.setTotalAmount(total);
        u.setTaxAmount(calcTaxAmount(total, order.getTaxIncluded(), order.getTaxRate()));
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SaleOrder order, List<SaleOrderItem> items) {
        SaleOrder old = orderMapper.selectById(order.getId());
        if (old == null) throw new BusinessException("销售单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        normalizeSettle(order);
        order.setCode(old.getCode());
        orderMapper.updateById(order);
        // 结算方式改回账期时要清掉旧账户（updateById 忽略 null，必须显式 set）
        if (!SettleType.isCash(order.getSettleType())) {
            orderMapper.update(null, new LambdaUpdateWrapper<SaleOrder>()
                    .eq(SaleOrder::getId, order.getId())
                    .set(SaleOrder::getSettleAccountId, null));
        }
        itemMapper.delete(new LambdaQueryWrapper<SaleOrderItem>().eq(SaleOrderItem::getOrderId, order.getId()));
        BigDecimal total = BigDecimal.ZERO;
        Long cid = CompanyContext.get();
        for (SaleOrderItem it : items) {
            it.setId(null);
            it.setOrderId(order.getId());
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal p = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(q.multiply(p));
            total = total.add(it.getAmount());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        SaleOrder u = new SaleOrder();
        u.setId(order.getId());
        u.setTotalAmount(total);
        u.setTaxAmount(calcTaxAmount(total, order.getTaxIncluded(), order.getTaxRate()));
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        SaleOrder old = orderMapper.selectById(id);
        if (old == null) throw new BusinessException("销售单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败
        if (!DocStatusGuard.claim(orderMapper, SaleOrder::getId, id, SaleOrder::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废");
        SaleOrder u = new SaleOrder();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        SaleOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("销售单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免应收台账重复生成
        if (!DocStatusGuard.claim(orderMapper, SaleOrder::getId, id, SaleOrder::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        List<SaleOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleOrderItem>().eq(SaleOrderItem::getOrderId, id));
        if (items.isEmpty()) throw new BusinessException("订单明细不能为空");
        // P2-33：数量必须为正（负数量会生成负向应收，边界矩阵实测可审核通过）
        for (SaleOrderItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("销售数量必须大于 0（明细行ID=" + it.getId() + "）");
        }
        // P2-33：客户与产品必须存在（原先会生成"客户不存在/空名"的应收台账，产品不存在则静默通过）
        if (order.getCustomerId() == null || customerMapper.selectById(order.getCustomerId()) == null)
            throw new BusinessException("客户不存在：ID=" + order.getCustomerId());
        for (SaleOrderItem it : items) {
            if (it.getProductId() != null && productMapper.selectById(it.getProductId()) == null)
                throw new BusinessException("产品不存在：ID=" + it.getProductId());
        }

        // 库存校验：库存不足不允许审核（口径：**销售单审核即出库**，草稿状态不触碰库存）
        // 前端详情页审核前已预校验，此处兜底，避免从列表页等其它入口绕过
        List<Map<String, Object>> shortage = checkStock(order.getWarehouseId(), items).stream()
                .filter(m -> !Boolean.TRUE.equals(m.get("sufficient")))
                .toList();
        if (!shortage.isEmpty()) {
            String detail = shortage.stream().limit(5)
                    .map(m -> String.format("%s（需 %s，库存 %s，缺 %s）",
                            m.get("productName"), m.get("required"), m.get("available"), m.get("shortage")))
                    .collect(Collectors.joining("；"));
            throw new BusinessException("库存不足，无法审核：" + detail
                    + (shortage.size() > 5 ? " 等 " + shortage.size() + " 项" : ""));
        }

        // 1) 出库扣库存（2026-09-17 D2 定稿口径：**销售单审核才出库**，草稿状态不出库）
        //    - 按明细品质逐行扣减，写库存流水（SALE_OUT / 关联单=销售单），不足由 stockService 抛精确异常
        //    - 成本口径不变：利润表成本 B = 净销售数量 × 移动加权成本价，故此处不写成本批次
        for (SaleOrderItem it : items) {
            Product product = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(order.getWarehouseId(),
                    product != null ? product.getName() : "",
                    it.getQuantity().negate(), StockChangeType.SALE_OUT, order.getCode(),
                    RelatedBillType.SALE_ORDER, it.getProductId(), "", order.getId(), it.getQualityType());
        }

        // 2) 生成应收台账
        // 反审核后重新审核时该单号台账已存在（冲销仅置 CANCELLED 并未删除），此处复用并重置，避免 bill_no 唯一键冲突
        FinanceReceivable exist = receivableMapper.selectOne(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getBillNo, order.getCode()));
        FinanceReceivable fr = exist != null ? exist : new FinanceReceivable();
        fr.setBillNo(order.getCode());
        fr.setCustomerId(order.getCustomerId());
        // 应收台账留痕：固化开单时的客户名（实时查一次）
        Customer c = order.getCustomerId() != null ? customerMapper.selectById(order.getCustomerId()) : null;
        fr.setCustomerName(c != null ? c.getName() : "");
        fr.setSourceBillType(SourceBillType.SALE_ORDER.getCode());
        fr.setSourceBillNo(order.getCode());
        fr.setSourceId(order.getId());
        fr.setAmount(order.getTotalAmount());
        fr.setPaidAmount(BigDecimal.ZERO);
        fr.setUnpaidAmount(order.getTotalAmount());
        fr.setDueDate(calcDueDate(order));
        fr.setStatus(SettlementStatus.UNSETTLED.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) fr.setCompanyId(cid);
        if (exist != null) receivableMapper.updateById(fr);
        else receivableMapper.insert(fr);

        // 3) 现金结算（2026-09-18，按单记；同日用户口径升级：**现金 = 立刻到账**）
        //    —— 自动生成收款单**并立即审核**：挂所选收款账户、金额 = 本单应收全额、核销明细指向本单应收台账，
        //    审核即完成核销 + 资金流水 + 账户余额更新（一律走 finance 既有逻辑，账务口径不变）
        if (SettleType.isCash(order.getSettleType())) createCashReceipt(order, fr);

        // 4) 更新订单状态为"已完成"（审核即出库）；记录审核时间供财务分析按月归集收入
        SaleOrder u = new SaleOrder();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditTime(LocalDateTime.now());
        orderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        SaleOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("销售单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免应收台账重复冲销
        if (!DocStatusGuard.claim(orderMapper, SaleOrder::getId, id, SaleOrder::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已审核的销售单可反审核");
        // 1) 回补出库库存（2026-09-17 D2：与审核的扣减严格对称 —— 原路加回，写 SALE_OUT_UN_AUDIT 流水）
        List<SaleOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleOrderItem>().eq(SaleOrderItem::getOrderId, id));
        for (SaleOrderItem it : items) {
            if (it.getProductId() == null || it.getQuantity() == null
                    || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product product = productMapper.selectById(it.getProductId());
            stockService.changeStock(order.getWarehouseId(),
                    product != null ? product.getName() : "",
                    it.getQuantity(), StockChangeType.SALE_OUT_UN_AUDIT, order.getCode(),
                    RelatedBillType.SALE_ORDER, it.getProductId(), "", order.getId(), it.getQualityType());
        }
        // 1.5) 现金结算自动生成的收款单（2026-09-18；现金 = 立刻到账后的反审核口径）：
        //      本销售单的**自动收款单**一律自动冲正 —— 已审核 → 先反审核（冲正资金流水 + 应收回退）
        //      再作废留痕（不物理删除）；草稿（历史数据）→ 直接作废。
        //      只处理 source 指向本销售单的自动单据，人工创建的收款单不受影响；
        //      这样"现金单审核后仍可反审核"（用户明确不接受"不能反审核"）。
        for (FinanceReceipt r : receiptService.findBySource(SourceBillType.SALE_ORDER.getCode(), order.getId())) {
            if (DocStatus.CANCELLED.getCode().equals(r.getStatus())) continue;
            if (DocStatus.AUDITED.getCode().equals(r.getStatus())) receiptService.unAudit(r.getId());
            receiptService.cancel(r.getId());
        }
        // 2) 冲销应收台账（反审核，已收款单据会校验拦截）；台账不存在时跳过（历史单据可能未生成）
        FinanceReceivable exist = receivableMapper.selectOne(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getBillNo, order.getCode()));
        if (exist != null) receivableHelper.reverseReceivable(order.getCode());
        // 3) 订单状态回退为草稿
        // F7-48（2026-09-19）：审核信息必须用 UpdateWrapper **显式置 null** —— updateById 会忽略 null 字段，
        // 反审核后 audit_time 仍残留（实测库中有 2 条草稿带审核时间），而该列注释为"供财务分析按月归集"，
        // 会让"草稿"被当成已审核单据计入归集。与其余 10 处反审核（盘点/报损/采购/销售退单等）口径一致。
        orderMapper.update(null, new LambdaUpdateWrapper<SaleOrder>()
                .eq(SaleOrder::getId, id)
                .set(SaleOrder::getStatus, DocStatus.DRAFT.getCode())
                .set(SaleOrder::getAuditTime, null));
    }

    @Override
    public List<Map<String, Object>> checkStock(Long warehouseId, List<SaleOrderItem> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (warehouseId == null || items == null || items.isEmpty()) return result;

        // 2026-09-17（D2）：按 (产品, 品质) 维度聚合校验 —— 与审核时的逐行扣减口径一致，
        // 避免"汇总够、某品质不够"造成"前端提示可审核、审核却报错"的迷惑体验
        Map<String, BigDecimal> requiredMap = new LinkedHashMap<>();
        Map<String, Long> productMap = new LinkedHashMap<>();
        Map<String, String> qualityMap = new LinkedHashMap<>();
        for (SaleOrderItem it : items) {
            if (it.getProductId() == null || it.getQuantity() == null) continue;
            String qt = it.getQualityType() != null && !it.getQualityType().isBlank()
                    ? it.getQualityType() : ProductQualityType.A.getCode();
            String key = it.getProductId() + "|" + qt;
            requiredMap.merge(key, it.getQuantity(), BigDecimal::add);
            productMap.putIfAbsent(key, it.getProductId());
            qualityMap.putIfAbsent(key, qt);
        }
        for (Map.Entry<String, BigDecimal> en : requiredMap.entrySet()) {
            String key = en.getKey();
            Long pid = productMap.get(key);
            String qt = qualityMap.get(key);
            Product product = productMapper.selectById(pid);
            if (product == null) continue;
            BigDecimal required = en.getValue();
            BigDecimal available = stockMapper.selectList(
                    new LambdaQueryWrapper<WarehouseStock>()
                            .eq(WarehouseStock::getWarehouseId, warehouseId)
                            .eq(WarehouseStock::getProductId, pid)
                            .eq(WarehouseStock::getQualityType, qt))
                    .stream().map(s -> s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal shortage = required.subtract(available);
            Map<String, Object> m = new HashMap<>();
            m.put("productId", pid);
            m.put("productName", product.getName());
            m.put("qualityType", qt);
            m.put("unit", product.getUnit() != null ? product.getUnit() : "");
            m.put("required", required);
            m.put("available", available);
            m.put("shortage", shortage.compareTo(BigDecimal.ZERO) > 0 ? shortage : BigDecimal.ZERO);
            m.put("sufficient", shortage.compareTo(BigDecimal.ZERO) <= 0);
            result.add(m);
        }
        return result;
    }

    /** 按客户账期计算到期日：月数+天数，都为0则当天到期（立即收款） */
    private LocalDate calcDueDate(SaleOrder order) {
        if (order.getOrderDate() == null) return null;
        LocalDate base = order.getOrderDate();
        if (order.getCustomerId() != null) {
            Customer c = customerMapper.selectById(order.getCustomerId());
            int months = c != null && c.getCreditPeriodMonths() != null ? c.getCreditPeriodMonths() : 0;
            int days = c != null && c.getCreditPeriod() != null ? c.getCreditPeriod() : 0;
            return base.plusMonths(months).plusDays(days);
        }
        return base; // 无客户信息默认当天到期
    }

    private String generateCode() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.SALE + d;
        LambdaQueryWrapper<SaleOrder> w = new LambdaQueryWrapper<SaleOrder>()
                .likeRight(SaleOrder::getCode, pat).orderByDesc(SaleOrder::getCode).last("LIMIT 1");
        SaleOrder last = orderMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.SALE + d + String.format("%03d", seq);
    }
}
