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
import com.beichen.erp.dev.entity.MaterialType;
import com.beichen.erp.dev.mapper.MaterialTypeMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.common.SubjectType;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import com.beichen.erp.finance.service.PayableHelper;
import com.beichen.erp.finance.service.ReceivableHelper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.outsource.common.MaterialOrderStatus;
import com.beichen.erp.outsource.common.MaterialReturnType;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.outsource.entity.*;
import com.beichen.erp.outsource.mapper.*;
import com.beichen.erp.outsource.service.OutsourceMaterialReturnService;
import com.beichen.erp.supplier.common.SupplierTypeEnum;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.entity.SupplierTypeRef;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.supplier.mapper.SupplierTypeRefMapper;
import com.beichen.erp.warehouse.common.WarehouseCategory;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 委外物料退货单业务层实现
 * <p>物料从源仓退回物料商，冲减应付。草稿-审核-取消审核状态机。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutsourceMaterialReturnServiceImpl implements OutsourceMaterialReturnService {

    /** 进度筛选：还有未返回（已审核、未结案、送修 > 已返回） */
    private static final String PROGRESS_PENDING_RETURN = "PENDING_RETURN";
    /** 进度筛选：已结案 */
    private static final String PROGRESS_CLOSED = "CLOSED";
    /** 进度筛选：**已返回完**（全部送回，含已结案；2026-09-27 三级菜单「已返回完」页签） */
    private static final String PROGRESS_RETURNED = "RETURNED";
    /** 进度筛选：**待返回（含草稿）**（2026-09-27）：草稿 ∪ (已审核且送修 &gt; 已返回)，口径同加工侧 OPEN */
    private static final String PROGRESS_OPEN = "OPEN";

    private final OutsourceMaterialReturnMapper returnMapper;
    private final OutsourceMaterialReturnItemMapper itemMapper;
    /** 维修返回记录（维修返回单的"回来"腿，2026-09-17） */
    private final OutsourceMaterialReturnRepairMapper repairMapper;
    /** 维修返回实际用料（子物料补料）明细（2026-09-25 物料形态化） */
    private final OutsourceMaterialReturnRepairMaterialMapper repairMaterialMapper;
    /** 物料子物料关系（维修返回「实际用料 = 送修物料的子物料」范围收口，2026-09-27 用户口径） */
    private final com.beichen.erp.outsource.mapper.OutsourceMaterialComponentMapper componentMapper;
    private final SupplierMapper supplierMapper;
    /** 2026-09-21（用户口径）：退货对象只能是辅料商/供应商，不能是供货商（成品商）⇒ 需要读类型关联 */
    private final SupplierTypeRefMapper supplierTypeRefMapper;
    private final WarehouseMapper warehouseMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final MaterialTypeMapper materialTypeMapper;
    private final WarehouseStockMapper warehouseStockMapper;
    /** 物料库存统一写入口（架构债 A1：原先本类私有实现直写 mapper + jdbcTemplate 写流水） */
    private final WarehouseStockService warehouseStockService;
    /** 2026-09-25 物料形态化：补料耗用 FIFO 结转到回仓主物料（登记 applyMaterial / 撤销 reverseByBill） */
    private final com.beichen.erp.warehouse.service.CostService costService;
    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;
    /** 收料单（物料收货）——「从收货页发起退货」的可退预填用 */
    private final OutsourceDeliveryMapper outsourceDeliveryMapper;
    private final OutsourceDeliveryItemMapper outsourceDeliveryItemMapper;
    private final PayableHelper payableHelper;
    /** P2（2026-09-28）：退货退款生成**对供应商的应收**（收款单按 subjectType=SUPPLIER 核销） */
    private final FinanceReceivableMapper receivableMapper;
    private final ReceivableHelper receivableHelper;
    private final UserMapper userMapper;
    /** F7-77（2026-09-20）：物料单价统一实现（FIFO 口径与此前一致，但统一排除 CANCELLED + 去 N+1） */
    private final com.beichen.erp.outsource.service.OutsourceMaterialPricingService pricingService;

    @Override
    public Page<Map<String, Object>> page(int pageNum, int pageSize, String code, Long supplierId, String status,
                                          String returnType, String progress, String statuses, String linked) {
        LambdaQueryWrapper<OutsourceMaterialReturn> w = new LambdaQueryWrapper<OutsourceMaterialReturn>()
                .eq(code != null && !code.isBlank(), OutsourceMaterialReturn::getCode, code)
                .eq(supplierId != null, OutsourceMaterialReturn::getSupplierId, supplierId)
                .eq(status != null && !status.isBlank(), OutsourceMaterialReturn::getStatus, status)
                // 类型页签（2026-09-17）：退货退款 / 维修返回
                .eq(returnType != null && !returnType.isBlank(), OutsourceMaterialReturn::getReturnType, returnType);
        // 2026-09-27 三级菜单（物料侧拆叶子，与加工侧 linked 同口径）：
        //   关联退料 = 由物料收货页发起、挂了物料订单（单号 MRH-）；无单退料 = 没挂订单（MRW-）。
        if ("WITH_ORDER".equalsIgnoreCase(linked)) w.isNotNull(OutsourceMaterialReturn::getMaterialOrderId);
        else if ("WITHOUT_ORDER".equalsIgnoreCase(linked)) w.isNull(OutsourceMaterialReturn::getMaterialOrderId);
        // 2026-09-27 三级菜单：状态多值（DRAFT,AUDITED=有效单据、CANCELLED=已作废）
        if (statuses != null && !statuses.isBlank()) {
            java.util.List<String> sts = java.util.Arrays.stream(statuses.split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toList());
            if (!sts.isEmpty()) w.in(OutsourceMaterialReturn::getStatus, sts);
        }
        w.orderByDesc(OutsourceMaterialReturn::getId);
        // 进度筛选（维修返回，2026-09-17）：
        //   PENDING_RETURN = 已审核、未结案，且「送修合计 > 已返回合计」（还有货在供应商处没回来）
        //   CLOSED         = 已结案（未返回清零并人工确认收尾）
        if (PROGRESS_PENDING_RETURN.equalsIgnoreCase(progress)) {
            w.eq(OutsourceMaterialReturn::getStatus, DocStatus.AUDITED.getCode())
             .eq(OutsourceMaterialReturn::getClosedFlag, 0)
             .apply("(SELECT IFNULL(SUM(i.quantity),0) FROM outsource_material_return_item i WHERE i.return_order_id = outsource_material_return.id)"
                     + " > (SELECT IFNULL(SUM(r.quantity),0) FROM outsource_material_return_repair r WHERE r.return_order_id = outsource_material_return.id)");
        } else if (PROGRESS_CLOSED.equalsIgnoreCase(progress)) {
            w.eq(OutsourceMaterialReturn::getClosedFlag, 1);
        } else if (PROGRESS_OPEN.equalsIgnoreCase(progress)) {
            w.in(OutsourceMaterialReturn::getStatus, java.util.List.of(DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
             .apply("(outsource_material_return.status = '" + DocStatus.DRAFT.getCode() + "'"
                     + " OR (SELECT IFNULL(SUM(i.quantity),0) FROM outsource_material_return_item i WHERE i.return_order_id = outsource_material_return.id)"
                     + " > (SELECT IFNULL(SUM(r.quantity),0) FROM outsource_material_return_repair r WHERE r.return_order_id = outsource_material_return.id))");
        } else if (PROGRESS_RETURNED.equalsIgnoreCase(progress)) {
            // 2026-09-27「已返回完」页签：已审核且全部送回（含已结案；与 PENDING_RETURN 互补）
            w.eq(OutsourceMaterialReturn::getStatus, DocStatus.AUDITED.getCode())
             .apply("(SELECT IFNULL(SUM(i.quantity),0) FROM outsource_material_return_item i WHERE i.return_order_id = outsource_material_return.id)"
                     + " <= (SELECT IFNULL(SUM(r.quantity),0) FROM outsource_material_return_repair r WHERE r.return_order_id = outsource_material_return.id)");
        }
        Page<OutsourceMaterialReturn> raw = returnMapper.selectPage(new Page<>(pageNum, pageSize), w);
        List<OutsourceMaterialReturn> records = raw.getRecords();
        List<Long> pageIds = records.stream().map(OutsourceMaterialReturn::getId).toList();
        // 已返回量：本页整批查一次（避免逐单查导致 N+1）
        Map<Long, BigDecimal> returnedMap = returnedQtyByOrders(pageIds);
        // F7-123（2026-09-20，同 §49 性能专项口径）：**整页批量取**来源收料单号 / 关联物料订单号 /
        // 供应商名 / 仓库名 / 明细 / 物料名 —— 原实现每行 4 次单查 + 逐行查明细 + 逐明细查物料名
        // ⇒ 一页 10 行 ≈ 60+ 次查询；现为固定 6 次（全部按本页收集的 id 集合一次查回）。
        java.util.Set<Long> deliveryIds = new java.util.HashSet<>();
        java.util.Set<Long> moIds = new java.util.HashSet<>();
        java.util.Set<Long> supIds = new java.util.HashSet<>();
        java.util.Set<Long> whIds = new java.util.HashSet<>();
        for (OutsourceMaterialReturn o : records) {
            if (o.getSourceDeliveryId() != null) deliveryIds.add(o.getSourceDeliveryId());
            if (o.getMaterialOrderId() != null) moIds.add(o.getMaterialOrderId());
            if (o.getSupplierId() != null) supIds.add(o.getSupplierId());
            if (o.getFromWarehouseId() != null) whIds.add(o.getFromWarehouseId());
        }
        Map<Long, String> deliveryCodeMap = new LinkedHashMap<>();
        if (!deliveryIds.isEmpty()) {
            for (OutsourceDelivery d : outsourceDeliveryMapper.selectBatchIds(deliveryIds)) {
                deliveryCodeMap.put(d.getId(), d.getCode());
            }
        }
        Map<Long, String> moCodeMap = new LinkedHashMap<>();
        if (!moIds.isEmpty()) {
            for (MaterialOrder mo : materialOrderMapper.selectBatchIds(moIds)) {
                moCodeMap.put(mo.getId(), mo.getCode());
            }
        }
        Map<Long, String> supNameMap = new LinkedHashMap<>();
        if (!supIds.isEmpty()) {
            for (Supplier s : supplierMapper.selectBatchIds(supIds)) {
                supNameMap.put(s.getId(), s.getName() != null ? s.getName() : "");
            }
        }
        Map<Long, String> whNameMap = new LinkedHashMap<>();
        if (!whIds.isEmpty()) {
            for (Warehouse wh : warehouseMapper.selectBatchIds(whIds)) {
                whNameMap.put(wh.getId(), wh.getWarehouseName() != null ? wh.getWarehouseName() : "");
            }
        }
        // 明细：本页一次 in 查 + 分组；顺带收集物料 id 供一次查名
        Map<Long, List<OutsourceMaterialReturnItem>> itemsByOrder = new LinkedHashMap<>();
        java.util.Set<Long> matIds = new java.util.HashSet<>();
        if (!pageIds.isEmpty()) {
            for (OutsourceMaterialReturnItem it : itemMapper.selectList(
                    new LambdaQueryWrapper<OutsourceMaterialReturnItem>()
                            .in(OutsourceMaterialReturnItem::getReturnOrderId, pageIds))) {
                itemsByOrder.computeIfAbsent(it.getReturnOrderId(), k -> new ArrayList<>()).add(it);
                if (it.getMaterialId() != null) matIds.add(it.getMaterialId());
            }
        }
        Map<Long, String> matNameMap = new LinkedHashMap<>();
        if (!matIds.isEmpty()) {
            for (OutsourceMaterial m : outsourceMaterialMapper.selectBatchIds(matIds)) {
                matNameMap.put(m.getId(), m.getMaterialName() != null ? m.getMaterialName() : "");
            }
        }
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, raw.getTotal());
        result.setRecords(records.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            // 类型归一后返回（历史 MATERIAL → REFUND），前端页签/标签直接用
            m.put("returnType", MaterialReturnType.normalize(o.getReturnType()).getCode());
            m.put("supplierId", o.getSupplierId());
            m.put("fromWarehouseId", o.getFromWarehouseId());
            m.put("sourceDeliveryId", o.getSourceDeliveryId());
            if (o.getSourceDeliveryId() != null) m.put("sourceDeliveryCode", deliveryCodeMap.get(o.getSourceDeliveryId()));
            // 关联物料订单（2026-09-17 维修返回闭环）：前端展示"已扣减收料 / 靠本单跟踪"
            m.put("materialOrderId", o.getMaterialOrderId());
            m.put("materialOrderCode", o.getMaterialOrderId() != null ? moCodeMap.get(o.getMaterialOrderId()) : null);
            m.put("deductedFlag", nzInt(o.getDeductedFlag()));
            m.put("closedFlag", nzInt(o.getClosedFlag()));
            m.put("closedTime", o.getClosedTime());
            m.put("returnDate", o.getReturnDate());
            m.put("status", o.getStatus());
            m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            if (o.getSupplierId() != null) m.put("supplierName", supNameMap.getOrDefault(o.getSupplierId(), ""));
            if (o.getFromWarehouseId() != null) m.put("warehouseName", whNameMap.getOrDefault(o.getFromWarehouseId(), ""));
            List<OutsourceMaterialReturnItem> items = itemsByOrder.getOrDefault(o.getId(), List.of());
            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalAmount = BigDecimal.ZERO;
            StringBuilder sb = new StringBuilder();
            // 2026-09-25（用户口径「物料列可点进详情」）：逐项 {materialId,materialName,unit,quantity}，
            // 前端渲染链接跳 /outsource/material-stock/detail/:materialId（物料档案 + 跨仓库存分布）
            List<Map<String, Object>> itemList = new ArrayList<>();
            for (OutsourceMaterialReturnItem it : items) {
                BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
                totalQty = totalQty.add(qty);
                if (it.getAmount() != null) totalAmount = totalAmount.add(it.getAmount());
                if (sb.length() > 0) sb.append("、");
                sb.append(it.getMaterialId() != null ? matNameMap.getOrDefault(it.getMaterialId(), "") : "")
                        .append("×").append(qty.stripTrailingZeros().toPlainString());
                if (it.getMaterialId() != null) {
                    Map<String, Object> im = new HashMap<>();
                    im.put("materialId", it.getMaterialId());
                    im.put("materialName", matNameMap.getOrDefault(it.getMaterialId(), ""));
                    im.put("unit", it.getUnit());
                    im.put("quantity", qty);
                    itemList.add(im);
                }
            }
            m.put("totalQuantity", totalQty);
            m.put("totalAmount", totalAmount);
            m.put("itemSummary", sb.toString());
            m.put("items", itemList);
            // 送修 / 已返回（维修返回跟踪用；退货退款也返回，前端只在维修页签展示）
            BigDecimal returnedQty = returnedMap.getOrDefault(o.getId(), BigDecimal.ZERO);
            m.put("sentQty", totalQty);
            m.put("returnedQty", returnedQty);
            m.put("unreturnedQty", totalQty.subtract(returnedQty).max(BigDecimal.ZERO));
            return m;
        }).toList());
        return result;
    }

    @Override
    public Map<String, Object> detail(Long id) {
        OutsourceMaterialReturn o = returnMapper.selectById(id);
        if (o == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId()); m.put("code", o.getCode());
        // 类型归一后返回（历史 MATERIAL → REFUND）
        m.put("returnType", MaterialReturnType.normalize(o.getReturnType()).getCode());
        m.put("supplierId", o.getSupplierId());
        m.put("fromWarehouseId", o.getFromWarehouseId());
        m.put("sourceDeliveryId", o.getSourceDeliveryId());
        if (o.getSourceDeliveryId() != null) m.put("sourceDeliveryCode", sourceDeliveryCode(o.getSourceDeliveryId()));
        // 关联物料订单（2026-09-17 维修返回闭环）：是否已在订单上扣减收料（deductedFlag）、订单当前状态
        m.put("materialOrderId", o.getMaterialOrderId());
        MaterialOrder mo = o.getMaterialOrderId() != null ? materialOrderMapper.selectById(o.getMaterialOrderId()) : null;
        m.put("materialOrderCode", mo != null ? mo.getCode() : null);
        m.put("materialOrderStatus", mo != null ? mo.getStatus() : null);
        m.put("deductedFlag", nzInt(o.getDeductedFlag()));
        m.put("closedFlag", nzInt(o.getClosedFlag()));
        m.put("closedTime", o.getClosedTime());
        m.put("closedBy", o.getClosedBy());
        m.put("returnDate", o.getReturnDate());
        m.put("status", o.getStatus());
        m.put("remark", o.getRemark());
        m.put("createTime", o.getCreateTime());
        if (o.getSupplierId() != null) {
            Supplier s = supplierMapper.selectById(o.getSupplierId());
            m.put("supplierName", s != null ? s.getName() : "");
        }
        if (o.getFromWarehouseId() != null) {
            Warehouse wh = warehouseMapper.selectById(o.getFromWarehouseId());
            m.put("warehouseName", wh != null ? wh.getWarehouseName() : "");
        }
        List<OutsourceMaterialReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnItem>().eq(OutsourceMaterialReturnItem::getReturnOrderId, id));
        List<Map<String, Object>> itemList = new ArrayList<>();
        for (OutsourceMaterialReturnItem it : items) {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("id", it.getId());
            im.put("materialId", it.getMaterialId());
            im.put("materialName", getMaterialName(it.getMaterialId()));
            im.put("materialTypeId", it.getMaterialTypeId());
            im.put("materialTypeName", getMaterialTypeName(it.getMaterialTypeId()));
            im.put("unit", it.getUnit());
            im.put("quantity", it.getQuantity());
            im.put("unitPrice", it.getUnitPrice());
            im.put("amount", it.getAmount());
            im.put("remark", it.getRemark());
            itemList.add(im);
        }
        m.put("items", itemList);

        // 维修返回记录（仅维修返回单会有；详情页展示并可撤销）2026-09-17
        List<OutsourceMaterialReturnRepair> repairs = repairMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnRepair>()
                        .eq(OutsourceMaterialReturnRepair::getReturnOrderId, id));
        List<Map<String, Object>> repairList = new ArrayList<>();
        BigDecimal repairReturnedQty = BigDecimal.ZERO;
        for (OutsourceMaterialReturnRepair r : repairs) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", r.getId());
            // 2026-09-28（草稿口径）：状态 + 审核人 + 审核时间 —— 详情页据此渲染「草稿→审核/删除、已审核→反审核」
            rm.put("status", r.getStatus());
            rm.put("auditorName", r.getAuditorName());
            rm.put("auditTime", r.getAuditTime());
            rm.put("repairDate", r.getRepairDate());
            rm.put("warehouseId", r.getWarehouseId());
            Warehouse rwh = r.getWarehouseId() != null ? warehouseMapper.selectById(r.getWarehouseId()) : null;
            rm.put("warehouseName", rwh != null ? rwh.getWarehouseName() : "");
            rm.put("materialId", r.getMaterialId());
            rm.put("materialName", r.getMaterialName() != null ? r.getMaterialName() : getMaterialName(r.getMaterialId()));
            rm.put("unit", r.getUnit());
            rm.put("quantity", r.getQuantity());
            rm.put("remark", r.getRemark());
            // P2-1 物料版（2026-09-25 物料形态化）：每条返回记录附实际用料（子物料）明细汇总
            List<Map<String, Object>> mats = new ArrayList<>();
            BigDecimal matTotal = BigDecimal.ZERO;
            StringBuilder summary = new StringBuilder();
            for (OutsourceMaterialReturnRepairMaterial it : repairMaterialMapper.selectList(
                    new LambdaQueryWrapper<OutsourceMaterialReturnRepairMaterial>()
                            .eq(OutsourceMaterialReturnRepairMaterial::getRepairRecordId, r.getId())
                            .orderByAsc(OutsourceMaterialReturnRepairMaterial::getId))) {
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
            repairList.add(rm);
            // 已返回量**只算已审核**（草稿未落账）：未返回量、结案判定、登记弹窗默认值全按它
            if (r.getQuantity() != null && DocStatus.AUDITED.getCode().equals(r.getStatus()))
                repairReturnedQty = repairReturnedQty.add(r.getQuantity());
        }
        m.put("repairReturns", repairList);
        m.put("repairReturnedQty", repairReturnedQty);
        // 送修 / 已返回 / 未返回（维修返回的进度口径，与列表、结案校验一致）
        BigDecimal sentQty = items.stream()
                .map(it -> it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        m.put("sentQty", sentQty);
        m.put("unreturnedQty", sentQty.subtract(repairReturnedQty).max(BigDecimal.ZERO));
        return m;
    }

    // ===== 维修返回（维修返回单的"回来"腿，2026-09-17） =====

    /**
     * 登记维修返回（**只建草稿**，2026-09-28 用户口径「加工**和物料**的登记返回都需要审核和反审核」）。
     *
     * <p>维修返回单已审核（货已送供应商）后，供应商修好分批把物料送回来。登记时只做**校验 + 落库**：</p>
     * <ul>
     *   <li>本单必须是**已审核**的维修返回单、且未结案；</li>
     *   <li>数量按**物料**不超过「送修量 − 已审核返回量」（草稿不占额度，可先建多张草稿）；</li>
     *   <li>实际用料的**可选范围**只能是送修物料的子物料（越界直接拒）。</li>
     * </ul>
     *
     * <p><b>不动库存、不回补订单收料数、不核销在厂行、不结转成本、不产生任何应付</b> ——
     * 这些全部在「审核」（{@link #auditRepairReturn}）执行；「反审核」对称逆回并留痕（记录回草稿），
     * 草稿可直接删除（{@link #cancelRepairReturn}）。与「加工返回」outsource_return_back 同一口径。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void repairReturn(Long id, Map<String, Object> body) {
        // F7-138（2026-09-20）：**行锁**（FOR UPDATE，带租户）。与成品侧同构：按「送修量 − 已返回量」
        // 核销后直接入库（changeMaterialStock），并发双击会读到相同的 returned ⇒ **重复入库**。
        Long lockCid = CompanyContext.get();
        if (lockCid != null && lockCid <= 0) lockCid = null;
        OutsourceMaterialReturn order = returnMapper.selectForUpdate(id, lockCid);
        if (order == null) throw new BusinessException("退货单不存在");
        if (!MaterialReturnType.isRepair(order.getReturnType()))
            throw new BusinessException("只有维修返回单可以登记维修返回");
        if (!DocStatus.AUDITED.getCode().equals(order.getStatus()))
            throw new BusinessException("只有已审核（已送修）的维修返回单才能登记维修返回");
        if (nzInt(order.getClosedFlag()) == 1)
            throw new BusinessException("该单已结案，如需继续登记请先「撤销结案」");

        Long whId = toLong(body.get("warehouseId"));
        if (whId == null) throw new BusinessException("请选择返回入库仓");
        Warehouse wh = warehouseMapper.selectById(whId);
        if (wh == null) throw new BusinessException("返回入库仓不存在");

        Object dateObj = body.get("repairDate");
        LocalDate repairDate = (dateObj != null && !dateObj.toString().isBlank())
                ? LocalDate.parse(dateObj.toString()) : LocalDate.now();

        List<Map<String, Object>> lines = asListMap(body.get("items"));
        if (lines.isEmpty()) throw new BusinessException("请填写维修返回数量");

        // 按**物料**核销送修/返回数量（物料库存只有良品一档，无品质维度）
        Map<Long, BigDecimal> sent = sentQtyByMaterial(id);
        Map<Long, BigDecimal> returned = returnedQtyByMaterial(id);
        Long cid = CompanyContext.get();
        // P2-1 物料版（2026-09-25 物料形态化）：供应商委外仓（核销在厂 MATERIAL_REPAIR 行 / 扣实际用料子物料）——
        // 2026-09-28 起这两条腿都在**审核**时执行（此处只建草稿）；委外仓在审核时解析（见 applyRepairLegs）。
        List<OutsourceMaterialReturnRepair> savedRows = new ArrayList<>();
        int saved = 0;
        for (Map<String, Object> line : lines) {
            Long materialId = toLong(line.get("materialId"));
            BigDecimal qty = toBigDecimal(line.get("quantity"));
            if (materialId == null || qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal sentQty = sent.getOrDefault(materialId, BigDecimal.ZERO);
            if (sentQty.compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("该物料不在本单送修范围内（物料ID=" + materialId + "）");
            BigDecimal already = returned.getOrDefault(materialId, BigDecimal.ZERO);
            if (already.add(qty).compareTo(sentQty) > 0)
                throw new BusinessException("返回数量超过送修数量（物料ID=" + materialId + "：送修 " + sentQty
                        + "、已返回 " + already + "、本次 " + qty + "）");

            // 2026-09-28（用户口径「加工和物料的登记返回都需要审核和反审核」）：**登记不落账** ——
            // 物料入库（MATERIAL_REPAIR_IN）已移到 auditRepairReturn，审核才执行。
            // 关联订单未完成时回补收料数/冲减送修中（分摊到本单该物料对应的订单明细行；一般为一行）：
            // 登记只**算**分摊计划（只读、不写库），真正回补同样在审核时按记录逐条执行。
            List<Object[]> credits = nzInt(order.getDeductedFlag()) == 1
                    ? planCreditBackToOrder(id, materialId, qty) : Collections.emptyList();
            BigDecimal credited = BigDecimal.ZERO;
            for (Object[] c : credits) {
                BigDecimal take = (BigDecimal) c[1];
                savedRows.add(insertRepairRow(id, repairDate, whId, materialId, line, take, (Long) c[0], cid));
                credited = credited.add(take);
                saved++;
            }
            // 差额（订单明细行已回补满 / 未关联订单）仍按普通返回记录落库，保证"送修/已返回"账目平
            if (qty.subtract(credited).compareTo(BigDecimal.ZERO) > 0) {
                savedRows.add(insertRepairRow(id, repairDate, whId, materialId, line, qty.subtract(credited), null, cid));
                saved++;
            }
            // P2-1 物料版：核销在厂 MATERIAL_REPAIR 行（供应商委外仓）—— **2026-09-28 起改在审核时执行**
            // （见 auditRepairReturn），并按"是否真的核销了"把 onsite_leg 写到记录上：反审核据此决定是否恢复在厂
            // （旧单审核于物料形态化改造前、在厂行不存在 ⇒ 留 0，否则反审核会凭空给在厂行 +qty）。
            returned.put(materialId, already.add(qty)); // 同一请求内多行也要累计，避免叠加超退
        }
        if (saved == 0) throw new BusinessException("请填写维修返回数量");

        // P2-1 物料版：实际用料（子物料补料）多行 —— 2026-09-28（草稿口径）：**登记只落数据**（物料 + 数量），
        // 不动库存、不按 FIFO 计价、不结转成本；计价（fifoPriceWithFallback）、扣委外仓料
        // （MATERIAL_REPAIR_COMPONENT）、Σ用料按返回量摊入回仓主物料（applyMaterial）全部搬到**审核**
        // （见 applyRepairLegs）。
        // ⚠️ 可选范围校验（2026-09-27 用户口径：只能是"送修物料的子物料"）**仍留在登记** —— 避免草稿存下非法用料。
        if (!savedRows.isEmpty()) {
            List<Map<String, Object>> mats = asListMap(body.get("materials"));
            if (!mats.isEmpty()) {
                Set<Long> pool = new HashSet<>();
                for (Map<String, Object> c : materialCandidatesOf(id)) pool.add((Long) c.get("materialId"));
                // 用料行挂在本次登记的**首条返回记录**上（一次登记 = 一批；审核该条时统一结算本批用料）。
                // 首条草稿被删除时，cancelRepairReturn 会把用料行**改挂**到本单仍在的首条草稿上（否则会随删除丢失）。
                Long anchorRecordId = savedRows.get(0).getId();
                for (Map<String, Object> mat : mats) {
                    Long mid = toLong(mat.get("materialId"));
                    BigDecimal mq = toBigDecimal(mat.get("quantity"));
                    if (mid == null || mq == null || mq.compareTo(BigDecimal.ZERO) <= 0) continue;
                    if (!pool.contains(mid))
                        throw new BusinessException("实际用料只能从**送修物料的子物料**里选：「" + getMaterialName(mid)
                                + "」不是本单送修物料的子物料"
                                + (pool.isEmpty() ? "（本单送修物料都还没维护子物料，请先到「物料信息管理 → 子物料」维护）"
                                                  : "（本单可选子物料 " + pool.size() + " 个）"));
                    OutsourceMaterialReturnRepairMaterial it = new OutsourceMaterialReturnRepairMaterial();
                    it.setRepairRecordId(anchorRecordId);
                    it.setMaterialId(mid);
                    it.setMaterialName(getMaterialName(mid));
                    OutsourceMaterial m0 = outsourceMaterialMapper.selectById(mid);
                    it.setUnit(m0 != null ? m0.getUnit() : null);
                    it.setQuantity(mq);
                    // 单价/金额 = 0（草稿未落账）：审核时按 FIFO 回填（页面据此也能看出"尚未落账"）
                    it.setUnitPrice(BigDecimal.ZERO);
                    it.setAmount(BigDecimal.ZERO);
                    if (cid != null && cid > 0) it.setCompanyId(cid);
                    repairMaterialMapper.insert(it);
                }
            }
        }
    }

    /**
     * 登记维修返回的**实际用料（补料）候选集**（2026-09-27 用户口径）：物料维修 ⇒ 用料只能从**送修物料自己的子物料**里选。
     * <p>数据源 {@code outsource_material_component}（父 = 本单送修行物料）；同一子物料被多个父物料引用时合并用量，
     * 并记下**来源物料**（前端按下拉分组显示）。不筛库存（沿用"可超用量、允许扣负"口径）。</p>
     */
    @Override
    public List<Map<String, Object>> repairMaterialCandidates(Long id) {
        OutsourceMaterialReturn o = returnMapper.selectById(id);
        if (o == null) throw new BusinessException("退货单不存在");
        return materialCandidatesOf(id);
    }

    /** 候选集实现（端点与提交范围校验**共用同一份逻辑**，避免"前端拦了后端没拦"） */
    private List<Map<String, Object>> materialCandidatesOf(Long orderId) {
        List<OutsourceMaterialReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnItem>()
                        .eq(OutsourceMaterialReturnItem::getReturnOrderId, orderId));
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        for (OutsourceMaterialReturnItem it : items) {
            Long parentId = it.getMaterialId();
            if (parentId == null) continue;
            for (OutsourceMaterialComponent c : componentMapper.selectList(
                    new LambdaQueryWrapper<OutsourceMaterialComponent>()
                            .eq(OutsourceMaterialComponent::getParentMaterialId, parentId))) {
                Long key = c.getChildMaterialId();
                if (key == null) continue;
                Map<String, Object> m = map.computeIfAbsent(key, k -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("materialId", k);
                    x.put("materialName", getMaterialName(k));
                    OutsourceMaterial om = outsourceMaterialMapper.selectById(k);
                    x.put("unit", om != null ? om.getUnit() : null);
                    x.put("quantity", BigDecimal.ZERO);
                    x.put("fromMaterialId", parentId);
                    x.put("fromMaterialName", getMaterialName(parentId));
                    return x;
                });
                m.put("quantity", ((BigDecimal) m.get("quantity")).add(nz(c.getQuantity())));
            }
        }
        return new ArrayList<>(map.values());
    }

    /**
     * P2-1 物料版：核销在厂物料（MATERIAL_REPAIR 行，定位键唯一 ⇒ 精确扣减）。
     * <p>⚠️ 存量兼容：旧单（改造前审核）从未入过厂 ⇒ 在厂行不存在时跳过核销腿并留痕（动作对称）；
     * 行存在但数量不足 = 真错账 ⇒ 硬报错。</p>
     *
     * @return **是否真的核销了**（true=扣减了在厂 / false=旧单无在厂行被跳过）。
     *         调用方必须把返回值写到返回记录的 {@code onsite_leg} 上，撤销腿据此决定是否恢复在厂
     *         （2026-09-27 二修：否则旧单撤销会凭空给在厂行 +qty）。
     */
    private boolean allocateOnSiteRepair(OutsourceMaterialReturn order, Long supplierWhId,
                                         Long materialId, BigDecimal qty, Long cid) {
        BigDecimal onSite = warehouseStockService.getMaterialQuantity(supplierWhId, materialId,
                WarehouseStock.FORM_MATERIAL_REPAIR);
        if (onSite.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("维修返回：旧单（改造前审核）无在厂 MATERIAL_REPAIR 行，跳过核销腿 code={} materialId={} qty={}",
                    order.getCode(), materialId, qty);
            return false;
        }
        if (onSite.compareTo(qty) < 0)
            throw new BusinessException("在厂物料（维修送修）不足：当前 " + onSite + "、需核销 " + qty);
        warehouseStockService.changeMaterialStock(supplierWhId, materialId, qty.negate(),
                StockChangeType.CANCEL_MATERIAL_REPAIR_STOCK_IN.getCode(), order.getCode(),
                RelatedBillType.OUTSOURCE_MATERIAL_REPAIR, null, null, order.getId(),
                WarehouseStock.FORM_MATERIAL_REPAIR);
        return true;
    }

    /** 按供应商解析委外仓（首个 OUTSOURCE 仓；无仓返回 null，由调用方决定报错或跳过） */
    private Long firstOutsourceWarehouseOf(Long supplierId) {
        if (supplierId == null) return null;
        List<Warehouse> whs = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getFactoryId, supplierId)
                .eq(Warehouse::getWarehouseCategory, WarehouseCategory.OUTSOURCE.getCode())
                .orderByAsc(Warehouse::getId));
        return whs.isEmpty() ? null : whs.get(0).getId();
    }

    /** 落一条维修返回记录（返回插入的行，含自增ID——供用料成本分摊挂 relatedBillId） */
    private OutsourceMaterialReturnRepair insertRepairRow(Long returnOrderId, LocalDate repairDate, Long whId, Long materialId,
                                                          Map<String, Object> line, BigDecimal qty, Long orderItemId, Long cid) {
        OutsourceMaterialReturnRepair row = new OutsourceMaterialReturnRepair();
        row.setReturnOrderId(returnOrderId);
        row.setRepairDate(repairDate);
        row.setWarehouseId(whId);
        row.setMaterialId(materialId);
        row.setMaterialName(getMaterialName(materialId));
        row.setUnit((String) line.get("unit"));
        row.setQuantity(qty);
        row.setRemark((String) line.get("remark"));
        row.setMaterialOrderItemId(orderItemId);
        // 2026-09-28（用户口径）：登记只建**草稿**（不动库存/账务）—— 审核才落账、反审核对称逆回。
        row.setStatus(DocStatus.DRAFT.getCode());
        // onsite_leg 默认"审核时会核销在厂"；审核时若为旧单（无在厂行）会被改写成 0（见 applyRepairLegs ②）
        row.setOnsiteLeg(1);
        if (cid != null && cid > 0) row.setCompanyId(cid);
        repairMapper.insert(row);
        return row;
    }

    /**
     * F7-71（2026-09-20 立，2026-09-28 扩到「订单退料」）：**「不超可退」校验**
     * （与 {@link #deductOrderReceived} 同口径 —— 退款与订单退料共用）。
     *
     * <p>可退 = 该物料在本订单上「已收 − 已退不良 − 送修中 − **订单退料已退** − 本单之外已审核的退货退款合计」。
     * 缺这道防线时，只要源仓有货即可反复退同一批收料 ⇒ 负应付超冲（把供应商往来冲成负数）。</p>
     *
     * <p>仅在退货单关联了物料订单时调用；同一订单同一物料可能有多行明细，故按物料**聚合**后比较。</p>
     */
    private void assertNotOverReturnable(OutsourceMaterialReturn order, List<OutsourceMaterialReturnItem> items) {
        Long moId = order.getMaterialOrderId();
        MaterialOrder mo = materialOrderMapper.selectById(moId);
        if (mo == null) throw new BusinessException("关联的物料订单不存在");

        // 该订单下每个物料的 [已收, 已退不良, 送修中, 订单退料已退]
        Map<Long, BigDecimal[]> agg = new LinkedHashMap<>();
        for (MaterialOrderItem oi : materialOrderItemMapper.selectList(
                new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, moId))) {
            if (oi.getMaterialId() == null) continue;
            BigDecimal[] a = agg.computeIfAbsent(oi.getMaterialId(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            a[0] = a[0].add(nz(oi.getReceivedQuantity()));
            a[1] = a[1].add(nz(oi.getDefectReturnedQty()));
            a[2] = a[2].add(nz(oi.getRepairReturnedQty()));
            a[3] = a[3].add(nz(oi.getOrderReturnedQty()));
        }

        // 本单之外、同订单、已审核的「退货退款」已退合计（按物料）
        Map<Long, BigDecimal> refunded = new LinkedHashMap<>();
        List<OutsourceMaterialReturn> others = returnMapper.selectList(new LambdaQueryWrapper<OutsourceMaterialReturn>()
                .eq(OutsourceMaterialReturn::getMaterialOrderId, moId)
                .eq(OutsourceMaterialReturn::getStatus, DocStatus.AUDITED.getCode())
                .ne(OutsourceMaterialReturn::getId, order.getId()));
        for (OutsourceMaterialReturn r : others) {
            // 跳过维修返回（已通过订单行"送修中"体现）与订单退料（已通过订单行"订单退料已退"体现）
            // —— 否则同一批数量会被扣两次。这里只统计"退款已退"。
            if (MaterialReturnType.isRepair(r.getReturnType()) || MaterialReturnType.isOrderReturn(r.getReturnType())) continue;
            for (OutsourceMaterialReturnItem it : itemMapper.selectList(
                    new LambdaQueryWrapper<OutsourceMaterialReturnItem>()
                            .eq(OutsourceMaterialReturnItem::getReturnOrderId, r.getId()))) {
                if (it.getMaterialId() == null) continue;
                refunded.merge(it.getMaterialId(), nz(it.getQuantity()), BigDecimal::add);
            }
        }

        for (OutsourceMaterialReturnItem it : items) {
            if (it.getMaterialId() == null || it.getQuantity() == null
                    || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal[] a = agg.getOrDefault(it.getMaterialId(),
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal already = refunded.getOrDefault(it.getMaterialId(), BigDecimal.ZERO);
            BigDecimal returnable = a[0].subtract(a[1]).subtract(a[2]).subtract(a[3]).subtract(already);
            if (returnable.compareTo(BigDecimal.ZERO) < 0) returnable = BigDecimal.ZERO;
            if (it.getQuantity().compareTo(returnable) > 0)
                throw new BusinessException("退货数量超过该物料订单的可退数量：物料「" + getMaterialName(it.getMaterialId())
                        + "」在订单 " + mo.getCode() + " 上已收 " + a[0].stripTrailingZeros().toPlainString()
                        + "、已退不良 " + a[1].stripTrailingZeros().toPlainString()
                        + "、送修中 " + a[2].stripTrailingZeros().toPlainString()
                        + "、订单退料 " + a[3].stripTrailingZeros().toPlainString()
                        + "、已退货退款 " + already.stripTrailingZeros().toPlainString()
                        + "，可退 " + returnable.stripTrailingZeros().toPlainString()
                        + "，本次 " + it.getQuantity().stripTrailingZeros().toPlainString());
        }
    }

    /**
     * 回补关联物料订单的**只读分摊计划**：按本单该物料「已冻结的订单明细行」分摊本次登记数量，
     * 返回分摊明细 [orderItemId, take]。
     *
     * <p>2026-09-28（草稿口径）：登记只**算**计划（**不写库** —— 草稿不动订单收料数）；
     * 真正回补（收料数 += take、送修中 -= take）在**审核**时由 {@link #applyOrderCredit} 按记录逐条执行
     * （见 {@code applyRepairLegs} 第 ③ 腿）。</p>
     */
    private List<Object[]> planCreditBackToOrder(Long returnOrderId, Long materialId, BigDecimal qty) {
        List<Object[]> out = new ArrayList<>();
        BigDecimal remain = qty;
        List<OutsourceMaterialReturnItem> rows = itemMapper.selectList(new LambdaQueryWrapper<OutsourceMaterialReturnItem>()
                .eq(OutsourceMaterialReturnItem::getReturnOrderId, returnOrderId)
                .eq(OutsourceMaterialReturnItem::getMaterialId, materialId)
                .isNotNull(OutsourceMaterialReturnItem::getMaterialOrderItemId)
                .orderByAsc(OutsourceMaterialReturnItem::getId));
        for (OutsourceMaterialReturnItem r : rows) {
            if (remain.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal credited = creditedQtyByOrderItem(returnOrderId, r.getMaterialOrderItemId());
            BigDecimal avail = nz(r.getQuantity()).subtract(credited);
            if (avail.compareTo(BigDecimal.ZERO) <= 0) continue;
            MaterialOrderItem oi = materialOrderItemMapper.selectById(r.getMaterialOrderItemId());
            if (oi == null) continue;
            BigDecimal take = avail.min(remain);
            out.add(new Object[]{r.getMaterialOrderItemId(), take});
            remain = remain.subtract(take);
        }
        return out;
    }

    /**
     * 审核落账（第 ③ 腿）：把一条返回记录的数量回补到**它冻结的那行**物料订单明细
     * （收料数 += qty、送修中 -= qty，SQL 原子加减）。
     *
     * <p>F7-69（2026-09-20）：**SQL 原子加减**（原为 Java 侧"读-改-写"再 {@code updateById(oi)} 全字段回写
     * ⇒ 同一订单明细行被两张维修返回单并发处理时互相覆盖、数量少记且不报错）。语义与 F7-49 其余 3 处一致：
     * 收料数累加、送修中递减且**不夹零**。</p>
     */
    private void applyOrderCredit(Long orderItemId, BigDecimal qty) {
        String qtySql = qty.toPlainString();
        materialOrderItemMapper.update(null, new LambdaUpdateWrapper<MaterialOrderItem>()
                .eq(MaterialOrderItem::getId, orderItemId)
                .setSql("received_quantity = IFNULL(received_quantity, 0) + (" + qtySql + ")")
                .setSql("repair_returned_qty = GREATEST(IFNULL(repair_returned_qty, 0) - (" + qtySql + "), 0)"));
    }

    /**
     * 反审核回滚（第 ③ 腿的逆）：把回补过的订单收料数退回、送修中加回（与 {@link #applyOrderCredit} 对称）。
     * <p>收料数侧保留原实现的"不为负"夹零语义（该行可能已被别的单据消耗/调整）。</p>
     */
    private void revertOrderCredit(Long orderItemId, BigDecimal qty) {
        String qtySql = qty.toPlainString();
        materialOrderItemMapper.update(null, new LambdaUpdateWrapper<MaterialOrderItem>()
                .eq(MaterialOrderItem::getId, orderItemId)
                .setSql("received_quantity = GREATEST(IFNULL(received_quantity, 0) - (" + qtySql + "), 0)")
                .setSql("repair_returned_qty = IFNULL(repair_returned_qty, 0) + (" + qtySql + ")"));
    }

    /**
     * 该单在该物料订单明细行上**已回补**的数量（= 已**审核**维修返回里挂到该行的数量合计）。
     * <p>2026-09-28（草稿口径）：只算已审核 —— 草稿还没回补订单收料数（回补在审核时执行）。</p>
     */
    private BigDecimal creditedQtyByOrderItem(Long returnOrderId, Long orderItemId) {
        return repairMapper.selectList(new LambdaQueryWrapper<OutsourceMaterialReturnRepair>()
                        .eq(OutsourceMaterialReturnRepair::getReturnOrderId, returnOrderId)
                        .eq(OutsourceMaterialReturnRepair::getMaterialOrderItemId, orderItemId)
                        .eq(OutsourceMaterialReturnRepair::getStatus, DocStatus.AUDITED.getCode()))
                .stream()
                .map(r -> nz(r.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 删除维修返回**草稿**（2026-09-28 用户口径「加工和物料的登记返回都需要审核和反审核」）。
     *
     * <p>草稿尚未落账（没动库存 / 订单收料数 / 在厂行 / 成本）⇒ 直接删记录即可；
     * **已审核的必须先「反审核」**（{@link #unAuditRepairReturn}：对称逆回 + 留痕）再删 ——
     * 原实现允许一步"撤销即逆回+删除"（不留痕），现与「加工返回」{@code revoke} 同一口径。</p>
     *
     * <p>⚠️ 一次登记的实际用料行挂在本次**首条**记录上（见 {@link #repairReturn}）⇒ 删除首条草稿时要把它
     * **改挂**到本单仍在的首条草稿上；本单已无其它草稿才随记录一起删除（否则用料会随删除静默丢失，
     * 后续审核就少了这腿账）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelRepairReturn(Long repairRecordId) {
        OutsourceMaterialReturnRepair row = repairMapper.selectById(repairRecordId);
        if (row == null) throw new BusinessException("维修返回记录不存在");
        if (!DocStatus.DRAFT.getCode().equals(row.getStatus()))
            throw new BusinessException("只有草稿可以删除；已审核的维修返回请先「反审核」（逆回库存/订单收料数并留痕）再删除");
        // F7-138（2026-09-20）：对**来源单据**加行锁（与 repairReturn 同一把锁）⇒ 同单登记/删除串行
        Long lockCid = CompanyContext.get();
        if (lockCid != null && lockCid <= 0) lockCid = null;
        OutsourceMaterialReturn order = returnMapper.selectForUpdate(row.getReturnOrderId(), lockCid);
        if (order == null) throw new BusinessException("退货单不存在");
        if (nzInt(order.getClosedFlag()) == 1)
            throw new BusinessException("该单已结案，如需删除草稿请先「撤销结案」");
        // 用料行改挂（或随记录一起删除）——见本方法 Javadoc
        List<OutsourceMaterialReturnRepairMaterial> mats = repairMaterialMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnRepairMaterial>()
                        .eq(OutsourceMaterialReturnRepairMaterial::getRepairRecordId, repairRecordId)
                        .orderByAsc(OutsourceMaterialReturnRepairMaterial::getId));
        if (!mats.isEmpty()) {
            OutsourceMaterialReturnRepair next = repairMapper.selectOne(new LambdaQueryWrapper<OutsourceMaterialReturnRepair>()
                    .eq(OutsourceMaterialReturnRepair::getReturnOrderId, row.getReturnOrderId())
                    .eq(OutsourceMaterialReturnRepair::getStatus, DocStatus.DRAFT.getCode())
                    .ne(OutsourceMaterialReturnRepair::getId, repairRecordId)
                    .orderByAsc(OutsourceMaterialReturnRepair::getId)
                    .last("LIMIT 1"));
            if (next != null) {
                for (OutsourceMaterialReturnRepairMaterial m : mats) {
                    m.setRepairRecordId(next.getId());
                    repairMaterialMapper.updateById(m);
                }
                log.info("删除维修返回草稿：{} 行实际用料改挂到草稿记录 {}（避免用料丢失）", mats.size(), next.getId());
            } else {
                repairMaterialMapper.delete(new LambdaQueryWrapper<OutsourceMaterialReturnRepairMaterial>()
                        .eq(OutsourceMaterialReturnRepairMaterial::getRepairRecordId, repairRecordId));
            }
        }
        // F7-138：**条件删除 + 判影响行数**（与行锁互为保险）——del==0 ⇒ 抛错 ⇒ 整体回滚
        int del = repairMapper.deleteById(repairRecordId);
        if (del == 0) throw new BusinessException("该维修返回记录已被删除，请刷新后重试");
        log.info("维修返回草稿已删除: id={}, order={}", repairRecordId, order.getCode());
    }

    /**
     * **审核**维修返回（2026-09-28 用户口径）：**审核才落账**，按**一条**返回记录执行四条腿 ——
     * ①物料入指定仓（{@code MATERIAL_REPAIR_IN}）②核销供应商委外仓在厂 {@code MATERIAL_REPAIR} 行
     * （旧单无在厂行 ⇒ 跳过并把 {@code onsite_leg} 记 0）③关联订单未完成时回补该行收料数、冲减「送修中」
     * ④实际用料：扣委外仓子物料（允许扣负）+ FIFO 计价 + Σ用料摊入回仓主物料成本；最后盖审核人章。
     *
     * <p>并发与失败语义：先按本单行锁串行；状态用 {@link DocStatusGuard} **原子抢占** DRAFT→AUDITED
     * （双击/并发只落账一次）；任一步失败**整体回滚**（含状态与已写流水）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditRepairReturn(Long repairRecordId) {
        OutsourceMaterialReturnRepair row = repairMapper.selectById(repairRecordId);
        if (row == null) throw new BusinessException("维修返回记录不存在");
        Long lockCid = CompanyContext.get();
        if (lockCid != null && lockCid <= 0) lockCid = null;
        OutsourceMaterialReturn order = returnMapper.selectForUpdate(row.getReturnOrderId(), lockCid);
        if (order == null) throw new BusinessException("退货单不存在");
        if (nzInt(order.getClosedFlag()) == 1)
            throw new BusinessException("该单已结案，如需审核维修返回请先「撤销结案」");
        // 审核时按**本单**复核一次"不超送修"（登记到审核之间可能又有别的返回被审核）：
        // 本记录此刻仍是草稿（不计入 returnedQtyByMaterial），故直接比较「已审核合计 + 本次」
        BigDecimal qty = nz(row.getQuantity());
        BigDecimal sentQty = sentQtyByMaterial(row.getReturnOrderId()).getOrDefault(row.getMaterialId(), BigDecimal.ZERO);
        BigDecimal already = returnedQtyByMaterial(row.getReturnOrderId()).getOrDefault(row.getMaterialId(), BigDecimal.ZERO);
        if (already.add(qty).compareTo(sentQty) > 0)
            throw new BusinessException("返回数量超过送修数量（物料ID=" + row.getMaterialId() + "：送修 " + sentQty
                    + "、已审核返回 " + already + "、本次 " + qty + "）");
        // 原子抢占 DRAFT→AUDITED（并发/双击只落账一次）
        if (!DocStatusGuard.claim(repairMapper, OutsourceMaterialReturnRepair::getId, repairRecordId,
                OutsourceMaterialReturnRepair::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可以审核");
        row = repairMapper.selectById(repairRecordId);
        applyRepairLegs(order, row, qty);
        log.info("维修返回审核 {} 物料{} × {}（入库 + 在厂核销 + 订单回补 + 实际用料/成本）",
                order.getCode(), row.getMaterialId(), qty);
    }

    /**
     * **反审核**维修返回（2026-09-28 用户口径）：逐腿对称逆回该条记录已落的账，
     * 记录回到**草稿**（留痕可查，区别于"删除草稿"）并清空审核人。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAuditRepairReturn(Long repairRecordId) {
        OutsourceMaterialReturnRepair row = repairMapper.selectById(repairRecordId);
        if (row == null) throw new BusinessException("维修返回记录不存在");
        Long lockCid = CompanyContext.get();
        if (lockCid != null && lockCid <= 0) lockCid = null;
        OutsourceMaterialReturn order = returnMapper.selectForUpdate(row.getReturnOrderId(), lockCid);
        if (order == null) throw new BusinessException("退货单不存在");
        if (nzInt(order.getClosedFlag()) == 1)
            throw new BusinessException("该单已结案，如需反审核维修返回请先「撤销结案」");
        if (!DocStatusGuard.claim(repairMapper, OutsourceMaterialReturnRepair::getId, repairRecordId,
                OutsourceMaterialReturnRepair::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已审核的维修返回可以反审核");
        row = repairMapper.selectById(repairRecordId);
        reverseRepairLegs(order, row);
        // 清空审核人（全站口径 2026-09-23：反审核清空审计信息）
        repairMapper.update(null, new LambdaUpdateWrapper<OutsourceMaterialReturnRepair>()
                .eq(OutsourceMaterialReturnRepair::getId, repairRecordId)
                .set(OutsourceMaterialReturnRepair::getAuditorId, null)
                .set(OutsourceMaterialReturnRepair::getAuditorName, null)
                .set(OutsourceMaterialReturnRepair::getAuditTime, null));
        log.info("维修返回反审核 {} 物料{} × {}（逆回入库/在厂/用料/订单收料数 + 成本反结转）",
                order.getCode(), row.getMaterialId(), row.getQuantity());
    }

    /** 审核落账实现（一条记录，见 {@link #auditRepairReturn} 的四条腿）；must be called inside the source-order lock. */
    private void applyRepairLegs(OutsourceMaterialReturn order, OutsourceMaterialReturnRepair row, BigDecimal qty) {
        Long cid = CompanyContext.get();
        // ① 物料入指定仓（物料库存只有良品一档，无品质维度 ⇒ 固定 GOOD）
        if (row.getWarehouseId() != null && row.getMaterialId() != null && qty.compareTo(BigDecimal.ZERO) > 0) {
            warehouseStockService.changeMaterialStock(row.getWarehouseId(), row.getMaterialId(), qty,
                    StockChangeType.MATERIAL_REPAIR_IN.getCode(), order.getCode(),
                    RelatedBillType.OUTSOURCE_MATERIAL_REPAIR, null, null, order.getId());
        }
        Long supplierWhId = firstOutsourceWarehouseOf(order.getSupplierId());
        // ② 核销在厂 MATERIAL_REPAIR 行（供应商委外仓）。⚠️ 旧单（审核于物料形态化改造前）在厂行为 0 ⇒
        //    本腿被**跳过**，必须把「没核销」落到记录上（onsite_leg=0），否则反审核会凭空给在厂行 +qty。
        boolean onsite = false;
        if (supplierWhId != null && row.getMaterialId() != null && qty.compareTo(BigDecimal.ZERO) > 0) {
            onsite = allocateOnSiteRepair(order, supplierWhId, row.getMaterialId(), qty, cid);
        }
        // ③ 关联订单未完成时（审核送修时已扣过收料数）：本行冻结的订单明细行回补收料数 + 冲减「送修中」
        if (row.getMaterialOrderItemId() != null && nzInt(order.getDeductedFlag()) == 1
                && qty.compareTo(BigDecimal.ZERO) > 0) {
            MaterialOrderItem oi = materialOrderItemMapper.selectById(row.getMaterialOrderItemId());
            if (oi != null) applyOrderCredit(oi.getId(), qty);
        }
        // ④ 实际用料（本记录下挂的用料行）：扣委外仓子物料（允许扣负 —— 供应商已实际耗用）+ FIFO 计价
        //    + Σ用料摊入回仓主物料成本（applyMaterial 按记录定位，反审核按同记录 reverseByBill）
        List<OutsourceMaterialReturnRepairMaterial> mats = repairMaterialMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnRepairMaterial>()
                        .eq(OutsourceMaterialReturnRepairMaterial::getRepairRecordId, row.getId())
                        .orderByAsc(OutsourceMaterialReturnRepairMaterial::getId));
        if (!mats.isEmpty()) {
            if (supplierWhId == null) {
                log.warn("维修返回审核：供应商未配置委外仓，跳过实际用料腿 code={} recordId={} 用料 {} 行",
                        order.getCode(), row.getId(), mats.size());
            } else {
                BigDecimal totalMat = BigDecimal.ZERO;
                for (OutsourceMaterialReturnRepairMaterial m : mats) {
                    if (m.getMaterialId() == null || m.getQuantity() == null
                            || m.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
                    warehouseStockService.changeMaterialStockAllowNegative(supplierWhId, m.getMaterialId(), m.getQuantity().negate(),
                            StockChangeType.MATERIAL_REPAIR_COMPONENT.getCode(), order.getCode(),
                            RelatedBillType.OUTSOURCE_MATERIAL_REPAIR, null, null, order.getId(),
                            WarehouseStock.FORM_MATERIAL);
                    BigDecimal unit = pricingService.fifoPriceWithFallback(m.getMaterialId(), m.getQuantity());
                    BigDecimal amount = unit.multiply(m.getQuantity()).setScale(2, RoundingMode.HALF_UP);
                    OutsourceMaterialReturnRepairMaterial u = new OutsourceMaterialReturnRepairMaterial();
                    u.setId(m.getId());
                    u.setUnitPrice(unit);
                    u.setAmount(amount);
                    repairMaterialMapper.updateById(u);
                    totalMat = totalMat.add(amount);
                }
                if (totalMat.compareTo(BigDecimal.ZERO) > 0 && qty.compareTo(BigDecimal.ZERO) > 0) {
                    costService.applyMaterial(row.getMaterialId(), qty, totalMat.divide(qty, 4, RoundingMode.HALF_UP),
                            StockChangeType.MATERIAL_REPAIR_IN.getCode(), row.getId(), order.getCode());
                }
            }
        }
        // ⑤ 留痕：onsite_leg（反审核据此决定是否恢复在厂）+ 审核人章
        OutsourceMaterialReturnRepair u = new OutsourceMaterialReturnRepair();
        u.setId(row.getId());
        u.setOnsiteLeg(onsite ? 1 : 0);
        u.setAuditorId(getCurrentUserId());
        u.setAuditorName(getCurrentUserName());
        u.setAuditTime(LocalDateTime.now());
        repairMapper.updateById(u);
    }

    /** 反审核逆回实现（一条记录）：与 {@link #applyRepairLegs} 逐腿对称；用料行**保留**（草稿可再次审核）。 */
    private void reverseRepairLegs(OutsourceMaterialReturn order, OutsourceMaterialReturnRepair row) {
        BigDecimal qty = nz(row.getQuantity());
        // ① 扣回已审核入库的物料
        if (row.getWarehouseId() != null && row.getMaterialId() != null && qty.compareTo(BigDecimal.ZERO) > 0) {
            warehouseStockService.changeMaterialStock(row.getWarehouseId(), row.getMaterialId(), qty.negate(),
                    StockChangeType.CANCEL_MATERIAL_REPAIR_IN.getCode(), order.getCode(),
                    RelatedBillType.OUTSOURCE_MATERIAL_REPAIR, null, null, order.getId());
        }
        Long supplierWhId = firstOutsourceWarehouseOf(order.getSupplierId());
        // ② 恢复在厂 MATERIAL_REPAIR 行。2026-09-27 **修复（在厂行只减不还）**：这条腿**与实际用料无关** ——
        //    审核时 allocateOnSiteRepair 是**无条件**核销在厂行的，逆回必须无条件对回（原实现关在
        //    `if (!mats.isEmpty())` 里 ⇒ 没填实际用料的记录逆回后在厂行**永久少记**，实测 ui-e2e-15 S8 命中）。
        //    2026-09-27 **二修（对称性补强）**：但**旧单**审核时本腿被跳过（onsite_leg=0）⇒ 也必须跳过，
        //    否则会给在厂行**凭空 +qty**。
        if (nzInt(row.getOnsiteLeg()) != 0 && supplierWhId != null && row.getMaterialId() != null
                && qty.compareTo(BigDecimal.ZERO) > 0) {
            warehouseStockService.changeMaterialStock(supplierWhId, row.getMaterialId(), qty,
                    StockChangeType.MATERIAL_REPAIR_STOCK_IN.getCode(), order.getCode(),
                    RelatedBillType.OUTSOURCE_MATERIAL_REPAIR, null, null, order.getId(),
                    WarehouseStock.FORM_MATERIAL_REPAIR);
        }
        // ③ 实际用料：回补委外仓子物料 + 成本反结转（用料行保留在记录上，价格清零 = 未落账态）
        List<OutsourceMaterialReturnRepairMaterial> mats = repairMaterialMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnRepairMaterial>()
                        .eq(OutsourceMaterialReturnRepairMaterial::getRepairRecordId, row.getId())
                        .orderByAsc(OutsourceMaterialReturnRepairMaterial::getId));
        if (!mats.isEmpty()) {
            if (supplierWhId == null) throw new BusinessException("该供应商未配置委外仓库，无法反审核维修返回");
            for (OutsourceMaterialReturnRepairMaterial m : mats) {
                if (m.getMaterialId() == null || m.getQuantity() == null
                        || m.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
                warehouseStockService.changeMaterialStockAllowNegative(supplierWhId, m.getMaterialId(), m.getQuantity(),
                        StockChangeType.CANCEL_MATERIAL_REPAIR_COMPONENT.getCode(), order.getCode(),
                        RelatedBillType.OUTSOURCE_MATERIAL_REPAIR, null, null, order.getId(),
                        WarehouseStock.FORM_MATERIAL);
                OutsourceMaterialReturnRepairMaterial u = new OutsourceMaterialReturnRepairMaterial();
                u.setId(m.getId());
                u.setUnitPrice(BigDecimal.ZERO);
                u.setAmount(BigDecimal.ZERO);
                repairMaterialMapper.updateById(u);
            }
            // 成本反结转（按记录 reverseByBill，与审核时 applyMaterial 同单据ID）
            costService.reverseByBill(StockChangeType.MATERIAL_REPAIR_IN.getCode(), row.getId());
        }
        // ④ 关联订单已回补的收料数同步回退（重新变回"送修中"）
        if (row.getMaterialOrderItemId() != null && nzInt(order.getDeductedFlag()) == 1
                && qty.compareTo(BigDecimal.ZERO) > 0) {
            revertOrderCredit(row.getMaterialOrderItemId(), qty);
        }
    }

    // ===== 结案 / 撤销结案（维修返回的收尾动作，2026-09-17） =====

    /**
     * 结案：维修返回单全部送修数量都已返回（未返回 = 0）后人工确认收尾。
     * <p>只有维修返回单需要结案（退货退款审核即终结）。结案后禁登记返回、禁撤销返回、禁反审核。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        // F7-138（2026-09-20）：行锁（与 repairReturn 同一把锁，理由同成品侧）。
        Long lockCid = CompanyContext.get();
        if (lockCid != null && lockCid <= 0) lockCid = null;
        OutsourceMaterialReturn order = returnMapper.selectForUpdate(id, lockCid);
        if (order == null) throw new BusinessException("退货单不存在");
        if (!MaterialReturnType.isRepair(order.getReturnType())) throw new BusinessException("只有维修返回单需要结案");
        if (nzInt(order.getClosedFlag()) == 1) throw new BusinessException("该单已结案");
        if (!DocStatus.AUDITED.getCode().equals(order.getStatus()))
            throw new BusinessException("只有已审核（已送修）的维修返回单才能结案");
        BigDecimal unreturned = unreturnedQty(id);
        if (unreturned.compareTo(BigDecimal.ZERO) > 0)
            throw new BusinessException("还有 " + unreturned.stripTrailingZeros().toPlainString()
                    + " 件未返回，不能结案（供应商尚未修好送回）");
        // F7-138（2026-09-20）：加 `.eq(closedFlag, 0)` 条件 + 判影响行数（与 reOpen 构成对称的条件更新）。
        int upd = returnMapper.update(null, new LambdaUpdateWrapper<OutsourceMaterialReturn>()
                .eq(OutsourceMaterialReturn::getId, id)
                .eq(OutsourceMaterialReturn::getClosedFlag, 0)
                .set(OutsourceMaterialReturn::getClosedFlag, 1)
                .set(OutsourceMaterialReturn::getClosedTime, LocalDateTime.now())
                .set(OutsourceMaterialReturn::getClosedBy, getCurrentUserName()));
        if (upd == 0) throw new BusinessException("该单状态已变化（可能已被结案），请刷新后重试");
    }

    /** 撤销结案：回到"送修中"跟踪状态（可继续登记维修返回） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reOpen(Long id) {
        Long lockCid = CompanyContext.get();
        if (lockCid != null && lockCid <= 0) lockCid = null;
        OutsourceMaterialReturn order = returnMapper.selectForUpdate(id, lockCid);
        if (order == null) throw new BusinessException("退货单不存在");
        if (nzInt(order.getClosedFlag()) != 1) throw new BusinessException("该单未结案");
        int upd = returnMapper.update(null, new LambdaUpdateWrapper<OutsourceMaterialReturn>()
                .eq(OutsourceMaterialReturn::getId, id)
                .eq(OutsourceMaterialReturn::getClosedFlag, 1)
                .set(OutsourceMaterialReturn::getClosedFlag, 0)
                .set(OutsourceMaterialReturn::getClosedTime, null)
                .set(OutsourceMaterialReturn::getClosedBy, null));
        if (upd == 0) throw new BusinessException("该单状态已变化（可能已撤销结案），请刷新后重试");
    }

    /** 未返回量 = 送修合计 − 已返回合计 */
    private BigDecimal unreturnedQty(Long returnOrderId) {
        BigDecimal sent = sentQtyByMaterial(returnOrderId).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returned = returnedQtyByMaterial(returnOrderId).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sent.subtract(returned).max(BigDecimal.ZERO);
    }

    /** 送修量：按物料合计本单明细 */
    private Map<Long, BigDecimal> sentQtyByMaterial(Long returnOrderId) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (OutsourceMaterialReturnItem it : itemMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnItem>()
                        .eq(OutsourceMaterialReturnItem::getReturnOrderId, returnOrderId))) {
            if (it.getMaterialId() == null) continue;
            map.merge(it.getMaterialId(), it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
        }
        return map;
    }

    /**
     * 已返回量：按物料合计**已审核**的维修返回（2026-09-28 草稿口径：草稿不占额度、不计进度）。
     * <p>登记时的"不超送修"校验、审核时的复核、未返回量/结案判定都吃这里的口径 ⇒ 天然只认已审核。</p>
     */
    private Map<Long, BigDecimal> returnedQtyByMaterial(Long returnOrderId) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (OutsourceMaterialReturnRepair r : repairMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnRepair>()
                        .eq(OutsourceMaterialReturnRepair::getReturnOrderId, returnOrderId)
                        .eq(OutsourceMaterialReturnRepair::getStatus, DocStatus.AUDITED.getCode()))) {
            if (r.getMaterialId() == null) continue;
            map.merge(r.getMaterialId(), r.getQuantity() != null ? r.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
        }
        return map;
    }

    /**
     * 退货对象类型校验（2026-09-21 用户口径）：**物料退货只能退给「辅料商 + 供应商」，不能退给供货商（成品商）**。
     * <p>与加工退货同一条业务规则的两半之一（那半边在 {@code OutsourceOrderDeliveryServiceImpl#returnDefectNoOrder}）。
     * 前端下拉已按 {@code excludeSupplierType=product} 过滤（放行 辅料商/方案商/加工厂），这里再兜一道：
     * 前端过滤只是体验，业务规则必须在服务层成立，否则直接调 API 就能绕过。</p>
     */
    private void assertReturnTargetAllowed(Long supplierId) {
        if (supplierId == null) return; // 必填校验由上层/前端负责，这里只判"类型越界"
        Supplier target = supplierMapper.selectById(supplierId);
        if (target == null) throw new BusinessException("退货对象不存在");
        boolean isVendor = supplierTypeRefMapper.selectList(
                        new LambdaQueryWrapper<SupplierTypeRef>().eq(SupplierTypeRef::getSupplierId, supplierId))
                .stream()
                .anyMatch(r -> SupplierTypeEnum.PRODUCT.getCode().equals(r.getTypeCode()));
        if (isVendor)
            throw new BusinessException("「" + target.getName()
                    + "」是供货商（成品商）：物料退货只能退给辅料商或供应商");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asListMap(Object o) {
        return o instanceof List ? (List<Map<String, Object>>) o : new ArrayList<>();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(OutsourceMaterialReturn order, List<Map<String, Object>> itemsRaw) {
        if (itemsRaw == null || itemsRaw.isEmpty()) throw new BusinessException("请添加退货物料");
        // 2026-09-21（用户口径）：只能退给辅料商或供应商，不能退给供货商（成品商）
        assertReturnTargetAllowed(order.getSupplierId());

        order.setCode(generateCode(order.getMaterialOrderId() != null));
        if (order.getReturnDate() == null) order.setReturnDate(LocalDate.now());
        order.setStatus(DocStatus.DRAFT.getCode());
        // 类型归一（空值/历史值 MATERIAL → REFUND 退货退款，2026-09-17）
        order.setReturnType(MaterialReturnType.normalize(order.getReturnType()).getCode());
        // 关联物料订单（2026-09-17 维修返回闭环）：显式传入优先；从「物料收货」按记录发起时
        // 按收料单的来源订单（outsource_delivery.source_order_id）自动带出
        if (order.getMaterialOrderId() == null && order.getSourceDeliveryId() != null) {
            OutsourceDelivery d = outsourceDeliveryMapper.selectById(order.getSourceDeliveryId());
            if (d != null && d.getSourceOrderId() != null) order.setMaterialOrderId(d.getSourceOrderId());
        }
        if (order.getDeductedFlag() == null) order.setDeductedFlag(0);
        if (order.getClosedFlag() == null) order.setClosedFlag(0);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        returnMapper.insert(order);
        saveItems(order.getId(), itemsRaw, order.getReturnType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OutsourceMaterialReturn order, List<Map<String, Object>> itemsRaw) {
        OutsourceMaterialReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("退货单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        // 2026-09-21（用户口径）：改对象时同样受"不能退给供货商（成品商）"约束（updateById 忽略 null ⇒ 不传即保留原值）
        if (order.getSupplierId() != null) assertReturnTargetAllowed(order.getSupplierId());

        order.setId(id);
        order.setCode(null); // 单号不可改
        order.setStatus(null);
        // 类型归一：草稿可改类型（REFUND↔REPAIR），历史值 MATERIAL 落回调成 REFUND
        if (order.getReturnType() != null) order.setReturnType(MaterialReturnType.normalize(order.getReturnType()).getCode());
        returnMapper.updateById(order);

        itemMapper.delete(new LambdaQueryWrapper<OutsourceMaterialReturnItem>().eq(OutsourceMaterialReturnItem::getReturnOrderId, id));
        // 单价口径按**本单最终类型**定（P3）：payload 未传类型时退回该单原类型，避免误按 FIFO 补价
        saveItems(id, itemsRaw, order.getReturnType() != null ? order.getReturnType() : old.getReturnType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        OutsourceMaterialReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        // P2-29：原子抢占 DRAFT→AUDITED，避免并发/双击重复出库与重复生成应付
        if (!DocStatusGuard.claim(returnMapper, OutsourceMaterialReturn::getId, id,
                OutsourceMaterialReturn::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("只有草稿状态可审核");
        }
        if (order.getSupplierId() == null) throw new BusinessException("请选择退回对象供应商");
        if (order.getFromWarehouseId() == null) throw new BusinessException("请选择出库源仓");

        List<OutsourceMaterialReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnItem>().eq(OutsourceMaterialReturnItem::getReturnOrderId, id));
        if (items.isEmpty()) throw new BusinessException("退货单明细不能为空");
        // P2-33：数量必须为正（负数量会反向操作库存与应付）
        for (OutsourceMaterialReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("退货数量必须大于 0（明细行ID=" + it.getId() + "）");
        }

        // 0. 类型三态（2026-09-28 用户口径）：订单退料(ORDER) / 退货退款(REFUND) / 维修返回(REPAIR)，
        //    并做「类型 ↔ 关联订单状态」校验：订单退料仅限**未结单**；退款/维修关联订单时必须**已结单**。
        MaterialReturnType rtype = MaterialReturnType.normalize(order.getReturnType());
        boolean repair = rtype == MaterialReturnType.REPAIR;
        boolean orderReturn = rtype == MaterialReturnType.ORDER;
        MaterialOrder linkedOrder = order.getMaterialOrderId() != null
                ? materialOrderMapper.selectById(order.getMaterialOrderId()) : null;
        if (order.getMaterialOrderId() != null && linkedOrder == null)
            throw new BusinessException("关联的物料订单不存在");
        if (linkedOrder != null && MaterialOrderStatus.CANCELLED.getCode().equals(linkedOrder.getStatus()))
            throw new BusinessException("关联的物料订单已作废，无法审核本单");
        String typeErr = MaterialReturnType.checkOrderStatus(order.getReturnType(),
                linkedOrder != null, linkedOrder != null ? linkedOrder.getStatus() : null);
        if (typeErr != null) throw new BusinessException(typeErr);

        // 1. 物料出源仓（默认扣良品 GOOD 库存）—— 统一走 WarehouseStockService（架构债 A1），
        //    严格口径：源仓库存不足直接抛错（与原先的私有实现行为一致）
        //    类型分流：订单退料/退货退款 = MATERIAL_RETURN_OUT（真退出去，不回来）；维修返回 = MATERIAL_REPAIR_OUT（送修，货会回来）
        String outType = repair ? StockChangeType.MATERIAL_REPAIR_OUT.getCode()
                : StockChangeType.MATERIAL_RETURN_OUT.getCode();
        RelatedBillType outBill = repair ? RelatedBillType.OUTSOURCE_MATERIAL_REPAIR
                : RelatedBillType.OUTSOURCE_MATERIAL_RETURN;
        // F7-71（2026-09-20 立，2026-09-28 扩到订单退料）：**「不超可退」护栏**（F2-2 已在成品退货侧补
        // assertReturnNotOverDelivered，物料侧漏改）。原实现只校验"数量>0 + 源仓库存充足" ⇒ 只要源仓有货就能退，
        // 可对同一批收料反复退 ⇒ 负应付超冲。口径与 deductOrderReceived 对称：
        //   可退 = 该物料在本订单上「已收 − 已退不良 − 送修中 − 订单退料 − 本单之外已审核的退货退款」。
        // 仅当本单**关联了物料订单**时校验（未关联则无订单口径可比）；订单退料与退货退款共用本护栏。
        if (!repair && linkedOrder != null) {
            assertNotOverReturnable(order, items);
        }
        // P2-1 物料版（2026-09-25 物料形态化）：维修送修 = 物料**转移**进供应商委外仓（MATERIAL_REPAIR 形态），
        // 修好前可按仓查询与盘点；核销走维修返回登记（CANCEL_MATERIAL_REPAIR_STOCK_IN）。
        // 供应商未配置委外仓 ⇒ 显式报错（与成品侧 F7-78 同口径，不允许静默跳过）。
        Long supplierWhId = null;
        if (repair) {
            supplierWhId = firstOutsourceWarehouseOf(order.getSupplierId());
            if (supplierWhId == null)
                throw new BusinessException("该供应商未配置委外仓库，无法审核维修送修（请先为该供应商创建委外仓库）");
        }
        for (OutsourceMaterialReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            warehouseStockService.changeMaterialStock(order.getFromWarehouseId(), it.getMaterialId(),
                    it.getQuantity().negate(), outType, order.getCode(),
                    outBill, null, null, order.getId());
            if (repair) {
                warehouseStockService.changeMaterialStock(supplierWhId, it.getMaterialId(), it.getQuantity(),
                        StockChangeType.MATERIAL_REPAIR_STOCK_IN.getCode(), order.getCode(),
                        outBill, null, null, order.getId(), WarehouseStock.FORM_MATERIAL_REPAIR);
            }
        }

        // 2. 账务（2026-09-28 三态定稿）：
        //    · **退货退款 REFUND** ⇒ 生成**对供应商的应收**（P2 口径变更：原先是负向应付冲减）——
        //      物料退给供应商、供应商把货款退给我们 ⇒ 挂应收，收款单按 subjectType=SUPPLIER 核销闭环；
        //    · **维修返回 REPAIR** ⇒ 有**维修费**时生成**对供应商的应付**（P3：供应商向我方收取维修费），
        //      为 0（不收费）则不落账；
        //    · **订单退料 ORDER** ⇒ 只扣源仓 + 扣订单出货/收料数，**不动账务**。
        BigDecimal totalAmount = items.stream()
                .map(it -> it.getAmount() != null ? it.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            if (rtype == MaterialReturnType.REFUND) {
                upsertReceivable(order, totalAmount);
            } else if (rtype == MaterialReturnType.REPAIR) {
                payableHelper.createPayable(order.getSupplierId(), SourceBillType.OUTSOURCE_MATERIAL_REPAIR_FEE.getCode(),
                        order.getCode(), order.getId(), totalAmount, order.getReturnDate(),
                        "委外物料维修费 - " + order.getCode());
            }
        }

        // 3. 关联物料订单（2026-09-28 改口径）：
        //    · **订单退料**（ORDER，仅未结单）⇒ 扣该单收料数 + 记「订单退料已退」（永久，不回来，不跟踪返回）；
        //    · 退货退款 / 维修返回 关联订单时**必须已结单**（上方 checkOrderStatus 已拦）⇒ 不动订单收料数。
        //    审核瞬间把"是否已扣订单收料"冻结进 deducted_flag（反审核按它回滚）。
        Integer deducted = 0;
        if (linkedOrder != null && orderReturn) {
            deductOrderReceived(linkedOrder, order, items, true);
            deducted = 1;
        }

        // 4. 更新状态与审计
        OutsourceMaterialReturn u = new OutsourceMaterialReturn();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditorId(getCurrentUserId());
        u.setAuditorName(getCurrentUserName());
        u.setAuditTime(LocalDateTime.now());
        // 审核瞬间冻结"是否已在订单上扣减收料"（反审核、登记返回都按它决定要不要回滚/回补）
        u.setDeductedFlag(deducted);
        returnMapper.updateById(u);
    }

    /**
     * **幂等落「对供应商的应收」**（P2 2026-09-28）——退货退款审核时调用。
     * <p>台账单号 = 单据号（与加工侧的 {@code OutsourceReturnBackServiceImpl.upsertReceivable} 同范式）：
     * 反审核把台账置 CANCELLED 留痕（金额清零），**再审核按同一单号复用该行**并显式重置各字段
     * （否则会撞 {@code uk_bill_no}）。subjectType=SUPPLIER ⇒ 收款单可直接按供应商核销闭环。</p>
     */
    private void upsertReceivable(OutsourceMaterialReturn order, BigDecimal amount) {
        FinanceReceivable exist = receivableMapper.selectOne(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getBillNo, order.getCode()));
        FinanceReceivable fr = exist != null ? exist : new FinanceReceivable();
        fr.setBillNo(order.getCode());
        fr.setSubjectType(SubjectType.SUPPLIER.getCode());
        fr.setSupplierId(order.getSupplierId());
        Supplier sup = order.getSupplierId() != null ? supplierMapper.selectById(order.getSupplierId()) : null;
        fr.setSupplierName(sup != null ? sup.getName() : "");
        fr.setSourceBillType(SourceBillType.OUTSOURCE_MATERIAL_RETURN.getCode());
        fr.setSourceBillNo(order.getCode());
        fr.setSourceId(order.getId());
        fr.setAmount(amount);
        fr.setPaidAmount(BigDecimal.ZERO);
        fr.setUnpaidAmount(amount);
        fr.setDueDate(order.getReturnDate());
        fr.setStatus(SettlementStatus.UNSETTLED.getCode());
        fr.setRemark("委外物料退货退款（供应商退款） " + order.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) fr.setCompanyId(cid);
        if (exist != null) receivableMapper.updateById(fr);
        else receivableMapper.insert(fr);
    }

    /**
     * 冲回应收（按台账单号定位）：**不存在则 no-op**。
     * <p>两种情况都要安全：①本单从未产生过应收（P2 改造前的旧单、金额为 0 的单）；
     * ②应收已被收款核销 ⇒ 交给 {@link ReceivableHelper#reverseReceivable(String)} 内的
     * "已有收款记录，不可反审核" 护栏报错（正确的资金安全行为）。</p>
     */
    private void reverseReceivableIfAny(String billNo) {
        if (billNo == null || billNo.isBlank()) return;
        FinanceReceivable fr = receivableMapper.selectOne(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getBillNo, billNo));
        if (fr == null) return;
        receivableHelper.reverseReceivable(billNo);
    }

    /**
     * 关联订单时把数量从该订单明细的**收料数**里扣掉（2026-09-28 起两个场景共用）：
     * <ul>
     *   <li>{@code asOrderReturn=true}（**订单退料**，新类型）⇒ 记入 {@code order_returned_qty}
     *       （永久退掉、供应商不用还回来）；</li>
     *   <li>{@code asOrderReturn=false}（**维修返回**的送修，存量/兼容分支）⇒ 记入 {@code repair_returned_qty}
     *       （送修中，登记维修返回时回补）。新口径下维修返回只能关联**已结单**订单 ⇒ 该分支实际不可达，
     *       保留是为了存量单据（改造前已审核、deducted_flag=1）的反审核回滚口径不变。</li>
     * </ul>
     * <p>同时把落点（物料订单明细行）冻结到退货明细的 material_order_item_id 上，之后回补/回滚都按它精确定位。</p>
     */
    private void deductOrderReceived(MaterialOrder mo, OutsourceMaterialReturn order,
                                    List<OutsourceMaterialReturnItem> items, boolean asOrderReturn) {
        String what = asOrderReturn ? "订单退料数量" : "送修数量";
        for (OutsourceMaterialReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            MaterialOrderItem oi = resolveOrderItem(mo, order, it);
            BigDecimal received = nz(oi.getReceivedQuantity());
            BigDecimal defect = nz(oi.getDefectReturnedQty());
            BigDecimal repairing = nz(oi.getRepairReturnedQty());
            BigDecimal ordered = nz(oi.getOrderReturnedQty());
            BigDecimal returnable = received.subtract(defect).subtract(repairing).subtract(ordered);
            if (returnable.compareTo(BigDecimal.ZERO) < 0) returnable = BigDecimal.ZERO;
            if (it.getQuantity().compareTo(returnable) > 0)
                throw new BusinessException(what + "超过该物料订单的可退数量：物料「" + getMaterialName(it.getMaterialId())
                        + "」在订单 " + mo.getCode() + " 上已收 " + received.stripTrailingZeros().toPlainString()
                        + "、已退不良 " + defect.stripTrailingZeros().toPlainString()
                        + "、送修中 " + repairing.stripTrailingZeros().toPlainString()
                        + "、订单退料 " + ordered.stripTrailingZeros().toPlainString()
                        + "，可退 " + returnable.stripTrailingZeros().toPlainString()
                        + "，本次 " + it.getQuantity().stripTrailingZeros().toPlainString());
            // 冻结落点
            it.setMaterialOrderItemId(oi.getId());
            itemMapper.updateById(it);
            // 扣减收料数 + 记「订单退料」或「送修中」
            // F7-49（2026-09-19）：SQL 原子加减（原 Java 侧"读-改-写"并发会互相覆盖）。
            // 此处 received 侧**保持不夹零**（原实现也不夹零：上方 returnable 校验已保证不会减成负数）。
            String qtySql = it.getQuantity().toPlainString();
            String addCol = asOrderReturn ? "order_returned_qty" : "repair_returned_qty";
            materialOrderItemMapper.update(null, new LambdaUpdateWrapper<MaterialOrderItem>()
                    .eq(MaterialOrderItem::getId, oi.getId())
                    .setSql("received_quantity = IFNULL(received_quantity, 0) - (" + qtySql + ")")
                    .setSql(addCol + " = IFNULL(" + addCol + ", 0) + (" + qtySql + ")"));
        }
    }

    /** 送修数量落点解析：优先按来源收料单的订单明细行（精确到行），否则取该订单中同物料的第一行 */
    private MaterialOrderItem resolveOrderItem(MaterialOrder mo, OutsourceMaterialReturn order, OutsourceMaterialReturnItem it) {
        if (order.getSourceDeliveryId() != null) {
            List<OutsourceDeliveryItem> dis = outsourceDeliveryItemMapper.selectList(
                    new LambdaQueryWrapper<OutsourceDeliveryItem>()
                            .eq(OutsourceDeliveryItem::getDeliveryId, order.getSourceDeliveryId())
                            .eq(OutsourceDeliveryItem::getMaterialId, it.getMaterialId()));
            for (OutsourceDeliveryItem di : dis) {
                if (di.getItemId() == null) continue;
                MaterialOrderItem oi = materialOrderItemMapper.selectById(di.getItemId());
                if (oi != null && mo.getId().equals(oi.getOrderId())) return oi;
            }
        }
        MaterialOrderItem oi = materialOrderItemMapper.selectOne(new LambdaQueryWrapper<MaterialOrderItem>()
                .eq(MaterialOrderItem::getOrderId, mo.getId())
                .eq(MaterialOrderItem::getMaterialId, it.getMaterialId())
                .orderByAsc(MaterialOrderItem::getId)
                .last("LIMIT 1"));
        if (oi == null)
            throw new BusinessException("物料「" + getMaterialName(it.getMaterialId())
                    + "」不在关联的物料订单 " + mo.getCode() + " 中，请核对关联订单");
        return oi;
    }

    /** 反审核时回滚订单扣减（审核时按 deducted_flag 冻结过，这里原样加回；按类型回滚到对应列） */
    private void rollbackOrderReceived(OutsourceMaterialReturn order, List<OutsourceMaterialReturnItem> items) {
        // 2026-09-28：订单退料回滚 order_returned_qty；存量维修送修回滚 repair_returned_qty
        boolean orderReturn = MaterialReturnType.isOrderReturn(order.getReturnType());
        String col = orderReturn ? "order_returned_qty" : "repair_returned_qty";
        for (OutsourceMaterialReturnItem it : items) {
            if (it.getMaterialOrderItemId() == null || it.getQuantity() == null
                    || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            MaterialOrderItem oi = materialOrderItemMapper.selectById(it.getMaterialOrderItemId());
            if (oi == null) continue;
            // F7-49（2026-09-19）：SQL 原子加减；回滚列保留原来的"不为负"夹零语义
            String qtySql = it.getQuantity().toPlainString();
            materialOrderItemMapper.update(null, new LambdaUpdateWrapper<MaterialOrderItem>()
                    .eq(MaterialOrderItem::getId, oi.getId())
                    .setSql("received_quantity = IFNULL(received_quantity, 0) + (" + qtySql + ")")
                    .setSql(col + " = GREATEST(IFNULL(" + col + ", 0) - (" + qtySql + "), 0)"));
        }
        // 落点解冻（下次审核重新解析）
        itemMapper.update(null, new LambdaUpdateWrapper<OutsourceMaterialReturnItem>()
                .eq(OutsourceMaterialReturnItem::getReturnOrderId, order.getId())
                .set(OutsourceMaterialReturnItem::getMaterialOrderItemId, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        OutsourceMaterialReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        // P2-29：原子抢占 AUDITED→DRAFT，避免并发反审核重复回补库存与冲销应付
        if (!DocStatusGuard.claim(returnMapper, OutsourceMaterialReturn::getId, id,
                OutsourceMaterialReturn::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("只有已审核状态可取消审核");
        }

        List<OutsourceMaterialReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnItem>().eq(OutsourceMaterialReturnItem::getReturnOrderId, id));
        boolean repair = MaterialReturnType.isRepair(order.getReturnType());
        // 0. 已结案的维修返回单禁止反审核（先撤销结案）
        if (nzInt(order.getClosedFlag()) == 1)
            throw new BusinessException("该单已结案，请先「撤销结案」再反审核");
        // 0.1 维修返回：已经有"**已审核**的维修返回入库"记录的单禁止反审核（否则会与已入库的返还数量打架）。
        // 2026-09-28（草稿口径）：**草稿不算** —— 草稿没落账、可留到反审核之后再审（与「加工返回」同口径）。
        if (repair && repairMapper.selectCount(new LambdaQueryWrapper<OutsourceMaterialReturnRepair>()
                .eq(OutsourceMaterialReturnRepair::getReturnOrderId, id)
                .eq(OutsourceMaterialReturnRepair::getStatus, DocStatus.AUDITED.getCode())) > 0) {
            throw new BusinessException("该单已有维修返回入库记录，请先「反审核」全部维修返回再反审核本单");
        }
        // 0.2 关联物料订单未完成时审核扣过收料数 → 反审核原样回滚（收料数加回、送修中冲减）
        if (nzInt(order.getDeductedFlag()) == 1) rollbackOrderReceived(order, items);
        // 1. 物料回源仓 —— 同样统一走 WarehouseStockService（架构债 A1）
        String backType = repair ? StockChangeType.MATERIAL_REPAIR_OUT_UN_AUDIT.getCode()
                : StockChangeType.CANCEL_MATERIAL_RETURN_OUT.getCode();
        RelatedBillType backBill = repair ? RelatedBillType.OUTSOURCE_MATERIAL_REPAIR
                : RelatedBillType.OUTSOURCE_MATERIAL_RETURN;
        // P2-1 物料版（2026-09-25 物料形态化）：对称核销供应商仓在厂 MATERIAL_REPAIR 行。
        // ⚠️ 存量兼容：改造前审核的旧单从未入过厂（在厂行不存在）⇒ 跳过核销腿 + 留痕；行存在但不足 = 真错账 ⇒ 硬报错。
        Long supplierWhId = repair ? firstOutsourceWarehouseOf(order.getSupplierId()) : null;
        for (OutsourceMaterialReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            warehouseStockService.changeMaterialStock(order.getFromWarehouseId(), it.getMaterialId(),
                    it.getQuantity(), backType, order.getCode(),
                    backBill, null, null, order.getId());
            if (repair) {
                if (supplierWhId == null) {
                    log.warn("送修反审核：旧单（改造前审核）且供应商无委外仓，跳过核销腿 code={} materialId={} qty={}",
                            order.getCode(), it.getMaterialId(), it.getQuantity());
                    continue;
                }
                BigDecimal onSite = warehouseStockService.getMaterialQuantity(supplierWhId, it.getMaterialId(),
                        WarehouseStock.FORM_MATERIAL_REPAIR);
                if (onSite.compareTo(BigDecimal.ZERO) > 0) {
                    if (onSite.compareTo(it.getQuantity()) < 0)
                        throw new BusinessException("在厂物料（维修送修）不足，无法反审核：物料ID=" + it.getMaterialId()
                                + " 当前在厂 " + onSite + "、需核销 " + it.getQuantity());
                    warehouseStockService.changeMaterialStock(supplierWhId, it.getMaterialId(), it.getQuantity().negate(),
                            StockChangeType.CANCEL_MATERIAL_REPAIR_STOCK_IN.getCode(), order.getCode(),
                            backBill, null, null, order.getId(), WarehouseStock.FORM_MATERIAL_REPAIR);
                } else {
                    log.warn("送修反审核：旧单（改造前审核）无在厂 MATERIAL_REPAIR 行，跳过核销腿 code={} materialId={} qty={}",
                            order.getCode(), it.getMaterialId(), it.getQuantity());
                }
            }
        }
        // 2. 冲销账务（2026-09-28 三态定稿）—— 内部都有"已收款/已付款则阻止反审核"的护栏：
        //    · 退货退款 ⇒ 冲回**应收**（按台账单号=单据号定位）；存量旧单挂的是负向应付，故再兜底冲一次应付；
        //    · 维修返回 ⇒ 冲回**维修费应付**（未产生过维修费的，按来源查询为空 ⇒ 安全 no-op）；
        //    · 订单退料 ⇒ 从未生成账务，不必冲。
        MaterialReturnType utype = MaterialReturnType.normalize(order.getReturnType());
        if (utype == MaterialReturnType.REFUND) {
            reverseReceivableIfAny(order.getCode());
            // 存量兼容（P2 改造前审核的退款单）：冲掉当时的负向应付
            payableHelper.reversePayable(id, SourceBillType.OUTSOURCE_MATERIAL_RETURN.getCode());
        } else if (utype == MaterialReturnType.REPAIR) {
            payableHelper.reversePayable(id, SourceBillType.OUTSOURCE_MATERIAL_REPAIR_FEE.getCode());
        }
        // 3. 回草稿
        // 审核信息必须用 UpdateWrapper 显式置 null：updateById 忽略 null 字段，反审核后仍显示审核人/时间
        returnMapper.update(null, new LambdaUpdateWrapper<OutsourceMaterialReturn>()
                .eq(OutsourceMaterialReturn::getId, id)
                .set(OutsourceMaterialReturn::getStatus, DocStatus.DRAFT.getCode())
                .set(OutsourceMaterialReturn::getAuditorId, null)
                .set(OutsourceMaterialReturn::getAuditorName, null)
                .set(OutsourceMaterialReturn::getAuditTime, null)
                .set(OutsourceMaterialReturn::getDeductedFlag, 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        OutsourceMaterialReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("退货单不存在");
        // F7-50（2026-09-19）：原子抢占 DRAFT→CANCELLED（原"先查后改"可与 audit 并发互覆）
        if (!DocStatusGuard.claim(returnMapper, OutsourceMaterialReturn::getId, id,
                OutsourceMaterialReturn::getStatus, DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 原子删除（O-7）：带状态条件的物理删，只有草稿能删；affected=0 说明已被并发删除/审核或状态已变
        int rows = returnMapper.delete(new LambdaQueryWrapper<OutsourceMaterialReturn>()
                .eq(OutsourceMaterialReturn::getId, id)
                .eq(OutsourceMaterialReturn::getStatus, DocStatus.DRAFT.getCode()));
        if (rows == 0) {
            if (returnMapper.selectById(id) == null) throw new BusinessException("退货单不存在");
            throw new BusinessException("只有草稿状态可删除");
        }
        itemMapper.delete(new LambdaQueryWrapper<OutsourceMaterialReturnItem>().eq(OutsourceMaterialReturnItem::getReturnOrderId, id));
    }

    @Override
    public List<Map<String, Object>> warehouseOptions() {
        List<Warehouse> whs = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getStatus, 1));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Warehouse w : whs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.getId());
            m.put("warehouseName", w.getWarehouseName());
            m.put("warehouseCategory", w.getWarehouseCategory());
            m.put("warehouseType", w.getWarehouseType());
            m.put("factoryId", w.getFactoryId());
            result.add(m);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> materialStock(Long warehouseId) {
        List<WarehouseStock> stocks = warehouseStockMapper.selectList(
                new LambdaQueryWrapper<WarehouseStock>()
                        .eq(WarehouseStock::getWarehouseId, warehouseId)
                        .isNotNull(WarehouseStock::getMaterialId));
        Map<Long, BigDecimal> qtyMap = new LinkedHashMap<>();
        for (WarehouseStock s : stocks) {
            if (!QualityType.GOOD.getCode().equals(s.getQualityType())) continue;
            BigDecimal q = s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
            qtyMap.merge(s.getMaterialId(), q, BigDecimal::add);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : qtyMap.entrySet()) {
            if (e.getValue().compareTo(BigDecimal.ZERO) <= 0) continue;
            OutsourceMaterial m = outsourceMaterialMapper.selectById(e.getKey());
            if (m == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialId", m.getId());
            row.put("materialName", m.getMaterialName());
            row.put("materialTypeId", m.getMaterialTypeId());
            row.put("materialTypeName", getMaterialTypeName(m.getMaterialTypeId()));
            row.put("unit", m.getUnit());
            row.put("quantity", e.getValue());
            result.add(row);
        }
        return result;
    }

    /**
     * 从「物料收货」发起退货的预填数据（2026-09-17）：
     * 按收料单带出退回对象（物料商）、出库源仓（该单收货仓）、该单物料明细与「已收 − 已退 = 可退」数量。
     */
    @Override
    public Map<String, Object> returnPrefill(Long deliveryId) {
        if (deliveryId == null) throw new BusinessException("缺少收料单参数");
        OutsourceDelivery d = outsourceDeliveryMapper.selectById(deliveryId);
        if (d == null) throw new BusinessException("收料单不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourceDeliveryId", deliveryId);
        m.put("sourceCode", d.getCode());
        // 退回对象：优先单据上的供应商，其次来源物料订单的供应商（非直发时单据可能为空）
        Long supplierId = d.getSupplierId();
        if (supplierId == null && d.getSourceOrderId() != null) {
            MaterialOrder mo = materialOrderMapper.selectById(d.getSourceOrderId());
            if (mo != null) supplierId = mo.getSupplierId();
        }
        m.put("supplierId", supplierId);
        Supplier s = supplierId != null ? supplierMapper.selectById(supplierId) : null;
        m.put("supplierName", s != null ? s.getName() : "");
        // 关联物料订单（2026-09-17 维修返回闭环）：收料单的来源订单，前端自动带出并按其状态提示
        m.put("materialOrderId", d.getSourceOrderId());
        MaterialOrder mo = d.getSourceOrderId() != null ? materialOrderMapper.selectById(d.getSourceOrderId()) : null;
        m.put("materialOrderCode", mo != null ? mo.getCode() : null);
        m.put("materialOrderStatus", mo != null ? mo.getStatus() : null);
        // 出库源仓 = 收料单的收货仓（退料即从该仓扣回）
        Long whId = d.getToWarehouseId() != null ? d.getToWarehouseId() : d.getFromWarehouseId();
        m.put("fromWarehouseId", whId);
        Warehouse wh = whId != null ? warehouseMapper.selectById(whId) : null;
        m.put("warehouseName", wh != null ? wh.getWarehouseName() : "");

        // 按物料聚合收货数量（同一物料可能拆多行）
        Map<Long, BigDecimal> received = new LinkedHashMap<>();
        Map<Long, OutsourceDeliveryItem> sample = new LinkedHashMap<>();
        for (OutsourceDeliveryItem it : outsourceDeliveryItemMapper.selectList(
                new LambdaQueryWrapper<OutsourceDeliveryItem>().eq(OutsourceDeliveryItem::getDeliveryId, deliveryId))) {
            if (it.getMaterialId() == null) continue;
            received.merge(it.getMaterialId(), it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
            sample.putIfAbsent(it.getMaterialId(), it);
        }
        Map<Long, BigDecimal> returned = returnedQtyByDelivery(deliveryId);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : received.entrySet()) {
            OutsourceDeliveryItem it = sample.get(e.getKey());
            BigDecimal done = returned.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal returnable = e.getValue().subtract(done);
            if (returnable.compareTo(BigDecimal.ZERO) < 0) returnable = BigDecimal.ZERO;
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("materialId", e.getKey());
            line.put("materialName", getMaterialName(e.getKey()));
            line.put("materialTypeId", it != null ? it.getMaterialTypeId() : null);
            line.put("materialTypeName", getMaterialTypeName(it != null ? it.getMaterialTypeId() : null));
            line.put("unit", it != null ? it.getUnit() : null);
            line.put("receivedQty", e.getValue());
            line.put("returnedQty", done);
            line.put("returnableQty", returnable);
            lines.add(line);
        }
        m.put("lines", lines);
        return m;
    }

    /** 已退货数量：按「来源收料单 + 物料」汇总未作废的物料退货单 */
    private Map<Long, BigDecimal> returnedQtyByDelivery(Long deliveryId) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        List<OutsourceMaterialReturn> orders = returnMapper.selectList(new LambdaQueryWrapper<OutsourceMaterialReturn>()
                .eq(OutsourceMaterialReturn::getSourceDeliveryId, deliveryId)
                .ne(OutsourceMaterialReturn::getStatus, DocStatus.CANCELLED.getCode()));
        if (orders.isEmpty()) return map;
        List<Long> ids = orders.stream().map(OutsourceMaterialReturn::getId).toList();
        for (OutsourceMaterialReturnItem it : itemMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnItem>().in(OutsourceMaterialReturnItem::getReturnOrderId, ids))) {
            if (it.getMaterialId() == null) continue;
            map.merge(it.getMaterialId(), it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
        }
        return map;
    }

    /** 来源收料单号（列表/详情展示用，便于追溯） */
    private String sourceDeliveryCode(Long sourceDeliveryId) {
        if (sourceDeliveryId == null) return null;
        OutsourceDelivery d = outsourceDeliveryMapper.selectById(sourceDeliveryId);
        return d != null ? d.getCode() : null;
    }

    @Override
    public BigDecimal fifoPrice(Long materialId, BigDecimal qty) {
        return calcFifoPrice(materialId, qty);
    }

    // ===== 私有方法 =====


    private void saveItems(Long returnOrderId, List<Map<String, Object>> itemsRaw, String returnType) {
        if (itemsRaw == null) return;
        Long cid = CompanyContext.get();
        // P3（2026-09-28）：单价口径**按类型**收口 ——
        //   · 退货退款 REFUND：留空/0 时按 **FIFO** 补全（"该退多少货款"有客观值，原口径不变）；
        //   · 维修返回 REPAIR：单价 = **维修费**（供应商报价）⇒ 留空 = **不收费 0**，
        //     ⚠️ 绝不能按 FIFO 补：否则审核时会拿"货值"生成一笔巨额维修费应付；
        //   · 订单退料 ORDER：不动账务，单价无业务含义 ⇒ 同样按 0，不落无意义金额。
        boolean refund = MaterialReturnType.normalize(returnType) == MaterialReturnType.REFUND;
        for (Map<String, Object> it : itemsRaw) {
            Long materialId = toLong(it.get("materialId"));
            BigDecimal qty = toBigDecimal(it.get("quantity"));
            BigDecimal price = it.get("unitPrice") != null && toBigDecimal(it.get("unitPrice")).compareTo(BigDecimal.ZERO) > 0
                    ? toBigDecimal(it.get("unitPrice"))
                    : (refund ? calcFifoPrice(materialId, qty) : BigDecimal.ZERO);

            OutsourceMaterialReturnItem item = new OutsourceMaterialReturnItem();
            item.setReturnOrderId(returnOrderId);
            item.setMaterialId(materialId);
            item.setMaterialTypeId(toLong(it.get("materialTypeId")));
            item.setUnit((String) it.get("unit"));
            item.setQuantity(qty);
            item.setUnitPrice(price);
            item.setAmount(qty.multiply(price));
            item.setRemark((String) it.get("remark"));
            if (cid != null && cid > 0) item.setCompanyId(cid);
            itemMapper.insert(item);
        }
    }

    private BigDecimal calcFifoPrice(Long materialId, BigDecimal requiredQty) {
        // F7-77（2026-09-20）：收敛到 OutsourceMaterialPricingService.fifoPrice
        // （算法与返回值口径不变：无有效数量返回 0；统一排除 CANCELLED + 批量取明细去 N+1）
        return pricingService.fifoPrice(materialId, requiredQty);
    }

    /** 已返回量：按退货单批量汇总（列表用，避免逐单查）；2026-09-28 起**只算已审核**（草稿未落账） */
    private Map<Long, BigDecimal> returnedQtyByOrders(List<Long> returnOrderIds) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        if (returnOrderIds == null || returnOrderIds.isEmpty()) return map;
        for (OutsourceMaterialReturnRepair r : repairMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnRepair>()
                        .in(OutsourceMaterialReturnRepair::getReturnOrderId, returnOrderIds)
                        .eq(OutsourceMaterialReturnRepair::getStatus, DocStatus.AUDITED.getCode()))) {
            map.merge(r.getReturnOrderId(), nz(r.getQuantity()), BigDecimal::add);
        }
        return map;
    }

    /** 关联物料订单号（列表/详情展示用） */
    private String materialOrderCode(Long materialOrderId) {
        if (materialOrderId == null) return null;
        MaterialOrder mo = materialOrderMapper.selectById(materialOrderId);
        return mo != null ? mo.getCode() : null;
    }

    /** null 视为 0 */
    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** null 视为 0 */
    private int nzInt(Integer v) {
        return v != null ? v : 0;
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

    private String getMaterialName(Long materialId) {
        if (materialId == null) return "";
        OutsourceMaterial m = outsourceMaterialMapper.selectById(materialId);
        return m != null ? m.getMaterialName() : "";
    }

    private String getMaterialTypeName(Long materialTypeId) {
        if (materialTypeId == null) return "-";
        MaterialType bt = materialTypeMapper.selectById(materialTypeId);
        return bt != null ? bt.getTypeName() : "-";
    }

    /**
     * 物料退货单取号（2026-09-25 起按**是否关联物料订单**区分前缀：关联 MRH- / 不关联 MRW-）。
     * 存量已生成的 MR- 单号不变；两类各自独立取号序号。
     */
    private String generateCode(boolean hasOrder) {
        // F7-75③（2026-09-20）：统一走 BillNoSeq（原 `count(*) + 1` 在并发/有删除时序号不可靠，见成品退货单同处注释）
        String prefix = (hasOrder ? BillPrefix.OUTSOURCE_MATERIAL_RETURN_HAS : BillPrefix.OUTSOURCE_MATERIAL_RETURN_NO)
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        OutsourceMaterialReturn last = returnMapper.selectOne(new LambdaQueryWrapper<OutsourceMaterialReturn>()
                .likeRight(OutsourceMaterialReturn::getCode, prefix).orderByDesc(OutsourceMaterialReturn::getCode).last("LIMIT 1"));
        int seq = last != null ? com.beichen.erp.common.BillNoSeq.lastSeq(last.getCode(), prefix) + 1 : 1;
        return com.beichen.erp.common.BillNoSeq.formatUnique(prefix, seq, cand -> returnMapper.selectCount(new LambdaQueryWrapper<OutsourceMaterialReturn>().eq(OutsourceMaterialReturn::getCode, cand)) > 0) /* F7-261 冲突检测+重试 */;
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
