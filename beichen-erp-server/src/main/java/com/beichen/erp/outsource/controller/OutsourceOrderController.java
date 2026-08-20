package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderMaterial;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.common.MaterialOrderStatus;
import com.beichen.erp.outsource.common.QualityType;
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
    private final com.beichen.erp.dev.mapper.BomTypeMapper bomTypeMapper;
    private final OutsourceDeliveryMapper deliveryMapper;
    private final OutsourceDeliveryItemMapper deliveryItemMapper;
    private final OutsourceOrderDeliveryMapper orderDeliveryMapper;
    private final OutsourceMaterialComponentMapper componentMapper;
    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;
    private final SupplierMaterialService supplierMaterialService;

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
        if (o == null) return R.ok(null);
        Map<String, Object> m = new HashMap<>();
        m.put("id", o.getId()); m.put("code", o.getCode()); m.put("status", o.getStatus());
        m.put("factoryId", o.getFactoryId());
        m.put("planStartDate", o.getPlanStartDate()); m.put("planEndDate", o.getPlanEndDate());
        m.put("actualStartDate", o.getActualStartDate()); m.put("actualEndDate", o.getActualEndDate());
        m.put("taxIncluded", o.getTaxIncluded()); m.put("taxRate", o.getTaxRate());
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
            pm.put("productName", p.getProductName()); pm.put("productSpec", p.getProductSpec());
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
            list.add(pm);
        }
        return R.ok(list);
    }

    /** BOM物料库存及缺料 */
    @GetMapping("/{id}/material-stock")
    public R<Map<String, Object>> materialStock(@PathVariable Long id) {
        OutsourceOrder order = orderService.getById(id);
        if (order == null) return R.ok(null);
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
                    m.put("bomTypeName", getBomTypeNameById(mat.getBomTypeId()));
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
        java.util.Map<String, java.math.BigDecimal> productDeliveredMap = new java.util.HashMap<>();
        java.util.List<OutsourceOrderDelivery> deliveries = orderDeliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceOrderDelivery>()
                .eq(OutsourceOrderDelivery::getOrderId, id));
        for (OutsourceOrderDelivery d : deliveries) {
            if (d.getProductId() == null) continue;
            java.math.BigDecimal qty = d.getQuantity() != null ? d.getQuantity() : java.math.BigDecimal.ZERO;
            if (qty.compareTo(java.math.BigDecimal.ZERO) <= 0) continue;
            productDeliveredMap.merge(String.valueOf(d.getProductId()), qty, java.math.BigDecimal::add);
        }
        // 按产品计算每个物料已被出货消耗的数量
        java.util.Map<Long, java.math.BigDecimal> shippedConsumedMap = new java.util.HashMap<>();
        for (OutsourceOrderProduct p : products) {
            java.math.BigDecimal pDelivered = productDeliveredMap.get(String.valueOf(p.getId()));
            if (pDelivered == null || pDelivered.compareTo(java.math.BigDecimal.ZERO) == 0) continue;
            java.math.BigDecimal pTotal = p.getQuantity() != null ? p.getQuantity() : java.math.BigDecimal.ONE;
            java.util.List<OutsourceOrderMaterial> mats = orderService.getMaterials(p.getId());
            for (OutsourceOrderMaterial mat : mats) {
                Long key = mat.getMaterialId();
                if (key == null) continue;
                java.math.BigDecimal matDemand = mat.getDemandQuantity() != null ? mat.getDemandQuantity() : java.math.BigDecimal.ZERO;
                if (matDemand.compareTo(java.math.BigDecimal.ZERO) == 0) continue;
                java.math.BigDecimal perUnit = matDemand.divide(pTotal, 10, java.math.RoundingMode.HALF_UP);
                java.math.BigDecimal consumed = perUnit.multiply(pDelivered);
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
    @PutMapping("/{id}/unaudit")
    public R<Void> unaudit(@PathVariable Long id) {
        orderService.unaudit(id);
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return R.ok();
    }

    /** 查询该加工单的交货/退料记录 */
    @GetMapping("/{id}/deliveries")
    public R<List<Map<String, Object>>> deliveries(@PathVariable Long id) {
        OutsourceOrder o = orderService.getById(id);
        if (o == null || o.getCode() == null) return R.ok(java.util.Collections.emptyList());
        List<OutsourceDelivery> list = deliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceDelivery>()
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
        orderMapper.updateById(update);
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
                    p.setProductName((String) map.get("productName"));
                    p.setProductSpec((String) map.get("productSpec"));
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
                                if (mm.get("bomTypeId") != null) mat.setBomTypeId(Long.valueOf(mm.get("bomTypeId").toString()));
                                mat.setUnit((String) mm.get("unit"));
                                if (mm.get("demandQuantity") != null && !mm.get("demandQuantity").toString().isBlank())
                                    mat.setDemandQuantity(new BigDecimal(mm.get("demandQuantity").toString()));
                                if (mm.get("lossRate") != null && !mm.get("lossRate").toString().isBlank())
                                    mat.setLossRate(new BigDecimal(mm.get("lossRate").toString()));
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

    /** 根据 BOM 类型ID 查询类型名称，空安全返回 "-" */
    private String getBomTypeNameById(Long bomTypeId) {
        if (bomTypeId == null) return "-";
        com.beichen.erp.dev.entity.BomType bt = bomTypeMapper.selectById(bomTypeId);
        return bt != null ? bt.getTypeName() : "-";
    }
}
