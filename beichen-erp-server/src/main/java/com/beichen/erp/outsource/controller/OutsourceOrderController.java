package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderMaterial;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.service.OutsourceOrderService;
import com.beichen.erp.outsource.mapper.OutsourceOrderMapper;
import com.beichen.erp.outsource.entity.OutsourceWarehouse;
import com.beichen.erp.outsource.entity.OutsourceWarehouseStock;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseMapper;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseStockMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outsource/order")
@RequiredArgsConstructor
public class OutsourceOrderController {

    private final OutsourceOrderService orderService;
    private final OutsourceOrderMapper orderMapper;
    private final ProjectMapper projectMapper;
    private final OutsourceWarehouseMapper warehouseMapper;
    private final OutsourceWarehouseStockMapper warehouseStockMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;

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
        List<OutsourceWarehouse> whs = warehouseMapper.selectList(
            new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, order.getFactoryId()));
        Long whId = whs.isEmpty() ? null : whs.get(0).getId();

        List<OutsourceOrderProduct> products = orderService.getProducts(id);
        // 汇总所有物料（合并同名物料的需求量）
        Map<String, Map<String, Object>> matMap = new java.util.LinkedHashMap<>();
        for (OutsourceOrderProduct p : products) {
            List<OutsourceOrderMaterial> mats = orderService.getMaterials(p.getId());
            for (OutsourceOrderMaterial mat : mats) {
                String key = mat.getMaterialName() != null ? mat.getMaterialName() : "";
                if (key.isBlank()) continue;
                if (matMap.containsKey(key)) {
                    Map<String, Object> existing = matMap.get(key);
                    BigDecimal oldDemand = (BigDecimal) existing.get("demandQuantity");
                    existing.put("demandQuantity", oldDemand.add(mat.getDemandQuantity() != null ? mat.getDemandQuantity() : BigDecimal.ZERO));
                } else {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("materialName", mat.getMaterialName());
                    m.put("materialType", mat.getMaterialType());
                    m.put("unit", mat.getUnit());
                    m.put("materialId", mat.getMaterialId());
                    m.put("demandQuantity", mat.getDemandQuantity() != null ? mat.getDemandQuantity() : BigDecimal.ZERO);
                    // 查物料关联的供应商
                    if (mat.getMaterialId() != null) {
                        OutsourceMaterial om = outsourceMaterialMapper.selectById(mat.getMaterialId());
                        if (om != null) { m.put("supplierIds", om.getSupplierIds()); m.put("supplierName", om.getSupplierName()); }
                    }
                    matMap.put(key, m);
                }
            }
        }
        // 查库存
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : matMap.entrySet()) {
            Map<String, Object> m = e.getValue();
            BigDecimal demand = (BigDecimal) m.get("demandQuantity");
            // 确保materialId已解析（BOM中materialId可能为空，需按名称补查）
            Long materialId = (Long) m.get("materialId");
            if (materialId == null) {
                Long mid = outsourceMaterialMapper.findIdByName((String) m.get("materialName"));
                if (mid != null) m.put("materialId", mid);
                materialId = mid;
            }
            // 补查供应商信息（即使无仓库也需要，供"去采购"使用）
            if (materialId != null && !m.containsKey("supplierIds")) {
                OutsourceMaterial om = outsourceMaterialMapper.selectById(materialId);
                if (om != null) { m.put("supplierIds", om.getSupplierIds()); m.put("supplierName", om.getSupplierName()); }
            }
            // 查良品库存
            BigDecimal stock = BigDecimal.ZERO;
            if (whId != null && materialId != null) {
                OutsourceWarehouseStock s = warehouseStockMapper.selectOne(
                    new LambdaQueryWrapper<OutsourceWarehouseStock>()
                        .eq(OutsourceWarehouseStock::getWarehouseId, whId)
                        .eq(OutsourceWarehouseStock::getMaterialId, materialId)
                        .eq(OutsourceWarehouseStock::getQualityType, "良品"));
                if (s != null && s.getQuantity() != null) stock = s.getQuantity();
            }
            m.put("stockQuantity", stock);
            m.put("shortage", demand.subtract(stock).max(BigDecimal.ZERO));
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

    @PutMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        orderService.confirm(id);
        return R.ok();
    }

    @PutMapping("/{id}/complete")
    public R<Void> complete(@PathVariable Long id) {
        orderService.complete(id);
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return R.ok();
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
                                mat.setMaterialName((String) mm.get("materialName"));
                                mat.setMaterialType((String) mm.get("materialType"));
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
}
