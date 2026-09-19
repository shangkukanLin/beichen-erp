package com.beichen.erp.sale.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import com.beichen.erp.finance.service.ReceivableHelper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.warehouse.common.WarehouseType;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.sale.common.AfterSaleSourceType;
import com.beichen.erp.sale.common.ExchangeChargeType;
import com.beichen.erp.sale.entity.AfterSalePending;
import com.beichen.erp.sale.entity.SaleReturn;
import com.beichen.erp.sale.entity.SaleReturnItem;
import com.beichen.erp.sale.entity.SaleOrder;
import com.beichen.erp.sale.entity.SaleOrderItem;
import com.beichen.erp.sale.mapper.AfterSalePendingMapper;
import com.beichen.erp.sale.mapper.SaleOrderItemMapper;
import com.beichen.erp.sale.mapper.SaleOrderMapper;
import com.beichen.erp.sale.mapper.SaleReturnItemMapper;
import com.beichen.erp.sale.mapper.SaleReturnMapper;
import com.beichen.erp.sale.service.SaleReturnService;
import org.springframework.jdbc.core.JdbcTemplate;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 销售退单业务实现
 * <p>客户退回待分类品（品质默认待分类），审核时按 (warehouseId, productId, qualityType) 入库增加库存，并写库存流水，
 * 同时登记售后待整理批次（after_sale_pending），由退货整理单统一消费并分选入成品仓/不良仓。</p>
 */
@Service
@RequiredArgsConstructor
public class SaleReturnServiceImpl implements SaleReturnService {

