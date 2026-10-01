package com.beichen.erp.sale.service.impl;

import com.beichen.erp.config.UserContext;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.BillNoSeq;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.common.SubjectType;
import com.beichen.erp.finance.entity.FinanceAccount;
import com.beichen.erp.finance.entity.FinanceReceipt;
import com.beichen.erp.finance.entity.FinanceReceiptAccount;
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
import com.beichen.erp.sale.entity.SaleOrderSettleAccount;
import com.beichen.erp.sale.mapper.SaleOrderMapper;
import com.beichen.erp.sale.mapper.SaleOrderItemMapper;
import com.beichen.erp.sale.mapper.SaleOrderSettleAccountMapper;
import com.beichen.erp.sale.service.SaleOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    /** 现金结算的**分款明细**（2026-09-30 多账户收款）：落库 sale_order_settle_account，与 finance_receipt_account 同构 */
    private final SaleOrderSettleAccountMapper settleAccountMapper;

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

    /** 现金结算的分款明细查询（详情页回显；与 finance_receipt 的 /{id}/accounts 同构） */
    @Override
    public List<SaleOrderSettleAccount> getSettleAccounts(Long orderId) {
        return settleAccountMapper.selectList(
                new LambdaQueryWrapper<SaleOrderSettleAccount>()
                        .eq(SaleOrderSettleAccount::getOrderId, orderId)
                        .orderByAsc(SaleOrderSettleAccount::getId));
    }

    /** 落库分款明细（2026-09-30）：调用前 normalizeSettle 已完成校验与首行快照；行 id 一律重建 */
    private void saveSettleAccounts(SaleOrder order, Long cid) {
        List<SaleOrderSettleAccount> rows = order.getSettleAccounts();
        if (rows == null || rows.isEmpty()) return;
        for (SaleOrderSettleAccount acc : rows) {
            acc.setId(null);
            acc.setOrderId(order.getId());
            if (cid != null && cid > 0) acc.setCompanyId(cid);
            settleAccountMapper.insert(acc);
        }
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
            order.setSettleAmount(null);
            order.setSettleAccounts(null);
            return;
        }
        // 2026-09-30 多账户收款：有分款行时**以行数据为准**（校验后合计写入 settleAmount、
        // 首行写入 settleAccountId 作为快照）；没有分款行时回退旧的单账户口径（老前端/历史草稿）。
        List<SaleOrderSettleAccount> rows = normalizeSettleRows(order.getSettleAccounts());
        if (rows.isEmpty()) {
            if (order.getSettleAccountId() == null)
                throw new BusinessException("结算方式为「现金」时必须选择收款账户");
            FinanceAccount acc = accountMapper.selectById(order.getSettleAccountId());
            if (acc == null) throw new BusinessException("收款账户不存在：ID=" + order.getSettleAccountId());
            if (acc.getStatus() != null && acc.getStatus() == 0)
                throw new BusinessException("收款账户已停用，请重新选择：" + acc.getAccountName());
            return;
        }
        order.setSettleAccounts(rows);
        BigDecimal splitTotal = BigDecimal.ZERO;
        for (SaleOrderSettleAccount acc : rows) splitTotal = splitTotal.add(acc.getAmount());
        if (order.getSettleAmount() != null && order.getSettleAmount().compareTo(splitTotal) != 0)
            throw new BusinessException("本次收款总额与各账户金额合计不一致（总额 "
                    + order.getSettleAmount().stripTrailingZeros().toPlainString() + "，合计 "
                    + splitTotal.stripTrailingZeros().toPlainString() + "）");
        order.setSettleAmount(splitTotal);
        // 首行快照（与 finance_receipt.account_id 同口径：列表列与旧读法都认它）
        SaleOrderSettleAccount first = rows.get(0);
        order.setSettleAccountId(first.getAccountId());
        order.setSettleAccountName(first.getAccountName());
    }

    /**
     * 分款行校验并归一（2026-09-30）：账户必选、金额 &gt; 0、同账户不许重复、账户存在且未停用；
     * 同时回填账户名快照并清掉前端传来的行 id（交给"先删后插"重建）。口径与收款单 saveAccounts 一致。
     */
    private List<SaleOrderSettleAccount> normalizeSettleRows(List<SaleOrderSettleAccount> input) {
        List<SaleOrderSettleAccount> rows = new ArrayList<>();
        if (input == null) return rows;
        Set<Long> seen = new HashSet<>();
        int rowNo = 0;
        for (SaleOrderSettleAccount acc : input) {
            if (acc == null) continue;
            if (acc.getAccountId() == null && acc.getAmount() == null) continue;   // 空行忽略
            rowNo++;
            if (acc.getAccountId() == null)
                throw new BusinessException("收款账户第 " + rowNo + " 行未选择账户");
            if (!seen.add(acc.getAccountId()))
                throw new BusinessException("同一账户请合并为一行（账户不允许重复）");
            BigDecimal amt = acc.getAmount() != null ? acc.getAmount() : BigDecimal.ZERO;
            if (amt.compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("收款账户第 " + rowNo + " 行金额必须大于 0");
            FinanceAccount fa = accountMapper.selectById(acc.getAccountId());
            if (fa == null) throw new BusinessException("收款账户不存在：ID=" + acc.getAccountId());
            if (fa.getStatus() != null && fa.getStatus() == 0)
                throw new BusinessException("收款账户已停用，请重新选择：" + fa.getAccountName());
            acc.setId(null);
            acc.setAccountName(fa.getAccountName());
            rows.add(acc);
        }
        return rows;
    }

    /**
     * 现金结算：生成收款单**并立即审核**（2026-09-18 用户口径：**现金 = 立刻到账、即结算**）。
     * <p>审核销售单时一步到位：核销本单应收 + 写资金流水 + 更新账户余额（全部复用 finance 既有逻辑，
     * 账务口径不变）；收款单落库即为「已审核」，备注标注为系统自动收款。</p>
     * <p><b>2026-10-01 多账户（第 4 步）</b>：按 {@code sale_order_settle_account} 分款明细**逐账户**生成
     * 收款单分款行（A 800 + B 200 ⇒ 两条资金流水，各自账户余额对得上）；收款单主表金额 = 分款合计。
     * 核销金额取「本次收款总额」（{@code settle_amount}）—— **部分收款时只核销收到的这部分**，
     * 差额由收款单审核按「未核销余额」落预收台账（既有 createUnsettledAdvance 能力，不改口径）。</p>
     * <p><b>历史兼容</b>：没有分款行的老销售单（旧草稿/旧数据）回退旧的单账户口径
     * （首行快照账户 + 核销全额），行为与改造前完全一致。</p>
     * <p>幂等：同一销售单已有**未作废**的收款单（草稿或已审核）时不再生成 —— 反审核会把自动收款单
     * 冲正并作废，重新审核才会再生成一张，不会重复挂账。</p>
     */
    private void createCashReceipt(SaleOrder order, FinanceReceivable fr) {
        boolean existValid = receiptService.findBySource(SourceBillType.SALE_ORDER.getCode(), order.getId()).stream()
                .anyMatch(r -> !DocStatus.CANCELLED.getCode().equals(r.getStatus()));
        if (existValid) return;
        // 分款明细（新口径）= 权威金额来源；为空 ⇒ 历史单，走下面的单账户分支
        List<SaleOrderSettleAccount> split = getSettleAccounts(order.getId());
        BigDecimal orderTotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal received;
        if (split.isEmpty()) {
            received = order.getSettleAmount() != null ? order.getSettleAmount() : orderTotal;
        } else {
            received = BigDecimal.ZERO;
            for (SaleOrderSettleAccount sa : split)
                received = received.add(sa.getAmount() != null ? sa.getAmount() : BigDecimal.ZERO);
        }
        // 兜底护栏（前端已拦、normalizeSettle 已校验合计=总额）：本次收款不得超过应收，否则核销会超出台账、
        // 多出的部分反被当成"多收预收"，与「禁止超收」口径冲突。此处在审核事务内抛错 ⇒ 整单回滚，不会留下半截数据。
        if (received.compareTo(orderTotal) > 0)
            throw new BusinessException("本次收款总额 " + received.stripTrailingZeros().toPlainString()
                    + " 不能超过销售单应收总额 " + orderTotal.stripTrailingZeros().toPlainString());
        FinanceReceipt r = new FinanceReceipt();
        r.setSubjectType(SubjectType.CUSTOMER.getCode());
        r.setCustomerId(order.getCustomerId());
        r.setReceiptDate(order.getOrderDate() != null ? order.getOrderDate() : LocalDate.now());
        r.setSourceBillType(SourceBillType.SALE_ORDER.getCode());
        r.setSourceBillNo(order.getCode());
        r.setSourceId(order.getId());
        r.setRemark(split.size() > 1
                ? "现金结算·系统自动收款（销售单 " + order.getCode() + "，" + split.size() + " 个账户分款）"
                : "现金结算·系统自动收款（销售单 " + order.getCode() + "）");
        List<FinanceReceiptAccount> accounts = new ArrayList<>();
        if (split.isEmpty()) {
            // 历史单兼容：只有单账户快照、没有分款行 ⇒ 单账户、金额 = 本次收款（= 应收，旧行为）
            r.setAccountId(order.getSettleAccountId());
            FinanceReceiptAccount only = new FinanceReceiptAccount();
            only.setAccountId(order.getSettleAccountId());
            only.setAmount(received);
            only.setRemark("单账户（历史销售单回退）");
            accounts.add(only);
        } else {
            // 首行快照交回收款单主表（saveAccounts 会按分款首行回写，这里显式设一次便于 raw 读法）
            r.setAccountId(split.get(0).getAccountId());
            for (SaleOrderSettleAccount sa : split) {
                FinanceReceiptAccount fa = new FinanceReceiptAccount();
                fa.setAccountId(sa.getAccountId());
                fa.setAmount(sa.getAmount());
                fa.setRemark(sa.getRemark());
                accounts.add(fa);
            }
        }
        FinanceReceiptItem it = new FinanceReceiptItem();
        it.setReceivableId(fr.getId());
        it.setReceivableBillNo(order.getCode());
        it.setThisAmount(received);
        // 三参重载：多账户分款 + 核销明细（金额一律由分款推导，收款单主表 amount = 分款合计）
        receiptService.create(r, accounts, List.of(it));
        // 立刻到账：紧接着审核该收款单（核销应收 + **逐账户**资金流水 + 账户余额）。create 未回填 id 时按来源反查兜底。
        Long rid = r.getId();
        if (rid == null) {
            rid = receiptService.findBySource(SourceBillType.SALE_ORDER.getCode(), order.getId()).stream()
                    .filter(x -> !DocStatus.CANCELLED.getCode().equals(x.getStatus()))
                    .map(FinanceReceipt::getId).findFirst().orElse(null);
        }
        if (rid == null) throw new BusinessException("现金结算收款单生成失败（销售单 " + order.getCode() + "）");
        receiptService.audit(rid);
    }

    /**
     * F7-108（2026-09-20）：草稿保存时的明细护栏 —— **与 {@link #audit} 同一口径**。
     *
     * <p>原先只在 audit 校验（明细非空 + 数量 &gt; 0）⇒ 保存阶段可落"空明细 / 负数量"草稿：
     * 前端显示为"正常的草稿"，用户以为已录入，直到审核才被拦（错误暴露得太晚，
     * 且空明细草稿若被清理脚本/报表统计会带来噪音）。此处把校验提前到保存那一步。</p>
     */
    private void assertItemsForDraft(List<SaleOrderItem> items) {
        if (items == null || items.isEmpty()) throw new BusinessException("销售单明细不能为空");
        for (SaleOrderItem it : items) {
            if (it.getProductId() == null) throw new BusinessException("销售明细必须选择产品");
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("销售数量必须大于 0（产品ID=" + it.getProductId() + "）");
            assertItemQualitySellable(it);
        }
    }

    /**
     * 2026-09-21（用户口径）：**销售单明细的品质不能是不良品(DEFECT)和待整理(PENDING)** ——
     * 销售单只能卖 **A规/B规/C规** 良品。
     *
     * <p>为什么必须在服务层拦（前端下拉过滤只是体验，直调接口 / 编辑历史草稿都能绕过）：</p>
     * <ol>
     *   <li><b>库存口径</b>：审核出库按明细品质逐行扣减（{@code changeStock(..., it.getQualityType())}），
     *       卖不良品就是扣不良品库存 —— 与"不良品只应走退货/维修"的业务线冲突，且报表按品质分类会失真；</li>
     *   <li><b>业务语义</b>：待整理(PENDING)是售后退回后**尚未整理**的暂存态（退货整理单才把它归到 A/B/C，
     *       见 {@code AfterSaleSort}），未整理的东西不该再被卖出去；</li>
     *   <li>与 {@link #assertItemsForDraft} 同一口径：保存草稿与审核都拦，避免"能存不能审"的迷惑体验。</li>
     * </ol>
     * <p>空值不拦：空 = 按 A规 处理（与 {@code checkStock} 和审核扣减的默认口径一致）。</p>
     */
    private void assertItemQualitySellable(SaleOrderItem it) {
        String qt = it.getQualityType();
        if (qt == null || qt.isBlank()) return;
        ProductQualityType quality = ProductQualityType.of(qt);
        if (quality == null) throw new BusinessException("非法的品质等级：" + qt);
        if (quality == ProductQualityType.DEFECT || quality == ProductQualityType.PENDING)
            throw new BusinessException("销售明细产品「" + itemProductDesc(it) + "」的品质是「" + quality.getLabel()
                    + "」：销售单只能销售 A规/B规/C规 良品，不能是不良品或待整理");
    }

    /** 明细报错用的产品描述：优先产品名（前端随明细一起传），否则退化为产品ID */
    private String itemProductDesc(SaleOrderItem it) {
        return it.getProductName() != null && !it.getProductName().isBlank()
                ? it.getProductName() : "ID=" + it.getProductId();
    }

    private BigDecimal calcTaxAmount(BigDecimal total, Integer taxIncluded, BigDecimal taxRate) {
        if (!Integer.valueOf(1).equals(taxIncluded) || taxRate == null || taxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        // F7-110（2026-09-20）：改用 RoundingMode（BigDecimal.ROUND_HALF_UP 自 Java 9 起已被废弃）
        BigDecimal rate = taxRate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        return total.multiply(rate).divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(SaleOrder order, List<SaleOrderItem> items) {
        if (order.getCustomerId() == null) throw new BusinessException("客户不能为空");
        assertItemsForDraft(items);
        normalizeSettle(order);
        order.setCode(generateCode());
        order.setStatus(DocStatus.DRAFT.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        BigDecimal total = BigDecimal.ZERO;
        orderMapper.insert(order);
        // 2026-09-30：现金结算的多账户分款行落库（账期单的 settleAccounts 已被 normalizeSettle 清空）
        saveSettleAccounts(order, cid);
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
        assertItemsForDraft(items);
        normalizeSettle(order);
        order.setCode(old.getCode());
        orderMapper.updateById(order);
        // 结算方式改回账期时要清掉旧账户（updateById 忽略 null，必须显式 set）
        if (!SettleType.isCash(order.getSettleType())) {
            orderMapper.update(null, new LambdaUpdateWrapper<SaleOrder>()
                    .eq(SaleOrder::getId, order.getId())
                    .set(SaleOrder::getSettleAccountId, null));
        }
        // ⚠️ F7-108（2026-09-20）：这里是"全删重插"⇒ **明细 id 会全部变化**。
        // 当前安全：只有**草稿**可编辑，而引用 sale_order_item.id 的只有 sale_outbound_item.orderItemId，
        // 出库单又要求挂**已审核**销售单 ⇒ 草稿阶段不可能存在引用。
        // **若将来允许编辑已审核单，或新增任何"按 item.id 引用销售明细"的功能（如批次/序列号追溯），
        // 必须改为按 id 差量更新**，否则那些引用会静默悬空。
        // 2026-09-30：分款明细同样"先删后插"（与明细同一策略；只有草稿可编辑，无外部引用）
        settleAccountMapper.delete(new LambdaQueryWrapper<SaleOrderSettleAccount>()
                .eq(SaleOrderSettleAccount::getOrderId, order.getId()));
        if (SettleType.isCash(order.getSettleType())) saveSettleAccounts(order, CompanyContext.get());
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
            // 2026-09-21（用户口径）：审核再兜一道 —— 品质不能是不良品/待整理（与保存草稿同一口径）
            assertItemQualitySellable(it);
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
            // P4（2026-09-30）：改调主重载（productId 直接作第 2 参），不再为旧签名重载查询产品名。
            stockService.changeStock(order.getWarehouseId(),
                    it.getProductId(),
                    it.getQuantity().negate(), StockChangeType.SALE_OUT, order.getCode(),
                    RelatedBillType.SALE_ORDER, "", order.getId(), it.getQualityType());
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
        // 2026-09-23（用户口径：单据详情显示「制单人 + 审核人」）：补记录审核人（原先只记时间）
        SaleOrder u = new SaleOrder();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditorId(UserContext.getId());
        u.setAuditorName(UserContext.getName());
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
            // P4（2026-09-30）：改调主重载（productId 直接作第 2 参）
            stockService.changeStock(order.getWarehouseId(),
                    it.getProductId(),
                    it.getQuantity(), StockChangeType.SALE_OUT_UN_AUDIT, order.getCode(),
                    RelatedBillType.SALE_ORDER, "", order.getId(), it.getQualityType());
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
        // 会让"草稿"被当成已审核单据计入归集。与其余 10 处反审核（盘点/报损/采购/销售退货单等）口径一致。
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
        // F7-109（2026-09-20）：统一走 BillNoSeq —— 不再 substring(len-3)（序号 ≥1000 会截错），
        // 也不再 `catch → seq = 1` 静默回退（真撞车交给 uk_code 抛可见错误）。
        int seq = BillNoSeq.lastSeq(last == null ? null : last.getCode(), pat) + 1;
        return BillNoSeq.formatUnique(pat, seq, cand -> orderMapper.selectCount(new LambdaQueryWrapper<SaleOrder>().eq(SaleOrder::getCode, cand)) > 0) /* F7-261 冲突检测+重试 */;
    }
}
