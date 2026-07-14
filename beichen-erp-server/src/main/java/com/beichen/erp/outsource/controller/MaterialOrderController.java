package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.entity.*;
import com.beichen.erp.outsource.mapper.*;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/outsource/material-order")
@RequiredArgsConstructor
public class MaterialOrderController {

    private final MaterialOrderMapper orderMapper;
    private final MaterialOrderItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final OutsourceMaterialMapper materialMapper;
    private final OutsourceWarehouseMapper warehouseMapper;
    private final OutsourceWarehouseStockMapper warehouseStockMapper;
    private final OutsourceDeliveryMapper deliveryMapper;
    private final OutsourceDeliveryItemMapper deliveryItemMapper;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String statuses) {
        LambdaQueryWrapper<MaterialOrder> w = new LambdaQueryWrapper<MaterialOrder>()
            .eq(code != null && !code.isBlank(), MaterialOrder::getCode, code);
        if (status != null && !status.isBlank()) {
            w.eq(MaterialOrder::getStatus, status);
        } else if (statuses != null && !statuses.isBlank()) {
            String[] arr = statuses.split(",");
            if (arr.length == 1) {
                w.eq(MaterialOrder::getStatus, arr[0].trim());
            } else if (arr.length > 1) {
                w.in(MaterialOrder::getStatus, java.util.Arrays.stream(arr).map(String::trim).toList());
            }
        }
        w.orderByDesc(MaterialOrder::getId);
        Page<MaterialOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(page.getRecords().stream().map(o -> {
            Map<String, Object> m = buildOrderMap(o);
            List<MaterialOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, o.getId()));
            m.put("items", items);
            return m;
        }).toList());
        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) return R.ok(null);
        Map<String, Object> m = buildOrderMap(o);
        m.put("items", itemMapper.selectList(
            new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, id)));
        return R.ok(m);
    }

    private Map<String, Object> buildOrderMap(MaterialOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId()); m.put("code", o.getCode());
        m.put("supplierId", o.getSupplierId());
        m.put("deliveryDate", o.getDeliveryDate()); m.put("status", o.getStatus());
        m.put("remark", o.getRemark()); m.put("createTime", o.getCreateTime());
        m.put("finishTime", o.getFinishTime());
        if (o.getSupplierId() != null) { Supplier s = supplierMapper.selectById(o.getSupplierId()); m.put("supplierName", s != null ? s.getName() : ""); }
        return m;
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Long> create(@RequestBody Map<String, Object> body) {
        MaterialOrder o = parseOrder(body);
        o.setCode(generateCode());
        o.setStatus("待确认");
        orderMapper.insert(o);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items != null) {
            for (Map<String, Object> it : items) {
                MaterialOrderItem item = parseItem(it);
                item.setOrderId(o.getId());
                itemMapper.insert(item);
            }
        }
        return R.ok(o.getId());
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MaterialOrder old = orderMapper.selectById(id);
        if (old == null) throw new BusinessException("订单不存在");
        if ("已取消".equals(old.getStatus())) throw new BusinessException("已取消的订单不可编辑");
        MaterialOrder o = parseOrder(body);
        o.setId(id);
        orderMapper.updateById(o);

        itemMapper.delete(new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, id));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items != null) {
            for (Map<String, Object> it : items) {
                MaterialOrderItem item = parseItem(it);
                item.setOrderId(id);
                itemMapper.insert(item);
            }
        }
        return R.ok();
    }

    @PutMapping("/{id}/confirm")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> confirm(@PathVariable Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException("订单不存在");
        if (!"待确认".equals(o.getStatus())) throw new BusinessException("只有待确认状态可确认");
        MaterialOrder upd = new MaterialOrder(); upd.setId(id); upd.setStatus("收货中");
        orderMapper.updateById(upd);
        return R.ok();
    }

    /** 收货，需指定 factoryId（收货仓库对应工厂） */
    @PostMapping("/{id}/receive")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> receive(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException("订单不存在");
        if (!"收货中".equals(o.getStatus()) && !"已确认".equals(o.getStatus()))
            throw new BusinessException("当前状态不可收货");

        Long factoryId = Long.valueOf(body.get("factoryId").toString());
        List<OutsourceWarehouse> whs = warehouseMapper.selectList(
            new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, factoryId));
        if (whs.isEmpty()) throw new BusinessException("该加工厂未配置委外仓库");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) throw new BusinessException("收货明细不能为空");

        OutsourceDelivery delivery = new OutsourceDelivery();
        delivery.setDeliveryType("收料");
        delivery.setFactoryId(factoryId);
        delivery.setToWarehouseId(whs.get(0).getId());
        delivery.setDeliveryDate(LocalDate.now());
        delivery.setStatus("已确认");
        delivery.setRemark("采购收货 - " + o.getCode());
        delivery.setCode(generateDeliveryCode());
        deliveryMapper.insert(delivery);

        for (Map<String, Object> it : items) {
            BigDecimal qty = new BigDecimal(it.get("quantity").toString());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            Long itemId = Long.valueOf(it.get("itemId").toString());
            MaterialOrderItem orderItem = itemMapper.selectById(itemId);
            if (orderItem == null) continue;
            orderItem.setReceivedQuantity(orderItem.getReceivedQuantity().add(qty));
            itemMapper.updateById(orderItem);

            OutsourceDeliveryItem di = new OutsourceDeliveryItem();
            di.setDeliveryId(delivery.getId());
            di.setMaterialId(orderItem.getMaterialId());
            di.setMaterialName(orderItem.getMaterialName());
            di.setMaterialType(orderItem.getMaterialType());
            di.setUnit(orderItem.getUnit());
            di.setQuantity(qty);
            di.setQualityType("良品");
            deliveryItemMapper.insert(di);

            updateStock(whs.get(0).getId(), orderItem.getMaterialId(), qty, "良品");
        }

        boolean allReceived = itemMapper.selectList(
            new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, id))
            .stream().allMatch(it -> it.getReceivedQuantity().compareTo(it.getOrderQuantity()) >= 0);
        if (allReceived) {
            MaterialOrder upd = new MaterialOrder(); upd.setId(id); upd.setStatus("已完成"); upd.setFinishTime(java.time.LocalDateTime.now());
            orderMapper.updateById(upd);
        }
        return R.ok();
    }

    /** 退不良，需指定 factoryId */
    @PostMapping("/{id}/return-defect")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> returnDefect(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException("订单不存在");

        Long factoryId = Long.valueOf(body.get("factoryId").toString());
        List<OutsourceWarehouse> whs = warehouseMapper.selectList(
            new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, factoryId));
        if (whs.isEmpty()) throw new BusinessException("该加工厂未配置委外仓库");
        Long whId = whs.get(0).getId();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) throw new BusinessException("退料明细不能为空");

        OutsourceDelivery delivery = new OutsourceDelivery();
        delivery.setDeliveryType("退料");
        delivery.setFactoryId(factoryId);
        delivery.setFromWarehouseId(whId);
        delivery.setDeliveryDate(LocalDate.now());
        delivery.setStatus("已确认");
        delivery.setRemark("采购不良退料 - " + o.getCode());
        delivery.setCode(generateDeliveryCode());
        deliveryMapper.insert(delivery);

        for (Map<String, Object> it : items) {
            BigDecimal qty = new BigDecimal(it.get("quantity").toString());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            Long itemId = Long.valueOf(it.get("itemId").toString());
            MaterialOrderItem orderItem = itemMapper.selectById(itemId);
            if (orderItem == null) continue;
            if (orderItem.getReceivedQuantity().subtract(orderItem.getDefectReturnedQty()).compareTo(qty) < 0)
                throw new BusinessException(orderItem.getMaterialName() + " 可退数量不足");

            orderItem.setDefectReturnedQty(orderItem.getDefectReturnedQty().add(qty));
            itemMapper.updateById(orderItem);

            OutsourceDeliveryItem di = new OutsourceDeliveryItem();
            di.setDeliveryId(delivery.getId());
            di.setMaterialId(orderItem.getMaterialId());
            di.setMaterialName(orderItem.getMaterialName());
            di.setMaterialType(orderItem.getMaterialType());
            di.setUnit(orderItem.getUnit());
            di.setQuantity(qty);
            di.setQualityType("不良品");
            deliveryItemMapper.insert(di);

            updateStock(whId, orderItem.getMaterialId(), qty.negate(), "良品");
        }
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> cancel(@PathVariable Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException("订单不存在");
        if ("已取消".equals(o.getStatus()) || "已完成".equals(o.getStatus()))
            throw new BusinessException("当前状态不可取消");
        MaterialOrder upd = new MaterialOrder(); upd.setId(id); upd.setStatus("已取消");
        orderMapper.updateById(upd);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        itemMapper.delete(new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, id));
        orderMapper.deleteById(id);
        return R.ok();
    }

    // ==================== 私有 ====================

    private MaterialOrder parseOrder(Map<String, Object> body) {
        MaterialOrder o = new MaterialOrder();
        Object sid = body.get("supplierId");
        if (sid != null && !sid.toString().isBlank()) o.setSupplierId(Long.valueOf(sid.toString()));
        Object dd = body.get("deliveryDate");
        if (dd != null && !dd.toString().isBlank()) o.setDeliveryDate(LocalDate.parse(dd.toString()));
        if (body.get("remark") != null) o.setRemark(body.get("remark").toString());
        return o;
    }

    private MaterialOrderItem parseItem(Map<String, Object> it) {
        MaterialOrderItem item = new MaterialOrderItem();
        if (it.get("materialId") != null) item.setMaterialId(Long.valueOf(it.get("materialId").toString()));
        item.setMaterialName((String) it.get("materialName"));
        item.setMaterialType((String) it.get("materialType"));
        item.setUnit((String) it.get("unit"));
        if (it.get("orderQuantity") != null) item.setOrderQuantity(new BigDecimal(it.get("orderQuantity").toString()));
        if (it.get("unitPrice") != null) item.setUnitPrice(new BigDecimal(it.get("unitPrice").toString()));
        if (item.getOrderQuantity() != null && item.getUnitPrice() != null) item.setAmount(item.getOrderQuantity().multiply(item.getUnitPrice()));
        if (it.get("remark") != null) item.setRemark((String) it.get("remark"));
        return item;
    }

    private void updateStock(Long warehouseId, Long materialId, BigDecimal delta, String qualityType) {
        LambdaQueryWrapper<OutsourceWarehouseStock> w = new LambdaQueryWrapper<OutsourceWarehouseStock>()
            .eq(OutsourceWarehouseStock::getWarehouseId, warehouseId)
            .eq(OutsourceWarehouseStock::getMaterialId, materialId)
            .eq(OutsourceWarehouseStock::getQualityType, qualityType);
        OutsourceWarehouseStock stock = warehouseStockMapper.selectOne(w);
        if (stock == null) {
            stock = new OutsourceWarehouseStock();
            stock.setWarehouseId(warehouseId); stock.setMaterialId(materialId);
            stock.setQualityType(qualityType); stock.setQuantity(delta);
            warehouseStockMapper.insert(stock);
        } else {
            stock.setQuantity(stock.getQuantity().add(delta));
            warehouseStockMapper.updateById(stock);
        }
    }

    private String generateCode() {
        String ds = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        LambdaQueryWrapper<MaterialOrder> w = new LambdaQueryWrapper<MaterialOrder>()
            .likeRight(MaterialOrder::getCode, "MWO-" + ds).orderByDesc(MaterialOrder::getCode).last("LIMIT 1");
        MaterialOrder last = orderMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception ignored) {}
        }
        return "MWO-" + ds + String.format("%03d", seq);
    }

    private String generateDeliveryCode() {
        String ds = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        LambdaQueryWrapper<OutsourceDelivery> w = new LambdaQueryWrapper<OutsourceDelivery>()
            .likeRight(OutsourceDelivery::getCode, "DEL-" + ds).orderByDesc(OutsourceDelivery::getCode).last("LIMIT 1");
        OutsourceDelivery last = deliveryMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception ignored) {}
        }
        return "DEL-" + ds + String.format("%03d", seq);
    }
}