    private final SaleReturnMapper returnMapper;
    private final SaleReturnItemMapper itemMapper;
    private final ProductMapper productMapper;
    private final CustomerMapper customerMapper;
    private final WarehouseStockService stockService;
    private final WarehouseMapper warehouseMapper;
    private final FinanceReceivableMapper financeReceivableMapper;
    private final ReceivableHelper receivableHelper;
    private final UserMapper userMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final AfterSalePendingMapper afterSalePendingMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public IPage<Map<String, Object>> page(String status, Long customerId, String code, Long saleOrderId, int pageNum, int pageSize) {
        LambdaQueryWrapper<SaleReturn> w = new LambdaQueryWrapper<SaleReturn>()
                .eq(status != null && !status.isBlank(), SaleReturn::getStatus, status)
                .eq(customerId != null, SaleReturn::getCustomerId, customerId)
                .eq(saleOrderId != null, SaleReturn::getSaleOrderId, saleOrderId)
                .like(code != null && !code.isBlank(), SaleReturn::getCode, code)
                .orderByDesc(SaleReturn::getId);
        Page<SaleReturn> raw = returnMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 客户名称是非表字段，按 customerId 批量回填（避免逐条 selectById）
        final Map<Long, String> customerNameMap = new HashMap<>();
        Set<Long> customerIds = raw.getRecords().stream().map(SaleReturn::getCustomerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!customerIds.isEmpty()) {
            customerMapper.selectBatchIds(customerIds)
                    .forEach(c -> customerNameMap.put(c.getId(), c.getName() != null ? c.getName() : ""));
        }
        // 批量查明细与产品
        Map<Long, Product> productMap = new HashMap<>();
        Map<Long, List<SaleReturnItem>> itemsMap = new HashMap<>();
        if (!raw.getRecords().isEmpty()) {
            List<Long> returnIds = raw.getRecords().stream().map(SaleReturn::getId).collect(Collectors.toList());
            List<SaleReturnItem> allItems = itemMapper.selectList(
                    new LambdaQueryWrapper<SaleReturnItem>().in(SaleReturnItem::getReturnId, returnIds));
            Set<Long> productIds = allItems.stream().map(SaleReturnItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!productIds.isEmpty()) {
                productMapper.selectBatchIds(productIds).forEach(p -> productMap.put(p.getId(), p));
            }
            itemsMap = allItems.stream().collect(Collectors.groupingBy(SaleReturnItem::getReturnId));
        }
        Map<Long, Product> finalProductMap = productMap;
        Map<Long, List<SaleReturnItem>> finalItemsMap = itemsMap;
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("customerId", o.getCustomerId());
            m.put("customerName", customerNameMap.getOrDefault(o.getCustomerId(), ""));
            m.put("warehouseId", o.getWarehouseId());
            m.put("saleOrderId", o.getSaleOrderId());
            m.put("saleOrderCode", o.getSaleOrderCode());
            m.put("returnDate", o.getReturnDate());
            m.put("status", o.getStatus());
            m.put("totalAmount", o.getTotalAmount());
            m.put("chargeFlag", o.getChargeFlag());
            m.put("chargeType", o.getChargeType());
            m.put("chargeAmount", o.getChargeAmount());
            m.put("chargeReason", o.getChargeReason());
            m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            List<SaleReturnItem> items = finalItemsMap.getOrDefault(o.getId(), Collections.emptyList());
            String itemsSummary = items.stream()
                    .map(it -> {
                        String name = it.getProductId() != null && finalProductMap.containsKey(it.getProductId())
                                ? finalProductMap.get(it.getProductId()).getName() : "";
                        String qty = it.getQuantity() != null ? it.getQuantity().stripTrailingZeros().toPlainString() : "0";
                        return name + "*" + qty;
                    })
                    .collect(Collectors.joining("，"));
            m.put("itemsSummary", itemsSummary);
            return m;
        }).toList());
        return res;
    }

    @Override
    public SaleReturn getById(Long id) {
        SaleReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("销售退单不存在");
        fillCustomerName(order);
        return order;
    }

    /** 客户名称为非表字段，详情接口按 customerId 回填，供前端直接展示 */
    private void fillCustomerName(SaleReturn order) {
        if (order == null || order.getCustomerId() == null) return;
        Customer c = customerMapper.selectById(order.getCustomerId());
        order.setCustomerName(c != null && c.getName() != null ? c.getName() : "");
    }

    @Override
    public List<SaleReturnItem> getItems(Long returnId) {
        List<SaleReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleReturnItem>().eq(SaleReturnItem::getReturnId, returnId));
        // 统一回填产品名称与 SKU（两者都是非表字段），前端免查库即可展示
        Map<Long, Product> pMap = productMap(items);
        for (SaleReturnItem it : items) {
            Product p = it.getProductId() != null ? pMap.get(it.getProductId()) : null;
            it.setProductName(p != null && p.getName() != null ? p.getName() : "");
            it.setSku(p != null && p.getSku() != null ? p.getSku() : "");
        }
        return items;
    }

    /** 按明细汇总退货总额 = Σ(数量 × 单价)：前端不传总额，后端必须以明细为准，否则总额恒为 0 会导致审核不生成负向应收 */
    private BigDecimal sumAmount(List<Map<String, Object>> itemMaps) {
        BigDecimal sum = BigDecimal.ZERO;
        if (itemMaps == null) return sum;
        for (Map<String, Object> m : itemMaps) {
            sum = sum.add(toBig(m.get("quantity")).multiply(toBig(m.get("unitPrice"))));
        }
        return sum;
    }

    private BigDecimal toBig(Object v) {
        if (v == null) return BigDecimal.ZERO;
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    /** 冲销应收台账：不存在则跳过（历史单据可能未生成）；已有收款仍由 reverseReceivable 内部护栏拦截 */
    private void reverseReceivableIfExists(String billNo) {
        if (billNo == null || billNo.isBlank()) return;
        FinanceReceivable fr = financeReceivableMapper.selectOne(
                new LambdaQueryWrapper<FinanceReceivable>().eq(FinanceReceivable::getBillNo, billNo));
        if (fr == null) return;
        receivableHelper.reverseReceivable(billNo);
    }

    // ==================== 退货收费（与换货单一致：chargeFlag 控制，金额手工填写，审核生成 -FEE 正向应收） ====================

    /** 收费字段归一化：不收费则金额归零、类型清空；收费则类型必须合法且金额必须 > 0 */
    private void normalizeCharge(SaleReturn e) {
        boolean charged = e.getChargeFlag() != null && e.getChargeFlag() == 1;
        if (!charged) {
            e.setChargeFlag(0);
            e.setChargeType(null);
            e.setChargeAmount(BigDecimal.ZERO);
            e.setChargeReason(null);
            return;
        }
        if (e.getChargeType() == null || e.getChargeType().isBlank())
            throw new BusinessException("已选择收费，请选择收费类型");
        if (!ExchangeChargeType.isValid(e.getChargeType()))
            throw new BusinessException("非法的收费类型：" + e.getChargeType());
        if (toBig(e.getChargeAmount()).compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("已选择收费，收费金额必须大于 0");
    }

    /** 审核时生成退货收费应收：单号 -FEE 后缀，与退单本体的负向冲抵区分，便于反审核精确冲销 */
    private void saveChargeReceivable(SaleReturn e) {
        boolean charged = e.getChargeFlag() != null && e.getChargeFlag() == 1;
        if (!charged || toBig(e.getChargeAmount()).compareTo(BigDecimal.ZERO) <= 0) return;
        FinanceReceivable fr = new FinanceReceivable();
        fr.setBillNo(e.getCode() + "-FEE");
        fr.setCustomerId(e.getCustomerId());
        Customer c = e.getCustomerId() != null ? customerMapper.selectById(e.getCustomerId()) : null;
        fr.setCustomerName(c != null ? c.getName() : "");
        fr.setSourceBillType(SourceBillType.SALE_RETURN_CHARGE.getCode());
        fr.setSourceBillNo(e.getCode());
        fr.setSourceId(e.getId());
        fr.setAmount(e.getChargeAmount());
        fr.setPaidAmount(BigDecimal.ZERO);
        fr.setUnpaidAmount(e.getChargeAmount());
        fr.setDueDate(e.getReturnDate());
        fr.setStatus(SettlementStatus.UNSETTLED.getCode());
        fr.setRemark("销售退货收费"
                + (e.getChargeReason() != null && !e.getChargeReason().isBlank() ? "：" + e.getChargeReason() : ""));
        saveReceivable(fr);
    }

    /** 批量取产品映射，避免循环内 selectById（N+1） */
    private Map<Long, Product> productMap(List<SaleReturnItem> items) {
        Set<Long> pids = items.stream().map(SaleReturnItem::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Product> map = new HashMap<>();
        if (!pids.isEmpty()) productMapper.selectBatchIds(pids).forEach(p -> map.put(p.getId(), p));
        return map;
    }

    // ==================== 售后待整理批次（统一追溯池） ====================

    /**
     * 登记售后待整理批次：退单审核后退回的待分类品进入统一待整理池，供退货整理单消费。
     * <p>先按 (source_type, source_item_id) 清掉残留再插入，保证「反审核 → 重新审核」不重复登记。</p>
     */
    private void createPendingBatches(SaleReturn order, List<SaleReturnItem> items, Map<Long, Product> pMap) {
        Long cid = CompanyContext.get();
        for (SaleReturnItem it : items) {
            if (it.getId() == null) continue;
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product p = pMap.get(it.getProductId());
            afterSalePendingMapper.delete(new LambdaQueryWrapper<AfterSalePending>()
                    .eq(AfterSalePending::getSourceType, AfterSaleSourceType.SALE_RETURN.getCode())
                    .eq(AfterSalePending::getSourceItemId, it.getId()));
            AfterSalePending pending = new AfterSalePending();
            pending.setSourceType(AfterSaleSourceType.SALE_RETURN.getCode());
            pending.setSourceId(order.getId());
            pending.setSourceItemId(it.getId());
            pending.setSourceCode(order.getCode());
            pending.setSourceDate(order.getReturnDate());
            pending.setWarehouseId(order.getWarehouseId());
            pending.setCustomerId(order.getCustomerId());
            pending.setProductId(it.getProductId());
            pending.setProductName(it.getProductName() != null && !it.getProductName().isBlank()
                    ? it.getProductName() : (p != null ? p.getName() : ""));
            pending.setUnit(p != null ? p.getUnit() : "");
            pending.setQuantity(it.getQuantity());
            pending.setSortedQuantity(BigDecimal.ZERO);
            pending.setUnitPrice(it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO);
            if (cid != null && cid > 0) pending.setCompanyId(cid);
            afterSalePendingMapper.insert(pending);
        }
    }

    /** 撤销待整理批次：护栏已确保未被整理，可安全删除 */
    private void deletePendingBatches(AfterSaleSourceType type, Long sourceId) {
        afterSalePendingMapper.delete(new LambdaQueryWrapper<AfterSalePending>()
                .eq(AfterSalePending::getSourceType, type.getCode())
                .eq(AfterSalePending::getSourceId, sourceId));
    }

    /**
     * 反审核护栏：来源单据已有货物被退货整理（待整理批次已产生已整理数量）则禁止反审核，
     * 否则售后仓库存已被整理单转走，反审核将扣不动或造成跨单据不一致。
     */
    private void assertNotSorted(AfterSaleSourceType type, Long sourceId, String billLabel) {
        List<AfterSalePending> batches = afterSalePendingMapper.selectList(new LambdaQueryWrapper<AfterSalePending>()
                .eq(AfterSalePending::getSourceType, type.getCode())
                .eq(AfterSalePending::getSourceId, sourceId));
        BigDecimal sorted = BigDecimal.ZERO;
        for (AfterSalePending b : batches) {
            if (b.getSortedQuantity() != null) sorted = sorted.add(b.getSortedQuantity());
        }
        if (sorted.compareTo(BigDecimal.ZERO) > 0)
            throw new BusinessException("该" + billLabel + "已有货物被退货整理（已整理 " + fmt(sorted)
                    + "），无法反审核，请先反审核对应的退货整理单");
    }

    /**
     * 保存应收台账：按 billNo 复用已存在记录后再写入。
     * <p>反审核是「冲销」（仅置 CANCELLED，记录保留），若再次审核时直接 insert 同 billNo 会撞 uk_bill_no，
     * 因此这里统一走「存在则显式重置各字段、不存在再插入」。</p>
     */
    private void saveReceivable(FinanceReceivable fr) {
        FinanceReceivable exist = financeReceivableMapper.selectOne(
                new LambdaQueryWrapper<FinanceReceivable>().eq(FinanceReceivable::getBillNo, fr.getBillNo()));
        if (exist == null) {
            financeReceivableMapper.insert(fr);
            return;
        }
        // 显式 set 各字段重置台账（不用 updateById，避免字段策略导致漏更新，造成"再审核成功但台账仍是已冲销状态"）
        financeReceivableMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FinanceReceivable>()
                        .eq(FinanceReceivable::getId, exist.getId())
                        .set(FinanceReceivable::getCustomerId, fr.getCustomerId())
                        .set(FinanceReceivable::getCustomerName, fr.getCustomerName())
                        .set(FinanceReceivable::getSourceBillType, fr.getSourceBillType())
                        .set(FinanceReceivable::getSourceBillNo, fr.getSourceBillNo())
                        .set(FinanceReceivable::getSourceId, fr.getSourceId())
                        .set(FinanceReceivable::getAmount, fr.getAmount())
                        .set(FinanceReceivable::getPaidAmount, fr.getPaidAmount())
                        .set(FinanceReceivable::getUnpaidAmount, fr.getUnpaidAmount())
                        .set(FinanceReceivable::getDueDate, fr.getDueDate())
                        .set(FinanceReceivable::getStatus, fr.getStatus())
                        .set(FinanceReceivable::getRemark, fr.getRemark()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaleReturn create(SaleReturn order, List<Map<String, Object>> itemMaps) {
        order.setId(null);
        order.setStatus(DocStatus.DRAFT.getCode());
        order.setCode(generateCode());
        fillSaleOrderInfo(order);
        validateReturnQuantity(order, itemMaps);
        // 总额以明细为准（前端不传 totalAmount），否则审核时不会生成负向应收
        order.setTotalAmount(sumAmount(itemMaps));
        normalizeCharge(order);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        returnMapper.insert(order);
        saveItems(order.getId(), itemMaps);
        // 客户名称是非表字段，列表接口已回填；创建返回同样回填，避免调用方拿到 null
        if (order.getCustomerName() == null && order.getCustomerId() != null) {
            Customer c = customerMapper.selectById(order.getCustomerId());
            if (c != null) order.setCustomerName(c.getName());
        }
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaleReturn update(Long id, SaleReturn order, List<Map<String, Object>> itemMaps) {
        SaleReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("销售退单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        order.setId(id);
        order.setCode(null);
        order.setStatus(null);
        fillSaleOrderInfo(order);
        validateReturnQuantity(order, itemMaps);
        // 同 create：总额以明细为准
        order.setTotalAmount(sumAmount(itemMaps));
        normalizeCharge(order);
        returnMapper.updateById(order);
        itemMapper.delete(new LambdaQueryWrapper<SaleReturnItem>().eq(SaleReturnItem::getReturnId, id));
        saveItems(id, itemMaps);
        return returnMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        SaleReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("销售退单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免库存/应收重复写
        if (!DocStatusGuard.claim(returnMapper, SaleReturn::getId, id, SaleReturn::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        List<SaleReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleReturnItem>().eq(SaleReturnItem::getReturnId, id));
        if (items.isEmpty()) throw new BusinessException("销售退单明细不能为空");
        // P2-33：数量必须为正（负数量会生成负向应收/负向库存）
        for (SaleReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("退货数量必须大于 0（明细行ID=" + it.getId() + "）");
        }
        // 落仓校验（2026-09-16 方案 A：仓型收敛为「成品仓/辅料仓」，原"售后仓"已取消）——
        // 退回的待分类品入**自有成品仓**（品质仍为 PENDING），后续由退货整理单按品质分流；
        // 前端下拉已过滤，此处防接口绕过。
        Warehouse wh = warehouseMapper.selectById(order.getWarehouseId());
        if (wh == null) throw new BusinessException("退货仓库不存在");
        if (!com.beichen.erp.warehouse.common.WarehouseCategory.INVENTORY.getCode().equals(wh.getWarehouseCategory())
                || !WarehouseType.FINISHED.getCode().equals(wh.getWarehouseType()))
            throw new BusinessException("销售退货只能选自有成品仓，当前仓库类别=" + wh.getWarehouseCategory()
                    + "，仓型=" + wh.getWarehouseType());
        // 库存联动：客户退回待分类品（品质默认待分类），入库增加库存
        // 批量取产品，避免循环内逐条查库（N+1）
        Map<Long, Product> pMap = productMap(items);
        for (SaleReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product product = pMap.get(it.getProductId());
            stockService.changeStock(order.getWarehouseId(),
                    // 流水留痕：与反审核保持一致，优先用明细冗余的产品名
                    it.getProductName() != null && !it.getProductName().isBlank() ? it.getProductName()
                            : (product != null ? product.getName() : ""),
                    it.getQuantity(),
                    StockChangeType.SALE_RETURN_IN, order.getCode(), RelatedBillType.SALE_RETURN, it.getProductId(),
                    "", order.getId(), it.getQualityType() != null ? it.getQualityType() : ProductQualityType.PENDING.getCode());
        }
        // 追溯联动：登记售后待整理批次，供退货整理单消费（退单与换货单统一入口）
        createPendingBatches(order, items, pMap);
        // 财务联动：生成负向应收冲抵原销售应收
        if (order.getTotalAmount() != null && order.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            FinanceReceivable fr = new FinanceReceivable();
            fr.setBillNo(order.getCode());
            fr.setCustomerId(order.getCustomerId());
            // 应收台账留痕：固化开单时的客户名（实时查一次）
            Customer c = order.getCustomerId() != null ? customerMapper.selectById(order.getCustomerId()) : null;
            fr.setCustomerName(c != null ? c.getName() : "");
            fr.setSourceBillType(SourceBillType.SALE_RETURN.getCode());
            fr.setSourceBillNo(order.getCode());
            fr.setSourceId(order.getId());
            fr.setAmount(order.getTotalAmount().negate());
            fr.setPaidAmount(BigDecimal.ZERO);
            // 负向应收：unpaidAmount 与 amount 一致（负数表示冲抵金额），便于应收汇总口径正确
            fr.setUnpaidAmount(order.getTotalAmount().negate());
            // 到期日必须落库：账单生成按「到期日判期」取数，为空会被整行漏掉（曾致账单金额虚高）
            fr.setDueDate(order.getReturnDate() != null ? order.getReturnDate() : LocalDate.now());
            fr.setStatus(SettlementStatus.UNSETTLED.getCode());
            fr.setRemark("销售退货冲抵应收");
            saveReceivable(fr);
        }
        // 注：折损收款已迁移到「退货整理单」（整理后才知道 B/C/不良 各多少，金额应在整理环节确定），此处不再生成 -LOSS 应收
        // 财务联动：选择收费时生成一条独立正向应收（单号 -FEE 后缀，与退单本体的负向冲抵区分）
        saveChargeReceivable(order);
        SaleReturn u = new SaleReturn();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditorId(getCurrentUserId());
        u.setAuditorName(getCurrentUserName());
        u.setAuditTime(LocalDateTime.now());
        returnMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        SaleReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("销售退单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免库存/应收重复冲销
        if (!DocStatusGuard.claim(returnMapper, SaleReturn::getId, id, SaleReturn::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已审核的销售退单可反审核");
        // 对称回滚：扣减已入库的待分类品库存
        List<SaleReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleReturnItem>().eq(SaleReturnItem::getReturnId, id));
        // 已被退货整理的货物不允许反审核：整理单会把售后仓待分类库存转走，反审核将扣不动或造成跨单据不一致
        assertNotSorted(AfterSaleSourceType.SALE_RETURN, id, "销售退单");
        // 批量取产品，避免循环内逐条查库（N+1）
        Map<Long, Product> pMap = productMap(items);
        for (SaleReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product product = pMap.get(it.getProductId());
            stockService.changeStock(order.getWarehouseId(),
                    // 流水留痕：优先用明细冗余的产品名，避免产品改名后历史流水备注跟着变
                    it.getProductName() != null && !it.getProductName().isBlank() ? it.getProductName()
                            : (product != null ? product.getName() : ""),
                    it.getQuantity().negate(),
                    StockChangeType.SALE_RETURN_UN_AUDIT, order.getCode(), RelatedBillType.SALE_RETURN, it.getProductId(),
                    "", order.getId(), it.getQualityType() != null ? it.getQualityType() : ProductQualityType.PENDING.getCode());
        }
        // 追溯联动：撤销本单登记的待整理批次（护栏已确保未被整理，可安全删除）
        deletePendingBatches(AfterSaleSourceType.SALE_RETURN, id);
        // 财务联动：冲销退货负向应收台账（历史单据可能未生成，不存在则跳过）
        reverseReceivableIfExists(order.getCode());
        // 财务联动：冲销退货收费台账（未收费时台账不存在，跳过）
        reverseReceivableIfExists(order.getCode() + "-FEE");
        // 审核信息必须用 UpdateWrapper 显式置 null：updateById 忽略 null 字段，反审核后仍显示审核人/时间
        returnMapper.update(null, new LambdaUpdateWrapper<SaleReturn>()
                .eq(SaleReturn::getId, id)
                .set(SaleReturn::getStatus, DocStatus.DRAFT.getCode())
                .set(SaleReturn::getAuditorId, null)
                .set(SaleReturn::getAuditorName, null)
                .set(SaleReturn::getAuditTime, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        SaleReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("销售退单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败
        if (!DocStatusGuard.claim(returnMapper, SaleReturn::getId, id, SaleReturn::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废");
        SaleReturn u = new SaleReturn();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        returnMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 退货单不做物理删除，语义等同于作废：避免已审核单据被物理删除后留下孤儿应收台账与库存流水
        cancel(id);
    }

    @Override
    public List<Map<String, Object>> saleOrderItems(Long saleOrderId) {
        if (saleOrderId == null) return List.of();
        List<SaleOrderItem> oiList = saleOrderItemMapper.selectList(new LambdaQueryWrapper<SaleOrderItem>()
                .eq(SaleOrderItem::getOrderId, saleOrderId));
        Map<Long, Product> pMap = new HashMap<>();
        Set<Long> pids = oiList.stream().map(SaleOrderItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!pids.isEmpty()) productMapper.selectBatchIds(pids).forEach(p -> pMap.put(p.getId(), p));
        // 批量取已退累计数量，避免每条明细查一次库（N+1）
        Map<Long, BigDecimal> returnedMap = alreadyReturnedBatch(
                oiList.stream().map(SaleOrderItem::getId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<Map<String, Object>> res = new ArrayList<>();
        for (SaleOrderItem oi : oiList) {
            Product p = pMap.get(oi.getProductId());
            BigDecimal sold = oi.getQuantity() == null ? BigDecimal.ZERO : oi.getQuantity();
            BigDecimal returned = returnedMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("saleOrderItemId", oi.getId());
            m.put("productId", oi.getProductId());
            m.put("productName", p != null ? p.getName() : "");
            m.put("unit", p != null ? p.getUnit() : "");
            m.put("qualityType", oi.getQualityType());
            m.put("quantity", oi.getQuantity());
            m.put("unitPrice", oi.getUnitPrice());
            m.put("amount", oi.getAmount());
            m.put("returnedQuantity", returned);
            m.put("canReturn", sold.subtract(returned));
            res.add(m);
        }
        return res;
    }

    /** 已退累计数量：关联该销售单明细的已审核退货单数量之和 */
    private BigDecimal alreadyReturned(Long saleOrderItemId) {
        return jdbcTemplate.query(
                "SELECT COALESCE(SUM(ri.quantity), 0) FROM sale_return_item ri " +
                "JOIN sale_return sr ON sr.id = ri.return_id " +
                "WHERE ri.sale_order_item_id = ? AND sr.status = 'AUDITED'",
                rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO, saleOrderItemId);
    }

    /**
     * 批量取已退累计数量（按销售单明细ID聚合），避免逐条查询。
     * <p>ID 集合来自数据库主键（非用户输入），拼接安全。</p>
     */
    private Map<Long, BigDecimal> alreadyReturnedBatch(Set<Long> saleOrderItemIds) {
        Map<Long, BigDecimal> res = new HashMap<>();
        if (saleOrderItemIds == null || saleOrderItemIds.isEmpty()) return res;
        String in = saleOrderItemIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.query(
                "SELECT ri.sale_order_item_id, COALESCE(SUM(ri.quantity), 0) FROM sale_return_item ri " +
                "JOIN sale_return sr ON sr.id = ri.return_id " +
                "WHERE ri.sale_order_item_id IN (" + in + ") AND sr.status = 'AUDITED' " +
                "GROUP BY ri.sale_order_item_id",
                rs -> {
                    while (rs.next()) res.put(rs.getLong(1), rs.getBigDecimal(2));
                    return null;
                });
        return res;
    }

    /** 填充关联销售单号（按 saleOrderId 实时查名） */
    private void fillSaleOrderInfo(SaleReturn order) {
        if (order.getSaleOrderId() != null) {
            SaleOrder so = saleOrderMapper.selectById(order.getSaleOrderId());
            order.setSaleOrderCode(so != null ? so.getCode() : null);
        } else {
            order.setSaleOrderCode(null);
        }
    }

    /** 关联销售单时校验：本次退货 ≤ 已售 - 已退（仅校验带 saleOrderItemId 的行） */
    private void validateReturnQuantity(SaleReturn order, List<Map<String, Object>> itemMaps) {
        if (order.getSaleOrderId() == null || itemMaps == null || itemMaps.isEmpty()) return;
        Map<Long, BigDecimal> qtyMap = new HashMap<>();
        Map<Long, String> nameMap = new HashMap<>();
        for (Map<String, Object> map : itemMaps) {
            Object soiObj = map.get("saleOrderItemId");
            if (soiObj == null || soiObj.toString().isBlank()) continue;
            Long soiId = Long.valueOf(soiObj.toString());
            BigDecimal qty = map.get("quantity") != null ? new BigDecimal(map.get("quantity").toString()) : BigDecimal.ZERO;
            qtyMap.merge(soiId, qty, BigDecimal::add);
            if (map.get("productId") != null) {
                Product p = productMapper.selectById(Long.valueOf(map.get("productId").toString()));
                if (p != null) nameMap.put(soiId, p.getName());
            }
        }
        if (qtyMap.isEmpty()) return;
        List<SaleOrderItem> oiList = saleOrderItemMapper.selectBatchIds(qtyMap.keySet());
        for (SaleOrderItem oi : oiList) {
            BigDecimal sold = oi.getQuantity() == null ? BigDecimal.ZERO : oi.getQuantity();
            BigDecimal returned = alreadyReturned(oi.getId());
            BigDecimal canReturn = sold.subtract(returned);
            BigDecimal thisQty = qtyMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            if (thisQty.compareTo(canReturn) > 0) {
                String name = nameMap.getOrDefault(oi.getId(), String.valueOf(oi.getProductId()));
                throw new BusinessException("产品[" + name + "]退货数量超过可退数量（已售" + fmt(sold)
                        + "，已退" + fmt(returned) + "，可退" + fmt(canReturn) + "）");
            }
        }
    }

    private String fmt(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private void saveItems(Long returnId, List<Map<String, Object>> itemMaps) {
        if (itemMaps == null) return;
        for (Map<String, Object> map : itemMaps) {
            SaleReturnItem it = new SaleReturnItem();
            it.setReturnId(returnId);
            if (map.get("saleOrderItemId") != null && !map.get("saleOrderItemId").toString().isBlank())
                it.setSaleOrderItemId(Long.valueOf(map.get("saleOrderItemId").toString()));
            if (map.get("productId") != null) it.setProductId(Long.valueOf(map.get("productId").toString()));
            if (map.get("quantity") != null) it.setQuantity(new BigDecimal(map.get("quantity").toString()));
            if (map.get("unitPrice") != null) it.setUnitPrice(new BigDecimal(map.get("unitPrice").toString()));
            if (map.get("amount") != null) it.setAmount(new BigDecimal(map.get("amount").toString()));
            if (map.get("remark") != null) it.setRemark(map.get("remark").toString());
            // 销售退货品质默认"待分类"，若前端传入则优先使用（售后待重新分类）
            String qt = map.get("qualityType") != null && !map.get("qualityType").toString().isBlank()
                    ? map.get("qualityType").toString() : ProductQualityType.PENDING.getCode();
            // 品质等级必须合法：非法值会导致该批库存无法被退货整理带出，形成孤儿库存
            if (!ProductQualityType.isValid(qt)) throw new BusinessException("非法的产品品质等级：" + qt);
            it.setQualityType(qt);
            Long cid = CompanyContext.get();
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    private String generateCode() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.SALE_RETURN + d;
        LambdaQueryWrapper<SaleReturn> w = new LambdaQueryWrapper<SaleReturn>()
                .likeRight(SaleReturn::getCode, pat).orderByDesc(SaleReturn::getCode).last("LIMIT 1");
        SaleReturn last = returnMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1;
            } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.SALE_RETURN + d + String.format("%03d", seq);
    }

    private Long getCurrentUserId() {
        try { return StpUtil.getLoginIdAsLong(); } catch (Exception e) { return null; }
    }

    private String getCurrentUserName() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = userMapper.selectById(userId);
            return user != null ? user.getUsername() : null;
        } catch (Exception e) { return null; }
    }
}
