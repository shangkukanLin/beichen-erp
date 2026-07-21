package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.entity.OutsourceDelivery;
import com.beichen.erp.outsource.entity.OutsourceDeliveryItem;
import com.beichen.erp.outsource.entity.OutsourceStockLog;
import com.beichen.erp.outsource.entity.OutsourceWarehouseStock;
import com.beichen.erp.outsource.entity.MaterialOrder;
import com.beichen.erp.outsource.entity.MaterialOrderItem;
import com.beichen.erp.inventory.entity.InventoryWarehouseStock;
import com.beichen.erp.inventory.mapper.InventoryWarehouseMapper;
import com.beichen.erp.inventory.mapper.InventoryWarehouseStockMapper;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryItemMapper;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryMapper;
import com.beichen.erp.outsource.mapper.OutsourceStockLogMapper;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseStockMapper;
import com.beichen.erp.outsource.mapper.MaterialOrderMapper;
import com.beichen.erp.outsource.mapper.MaterialOrderItemMapper;
import com.beichen.erp.outsource.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryServiceImpl implements DeliveryService {

    private final OutsourceDeliveryMapper deliveryMapper;
    private final OutsourceDeliveryItemMapper itemMapper;
    private final OutsourceWarehouseStockMapper stockMapper;
    private final OutsourceStockLogMapper stockLogMapper;
    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;
    private final InventoryWarehouseStockMapper inventoryStockMapper;
    private final InventoryWarehouseMapper inventoryWarehouseMapper;
    private final InventoryWarehouseStockService inventoryStockService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Page<OutsourceDelivery> page(String deliveryType, Long factoryId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<OutsourceDelivery> w = new LambdaQueryWrapper<OutsourceDelivery>()
                .eq(deliveryType != null && !deliveryType.isBlank(), OutsourceDelivery::getDeliveryType, deliveryType)
                .eq(factoryId != null, OutsourceDelivery::getFactoryId, factoryId)
                .eq(code != null && !code.isBlank(), OutsourceDelivery::getCode, code)
                .orderByDesc(OutsourceDelivery::getId);
        return deliveryMapper.selectPage(new Page<>(pageNum, pageSize), w);
    }

    @Override
    public List<OutsourceDeliveryItem> getItems(Long deliveryId) {
        return itemMapper.selectList(new LambdaQueryWrapper<OutsourceDeliveryItem>()
                .eq(OutsourceDeliveryItem::getDeliveryId, deliveryId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items) {
        if (delivery.getDeliveryType() == null || delivery.getDeliveryType().isBlank()) {
            throw new BusinessException("收发类型不能为空");
        }
        if (delivery.getFactoryId() == null) {
            throw new BusinessException("加工厂不能为空");
        }
        // 校验
        if ("调拨".equals(delivery.getDeliveryType())) {
            if (delivery.getFromWarehouseId() == null) throw new BusinessException("来源仓库不能为空");
            if (delivery.getToWarehouseId() == null) throw new BusinessException("目标仓库不能为空");
        } else if ("发料".equals(delivery.getDeliveryType())) {
            if (delivery.getSupplierDirect() != null && delivery.getSupplierDirect() == 0 && delivery.getFromWarehouseId() == null)
                throw new BusinessException("非直发时来源仓库不能为空");
            if (delivery.getToWarehouseId() == null) throw new BusinessException("目标仓库不能为空");
        } else {
            if (delivery.getFromWarehouseId() == null) throw new BusinessException("来源仓库不能为空");
        }
        // 生成编码
        delivery.setCode(generateCode());
        delivery.setStatus("已确认");
        deliveryMapper.insert(delivery);

        // 插入明细 + 联动库存
        for (OutsourceDeliveryItem item : items) {
            item.setDeliveryId(delivery.getId());
            itemMapper.insert(item);
            BigDecimal qty = item.getQuantity();
            if ("发料".equals(delivery.getDeliveryType())) {
                // 扣减来源仓库（我方仓或委外仓）
                if (delivery.getFromWarehouseId() != null) {
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate(), item.getMaterialName(), item.getQualityType(), "发料出", delivery.getCode());
                }
                // 增加目标仓库（委外仓）
                if (delivery.getToWarehouseId() != null)
                    updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            item.getMaterialName(), "发料入", delivery.getCode());
            } else if ("调拨".equals(delivery.getDeliveryType())) {
                // 来源仓库-，目标仓库+
                if (delivery.getFromWarehouseId() != null)
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate(), item.getMaterialName(), item.getQualityType(), "调拨出", delivery.getCode());
                if (delivery.getToWarehouseId() != null)
                    adjustSourceStock(delivery.getToWarehouseId(), item.getMaterialId(), qty, item.getMaterialName(), item.getQualityType(), "调拨入", delivery.getCode());
            } else {
                String type = delivery.getDeliveryType();
                if (delivery.getFromWarehouseId() != null)
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate(), item.getMaterialName(), item.getQualityType(), type != null ? type : "退料", delivery.getCode());
            }
        }
        // 发料时同步加工单物料已发数量
        if ("发料".equals(delivery.getDeliveryType())) {
            syncDeliveredQuantity(delivery, items, true);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        OutsourceDelivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) {
            throw new BusinessException("单据不存在");
        }
        if ("已取消".equals(delivery.getStatus())) {
            throw new BusinessException("单据已取消，不可重复取消");
        }

        // 逆向库存
        List<OutsourceDeliveryItem> items = getItems(id);
        for (OutsourceDeliveryItem item : items) {
            BigDecimal qty = item.getQuantity();
            if ("发料".equals(delivery.getDeliveryType())) {
                // 恢复我方仓库库存
                if (delivery.getFromWarehouseId() != null && item.getMaterialId() != null) {
                    inventoryStockService.changeStock(delivery.getFromWarehouseId(), item.getMaterialName(), qty,
                        "取消发料", delivery.getCode(), "委外发料", item.getMaterialId(), null);
                }
                // 扣回委外仓库
                if (delivery.getToWarehouseId() != null)
                    updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty.negate(), item.getQualityType(),
                            item.getMaterialName(), "取消发料", delivery.getCode());
            } else if ("调拨".equals(delivery.getDeliveryType())) {
                // 逆向：来源仓库+，目标仓库-
                if (delivery.getFromWarehouseId() != null)
                    updateStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            item.getMaterialName(), "取消调拨出", delivery.getCode());
                if (delivery.getToWarehouseId() != null)
                    updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty.negate(), item.getQualityType(),
                            item.getMaterialName(), "取消调拨入", delivery.getCode());
            } else {
                String type = delivery.getDeliveryType();
                if (delivery.getFromWarehouseId() != null)
                    updateStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            item.getMaterialName(), "取消" + (type != null ? type : "收料"), delivery.getCode());
            }
        }

        // 发料取消时同步加工单物料已发数量（逆向）
        if ("发料".equals(delivery.getDeliveryType())) {
            syncDeliveredQuantity(delivery, items, false);
        }
        // 更新状态
        OutsourceDelivery update = new OutsourceDelivery();
        update.setId(id);
        update.setStatus("已取消");
        deliveryMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items) {
        OutsourceDelivery old = deliveryMapper.selectById(delivery.getId());
        if (old == null) throw new BusinessException("单据不存在");
        if ("已取消".equals(old.getStatus())) throw new BusinessException("已取消的单据不可编辑");

        // 1. 加载旧明细并逆向库存
        List<OutsourceDeliveryItem> oldItems = getItems(delivery.getId());
        // 逆向旧已发数量（仅发料）
        if ("发料".equals(old.getDeliveryType())) {
            syncDeliveredQuantity(old, oldItems, false);
        }
        for (OutsourceDeliveryItem item : oldItems) {
            BigDecimal qty = item.getQuantity();
            if ("发料".equals(old.getDeliveryType())) {
                // 恢复我方仓库库存
                if (old.getFromWarehouseId() != null && item.getMaterialId() != null) {
                    inventoryStockService.changeStock(old.getFromWarehouseId(), item.getMaterialName(), qty,
                        "编辑回滚-发料", old.getCode(), "委外发料", item.getMaterialId(), null);
                }
                // 扣回委外仓库
                if (old.getToWarehouseId() != null)
                    updateStock(old.getToWarehouseId(), item.getMaterialId(), qty.negate(), item.getQualityType(),
                            item.getMaterialName(), "编辑回滚-发料", old.getCode());
            } else if ("调拨".equals(old.getDeliveryType())) {
                // 逆向：来源仓库+，目标仓库-
                if (old.getFromWarehouseId() != null)
                    updateStock(old.getFromWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            item.getMaterialName(), "编辑回滚-调拨出", old.getCode());
                if (old.getToWarehouseId() != null)
                    updateStock(old.getToWarehouseId(), item.getMaterialId(), qty.negate(), item.getQualityType(),
                            item.getMaterialName(), "编辑回滚-调拨入", old.getCode());
            } else {
                String type = old.getDeliveryType();
                if (old.getFromWarehouseId() != null)
                    updateStock(old.getFromWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            item.getMaterialName(), "编辑回滚-" + (type != null ? type : "收料"), old.getCode());
            }
        }
        // 2. 删旧明细
        itemMapper.delete(new LambdaQueryWrapper<OutsourceDeliveryItem>().eq(OutsourceDeliveryItem::getDeliveryId, delivery.getId()));
        // 3. 更新主表
        delivery.setCode(old.getCode());
        delivery.setStatus("已确认");
        deliveryMapper.updateById(delivery);
        // 4. 插入新明细 + 重新算库存
        for (OutsourceDeliveryItem item : items) {
            item.setDeliveryId(delivery.getId());
            itemMapper.insert(item);
            BigDecimal qty = item.getQuantity();
            if ("发料".equals(delivery.getDeliveryType())) {
                if (delivery.getToWarehouseId() != null)
                    updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            item.getMaterialName(), "编辑-发料", delivery.getCode());
            } else {
                String type = delivery.getDeliveryType();
                if (delivery.getFromWarehouseId() != null)
                    updateStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate(), item.getQualityType(),
                            item.getMaterialName(), "编辑-" + (type != null ? type : "收料"), delivery.getCode());
            }
        }
        // 发料时重新应用新已发数量
        if ("发料".equals(delivery.getDeliveryType())) {
            syncDeliveredQuantity(delivery, items, true);
        }
    }

    @Override
    public OutsourceDelivery getById(Long id) {
        return deliveryMapper.selectById(id);
    }

    @Override
    public void clearAttachUrl(Long id) {
        OutsourceDelivery update = new OutsourceDelivery();
        update.setId(id);
        update.setAttachUrl("");
        deliveryMapper.updateById(update);
    }

    // ==================== 私有方法 ====================

    /**
     * 同步加工单物料的已发数量
     * 发料时，根据物料名称匹配该工厂下"生产中"的加工单物料记录，累加/扣减 delivered_quantity
     * @param delivery 收发单
     * @param items 收发明细
     * @param increase true=发料(累加) / false=取消(扣减)
     */
    private void syncDeliveredQuantity(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items, boolean increase) {
        if (delivery.getFactoryId() == null) return;
        for (OutsourceDeliveryItem item : items) {
            if (item.getMaterialName() == null && item.getMaterialId() == null) continue;
            try {
                // 找到该工厂"生产中"状态的加工单物料记录
                String findSql = "SELECT om.id, om.delivered_quantity FROM outsource_order_material om " +
                    "INNER JOIN outsource_order_product op ON om.product_id = op.id " +
                    "INNER JOIN outsource_order o ON op.order_id = o.id " +
                    "WHERE o.factory_id = ? AND o.status = '生产中' " +
                    "AND (om.material_name = ? OR om.material_id = ?) LIMIT 1";
                List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                    findSql, delivery.getFactoryId(),
                    item.getMaterialName() != null ? item.getMaterialName() : "",
                    item.getMaterialId() != null ? item.getMaterialId() : -1L);
                if (rows.isEmpty()) continue;
                Long omId = ((Number) rows.get(0).get("id")).longValue();
                BigDecimal oldQty = (BigDecimal) rows.get(0).get("delivered_quantity");
                if (oldQty == null) oldQty = BigDecimal.ZERO;
                BigDecimal newQty = increase ? oldQty.add(item.getQuantity()) : oldQty.subtract(item.getQuantity());
                if (newQty.compareTo(BigDecimal.ZERO) < 0) newQty = BigDecimal.ZERO;
                jdbcTemplate.update("UPDATE outsource_order_material SET delivered_quantity = ? WHERE id = ?", newQty, omId);
                log.info("加工单物料(ID={})已发数量: {} → {}, 物料: {}", omId, oldQty, newQty, item.getMaterialName());
            } catch (Exception e) {
                log.warn("同步已发数量失败: material={}, err={}", item.getMaterialName(), e.getMessage());
            }
        }
    }

    /**
     * 计算库存变动量：
     * 发料 → 库存增加(+)
     * 收料/退料 → 库存减少(-)
     */
    private BigDecimal getStockDelta(String deliveryType, BigDecimal quantity) {
        if ("发料".equals(deliveryType)) {
            return quantity;
        } else {
            return quantity.negate();
        }
    }

    /**
     * 更新仓库库存并写入流水日志
     */
    private void updateStock(Long warehouseId, Long materialId, BigDecimal delta, String qualityType,
                             String materialName, String changeType, String deliveryCode) {
        String qt = qualityType != null ? qualityType : "良品";
        LambdaQueryWrapper<OutsourceWarehouseStock> w = new LambdaQueryWrapper<OutsourceWarehouseStock>()
                .eq(OutsourceWarehouseStock::getWarehouseId, warehouseId)
                .eq(OutsourceWarehouseStock::getMaterialId, materialId)
                .eq(OutsourceWarehouseStock::getQualityType, qt);
        OutsourceWarehouseStock stock = stockMapper.selectOne(w);
        BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
        BigDecimal after;
        if (stock == null) {
            stock = new OutsourceWarehouseStock();
            stock.setWarehouseId(warehouseId);
            stock.setMaterialId(materialId);
            stock.setQualityType(qt);
            after = delta;
            stock.setQuantity(after);
            stockMapper.insert(stock);
        } else {
            after = before.add(delta);
            stock.setQuantity(after);
            stockMapper.updateById(stock);
        }
        // 写入流水日志
        OutsourceStockLog logEntry = new OutsourceStockLog();
        logEntry.setWarehouseId(warehouseId);
        logEntry.setMaterialId(materialId);
        logEntry.setMaterialName(materialName);
        logEntry.setChangeType(changeType);
        logEntry.setChangeQuantity(delta);
        logEntry.setBeforeQuantity(before);
        logEntry.setAfterQuantity(after);
        logEntry.setRelatedOrderCode(deliveryCode);
        stockLogMapper.insert(logEntry);
    }

    /**
     * 生成编码：DEL-YYYYMMDDNNN
     */
    private String generateCode() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String likePattern = "DEL-" + dateStr;

        LambdaQueryWrapper<OutsourceDelivery> w = new LambdaQueryWrapper<OutsourceDelivery>()
                .likeRight(OutsourceDelivery::getCode, likePattern)
                .orderByDesc(OutsourceDelivery::getCode)
                .last("LIMIT 1");
        OutsourceDelivery last = deliveryMapper.selectOne(w);

        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                String numPart = last.getCode().substring(last.getCode().length() - 3);
                seq = Integer.parseInt(numPart) + 1;
            } catch (Exception e) {
                seq = 1;
            }
        }
        return "DEL-" + dateStr + String.format("%03d", seq);
    }

    /** 调整库存：自动判断仓库类型（我方仓用 changeStock，委外仓用 updateStock） */
    private void adjustSourceStock(Long warehouseId, Long materialId, BigDecimal delta, String materialName,
                                    String qualityType, String changeType, String orderCode) {
        // 检查是否为我方仓
        if (inventoryWarehouseMapper.selectById(warehouseId) != null) {
            inventoryStockService.changeStock(warehouseId, materialName, delta, changeType, orderCode, "物料收发", materialId, null);
        } else {
            updateStock(warehouseId, materialId, delta, qualityType, materialName, changeType, orderCode);
        }
    }

    /** 扣减进销存仓库库存（统一走 changeStock，自动写 inventory_stock_log） */
    private void deductInventoryStock(Long warehouseId, Long materialId, java.math.BigDecimal qty, String materialName, String qualityType, String deliveryCode) {
        inventoryStockService.changeStock(warehouseId, materialName, qty.negate(),
            "委外发料出库", deliveryCode, "委外发料", materialId, null);
    }

    @Override
    public java.math.BigDecimal calcWeightedPrice(Long factoryId, String materialName) {
        if (factoryId == null || materialName == null) return java.math.BigDecimal.ZERO;
        try {
            List<MaterialOrder> orders = materialOrderMapper.selectList(
                new LambdaQueryWrapper<MaterialOrder>().eq(MaterialOrder::getSupplierId, factoryId));
            java.math.BigDecimal totalAmount = java.math.BigDecimal.ZERO, totalQty = java.math.BigDecimal.ZERO;
            for (MaterialOrder o : orders) {
                List<MaterialOrderItem> mItems = materialOrderItemMapper.selectList(
                    new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, o.getId()));
                for (MaterialOrderItem it : mItems) {
                    if (java.util.Objects.equals(materialName, it.getMaterialName())) {
                        java.math.BigDecimal qty = it.getOrderQuantity() != null ? it.getOrderQuantity() : java.math.BigDecimal.ZERO;
                        java.math.BigDecimal price = it.getUnitPrice() != null ? it.getUnitPrice() : java.math.BigDecimal.ZERO;
                        totalAmount = totalAmount.add(qty.multiply(price));
                        totalQty = totalQty.add(qty);
                    }
                }
            }
            if (totalQty.compareTo(java.math.BigDecimal.ZERO) > 0)
                return totalAmount.divide(totalQty, 4, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) { log.warn("计算加权均价失败: {}", e.getMessage()); }
        return java.math.BigDecimal.ZERO;
    }
}
