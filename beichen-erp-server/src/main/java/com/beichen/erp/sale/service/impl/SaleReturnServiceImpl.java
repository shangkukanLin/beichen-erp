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
import com.beichen.erp.common.BillNoSeq;
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
// 2026-10-02（用户口径）：退货单不再借用换货的 ExchangeChargeType —— 退单只用「盖板划伤 / 其他」两项
import com.beichen.erp.sale.common.SaleReturnChargeType;
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
 * 销售退货单业务实现
 * <p>客户退回待整理品（品质默认待整理），审核时按 (warehouseId, productId, qualityType) 入库增加库存，并写库存流水，
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
            // 2026-09-26 B4（用户口径「明细列可点进产品详情」）：同一次遍历产出逐项 [{id,name,quantity}]
            // （id = 产品主数据ID，前端 EntityLinks(target=product) 直接用）
            List<Map<String, Object>> itemList = new ArrayList<>();
            StringBuilder summarySb = new StringBuilder();
            for (SaleReturnItem it : items) {
                String name = it.getProductId() != null && finalProductMap.containsKey(it.getProductId())
                        ? finalProductMap.get(it.getProductId()).getName() : "";
                String qty = it.getQuantity() != null ? it.getQuantity().stripTrailingZeros().toPlainString() : "0";
                if (summarySb.length() > 0) summarySb.append("，");
                summarySb.append(name).append("*").append(qty);
                if (it.getProductId() != null) {
                    Map<String, Object> im = new HashMap<>();
                    im.put("id", it.getProductId());
                    im.put("name", name);
                    im.put("quantity", it.getQuantity());
                    itemList.add(im);
                }
            }
            m.put("itemsSummary", summarySb.toString());
            m.put("items", itemList);
            return m;
        }).toList());
        return res;
    }

    @Override
    public SaleReturn getById(Long id) {
        SaleReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("销售退货单不存在");
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

    // ==================== 退货收费（2026-09-21 用户口径：收费**精确到产品**） ====================

    /**
     * 收费归一化：**明细级为准，单据级只作"批量默认"**。
     * <p>用户口径（2026-09-21）：「销售退货单和销售换货单应该都有付费，而且付费需要精确到产品上」⇒
     * 金额挂在明细行（一行 = 一个产品），单据级 charge_amount 由 {@link #recalcDocCharge} 按 Σ 明细回写；
     * 前端把单据级的类型/说明当"批量默认"下发，逐行可改。</p>
     * <p>⚠️ 只选了单据级收费、却没有任何一行填金额 ⇒ **报错**（避免"看起来收了费、台账却是 0"）。</p>
     */
    private void normalizeCharge(SaleReturn e, List<Map<String, Object>> itemMaps) {
        // 单据级类型（批量默认）必须合法（2026-10-02：退单专用枚举 —— 盖板划伤 / 其他）
        if (e.getChargeType() != null && !e.getChargeType().isBlank() && !SaleReturnChargeType.isValid(e.getChargeType()))
            throw new BusinessException("非法的收费类型：" + e.getChargeType());
        boolean anyItem = hasItemCharge(itemMaps);
        // 兼容旧前端（只填单据级金额、没逐行填）：落到**第一条明细** —— 保证「Σ明细 = 单据金额」恒等，
        // 既不丢钱也不报错（新前端会逐行填，走上面那条主路径）
        if (!anyItem && e.getChargeFlag() != null && e.getChargeFlag() == 1
                && toBig(e.getChargeAmount()).compareTo(BigDecimal.ZERO) > 0) {
            applyDocChargeToFirstItem(itemMaps, e.getChargeType(), e.getChargeAmount(), e.getChargeReason());
            anyItem = true;
        }
        if (!anyItem && e.getChargeFlag() != null && e.getChargeFlag() == 1)
            throw new BusinessException("已选择收费，请为具体产品填写收费金额（收费精确到产品）");
        // 单据级先按"有没有逐产品收费"归一化；金额交给 recalcDocCharge 按 Σ 明细回写
        e.setChargeFlag(anyItem ? 1 : 0);
        e.setChargeAmount(BigDecimal.ZERO);
        if (!anyItem) {
            e.setChargeType(null);
            e.setChargeReason(null);
        }
    }

    /** 兼容旧前端：把"单据级收费"落到第一条明细（类型缺省 OTHER） */
    private void applyDocChargeToFirstItem(List<Map<String, Object>> itemMaps, String type, BigDecimal amount, String reason) {
        if (itemMaps == null || itemMaps.isEmpty())
            throw new BusinessException("已选择收费，请先添加明细（收费精确到产品）");
        Map<String, Object> first = itemMaps.get(0);
        first.put("chargeAmount", amount);
        first.put("chargeType", type != null && !type.isBlank() ? type : SaleReturnChargeType.OTHER.getCode());
        if (reason != null) first.put("chargeReason", reason);
    }

    /** 本次提交里是否有任意一行填了收费金额（> 0） */
    private boolean hasItemCharge(List<Map<String, Object>> itemMaps) {
        if (itemMaps == null) return false;
        for (Map<String, Object> m : itemMaps) {
            Object amt = m.get("chargeAmount");
            if (amt != null && !amt.toString().isBlank() && toBig(amt).compareTo(BigDecimal.ZERO) > 0) return true;
        }
        return false;
    }

    /** 主表收费 = Σ(明细)：charge_flag=任一行收费；charge_amount=Σ；charge_type 仅当各收费行**类型一致**时回填，否则留空 */
    private void recalcDocCharge(Long returnId) {
        List<SaleReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleReturnItem>().eq(SaleReturnItem::getReturnId, returnId));
        BigDecimal sum = BigDecimal.ZERO;
        java.util.Set<String> types = new java.util.LinkedHashSet<>();
        for (SaleReturnItem it : items) {
            if (toBig(it.getChargeAmount()).compareTo(BigDecimal.ZERO) > 0) {
                sum = sum.add(it.getChargeAmount());
                if (it.getChargeType() != null && !it.getChargeType().isBlank()) types.add(it.getChargeType());
            }
        }
        returnMapper.update(null, new LambdaUpdateWrapper<SaleReturn>()
                .eq(SaleReturn::getId, returnId)
                .set(SaleReturn::getChargeFlag, sum.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0)
                .set(SaleReturn::getChargeAmount, sum)
                .set(SaleReturn::getChargeType, types.size() == 1 ? types.iterator().next() : null));
    }

    /**
     * 审核时生成退货收费应收：单号 -FEE 后缀（与退货单本体的负向冲抵区分，便于反审核精确冲销）。
     * <p>金额 = <b>Σ 明细行收费</b>（口径 A：一张单据一条台账）；remark 逐产品列出，
     * 财务列表能直接看到"哪个产品收了多少"，客户付款仍可一笔核销整单。</p>
     */
    private void saveChargeReceivable(SaleReturn e) {
        List<SaleReturnItem> items = getItems(e.getId()); // 已回填产品名，remark 直接可读
        BigDecimal total = BigDecimal.ZERO;
        List<String> parts = new java.util.ArrayList<>();
        for (SaleReturnItem it : items) {
            BigDecimal amt = toBig(it.getChargeAmount());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) continue;
            total = total.add(amt);
            String name = it.getProductName() != null && !it.getProductName().isBlank()
                    ? it.getProductName() : "产品" + it.getProductId();
            // 2026-10-02：台账备注展示**中文标签**（原先直接拼原始编码，如 COVER_SCRATCH ⇒ 现在「盖板划伤」）
            parts.add(name + " " + fmt(amt)
                    + (it.getChargeType() != null && !it.getChargeType().isBlank()
                        ? "（" + SaleReturnChargeType.labelOf(it.getChargeType()) + "）" : ""));
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0) return;
        FinanceReceivable fr = new FinanceReceivable();
        fr.setBillNo(e.getCode() + "-FEE");
        fr.setCustomerId(e.getCustomerId());
        Customer c = e.getCustomerId() != null ? customerMapper.selectById(e.getCustomerId()) : null;
        fr.setCustomerName(c != null ? c.getName() : "");
        fr.setSourceBillType(SourceBillType.SALE_RETURN_CHARGE.getCode());
        fr.setSourceBillNo(e.getCode());
        fr.setSourceId(e.getId());
        fr.setAmount(total);
        fr.setPaidAmount(BigDecimal.ZERO);
        fr.setUnpaidAmount(total);
        fr.setDueDate(e.getReturnDate());
        fr.setStatus(SettlementStatus.UNSETTLED.getCode());
        fr.setRemark("销售退货收费（逐产品）：" + String.join("；", parts)
                + (e.getChargeReason() != null && !e.getChargeReason().isBlank() ? "；说明：" + e.getChargeReason() : ""));
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
     * 登记售后待整理批次：退货单审核后退回的待整理品进入统一待整理池，供退货整理单消费。
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
        normalizeCharge(order, itemMaps);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        returnMapper.insert(order);
        saveItems(order.getId(), itemMaps, order.getChargeReason());
        // 主表收费 = Σ(明细)（2026-09-21 逐产品口径）
        recalcDocCharge(order.getId());
        SaleReturn fresh = returnMapper.selectById(order.getId());
        if (fresh != null) {
            order.setChargeFlag(fresh.getChargeFlag());
            order.setChargeAmount(fresh.getChargeAmount());
            order.setChargeType(fresh.getChargeType());
        }
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
        if (old == null) throw new BusinessException("销售退货单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        order.setId(id);
        order.setCode(null);
        order.setStatus(null);
        fillSaleOrderInfo(order);
        validateReturnQuantity(order, itemMaps);
        // 同 create：总额以明细为准
        order.setTotalAmount(sumAmount(itemMaps));
        normalizeCharge(order, itemMaps);
        returnMapper.updateById(order);
        itemMapper.delete(new LambdaQueryWrapper<SaleReturnItem>().eq(SaleReturnItem::getReturnId, id));
        saveItems(id, itemMaps, order.getChargeReason());
        // 主表收费 = Σ(明细)（2026-09-21 逐产品口径）；返回体从库里重取，保证 charge_* 是回写后的值
        recalcDocCharge(id);
        return returnMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        SaleReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("销售退货单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免库存/应收重复写
        if (!DocStatusGuard.claim(returnMapper, SaleReturn::getId, id, SaleReturn::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        List<SaleReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleReturnItem>().eq(SaleReturnItem::getReturnId, id));
        if (items.isEmpty()) throw new BusinessException("销售退货单明细不能为空");
        // P2-33：数量必须为正（负数量会生成负向应收/负向库存）
        for (SaleReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("退货数量必须大于 0（明细行ID=" + it.getId() + "）");
        }
        // 落仓校验（2026-09-16 方案 A：仓型收敛为「成品仓/辅料仓」，原"售后仓"已取消）——
        // 退回的待整理品入**自有成品仓**（品质仍为 PENDING），后续由退货整理单按品质分流；
        // 前端下拉已过滤，此处防接口绕过。
        Warehouse wh = warehouseMapper.selectById(order.getWarehouseId());
        if (wh == null) throw new BusinessException("退货仓库不存在");
        if (!com.beichen.erp.warehouse.common.WarehouseCategory.INVENTORY.getCode().equals(wh.getWarehouseCategory())
                || !WarehouseType.FINISHED.getCode().equals(wh.getWarehouseType()))
            throw new BusinessException("销售退货只能选自有成品仓，当前仓库类别=" + wh.getWarehouseCategory()
                    + "，仓型=" + wh.getWarehouseType());
        // 库存联动：客户退回待整理品（品质默认待整理），入库增加库存
        // 批量取产品，避免循环内逐条查库（N+1）
        Map<Long, Product> pMap = productMap(items);
        // 产品存在性预检（**保留**）：不存在的产品必须拒绝，否则会静默入库、库存流水备注退化成空串
        // （实测 productId=999999 曾报"未销售过"，提示语误导）。
        for (SaleReturnItem it : items) {
            if (it.getProductId() == null || pMap.get(it.getProductId()) == null)
                throw new BusinessException("产品不存在：ID=" + it.getProductId() + "（明细行ID=" + it.getId() + "）");
        }
        // 2026-10-01（F8 进货/销售退换货改造 · 第 4 步，用户口径「无来源的退货/换货只做库存/成本校验」）：
        // **撤销** F7-111（2026-09-20）的"产品必须**曾售出**"护栏 —— 它当初正是为"无来源退货缺少校验"
        // 补的底线（见该方法 javadoc），与本次"子菜单裸进可独立制单（线下/历史/其他渠道补录）"的口径直接冲突。
        // ⚠️ 副作用（用户已知悉并确认）：从未销售过的产品也能退货入库 ⇒ 若被误用会造成库存虚增，
        //    且无来源单没有对应的负应收可冲抵。**恢复方式**：取消下面一行的注释即可（方法体完整保留）。
        // assertProductsSoldOnce(items.stream().map(SaleReturnItem::getProductId).toList());
        for (SaleReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product product = pMap.get(it.getProductId());
            // F7-111（2026-09-20）：产品必须存在。原实现 pMap.get() 为 null 时**也照常入库**（流水备注退化为空串），
            // 与上面的"曾售出"合起来即为「产品已建档 + 卖过」双底线；与 SaleOrderServiceImpl.audit 口径对齐。
            if (it.getProductId() == null || product == null)
                throw new BusinessException("产品不存在：ID=" + it.getProductId() + "（明细行ID=" + it.getId() + "）");
            // P4（2026-09-30）：改调主重载（productId 直接作第 2 参）—— 原先那段"优先用明细冗余产品名"的取值
            // 只是为了喂给旧签名重载的第 2 个形参，而该形参在主重载里并不存在（流水备注按 productId 现取）。
            stockService.changeStock(order.getWarehouseId(),
                    it.getProductId(),
                    it.getQuantity(),
                    StockChangeType.SALE_RETURN_IN, order.getCode(), RelatedBillType.SALE_RETURN,
                    "", order.getId(), it.getQualityType() != null ? it.getQualityType() : ProductQualityType.PENDING.getCode());
        }
        // 追溯联动：登记售后待整理批次，供退货整理单消费（退货单与换货单统一入口）
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
        // 财务联动：选择收费时生成一条独立正向应收（单号 -FEE 后缀，与退货单本体的负向冲抵区分）
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
        if (order == null) throw new BusinessException("销售退货单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免库存/应收重复冲销
        if (!DocStatusGuard.claim(returnMapper, SaleReturn::getId, id, SaleReturn::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已审核的销售退货单可反审核");
        // 对称回滚：扣减已入库的待整理品库存
        List<SaleReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleReturnItem>().eq(SaleReturnItem::getReturnId, id));
        // 已被退货整理的货物不允许反审核：整理单会把售后仓待整理库存转走，反审核将扣不动或造成跨单据不一致
        assertNotSorted(AfterSaleSourceType.SALE_RETURN, id, "销售退货单");
        // 批量取产品，避免循环内逐条查库（N+1）
        Map<Long, Product> pMap = productMap(items);
        for (SaleReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            // P4（2026-09-30）：改调主重载（productId 直接作第 2 参）—— 原先那段"优先用明细冗余产品名"只为喂旧重载的第 2 形参
            stockService.changeStock(order.getWarehouseId(),
                    it.getProductId(),
                    it.getQuantity().negate(),
                    StockChangeType.SALE_RETURN_UN_AUDIT, order.getCode(), RelatedBillType.SALE_RETURN,
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
        if (old == null) throw new BusinessException("销售退货单不存在");
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
            // 历史累计仅作参考展示（2026-10-01 起**不参与**可退量计算）
            m.put("returnedQuantity", returned);
            // 2026-10-01（用户口径）：回显的"可退数量"必须与 validateReturnQuantity **同口径** ——
            // = 该明细的销售数量（不再扣历史累计），否则前端显示 0 而提交却能通过。
            m.put("canReturn", sold);
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

    /**
     * 关联销售单时校验：**本次退货 ≤ 已售**（仅校验带 saleOrderItemId 的行）。
     *
     * <p>⚠️ 2026-10-01 口径变更（用户口径「只要有库存就可以一直退/换，每一次不超过销售总数」）：
     * 原为 {@code 本次 ≤ 已售 − 已退}（累计扣减），现**不扣历史累计**；历史已退量仅用于报错信息参考。
     * 库存是否够、能否入库由审核环节把关。</p>
     */
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
            BigDecimal thisQty = qtyMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            // 2026-10-01（用户口径：只要有库存就可以一直退/换，**每一次不超过销售总数**）：
            // 单次退货量 ≤ 该明细的**销售数量**，**不再扣历史累计已退量**（历史量仅用于报错信息参考）。
            if (thisQty.compareTo(sold) > 0) {
                String name = nameMap.getOrDefault(oi.getId(), String.valueOf(oi.getProductId()));
                throw new BusinessException("产品[" + name + "]本次退货数量" + fmt(thisQty)
                        + "超过该明细的销售数量（销售" + fmt(sold) + "，历史已退" + fmt(returned)
                        + "，本次" + fmt(thisQty) + "）");
            }
        }
    }

    private String fmt(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private void saveItems(Long returnId, List<Map<String, Object>> itemMaps, String docChargeReason) {
        if (itemMaps == null) return;
        for (Map<String, Object> map : itemMaps) {
            SaleReturnItem it = new SaleReturnItem();
            it.setReturnId(returnId);
            if (map.get("saleOrderItemId") != null && !map.get("saleOrderItemId").toString().isBlank())
                it.setSaleOrderItemId(Long.valueOf(map.get("saleOrderItemId").toString()));
            // F7-111（2026-09-20）：产品必填 —— 原为 `if (map.get("productId") != null)` 才 set，
            // 允许落 product_id = NULL 的明细（现网 0 条，无历史包袱）。
            if (map.get("productId") == null || map.get("productId").toString().isBlank())
                throw new BusinessException("退货明细必须选择产品");
            it.setProductId(Long.valueOf(map.get("productId").toString()));
            if (map.get("quantity") != null) it.setQuantity(new BigDecimal(map.get("quantity").toString()));
            if (map.get("unitPrice") != null) it.setUnitPrice(new BigDecimal(map.get("unitPrice").toString()));
            // F7-115（2026-09-20）：明细金额**由服务端重算**，不再原样采用前端传值 ——
            // 原实现下"明细 amount（前端传什么存什么）"与"单头 totalAmount（服务端 Σ数量×单价）"
            // 可以不一致，对账时两边对不上；现与 SaleOrderServiceImpl.create 口径统一。
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal price = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(qty.multiply(price));
            // 逐产品收费（2026-09-21 用户口径）：金额 > 0 即收费；类型必须合法；说明缺省取单据级（批量默认）
            BigDecimal chargeAmt = map.get("chargeAmount") == null || map.get("chargeAmount").toString().isBlank()
                    ? BigDecimal.ZERO : new BigDecimal(map.get("chargeAmount").toString());
            if (chargeAmt.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("产品收费金额不能为负数");
            String chargeType = map.get("chargeType") != null && !map.get("chargeType").toString().isBlank()
                    ? map.get("chargeType").toString() : null;
            if (chargeAmt.compareTo(BigDecimal.ZERO) > 0) {
                String pname = map.get("productName") != null ? map.get("productName").toString() : String.valueOf(it.getProductId());
                if (chargeType == null) throw new BusinessException("产品[" + pname + "]已填收费金额，请选择收费类型");
                // 2026-10-02：退单专用枚举（盖板划伤 / 其他）
                if (!SaleReturnChargeType.isValid(chargeType)) throw new BusinessException("非法的收费类型：" + chargeType);
            } else {
                chargeType = null;
            }
            String chargeReason = map.get("chargeReason") != null && !map.get("chargeReason").toString().isBlank()
                    ? map.get("chargeReason").toString()
                    : (docChargeReason != null && !docChargeReason.isBlank() ? docChargeReason : null);
            it.setChargeFlag(chargeAmt.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0);
            it.setChargeType(chargeType);
            it.setChargeAmount(chargeAmt);
            it.setChargeReason(chargeAmt.compareTo(BigDecimal.ZERO) > 0 ? chargeReason : null);
            if (map.get("remark") != null) it.setRemark(map.get("remark").toString());
            // 销售退货品质默认"待整理"，若前端传入则优先使用（售后待重新分类）
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

    /**
     * F7-111（2026-09-20）：产品必须"**曾售出**"（宽松版）。
     *
     * <p>⚠️ <b>2026-10-01 起已停用（F8 进货/销售退换货改造 · 第 4 步）</b>：调用点已在
     * {@code audit} 中注释掉 —— 用户口径改为「无来源（子菜单裸进）的退货/换货只做库存/成本校验」，
     * 本护栏与之（线下/历史/其他渠道补录）冲突。方法体**完整保留**，需要恢复时取消 audit 里的注释；
     * 恢复前请评估"库存虚增"风险（无来源单没有对应负应收可冲抵）。</p>
     *
     * <p>判定 = 本公司范围内该产品存在**销售出库或换货出库**流水（{@code warehouse_stock_log.change_type}
     * ∈ {SALE_OUT, EXCHANGE_OUT}）。**不校验剩余可退量** —— 那是"关联销售单"时的口径（见
     * {@link #validateReturnQuantity}），本校验只兜住"从未卖过的产品凭空入库"。</p>
     *
     * <p>批量一次 {@code IN} 查询（避免逐行 N+1）。⚠️ id 列表由 {@code Long} 拼入 SQL：
     * 它们来自数据库主键 / 已由 {@code Long.valueOf} 解析的入参 ⇒ 只可能是数字 ⇒ 无注入面；
     * 若将来允许传字符串形参，必须改为参数化绑定。</p>
     */
    private void assertProductsSoldOnce(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return;
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        Set<Long> sold = new HashSet<>();
        jdbcTemplate.query(
                "SELECT DISTINCT product_id FROM warehouse_stock_log "
                        + "WHERE change_type IN ('SALE_OUT','EXCHANGE_OUT') AND product_id IN (" + in + ")",
                rs -> {
                    while (rs.next()) sold.add(rs.getLong(1));
                    return null;
                });
        for (Long pid : ids) {
            if (!sold.contains(pid))
                throw new BusinessException("产品未销售过，无法退货入库（产品ID=" + pid
                        + "）。若确属特殊业务（客户未售先退），请先补开销售单，或改走其他出入库单。");
        }
    }

    private String generateCode() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.SALE_RETURN + d;
        LambdaQueryWrapper<SaleReturn> w = new LambdaQueryWrapper<SaleReturn>()
                .likeRight(SaleReturn::getCode, pat).orderByDesc(SaleReturn::getCode).last("LIMIT 1");
        SaleReturn last = returnMapper.selectOne(w);
        // F7-109（2026-09-20）：统一走 BillNoSeq（详见该类 javadoc）。
        int seq = BillNoSeq.lastSeq(last == null ? null : last.getCode(), pat) + 1;
        return BillNoSeq.formatUnique(pat, seq, cand -> returnMapper.selectCount(new LambdaQueryWrapper<SaleReturn>().eq(SaleReturn::getCode, cand)) > 0) /* F7-261 冲突检测+重试 */;
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
