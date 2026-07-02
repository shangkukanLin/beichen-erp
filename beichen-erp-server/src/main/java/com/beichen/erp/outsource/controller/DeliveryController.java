package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.outsource.entity.OutsourceDelivery;
import com.beichen.erp.outsource.entity.OutsourceDeliveryItem;
import com.beichen.erp.outsource.entity.OutsourceWarehouse;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryItemMapper;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseMapper;
import com.beichen.erp.outsource.service.DeliveryService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.inventory.entity.InventoryWarehouse;
import com.beichen.erp.inventory.mapper.InventoryWarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outsource/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final OutsourceWarehouseMapper warehouseMapper;
    private final OutsourceDeliveryItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final InventoryWarehouseMapper inventoryWarehouseMapper;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam String deliveryType,
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<OutsourceDelivery> rawPage = deliveryService.page(deliveryType, factoryId, code, pageNum, pageSize);
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, rawPage.getTotal());
        result.setRecords(rawPage.getRecords().stream().map(d -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", d.getId()); m.put("code", d.getCode()); m.put("deliveryType", d.getDeliveryType());
            m.put("deliveryDate", d.getDeliveryDate()); m.put("status", d.getStatus());
            m.put("supplierDirect", d.getSupplierDirect()); m.put("logisticsCompany", d.getLogisticsCompany());
            m.put("logisticsNo", d.getLogisticsNo()); m.put("remark", d.getRemark());
            // 工厂名
            if (d.getFactoryId() != null) { Supplier sup = supplierMapper.selectById(d.getFactoryId()); m.put("factoryName", sup != null ? sup.getName() : ""); }
            // 直发供应商名
            if (d.getSupplierDirect() != null && d.getSupplierDirect() == 1 && d.getSupplierId() != null) { Supplier s = supplierMapper.selectById(d.getSupplierId()); m.put("supplierName", s != null ? s.getName() : ""); }
            // 仓库名
            if (d.getFromWarehouseId() != null) { OutsourceWarehouse w = warehouseMapper.selectById(d.getFromWarehouseId()); m.put("fromWarehouseName", w != null ? w.getWarehouseName() : ""); }
            if (d.getToWarehouseId() != null) { OutsourceWarehouse w = warehouseMapper.selectById(d.getToWarehouseId()); m.put("toWarehouseName", w != null ? w.getWarehouseName() : ""); }
            // 物料统计
            Long count = itemMapper.selectCount(new LambdaQueryWrapper<OutsourceDeliveryItem>().eq(OutsourceDeliveryItem::getDeliveryId, d.getId()));
            m.put("itemCount", count);
            return m;
        }).toList());
        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> getById(@PathVariable Long id) {
        OutsourceDelivery d = deliveryService.getById(id);
        if (d == null) return R.ok(null);
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId()); m.put("code", d.getCode()); m.put("deliveryType", d.getDeliveryType());
        m.put("deliveryDate", d.getDeliveryDate()); m.put("status", d.getStatus());
        m.put("supplierDirect", d.getSupplierDirect()); m.put("logisticsCompany", d.getLogisticsCompany());
        m.put("logisticsNo", d.getLogisticsNo()); m.put("contact", d.getContact()); m.put("phone", d.getPhone());
        m.put("remark", d.getRemark()); m.put("attachUrl", d.getAttachUrl());
        m.put("factoryId", d.getFactoryId());
        m.put("supplierId", d.getSupplierId());
        m.put("fromWarehouseId", d.getFromWarehouseId());
        m.put("toWarehouseId", d.getToWarehouseId());
        if (d.getFactoryId() != null) { Supplier sup = supplierMapper.selectById(d.getFactoryId()); m.put("factoryName", sup != null ? sup.getName() : ""); }
        if (d.getSupplierId() != null) { Supplier sup = supplierMapper.selectById(d.getSupplierId()); m.put("supplierName", sup != null ? sup.getName() : ""); }
        if (d.getFromWarehouseId() != null) {
            OutsourceWarehouse w = warehouseMapper.selectById(d.getFromWarehouseId());
            if (w != null) m.put("fromWarehouseName", w.getWarehouseName());
            else { InventoryWarehouse iw = inventoryWarehouseMapper.selectById(d.getFromWarehouseId()); if (iw != null) m.put("fromWarehouseName", iw.getWarehouseName()); }
        }
        if (d.getToWarehouseId() != null) {
            OutsourceWarehouse w = warehouseMapper.selectById(d.getToWarehouseId());
            if (w != null) m.put("toWarehouseName", w.getWarehouseName());
        }
        return R.ok(m);
    }

    @GetMapping("/{id}/items")
    public R<List<OutsourceDeliveryItem>> getItems(@PathVariable Long id) {
        return R.ok(deliveryService.getItems(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        OutsourceDelivery delivery = parseDelivery(body);
        List<OutsourceDeliveryItem> items = parseItems(body);
        deliveryService.create(delivery, items);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        OutsourceDelivery delivery = parseDelivery(body);
        delivery.setId(id);
        List<OutsourceDeliveryItem> items = parseItems(body);
        deliveryService.update(delivery, items);
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        deliveryService.cancel(id);
        return R.ok();
    }

    @GetMapping("/warehouses/by-factory/{factoryId}")
    public R<Object> warehousesByFactory(@PathVariable Long factoryId) {
        return R.ok(warehouseMapper.selectList(new LambdaQueryWrapper<OutsourceWarehouse>()
                .eq(OutsourceWarehouse::getFactoryId, factoryId)));
    }

    @GetMapping("/warehouses/inventory")
    public R<Object> inventoryWarehouses() {
        return R.ok(inventoryWarehouseMapper.selectList(new LambdaQueryWrapper<InventoryWarehouse>()
                .eq(InventoryWarehouse::getStatus, 1).orderByAsc(InventoryWarehouse::getId)));
    }

    @SuppressWarnings("unchecked")
    private OutsourceDelivery parseDelivery(Map<String, Object> body) {
        // 兼容嵌套格式 {delivery:{...}, items:[...]}
        Map<String, Object> dBody = body.containsKey("delivery") ? (Map<String, Object>) body.get("delivery") : body;
        OutsourceDelivery d = new OutsourceDelivery();
        d.setDeliveryType((String) dBody.get("deliveryType"));
        if (dBody.get("projectId") != null) {
            d.setProjectId(Long.valueOf(dBody.get("projectId").toString()));
        }
        if (dBody.get("factoryId") != null) {
            d.setFactoryId(Long.valueOf(dBody.get("factoryId").toString()));
        }
        if (dBody.get("fromWarehouseId") != null) {
            d.setFromWarehouseId(Long.valueOf(dBody.get("fromWarehouseId").toString()));
        }
        if (dBody.get("toWarehouseId") != null) {
            d.setToWarehouseId(Long.valueOf(dBody.get("toWarehouseId").toString()));
        }
        if (dBody.get("supplierDirect") != null) {
            d.setSupplierDirect(Integer.valueOf(dBody.get("supplierDirect").toString()));
        }
        if (dBody.get("supplierId") != null) {
            d.setSupplierId(Long.valueOf(dBody.get("supplierId").toString()));
        }
        d.setLogisticsCompany((String) dBody.get("logisticsCompany"));
        d.setLogisticsNo((String) dBody.get("logisticsNo"));
        if (dBody.get("deliveryDate") != null) {
            d.setDeliveryDate(LocalDate.parse(dBody.get("deliveryDate").toString()));
        }
        d.setContact((String) dBody.get("contact"));
        d.setPhone((String) dBody.get("phone"));
        d.setRemark((String) dBody.get("remark"));
        d.setAttachUrl((String) dBody.get("attachUrl"));
        return d;
    }

    @SuppressWarnings("unchecked")
    private List<OutsourceDeliveryItem> parseItems(Map<String, Object> body) {
        List<OutsourceDeliveryItem> items = new ArrayList<>();
        Object itemsObj = body.get("items");
        if (itemsObj instanceof List<?> list) {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> itemMap) {
                    Map<String, Object> map = (Map<String, Object>) itemMap;
                    OutsourceDeliveryItem item = new OutsourceDeliveryItem();
                    // 兼容 materialId 和 material_id
                    Object mid = map.get("materialId") != null ? map.get("materialId") : map.get("material_id");
                    if (mid != null) item.setMaterialId(Long.valueOf(mid.toString()));
                    item.setMaterialName((String) (map.get("materialName") != null ? map.get("materialName") : map.get("material_name")));
                    item.setMaterialType((String) (map.get("materialType") != null ? map.get("materialType") : map.get("material_type")));
                    item.setUnit((String) map.get("unit"));
                    if (map.get("quantity") != null && !map.get("quantity").toString().isBlank()) {
                        item.setQuantity(new BigDecimal(map.get("quantity").toString()));
                    } else {
                        item.setQuantity(BigDecimal.ZERO);
                    }
                    items.add(item);
                }
            }
        }
        return items;
    }
}
