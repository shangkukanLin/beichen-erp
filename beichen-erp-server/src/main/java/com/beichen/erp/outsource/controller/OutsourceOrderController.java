package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderMaterial;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.common.MaterialOrderStatus;
import com.beichen.erp.outsource.common.MaterialRequirementCalc;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.outsource.service.BomSnapshotService;
import com.beichen.erp.outsource.service.OutsourceOrderService;
import com.beichen.erp.outsource.service.SupplierMaterialService;
import com.beichen.erp.outsource.mapper.OutsourceOrderMapper;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryMapper;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryItemMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderDeliveryMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialComponentMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceMaterialComponent;
import com.beichen.erp.outsource.entity.OutsourceDelivery;
import com.beichen.erp.outsource.entity.OutsourceDeliveryItem;
import com.beichen.erp.outsource.entity.OutsourceOrderDelivery;
import com.beichen.erp.outsource.entity.MaterialOrder;
import com.beichen.erp.outsource.entity.MaterialOrderItem;
import com.beichen.erp.outsource.mapper.MaterialOrderMapper;
import com.beichen.erp.outsource.mapper.MaterialOrderItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/outsource/order")
@RequiredArgsConstructor
public class OutsourceOrderController {

    private final OutsourceOrderService orderService;
    private final OutsourceOrderMapper orderMapper;
    private final ProjectMapper projectMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseStockMapper warehouseStockMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final com.beichen.erp.dev.mapper.MaterialTypeMapper materialTypeMapper;
    private final OutsourceDeliveryMapper deliveryMapper;
    private final OutsourceDeliveryItemMapper deliveryItemMapper;
    private final OutsourceOrderDeliveryMapper orderDeliveryMapper;
    private final OutsourceMaterialComponentMapper componentMapper;
    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;
    private final SupplierMaterialService supplierMaterialService;
    private final BomSnapshotService bomSnapshotService;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(orderService.page(status, factoryId, code, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> getById(@PathVariable Long id) {
        OutsourceOrder o = orderService.getById(id);
        // 不存在（含被多租户隔离而查不到）统一返回 404，与 GlobalExceptionHandler 的 404 语义一致；
        // 原先 R.ok(null) 会让前端呈现空白页而不给出任何提示（见 §12.29 观察项 B）
        if (o == null) throw new BusinessException(404, "加工单不存在或无权访问");
        Map<String, Object> m = new HashMap<>();
        m.put("id", o.getId()); m.put("code", o.getCode()); m.put("status", o.getStatus());
        m.put("supplyMode", o.getSupplyMode());
        m.put("factoryId", o.getFactoryId());
        m.put("planStartDate", o.getPlanStartDate()); m.put("planEndDate", o.getPlanEndDate());
        m.put("actualStartDate", o.getActualStartDate()); m.put("actualEndDate", o.getActualEndDate());
        m.put("taxIncluded", o.getTaxIncluded()); m.put("taxRate", o.getTaxRate()); m.put("taxAmount", o.getTaxAmount());
        m.put("totalAmount", o.getTotalAmount()); m.put("remark", o.getRemark());
        m.put("attachUrl", o.getAttachUrl());
        m.put("logisticsCompany", o.getLogisticsCompany());
        m.put("logisticsNo", o.getLogisticsNo());
        m.put("createTime", o.getCreateTime());
        return R.ok(m);
    }

    @GetMapping("/{id}/products")
    public R<List<Map<String, Object>>> getProducts(@PathVariable Long id) {
        List<OutsourceOrderProduct> products = orderService.getProducts(id);
        List<Map<String, Object>> list = new ArrayList<>();
        for (OutsourceOrderProduct p : products) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("id", p.getId()); pm.put("orderId", p.getOrderId()); pm.put("projectId", p.getProjectId());
            // 产品主数据ID(product.id)，退不良库存匹配使用
            pm.put("productId", p.getProductId());
            pm.put("productName", p.getProductName());
            pm.put("quantity", p.getQuantity()); pm.put("unitPrice", p.getUnitPrice());
            pm.put("amount", p.getAmount()); pm.put("remark", p.getRemark());
            // 项目名
            if (p.getProjectId() != null) {
                Project proj = projectMapper.selectById(p.getProjectId());
                pm.put("projectName", proj != null ? proj.getName() : "");
            }
            // 物料
            List<OutsourceOrderMaterial> materials = orderService.getMaterials(p.getId());
            pm.put("materials", materials);
            // 所用 BOM 快照（2026-09-17 重构）：同 BOM 版本同内容的多张加工单共享一份快照，前端显示「BOM 版本 vN」
            pm.put("bomSnapshotId", p.getBomSnapshotId());
            com.beichen.erp.outsource.entity.BomSnapshot snap = bomSnapshotService.snapshotOfOrderProduct(p.getId());
            if (snap != null) {
                pm.put("bomVersion", snap.getBomVersion());
                pm.put("bomSnapshotKind", snap.getKind());
                pm.put("bomSnapshotTime", snap.getCreateTime());
            }
            list.add(pm);
        }
        return R.ok(list);
    }

    /** BOM物料库存及缺料 */
    @GetMapping("/{id}/material-stock")
    public R<Map<String, Object>> materialStock(@PathVariable Long id) {
        OutsourceOrder order = orderService.getById(id);
        if (order == null) throw new BusinessException(404, "加工单不存在或无权访问");
        // 找到工厂的委外仓库
        List<Warehouse> whs = warehouseMapper.selectList(
            new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getFactoryId, order.getFactoryId()));
        Long whId = whs.isEmpty() ? null : whs.get(0).getId();

        List<OutsourceOrderProduct> products = orderService.getProducts(id);
        // 汇总所有物料（按物料ID合并需求量）
        Map<Long, Map<String, Object>> matMap = new java.util.LinkedHashMap<>();
        for (OutsourceOrderProduct p : products) {
            List<OutsourceOrderMaterial> mats = orderService.getMaterials(p.getId());
            for (OutsourceOrderMaterial mat : mats) {
                Long key = mat.getMaterialId();
                if (key == null) continue;
                if (matMap.containsKey(key)) {
                    Map<String, Object> existing = matMap.get(key);
                    BigDecimal oldDemand = (BigDecimal) existing.get("demandQuantity");
                    existing.put("demandQuantity", oldDemand.add(mat.getDemandQuantity() != null ? mat.getDemandQuantity() : BigDecimal.ZERO));
                } else {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("materialName", mat.getMaterialId() != null ? getMaterialNameById(mat.getMaterialId()) : "");
                    m.put("materialTypeName", getMaterialTypeNameById(mat.getMaterialTypeId()));
                    m.put("unit", mat.getUnit());
                    m.put("materialId", mat.getMaterialId());
                    m.put("demandQuantity", mat.getDemandQuantity() != null ? mat.getDemandQuantity() : BigDecimal.ZERO);
                    // 查物料关联的供应商（从 supplier_material 居间表实时联查）
                    if (mat.getMaterialId() != null) {
                        m.put("supplierIds", supplierMaterialService.listSupplierIdsByMaterial(mat.getMaterialId()));
                    }
                    matMap.put(key, m);
                }
            }
        }
        // 计算已交货产品消耗的物料（出货量）
        // 交货记录以「产品主数据ID」为准关联（加工单编辑会重建产品行、行ID变化，主数据ID稳定）；
        // 历史数据缺失主数据ID时回退按产品行ID匹配，保证新旧数据都能算对
        java.util.Map<String, java.math.BigDecimal> deliveredByMaster = new java.util.HashMap<>();
        java.util.Map<String, java.math.BigDecimal> deliveredByRow = new java.util.HashMap<>();
        java.util.List<OutsourceOrderDelivery> deliveries = orderDeliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceOrderDelivery>()
                .eq(OutsourceOrderDelivery::getOrderId, id));
        for (OutsourceOrderDelivery d : deliveries) {
            java.math.BigDecimal qty = d.getQuantity() != null ? d.getQuantity() : java.math.BigDecimal.ZERO;
            if (qty.compareTo(java.math.BigDecimal.ZERO) <= 0) continue;
            if (d.getProductMasterId() != null)
                deliveredByMaster.merge(String.valueOf(d.getProductMasterId()), qty, java.math.BigDecimal::add);
            else if (d.getProductId() != null)
                deliveredByRow.merge(String.valueOf(d.getProductId()), qty, java.math.BigDecimal::add);
        }
        // 按产品计算每个物料已被出货消耗的数量
        java.util.Map<Long, java.math.BigDecimal> shippedConsumedMap = new java.util.HashMap<>();
        for (OutsourceOrderProduct p : products) {
            java.math.BigDecimal pDelivered = p.getProductId() != null
                    ? deliveredByMaster.get(String.valueOf(p.getProductId())) : null;
            if (pDelivered == null) pDelivered = deliveredByRow.get(String.valueOf(p.getId()));
            if (pDelivered == null) pDelivered = java.math.BigDecimal.ZERO;
            if (pDelivered == null || pDelivered.compareTo(java.math.BigDecimal.ZERO) == 0) continue;
            java.math.BigDecimal pTotal = p.getQuantity() != null ? p.getQuantity() : java.math.BigDecimal.ONE;
            java.util.List<OutsourceOrderMaterial> mats = orderService.getMaterials(p.getId());
            for (OutsourceOrderMaterial mat : mats) {
                Long key = mat.getMaterialId();
                if (key == null) continue;
                java.math.BigDecimal matDemand = mat.getDemandQuantity() != null ? mat.getDemandQuantity() : java.math.BigDecimal.ZERO;
                if (matDemand.compareTo(java.math.BigDecimal.ZERO) == 0) continue;
                // F2-3（2026-09-18 审核修复）：优先直取视图 quantity_per_set（精确），反算仅作兜底
                // F7-60（2026-09-20）：收敛到 MaterialRequirementCalc —— 反算精度由 10 位统一为 6 位，
                // 与交货侧/列表页一致（仅在 quantity_per_set 缺失时才走反算，故实际影响极小）。
                java.math.BigDecimal perUnit = MaterialRequirementCalc.perUnit(mat.getQuantityPerSet(), matDemand, pTotal);
                // 数量一律为整数（2026-09-16）：单套用量(比率) × 已交货数量 → 取整
                java.math.BigDecimal consumed = MaterialRequirementCalc.need(perUnit, pDelivered);
                shippedConsumedMap.merge(key, consumed, java.math.BigDecimal::add);
            }
        }

