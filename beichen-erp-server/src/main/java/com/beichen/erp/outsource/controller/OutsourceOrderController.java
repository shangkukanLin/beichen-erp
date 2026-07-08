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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
