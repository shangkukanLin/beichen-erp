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
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.service.PayableHelper;
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

    private final OutsourceMaterialReturnMapper returnMapper;
    private final OutsourceMaterialReturnItemMapper itemMapper;
    /** 维修返回记录（维修退货单的"回来"腿，2026-09-17） */
    private final OutsourceMaterialReturnRepairMapper repairMapper;
    /** 维修返回实际用料（子物料补料）明细（2026-09-25 物料形态化） */
    private final OutsourceMaterialReturnRepairMaterialMapper repairMaterialMapper;
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
    private final UserMapper userMapper;
    /** F7-77（2026-09-20）：物料单价统一实现（FIFO 口径与此前一致，但统一排除 CANCELLED + 去 N+1） */
    private final com.beichen.erp.outsource.service.OutsourceMaterialPricingService pricingService;

    @Override
    public Page<Map<String, Object>> page(int pageNum, int pageSize, String code, Long supplierId, String status, String returnType, String progress) {
        LambdaQueryWrapper<OutsourceMaterialReturn> w = new LambdaQueryWrapper<OutsourceMaterialReturn>()
                .eq(code != null && !code.isBlank(), OutsourceMaterialReturn::getCode, code)
                .eq(supplierId != null, OutsourceMaterialReturn::getSupplierId, supplierId)
                .eq(status != null && !status.isBlank(), OutsourceMaterialReturn::getStatus, status)
                // 类型页签（2026-09-17）：退货退款 / 维修退货
                .eq(returnType != null && !returnType.isBlank(), OutsourceMaterialReturn::getReturnType, returnType)
                .orderByDesc(OutsourceMaterialReturn::getId);
        // 进度筛选（维修退货，2026-09-17）：
        //   PENDING_RETURN = 已审核、未结案，且「送修合计 > 已返回合计」（还有货在供应商处没回来）
        //   CLOSED         = 已结案（未返回清零并人工确认收尾）
        if (PROGRESS_PENDING_RETURN.equalsIgnoreCase(progress)) {
            w.eq(OutsourceMaterialReturn::getStatus, DocStatus.AUDITED.getCode())
             .eq(OutsourceMaterialReturn::getClosedFlag, 0)
             .apply("(SELECT IFNULL(SUM(i.quantity),0) FROM outsource_material_return_item i WHERE i.return_order_id = outsource_material_return.id)"
                     + " > (SELECT IFNULL(SUM(r.quantity),0) FROM outsource_material_return_repair r WHERE r.return_order_id = outsource_material_return.id)");
        } else if (PROGRESS_CLOSED.equalsIgnoreCase(progress)) {
            w.eq(OutsourceMaterialReturn::getClosedFlag, 1);
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
            // 关联物料订单（2026-09-17 维修退货闭环）：前端展示"已扣减收料 / 靠本单跟踪"
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
            for (OutsourceMaterialReturnItem it : items) {
                BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
                totalQty = totalQty.add(qty);
                if (it.getAmount() != null) totalAmount = totalAmount.add(it.getAmount());
                if (sb.length() > 0) sb.append("、");
                sb.append(it.getMaterialId() != null ? matNameMap.getOrDefault(it.getMaterialId(), "") : "")
                        .append("×").append(qty.stripTrailingZeros().toPlainString());
            }
            m.put("totalQuantity", totalQty);
            m.put("totalAmount", totalAmount);
            m.put("itemSummary", sb.toString());
            // 送修 / 已返回（维修退货跟踪用；退货退款也返回，前端只在维修页签展示）
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
        // 关联物料订单（2026-09-17 维修退货闭环）：是否已在订单上扣减收料（deductedFlag）、订单当前状态
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

        // 维修返回记录（仅维修退货单会有；详情页展示并可撤销）2026-09-17
        List<OutsourceMaterialReturnRepair> repairs = repairMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnRepair>()
                        .eq(OutsourceMaterialReturnRepair::getReturnOrderId, id));
        List<Map<String, Object>> repairList = new ArrayList<>();
        BigDecimal repairReturnedQty = BigDecimal.ZERO;
        for (OutsourceMaterialReturnRepair r : repairs) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", r.getId());
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
            if (r.getQuantity() != null) repairReturnedQty = repairReturnedQty.add(r.getQuantity());
        }
        m.put("repairReturns", repairList);
        m.put("repairReturnedQty", repairReturnedQty);
        // 送修 / 已返回 / 未返回（维修退货的进度口径，与列表、结案校验一致）
        BigDecimal sentQty = items.stream()
                .map(it -> it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        m.put("sentQty", sentQty);
        m.put("unreturnedQty", sentQty.subtract(repairReturnedQty).max(BigDecimal.ZERO));
        return m;
    }

    // ===== 维修返回（维修退货单的"回来"腿，2026-09-17） =====

    /**
     * 登记维修返回：维修退货单已审核（货已送供应商）后，供应商修好分批把物料送回来。
     * <p>登记即生效：物料入指定仓（`MATERIAL_REPAIR_IN`，物料固定良品 GOOD），**不产生任何应付**；
     * 数量按**物料**校验不超过送修量（送修量 = 本单明细数量合计）。</p>
     * <p>关联订单未完成时（审核已扣过收料数）：本次返回同时<b>回补</b>该订单明细的收料数并冲减「送修中」，
     * 订单台账自动闭环（2026-09-17）。</p>
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
            throw new BusinessException("只有维修退货单可以登记维修返回");
        if (!DocStatus.AUDITED.getCode().equals(order.getStatus()))
            throw new BusinessException("只有已审核（已送修）的维修退货单才能登记维修返回");
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
        // P2-1 物料版（2026-09-25 物料形态化）：供应商委外仓（核销在厂 MATERIAL_REPAIR 行 / 扣实际用料子物料）。
        // 旧数据 supplierId 为空或未配委外仓 ⇒ 跳过 P2-1 新腿（存量单审核时也没入过厂），保持原两腿行为。
        Long supplierWhId = firstOutsourceWarehouseOf(order.getSupplierId());
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

            warehouseStockService.changeMaterialStock(whId, materialId, qty,
                    StockChangeType.MATERIAL_REPAIR_IN.getCode(), order.getCode(),
                    RelatedBillType.OUTSOURCE_MATERIAL_REPAIR, null, null, order.getId());

            // 关联订单未完成时回补收料数/冲减送修中（分摊到本单该物料对应的订单明细行；一般为一行）
            List<Object[]> credits = nzInt(order.getDeductedFlag()) == 1
                    ? creditBackToOrder(id, materialId, qty) : Collections.emptyList();
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
            // P2-1 物料版：核销在厂 MATERIAL_REPAIR 行（供应商委外仓），撤销时按记录对称恢复
            if (supplierWhId != null) {
                allocateOnSiteRepair(order, supplierWhId, materialId, qty, cid);
            }
            returned.put(materialId, already.add(qty)); // 同一请求内多行也要累计，避免叠加超退
        }
        if (saved == 0) throw new BusinessException("请填写维修返回数量");

        // P2-1 物料版：实际用料（子物料补料）多行——可超 BOM、允许扣负（供应商已实际耗用），按 FIFO 计价快照，
        // Σ用料按返回量占比摊入回仓主物料（applyMaterial）；**无赔料应收**（与成品维修同口径）。
        if (supplierWhId != null && !savedRows.isEmpty()) {
            List<Map<String, Object>> mats = asListMap(body.get("materials"));
            if (!mats.isEmpty()) {
                BigDecimal totalMat = BigDecimal.ZERO;
                Long anchorRecordId = savedRows.get(0).getId();
                for (Map<String, Object> mat : mats) {
                    Long mid = toLong(mat.get("materialId"));
                    BigDecimal mq = toBigDecimal(mat.get("quantity"));
                    if (mid == null || mq == null || mq.compareTo(BigDecimal.ZERO) <= 0) continue;
                    warehouseStockService.changeMaterialStockAllowNegative(supplierWhId, mid, mq.negate(),
                            StockChangeType.MATERIAL_REPAIR_COMPONENT.getCode(), order.getCode(),
                            RelatedBillType.OUTSOURCE_MATERIAL_REPAIR, null, null, order.getId(),
                            WarehouseStock.FORM_MATERIAL);
                    BigDecimal unit = pricingService.fifoPriceWithFallback(mid, mq);
                    BigDecimal amount = unit.multiply(mq).setScale(2, RoundingMode.HALF_UP);
                    OutsourceMaterialReturnRepairMaterial it = new OutsourceMaterialReturnRepairMaterial();
                    it.setRepairRecordId(anchorRecordId);
                    it.setMaterialId(mid);
                    it.setMaterialName(getMaterialName(mid));
                    OutsourceMaterial m0 = outsourceMaterialMapper.selectById(mid);
                    it.setUnit(m0 != null ? m0.getUnit() : null);
                    it.setQuantity(mq);
                    it.setUnitPrice(unit);
                    it.setAmount(amount);
                    if (cid != null && cid > 0) it.setCompanyId(cid);
                    repairMaterialMapper.insert(it);
                    totalMat = totalMat.add(amount);
                }
                if (totalMat.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal totalQty = savedRows.stream()
                            .map(r -> r.getQuantity() == null ? BigDecimal.ZERO : r.getQuantity())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal unitCost = totalMat.divide(totalQty, 4, RoundingMode.HALF_UP);
                        for (OutsourceMaterialReturnRepair r : savedRows) {
                            costService.applyMaterial(r.getMaterialId(), r.getQuantity(), unitCost,
                                    StockChangeType.MATERIAL_REPAIR_IN.getCode(), r.getId(), order.getCode());
                        }
                    }
                }
            }
        }
    }

    /**
     * P2-1 物料版：核销在厂物料（MATERIAL_REPAIR 行，定位键唯一 ⇒ 精确扣减）。
     * <p>⚠️ 存量兼容：旧单（改造前审核）从未入过厂 ⇒ 在厂行不存在时跳过核销腿并留痕（动作对称）；
     * 行存在但数量不足 = 真错账 ⇒ 硬报错。</p>
     */
    private void allocateOnSiteRepair(OutsourceMaterialReturn order, Long supplierWhId,
                                      Long materialId, BigDecimal qty, Long cid) {
        BigDecimal onSite = warehouseStockService.getMaterialQuantity(supplierWhId, materialId,
                WarehouseStock.FORM_MATERIAL_REPAIR);
        if (onSite.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("维修返回：旧单（改造前审核）无在厂 MATERIAL_REPAIR 行，跳过核销腿 code={} materialId={} qty={}",
                    order.getCode(), materialId, qty);
            return;
        }
        if (onSite.compareTo(qty) < 0)
            throw new BusinessException("在厂物料（维修送修）不足：当前 " + onSite + "、需核销 " + qty);
        warehouseStockService.changeMaterialStock(supplierWhId, materialId, qty.negate(),
                StockChangeType.CANCEL_MATERIAL_REPAIR_STOCK_IN.getCode(), order.getCode(),
                RelatedBillType.OUTSOURCE_MATERIAL_REPAIR, null, null, order.getId(),
                WarehouseStock.FORM_MATERIAL_REPAIR);
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
        if (cid != null && cid > 0) row.setCompanyId(cid);
        repairMapper.insert(row);
        return row;
    }

    /**
     * F7-71（2026-09-20）：**退货退款（REFUND）的「不超可退」校验**（与 {@link #deductOrderReceived} 的送修口径对称）。
     *
     * <p>可退 = 该物料在本订单上「已收 − 已退不良 − 送修中 − 本单之外已审核的退货退款合计」。
     * 缺这道防线时，只要源仓有货即可反复退同一批收料 ⇒ 负应付超冲（把供应商往来冲成负数）。</p>
     *
     * <p>仅在退货单关联了物料订单时调用；同一订单同一物料可能有多行明细，故按物料**聚合**后比较。</p>
     */
    private void assertRefundNotOverReceived(OutsourceMaterialReturn order, List<OutsourceMaterialReturnItem> items) {
        Long moId = order.getMaterialOrderId();
        MaterialOrder mo = materialOrderMapper.selectById(moId);
        if (mo == null) throw new BusinessException("关联的物料订单不存在");

        // 该订单下每个物料的 [已收, 已退不良, 送修中]
        Map<Long, BigDecimal[]> agg = new LinkedHashMap<>();
        for (MaterialOrderItem oi : materialOrderItemMapper.selectList(
                new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, moId))) {
            if (oi.getMaterialId() == null) continue;
            BigDecimal[] a = agg.computeIfAbsent(oi.getMaterialId(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            a[0] = a[0].add(nz(oi.getReceivedQuantity()));
            a[1] = a[1].add(nz(oi.getDefectReturnedQty()));
            a[2] = a[2].add(nz(oi.getRepairReturnedQty()));
        }

        // 本单之外、同订单、已审核的「退货退款」已退合计（按物料）
        Map<Long, BigDecimal> refunded = new LinkedHashMap<>();
        List<OutsourceMaterialReturn> others = returnMapper.selectList(new LambdaQueryWrapper<OutsourceMaterialReturn>()
                .eq(OutsourceMaterialReturn::getMaterialOrderId, moId)
                .eq(OutsourceMaterialReturn::getStatus, DocStatus.AUDITED.getCode())
                .ne(OutsourceMaterialReturn::getId, order.getId()));
        for (OutsourceMaterialReturn r : others) {
            if (MaterialReturnType.isRepair(r.getReturnType())) continue;
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
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal already = refunded.getOrDefault(it.getMaterialId(), BigDecimal.ZERO);
            BigDecimal returnable = a[0].subtract(a[1]).subtract(a[2]).subtract(already);
            if (returnable.compareTo(BigDecimal.ZERO) < 0) returnable = BigDecimal.ZERO;
            if (it.getQuantity().compareTo(returnable) > 0)
                throw new BusinessException("退货数量超过该物料订单的可退数量：物料「" + getMaterialName(it.getMaterialId())
                        + "」在订单 " + mo.getCode() + " 上已收 " + a[0].stripTrailingZeros().toPlainString()
                        + "、已退不良 " + a[1].stripTrailingZeros().toPlainString()
                        + "、送修中 " + a[2].stripTrailingZeros().toPlainString()
                        + "、已退货退款 " + already.stripTrailingZeros().toPlainString()
                        + "，可退 " + returnable.stripTrailingZeros().toPlainString()
                        + "，本次 " + it.getQuantity().stripTrailingZeros().toPlainString());
        }
    }

    /**
     * 回补关联物料订单：按本单该物料「已冻结的订单明细行」分摊本次返回数量，
     * 每行回补 收料数 += take、送修中 -= take。返回分摊明细 [orderItemId, take]。
     */
    private List<Object[]> creditBackToOrder(Long returnOrderId, Long materialId, BigDecimal qty) {
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
            // F7-69（2026-09-20）：改 **SQL 原子加减**（原为 Java 侧"读-改-写"后 `updateById(oi)` 全字段回写
            // ⇒ 同一订单明细行被两张维修返回单并发登记时互相覆盖、数量少记且不报错）。这是 F7-49 的
            // **第 4 处漏改**（其余 3 处已改）。语义与原实现完全等价：收料数累加、送修中递减且**不夹零**。
            String takeSql = take.toPlainString();
            materialOrderItemMapper.update(null, new LambdaUpdateWrapper<MaterialOrderItem>()
                    .eq(MaterialOrderItem::getId, oi.getId())
                    .setSql("received_quantity = IFNULL(received_quantity, 0) + (" + takeSql + ")")
                    .setSql("repair_returned_qty = GREATEST(IFNULL(repair_returned_qty, 0) - (" + takeSql + "), 0)"));
            out.add(new Object[]{r.getMaterialOrderItemId(), take});
            remain = remain.subtract(take);
        }
        return out;
    }

    /** 该单在该物料订单明细行上已回补的数量（= 已登记维修返回里挂到该行的数量合计） */
    private BigDecimal creditedQtyByOrderItem(Long returnOrderId, Long orderItemId) {
        return repairMapper.selectList(new LambdaQueryWrapper<OutsourceMaterialReturnRepair>()
                        .eq(OutsourceMaterialReturnRepair::getReturnOrderId, returnOrderId)
                        .eq(OutsourceMaterialReturnRepair::getMaterialOrderItemId, orderItemId))
                .stream()
                .map(r -> nz(r.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 撤销维修返回：把已入库物料扣回并删除该条记录（不校验单据状态，作废/草稿单也可清理） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelRepairReturn(Long repairRecordId) {
        OutsourceMaterialReturnRepair row = repairMapper.selectById(repairRecordId);
        if (row == null) throw new BusinessException("维修返回记录不存在");
        // F7-138（2026-09-20）：对**来源单据**加行锁（与 repairReturn 同一把锁）⇒ 同单登记/撤销串行；
        // 否则并发双击两次都读到该 row ⇒ 两次 changeMaterialStock(-qty) ⇒ **重复扣减**。
        Long lockCid = CompanyContext.get();
        if (lockCid != null && lockCid <= 0) lockCid = null;
        OutsourceMaterialReturn order = returnMapper.selectForUpdate(row.getReturnOrderId(), lockCid);
        if (order == null) throw new BusinessException("退货单不存在");
        if (nzInt(order.getClosedFlag()) == 1)
            throw new BusinessException("该单已结案，如需撤销返回请先「撤销结案」");
        if (row.getWarehouseId() != null && row.getMaterialId() != null
                && row.getQuantity() != null && row.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            warehouseStockService.changeMaterialStock(row.getWarehouseId(), row.getMaterialId(), row.getQuantity().negate(),
                    StockChangeType.CANCEL_MATERIAL_REPAIR_IN.getCode(), order.getCode(),
                    RelatedBillType.OUTSOURCE_MATERIAL_REPAIR, null, null, order.getId());
        }
        // P2-1 物料版（2026-09-25 物料形态化）：对称逆回 —— 恢复供应商仓在厂 MATERIAL_REPAIR 行 + 回补实际用料子物料
        // + 成本反结转（按记录 reverseByBill，与登记时 applyMaterial 同单据ID）。旧行无在厂腿（存量）⇒ 跳过恢复。
        List<OutsourceMaterialReturnRepairMaterial> mats = repairMaterialMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnRepairMaterial>()
                        .eq(OutsourceMaterialReturnRepairMaterial::getRepairRecordId, repairRecordId)
                        .orderByAsc(OutsourceMaterialReturnRepairMaterial::getId));
        Long supplierWhId = firstOutsourceWarehouseOf(order.getSupplierId());
        if (!mats.isEmpty()) {
            if (supplierWhId == null) throw new BusinessException("该供应商未配置委外仓库，无法撤销维修返回");
            // 恢复在厂 MATERIAL_REPAIR 行（登记时核销了多少就恢复多少）
            if (row.getMaterialId() != null && row.getQuantity() != null
                    && row.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                warehouseStockService.changeMaterialStock(supplierWhId, row.getMaterialId(), row.getQuantity(),
                        StockChangeType.MATERIAL_REPAIR_STOCK_IN.getCode(), order.getCode(),
                        RelatedBillType.OUTSOURCE_MATERIAL_REPAIR, null, null, order.getId(),
                        WarehouseStock.FORM_MATERIAL_REPAIR);
            }
            // 回补实际用料子物料
            for (OutsourceMaterialReturnRepairMaterial m : mats) {
                warehouseStockService.changeMaterialStockAllowNegative(supplierWhId, m.getMaterialId(), m.getQuantity(),
                        StockChangeType.CANCEL_MATERIAL_REPAIR_COMPONENT.getCode(), order.getCode(),
                        RelatedBillType.OUTSOURCE_MATERIAL_REPAIR, null, null, order.getId(),
                        WarehouseStock.FORM_MATERIAL);
            }
            // 成本反结转（删除批次并反加权；必须在删除记录前、以记录ID 定位）
            costService.reverseByBill(StockChangeType.MATERIAL_REPAIR_IN.getCode(), repairRecordId);
            repairMaterialMapper.delete(new LambdaQueryWrapper<OutsourceMaterialReturnRepairMaterial>()
                    .eq(OutsourceMaterialReturnRepairMaterial::getRepairRecordId, repairRecordId));
        }
        // 关联订单已回补的收料数同步回退（重新变回"送修中"）
        if (row.getMaterialOrderItemId() != null && row.getQuantity() != null) {
            MaterialOrderItem oi = materialOrderItemMapper.selectById(row.getMaterialOrderItemId());
            if (oi != null) {
                // F7-49（2026-09-19）：SQL 原子加减；received 侧保留原来的"不为负"夹零语义
                String qtySql = row.getQuantity().toPlainString();
                materialOrderItemMapper.update(null, new LambdaUpdateWrapper<MaterialOrderItem>()
                        .eq(MaterialOrderItem::getId, oi.getId())
                        .setSql("received_quantity = GREATEST(IFNULL(received_quantity, 0) - (" + qtySql + "), 0)")
                        .setSql("repair_returned_qty = IFNULL(repair_returned_qty, 0) + (" + qtySql + ")"));
            }
        }
        // F7-138（2026-09-20）：**条件删除 + 判影响行数**（与行锁互为保险）—— 只有真正删到这一行的请求
        // 才算撤销成功；del==0 ⇒ 抛错 ⇒ 事务回滚 ⇒ 上面的 changeMaterialStock(-qty) 与订单收料数回退一并撤销。
        int del = repairMapper.deleteById(repairRecordId);
        if (del == 0) throw new BusinessException("该维修返回记录已被撤销，请刷新后重试");
    }

    // ===== 结案 / 撤销结案（维修退货的收尾动作，2026-09-17） =====

    /**
     * 结案：维修退货单全部送修数量都已返回（未返回 = 0）后人工确认收尾。
     * <p>只有维修退货单需要结案（退货退款审核即终结）。结案后禁登记返回、禁撤销返回、禁反审核。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        // F7-138（2026-09-20）：行锁（与 repairReturn 同一把锁，理由同成品侧）。
        Long lockCid = CompanyContext.get();
        if (lockCid != null && lockCid <= 0) lockCid = null;
        OutsourceMaterialReturn order = returnMapper.selectForUpdate(id, lockCid);
        if (order == null) throw new BusinessException("退货单不存在");
        if (!MaterialReturnType.isRepair(order.getReturnType())) throw new BusinessException("只有维修退货单需要结案");
        if (nzInt(order.getClosedFlag()) == 1) throw new BusinessException("该单已结案");
        if (!DocStatus.AUDITED.getCode().equals(order.getStatus()))
            throw new BusinessException("只有已审核（已送修）的维修退货单才能结案");
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

    /** 已返回量：按物料合计已登记的维修返回 */
    private Map<Long, BigDecimal> returnedQtyByMaterial(Long returnOrderId) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (OutsourceMaterialReturnRepair r : repairMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnRepair>()
                        .eq(OutsourceMaterialReturnRepair::getReturnOrderId, returnOrderId))) {
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
        // 关联物料订单（2026-09-17 维修退货闭环）：显式传入优先；从「物料收货」按记录发起时
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
        saveItems(order.getId(), itemsRaw);
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
        saveItems(id, itemsRaw);
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

        // 1. 物料出源仓（默认扣良品 GOOD 库存）—— 统一走 WarehouseStockService（架构债 A1），
        //    严格口径：源仓库存不足直接抛错（与原先的私有实现行为一致）
        //    类型分流（2026-09-17）：退货退款 = MATERIAL_RETURN_OUT；维修退货 = MATERIAL_REPAIR_OUT（送修，货会回来）
        boolean repair = MaterialReturnType.isRepair(order.getReturnType());
        String outType = repair ? StockChangeType.MATERIAL_REPAIR_OUT.getCode()
                : StockChangeType.MATERIAL_RETURN_OUT.getCode();
        RelatedBillType outBill = repair ? RelatedBillType.OUTSOURCE_MATERIAL_REPAIR
                : RelatedBillType.OUTSOURCE_MATERIAL_RETURN;
        // F7-71（2026-09-20）：**退货退款补「不超可退」护栏**（F2-2 已在成品退货侧补 assertReturnNotOverDelivered，
        // 物料侧漏改）。原实现只校验"数量>0 + 源仓库存充足" ⇒ 只要源仓有货就能退，可对同一批收料反复退
        // ⇒ 负应付超冲。口径与 deductOrderReceived（送修）对称：
        //   可退 = 该物料在本订单上「已收 − 已退不良 − 送修中 − 本单之外已审核的退货退款」。
        // 仅当本单**关联了物料订单**时校验（未关联则无订单口径可比 —— 现网 6 张退货单均未关联 ⇒ 零影响）。
        if (!repair && order.getMaterialOrderId() != null) {
            assertRefundNotOverReceived(order, items);
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

        // 2. 冲减应付（负向应付，允许负应付）—— **仅退货退款**；
        //    维修退货只是把物料送去维修、修好会还回来，不冲减应付（用户口径 2026-09-17：不收费、不动应付）
        BigDecimal totalAmount = items.stream()
                .map(it -> it.getAmount() != null ? it.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!repair && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            payableHelper.createPayable(order.getSupplierId(), SourceBillType.OUTSOURCE_MATERIAL_RETURN.getCode(),
                    order.getCode(), order.getId(), totalAmount.negate(), order.getReturnDate(),
                    "委外物料退货 - " + order.getCode());
        }

        // 3. 关联物料订单（2026-09-17 维修退货闭环，三种情况按订单是否完成分流）：
        //    ①订单未完成(RECEIVING) → 扣减该订单明细的收料数（净收料 = 收料总数 − 送修数）+ 记「送修中」，
        //      修好「登记维修返回」时自动回补，订单台账闭环；审核瞬间按订单状态冻结进 deducted_flag。
        //    ②订单已完成(FINISHED) / ③未关联订单 → 不动订单，返回情况靠本单「送修/已返回」+ 结案跟踪。
        Integer deducted = 0;
        if (repair && order.getMaterialOrderId() != null) {
            MaterialOrder mo = materialOrderMapper.selectById(order.getMaterialOrderId());
            if (mo == null) throw new BusinessException("关联的物料订单不存在");
            if (MaterialOrderStatus.CANCELLED.getCode().equals(mo.getStatus()))
                throw new BusinessException("关联的物料订单已作废，无法审核本单");
            if (MaterialOrderStatus.RECEIVING.getCode().equals(mo.getStatus())) {
                deductOrderReceived(mo, order, items);
                deducted = 1;
            }
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
     * 情况①：关联订单未完成 —— 把送修数量从该订单明细的收料数里扣掉，并记入「送修中」。
     * <p>同时把落点（物料订单明细行）冻结到退货明细的 material_order_item_id 上，
     * 之后登记返回/反审核都按它精确回补或回滚。</p>
     */
    private void deductOrderReceived(MaterialOrder mo, OutsourceMaterialReturn order, List<OutsourceMaterialReturnItem> items) {
        for (OutsourceMaterialReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            MaterialOrderItem oi = resolveOrderItem(mo, order, it);
            BigDecimal received = nz(oi.getReceivedQuantity());
            BigDecimal defect = nz(oi.getDefectReturnedQty());
            BigDecimal repairing = nz(oi.getRepairReturnedQty());
            BigDecimal returnable = received.subtract(defect).subtract(repairing);
            if (returnable.compareTo(BigDecimal.ZERO) < 0) returnable = BigDecimal.ZERO;
            if (it.getQuantity().compareTo(returnable) > 0)
                throw new BusinessException("送修数量超过该物料订单的可退数量：物料「" + getMaterialName(it.getMaterialId())
                        + "」在订单 " + mo.getCode() + " 上已收 " + received.stripTrailingZeros().toPlainString()
                        + "、已退不良 " + defect.stripTrailingZeros().toPlainString()
                        + "、送修中 " + repairing.stripTrailingZeros().toPlainString()
                        + "，可退 " + returnable.stripTrailingZeros().toPlainString()
                        + "，本次 " + it.getQuantity().stripTrailingZeros().toPlainString());
            // 冻结落点
            it.setMaterialOrderItemId(oi.getId());
            itemMapper.updateById(it);
            // 扣减收料数 + 记送修中（净收料 = 收料总数 − 送修数）
            // F7-49（2026-09-19）：SQL 原子加减（原 Java 侧"读-改-写"并发会互相覆盖）。
            // 此处 received 侧**保持不夹零**（原实现也不夹零：上方 returnable 校验已保证不会减成负数）。
            String qtySql = it.getQuantity().toPlainString();
            materialOrderItemMapper.update(null, new LambdaUpdateWrapper<MaterialOrderItem>()
                    .eq(MaterialOrderItem::getId, oi.getId())
                    .setSql("received_quantity = IFNULL(received_quantity, 0) - (" + qtySql + ")")
                    .setSql("repair_returned_qty = IFNULL(repair_returned_qty, 0) + (" + qtySql + ")"));
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

    /** 反审核时回滚情况①的订单扣减（审核时按 deducted_flag 冻结过，这里原样加回） */
    private void rollbackOrderReceived(OutsourceMaterialReturn order, List<OutsourceMaterialReturnItem> items) {
        for (OutsourceMaterialReturnItem it : items) {
            if (it.getMaterialOrderItemId() == null || it.getQuantity() == null
                    || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            MaterialOrderItem oi = materialOrderItemMapper.selectById(it.getMaterialOrderItemId());
            if (oi == null) continue;
            // F7-49（2026-09-19）：SQL 原子加减；repair_returned_qty 保留原来的"不为负"夹零语义
            String qtySql = it.getQuantity().toPlainString();
            materialOrderItemMapper.update(null, new LambdaUpdateWrapper<MaterialOrderItem>()
                    .eq(MaterialOrderItem::getId, oi.getId())
                    .setSql("received_quantity = IFNULL(received_quantity, 0) + (" + qtySql + ")")
                    .setSql("repair_returned_qty = GREATEST(IFNULL(repair_returned_qty, 0) - (" + qtySql + "), 0)"));
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
        // 0. 已结案的维修退货单禁止反审核（先撤销结案）
        if (nzInt(order.getClosedFlag()) == 1)
            throw new BusinessException("该单已结案，请先「撤销结案」再反审核");
        // 0.1 维修退货：已经有"维修返回入库"记录的单禁止反审核（否则会与已入库的返还数量打架）
        if (repair && repairMapper.selectCount(new LambdaQueryWrapper<OutsourceMaterialReturnRepair>()
                .eq(OutsourceMaterialReturnRepair::getReturnOrderId, id)) > 0) {
            throw new BusinessException("该单已有维修返回记录，请先撤销全部维修返回再反审核");
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
        // 2. 冲销应付（内部校验已付款则阻止）—— 仅退货退款有应付；维修退货没动过应付，不用冲
        if (!repair) payableHelper.reversePayable(id, SourceBillType.OUTSOURCE_MATERIAL_RETURN.getCode());
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
        // 关联物料订单（2026-09-17 维修退货闭环）：收料单的来源订单，前端自动带出并按其状态提示
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


    private void saveItems(Long returnOrderId, List<Map<String, Object>> itemsRaw) {
        if (itemsRaw == null) return;
        Long cid = CompanyContext.get();
        for (Map<String, Object> it : itemsRaw) {
            Long materialId = toLong(it.get("materialId"));
            BigDecimal qty = toBigDecimal(it.get("quantity"));
            BigDecimal price = it.get("unitPrice") != null && toBigDecimal(it.get("unitPrice")).compareTo(BigDecimal.ZERO) > 0
                    ? toBigDecimal(it.get("unitPrice"))
                    : calcFifoPrice(materialId, qty);

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

    /** 已返回量：按退货单批量汇总（列表用，避免逐单查） */
    private Map<Long, BigDecimal> returnedQtyByOrders(List<Long> returnOrderIds) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        if (returnOrderIds == null || returnOrderIds.isEmpty()) return map;
        for (OutsourceMaterialReturnRepair r : repairMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnRepair>()
                        .in(OutsourceMaterialReturnRepair::getReturnOrderId, returnOrderIds))) {
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
        return com.beichen.erp.common.BillNoSeq.format(prefix, seq);
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