        // 查所有活跃物料订单的在途数量（可能不精确，仅按物料名汇总）
        Map<Long, BigDecimal> inTransitMap = new HashMap<>();
        List<MaterialOrder> activeOrders = materialOrderMapper.selectList(
            new LambdaQueryWrapper<MaterialOrder>()
                .notIn(MaterialOrder::getStatus, List.of(MaterialOrderStatus.FINISHED.getCode(), MaterialOrderStatus.CANCELLED.getCode())));
        if (!activeOrders.isEmpty()) {
            List<Long> orderIds = activeOrders.stream().map(MaterialOrder::getId).collect(Collectors.toList());
            List<MaterialOrderItem> items = materialOrderItemMapper.selectList(
                new LambdaQueryWrapper<MaterialOrderItem>()
                    .in(MaterialOrderItem::getOrderId, orderIds));
            for (MaterialOrderItem item : items) {
                if (item.getMaterialId() == null) continue;
                BigDecimal ordered = item.getOrderQuantity() != null ? item.getOrderQuantity() : BigDecimal.ZERO;
                BigDecimal received = item.getReceivedQuantity() != null ? item.getReceivedQuantity() : BigDecimal.ZERO;
                BigDecimal inTransit = ordered.subtract(received);
                if (inTransit.compareTo(BigDecimal.ZERO) > 0) {
                    inTransitMap.merge(item.getMaterialId(), inTransit, BigDecimal::add);
                }
            }
        }

