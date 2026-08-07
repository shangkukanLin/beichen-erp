package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.entity.OutsourceDelivery;
import com.beichen.erp.outsource.entity.OutsourceDeliveryItem;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceStockLog;
import com.beichen.erp.outsource.entity.OutsourceWarehouseStock;
import com.beichen.erp.outsource.entity.MaterialOrder;
import com.beichen.erp.outsource.entity.MaterialOrderItem;
import com.beichen.erp.inventory.entity.InventoryWarehouseStock;
import com.beichen.erp.inventory.mapper.InventoryWarehouseMapper;
import com.beichen.erp.inventory.mapper.InventoryWarehouseStockMapper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.outsource.common.DeliveryType;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.outsource.common.OutsourceStockChangeType;
import com.beichen.erp.outsource.common.MaterialOrderStatus;
import com.beichen.erp.outsource.common.DefectHandleType;
import com.beichen.erp.finance.service.PayableHelper;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryItemMapper;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryMapper;
import com.beichen.erp.outsource.mapper.OutsourceStockLogMapper;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseStockMapper;
import com.beichen.erp.outsource.mapper.MaterialOrderMapper;
import com.beichen.erp.outsource.mapper.MaterialOrderItemMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final InventoryWarehouseStockMapper inventoryStockMapper;
    private final InventoryWarehouseMapper inventoryWarehouseMapper;
    private final InventoryWarehouseStockService inventoryStockService;
    private final PayableHelper payableHelper;
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
        if (DeliveryType.TRANSFER.name().equals(delivery.getDeliveryType())) {
            if (delivery.getFromWarehouseId() == null) throw new BusinessException("来源仓库不能为空");
            if (delivery.getToWarehouseId() == null) throw new BusinessException("目标仓库不能为空");
        } else if (DeliveryType.DELIVERY.name().equals(delivery.getDeliveryType())) {
            if (delivery.getSupplierDirect() != null && delivery.getSupplierDirect() == 0 && delivery.getFromWarehouseId() == null)
                throw new BusinessException("非直发时来源仓库不能为空");
            if (delivery.getToWarehouseId() == null) throw new BusinessException("目标仓库不能为空");
        } else {
            if (delivery.getFromWarehouseId() == null) throw new BusinessException("来源仓库不能为空");
        }
        // 生成编码，草稿态存盘，不落库存
        delivery.setCode(generateCode());
        delivery.setStatus(DocStatus.DRAFT.name());
        deliveryMapper.insert(delivery);

        // 插入明细（仅存盘，库存动作推迟到审核时执行）
        for (OutsourceDeliveryItem item : items) {
            item.setDeliveryId(delivery.getId());
            itemMapper.insert(item);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        OutsourceDelivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) throw new BusinessException("单据不存在");
        if (!DocStatus.DRAFT.name().equals(delivery.getStatus())) {
            throw new BusinessException("仅草稿状态可以审核");
        }
        List<OutsourceDeliveryItem> items = getItems(id);
        // 审核通过：扣/增库存 + 写流水 + 同步已发数量
        applyDeliveryStock(delivery, items);
        OutsourceDelivery update = new OutsourceDelivery();
        update.setId(id);
        update.setStatus(DocStatus.AUDITED.name());
        deliveryMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unaudit(Long id) {
        OutsourceDelivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) throw new BusinessException("单据不存在");
        if (!DocStatus.AUDITED.name().equals(delivery.getStatus())) {
            throw new BusinessException("仅已审核状态可以反审核");
        }
        // 反审核：逆向库存 + 回滚已发数量，回到草稿
        List<OutsourceDeliveryItem> items = getItems(id);
        reverseDeliveryStock(delivery, items);
        OutsourceDelivery update = new OutsourceDelivery();
        update.setId(id);
        update.setStatus(DocStatus.DRAFT.name());
        deliveryMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditMaterialDelivery(Long id) {
        OutsourceDelivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) throw new BusinessException("单据不存在");
        if (!DocStatus.DRAFT.name().equals(delivery.getStatus())) {
            throw new BusinessException("仅草稿状态可以审核");
        }
        // 仅处理委外物料订单的收货/退不良单
        if (!DeliveryType.RECEIVE.name().equals(delivery.getDeliveryType())
                && !DeliveryType.DEFECT_RETURN.name().equals(delivery.getDeliveryType())) {
            throw new BusinessException("该单据非物料订单收货/退不良单，不可审核");
        }
        Long orderId = delivery.getSourceOrderId();
        if (orderId == null) throw new BusinessException("收货单缺少关联物料订单");
        MaterialOrder order = materialOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("关联物料订单不存在");
        List<OutsourceDeliveryItem> items = getItems(id);
        if (items.isEmpty()) throw new BusinessException("单据无明细，无法审核");

        // 1. 库存变动：收货入库(+)、退不良出库(-)，均作用于目标仓库
        boolean isReceive = DeliveryType.RECEIVE.name().equals(delivery.getDeliveryType());
        for (OutsourceDeliveryItem item : items) {
            if (item.getMaterialId() == null || item.getQuantity() == null) continue;
            String matName = getMaterialNameById(item.getMaterialId());
            if (delivery.getToWarehouseId() == null) throw new BusinessException("单据缺少目标仓库，无法审核");
            if (isReceive) {
                // 收货入库：目标仓库良品 +qty，写外协库存流水
                changeOutsourceStock(delivery.getToWarehouseId(), item.getMaterialId(), item.getQuantity(),
                        QualityType.GOOD.getCode(), "委外收货入库", delivery.getCode());
            } else {
                // 退不良：仅维修返还（非折现退款）扣减目标仓库良品库存
                if (!DefectHandleType.CASH_REFUND.getCode().equals(item.getHandleType())) {
                    changeOutsourceStock(delivery.getToWarehouseId(), item.getMaterialId(), item.getQuantity().negate(),
                            QualityType.GOOD.getCode(), "退不良扣回", delivery.getCode());
                }
            }
        }

        // 2. 生成应付：收货为正应付，退不良为负（冲减）
        BigDecimal totalAmount = items.stream()
                .map(it -> (it.getAmount() != null ? it.getAmount() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAmount.compareTo(BigDecimal.ZERO) != 0) {
            payableHelper.createPayable(order.getSupplierId(), "OUTSOURCE_MATERIAL_DELIVERY", delivery.getCode(),
                    delivery.getId(), totalAmount, delivery.getDeliveryDate(), "委外物料订单" + (isReceive ? "收货" : "退不良"));
        }

        // 3. 回写订单明细累计数量
        for (OutsourceDeliveryItem item : items) {
            if (item.getItemId() == null || item.getQuantity() == null) continue;
            MaterialOrderItem oi = materialOrderItemMapper.selectById(item.getItemId());
            if (oi == null) continue;
            if (isReceive) {
                oi.setReceivedQuantity(safeAdd(oi.getReceivedQuantity(), item.getQuantity()));
            } else {
                oi.setDefectReturnedQty(safeAdd(oi.getDefectReturnedQty(), item.getQuantity()));
            }
            materialOrderItemMapper.updateById(oi);
        }
        // 4. 重算订单状态
        recomputeMaterialOrderStatus(orderId);

        // 5. 单据置为已审核
        OutsourceDelivery up = new OutsourceDelivery();
        up.setId(id);
        up.setStatus(DocStatus.AUDITED.name());
        deliveryMapper.updateById(up);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unauditMaterialDelivery(Long id) {
        OutsourceDelivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) throw new BusinessException("单据不存在");
        if (!DocStatus.AUDITED.name().equals(delivery.getStatus())) {
            throw new BusinessException("仅已审核状态可以反审核");
        }
        if (!DeliveryType.RECEIVE.name().equals(delivery.getDeliveryType())
                && !DeliveryType.DEFECT_RETURN.name().equals(delivery.getDeliveryType())) {
            throw new BusinessException("该单据非物料订单收货/退不良单，不可反审核");
        }
        Long orderId = delivery.getSourceOrderId();
        if (orderId == null) throw new BusinessException("收货单缺少关联物料订单");
        List<OutsourceDeliveryItem> items = getItems(id);

        // 1. 逆向库存
        boolean isReceive = DeliveryType.RECEIVE.name().equals(delivery.getDeliveryType());
        for (OutsourceDeliveryItem item : items) {
            if (item.getMaterialId() == null || item.getQuantity() == null) continue;
            String matName = getMaterialNameById(item.getMaterialId());
            if (delivery.getToWarehouseId() == null) continue;
            if (isReceive) {
                // 反审核：回滚收货入库（目标仓库良品 -qty）
                changeOutsourceStock(delivery.getToWarehouseId(), item.getMaterialId(), item.getQuantity().negate(),
                        QualityType.GOOD.getCode(), "退审收货入库", delivery.getCode());
            } else {
                // 反审核：恢复维修返还扣减的库存（+qty）
                if (!DefectHandleType.CASH_REFUND.getCode().equals(item.getHandleType())) {
                    changeOutsourceStock(delivery.getToWarehouseId(), item.getMaterialId(), item.getQuantity(),
                            QualityType.GOOD.getCode(), "退审退不良", delivery.getCode());
                }
            }
        }

        // 2. 冲回应付（已付款的阻止）
        payableHelper.reversePayable(delivery.getId());

        // 3. 回滚订单明细累计数量
        for (OutsourceDeliveryItem item : items) {
            if (item.getItemId() == null || item.getQuantity() == null) continue;
            MaterialOrderItem oi = materialOrderItemMapper.selectById(item.getItemId());
            if (oi == null) continue;
            if (isReceive) {
                oi.setReceivedQuantity(safeSubtract(oi.getReceivedQuantity(), item.getQuantity()));
            } else {
                oi.setDefectReturnedQty(safeSubtract(oi.getDefectReturnedQty(), item.getQuantity()));
            }
            materialOrderItemMapper.updateById(oi);
        }
        // 4. 重算订单状态
        recomputeMaterialOrderStatus(orderId);

        // 5. 单据回到草稿
        OutsourceDelivery up = new OutsourceDelivery();
        up.setId(id);
        up.setStatus(DocStatus.DRAFT.name());
        deliveryMapper.updateById(up);
    }

    /** 安全累加，null 视为 0 */
    private BigDecimal safeAdd(BigDecimal a, BigDecimal b) {
        return (a == null ? BigDecimal.ZERO : a).add(b == null ? BigDecimal.ZERO : b);
    }

    /** 安全相减，结果不为负 */
    private BigDecimal safeSubtract(BigDecimal a, BigDecimal b) {
        BigDecimal r = (a == null ? BigDecimal.ZERO : a).subtract(b == null ? BigDecimal.ZERO : b);
        return r.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : r;
    }

    /** 根据累计收货/退不良数量重算物料订单状态：全部收满→FINISHED，部分→RECEIVING，否则 PENDING */
    private void recomputeMaterialOrderStatus(Long orderId) {
        MaterialOrder order = materialOrderMapper.selectById(orderId);
        if (order == null) return;
        if (MaterialOrderStatus.CANCELLED.name().equals(order.getStatus())) return;
        List<MaterialOrderItem> items = materialOrderItemMapper.selectList(
                new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, orderId));
        boolean allDone = true, anyReceived = false;
        for (MaterialOrderItem it : items) {
            BigDecimal ord = it.getOrderQuantity() != null ? it.getOrderQuantity() : BigDecimal.ZERO;
            BigDecimal rec = it.getReceivedQuantity() != null ? it.getReceivedQuantity() : BigDecimal.ZERO;
            if (rec.compareTo(BigDecimal.ZERO) > 0) anyReceived = true;
            if (rec.compareTo(ord) < 0) allDone = false;
        }
        if (allDone && !items.isEmpty()) {
            order.setStatus(MaterialOrderStatus.FINISHED.name());
            order.setFinishTime(LocalDateTime.now());
        } else if (anyReceived) {
            order.setStatus(MaterialOrderStatus.RECEIVING.name());
            order.setFinishTime(null);
        } else {
            order.setStatus(MaterialOrderStatus.PENDING.name());
            order.setFinishTime(null);
        }
        materialOrderMapper.updateById(order);
    }

    /** 变更外协库存（outsource_warehouse_stock）并写流水日志（outsource_stock_log），供物料订单收货/退不良使用 */
    private void changeOutsourceStock(Long warehouseId, Long materialId, BigDecimal delta, String qualityType,
                                      String changeType, String relatedCode) {
        if (warehouseId == null || materialId == null) return;
        LambdaQueryWrapper<OutsourceWarehouseStock> w = new LambdaQueryWrapper<OutsourceWarehouseStock>()
                .eq(OutsourceWarehouseStock::getWarehouseId, warehouseId)
                .eq(OutsourceWarehouseStock::getMaterialId, materialId)
                .eq(OutsourceWarehouseStock::getQualityType, qualityType);
        OutsourceWarehouseStock stock = warehouseStockMapper.selectOne(w);
        BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
        BigDecimal after;
        if (stock == null) {
            stock = new OutsourceWarehouseStock();
            stock.setWarehouseId(warehouseId);
            stock.setMaterialId(materialId);
            stock.setQualityType(qualityType);
            after = delta;
            stock.setQuantity(after);
            warehouseStockMapper.insert(stock);
        } else {
            after = before.add(delta);
            stock.setQuantity(after);
            warehouseStockMapper.updateById(stock);
        }
        OutsourceStockLog logEntry = new OutsourceStockLog();
        logEntry.setWarehouseId(warehouseId);
        logEntry.setMaterialId(materialId);
        logEntry.setMaterialName(getMaterialNameById(materialId));
        logEntry.setChangeType(changeType);
        logEntry.setChangeQuantity(delta);
        logEntry.setBeforeQuantity(before);
        logEntry.setAfterQuantity(after);
        logEntry.setRelatedOrderCode(relatedCode);
        stockLogMapper.insert(logEntry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        OutsourceDelivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) {
            throw new BusinessException("单据不存在");
        }
        if (DocStatus.CANCELLED.name().equals(delivery.getStatus())) {
            throw new BusinessException("单据已取消，不可重复取消");
        }
        // 已审核单据作废前需先逆向库存，保证账实一致
        if (DocStatus.AUDITED.name().equals(delivery.getStatus())) {
            reverseDeliveryStock(delivery, getItems(id));
        }

        // 逆向库存
        List<OutsourceDeliveryItem> items = getItems(id);
        for (OutsourceDeliveryItem item : items) {
            BigDecimal qty = item.getQuantity();
            if (DeliveryType.DELIVERY.name().equals(delivery.getDeliveryType())) {
                // 恢复我方仓库库存
                if (delivery.getFromWarehouseId() != null && item.getMaterialId() != null) {
                    inventoryStockService.changeStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty,
                        StockChangeType.OUTSOURCE_CANCEL_DELIVERY, delivery.getCode(), RelatedBillType.OUTSOURCE_DELIVERY, null, delivery.getId(), null);
                }
                // 扣回委外仓库
                if (delivery.getToWarehouseId() != null)
                    updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty.negate(), item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), OutsourceStockChangeType.CANCEL_DELIVERY.name(), delivery.getCode());
            } else if (DeliveryType.TRANSFER.name().equals(delivery.getDeliveryType())) {
                // 逆向：来源仓库+，目标仓库-
                if (delivery.getFromWarehouseId() != null)
                    updateStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), OutsourceStockChangeType.CANCEL_TRANSFER_OUT.name(), delivery.getCode());
                if (delivery.getToWarehouseId() != null)
                    updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty.negate(), item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), OutsourceStockChangeType.CANCEL_TRANSFER_IN.name(), delivery.getCode());
            } else {
                String type = delivery.getDeliveryType();
                if (delivery.getFromWarehouseId() != null)
                    updateStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), "取消" + (type != null ? type : "收料"), delivery.getCode());
            }
        }

        // 发料取消时同步加工单物料已发数量（逆向）
        if (DeliveryType.DELIVERY.name().equals(delivery.getDeliveryType())) {
            syncDeliveredQuantity(delivery, items, false);
        }
        // 更新状态
        OutsourceDelivery update = new OutsourceDelivery();
        update.setId(id);
        update.setStatus(DocStatus.CANCELLED.name());
        deliveryMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items) {
        OutsourceDelivery old = deliveryMapper.selectById(delivery.getId());
        if (old == null) throw new BusinessException("单据不存在");
        if (!DocStatus.DRAFT.name().equals(old.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑，已审核单据请先反审核");
        }

        // 草稿态编辑不触碰库存，仅删旧明细、更新主表、插新明细
        // 1. 删旧明细
        itemMapper.delete(new LambdaQueryWrapper<OutsourceDeliveryItem>().eq(OutsourceDeliveryItem::getDeliveryId, delivery.getId()));
        // 2. 更新主表（保持草稿状态）
        delivery.setCode(old.getCode());
        delivery.setStatus(DocStatus.DRAFT.name());
        deliveryMapper.updateById(delivery);
        // 3. 插入新明细（库存动作推迟到审核）
        for (OutsourceDeliveryItem item : items) {
            item.setDeliveryId(delivery.getId());
            itemMapper.insert(item);
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
     * 审核通过：扣/增库存 + 写流水 + 发料时同步加工单已发数量
     * 抽取自原 create，使库存动作统一在审核时发生
     */
    private void applyDeliveryStock(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items) {
        for (OutsourceDeliveryItem item : items) {
            BigDecimal qty = item.getQuantity();
            if (DeliveryType.DELIVERY.name().equals(delivery.getDeliveryType())) {
                // 扣减来源仓库（我方仓或委外仓）
                if (delivery.getFromWarehouseId() != null) {
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate(), getMaterialNameById(item.getMaterialId()), item.getQualityType(), OutsourceStockChangeType.DELIVERY_OUT.name(), delivery.getCode());
                }
                // 增加目标仓库（委外仓）
                if (delivery.getToWarehouseId() != null)
                    updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), OutsourceStockChangeType.DELIVERY_IN.name(), delivery.getCode());
            } else if (DeliveryType.TRANSFER.name().equals(delivery.getDeliveryType())) {
                // 来源仓库-，目标仓库+
                if (delivery.getFromWarehouseId() != null)
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate(), getMaterialNameById(item.getMaterialId()), item.getQualityType(), OutsourceStockChangeType.TRANSFER_OUT.name(), delivery.getCode());
                if (delivery.getToWarehouseId() != null)
                    adjustSourceStock(delivery.getToWarehouseId(), item.getMaterialId(), qty, getMaterialNameById(item.getMaterialId()), item.getQualityType(), OutsourceStockChangeType.TRANSFER_IN.name(), delivery.getCode());
            } else {
                String type = delivery.getDeliveryType();
                if (delivery.getFromWarehouseId() != null)
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate(), getMaterialNameById(item.getMaterialId()), item.getQualityType(), type != null ? type : "退料", delivery.getCode());
            }
        }
        // 发料时同步加工单物料已发数量
        if (DeliveryType.DELIVERY.name().equals(delivery.getDeliveryType())) {
            syncDeliveredQuantity(delivery, items, true);
        }
    }

    /**
     * 反审核/取消：逆向库存 + 发料时回滚已发数量
     */
    private void reverseDeliveryStock(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items) {
        for (OutsourceDeliveryItem item : items) {
            BigDecimal qty = item.getQuantity();
            if (DeliveryType.DELIVERY.name().equals(delivery.getDeliveryType())) {
                // 恢复我方仓库库存
                if (delivery.getFromWarehouseId() != null && item.getMaterialId() != null) {
                    inventoryStockService.changeStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty,
                        StockChangeType.OUTSOURCE_CANCEL_DELIVERY, delivery.getCode(), RelatedBillType.OUTSOURCE_DELIVERY, null, delivery.getId(), null);
                }
                // 扣回委外仓库
                if (delivery.getToWarehouseId() != null)
                    updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty.negate(), item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), OutsourceStockChangeType.CANCEL_DELIVERY.name(), delivery.getCode());
            } else if (DeliveryType.TRANSFER.name().equals(delivery.getDeliveryType())) {
                // 逆向：来源仓库+，目标仓库-
                if (delivery.getFromWarehouseId() != null)
                    updateStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), OutsourceStockChangeType.CANCEL_TRANSFER_OUT.name(), delivery.getCode());
                if (delivery.getToWarehouseId() != null)
                    updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty.negate(), item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), OutsourceStockChangeType.CANCEL_TRANSFER_IN.name(), delivery.getCode());
            } else {
                String type = delivery.getDeliveryType();
                if (delivery.getFromWarehouseId() != null)
                    updateStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), "取消" + (type != null ? type : "收料"), delivery.getCode());
            }
        }
        // 发料时逆向已发数量
        if (DeliveryType.DELIVERY.name().equals(delivery.getDeliveryType())) {
            syncDeliveredQuantity(delivery, items, false);
        }
    }

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
            if (item.getMaterialId() == null) continue;
            try {
                String findSql = "SELECT om.id, om.delivered_quantity FROM outsource_order_material om " +
                    "INNER JOIN outsource_order_product op ON om.product_id = op.id " +
                    "INNER JOIN outsource_order o ON op.order_id = o.id " +
                    "WHERE o.factory_id = ? AND o.status = '生产中' " +
                    "AND om.outsource_material_id = ? LIMIT 1";
                List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                    findSql, delivery.getFactoryId(), item.getMaterialId());
                if (rows.isEmpty()) continue;
                Long omId = ((Number) rows.get(0).get("id")).longValue();
                BigDecimal oldQty = (BigDecimal) rows.get(0).get("delivered_quantity");
                if (oldQty == null) oldQty = BigDecimal.ZERO;
                BigDecimal newQty = increase ? oldQty.add(item.getQuantity()) : oldQty.subtract(item.getQuantity());
                if (newQty.compareTo(BigDecimal.ZERO) < 0) newQty = BigDecimal.ZERO;
                jdbcTemplate.update("UPDATE outsource_order_material SET delivered_quantity = ? WHERE id = ?", newQty, omId);
                log.info("加工单物料(ID={})已发数量: {} → {}, 物料: {}", omId, oldQty, newQty, getMaterialNameById(item.getMaterialId()));
            } catch (Exception e) {
                log.warn("同步已发数量失败: material={}, err={}", getMaterialNameById(item.getMaterialId()), e.getMessage());
            }
        }
    }

    /**
     * 计算库存变动量：
     * 发料 → 库存增加(+)
     * 收料/退料 → 库存减少(-)
     */
    private BigDecimal getStockDelta(String deliveryType, BigDecimal quantity) {
        if (DeliveryType.DELIVERY.name().equals(deliveryType)) {
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
        String qt = qualityType != null ? qualityType : QualityType.GOOD.getCode();
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

    /** 根据委外物料ID查询名称，用于展示回填（ID关联查询替代冗余name字段） */
    private String getMaterialNameById(Long materialId) {
        if (materialId == null) return "";
        OutsourceMaterial m = outsourceMaterialMapper.selectById(materialId);
        return m != null ? m.getMaterialName() : "";
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
            StockChangeType type = StockChangeType.fromCode(changeType);
            if (type == null) type = StockChangeType.DELIVERY_OUT; // 兜底
            inventoryStockService.changeStock(warehouseId, materialId, delta, type, orderCode, RelatedBillType.MATERIAL_IO, null, null, null);
        } else {
            updateStock(warehouseId, materialId, delta, qualityType, materialName, changeType, orderCode);
        }
    }

    /** 扣减进销存仓库库存（统一走 changeStock，自动写 inventory_stock_log） */
    private void deductInventoryStock(Long warehouseId, Long materialId, java.math.BigDecimal qty, String materialName, String qualityType, String deliveryCode) {
        inventoryStockService.changeStock(warehouseId, materialId, qty.negate(),
            StockChangeType.OUTSOURCE_DELIVERY_OUT, deliveryCode, RelatedBillType.OUTSOURCE_DELIVERY, null, null, null);
    }

    @Override
    public java.math.BigDecimal calcWeightedPrice(Long factoryId, Long materialId) {
        if (factoryId == null || materialId == null) return java.math.BigDecimal.ZERO;
        try {
            List<MaterialOrder> orders = materialOrderMapper.selectList(
                new LambdaQueryWrapper<MaterialOrder>().eq(MaterialOrder::getSupplierId, factoryId));
            java.math.BigDecimal totalAmount = java.math.BigDecimal.ZERO, totalQty = java.math.BigDecimal.ZERO;
            for (MaterialOrder o : orders) {
                LambdaQueryWrapper<MaterialOrderItem> itemW = new LambdaQueryWrapper<MaterialOrderItem>()
                    .eq(MaterialOrderItem::getOrderId, o.getId())
                    .eq(MaterialOrderItem::getMaterialId, materialId);
                List<MaterialOrderItem> mItems = materialOrderItemMapper.selectList(itemW);
                for (MaterialOrderItem it : mItems) {
                    java.math.BigDecimal qty = it.getOrderQuantity() != null ? it.getOrderQuantity() : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal price = it.getUnitPrice() != null ? it.getUnitPrice() : java.math.BigDecimal.ZERO;
                    totalAmount = totalAmount.add(qty.multiply(price));
                    totalQty = totalQty.add(qty);
                }
            }
            if (totalQty.compareTo(java.math.BigDecimal.ZERO) > 0)
                return totalAmount.divide(totalQty, 4, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) { log.warn("计算加权均价失败: {}", e.getMessage()); }
        return java.math.BigDecimal.ZERO;
    }
}
