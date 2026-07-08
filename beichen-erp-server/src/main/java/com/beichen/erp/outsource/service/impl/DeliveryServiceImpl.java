package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.entity.OutsourceDelivery;
import com.beichen.erp.outsource.entity.OutsourceDeliveryItem;
import com.beichen.erp.outsource.entity.OutsourceWarehouseStock;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryItemMapper;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryMapper;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseStockMapper;
import com.beichen.erp.outsource.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryServiceImpl implements DeliveryService {

    private final OutsourceDeliveryMapper deliveryMapper;
    private final OutsourceDeliveryItemMapper itemMapper;
    private final OutsourceWarehouseStockMapper stockMapper;

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
        if ("发料".equals(delivery.getDeliveryType()) && delivery.getSupplierDirect() != null && delivery.getSupplierDirect() == 0 && delivery.getFromWarehouseId() == null) {
            throw new BusinessException("非直发时来源仓库不能为空");
        }
        if ("发料".equals(delivery.getDeliveryType()) && delivery.getToWarehouseId() == null) {
            throw new BusinessException("目标仓库不能为空");
        }
        if (!"发料".equals(delivery.getDeliveryType()) && delivery.getFromWarehouseId() == null) {
            throw new BusinessException("来源仓库不能为空");
        }
        // 生成编码
        delivery.setCode(generateCode());
        delivery.setStatus("已确认");
        deliveryMapper.insert(delivery);

        // 插入明细 + 联动库存
        for (OutsourceDeliveryItem item : items) {
            item.setDeliveryId(delivery.getId());
            itemMapper.insert(item);
            // 库存联动：发料→目标仓库+，收料/退料→来源仓库-
            BigDecimal qty = item.getQuantity();
            if ("发料".equals(delivery.getDeliveryType())) {
                if (delivery.getToWarehouseId() != null) updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty);
            } else {
                if (delivery.getFromWarehouseId() != null) updateStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate());
            }
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
                if (delivery.getToWarehouseId() != null) updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty.negate());
            } else {
                if (delivery.getFromWarehouseId() != null) updateStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty);
            }
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

        // 1. 逆向旧库存
        List<OutsourceDeliveryItem> oldItems = getItems(delivery.getId());
        for (OutsourceDeliveryItem item : oldItems) {
            BigDecimal qty = item.getQuantity();
            if ("发料".equals(old.getDeliveryType())) {
                if (old.getToWarehouseId() != null) updateStock(old.getToWarehouseId(), item.getMaterialId(), qty.negate());
            } else {
                if (old.getFromWarehouseId() != null) updateStock(old.getFromWarehouseId(), item.getMaterialId(), qty);
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
                if (delivery.getToWarehouseId() != null) updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty);
            } else {
                if (delivery.getFromWarehouseId() != null) updateStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate());
            }
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
     * 更新仓库库存（先查后upsert）
     */
    private void updateStock(Long warehouseId, Long materialId, BigDecimal delta) {
        LambdaQueryWrapper<OutsourceWarehouseStock> w = new LambdaQueryWrapper<OutsourceWarehouseStock>()
                .eq(OutsourceWarehouseStock::getWarehouseId, warehouseId)
                .eq(OutsourceWarehouseStock::getMaterialId, materialId);
        OutsourceWarehouseStock stock = stockMapper.selectOne(w);
        if (stock == null) {
            stock = new OutsourceWarehouseStock();
            stock.setWarehouseId(warehouseId);
            stock.setMaterialId(materialId);
            stock.setQuantity(delta);
            stockMapper.insert(stock);
        } else {
            stock.setQuantity(stock.getQuantity().add(delta));
            stockMapper.updateById(stock);
        }
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
}
