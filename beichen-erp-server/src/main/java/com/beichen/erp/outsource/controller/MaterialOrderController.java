package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.service.PayableHelper;
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
    private final OutsourceMaterialComponentMapper componentMapper;
    private final OutsourceStockLogMapper stockLogMapper;
    private final PayableHelper payableHelper;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String statuses,
            @RequestParam(required = false) Long supplierId) {
        LambdaQueryWrapper<MaterialOrder> w = new LambdaQueryWrapper<MaterialOrder>()
            .eq(code != null && !code.isBlank(), MaterialOrder::getCode, code)
            .eq(supplierId != null, MaterialOrder::getSupplierId, supplierId);
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
            m.put("items", buildItemMaps(items));
            return m;
        }).toList());
        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) return R.ok(null);
        Map<String, Object> m = buildOrderMap(o);
        m.put("items", buildItemMaps(itemMapper.selectList(
            new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, id))));
        return R.ok(m);
    }

    private Map<String, Object> buildOrderMap(MaterialOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId()); m.put("code", o.getCode());
        m.put("supplierId", o.getSupplierId());
        m.put("orderType", o.getOrderType() != null ? o.getOrderType() : "采购");
        m.put("targetWarehouseId", o.getTargetWarehouseId());
        m.put("deliveryDate", o.getDeliveryDate()); m.put("status", o.getStatus());
        m.put("remark", o.getRemark()); m.put("createTime", o.getCreateTime());
        m.put("finishTime", o.getFinishTime());
        m.put("attachUrl", o.getAttachUrl());
        if (o.getSupplierId() != null) { Supplier s = supplierMapper.selectById(o.getSupplierId()); m.put("supplierName", s != null ? s.getName() : ""); }
        if (o.getTargetWarehouseId() != null) {
            OutsourceWarehouse wh = warehouseMapper.selectById(o.getTargetWarehouseId());
            m.put("targetWarehouseName", wh != null ? wh.getWarehouseName() : "");
        }
        // 最近一次交货时间
        OutsourceDelivery lastDelivery = deliveryMapper.selectOne(
            new LambdaQueryWrapper<OutsourceDelivery>()
                .like(OutsourceDelivery::getRemark, o.getCode())
                .orderByDesc(OutsourceDelivery::getCreateTime)
                .last("LIMIT 1"));
        m.put("lastDeliveryTime", lastDelivery != null ? lastDelivery.getCreateTime() : null);
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

    /** 收货，需指定 factoryId（收货仓库对应工厂）。含子物料库存校验与扣减 */
    @PostMapping("/{id}/receive")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> receive(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException("订单不存在");
        if (!"收货中".equals(o.getStatus()) && !"已确认".equals(o.getStatus()))
            throw new BusinessException("当前状态不可收货");

        // 供应商（加工厂）仓库：子物料从该仓扣减
        List<OutsourceWarehouse> supWhs = warehouseMapper.selectList(
            new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, o.getSupplierId()));
        Long compWhId = supWhs.isEmpty() ? null : supWhs.get(0).getId();

        // 父物料收货仓：前端指定 > 订单目标仓 > 供应商仓
        Long whId;
        if (body.get("warehouseId") != null) {
            whId = Long.valueOf(body.get("warehouseId").toString());
        } else if (o.getTargetWarehouseId() != null) {
            whId = o.getTargetWarehouseId();
        } else {
            if (compWhId == null) throw new BusinessException("未指定收货仓库且供应商无默认仓库");
            whId = compWhId;
        }
        if (compWhId == null) compWhId = whId;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) throw new BusinessException("收货明细不能为空");

        // 1. 校验所有物料的子物料库存是否充足
        for (Map<String, Object> it : items) {
            BigDecimal qty = new BigDecimal(it.get("quantity").toString());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            Long itemId = Long.valueOf(it.get("itemId").toString());
            MaterialOrderItem orderItem = itemMapper.selectById(itemId);
            if (orderItem == null || orderItem.getMaterialId() == null) continue;
            List<OutsourceMaterialComponent> comps = componentMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialComponent>()
                    .eq(OutsourceMaterialComponent::getParentMaterialId, orderItem.getMaterialId()));
            if (comps == null || comps.isEmpty()) continue;
            for (OutsourceMaterialComponent c : comps) {
                BigDecimal compDemand = (c.getQuantity() != null ? c.getQuantity() : BigDecimal.ONE).multiply(qty);
                BigDecimal compStock = getStock(compWhId, c.getChildMaterialId());
                if (compStock.compareTo(compDemand) < 0) {
                    String childName = c.getChildMaterialId() != null ? 
                        (materialMapper.selectById(c.getChildMaterialId()) != null ? materialMapper.selectById(c.getChildMaterialId()).getMaterialName() : "子物料" + c.getChildMaterialId()) : "子物料";
                    throw new BusinessException("子物料「" + childName + "」库存不足（需求：" + compDemand.stripTrailingZeros().toPlainString()
                        + "，库存：" + compStock.stripTrailingZeros().toPlainString() + "），无法收货");
                }
            }
        }

        // 2. 创建收货单
        OutsourceDelivery delivery = new OutsourceDelivery();
        delivery.setDeliveryType("收料");
        delivery.setFactoryId(o.getSupplierId());
        delivery.setToWarehouseId(whId);
        delivery.setDeliveryDate(LocalDate.now());
        delivery.setStatus("已确认");
        delivery.setToWarehouseId(whId);
        delivery.setRemark(("委外".equals(o.getOrderType()) ? "委外收货 - " : "采购收货 - ") + o.getCode());
        // 来源标记：供应商（列表页会自动查名称）
        delivery.setSupplierDirect(1);
        delivery.setSupplierId(o.getSupplierId());
        delivery.setCode(generateDeliveryCode());
        deliveryMapper.insert(delivery);

        // 3. 执行收货：父物料入库 + 子物料出库
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

            // 父物料入库 + 流水
            updateStockLog(whId, orderItem.getMaterialId(), qty, "良品",
                orderItem.getMaterialName(), "委外收货入库", o.getCode());

            // 子物料出库（从加工厂仓扣减）+ 流水
            List<OutsourceMaterialComponent> comps = componentMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialComponent>()
                    .eq(OutsourceMaterialComponent::getParentMaterialId, orderItem.getMaterialId()));
            if (comps != null) {
                for (OutsourceMaterialComponent c : comps) {
                    BigDecimal compDemand = (c.getQuantity() != null ? c.getQuantity() : BigDecimal.ONE).multiply(qty);
                    OutsourceMaterial cm = materialMapper.selectById(c.getChildMaterialId());
                    String childName = cm != null ? cm.getMaterialName() : "子物料";
                    updateStockLog(compWhId, c.getChildMaterialId(), compDemand.negate(), "良品",
                        childName, "委外收货耗料", o.getCode());
                    // 记录子物料出库明细
                    OutsourceDeliveryItem cdi = new OutsourceDeliveryItem();
                    cdi.setDeliveryId(delivery.getId());
                    cdi.setMaterialId(c.getChildMaterialId());
                    cdi.setQuantity(compDemand);
                    cdi.setQualityType("良品");
                    OutsourceMaterial childMat = materialMapper.selectById(c.getChildMaterialId());
                    if (childMat != null) {
                        cdi.setMaterialName(childMat.getMaterialName());
                        cdi.setMaterialType(childMat.getMaterialType());
                        cdi.setUnit(childMat.getUnit());
                    }
                    deliveryItemMapper.insert(cdi);
                }
            }
        }

        boolean allReceived = itemMapper.selectList(
            new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, id))
            .stream().allMatch(it -> it.getReceivedQuantity().compareTo(it.getOrderQuantity()) >= 0);
        if (allReceived) {
            MaterialOrder upd = new MaterialOrder(); upd.setId(id); upd.setStatus("已完成"); upd.setFinishTime(java.time.LocalDateTime.now());
            orderMapper.updateById(upd);
        }

        // 4. 按本次实际收货生成应付（金额 = Σ 收货数 × 单价）
        BigDecimal payableAmount = BigDecimal.ZERO;
        for (Map<String, Object> it : items) {
            BigDecimal qty = new BigDecimal(it.get("quantity").toString());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            MaterialOrderItem orderItem = itemMapper.selectById(Long.valueOf(it.get("itemId").toString()));
            if (orderItem != null && orderItem.getUnitPrice() != null)
                payableAmount = payableAmount.add(qty.multiply(orderItem.getUnitPrice()));
        }
        if (payableAmount.compareTo(BigDecimal.ZERO) > 0) {
            payableHelper.createPayable(o.getSupplierId(),
                "委外".equals(o.getOrderType()) ? "委外物料收货" : "物料采购收货",
                o.getCode(), delivery.getId(), payableAmount, LocalDate.now(),
                ("委外".equals(o.getOrderType()) ? "委外收货" : "采购收货") + " - " + o.getCode());
        }
        return R.ok();
    }

    /** 获取某个仓库某个物料的良品库存 */
    private BigDecimal getStock(Long warehouseId, Long materialId) {
        if (warehouseId == null || materialId == null) return BigDecimal.ZERO;
        OutsourceWarehouseStock s = warehouseStockMapper.selectOne(
            new LambdaQueryWrapper<OutsourceWarehouseStock>()
                .eq(OutsourceWarehouseStock::getWarehouseId, warehouseId)
                .eq(OutsourceWarehouseStock::getMaterialId, materialId)
                .eq(OutsourceWarehouseStock::getQualityType, "良品"));
        return s != null && s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
    }

    /** 退不良，使用订单关联的供应商作为工厂。支持处理方式：维修返还(扣库存)/折现退款(仅记录) */
    @PostMapping("/{id}/return-defect")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> returnDefect(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException("订单不存在");

        Long factoryId = o.getSupplierId();
        if (factoryId == null) throw new BusinessException("订单未关联供应商");
        String handleType = body.get("handleType") != null ? body.get("handleType").toString() : "维修返还";

        // 退不良仓库
        Long whId = body.get("warehouseId") != null ? Long.valueOf(body.get("warehouseId").toString()) : null;
        if (!"折现退款".equals(handleType) && whId == null) {
            List<OutsourceWarehouse> whs = warehouseMapper.selectList(
                new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, factoryId));
            whId = whs.isEmpty() ? null : whs.get(0).getId();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) throw new BusinessException("退料明细不能为空");

        OutsourceDelivery delivery = new OutsourceDelivery();
        delivery.setDeliveryType("退料");
        delivery.setFactoryId(factoryId);
        delivery.setDeliveryDate(LocalDate.now());
        delivery.setStatus("已确认");
        delivery.setRemark("不良退料(" + handleType + ") - " + o.getCode());
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

            // 维修返还：校验仓库库存是否足够
            if (!"折现退款".equals(handleType) && whId != null && orderItem.getMaterialId() != null) {
                OutsourceWarehouseStock s = warehouseStockMapper.selectOne(
                    new LambdaQueryWrapper<OutsourceWarehouseStock>()
                        .eq(OutsourceWarehouseStock::getWarehouseId, whId)
                        .eq(OutsourceWarehouseStock::getMaterialId, orderItem.getMaterialId())
                        .eq(OutsourceWarehouseStock::getQualityType, "良品"));
                BigDecimal stockQty = s != null && s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
                if (stockQty.compareTo(qty) < 0)
                    throw new BusinessException(orderItem.getMaterialName() + " 仓库库存不足(库存:" + stockQty + "，退:" + qty + ")");
            }

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
            di.setHandleType(handleType);
            deliveryItemMapper.insert(di);

            // 维修返还：扣减仓库良品库存+写流水；折现退款：不扣库存
            if (!"折现退款".equals(handleType) && whId != null && orderItem.getMaterialId() != null) {
                updateStockLog(whId, orderItem.getMaterialId(), qty.negate(), "良品",
                    orderItem.getMaterialName(), "退不良扣回", o.getCode());
            }
        }

        // 退不良冲减应付（维修返还待修好重新收货时重新产生应付）
        BigDecimal deductAmount = BigDecimal.ZERO;
        for (Map<String, Object> it : items) {
            BigDecimal qty = new BigDecimal(it.get("quantity").toString());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            MaterialOrderItem orderItem = itemMapper.selectById(Long.valueOf(it.get("itemId").toString()));
            if (orderItem != null && orderItem.getUnitPrice() != null)
                deductAmount = deductAmount.add(qty.multiply(orderItem.getUnitPrice()));
        }
        if (deductAmount.compareTo(BigDecimal.ZERO) > 0) {
            payableHelper.createPayable(o.getSupplierId(), "退不良冲减", o.getCode(), delivery.getId(),
                deductAmount.negate(), LocalDate.now(), "退不良(" + handleType + ") - " + o.getCode());
        }
        return R.ok();
    }

    /** 结单：直接标记为已完成 */
    @PutMapping("/{id}/finish")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> finish(@PathVariable Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException("订单不存在");
        if ("已取消".equals(o.getStatus())) throw new BusinessException("已取消的订单不可结单");
        if ("已完成".equals(o.getStatus())) throw new BusinessException("订单已完成");
        MaterialOrder upd = new MaterialOrder(); upd.setId(id); upd.setStatus("已完成"); upd.setFinishTime(java.time.LocalDateTime.now());
        orderMapper.updateById(upd);
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> cancel(@PathVariable Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException("订单不存在");
        if ("已取消".equals(o.getStatus()) || "已完成".equals(o.getStatus()))
            throw new BusinessException("当前状态不可取消");
        List<MaterialOrderItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, id));
        boolean hasDelivery = items.stream().anyMatch(it -> it.getReceivedQuantity() != null && it.getReceivedQuantity().compareTo(BigDecimal.ZERO) > 0);
        if (hasDelivery) throw new BusinessException("该订单已有交货记录，不可取消");
        MaterialOrder upd = new MaterialOrder(); upd.setId(id); upd.setStatus("已取消");
        orderMapper.updateById(upd);
        return R.ok();
    }

    /** 查询该订单的交货记录 */
    /** 查询该物料订单的物料发到了哪些委外仓库（用于退不良选择） */
    @GetMapping("/{id}/defect-warehouses")
    public R<List<Map<String, Object>>> defectWarehouses(@PathVariable Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null || o.getCode() == null) return R.ok(Collections.emptyList());
        // 查询该订单关联的所有收发单，收集目标仓库（收料=收货入库）
        List<OutsourceDelivery> deliveries = deliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceDelivery>()
                .like(OutsourceDelivery::getRemark, o.getCode())
                .in(OutsourceDelivery::getDeliveryType, "收料", "发料")
                .orderByDesc(OutsourceDelivery::getId));
        // 去重仓库ID
        Set<Long> whIds = new LinkedHashSet<>();
        for (OutsourceDelivery d : deliveries) {
            if (d.getToWarehouseId() != null) whIds.add(d.getToWarehouseId());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long whId : whIds) {
            OutsourceWarehouse wh = warehouseMapper.selectById(whId);
            if (wh != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", wh.getId()); m.put("warehouseName", wh.getWarehouseName());
                m.put("factoryId", wh.getFactoryId());
                result.add(m);
            }
        }
        return R.ok(result);
    }

    @GetMapping("/{id}/deliveries")
    public R<List<Map<String, Object>>> deliveries(@PathVariable Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) return R.ok(Collections.emptyList());
        List<OutsourceDelivery> list = deliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceDelivery>()
                .like(OutsourceDelivery::getRemark, o.getCode())
                .orderByDesc(OutsourceDelivery::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (OutsourceDelivery d : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId()); m.put("code", d.getCode()); m.put("deliveryType", d.getDeliveryType());
            m.put("deliveryDate", d.getDeliveryDate()); m.put("status", d.getStatus()); m.put("remark", d.getRemark());
            m.put("toWarehouseId", d.getToWarehouseId());
            if (d.getToWarehouseId() != null) {
                OutsourceWarehouse wh = warehouseMapper.selectById(d.getToWarehouseId());
                m.put("warehouseName", wh != null ? wh.getWarehouseName() : "");
            }
            List<OutsourceDeliveryItem> items = deliveryItemMapper.selectList(
                new LambdaQueryWrapper<OutsourceDeliveryItem>().eq(OutsourceDeliveryItem::getDeliveryId, d.getId()));
            m.put("items", items);
            result.add(m);
        }
        return R.ok(result);
    }

    @DeleteMapping("/{id}/attach")
    public R<Void> deleteAttach(@PathVariable Long id) {
        MaterialOrder update = new MaterialOrder();
        update.setId(id);
        update.setAttachUrl("");
        orderMapper.updateById(update);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        itemMapper.delete(new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, id));
        orderMapper.deleteById(id);
        return R.ok();
    }

    // ==================== 私有 ====================

    /** 将 MaterialOrderItem 转为 Map，附带子物料组件列表 */
    private List<Map<String, Object>> buildItemMaps(List<MaterialOrderItem> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        return items.stream().map(it -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", it.getId());
            map.put("orderId", it.getOrderId());
            map.put("materialId", it.getMaterialId());
            map.put("materialName", it.getMaterialName());
            map.put("materialType", it.getMaterialType());
            map.put("unit", it.getUnit());
            map.put("orderQuantity", it.getOrderQuantity());
            map.put("receivedQuantity", it.getReceivedQuantity());
            map.put("defectReturnedQty", it.getDefectReturnedQty());
            map.put("unitPrice", it.getUnitPrice());
            map.put("amount", it.getAmount());
            map.put("remark", it.getRemark());
            // 查子物料组件（含库存、缺料、已发料）
            if (it.getMaterialId() != null) {
                List<OutsourceMaterialComponent> comps = componentMapper.selectList(
                    new LambdaQueryWrapper<OutsourceMaterialComponent>()
                        .eq(OutsourceMaterialComponent::getParentMaterialId, it.getMaterialId()));
                if (comps != null && !comps.isEmpty()) {
                    List<Map<String, Object>> compMaps = new ArrayList<>();
                    for (OutsourceMaterialComponent c : comps) {
                        Map<String, Object> cm = new LinkedHashMap<>();
                        cm.put("id", c.getId());
                        cm.put("childMaterialId", c.getChildMaterialId());
                        cm.put("quantity", c.getQuantity());
                        cm.put("lossRate", c.getLossRate());
                        cm.put("remark", c.getRemark());
                        // 需求总数 = 每套用量 × 下单数
                        BigDecimal compQty = c.getQuantity() != null ? c.getQuantity() : BigDecimal.ONE;
                        BigDecimal ordQty = it.getOrderQuantity() != null ? it.getOrderQuantity() : BigDecimal.ZERO;
                        BigDecimal demand = compQty.multiply(ordQty);
                        cm.put("demandQuantity", demand);
                        // 已发料 = 每套用量 × 已收数（按比例）
                        BigDecimal recQty = it.getReceivedQuantity() != null ? it.getReceivedQuantity() : BigDecimal.ZERO;
                        cm.put("deliveredQuantity", compQty.multiply(recQty));
                        // 查子物料的供应商信息（供"去采购"使用）
                        if (c.getChildMaterialId() != null) {
                            OutsourceMaterial childMat = materialMapper.selectById(c.getChildMaterialId());
                            if (childMat != null) {
                                cm.put("childMaterialName", childMat.getMaterialName());
                                cm.put("childUnit", childMat.getUnit());
                                cm.put("childMaterialType", childMat.getMaterialType());
                                cm.put("supplierIds", childMat.getSupplierIds());
                                cm.put("supplierName", childMat.getSupplierName());
                            }
                            // 查所有委外仓库库存总和
                            BigDecimal stock = BigDecimal.ZERO;
                            List<OutsourceWarehouseStock> stocks = warehouseStockMapper.selectList(
                                new LambdaQueryWrapper<OutsourceWarehouseStock>()
                                    .eq(OutsourceWarehouseStock::getMaterialId, c.getChildMaterialId())
                                    .eq(OutsourceWarehouseStock::getQualityType, "良品"));
                            if (stocks != null) {
                                for (OutsourceWarehouseStock s : stocks) {
                                    if (s.getQuantity() != null) stock = stock.add(s.getQuantity());
                                }
                            }
                            cm.put("stockQuantity", stock);
                            cm.put("shortage", demand.subtract(stock).max(BigDecimal.ZERO));
                        }
                        compMaps.add(cm);
                    }
                    map.put("components", compMaps);
                }
            }
            return map;
        }).toList();
    }

    private MaterialOrder parseOrder(Map<String, Object> body) {
        MaterialOrder o = new MaterialOrder();
        Object sid = body.get("supplierId");
        if (sid != null && !sid.toString().isBlank()) o.setSupplierId(Long.valueOf(sid.toString()));
        if (body.get("orderType") != null && !body.get("orderType").toString().isBlank())
            o.setOrderType(body.get("orderType").toString());
        Object twid = body.get("targetWarehouseId");
        if (twid != null && !twid.toString().isBlank()) o.setTargetWarehouseId(Long.valueOf(twid.toString()));
        Object dd = body.get("deliveryDate");
        if (dd != null && !dd.toString().isBlank()) o.setDeliveryDate(LocalDate.parse(dd.toString()));
        if (body.get("remark") != null) o.setRemark(body.get("remark").toString());
        if (body.get("attachUrl") != null) o.setAttachUrl(body.get("attachUrl").toString());
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
        BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
        BigDecimal after;
        if (stock == null) {
            stock = new OutsourceWarehouseStock();
            stock.setWarehouseId(warehouseId); stock.setMaterialId(materialId);
            stock.setQualityType(qualityType); after = delta;
            stock.setQuantity(after);
            warehouseStockMapper.insert(stock);
        } else {
            after = before.add(delta);
            stock.setQuantity(after);
            warehouseStockMapper.updateById(stock);
        }
    }

    /** 更新库存并写入流水日志 */
    private void updateStockLog(Long warehouseId, Long materialId, BigDecimal delta, String qualityType,
                                 String materialName, String changeType, String orderCode) {
        LambdaQueryWrapper<OutsourceWarehouseStock> w = new LambdaQueryWrapper<OutsourceWarehouseStock>()
            .eq(OutsourceWarehouseStock::getWarehouseId, warehouseId)
            .eq(OutsourceWarehouseStock::getMaterialId, materialId)
            .eq(OutsourceWarehouseStock::getQualityType, qualityType);
        OutsourceWarehouseStock stock = warehouseStockMapper.selectOne(w);
        BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
        BigDecimal after;
        if (stock == null) {
            stock = new OutsourceWarehouseStock();
            stock.setWarehouseId(warehouseId); stock.setMaterialId(materialId);
            stock.setQualityType(qualityType); after = delta;
            stock.setQuantity(after);
            warehouseStockMapper.insert(stock);
        } else {
            after = before.add(delta);
            stock.setQuantity(after);
            warehouseStockMapper.updateById(stock);
        }
        OutsourceStockLog logEntry = new OutsourceStockLog();
        logEntry.setWarehouseId(warehouseId); logEntry.setMaterialId(materialId);
        logEntry.setMaterialName(materialName); logEntry.setChangeType(changeType);
        logEntry.setChangeQuantity(delta); logEntry.setBeforeQuantity(before);
        logEntry.setAfterQuantity(after); logEntry.setRelatedOrderCode(orderCode);
        stockLogMapper.insert(logEntry);
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
