package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.entity.OutsourceDelivery;
import com.beichen.erp.outsource.entity.OutsourceDeliveryItem;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceMaterialComponent;
import com.beichen.erp.outsource.entity.MaterialOrder;
import com.beichen.erp.outsource.entity.MaterialOrderItem;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.entity.WarehouseStockLog;
import com.beichen.erp.warehouse.common.WarehouseCategory;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.outsource.common.DeliveryType;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.outsource.common.MaterialOrderStatus;
import com.beichen.erp.outsource.common.DefectHandleType;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.outsource.common.OrderType;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.finance.service.PayableHelper;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryItemMapper;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockLogMapper;
import com.beichen.erp.outsource.mapper.MaterialOrderMapper;
import com.beichen.erp.outsource.mapper.MaterialOrderItemMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialComponentMapper;
import com.beichen.erp.outsource.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final WarehouseStockMapper stockMapper;
    private final WarehouseStockLogMapper stockLogMapper;
    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final OutsourceMaterialComponentMapper componentMapper;
    private final WarehouseStockMapper inventoryStockMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseStockService warehouseStockService;
    private final PayableHelper payableHelper;
    private final SupplierMapper supplierMapper;

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
        if (DeliveryType.TRANSFER.getCode().equals(delivery.getDeliveryType())) {
            if (delivery.getFromWarehouseId() == null) throw new BusinessException("来源仓库不能为空");
            if (delivery.getToWarehouseId() == null) throw new BusinessException("目标仓库不能为空");
        } else if (DeliveryType.DELIVERY.getCode().equals(delivery.getDeliveryType())) {
            if (delivery.getSupplierDirect() != null && delivery.getSupplierDirect() == 0 && delivery.getFromWarehouseId() == null)
                throw new BusinessException("非直发时来源仓库不能为空");
            if (delivery.getToWarehouseId() == null) throw new BusinessException("目标仓库不能为空");
        } else {
            if (delivery.getFromWarehouseId() == null) throw new BusinessException("来源仓库不能为空");
        }
        // 生成编码，草稿态存盘，不落库存
        delivery.setCode(generateCode());
        delivery.setStatus(DocStatus.DRAFT.getCode());
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
        if (!DocStatus.DRAFT.getCode().equals(delivery.getStatus())) {
            throw new BusinessException("仅草稿状态可以审核");
        }
        List<OutsourceDeliveryItem> items = getItems(id);
        // 审核通过：扣/增库存 + 写流水 + 同步已发数量
        applyDeliveryStock(delivery, items);
        OutsourceDelivery update = new OutsourceDelivery();
        update.setId(id);
        update.setStatus(DocStatus.AUDITED.getCode());
        deliveryMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unaudit(Long id) {
        OutsourceDelivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) throw new BusinessException("单据不存在");
        if (!DocStatus.AUDITED.getCode().equals(delivery.getStatus())) {
            throw new BusinessException("仅已审核状态可以反审核");
        }
        // 反审核：逆向库存 + 回滚已发数量，回到草稿
        List<OutsourceDeliveryItem> items = getItems(id);
        reverseDeliveryStock(delivery, items);
        OutsourceDelivery update = new OutsourceDelivery();
        update.setId(id);
        update.setStatus(DocStatus.DRAFT.getCode());
        deliveryMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditMaterialDelivery(Long id) {
        OutsourceDelivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) throw new BusinessException("单据不存在");
        if (!DocStatus.DRAFT.getCode().equals(delivery.getStatus())) {
            throw new BusinessException("仅草稿状态可以审核");
        }
        // 仅处理委外物料订单的收货/退不良单
        if (!DeliveryType.RECEIVE.getCode().equals(delivery.getDeliveryType())
                && !DeliveryType.DEFECT_RETURN.getCode().equals(delivery.getDeliveryType())) {
            throw new BusinessException("该单据非物料订单收货/退不良单，不可审核");
        }
        Long orderId = delivery.getSourceOrderId();
        if (orderId == null) throw new BusinessException("收货单缺少关联物料订单");
        MaterialOrder order = materialOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("关联物料订单不存在");
        List<OutsourceDeliveryItem> items = getItems(id);
        if (items.isEmpty()) throw new BusinessException("单据无明细，无法审核");

        // 1. 库存变动：收货入库(+)、退不良出库(-)，均作用于目标仓库
        boolean isReceive = DeliveryType.RECEIVE.getCode().equals(delivery.getDeliveryType());
        for (OutsourceDeliveryItem item : items) {
            if (item.getMaterialId() == null || item.getQuantity() == null) continue;
            String matName = getMaterialNameById(item.getMaterialId());
            if (delivery.getToWarehouseId() == null) throw new BusinessException("单据缺少目标仓库，无法审核");
            if (isReceive) {
                // 收货入库：目标仓库良品 +qty，写外协库存流水
                changeOutsourceStock(delivery.getToWarehouseId(), item.getMaterialId(), item.getQuantity(),
                        QualityType.GOOD.getCode(), StockChangeType.RECEIVE_IN.getCode(), delivery.getCode());
            } else {
                // 退不良：维修返还、折现退款均扣减目标仓库良品库存
                changeOutsourceStock(delivery.getToWarehouseId(), item.getMaterialId(), item.getQuantity().negate(),
                        QualityType.GOOD.getCode(), StockChangeType.DEFECT_OUT.getCode(), delivery.getCode());
            }
        }

        // 2. 委外单收货：扣减子物料库存（从供应商/加工厂委外仓扣，用量×收货数，可扣至负数=强制出库）
        if (isReceive && OrderType.OUTSOURCE.getLabel().equals(order.getOrderType())) {
            deductComponents(order, items, delivery);
        }

        // 3. 生成应付：收货为正应付；退不良-折现退款为负应付（冲减）；退不良-维修返还不涉及款项
        if (isReceive) {
            BigDecimal totalAmount = items.stream()
                    .map(it -> (it.getAmount() != null ? it.getAmount() : BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalAmount.compareTo(BigDecimal.ZERO) != 0) {
                payableHelper.createPayable(order.getSupplierId(), SourceBillType.OUTSOURCE_MATERIAL_DELIVERY.getCode(),
                        delivery.getCode(), delivery.getId(), totalAmount, delivery.getDeliveryDate(), "委外物料订单收货");
            }
        } else {
            // 退不良：仅折现退款生成负应付冲减供应商应付
            BigDecimal cashRefundAmount = items.stream()
                    .filter(it -> DefectHandleType.CASH_REFUND.getLabel().equals(it.getHandleType()))
                    .map(it -> (it.getAmount() != null ? it.getAmount() : BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (cashRefundAmount.compareTo(BigDecimal.ZERO) > 0) {
                payableHelper.createPayable(order.getSupplierId(), SourceBillType.OUTSOURCE_MATERIAL_DELIVERY.getCode(),
                        delivery.getCode(), delivery.getId(), cashRefundAmount.negate(), delivery.getDeliveryDate(), "委外物料订单退不良折现退款");
            }
        }

        // 4. 回写订单明细累计数量
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
        // 5. 重算订单状态
        recomputeMaterialOrderStatus(orderId);

        // 6. 单据置为已审核
        OutsourceDelivery up = new OutsourceDelivery();
        up.setId(id);
        up.setStatus(DocStatus.AUDITED.getCode());
        deliveryMapper.updateById(up);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unauditMaterialDelivery(Long id) {
        OutsourceDelivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) throw new BusinessException("单据不存在");
        if (!DocStatus.AUDITED.getCode().equals(delivery.getStatus())) {
            throw new BusinessException("仅已审核状态可以反审核");
        }
        if (!DeliveryType.RECEIVE.getCode().equals(delivery.getDeliveryType())
                && !DeliveryType.DEFECT_RETURN.getCode().equals(delivery.getDeliveryType())) {
            throw new BusinessException("该单据非物料订单收货/退不良单，不可反审核");
        }
        Long orderId = delivery.getSourceOrderId();
        if (orderId == null) throw new BusinessException("收货单缺少关联物料订单");
        List<OutsourceDeliveryItem> items = getItems(id);

        // 1. 逆向库存
        boolean isReceive = DeliveryType.RECEIVE.getCode().equals(delivery.getDeliveryType());
        for (OutsourceDeliveryItem item : items) {
            if (item.getMaterialId() == null || item.getQuantity() == null) continue;
            String matName = getMaterialNameById(item.getMaterialId());
            if (delivery.getToWarehouseId() == null) continue;
            if (isReceive) {
                // 反审核：回滚收货入库（目标仓库良品 -qty）
                changeOutsourceStock(delivery.getToWarehouseId(), item.getMaterialId(), item.getQuantity().negate(),
                        QualityType.GOOD.getCode(), "退审收货入库", delivery.getCode());
            } else {
                // 反审核：恢复退不良扣减的库存（+qty，维修返还、折现退款均恢复）
                changeOutsourceStock(delivery.getToWarehouseId(), item.getMaterialId(), item.getQuantity(),
                        QualityType.GOOD.getCode(), "退审退不良", delivery.getCode());
            }
        }

        // 2. 委外单收货反审核：恢复子物料库存（对称加回）
        if (isReceive) {
            MaterialOrder order = materialOrderMapper.selectById(orderId);
            if (order != null && OrderType.OUTSOURCE.getLabel().equals(order.getOrderType())) {
                restoreComponents(order, items, delivery);
            }
        }

        // 3. 冲回应付（已付款的阻止）+ 回退供应商应付余额
        // 收货：回滚正应付；退不良：仅折现退款回滚负应付（维修返还无应付）
        BigDecimal reverseAmount;
        if (isReceive) {
            reverseAmount = items.stream()
                    .map(it -> (it.getAmount() != null ? it.getAmount() : BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            reverseAmount = items.stream()
                    .filter(it -> DefectHandleType.CASH_REFUND.getLabel().equals(it.getHandleType()))
                    .map(it -> (it.getAmount() != null ? it.getAmount() : BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
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
        up.setStatus(DocStatus.DRAFT.getCode());
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

    /**
     * 根据累计收货/退不良数量重算物料订单状态。
     * 仅用于"已确认(RECEIVING)及以上"的订单：全部收满→FINISHED，否则保持 RECEIVING。
     * 注意：已确认的订单不会因单张收货单反审而退回 PENDING(未确认)，
     * 避免反审核一张收货单就把整张订单打回未确认态。
     */
    private void recomputeMaterialOrderStatus(Long orderId) {
        MaterialOrder order = materialOrderMapper.selectById(orderId);
        if (order == null) return;
        if (MaterialOrderStatus.CANCELLED.getCode().equals(order.getStatus())) return;
        // 非已确认流程的订单(如 PENDING)不在此维护状态，避免误退回未确认
        if (!MaterialOrderStatus.RECEIVING.getCode().equals(order.getStatus())
                && !MaterialOrderStatus.FINISHED.getCode().equals(order.getStatus())) {
            return;
        }
        List<MaterialOrderItem> items = materialOrderItemMapper.selectList(
                new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, orderId));
        boolean allDone = true;
        for (MaterialOrderItem it : items) {
            BigDecimal ord = it.getOrderQuantity() != null ? it.getOrderQuantity() : BigDecimal.ZERO;
            BigDecimal rec = it.getReceivedQuantity() != null ? it.getReceivedQuantity() : BigDecimal.ZERO;
            BigDecimal def = it.getDefectReturnedQty() != null ? it.getDefectReturnedQty() : BigDecimal.ZERO;
            // 有效交货 = 收货数 - 退不良数，必须 >= 下单数才算交齐
            if (rec.subtract(def).compareTo(ord) < 0) allDone = false;
        }
        if (allDone && !items.isEmpty()) {
            order.setStatus(MaterialOrderStatus.FINISHED.getCode());
            order.setFinishTime(LocalDateTime.now());
        } else {
            // 关键：保持 RECEIVING，不退回 PENDING
            order.setStatus(MaterialOrderStatus.RECEIVING.getCode());
            order.setFinishTime(null);
        }
        materialOrderMapper.updateById(order);
    }

    /** 变更外协库存（outsource_warehouse_stock）并写流水日志（outsource_stock_log），供物料订单收货/退不良使用 */
    private void changeOutsourceStock(Long warehouseId, Long materialId, BigDecimal delta, String qualityType,
                                      String changeType, String relatedCode) {
        // 收敛为统一入口：委托 updateStock，避免两套重复实现导致库存写入不一致
        if (warehouseId == null || materialId == null) return;
        String matName = getMaterialNameById(materialId);
        if (qualityType == null) qualityType = QualityType.GOOD.getCode();
        updateStock(warehouseId, materialId, delta, qualityType, matName, changeType, relatedCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        OutsourceDelivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) {
            throw new BusinessException("单据不存在");
        }
        if (DocStatus.CANCELLED.getCode().equals(delivery.getStatus())) {
            throw new BusinessException("单据已取消，不可重复取消");
        }
        // 已审核单据作废前先逆向库存（统一入口 reverseDeliveryStock，内含库存冲减与发料已发数量回滚，禁止重复冲减）
        if (DocStatus.AUDITED.getCode().equals(delivery.getStatus())) {
            reverseDeliveryStock(delivery, getItems(id));
        }
        // 更新状态
        OutsourceDelivery update = new OutsourceDelivery();
        update.setId(id);
        update.setStatus(DocStatus.CANCELLED.getCode());
        deliveryMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items) {
        OutsourceDelivery old = deliveryMapper.selectById(delivery.getId());
        if (old == null) throw new BusinessException("单据不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑，已审核单据请先反审核");
        }

        // 草稿态编辑不触碰库存，仅删旧明细、更新主表、插新明细
        // 1. 删旧明细
        itemMapper.delete(new LambdaQueryWrapper<OutsourceDeliveryItem>().eq(OutsourceDeliveryItem::getDeliveryId, delivery.getId()));
        // 2. 更新主表（保持草稿状态）
        delivery.setCode(old.getCode());
        delivery.setStatus(DocStatus.DRAFT.getCode());
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
            if (DeliveryType.DELIVERY.getCode().equals(delivery.getDeliveryType())) {
                // 扣减来源仓库（我方仓或委外仓）
                if (delivery.getFromWarehouseId() != null) {
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate(), getMaterialNameById(item.getMaterialId()), item.getQualityType(), StockChangeType.DELIVERY_OUT.getCode(), delivery.getCode());
                }
                // 增加目标仓库（委外仓）
                if (delivery.getToWarehouseId() != null)
                    updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), StockChangeType.DELIVERY_IN.getCode(), delivery.getCode());
            } else if (DeliveryType.TRANSFER.getCode().equals(delivery.getDeliveryType())) {
                // 来源仓库-，目标仓库+
                if (delivery.getFromWarehouseId() != null)
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate(), getMaterialNameById(item.getMaterialId()), item.getQualityType(), StockChangeType.TRANSFER_OUT.getCode(), delivery.getCode());
                if (delivery.getToWarehouseId() != null)
                    adjustSourceStock(delivery.getToWarehouseId(), item.getMaterialId(), qty, getMaterialNameById(item.getMaterialId()), item.getQualityType(), StockChangeType.TRANSFER_IN.getCode(), delivery.getCode());
            } else {
                String type = delivery.getDeliveryType();
                if (delivery.getFromWarehouseId() != null)
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate(), getMaterialNameById(item.getMaterialId()), item.getQualityType(), type != null ? type : "退料", delivery.getCode());
            }
        }
    }

    /**
     * 反审核/取消：逆向库存 + 发料时回滚已发数量
     */
    private void reverseDeliveryStock(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items) {
        for (OutsourceDeliveryItem item : items) {
            BigDecimal qty = item.getQuantity();
            if (DeliveryType.DELIVERY.getCode().equals(delivery.getDeliveryType())) {
                // 恢复来源仓库库存（与正向 applyDeliveryStock->adjustSourceStock 对称，自动区分我方仓/委外仓）
                if (delivery.getFromWarehouseId() != null && item.getMaterialId() != null) {
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty,
                            getMaterialNameById(item.getMaterialId()), item.getQualityType(),
                            StockChangeType.OUTSOURCE_CANCEL_DELIVERY.getCode(), delivery.getCode());
                }
                // 扣回委外仓库
                if (delivery.getToWarehouseId() != null)
                    updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty.negate(), item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), StockChangeType.CANCEL_DELIVERY.getCode(), delivery.getCode());
            } else if (DeliveryType.TRANSFER.getCode().equals(delivery.getDeliveryType())) {
                // 逆向：来源仓库+，目标仓库-
                if (delivery.getFromWarehouseId() != null)
                    updateStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), StockChangeType.CANCEL_TRANSFER_OUT.getCode(), delivery.getCode());
                if (delivery.getToWarehouseId() != null)
                    updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty.negate(), item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), StockChangeType.CANCEL_TRANSFER_IN.getCode(), delivery.getCode());
            } else if (DeliveryType.RECEIVE.getCode().equals(delivery.getDeliveryType())) {
                // 收料入库，取消则出库（方向同 unauditMaterialDelivery：目标仓库 -qty）
                if (delivery.getToWarehouseId() != null)
                    updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty.negate(), item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), StockChangeType.CANCEL_RECEIVE_IN.getCode(), delivery.getCode());
            } else if (DeliveryType.DEFECT_RETURN.getCode().equals(delivery.getDeliveryType())) {
                // 退不良出库，取消则回库（方向同 unauditMaterialDelivery：目标仓库 +qty）
                if (delivery.getToWarehouseId() != null)
                    updateStock(delivery.getToWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), StockChangeType.CANCEL_DEFECT_OUT.getCode(), delivery.getCode());
            } else {
                // 其他退料类（以 fromWarehouseId 出库方向逆向）
                if (delivery.getFromWarehouseId() != null)
                    updateStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty, item.getQualityType(),
                            getMaterialNameById(item.getMaterialId()), StockChangeType.RETURN_IN.getCode(), delivery.getCode());
            }
        }
        // 已审核的收料/退不良单作废或反审核时，冲销对应应付（避免应付悬空；现金退款退不良单审核未生成应付，reversePayable 内部安全拦截）
        if (DocStatus.AUDITED.getCode().equals(delivery.getStatus())
                && (DeliveryType.RECEIVE.getCode().equals(delivery.getDeliveryType())
                    || DeliveryType.DEFECT_RETURN.getCode().equals(delivery.getDeliveryType()))) {
            payableHelper.reversePayable(delivery.getId());
        }
    }

    /**
     * 计算库存变动量：
     * 发料 → 库存增加(+)
     * 收料/退料 → 库存减少(-)
     */
    private BigDecimal getStockDelta(String deliveryType, BigDecimal quantity) {
        if (DeliveryType.DELIVERY.getCode().equals(deliveryType)) {
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
        LambdaQueryWrapper<WarehouseStock> w = new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId)
                .eq(WarehouseStock::getMaterialId, materialId)
                .eq(WarehouseStock::getQualityType, qt);
        WarehouseStock stock = stockMapper.selectOne(w);
        BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
        BigDecimal after;
        if (stock == null) {
            stock = new WarehouseStock();
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
        WarehouseStockLog logEntry = new WarehouseStockLog();
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
     * 委外单收货审核：扣减子物料库存。
     * 从供应商（加工厂）委外仓扣减，扣减量 = 组件用量 × 收货数量；
     * 库存不足时直接扣至负数（对应"强制出库"，缺料提示已在收货时拦截）。
     */
    private void deductComponents(MaterialOrder order, List<OutsourceDeliveryItem> items, OutsourceDelivery delivery) {
        // 供应商委外仓：子物料从该仓扣减（与收货前缺料校验口径一致）
        List<Warehouse> supWhs = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getFactoryId, order.getSupplierId()));
        Long compWhId = supWhs.isEmpty() ? null : supWhs.get(0).getId();
        if (compWhId == null) return;
        for (OutsourceDeliveryItem item : items) {
            if (item.getItemId() == null || item.getQuantity() == null) continue;
            MaterialOrderItem oi = materialOrderItemMapper.selectById(item.getItemId());
            if (oi == null || oi.getMaterialId() == null) continue;
            List<OutsourceMaterialComponent> comps = componentMapper.selectList(
                    new LambdaQueryWrapper<OutsourceMaterialComponent>()
                            .eq(OutsourceMaterialComponent::getParentMaterialId, oi.getMaterialId()));
            if (comps == null || comps.isEmpty()) continue;
            for (OutsourceMaterialComponent c : comps) {
                if (c.getChildMaterialId() == null) continue;
                BigDecimal demand = (c.getQuantity() != null ? c.getQuantity() : BigDecimal.ONE).multiply(item.getQuantity());
                String childName = getMaterialNameById(c.getChildMaterialId());
                // 负向扣减，可扣至负数
                updateStock(compWhId, c.getChildMaterialId(), demand.negate(),
                        QualityType.GOOD.getCode(), childName, "委外收货扣子物料", delivery.getCode());
            }
        }
    }

    /**
     * 委外单收货反审核：对称恢复子物料库存（加回扣减量）。
     */
    private void restoreComponents(MaterialOrder order, List<OutsourceDeliveryItem> items, OutsourceDelivery delivery) {
        List<Warehouse> supWhs = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getFactoryId, order.getSupplierId()));
        Long compWhId = supWhs.isEmpty() ? null : supWhs.get(0).getId();
        if (compWhId == null) return;
        for (OutsourceDeliveryItem item : items) {
            if (item.getItemId() == null || item.getQuantity() == null) continue;
            MaterialOrderItem oi = materialOrderItemMapper.selectById(item.getItemId());
            if (oi == null || oi.getMaterialId() == null) continue;
            List<OutsourceMaterialComponent> comps = componentMapper.selectList(
                    new LambdaQueryWrapper<OutsourceMaterialComponent>()
                            .eq(OutsourceMaterialComponent::getParentMaterialId, oi.getMaterialId()));
            if (comps == null || comps.isEmpty()) continue;
            for (OutsourceMaterialComponent c : comps) {
                if (c.getChildMaterialId() == null) continue;
                BigDecimal demand = (c.getQuantity() != null ? c.getQuantity() : BigDecimal.ONE).multiply(item.getQuantity());
                String childName = getMaterialNameById(c.getChildMaterialId());
                updateStock(compWhId, c.getChildMaterialId(), demand,
                        QualityType.GOOD.getCode(), childName, "退审恢复子物料", delivery.getCode());
            }
        }
    }

    /**
     * 生成编码：DEL-YYYYMMDDNNN
     */
    private String generateCode() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String likePattern = BillPrefix.OUTSOURCE_DELIVERY + dateStr;

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
        return BillPrefix.OUTSOURCE_DELIVERY + dateStr + String.format("%03d", seq);
    }

    /** 调整库存：自动判断仓库类型（我方仓用 changeStock，委外仓用 updateStock） */
    private void adjustSourceStock(Long warehouseId, Long materialId, BigDecimal delta, String materialName,
                                    String qualityType, String changeType, String orderCode) {
        // 按仓库类别区分：INVENTORY(自有仓)走进销存 changeStock，OUTSOURCE(委外仓)走委外库存 updateStock
        Warehouse wh = warehouseId != null ? warehouseMapper.selectById(warehouseId) : null;
        if (wh != null && WarehouseCategory.INVENTORY.getCode().equals(wh.getWarehouseCategory())) {
            StockChangeType type = StockChangeType.fromCode(changeType);
            if (type == null) type = StockChangeType.DELIVERY_OUT; // 兜底
            warehouseStockService.changeStock(warehouseId, materialId, delta, type, orderCode, RelatedBillType.MATERIAL_IO, null, null, null);
        } else {
            updateStock(warehouseId, materialId, delta, qualityType, materialName, changeType, orderCode);
        }
    }

    /** 扣减进销存仓库库存（统一走 changeStock，自动写 inventory_stock_log） */
    private void deductInventoryStock(Long warehouseId, Long materialId, java.math.BigDecimal qty, String materialName, String qualityType, String deliveryCode) {
        warehouseStockService.changeStock(warehouseId, materialId, qty.negate(),
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