        // 查库存
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Object>> e : matMap.entrySet()) {
            Map<String, Object> m = e.getValue();
            BigDecimal demand = (BigDecimal) m.get("demandQuantity");
            // 扣除已出货消耗
            BigDecimal shippedConsumed = shippedConsumedMap.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal remainingDemand = demand.subtract(shippedConsumed);
            if (remainingDemand.compareTo(BigDecimal.ZERO) < 0) remainingDemand = BigDecimal.ZERO;
            // matMap仅汇总materialId非空的物料，materialId必然已解析
            Long materialId = (Long) m.get("materialId");
            // 补查供应商信息（即使无仓库也需要，供"去采购/去委外"使用，从 supplier_material 居间表实时联查）
            if (materialId != null && !m.containsKey("supplierIds")) {
                m.put("supplierIds", supplierMaterialService.listSupplierIdsByMaterial(materialId));
            }
            // 查良品库存
            BigDecimal stock = BigDecimal.ZERO;
            if (whId != null && materialId != null) {
                WarehouseStock s = warehouseStockMapper.selectOne(
                    new LambdaQueryWrapper<WarehouseStock>()
                        .eq(WarehouseStock::getWarehouseId, whId)
                        .eq(WarehouseStock::getMaterialId, materialId)
                        .eq(WarehouseStock::getQualityType, QualityType.GOOD.getCode()));
                if (s != null && s.getQuantity() != null) stock = s.getQuantity();
            }
            m.put("stockQuantity", stock);
            m.put("shippedConsumed", shippedConsumed);
            m.put("remainingDemand", remainingDemand);
            m.put("shortage", remainingDemand.subtract(stock).max(BigDecimal.ZERO));
            m.put("inTransit", inTransitMap.getOrDefault(e.getKey(), BigDecimal.ZERO));
            // 是否有子物料组成（有则可"去委外"）
            boolean hasComps = false;
            if (materialId != null) {
                Long cnt = componentMapper.selectCount(
                    new LambdaQueryWrapper<OutsourceMaterialComponent>()
                        .eq(OutsourceMaterialComponent::getParentMaterialId, materialId));
                hasComps = cnt != null && cnt > 0;
            }
            m.put("hasComponents", hasComps);
            result.add(m);
        }
        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("factoryId", order.getFactoryId());
        resp.put("materials", result);
        return R.ok(resp);
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        OutsourceOrder order = parseOrder(body);
        List<OutsourceOrderProduct> products = parseProducts(body);
        orderService.create(order, products);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        OutsourceOrder order = parseOrder(body);
        order.setId(id);
        List<OutsourceOrderProduct> products = parseProducts(body);
        orderService.update(order, products);
        return R.ok();
    }

    /** 审核：待确认 → 生产中 */
    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) {
        orderService.audit(id);
        return R.ok();
    }

    /** 反审核：生产中 → 待确认，回滚交货库存和应付 */
    // E1 口径（2026-09-12）：反审核统一 /un-audit，旧路径 /unaudit 保留为别名
    @PutMapping({"/{id}/un-audit", "/{id}/unaudit"})
    public R<Void> unaudit(@PathVariable Long id) {
        orderService.unaudit(id);
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return R.ok();
    }

    /**
     * 查询该加工单的交货/退料记录。
     *
     * <p>⚠️ <b>F7-63（2026-09-20，已确认脆弱但保留）</b>：关联方式为 `remark LIKE 加工单号`
     * —— 用**可变文本**当外键。已知后果：空备注的收发单永不出现在任何加工单下；若两个单号互为子串
     * 会串单（现网 0 冲突）。</p>
     * <p><b>为何不直接改</b>：收支单（`outsource_delivery`）与加工单**没有结构化外键**
     * （`source_order_id` 语义是"物料订单ID"，不能复用），且该端点**前端已无调用方**（死接口）
     * ⇒ 改动收益低、破坏外部调用方的风险高。**建议**：确认无外部调用后**整体删除本端点**
     * （或为 `outsource_delivery` 增加真正的加工单外键）。</p>
     */
    @GetMapping("/{id}/deliveries")
    public R<List<Map<String, Object>>> deliveries(@PathVariable Long id) {
        OutsourceOrder o = orderService.getById(id);
        if (o == null || o.getCode() == null) return R.ok(java.util.Collections.emptyList());
        List<OutsourceDelivery> list = deliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceDelivery>()
                // F7-63：见方法注释 —— 历史遗留的弱关联（LIKE 可变文本），保留以兼容既有前端/外部调用
                .like(OutsourceDelivery::getRemark, o.getCode())
                .orderByDesc(OutsourceDelivery::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (OutsourceDelivery d : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", d.getId()); m.put("code", d.getCode()); m.put("deliveryType", d.getDeliveryType());
            m.put("deliveryDate", d.getDeliveryDate()); m.put("status", d.getStatus()); m.put("remark", d.getRemark());
            List<OutsourceDeliveryItem> items = deliveryItemMapper.selectList(
                new LambdaQueryWrapper<OutsourceDeliveryItem>().eq(OutsourceDeliveryItem::getDeliveryId, d.getId()));
            m.put("items", items);
            result.add(m);
        }
        return R.ok(result);
    }

    @DeleteMapping("/{id}/attach")
    public R<Void> deleteAttach(@PathVariable Long id) {
        OutsourceOrder update = new OutsourceOrder();
        update.setId(id);
        update.setAttachUrl("");
        // 校验影响行数：跨公司/不存在的 id 会被租户插件拦截（影响 0 行），不能静默返回成功
        if (orderMapper.updateById(update) == 0) return R.fail("加工单不存在");
        return R.ok();
    }

    /**
     * 保存合同文件地址：仅更新 attachUrl。
     * 注意：不可复用 PUT /{id} 整单更新——其会先删除该单全部产品及BOM物料再重建，
     * 只传 attachUrl 会导致产品明细被清空，故此处单独提供轻量接口。
     */
    @PutMapping("/{id}/attach")
    public R<Void> saveAttach(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String url = body == null ? null : (String) body.get("attachUrl");
        if (url == null || url.isBlank()) return R.fail("附件地址不能为空");
        OutsourceOrder update = new OutsourceOrder();
        update.setId(id);
        update.setAttachUrl(url);
        // 校验影响行数：跨公司/不存在的 id 会被租户插件拦截（影响 0 行），不能静默返回成功
        if (orderMapper.updateById(update) == 0) return R.fail("加工单不存在");
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    private OutsourceOrder parseOrder(Map<String, Object> body) {
        Map<String, Object> dBody = body.containsKey("order") ? (Map<String, Object>) body.get("order") : body;
        OutsourceOrder o = new OutsourceOrder();
        if (dBody.get("factoryId") != null) o.setFactoryId(Long.valueOf(dBody.get("factoryId").toString()));
        if (dBody.get("planStartDate") != null && !dBody.get("planStartDate").toString().isBlank())
            o.setPlanStartDate(LocalDate.parse(dBody.get("planStartDate").toString()));
        if (dBody.get("planEndDate") != null && !dBody.get("planEndDate").toString().isBlank())
            o.setPlanEndDate(LocalDate.parse(dBody.get("planEndDate").toString()));
        if (dBody.get("supplyMode") != null && !dBody.get("supplyMode").toString().isBlank())
            o.setSupplyMode(dBody.get("supplyMode").toString());
        if (dBody.get("taxIncluded") != null) o.setTaxIncluded(Integer.valueOf(dBody.get("taxIncluded").toString()));
        if (dBody.get("taxRate") != null && !dBody.get("taxRate").toString().isBlank())
            o.setTaxRate(new BigDecimal(dBody.get("taxRate").toString()));
        o.setRemark((String) dBody.get("remark"));
        o.setAttachUrl((String) dBody.get("attachUrl"));
        o.setLogisticsCompany((String) dBody.get("logisticsCompany"));
        o.setLogisticsNo((String) dBody.get("logisticsNo"));
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<OutsourceOrderProduct> parseProducts(Map<String, Object> body) {
        List<OutsourceOrderProduct> list = new ArrayList<>();
        Object productsObj = body.get("products");
        if (productsObj instanceof List<?> rawList) {
            for (Object obj : rawList) {
                if (obj instanceof Map<?, ?> itemMap) {
                    Map<String, Object> map = (Map<String, Object>) itemMap;
                    OutsourceOrderProduct p = new OutsourceOrderProduct();
                    if (map.get("projectId") != null) p.setProjectId(Long.valueOf(map.get("projectId").toString()));
                    // 产品主数据ID(product.id)：前端按项目带出，用于交货/库存落账。
                    // 此前未接收该字段，导致不带项目时交货报「未关联产品主数据」。
                    if (map.get("productId") != null && !map.get("productId").toString().isBlank())
                        p.setProductId(Long.valueOf(map.get("productId").toString()));
                    p.setProductName((String) map.get("productName"));
                    if (map.get("quantity") != null && !map.get("quantity").toString().isBlank())
                        p.setQuantity(new BigDecimal(map.get("quantity").toString()));
                    if (map.get("unitPrice") != null && !map.get("unitPrice").toString().isBlank())
                        p.setUnitPrice(new BigDecimal(map.get("unitPrice").toString()));
                    p.setRemark((String) map.get("remark"));
                    // 物料
                    Object matsObj = map.get("materials");
                    if (matsObj instanceof List<?> matList) {
                        List<OutsourceOrderMaterial> materials = new ArrayList<>();
                        for (Object matObj : matList) {
                            if (matObj instanceof Map<?, ?> matMap) {
                                Map<String, Object> mm = (Map<String, Object>) matMap;
                                OutsourceOrderMaterial mat = new OutsourceOrderMaterial();
                                if (mm.get("materialId") != null) mat.setMaterialId(Long.valueOf(mm.get("materialId").toString()));
                                if (mm.get("materialTypeId") != null) mat.setMaterialTypeId(Long.valueOf(mm.get("materialTypeId").toString()));
                                mat.setUnit((String) mm.get("unit"));
                                // 单套用量（前端 bomQuantityPerSet）：写入 BOM 快照时用，缺失则按 需求数量 ÷ 产品数量 折算
                                if (mm.get("bomQuantityPerSet") != null && !mm.get("bomQuantityPerSet").toString().isBlank())
                                    mat.setQuantityPerSet(new BigDecimal(mm.get("bomQuantityPerSet").toString()));
                                if (mm.get("demandQuantity") != null && !mm.get("demandQuantity").toString().isBlank())
                                    mat.setDemandQuantity(new BigDecimal(mm.get("demandQuantity").toString()));
                                if (mm.get("lossRate") != null && !mm.get("lossRate").toString().isBlank())
                                    mat.setLossRate(new BigDecimal(mm.get("lossRate").toString()));
                                if (mm.get("supplyType") != null && !mm.get("supplyType").toString().isBlank())
                                    mat.setSupplyType(mm.get("supplyType").toString());
                                mat.setRemark((String) mm.get("remark"));
                                materials.add(mat);
                            }
                        }
                        p.setMaterials(materials);
                    }
                    list.add(p);
                }
            }
        }
        return list;
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
}
