package com.beichen.erp.outsource.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.mapper.MaterialTypeMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.service.PayableHelper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.outsource.common.OutsourceChargeType;
import com.beichen.erp.outsource.common.OutsourceOrderStatus;
import com.beichen.erp.outsource.common.OutsourceReturnType;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.outsource.entity.*;
import com.beichen.erp.outsource.mapper.*;
import com.beichen.erp.outsource.service.OutsourceReturnOrderService;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.warehouse.service.CostService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 委外加工退货单业务层实现
 * <p>退货物料入工厂委外仓、成品出库、负向应付 + 收费应付。草稿-审核-取消审核状态机。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutsourceReturnOrderServiceImpl implements OutsourceReturnOrderService {

    /** 维修退货进度筛选：还有未返回（已审核、未结案、送修 > 已返回） */
    private static final String PROGRESS_PENDING_RETURN = "PENDING_RETURN";
    /** 维修退货进度筛选：已结案 */
    private static final String PROGRESS_CLOSED = "CLOSED";
    /** 维修退货进度筛选：**已返回完**（全部送回，含已结案；2026-09-27 三级菜单「已返回完」页签） */
    private static final String PROGRESS_RETURNED = "RETURNED";
    /**
     * 维修退货进度筛选：**待返回（含草稿）**（2026-09-27 三级菜单「待返回」页签）。
     * <p>与 PENDING_RETURN 的差别：PENDING_RETURN 只认"已审核且未送完"，会漏掉**草稿**（还没送修的半成品单）
     * ⇒ 页签口径下草稿会在三个页签里"消失"。OPEN = 草稿 ∪ (已审核且送修 &gt; 已返回)。</p>
     */
    private static final String PROGRESS_OPEN = "OPEN";

    private final ReturnOrderMapper returnOrderMapper;
    private final ReturnOrderItemMapper returnOrderItemMapper;
    private final OutsourceReturnOrderProductMapper returnProductMapper;
    /** 维修返回记录（维修退货单的"回来"腿，2026-09-17） */
    private final OutsourceReturnOrderRepairMapper repairMapper;
    /** 维修返回明细（P2-1 2026-09-25：在厂核销 ALLOC + 实际用料 MATERIAL） */
    private final OutsourceReturnOrderRepairItemMapper repairItemMapper;
    private final OutsourceOrderMapper orderMapper;
    private final OutsourceOrderProductMapper orderProductMapper;
    /** BOM 快照（2026-09-17）：退货页「BOM来源」选项按快照列出，物料明细按快照ID取 */
    private final BomSnapshotMapper bomSnapshotMapper;
    private final BomSnapshotItemMapper bomSnapshotItemMapper;
    /** 交货记录（成品收货）——「从收货页发起退货」的可退预填用 */
    private final OutsourceOrderDeliveryMapper orderDeliveryMapper;
    private final WarehouseMapper warehouseMapper;
    /** P2-1（2026-09-25）：读在厂成品 PRODUCT_REPAIR 行（核销按规格行分配） */
    private final WarehouseStockMapper warehouseStockMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final com.beichen.erp.dev.mapper.MaterialTypeMapper materialTypeMapper;
    private final SupplierMapper supplierMapper;
    private final PayableHelper payableHelper;
    private final WarehouseStockService warehouseStockService;
    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;
    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    /** F7-77（2026-09-20）：物料单价统一实现（成本价 → FIFO → 参考价的三级链保持不变） */
    private final com.beichen.erp.outsource.service.OutsourceMaterialPricingService pricingService;
    /** P2-1（2026-09-25）：维修用料 FIFO 成本结转（登记时 applyProduct / 撤销时 reverseByBill） */
    private final CostService costService;

    @Override
    public Page<Map<String, Object>> page(int pageNum, int pageSize, String code, Long factoryId, String returnType,
                                          String progress, String statuses) {
        LambdaQueryWrapper<ReturnOrder> w = new LambdaQueryWrapper<ReturnOrder>()
            .eq(code != null && !code.isBlank(), ReturnOrder::getCode, code)
            .eq(factoryId != null, ReturnOrder::getFactoryId, factoryId)
            .eq(returnType != null && !returnType.isBlank(), ReturnOrder::getReturnType, returnType);
        // 2026-09-27：状态多值（与 MaterialOrderService 的 statuses 同一约定）——
        // 三级菜单「有效单据」页签 = DRAFT,AUDITED（排除已作废）、「已作废」= CANCELLED
        if (statuses != null && !statuses.isBlank()) {
            List<String> sts = java.util.Arrays.stream(statuses.split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toList());
            if (!sts.isEmpty()) w.in(ReturnOrder::getStatus, sts);
        }
        w.orderByDesc(ReturnOrder::getId);
        // 维修退货进度筛选（2026-09-17）：
        //   PENDING_RETURN = 维修退货、已审核、未结案，且「送修合计 > 已返回合计」（工厂还没把货送完）
        //   CLOSED         = 已结案（全部送回并人工确认收尾）
        if (PROGRESS_PENDING_RETURN.equalsIgnoreCase(progress)) {
            w.eq(ReturnOrder::getReturnType, OutsourceReturnType.REPAIR.getCode())
             .eq(ReturnOrder::getStatus, DocStatus.AUDITED.getCode())
             .eq(ReturnOrder::getClosedFlag, 0)
             .apply("(SELECT IFNULL(SUM(p.quantity),0) FROM outsource_return_order_product p WHERE p.return_order_id = outsource_return_order.id)"
                     + " > (SELECT IFNULL(SUM(r.quantity),0) FROM outsource_return_order_repair r WHERE r.return_order_id = outsource_return_order.id)");
        } else if (PROGRESS_CLOSED.equalsIgnoreCase(progress)) {
            w.eq(ReturnOrder::getClosedFlag, 1);
        } else if (PROGRESS_OPEN.equalsIgnoreCase(progress)) {
            w.eq(ReturnOrder::getReturnType, OutsourceReturnType.REPAIR.getCode())
             .in(ReturnOrder::getStatus, java.util.List.of(DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
             .apply("(outsource_return_order.status = '" + DocStatus.DRAFT.getCode() + "'"
                     + " OR (SELECT IFNULL(SUM(p.quantity),0) FROM outsource_return_order_product p WHERE p.return_order_id = outsource_return_order.id)"
                     + " > (SELECT IFNULL(SUM(r.quantity),0) FROM outsource_return_order_repair r WHERE r.return_order_id = outsource_return_order.id))");
        } else if (PROGRESS_RETURNED.equalsIgnoreCase(progress)) {
            // 2026-09-27「已返回完」页签：已审核且**全部送回**（含已结案；与 PENDING_RETURN 互补）
            w.eq(ReturnOrder::getReturnType, OutsourceReturnType.REPAIR.getCode())
             .eq(ReturnOrder::getStatus, DocStatus.AUDITED.getCode())
             .apply("(SELECT IFNULL(SUM(p.quantity),0) FROM outsource_return_order_product p WHERE p.return_order_id = outsource_return_order.id)"
                     + " <= (SELECT IFNULL(SUM(r.quantity),0) FROM outsource_return_order_repair r WHERE r.return_order_id = outsource_return_order.id)");
        }
        Page<ReturnOrder> raw = returnOrderMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 维修返回量：一次性按本页单据汇总（列表展示"送修 N / 已返回 M"）
        List<Long> pageIds = raw.getRecords().stream().map(ReturnOrder::getId).toList();
        Map<Long, BigDecimal> repairReturned = new LinkedHashMap<>();
        if (!pageIds.isEmpty()) {
            for (OutsourceReturnOrderRepair r : repairMapper.selectList(new LambdaQueryWrapper<OutsourceReturnOrderRepair>()
                    .in(OutsourceReturnOrderRepair::getReturnOrderId, pageIds))) {
                if (r.getReturnOrderId() == null) continue;
                repairReturned.merge(r.getReturnOrderId(), nz(r.getQuantity()), BigDecimal::add);
            }
        }
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            m.put("returnType", OutsourceReturnType.normalize(o.getReturnType()));
            m.put("repairReturnedQty", repairReturned.getOrDefault(o.getId(), BigDecimal.ZERO));
            m.put("factoryId", o.getFactoryId()); m.put("orderId", o.getOrderId());
            m.put("sourceDeliveryId", o.getSourceDeliveryId());
            m.put("returnDate", o.getReturnDate()); m.put("status", o.getStatus());
            m.put("remark", o.getRemark()); m.put("createTime", o.getCreateTime());
            m.put("chargeFlag", o.getChargeFlag()); m.put("chargeType", o.getChargeType());
            m.put("chargeAmount", o.getChargeAmount()); m.put("chargeReason", o.getChargeReason());
            if (o.getFactoryId() != null) {
                Supplier f = supplierMapper.selectById(o.getFactoryId());
                m.put("factoryName", f != null ? f.getName() : "");
            }
            if (o.getOrderId() != null) {
                OutsourceOrder ord = orderMapper.selectById(o.getOrderId());
                m.put("orderCode", ord != null ? ord.getCode() : "");
            }
            List<ReturnOrderItem> items = returnOrderItemMapper.selectList(
                new LambdaQueryWrapper<ReturnOrderItem>().eq(ReturnOrderItem::getReturnOrderId, o.getId()));
            BigDecimal totalQty = BigDecimal.ZERO;
            StringBuilder sb = new StringBuilder();
            for (ReturnOrderItem it : items) {
                BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
                totalQty = totalQty.add(qty);
                if (sb.length() > 0) sb.append("、");
                sb.append(getMaterialNameById(it.getMaterialId())).append("×").append(qty.stripTrailingZeros().toPlainString());
            }
            m.put("totalQuantity", totalQty); m.put("itemSummary", sb.toString());
            // 退货成品摘要：维修退货没有物料明细（itemSummary 为空），列表改看"产品×数量"（2026-09-17）
            StringBuilder psb = new StringBuilder();
            BigDecimal sentQty = BigDecimal.ZERO;
            // 2026-09-25（用户口径「产品列可点进详情」）：逐项 {id,name,quantity}，前端渲染链接跳 /product/detail/:id
            List<Map<String, Object>> prodList = new ArrayList<>();
            for (OutsourceReturnOrderProduct p : returnProductMapper.selectList(
                    new LambdaQueryWrapper<OutsourceReturnOrderProduct>().eq(OutsourceReturnOrderProduct::getReturnOrderId, o.getId()))) {
                if (p.getQuantity() == null || p.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
                sentQty = sentQty.add(p.getQuantity());
                if (psb.length() > 0) psb.append("、");
                psb.append(p.getProductName() != null ? p.getProductName() : ("#" + p.getProductId()))
                   .append("×").append(p.getQuantity().stripTrailingZeros().toPlainString());
                if (p.getProductId() != null) {
                    Map<String, Object> pm = new HashMap<>();
                    pm.put("id", p.getProductId());
                    pm.put("name", p.getProductName());
                    pm.put("quantity", p.getQuantity());
                    prodList.add(pm);
                }
            }
            m.put("productSummary", psb.toString());
            m.put("products", prodList);
            // 送修 / 已返回 / 未返回 + 结案（维修退货列表跟踪用，2026-09-17）
            BigDecimal returnedQty = repairReturned.getOrDefault(o.getId(), BigDecimal.ZERO);
            m.put("sentQty", sentQty);
            m.put("unreturnedQty", sentQty.subtract(returnedQty).max(BigDecimal.ZERO));
            m.put("closedFlag", o.getClosedFlag() != null ? o.getClosedFlag() : 0);
            m.put("closedTime", o.getClosedTime());
            return m;
        }).toList());
        return result;
    }

    @Override
    public Map<String, Object> detail(Long id) {
        ReturnOrder o = returnOrderMapper.selectById(id);
        if (o == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId()); m.put("code", o.getCode()); m.put("factoryId", o.getFactoryId());
        m.put("returnType", OutsourceReturnType.normalize(o.getReturnType()));
        m.put("orderId", o.getOrderId()); m.put("returnDate", o.getReturnDate());
        m.put("sourceDeliveryId", o.getSourceDeliveryId());
        m.put("status", o.getStatus()); m.put("remark", o.getRemark());
        m.put("chargeFlag", o.getChargeFlag()); m.put("chargeType", o.getChargeType());
        m.put("chargeAmount", o.getChargeAmount()); m.put("chargeReason", o.getChargeReason());
        // E4：编辑页需要回填「成品出库仓」与退货成品明细（原先只返回物料明细）
        m.put("warehouseId", o.getWarehouseId());
        // 2026-09-27：详情页要**显示仓名**（此前只回 id ⇒ 页面只能显示数字，用户实测反馈）
        if (o.getWarehouseId() != null) {
            Warehouse wh = warehouseMapper.selectById(o.getWarehouseId());
            m.put("warehouseName", wh != null ? wh.getWarehouseName() : "");
        }
        // 维修返回记录（仅维修退货单会有；详情页展示"送修 N / 已返回 M"并可撤销）
        List<OutsourceReturnOrderRepair> repairs = repairMapper.selectList(
            new LambdaQueryWrapper<OutsourceReturnOrderRepair>().eq(OutsourceReturnOrderRepair::getReturnOrderId, id));
        // P2-1（2026-09-25）：每条返回记录附用料明细汇总（前端记录列表显示"用料"列）
        List<Map<String, Object>> repairMaps = new ArrayList<>();
        for (OutsourceReturnOrderRepair r : repairs) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", r.getId());
            rm.put("returnOrderId", r.getReturnOrderId());
            rm.put("repairDate", r.getRepairDate());
            rm.put("warehouseId", r.getWarehouseId());
            rm.put("productId", r.getProductId());
            rm.put("productName", r.getProductName());
            rm.put("qualityType", r.getQualityType());
            rm.put("quantity", r.getQuantity());
            rm.put("remark", r.getRemark());
            List<Map<String, Object>> mats = new ArrayList<>();
            BigDecimal matTotal = BigDecimal.ZERO;
            StringBuilder summary = new StringBuilder();
            for (OutsourceReturnOrderRepairItem it : repairItemMapper.selectList(new LambdaQueryWrapper<OutsourceReturnOrderRepairItem>()
                    .eq(OutsourceReturnOrderRepairItem::getRepairRecordId, r.getId())
                    .eq(OutsourceReturnOrderRepairItem::getItemType, OutsourceReturnOrderRepairItem.TYPE_MATERIAL)
                    .orderByAsc(OutsourceReturnOrderRepairItem::getId))) {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("materialId", it.getMaterialId());
                im.put("materialName", it.getMaterialName());
                im.put("unit", it.getUnit());
                im.put("quantity", it.getQuantity());
                im.put("unitPrice", it.getUnitPrice());
                im.put("amount", it.getAmount());
                mats.add(im);
                matTotal = matTotal.add(it.getAmount() == null ? BigDecimal.ZERO : it.getAmount());
                if (summary.length() > 0) summary.append("、");
                summary.append(it.getMaterialName()).append("×").append(it.getQuantity());
            }
            rm.put("materials", mats);
            rm.put("materialSummary", summary.toString());
            rm.put("materialAmount", matTotal);
            repairMaps.add(rm);
        }
        m.put("repairReturns", repairMaps);
        m.put("repairReturnedQty", repairs.stream().map(r -> nz(r.getQuantity())).reduce(BigDecimal.ZERO, BigDecimal::add));
        // 结案信息（仅维修退货用，2026-09-17）
        m.put("closedFlag", o.getClosedFlag() != null ? o.getClosedFlag() : 0);
        m.put("closedTime", o.getClosedTime());
        m.put("closedBy", o.getClosedBy());
        // 退货成品明细：附「所用 BOM 快照」的版本/来源，便于详情页与编辑页回看（2026-09-17）
        List<Map<String, Object>> prods = new ArrayList<>();
        for (OutsourceReturnOrderProduct p : returnProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceReturnOrderProduct>().eq(OutsourceReturnOrderProduct::getReturnOrderId, id))) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", p.getId());
            pm.put("productId", p.getProductId());
            pm.put("productName", p.getProductName());
            pm.put("quantity", p.getQuantity());
            pm.put("qualityType", p.getQualityType());
            pm.put("bomSnapshotId", p.getBomSnapshotId());
            if (p.getBomSnapshotId() != null) {
                BomSnapshot s = bomSnapshotMapper.selectById(p.getBomSnapshotId());
                pm.put("bomVersion", s != null ? s.getBomVersion() : null);
                pm.put("bomSnapshotKind", s != null ? s.getKind() : null);
            }
            prods.add(pm);
        }
        m.put("products", prods);
        // 送修 / 已返回 / 未返回（维修退货进度口径，与列表、结案校验一致）
        BigDecimal sentQty = prods.stream()
                .map(pm -> toBigDecimal(pm.get("quantity")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal repairReturnedQty = repairs.stream().map(r -> nz(r.getQuantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
        m.put("sentQty", sentQty);
        m.put("unreturnedQty", sentQty.subtract(repairReturnedQty).max(BigDecimal.ZERO));
        m.put("createTime", o.getCreateTime());
        if (o.getFactoryId() != null) {
            Supplier f = supplierMapper.selectById(o.getFactoryId());
            m.put("factoryName", f != null ? f.getName() : "");
        }
        if (o.getOrderId() != null) {
            OutsourceOrder ord = orderMapper.selectById(o.getOrderId());
            m.put("orderCode", ord != null ? ord.getCode() : "");
        }
        // 明细补物料/类型名称（2026-09-17：原先直接返回实体，详情页只能显示物料ID）
        List<Map<String, Object>> itemList = new ArrayList<>();
        for (ReturnOrderItem it : returnOrderItemMapper.selectList(
                new LambdaQueryWrapper<ReturnOrderItem>().eq(ReturnOrderItem::getReturnOrderId, id))) {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("id", it.getId());
            im.put("materialId", it.getMaterialId());
            im.put("materialName", getMaterialNameById(it.getMaterialId()));
            im.put("materialTypeId", it.getMaterialTypeId());
            im.put("materialTypeName", getMaterialTypeNameById(it.getMaterialTypeId()));
            im.put("unit", it.getUnit());
            im.put("quantity", it.getQuantity());
            im.put("unitPrice", it.getUnitPrice());
            im.put("amount", it.getAmount());
            itemList.add(im);
        }
        m.put("items", itemList);
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(ReturnOrder order, Map<String, Object> body) {
        // 退货类型（2026-09-17）：空值按「加工退货」（与建表默认值一致，兼容存量调用）
        String type = OutsourceReturnType.normalize(order.getReturnType());
        // 2026-09-21 用户口径：**"退回加工厂"不再是本页的职责** —— 该动作本意是"可以不关联加工单"的
        // 红冲收货，已统一到「加工退货」页（有单 → 该加工单收货详细页的「加工退货」按钮；无单 →
        // 该页的「新增无单加工退货」），两者都往 outsource_order_delivery 写负数记录、走同一套审核，
        // 并汇总到该页同一张台账。本页只保留维修退货（送修/收费/维修返回/结案确实不是一回事）。
        // 存量独立退货单不受影响，仍可审核/作废（走详情页 URL）。
        if (!OutsourceReturnType.isRepair(type))
            throw new BusinessException("本页只处理维修退货；退回加工厂请在「加工退货」页办理（有加工单 → 该加工单的"
                    + "收货详细页；没有加工单 → 该页的「新增无单加工退货」）");
        order.setReturnType(type);
        // 收费字段先规范化（不收费归零；收费则类型合法且金额 > 0），再做类型专属校验
        normalizeCharge(order, body);
        // 成品出库仓（审核时从我方仓扣减成品）
        Object invWhObj = body.get("warehouseId");
        if (invWhObj != null && !invWhObj.toString().isBlank()) order.setWarehouseId(Long.valueOf(invWhObj.toString()));
        validateByType(type, order, body);

        order.setCode(generateCode());
        if (order.getReturnDate() == null) order.setReturnDate(LocalDate.now());
        order.setStatus(DocStatus.DRAFT.getCode());
        order.setRemark((String) body.get("remark"));
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        returnOrderMapper.insert(order);

        saveDetailLines(order, body, cid);
    }

    /**
     * 类型专属校验（2026-09-17 新增，create / update 共用）：
     * <ul>
     *   <li><b>加工退货</b>：可关联加工单（也可不关联）；<b>不产生工厂收费</b>（不良是工厂的问题，加工厂不向我方收费）。</li>
     *   <li><b>维修退货</b>：<b>必须不关联加工单</b>；<b>必须由加工厂向我方收费</b>（收费类型 + 金额 &gt; 0）；
     *       不涉及 BOM 还料（物料明细必须为空）。</li>
     * </ul>
     * 两类都必须至少有一行「退货成品」（数量 &gt; 0）。
     */
    private void validateByType(String type, ReturnOrder order, Map<String, Object> body) {
        boolean repair = OutsourceReturnType.isRepair(type);
        if (repair && order.getOrderId() != null)
            throw new BusinessException("维修退货不关联加工订单，请清除「关联加工单」");
        if (repair) {
            if (order.getChargeFlag() == null || order.getChargeFlag() != 1
                    || order.getChargeAmount() == null || order.getChargeAmount().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("维修退货必须填写加工厂向我方收取的维修费（收费类型 + 金额大于 0）");
        } else if (order.getChargeFlag() != null && order.getChargeFlag() == 1) {
            throw new BusinessException("加工退货不产生工厂收费（不良是工厂的问题，加工厂不向我方收费）");
        }
        Object productsObj = body.get("products");
        boolean hasProduct = false;
        if (productsObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> pm && toBigDecimal(pm.get("quantity")).compareTo(BigDecimal.ZERO) > 0) {
                    hasProduct = true;
                    break;
                }
            }
        }
        if (!hasProduct) throw new BusinessException("请添加退货成品（至少一行数量大于 0）");
    }

    /**
     * 编辑草稿（E4 · 2026-09-12 补端点）。
     * <p>草稿阶段不产生库存/应付副作用，故明细可整体替换（删旧插新）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ReturnOrder order, Map<String, Object> body) {
        ReturnOrder exist = returnOrderMapper.selectById(id);
        if (exist == null) throw new BusinessException("退货单不存在");
        // 用 DRAFT→DRAFT 的条件更新充当"草稿才可编辑"的原子护栏（并发编辑/编辑与审核并发时只有一个能过）
        if (!DocStatusGuard.claim(returnOrderMapper, ReturnOrder::getId, id,
                ReturnOrder::getStatus, DocStatus.DRAFT.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("只有草稿状态可编辑");
        }
        // 退货类型：不传保持原值（草稿可改类型，改后校验按新类型走）
        String type = OutsourceReturnType.normalize(order.getReturnType() == null ? exist.getReturnType() : order.getReturnType());
        // 2026-09-21：退回加工厂已统一到「加工退货」页 ⇒ 不允许把维修退货改成加工退货
        // （存量加工退货草稿保持原类型仍可编辑，故只拦"改类型"这一种）
        if (!OutsourceReturnType.isRepair(type) && OutsourceReturnType.isRepair(exist.getReturnType()))
            throw new BusinessException("退回加工厂请到「加工退货」页办理（有加工单走该单收货详细页、没有加工单走该页的「新增无单加工退货」），不能再把维修退货改成加工退货");
        order.setReturnType(type);
        // 收费字段先规范化，再做类型专属校验
        normalizeCharge(order, body);
        // 表头：单号/状态不可改；仓库不传则保持原值（MP updateById 会忽略 null）
        order.setId(id);
        order.setCode(exist.getCode());
        order.setStatus(DocStatus.DRAFT.getCode());
        Object invWhObj = body.get("warehouseId");
        if (invWhObj != null && !invWhObj.toString().isBlank()) order.setWarehouseId(Long.valueOf(invWhObj.toString()));
        order.setRemark((String) body.get("remark"));
        validateByType(type, order, body);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        returnOrderMapper.updateById(order);

        // 明细整体替换
        returnOrderItemMapper.delete(new LambdaQueryWrapper<ReturnOrderItem>()
                .eq(ReturnOrderItem::getReturnOrderId, id));
        returnProductMapper.delete(new LambdaQueryWrapper<OutsourceReturnOrderProduct>()
                .eq(OutsourceReturnOrderProduct::getReturnOrderId, id));
        saveDetailLines(order, body, cid);
    }

    /** 保存明细（退货物料 FIFO 计价 + 退货成品）：create / update 共用，避免两处口径漂移（E4 抽出） */
    private void saveDetailLines(ReturnOrder order, Map<String, Object> body, Long cid) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsRaw = (List<Map<String, Object>>) body.get("items");
        // 维修退货：不还料（料账与维修无关），有明细直接拒绝，避免"送修却还了料"的错账
        if (OutsourceReturnType.isRepair(order.getReturnType())) {
            if (itemsRaw != null && !itemsRaw.isEmpty())
                throw new BusinessException("维修退货不涉及退货物料（BOM 还料），请清除物料明细");
            itemsRaw = null;
        }
        // 加工退货：明细按 BOM 快照带出，**允许为空**（包工包料产品无物料，或该产品无 BOM 快照时只退成品）
        if (itemsRaw == null) itemsRaw = new ArrayList<>();
        // 保存退货物料明细（FIFO 价，草稿阶段不动库存/应付）
        for (Map<String, Object> it : itemsRaw) {
            BigDecimal qty = toBigDecimal(it.get("quantity"));
            Long matId = toLong(it.get("materialId"));
            BigDecimal price = calcFifoPrice(matId, qty);

            ReturnOrderItem item = new ReturnOrderItem();
            item.setReturnOrderId(order.getId());
            item.setMaterialId(matId);
            item.setMaterialTypeId(toLong(it.get("materialTypeId")));
            item.setUnit((String) it.get("unit"));
            item.setQuantity(qty);
            item.setUnitPrice(price);
            item.setAmount(qty.multiply(price));
            item.setRemark((String) it.get("remark"));
            if (cid != null && cid > 0) item.setCompanyId(cid);
            returnOrderItemMapper.insert(item);
        }

        // 保存退货成品明细（审核时扣减成品库存）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) body.get("products");
        if (products != null) {
            for (Map<String, Object> p : products) {
                BigDecimal qty = toBigDecimal(p.get("quantity"));
                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                OutsourceReturnOrderProduct prod = new OutsourceReturnOrderProduct();
                prod.setReturnOrderId(order.getId());
                // 产品维度统一落**产品主数据ID**：前端可能传订单产品行ID，这里解析后落库
                Long masterId = resolveStockProductId(order, toLong(p.get("productId")), toLong(p.get("productMasterId")));
                prod.setProductId(masterId);
                // BOM 快照（2026-09-17）：**关联了加工单就以该订单所用快照为准（用户要求：不允许手改）**，
                // 未关联加工单时用前端选的快照；两者都取不到则留空（该产品无 BOM 时不带料）
                Long snapshotId = snapshotIdOfOrderProduct(order.getOrderId(), masterId);
                if (snapshotId == null) snapshotId = toLong(p.get("bomSnapshotId"));
                prod.setBomSnapshotId(snapshotId);
                prod.setProductName((String) p.get("productName"));
                prod.setQuantity(qty);
                prod.setQualityType(normalizeQualityType(p.get("qualityType")));
                if (cid != null && cid > 0) prod.setCompanyId(cid);
                returnProductMapper.insert(prod);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        ReturnOrder order = returnOrderMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        // P2-29：原子抢占 DRAFT→AUDITED，避免并发/双击重复扣料与重复生成应付
        if (!DocStatusGuard.claim(returnOrderMapper, ReturnOrder::getId, id,
                ReturnOrder::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("只有草稿状态可审核");
        }

        boolean repair = OutsourceReturnType.isRepair(order.getReturnType());
        // ⚠️ P3-1 收敛留痕（2026-09-25）：非 repair（DEFECT）分支为**存量历史单专用**（新增入口已封死：
        // create 只收 REPAIR；存量 DRAFT=0、AUDITED=3 张）。其落账口径（还料+扣成品+负应付、无 PRODUCT_DEFECT
        // 转移腿）**勿对齐** P1-1 的无单红冲新口径 —— AUDITED 3 张若反审核，必须走与历史审核严格对称的原逆向。
        List<ReturnOrderItem> items = returnOrderItemMapper.selectList(
            new LambdaQueryWrapper<ReturnOrderItem>().eq(ReturnOrderItem::getReturnOrderId, id));
        // 2026-09-17：维修退货不还料（明细必须为空）；加工退货允许无明细（包工包料 / 该产品无 BOM 快照 → 只退成品）
        if (repair && !items.isEmpty())
            throw new BusinessException("维修退货不涉及退货物料，请先清除物料明细");
        // P2-33：数量必须为正；加工厂必须存在（否则找不到工厂委外仓会静默跳过退料入库）
        for (ReturnOrderItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("退货数量必须大于 0（明细行ID=" + it.getId() + "）");
        }
        if (order.getFactoryId() == null || supplierMapper.selectById(order.getFactoryId()) == null)
            throw new BusinessException("加工厂不存在：ID=" + order.getFactoryId());
        // 维修退货：必须由加工厂向我方收费（草稿校验之外再兜一层，防历史/直改库数据绕过）
        if (repair && (order.getChargeFlag() == null || order.getChargeFlag() != 1
                || order.getChargeAmount() == null || order.getChargeAmount().compareTo(BigDecimal.ZERO) <= 0))
            throw new BusinessException("维修退货必须填写加工厂向我方收取的维修费（收费类型 + 金额大于 0）");

        // F2-2（2026-09-18 审核修复）：复核「累计退货量 ≤ 已交货量」，防直调接口超退（详见审核报告 F2-2）
        assertReturnNotOverDelivered(order);

        // 工厂委外仓（物料退回目标仓）
        // F7-78（2026-09-20）：工厂存在但**无委外仓**时必须**显式报错**。原实现是 `factoryWhId != null`
        // 才入库、否则**静默跳过** ⇒ 成品出库与应付照旧生成、退货物料却不入库 ⇒ 料账不符。
        // 口径对齐同模块的 CloseReportServiceImpl.confirmClose（"该加工厂未配置委外仓库"）。
        if (order.getFactoryId() == null)
            throw new BusinessException("退货单缺少加工厂，无法审核");
        List<Warehouse> factoryWhList = warehouseMapper.selectList(
            new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getFactoryId, order.getFactoryId())
                // F7-81①（2026-09-20）：口径收紧为**委外仓**（原只按 factory_id ⇒ 该工厂若另有成品仓会取错仓；
                // 现网每个工厂仅有 1 个 OUTSOURCE 仓 ⇒ 行为不变）
                .eq(Warehouse::getWarehouseCategory, com.beichen.erp.warehouse.common.WarehouseCategory.OUTSOURCE.getCode())
                .orderByAsc(Warehouse::getId));
        if (factoryWhList.isEmpty())
            throw new BusinessException("该加工厂未配置委外仓库，无法审核退货单（请先在【委外仓库】页面为该工厂创建委外仓库）");
        Long factoryWhId = factoryWhList.get(0).getId();
        Long invWhId = order.getWarehouseId();

        // 1. 退货物料入工厂委外仓 + 流水
        BigDecimal totalReturnAmount = BigDecimal.ZERO;
        for (ReturnOrderItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            // factoryWhId 已由上方保证非空（F7-78）
            if (it.getMaterialId() != null) {
                updateOutsourceStock(factoryWhId, it.getMaterialId(), it.getQuantity(), QualityType.GOOD.getCode(), StockChangeType.RETURN_IN.getCode(), order.getCode(), order.getId());
            }
            if (it.getAmount() != null) totalReturnAmount = totalReturnAmount.add(it.getAmount());
        }

        // 2. 成品从我方仓减少（维修退货=送修出库，用维修流水；加工退货沿用委外退料流水）
        StockChangeType outType = repair ? StockChangeType.OUTSOURCE_REPAIR_OUT : StockChangeType.OUTSOURCE_RETURN_OUT;
        RelatedBillType outBill = repair ? RelatedBillType.OUTSOURCE_REPAIR : RelatedBillType.OUTSOURCE_RETURN;
        if (invWhId != null) {
            List<OutsourceReturnOrderProduct> products = returnProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceReturnOrderProduct>().eq(OutsourceReturnOrderProduct::getReturnOrderId, id));
            for (OutsourceReturnOrderProduct p : products) {
                if (p.getQuantity() == null || p.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
                // 产品按主数据ID落账（历史数据存的可能是订单产品行ID，这里兜底解析）；
                // 规格按明细上的 quality_type 扣减（历史数据为空时按 A 规）
                Long pid = resolveStockProductId(order, p.getProductId(), null);
                String qt = normalizeQualityType(p.getQualityType());
                warehouseStockService.changeStock(invWhId,
                        pid, p.getQuantity().negate(),
                        outType, order.getCode(), outBill,
                        "", order.getId(), qt);   // F7-65①：spec 按约定传 ""（原传 null）
                // P2-1（2026-09-25）：送修 = 成品（维修退货）**转移**进加工厂委外仓（PRODUCT_REPAIR 形态），
                // 修好送回前可按仓查询与盘点；核销走维修返回登记（CANCEL_OUTSOURCE_REPAIR_STOCK_IN）
                if (repair) {
                    warehouseStockService.changeStock(factoryWhId, pid, p.getQuantity(),
                            StockChangeType.OUTSOURCE_REPAIR_STOCK_IN, order.getCode(), outBill,
                            "", order.getId(), qt, WarehouseStock.FORM_PRODUCT_REPAIR);
                }
            }
        } else {
            // F2-1（2026-09-18 审核修复）：两类退货都**必须**先选仓才能审核 ——
            // 加工退货原先在未选仓时"静默跳过成品扣减"，却照旧执行「退货物料入工厂委外仓 + 负向应付」
            // ⇒ 料账/应付动了、成品库存没动，账实不符（前端已必填，此处是接口层兜底）。
            throw new BusinessException(repair
                    ? "请选择送修出库仓（我方成品仓）"
                    : "请选择成品出库仓（我方成品仓）");
        }

        // 3. 应付冲减（负向应付）：仅加工退货；维修退货不冲减（维修不是退货，加工费照付）
        if (totalReturnAmount.compareTo(BigDecimal.ZERO) > 0) {
            payableHelper.createPayable(order.getFactoryId(), SourceBillType.OUTSOURCE_RETURN.getCode(),
                order.getCode(), order.getId(), totalReturnAmount.negate(), order.getReturnDate(),
                "委外退料 - " + order.getCode());
        }

        // 4. 收费应付（正向）：**加工厂向我方收取**的费用（我方付加工厂）
        //    维修退货 → OUTSOURCE_REPAIR_CHARGE「委外维修收费」（单独分账，便于与加工退货对账）
        //    加工退货 → OUTSOURCE_RETURN_CHARGE（保留历史口径；新建时已禁止收费）
        if (order.getChargeFlag() != null && order.getChargeFlag() == 1
                && order.getChargeAmount() != null && order.getChargeAmount().compareTo(BigDecimal.ZERO) > 0) {
            String chargeType = repair ? SourceBillType.OUTSOURCE_REPAIR_CHARGE.getCode()
                    : SourceBillType.OUTSOURCE_RETURN_CHARGE.getCode();
            String chargeMemo = (repair ? "委外维修收费（加工厂向我方收取）" : "委外加工退货收费（加工厂向我方收取）")
                    + (order.getChargeReason() != null && !order.getChargeReason().isBlank()
                        ? "：" + order.getChargeReason() : "");
            payableHelper.createPayable(order.getFactoryId(), chargeType,
                order.getCode(), order.getId(), order.getChargeAmount(), order.getReturnDate(), chargeMemo);
        }

        // 5. 更新状态与审计
        ReturnOrder u = new ReturnOrder();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditorId(getCurrentUserId());
        u.setAuditorName(getCurrentUserName());
        u.setAuditTime(LocalDateTime.now());
        returnOrderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        ReturnOrder order = returnOrderMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        // P2-29：原子抢占 AUDITED→DRAFT，避免并发反审核重复回滚库存与应付
        if (!DocStatusGuard.claim(returnOrderMapper, ReturnOrder::getId, id,
                ReturnOrder::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("只有已审核状态可取消审核");
        }

        boolean repair = OutsourceReturnType.isRepair(order.getReturnType());
        // 维修退货：已结案的不能反审核（需先撤销结案，2026-09-17）
        if (repair && nzInt(order.getClosedFlag()) == 1)
            throw new BusinessException("该单已结案，请先「撤销结案」再反审核");
        // 维修退货：已登记过"维修返回"的不能反审核（返回的货已入库，反审核会把送修量收回 → 账实错位）
        if (repair && repairMapper.selectCount(new LambdaQueryWrapper<OutsourceReturnOrderRepair>()
                .eq(OutsourceReturnOrderRepair::getReturnOrderId, id)) > 0) {
            throw new BusinessException("该单已有维修返回记录，请先撤销全部维修返回再反审核");
        }

        // 工厂委外仓
        Long whId = null;
        if (order.getFactoryId() != null) {
            List<Warehouse> whs = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getFactoryId, order.getFactoryId()));
            whId = whs.isEmpty() ? null : whs.get(0).getId();
        }
        // 1. 物料逆向（从工厂委外仓扣回）—— 维修退货无物料明细，循环自然为空
        List<ReturnOrderItem> items = returnOrderItemMapper.selectList(
            new LambdaQueryWrapper<ReturnOrderItem>().eq(ReturnOrderItem::getReturnOrderId, id));
        for (ReturnOrderItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            if (whId != null && it.getMaterialId() != null) {
                updateOutsourceStock(whId, it.getMaterialId(), it.getQuantity().negate(), QualityType.GOOD.getCode(), StockChangeType.CANCEL_RETURN_IN.getCode(), order.getCode(), order.getId());
            }
        }
        // 2. 成品逆向（恢复我方成品库存；维修退货=撤销送修出库）
        StockChangeType backType = repair ? StockChangeType.OUTSOURCE_REPAIR_OUT_UN_AUDIT
                : StockChangeType.OUTSOURCE_RETURN_OUT_UN_AUDIT;
        RelatedBillType backBill = repair ? RelatedBillType.OUTSOURCE_REPAIR : RelatedBillType.OUTSOURCE_RETURN;
        Long invWhId = order.getWarehouseId();
        // P2-1（2026-09-25）：送修反审核需核销"成品（维修退货）在厂行"，必须按**委外仓**定位（与审核同口径）
        Long repairFactoryWhId = null;
        if (repair && order.getFactoryId() != null) {
            List<Warehouse> outWhs = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                    .eq(Warehouse::getFactoryId, order.getFactoryId())
                    .eq(Warehouse::getWarehouseCategory, com.beichen.erp.warehouse.common.WarehouseCategory.OUTSOURCE.getCode())
                    .orderByAsc(Warehouse::getId));
            repairFactoryWhId = outWhs.isEmpty() ? null : outWhs.get(0).getId();
        }
        if (invWhId != null) {
            List<OutsourceReturnOrderProduct> products = returnProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceReturnOrderProduct>().eq(OutsourceReturnOrderProduct::getReturnOrderId, id));
            for (OutsourceReturnOrderProduct p : products) {
                if (p.getQuantity() == null || p.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
                Long pid = resolveStockProductId(order, p.getProductId(), null);
                String qt = normalizeQualityType(p.getQualityType());
                warehouseStockService.changeStock(invWhId,
                        pid, p.getQuantity(),
                        backType, order.getCode(), backBill,
                        "", order.getId(), qt);   // F7-65①：spec 按约定传 ""（原传 null）
                // P2-1：对称核销在厂 PRODUCT_REPAIR 行。⚠️ 存量兼容：改造前审核的旧单**从未入过厂**
                //（在厂行不存在）⇒ 审核时没做的动作反审核也不做，跳过并留痕；行存在但数量不足 = 真错账 ⇒ 硬报错。
                if (repair && repairFactoryWhId != null) {
                    BigDecimal onSite = warehouseStockService.getQuantity(repairFactoryWhId, pid, qt, WarehouseStock.FORM_PRODUCT_REPAIR);
                    if (onSite.compareTo(BigDecimal.ZERO) > 0) {
                        if (onSite.compareTo(p.getQuantity()) < 0)
                            throw new BusinessException("在厂成品（维修退货）不足，无法反审核：规格 " + qt
                                    + " 当前在厂 " + onSite + "、需核销 " + p.getQuantity());
                        warehouseStockService.changeStock(repairFactoryWhId, pid, p.getQuantity().negate(),
                                StockChangeType.CANCEL_OUTSOURCE_REPAIR_STOCK_IN, order.getCode(), backBill,
                                "", order.getId(), qt, WarehouseStock.FORM_PRODUCT_REPAIR);
                    } else {
                        log.warn("送修反审核：旧单（改造前审核）无在厂 PRODUCT_REPAIR 行，跳过核销腿 code={} productId={} qty={}",
                                order.getCode(), pid, p.getQuantity());
                    }
                }
            }
        }
        // 3. 冲销应付：加工退货=退料负向 + 退货收费正向；维修退货=仅维修收费正向
        if (repair) {
            payableHelper.reversePayable(id, SourceBillType.OUTSOURCE_REPAIR_CHARGE.getCode());
        } else {
            payableHelper.reversePayable(id,
                    SourceBillType.OUTSOURCE_RETURN.getCode(), SourceBillType.OUTSOURCE_RETURN_CHARGE.getCode());
        }
        // 4. 回草稿
        // 审核信息必须用 UpdateWrapper 显式置 null：updateById 忽略 null 字段，反审核后仍显示审核人/时间
        returnOrderMapper.update(null, new LambdaUpdateWrapper<ReturnOrder>()
                .eq(ReturnOrder::getId, id)
                .set(ReturnOrder::getStatus, DocStatus.DRAFT.getCode())
                .set(ReturnOrder::getAuditorId, null)
                .set(ReturnOrder::getAuditorName, null)
                .set(ReturnOrder::getAuditTime, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        ReturnOrder order = returnOrderMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        // F7-50（2026-09-19）：原子抢占 DRAFT→CANCELLED（原"先查后改"可与 audit 并发互覆）
        if (!DocStatusGuard.claim(returnOrderMapper, ReturnOrder::getId, id,
                ReturnOrder::getStatus, DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废");
    }

    // ===== 维修返回（维修退货单的"回来"腿，2026-09-17） =====

    /**
     * 登记维修返回：维修退货单已审核（货已送工厂）后，工厂修好分批送回。
     * <p>登记即生效：成品入我方指定仓（`OUTSOURCE_REPAIR_IN`），**不产生任何应付**；
     * 数量按「产品主数据ID + 规格」校验不超过送修量。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void repairReturn(Long id, Map<String, Object> body) {
        // F7-138（2026-09-20）：**行锁**（FOR UPDATE，带租户）。本方法按「送修量 − 已返回量」核销后**直接入库**
        // （changeStock，OUTSOURCE_REPAIR_IN），是典型"先查后写"：并发双击时两个请求读到**相同的** returned
        // ⇒ 双双通过"不超过送修量"的校验 ⇒ **重复入库、超出送修量**，且无任何唯一索引能兜住。
        // 加锁后同单串行：第二个请求会读到已累计的 returned 而被拦。
        Long lockCid = CompanyContext.get();
        if (lockCid != null && lockCid <= 0) lockCid = null;
        ReturnOrder order = returnOrderMapper.selectForUpdate(id, lockCid);
        if (order == null) throw new BusinessException("退货单不存在");
        if (!OutsourceReturnType.isRepair(order.getReturnType()))
            throw new BusinessException("只有维修退货单可以登记维修返回");
        if (!DocStatus.AUDITED.getCode().equals(order.getStatus()))
            throw new BusinessException("只有已审核（已送修）的维修退货单才能登记维修返回");
        if (nzInt(order.getClosedFlag()) == 1)
            throw new BusinessException("该单已结案，如需继续登记请先「撤销结案」");

        Long whId = toLong(body.get("warehouseId"));
        if (whId == null) throw new BusinessException("请选择返回入库仓");
        Warehouse wh = warehouseMapper.selectById(whId);
        if (wh == null) throw new BusinessException("返回入库仓不存在");
        if (!"INVENTORY".equalsIgnoreCase(wh.getWarehouseCategory()))
            throw new BusinessException("返回入库仓必须是我方仓库（自有仓）");

        Object dateObj = body.get("repairDate");
        LocalDate repairDate = (dateObj != null && !dateObj.toString().isBlank())
                ? LocalDate.parse(dateObj.toString()) : LocalDate.now();

        List<Map<String, Object>> lines = asListMap(body.get("items"));
        if (lines.isEmpty()) throw new BusinessException("请填写维修返回数量");

        // 按**产品**核销送修/返回数量（不按规格）：送修的是"不良品"，修好回来通常是"A规"等良品，
        // 用「产品+规格」做键会把正常业务拦死（2026-09-17 实测修正）。
        Map<Long, BigDecimal> sent = sentQtyByProduct(id);
        Map<Long, BigDecimal> returned = returnedQtyByProduct(id);
        Long cid = CompanyContext.get();
        // P2-1（2026-09-25）：加工厂委外仓（在厂核销/用料扣减目标仓）。旧数据 factoryId 为空或未配委外仓 ⇒
        // 跳过 P2-1 新腿（存量单审核时也没入过厂），保持原两腿行为。
        Long factoryWhId = null;
        if (order.getFactoryId() != null) {
            List<Warehouse> outWhs = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                    .eq(Warehouse::getFactoryId, order.getFactoryId())
                    .eq(Warehouse::getWarehouseCategory, com.beichen.erp.warehouse.common.WarehouseCategory.OUTSOURCE.getCode())
                    .orderByAsc(Warehouse::getId));
            factoryWhId = outWhs.isEmpty() ? null : outWhs.get(0).getId();
        }
        List<OutsourceReturnOrderRepair> savedRows = new ArrayList<>();
        int saved = 0;
        for (Map<String, Object> line : lines) {
            BigDecimal qty = toBigDecimal(line.get("quantity"));
            // F7-79（2026-09-20）：产品ID**必须归一**（与 sentQtyByProduct 同口径）。
            // 原实现直接用入参 productId ⇒ 若前端传的是「订单产品行ID」而核销键是「产品主数据ID」，
            // 既会把库存记到错的产品上，又让 returnedQtyByProduct 的键与 sent 的键不是同一套 ⇒ 核销失效。
            Long productId = resolveStockProductId(order, toLong(line.get("productId")), toLong(line.get("productMasterId")));
            if (productId == null || qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            String quality = normalizeQualityType(line.get("qualityType"));
            BigDecimal sentQty = sent.getOrDefault(productId, BigDecimal.ZERO);
            if (sentQty.compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("该产品不在本单送修范围内（产品ID=" + productId + "）");
            BigDecimal already = returned.getOrDefault(productId, BigDecimal.ZERO);
            if (already.add(qty).compareTo(sentQty) > 0)
                throw new BusinessException("返回数量超过送修数量（产品ID=" + productId + "：送修 " + sentQty
                        + "、已返回 " + already + "、本次 " + qty + "）");

            warehouseStockService.changeStock(whId, productId, qty,
                    StockChangeType.OUTSOURCE_REPAIR_IN, order.getCode(), RelatedBillType.OUTSOURCE_REPAIR,
                    "", order.getId(), quality);   // F7-65①：spec 按约定传 ""（原传 null）

            OutsourceReturnOrderRepair row = new OutsourceReturnOrderRepair();
            row.setReturnOrderId(id);
            row.setRepairDate(repairDate);
            row.setWarehouseId(whId);
            row.setProductId(productId);
            row.setProductName((String) line.get("productName"));
            row.setQualityType(quality);
            row.setQuantity(qty);
            row.setRemark((String) line.get("remark"));
            if (cid != null && cid > 0) row.setCompanyId(cid);
            repairMapper.insert(row);
            saved++;
            savedRows.add(row);
            // P2-1（2026-09-25）：核销在厂成品（PRODUCT_REPAIR 行，按规格行分配扣减 + 落 ALLOC 明细）
            if (factoryWhId != null) {
                allocateOnSiteRepair(order, factoryWhId, productId, row.getProductName(), qty, row, cid);
            }
            returned.put(productId, already.add(qty)); // 同一请求内多行也要累计，避免叠加超退
        }
        if (saved == 0) throw new BusinessException("请填写维修返回数量");

        // P2-1（2026-09-25）：实际用料多行（**可超 BOM**，不做 BOM 比对）从委外仓扣减（允许扣负——工厂已实际耗用）
        // + FIFO 计价快照 + 料款成本按行数量比例结转到回仓成品。**无赔料应收**（我方责任；charge* 维修费应付另行保持）。
        if (factoryWhId != null) {
            List<Map<String, Object>> mats = asListMap(body.get("materials"));
            if (!mats.isEmpty()) {
                BigDecimal totalMat = BigDecimal.ZERO;
                for (Map<String, Object> mat : mats) {
                    Long materialId = toLong(mat.get("materialId"));
                    BigDecimal mq = toBigDecimal(mat.get("quantity"));
                    if (materialId == null || mq == null || mq.compareTo(BigDecimal.ZERO) <= 0) continue;
                    stockServiceChangeMaterial(factoryWhId, materialId, mq.negate(), order.getCode(), id);
                    BigDecimal unit = pricingService.fifoPriceWithFallback(materialId, mq);
                    BigDecimal amount = unit.multiply(mq).setScale(2, RoundingMode.HALF_UP);
                    OutsourceReturnOrderRepairItem it = new OutsourceReturnOrderRepairItem();
                    it.setRepairRecordId(savedRows.get(0).getId());
                    it.setItemType(OutsourceReturnOrderRepairItem.TYPE_MATERIAL);
                    it.setMaterialId(materialId);
                    it.setMaterialName(getMaterialNameById(materialId));
                    OutsourceMaterial m0 = materialId != null ? outsourceMaterialMapper.selectById(materialId) : null;
                    it.setUnit(m0 != null ? m0.getUnit() : null);
                    it.setQuantity(mq);
                    it.setUnitPrice(unit);
                    it.setAmount(amount);
                    if (cid != null && cid > 0) it.setCompanyId(cid);
                    repairItemMapper.insert(it);
                    totalMat = totalMat.add(amount);
                }
                // 成本结转：Σ用料 FIFO 按各返回行数量占比摊入回仓成品（移动加权；撤销按记录 reverseByBill）
                if (totalMat.compareTo(BigDecimal.ZERO) > 0 && !savedRows.isEmpty()) {
                    BigDecimal totalQty = savedRows.stream().map(r -> r.getQuantity() == null ? BigDecimal.ZERO : r.getQuantity())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal unitCost = totalMat.divide(totalQty, 4, RoundingMode.HALF_UP);
                        for (OutsourceReturnOrderRepair r : savedRows) {
                            costService.applyProduct(r.getProductId(), r.getQuantity(), unitCost,
                                    StockChangeType.OUTSOURCE_REPAIR_IN.getCode(), r.getId(), order.getCode());
                        }
                    }
                }
            }
        }
    }

    /**
     * P2-1：核销在厂成品（维修退货）——按该产品在厂 PRODUCT_REPAIR 各规格行（id 升序）分配扣减，
     * 每笔扣减落一条 ALLOC 明细（撤销时按行规格精确恢复）。
     * <p>⚠️ 存量兼容：旧单（改造前审核）从未入过厂 ⇒ 在厂行不存在时跳过核销腿并留痕（动作对称）；
     * 行存在但数量不足 = 真错账 ⇒ 硬报错。</p>
     */
    private void allocateOnSiteRepair(ReturnOrder order, Long factoryWhId, Long productId,
                                      String productName, BigDecimal qty, OutsourceReturnOrderRepair row, Long cid) {
        List<WarehouseStock> onSiteRows = warehouseStockMapper.selectList(new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, factoryWhId)
                .eq(WarehouseStock::getProductId, productId)
                .eq(WarehouseStock::getStockForm, WarehouseStock.FORM_PRODUCT_REPAIR)
                .gt(WarehouseStock::getQuantity, BigDecimal.ZERO)
                .orderByAsc(WarehouseStock::getId));
        BigDecimal onSite = onSiteRows.stream()
                .map(r -> r.getQuantity() == null ? BigDecimal.ZERO : r.getQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (onSite.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("维修返回：旧单（改造前审核）无在厂 PRODUCT_REPAIR 行，跳过核销腿 code={} productId={} qty={}",
                    order.getCode(), productId, qty);
            return;
        }
        if (onSite.compareTo(qty) < 0)
            throw new BusinessException("在厂成品（维修退货）不足：当前 " + onSite + "、需核销 " + qty);
        BigDecimal remain = qty;
        for (WarehouseStock r : onSiteRows) {
            if (remain.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal take = r.getQuantity().min(remain);
            warehouseStockService.changeStock(factoryWhId, productId, take.negate(),
                    StockChangeType.CANCEL_OUTSOURCE_REPAIR_STOCK_IN, order.getCode(), RelatedBillType.OUTSOURCE_REPAIR,
                    "", order.getId(), r.getQualityType(), WarehouseStock.FORM_PRODUCT_REPAIR);
            OutsourceReturnOrderRepairItem it = new OutsourceReturnOrderRepairItem();
            it.setRepairRecordId(row.getId());
            it.setItemType(OutsourceReturnOrderRepairItem.TYPE_ALLOC);
            it.setProductId(productId);
            it.setProductName(productName);
            it.setQualityType(r.getQualityType());
            it.setQuantity(take);
            if (cid != null && cid > 0) it.setCompanyId(cid);
            repairItemMapper.insert(it);
            remain = remain.subtract(take);
        }
    }

    /** P2-1：用料增减（changeMaterialStockAllowNegative 带形态重载的统一薄封装：delta<0=扣减 / delta>0=撤销回补） */
    private void stockServiceChangeMaterial(Long factoryWhId, Long materialId, BigDecimal delta,
                                            String billNo, Long orderId) {
        String changeType = delta.compareTo(BigDecimal.ZERO) < 0
                ? StockChangeType.OUTSOURCE_REPAIR_MATERIAL.getCode()
                : StockChangeType.CANCEL_OUTSOURCE_REPAIR_MATERIAL.getCode();
        warehouseStockService.changeMaterialStockAllowNegative(factoryWhId, materialId, delta,
                changeType, billNo, RelatedBillType.OUTSOURCE_REPAIR, null, null, orderId, WarehouseStock.FORM_MATERIAL);
    }

    /** 撤销维修返回：把已入库成品扣回并删除该条记录（不校验单据状态，作废/草稿单也可清理） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelRepairReturn(Long repairRecordId) {
        OutsourceReturnOrderRepair row = repairMapper.selectById(repairRecordId);
        if (row == null) throw new BusinessException("维修返回记录不存在");
        // F7-138（2026-09-20）：对**来源单据**加行锁（与 repairReturn 同一把锁）⇒ 同单的登记/撤销串行；
        // 否则并发双击两次都读到该 row ⇒ 两次 changeStock(-qty) ⇒ **重复扣减库存**。
        Long lockCid = CompanyContext.get();
        if (lockCid != null && lockCid <= 0) lockCid = null;
        ReturnOrder order = returnOrderMapper.selectForUpdate(row.getReturnOrderId(), lockCid);
        if (order == null) throw new BusinessException("退货单不存在");
        if (nzInt(order.getClosedFlag()) == 1)
            throw new BusinessException("该单已结案，如需撤销返回请先「撤销结案」");
        if (row.getWarehouseId() != null && row.getProductId() != null
                && row.getQuantity() != null && row.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            warehouseStockService.changeStock(row.getWarehouseId(), row.getProductId(), row.getQuantity().negate(),
                    StockChangeType.CANCEL_OUTSOURCE_REPAIR_IN, order.getCode(), RelatedBillType.OUTSOURCE_REPAIR,
                    "", order.getId(), normalizeQualityType(row.getQualityType()));   // F7-65①：spec 按约定传 ""（原传 null）
        }
        // P2-1（2026-09-25）：按明细对称逆回 —— ALLOC 行恢复在厂 PRODUCT_REPAIR（同规格）、
        // MATERIAL 行等量回补委外仓物料；成本反结转（按记录 reverseByBill，与登记时 applyProduct 同单据ID）。
        List<OutsourceReturnOrderRepairItem> its = repairItemMapper.selectList(new LambdaQueryWrapper<OutsourceReturnOrderRepairItem>()
                .eq(OutsourceReturnOrderRepairItem::getRepairRecordId, repairRecordId)
                .orderByAsc(OutsourceReturnOrderRepairItem::getId));
        if (!its.isEmpty()) {
            Long factoryWhId = null;
            if (order.getFactoryId() != null) {
                List<Warehouse> outWhs = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                        .eq(Warehouse::getFactoryId, order.getFactoryId())
                        .eq(Warehouse::getWarehouseCategory, com.beichen.erp.warehouse.common.WarehouseCategory.OUTSOURCE.getCode())
                        .orderByAsc(Warehouse::getId));
                factoryWhId = outWhs.isEmpty() ? null : outWhs.get(0).getId();
            }
            if (factoryWhId == null) throw new BusinessException("该加工厂未配置委外仓库，无法撤销维修返回");
            for (OutsourceReturnOrderRepairItem it : its) {
                if (OutsourceReturnOrderRepairItem.TYPE_ALLOC.equals(it.getItemType())) {
                    warehouseStockService.changeStock(factoryWhId, it.getProductId(), it.getQuantity(),
                            StockChangeType.OUTSOURCE_REPAIR_STOCK_IN, order.getCode(), RelatedBillType.OUTSOURCE_REPAIR,
                            "", order.getId(), it.getQualityType(), WarehouseStock.FORM_PRODUCT_REPAIR);
                } else if (OutsourceReturnOrderRepairItem.TYPE_MATERIAL.equals(it.getItemType())) {
                    stockServiceChangeMaterial(factoryWhId, it.getMaterialId(), it.getQuantity(),
                            order.getCode(), order.getId());
                }
            }
            // 成本反结转（删除批次并反加权；必须在删除记录前、以记录ID 定位）
            costService.reverseByBill(StockChangeType.OUTSOURCE_REPAIR_IN.getCode(), repairRecordId);
            repairItemMapper.delete(new LambdaQueryWrapper<OutsourceReturnOrderRepairItem>()
                    .eq(OutsourceReturnOrderRepairItem::getRepairRecordId, repairRecordId));
        }
        // F7-138（2026-09-20）：**条件删除 + 判影响行数**（第二道防线，与上面的行锁互为保险）——
        // 只有真正删掉这一行的那次请求才算撤销成功；del==0 说明已被别处撤销 ⇒ 抛错 ⇒ 整个事务回滚
        // ⇒ 上面那次 changeStock(-qty) 一并撤销（原实现 deleteById 不看行数，第二次执行会"扣了库存却没删到东西"）。
        int del = repairMapper.deleteById(repairRecordId);
        if (del == 0) throw new BusinessException("该维修返回记录已被撤销，请刷新后重试");
    }

    // ===== 结案 / 撤销结案（维修退货的收尾动作，2026-09-17） =====

    /**
     * 结案：维修退货单中工厂送修的全部成品都已送回（未返回 = 0）后，人工确认收尾。
     * <p>只有维修退货需要结案（加工退货审核即终结：料入工厂仓 + 成品出库 + 负应付，没有"回来"腿）。
     * 结案后禁止再登记/撤销维修返回、禁止反审核，需先「撤销结案」。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        // F7-138（2026-09-20）：行锁 —— 结案前要算"未返回量"，与 repairReturn 的登记属同一组"先查后写"，
        // 统一在同一把锁下串行（避免"刚登记完就被结案"或重复结案这类竞争）。
        Long lockCid = CompanyContext.get();
        if (lockCid != null && lockCid <= 0) lockCid = null;
        ReturnOrder order = returnOrderMapper.selectForUpdate(id, lockCid);
        if (order == null) throw new BusinessException("退货单不存在");
        if (!OutsourceReturnType.isRepair(order.getReturnType())) throw new BusinessException("只有维修退货单需要结案");
        if (nzInt(order.getClosedFlag()) == 1) throw new BusinessException("该单已结案");
        if (!DocStatus.AUDITED.getCode().equals(order.getStatus()))
            throw new BusinessException("只有已审核（已送修）的维修退货单才能结案");
        BigDecimal unreturned = unreturnedQty(id);
        if (unreturned.compareTo(BigDecimal.ZERO) > 0)
            throw new BusinessException("还有 " + unreturned.stripTrailingZeros().toPlainString()
                    + " 件未返回，不能结案（工厂尚未修好送回）");
        // F7-138（2026-09-20）：加 `.eq(closedFlag, 0)` 条件 + 判影响行数（原为无条件更新 ⇒ 与 reOpen
        // 并发时"后写者胜"且不留痕）。现在与 reOpen 构成**对称**的条件更新。
        int upd = returnOrderMapper.update(null, new LambdaUpdateWrapper<ReturnOrder>()
                .eq(ReturnOrder::getId, id)
                .eq(ReturnOrder::getClosedFlag, 0)
                .set(ReturnOrder::getClosedFlag, 1)
                .set(ReturnOrder::getClosedTime, LocalDateTime.now())
                .set(ReturnOrder::getClosedBy, getCurrentUserName()));
        if (upd == 0) throw new BusinessException("该单状态已变化（可能已被结案），请刷新后重试");
    }

    /** 撤销结案：回到「送修中」跟踪状态（可继续登记维修返回） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reOpen(Long id) {
        Long lockCid = CompanyContext.get();
        if (lockCid != null && lockCid <= 0) lockCid = null;
        ReturnOrder order = returnOrderMapper.selectForUpdate(id, lockCid);
        if (order == null) throw new BusinessException("退货单不存在");
        if (nzInt(order.getClosedFlag()) != 1) throw new BusinessException("该单未结案");
        int upd = returnOrderMapper.update(null, new LambdaUpdateWrapper<ReturnOrder>()
                .eq(ReturnOrder::getId, id)
                .eq(ReturnOrder::getClosedFlag, 1)
                .set(ReturnOrder::getClosedFlag, 0)
                .set(ReturnOrder::getClosedTime, null)
                .set(ReturnOrder::getClosedBy, null));
        if (upd == 0) throw new BusinessException("该单状态已变化（可能已撤销结案），请刷新后重试");
    }

    /** 未返回量 = 送修合计（按产品） − 已返回合计 */
    private BigDecimal unreturnedQty(Long returnOrderId) {
        BigDecimal sent = sentQtyByProduct(returnOrderId).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returned = returnedQtyByProduct(returnOrderId).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sent.subtract(returned).max(BigDecimal.ZERO);
    }

    /** 送修量：按产品主数据ID合计本单退货成品（历史数据里的订单产品行ID会先解析成主数据ID） */
    private Map<Long, BigDecimal> sentQtyByProduct(Long returnOrderId) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (OutsourceReturnOrderProduct p : returnProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceReturnOrderProduct>().eq(OutsourceReturnOrderProduct::getReturnOrderId, returnOrderId))) {
            Long pid = resolveStockProductId(null, p.getProductId(), null);
            if (pid == null) continue;
            map.merge(pid, nz(p.getQuantity()), BigDecimal::add);
        }
        return map;
    }

    /** 已返回量：按产品主数据ID合计已登记的维修返回（返回品质可与送修品质不同，故不参与键） */
    private Map<Long, BigDecimal> returnedQtyByProduct(Long returnOrderId) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (OutsourceReturnOrderRepair r : repairMapper.selectList(
                new LambdaQueryWrapper<OutsourceReturnOrderRepair>().eq(OutsourceReturnOrderRepair::getReturnOrderId, returnOrderId))) {
            if (r.getProductId() == null) continue;
            // F7-79（2026-09-20）：**读取侧同样归一** —— 历史行里可能存的是"订单产品行ID"，
            // 而归一后的键是"产品主数据ID"。若不归一，sent(主数据ID) 与 returned(行ID) 不是同一套键
            // ⇒ "已返回量"恒为 0 ⇒ 送修量校验失效 ⇒ 可超量登记维修返回（返回是入库 +
            // 且无 changeStock 的 >=0 护栏兜底）。
            Long pid = resolveStockProductId(null, r.getProductId(), null);
            if (pid == null) continue;
            map.merge(pid, nz(r.getQuantity()), BigDecimal::add);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asListMap(Object o) {
        return o instanceof List ? (List<Map<String, Object>>) o : new ArrayList<>();
    }

    @Override
    public BigDecimal fifoPrice(Long materialId, BigDecimal qty) {
        return calcFifoPrice(materialId, qty);
    }

    @Override
    public List<Map<String, Object>> orderProducts(Long factoryId) {
        // 查该工厂**所有**加工单（2026-09-17 放开状态限制）：加工退货可以不关联加工订单，
        // 但退货物料要按「BOM 快照」带出 —— 只要该工厂加工过这个产品，就能取到当时的 BOM 用量快照。
        // 2026-09-17 再调（用户口径）：选项单位从「加工单」改为「BOM 快照」——
        //   同一份快照被多张加工单共享时**只出现一次**（不再"有 10 张单就重复 10 行"），
        //   并附带"用过它的加工单"，便于当关联了加工单时自动选中该单那一份快照。
        List<OutsourceOrder> orders = orderMapper.selectList(
            new LambdaQueryWrapper<OutsourceOrder>().eq(OutsourceOrder::getFactoryId, factoryId)
                .orderByDesc(OutsourceOrder::getCreateTime));
        Map<String, Map<String, Object>> productMap = new LinkedHashMap<>();
        for (OutsourceOrder o : orders) {
            List<OutsourceOrderProduct> prods = orderProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderProduct>().eq(OutsourceOrderProduct::getOrderId, o.getId()));
            for (OutsourceOrderProduct p : prods) {
                String pn = p.getProductName() != null ? p.getProductName() : "";
                // 无快照（该产品下加工单时没有 BOM）→ 没有可带出的料，不必出现在选项里
                if (pn.isBlank() || p.getBomSnapshotId() == null) continue;
                Map<String, Object> pm = productMap.computeIfAbsent(pn, k -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("productName", k);
                    // 产品主数据ID：库存按它查（同一产品所有快照一致）
                    x.put("productMasterId", null);
                    x.put("snapshots", new ArrayList<Map<String, Object>>());
                    x.put("_index", new LinkedHashMap<Long, Map<String, Object>>());
                    return x;
                });
                if (pm.get("productMasterId") == null) pm.put("productMasterId", p.getProductId());
                @SuppressWarnings("unchecked")
                Map<Long, Map<String, Object>> index = (Map<Long, Map<String, Object>>) pm.get("_index");
                Map<String, Object> snap = index.get(p.getBomSnapshotId());
                if (snap == null) {
                    BomSnapshot s = bomSnapshotMapper.selectById(p.getBomSnapshotId());
                    snap = new LinkedHashMap<>();
                    snap.put("snapshotId", p.getBomSnapshotId());
                    snap.put("bomVersion", s != null ? s.getBomVersion() : null);
                    snap.put("kind", s != null ? s.getKind() : null);
                    snap.put("itemCount", s != null ? s.getItemCount() : null);
                    snap.put("createTime", s != null ? s.getCreateTime() : null);
                    snap.put("orders", new ArrayList<Map<String, Object>>());
                    index.put(p.getBomSnapshotId(), snap);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> list = (List<Map<String, Object>>) pm.get("snapshots");
                    list.add(snap);
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> used = (List<Map<String, Object>>) snap.get("orders");
                Map<String, Object> u = new LinkedHashMap<>();
                u.put("orderId", o.getId());
                u.put("orderCode", o.getCode());
                u.put("status", o.getStatus());
                used.add(u);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(productMap.values());
        for (Map<String, Object> pm : result) {
            pm.remove("_index");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> snapshots = (List<Map<String, Object>>) pm.get("snapshots");
            // 版本号高的在前；同版本新的在前（下拉里"v1 / v2"的顺序）
            snapshots.sort((a, b) -> {
                Integer av = (Integer) a.get("bomVersion"), bv = (Integer) b.get("bomVersion");
                if (av != null || bv != null) {
                    if (av == null) return 1;
                    if (bv == null) return -1;
                    int c = bv.compareTo(av);
                    if (c != 0) return c;
                }
                return Long.compare((Long) b.get("snapshotId"), (Long) a.get("snapshotId"));
            });
        }
        return result;
    }

    /**
     * 从「成品收货」发起退货的预填数据（2026-09-17）。
     * <p>两种入口：①按交货记录（deliveryId）—— 带出工厂/成品出库仓/产品/各规格「已交-已退=可退」数量；
     * ②按加工单（orderId，列表行入口）—— 只带出工厂/出库仓/该单产品，数量留空由用户填写。</p>
     */
    @Override
    public Map<String, Object> returnPrefill(Long deliveryId, Long orderId) {
        OutsourceOrderDelivery d = deliveryId != null ? orderDeliveryMapper.selectById(deliveryId) : null;
        if (deliveryId != null && d == null) throw new BusinessException("收货记录不存在");
        Long oid = (d != null && d.getOrderId() != null) ? d.getOrderId() : orderId;
        if (oid == null) throw new BusinessException("缺少加工单或收货记录参数");
        OutsourceOrder o = orderMapper.selectById(oid);
        if (o == null) throw new BusinessException("加工单不存在");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourceDeliveryId", deliveryId);
        m.put("orderId", oid);
        m.put("orderCode", o.getCode());
        m.put("factoryId", o.getFactoryId());
        Supplier f = o.getFactoryId() != null ? supplierMapper.selectById(o.getFactoryId()) : null;
        m.put("factoryName", f != null ? f.getName() : "");
        // 成品出库仓：优先取该交货记录的收货仓；订单级入口取该单最近一条带仓的交货记录
        Long whId = d != null ? d.getWarehouseId() : null;
        if (whId == null) {
            List<OutsourceOrderDelivery> ds = orderDeliveryMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderDelivery>().eq(OutsourceOrderDelivery::getOrderId, oid)
                    .isNotNull(OutsourceOrderDelivery::getWarehouseId).orderByDesc(OutsourceOrderDelivery::getId));
            whId = ds.isEmpty() ? null : ds.get(0).getWarehouseId();
        }
        m.put("warehouseId", whId);
        Warehouse wh = whId != null ? warehouseMapper.selectById(whId) : null;
        m.put("warehouseName", wh != null ? wh.getWarehouseName() : "");

        ReturnOrder ctx = new ReturnOrder();
        ctx.setOrderId(oid);
        List<Map<String, Object>> lines = new ArrayList<>();
        if (d != null) {
            Long masterId = resolveStockProductId(ctx, d.getProductId(), d.getProductMasterId());
            OutsourceOrderProduct op = d.getProductId() != null ? orderProductMapper.selectById(d.getProductId()) : null;
            m.put("productId", d.getProductId());
            m.put("productMasterId", masterId);
            m.put("productName", op != null ? op.getProductName() : "");
            // 该单该产品所用的 BOM 快照（前端直接选中，2026-09-17）
            m.put("snapshotId", op != null ? op.getBomSnapshotId() : null);
            Map<String, BigDecimal> returned = returnedQtyByDelivery(deliveryId, masterId);
            BigDecimal a = nz(d.getAQty()), b = nz(d.getBQty()), c = nz(d.getCQty()), df = nz(d.getDefectQty());
            // 历史/手工数据的等级列为空时，把总数量按 A 规兜底（与审核落账口径一致）
            if (a.add(b).add(c).add(df).compareTo(BigDecimal.ZERO) == 0 && nz(d.getQuantity()).compareTo(BigDecimal.ZERO) > 0)
                a = nz(d.getQuantity());
            lines.add(prefillLine(ProductQualityType.A.getCode(), a, returned, masterId, d.getProductId(), op));
            lines.add(prefillLine(ProductQualityType.B.getCode(), b, returned, masterId, d.getProductId(), op));
            lines.add(prefillLine(ProductQualityType.C.getCode(), c, returned, masterId, d.getProductId(), op));
            lines.add(prefillLine(ProductQualityType.DEFECT.getCode(), df, returned, masterId, d.getProductId(), op));
        } else {
            for (OutsourceOrderProduct p : orderProductMapper.selectList(
                    new LambdaQueryWrapper<OutsourceOrderProduct>().eq(OutsourceOrderProduct::getOrderId, oid))) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("qualityType", ProductQualityType.A.getCode());
                line.put("productId", p.getId());
                line.put("productMasterId", p.getProductId());
                line.put("productName", p.getProductName());
                // 该产品在这张加工单里用的 BOM 快照（前端直接选中）
                line.put("snapshotId", p.getBomSnapshotId());
                line.put("deliveredQty", null);
                line.put("returnedQty", BigDecimal.ZERO);
                line.put("returnableQty", null); // 订单级入口：数量留空，由用户填写
                lines.add(line);
            }
        }
        m.put("lines", lines);
        return m;
    }

    /** 预填一行：可退 = 已交 − 已退（不为负）；数量为 0 的行由前端忽略 */
    private Map<String, Object> prefillLine(String qualityType, BigDecimal delivered, Map<String, BigDecimal> returned,
                                            Long masterId, Long orderProductId, OutsourceOrderProduct op) {
        BigDecimal done = returned.getOrDefault(qualityType, BigDecimal.ZERO);
        BigDecimal returnable = delivered.subtract(done);
        if (returnable.compareTo(BigDecimal.ZERO) < 0) returnable = BigDecimal.ZERO;
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("qualityType", qualityType);
        line.put("productId", orderProductId);
        line.put("productMasterId", masterId);
        line.put("productName", op != null ? op.getProductName() : "");
        line.put("deliveredQty", delivered);
        line.put("returnedQty", done);
        line.put("returnableQty", returnable);
        return line;
    }

    /** 已退货数量：按「来源交货记录（+产品主数据ID）+退回规格」汇总未作废的退货单 */
    private Map<String, BigDecimal> returnedQtyByDelivery(Long deliveryId, Long masterProductId) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        if (deliveryId == null) return map;
        List<ReturnOrder> orders = returnOrderMapper.selectList(new LambdaQueryWrapper<ReturnOrder>()
                .eq(ReturnOrder::getSourceDeliveryId, deliveryId)
                .ne(ReturnOrder::getStatus, DocStatus.CANCELLED.getCode()));
        if (orders.isEmpty()) return map;
        List<Long> ids = orders.stream().map(ReturnOrder::getId).toList();
        for (OutsourceReturnOrderProduct p : returnProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceReturnOrderProduct>().in(OutsourceReturnOrderProduct::getReturnOrderId, ids))) {
            Long pid = resolveStockProductId(null, p.getProductId(), null); // 兼容历史数据存的订单产品行ID
            if (masterProductId != null && pid != null && !masterProductId.equals(pid)) continue;
            String q = p.getQualityType() == null ? ProductQualityType.A.getCode() : p.getQualityType();
            map.merge(q, nz(p.getQuantity()), BigDecimal::add);
        }
        return map;
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** 去掉无意义的末尾 0，便于提示语展示 */
    private String fmt(BigDecimal v) { return v == null ? "0" : v.stripTrailingZeros().toPlainString(); }

    /** 交货记录的总数量：等级列之和，历史/手工数据等级全空时按总数量兜底（与审核落账口径一致） */
    private BigDecimal deliveryTotalQty(OutsourceOrderDelivery d) {
        BigDecimal sum = nz(d.getAQty()).add(nz(d.getBQty())).add(nz(d.getCQty())).add(nz(d.getDefectQty()));
        if (sum.compareTo(BigDecimal.ZERO) == 0 && nz(d.getQuantity()).compareTo(BigDecimal.ZERO) > 0)
            return nz(d.getQuantity());
        return sum;
    }

    /**
     * F2-2（2026-09-18 审核修复）：审核时复核「累计退货量 ≤ 已交货量」，与前端「可退量」同源但按**产品合计**。
     *
     * <p>三个关键口径：</p>
     * <ol>
     *   <li><b>按产品主数据ID合计，不按规格</b>：加工退货常把 A 规交来的货以 DEFECT 规格退回，
     *       若按「产品+规格」比对，DEFECT 已交量为 0 会把正常业务全部拦死；</li>
     *   <li><b>"已退"只统计已审核的其它退货单</b>（草稿未生效不该占用额度），且**必须排除本单** ——
     *       claim 已把本单置为 AUDITED，不排除就会把自己算进去（批 1 F1-1 的同类坑）；</li>
     *   <li><b>基准</b>：有来源交货记录 → 取该条交货量；只关联加工单 → 取该单已审交货量合计；
     *       两者都没有 → 无基准可依，跳过（仍由"库存不得为负"兜底）。</li>
     * </ol>
     */
    private void assertReturnNotOverDelivered(ReturnOrder order) {
        Long deliveryId = order.getSourceDeliveryId();
        Long orderId = order.getOrderId();
        if (deliveryId == null && orderId == null) return;
        List<OutsourceReturnOrderProduct> products = returnProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceReturnOrderProduct>()
                        .eq(OutsourceReturnOrderProduct::getReturnOrderId, order.getId()));
        if (products.isEmpty()) return;
        Map<Long, BigDecimal> thisQty = new LinkedHashMap<>();
        Map<Long, String> nameMap = new HashMap<>();
        for (OutsourceReturnOrderProduct p : products) {
            Long pid = resolveStockProductId(null, p.getProductId(), null);
            if (pid == null) continue;
            thisQty.merge(pid, nz(p.getQuantity()), BigDecimal::add);
            if (p.getProductName() != null && !p.getProductName().isBlank()) nameMap.put(pid, p.getProductName());
        }
        if (thisQty.isEmpty()) return;

        // 已交货量（按产品合计）
        Map<Long, BigDecimal> delivered = new LinkedHashMap<>();
        if (deliveryId != null) {
            OutsourceOrderDelivery d = orderDeliveryMapper.selectById(deliveryId);
            if (d != null) {
                Long pid = resolveStockProductId(null, d.getProductId(), d.getProductMasterId());
                BigDecimal q = deliveryTotalQty(d);
                if (pid != null && q.compareTo(BigDecimal.ZERO) > 0) delivered.merge(pid, q, BigDecimal::add);
            }
        } else {
            for (OutsourceOrderDelivery d : orderDeliveryMapper.selectList(new LambdaQueryWrapper<OutsourceOrderDelivery>()
                    .eq(OutsourceOrderDelivery::getOrderId, orderId)
                    .eq(OutsourceOrderDelivery::getStatus, DocStatus.AUDITED.getCode()))) {
                Long pid = resolveStockProductId(null, d.getProductId(), d.getProductMasterId());
                if (pid == null) continue;
                delivered.merge(pid, nz(d.getQuantity()), BigDecimal::add);
            }
        }

        // 已退量（其它已审核退货单，排除本单）
        LambdaQueryWrapper<ReturnOrder> w = new LambdaQueryWrapper<ReturnOrder>()
                .eq(ReturnOrder::getStatus, DocStatus.AUDITED.getCode())
                .ne(ReturnOrder::getId, order.getId());
        if (deliveryId != null) w.eq(ReturnOrder::getSourceDeliveryId, deliveryId);
        else w.eq(ReturnOrder::getOrderId, orderId);
        List<ReturnOrder> others = returnOrderMapper.selectList(w);
        Map<Long, BigDecimal> returned = new LinkedHashMap<>();
        if (!others.isEmpty()) {
            List<Long> ids = others.stream().map(ReturnOrder::getId).toList();
            for (OutsourceReturnOrderProduct p : returnProductMapper.selectList(
                    new LambdaQueryWrapper<OutsourceReturnOrderProduct>()
                            .in(OutsourceReturnOrderProduct::getReturnOrderId, ids))) {
                Long pid = resolveStockProductId(null, p.getProductId(), null);
                if (pid == null) continue;
                returned.merge(pid, nz(p.getQuantity()), BigDecimal::add);
            }
        }

        for (Map.Entry<Long, BigDecimal> e : thisQty.entrySet()) {
            Long pid = e.getKey();
            BigDecimal base = delivered.getOrDefault(pid, BigDecimal.ZERO);
            BigDecimal done = returned.getOrDefault(pid, BigDecimal.ZERO);
            if (done.add(e.getValue()).compareTo(base) > 0) {
                throw new BusinessException("退货数量超过该产品已收货量（产品["
                        + nameMap.getOrDefault(pid, "#" + pid) + "]已收 " + fmt(base)
                        + "、已退 " + fmt(done) + "、本次 " + fmt(e.getValue()) + "）");
            }
        }
    }

    /** null 视为 0 */
    private int nzInt(Integer v) { return v != null ? v : 0; }

    @Override
    public List<Map<String, Object>> bomSnapshot(Long snapshotId) {
        if (snapshotId == null) return new ArrayList<>();
        List<BomSnapshotItem> items = bomSnapshotItemMapper.selectList(
            new LambdaQueryWrapper<BomSnapshotItem>().eq(BomSnapshotItem::getSnapshotId, snapshotId));
        // 同一物料可能拆多行 → 按物料合并单套用量
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        for (BomSnapshotItem it : items) {
            Long key = it.getMaterialId();
            if (key == null) continue;
            Map<String, Object> m = map.computeIfAbsent(key, k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("outsourceMaterialId", key);
                x.put("materialName", getMaterialNameById(key));
                x.put("materialTypeId", it.getMaterialTypeId());
                x.put("materialTypeName", getMaterialTypeNameById(it.getMaterialTypeId()));
                x.put("unit", it.getUnit());
                x.put("perSetQuantity", BigDecimal.ZERO);
                return x;
            });
            m.put("perSetQuantity", ((BigDecimal) m.get("perSetQuantity")).add(nz(it.getQuantityPerSet())));
        }
        return new ArrayList<>(map.values());
    }

    // ===== 私有方法 =====

    private void updateOutsourceStock(Long warehouseId, Long materialId, BigDecimal delta, String qualityType,
                                      String changeType, String orderCode, Long relatedBillId) {
        // 物料库存统一 GOOD 品质；统一走标准库存服务（流水自动补齐 quality_type/before/after、物料名、单据类型/单据号）。
        // 4) F1（2026-09-17）：补传 relatedBillId（本退货单ID），使流水可回溯单据
        //
        // I17 修复（2026-09-18，用户选定口径①）：改用**允许负数**口径 —— 与「交货领料 / 退不良还料 / 委外其他出入库」一致。
        // 原实现走严格校验（quantity + delta >= 0）：委外仓在"缺料强制出库"后本来就可能是负库存，
        // 此时把 BOM 料**退回工厂（入库方向）**也会被判 `库存不足，无法出库` → 单据永久卡草稿（真实业务死锁），
        // 且文案方向与"退回入库"不符。审核（+quantity）与反审核（−quantity）两条腿共用本方法，口径必须一致。
        if (warehouseId == null || materialId == null) return;
        warehouseStockService.changeMaterialStockAllowNegative(warehouseId, materialId, delta, changeType, orderCode,
                RelatedBillType.OUTSOURCE_RETURN, null, null, relatedBillId);
    }

    /**
     * 收费字段归一化（与销售退货 SaleReturnServiceImpl.normalizeCharge 同策略）：
     * 不收费则金额归零、类型清空；收费则类型必须合法且金额必须 > 0。
     * 收费方向是「<b>加工厂向我方收取</b>」（我方付加工厂），审核后生成一条正向应付。
     */
    private void normalizeCharge(ReturnOrder order, Map<String, Object> body) {
        Long flagVal = toLong(body.get("chargeFlag"));
        if (flagVal == null || flagVal != 1) {
            order.setChargeFlag(0);
            order.setChargeType(null);
            order.setChargeAmount(BigDecimal.ZERO);
            order.setChargeReason(null);
            return;
        }
        Object t = body.get("chargeType");
        String type = t == null ? null : t.toString().trim();
        if (type == null || type.isEmpty()) throw new BusinessException("已选择收费，请选择收费类型");
        if (!OutsourceChargeType.isValid(type))
            throw new BusinessException("非法的收费类型：" + type);
        BigDecimal amount = toBigDecimal(body.get("chargeAmount"));
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("已选择收费，收费金额必须大于 0");
        order.setChargeFlag(1);
        order.setChargeType(OutsourceChargeType.fromCode(type).getCode());
        order.setChargeAmount(amount);
        order.setChargeReason((String) body.get("chargeReason"));
    }

    private BigDecimal calcFifoPrice(Long materialId, BigDecimal requiredQty) {
        // F7-77（2026-09-20）：收敛到 OutsourceMaterialPricingService.fifoPriceWithFallback
        // （三级链保持不变：① 物料移动加权成本 → ② 交期 FIFO → ③ 物料主数据参考价；
        //   统一排除 CANCELLED + 批量取明细去 N+1）
        return pricingService.fifoPriceWithFallback(materialId, requiredQty);
    }

    /**
     * 该产品在指定加工单里所用的 **BOM 快照ID**（2026-09-17）。
     * <p>用户口径：**关联了加工单就不允许再手改快照** —— 前端已对"BOM来源"置灰，
     * 这里按订单产品行再兜底纠正一次，避免被绕过（例如直接调接口）。</p>
     *
     * @param orderId         关联的加工单ID（未关联时返回 null，表示可用前端选的快照）
     * @param productMasterId 产品主数据ID
     */
    private Long snapshotIdOfOrderProduct(Long orderId, Long productMasterId) {
        if (orderId == null || productMasterId == null) return null;
        for (OutsourceOrderProduct op : orderProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderProduct>().eq(OutsourceOrderProduct::getOrderId, orderId))) {
            if (productMasterId.equals(op.getProductId())) return op.getBomSnapshotId();
        }
        return null;
    }

    /**
     * 退货成品的产品维度统一为「产品主数据ID」。
     * <p>前端产品下拉取值来自 {@link #orderProducts(Long)}，若直接当主数据ID写库存，
     * 会出现「库存不足，无法出库：产品ID=行ID」或扣错产品。此处统一解析：
     * 显式传了主数据ID优先；否则按订单产品行ID解析出主数据ID；解析不到则原样返回（交由库存校验报错）。</p>
     */
    private Long resolveStockProductId(ReturnOrder order, Long productKey, Long explicitMasterId) {
        if (explicitMasterId != null) return explicitMasterId;
        if (productKey == null) return null;
        OutsourceOrderProduct row = orderProductMapper.selectById(productKey);
        if (row != null && row.getProductId() != null
                && (order == null || order.getOrderId() == null || order.getOrderId().equals(row.getOrderId()))) {
            return row.getProductId();
        }
        return productKey;
    }

    /** 退回成品规格归一化：仅允许 A/B/C/DEFECT，空值按 A 规 */
    private String normalizeQualityType(Object raw) {
        String code = raw == null ? null : raw.toString().trim();
        if (code == null || code.isEmpty()) return ProductQualityType.A.getCode();
        String upper = code.toUpperCase();
        if ("A".equals(upper) || "B".equals(upper) || "C".equals(upper) || "DEFECT".equals(upper))
            return upper;
        throw new BusinessException("非法的退回成品规格：" + code);
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        String s = val.toString().trim();
        if (s.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        String s = val.toString().trim();
        if (s.isEmpty()) return null;
        try { return Long.valueOf(s); } catch (NumberFormatException e) { return null; }
    }

    /** 根据委外物料ID查询名称，用于展示回填（ID关联查询替代冗余name字段） */
    private String getMaterialNameById(Long materialId) {
        if (materialId == null) return "";
        OutsourceMaterial m = outsourceMaterialMapper.selectById(materialId);
        return m != null ? m.getMaterialName() : "";
    }

    /** 根据 物料类型ID 查询类型名称，空安全返回 "-" */
    private String getMaterialTypeNameById(Long materialTypeId) {
        if (materialTypeId == null) return "-";
        com.beichen.erp.dev.entity.MaterialType bt = materialTypeMapper.selectById(materialTypeId);
        return bt != null ? bt.getTypeName() : "-";
    }

    private String generateCode() {
        // F7-75③（2026-09-20）：统一走 BillNoSeq。原实现用 `count(*) + 1` ⇒ ① 并发两请求拿到同一序号
        // （靠 uk_code 兜底报错）② 历史单据被删/跨日残留会让序号与实际最大号错位 ⇒ 取"最大号 +1"更稳。
        String prefix = BillPrefix.OUTSOURCE_RETURN_ORDER + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        ReturnOrder last = returnOrderMapper.selectOne(new LambdaQueryWrapper<ReturnOrder>()
                .likeRight(ReturnOrder::getCode, prefix).orderByDesc(ReturnOrder::getCode).last("LIMIT 1"));
        int seq = last != null ? com.beichen.erp.common.BillNoSeq.lastSeq(last.getCode(), prefix) + 1 : 1;
        return com.beichen.erp.common.BillNoSeq.format(prefix, seq);
    }

    /** 当前登录用户ID */
    private Long getCurrentUserId() {
        try { return StpUtil.getLoginIdAsLong(); } catch (Exception e) { return null; }
    }

    /** 当前登录用户名 */
    private String getCurrentUserName() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = userMapper.selectById(userId);
            return user != null ? user.getUsername() : null;
        } catch (Exception e) { return null; }
    }
}
