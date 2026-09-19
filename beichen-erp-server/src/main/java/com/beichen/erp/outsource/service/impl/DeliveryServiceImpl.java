package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.entity.OutsourceDelivery;
import com.beichen.erp.outsource.entity.OutsourceDeliveryItem;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceMaterialComponent;
import com.beichen.erp.outsource.entity.MaterialOrder;
import com.beichen.erp.outsource.entity.MaterialOrderItem;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.warehouse.common.WarehouseCategory;
import com.beichen.erp.warehouse.common.WarehouseType;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryServiceImpl implements DeliveryService {

    private final OutsourceDeliveryMapper deliveryMapper;
    private final OutsourceDeliveryItemMapper itemMapper;
    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final OutsourceMaterialComponentMapper componentMapper;
    private final WarehouseStockMapper inventoryStockMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseStockService warehouseStockService;
    private final PayableHelper payableHelper;
    private final SupplierMapper supplierMapper;
    private final com.beichen.erp.warehouse.service.CostService costService;

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
        // ==== 2026-09-16 流程重构：手工单据只允许「发料 / 调拨」 ====
        // 退料(RETURN)/收料(RECEIVE)/退不良(DEFECT_RETURN) 不再允许手工新建：
        //   收料/退不良由物料订单流程自动生成；退料整体下线（历史单据仍可查询/反审核）
        if (!MANUAL_TYPES.contains(delivery.getDeliveryType())) {
            throw new BusinessException("该收发类型已不再支持手工新建（" + delivery.getDeliveryType() + "），手工单据仅支持 发料 / 调拨");
        }
        // 供应商直发已下线：发料一律从我方物料仓发出（与"发料=我方仓→供应商委外仓"的定义一致）
        delivery.setSupplierDirect(0);
        validateWarehouses(delivery);
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
        // 2026-09-17 修复 D1：「收料 / 退不良」单必须走物料订单专用审核（库存±、应付、成本、订单已收数量全套）。
        // 否则会落到 applyDeliveryStock 的 else 分支（只认 fromWarehouseId，而收料单该字段为空）→ 静默不落库存、
        // 不生成应付，却把状态置为已审核；而同一列表入口的「反审核」会扣库存/冲应付 ⇒ 正反不对称、账实不符。
        String auditType = delivery.getDeliveryType();
        if (DeliveryType.RECEIVE.getCode().equals(auditType) || DeliveryType.DEFECT_RETURN.getCode().equals(auditType)) {
            auditMaterialDelivery(id);
            return;
        }
        // 2026-09-16：原子抢占 DRAFT→AUDITED（与盘点单/移仓单统一），避免双击/并发重复落库存
        if (!DocStatusGuard.claim(deliveryMapper, OutsourceDelivery::getId, id,
                OutsourceDelivery::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("仅草稿状态可以审核");
        }
        List<OutsourceDeliveryItem> items = getItems(id);
        // 审核通过：扣/增库存 + 写流水 + 同步已发数量
        applyDeliveryStock(delivery, items);
        // 移动加权成本：发料到委外仓按明细单价加权（退料/移仓不影响成本）
        if (DeliveryType.DELIVERY.getCode().equals(delivery.getDeliveryType())) {
            for (OutsourceDeliveryItem item : items) {
                costService.applyMaterial(item.getMaterialId(), item.getQuantity(), item.getUnitPrice(),
                        StockChangeType.DELIVERY_IN.getCode(), delivery.getId(), delivery.getCode());
            }
        }
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
        // F7-44（2026-09-19）：与 audit 对称分流 —— 「收料/退不良」单必须走物料订单专用反审核
        // （逆向库存 + 冲回应付 + **回滚订单已收/退不良数量** + BOM 子料回补 + 成本冲销 全套）。
        // 原先通用反审核**不分流** ⇒ 只做「库存逆向 + 应付冲销」，其余三项不回滚 ⇒ 账实不符
        //（即 D1「审核侧已分流、反审核侧漏改」的另一半）。
        String unAuditType = delivery.getDeliveryType();
        if (DeliveryType.RECEIVE.getCode().equals(unAuditType) || DeliveryType.DEFECT_RETURN.getCode().equals(unAuditType)) {
            unauditMaterialDelivery(id);
            return;
        }
        // F7-45（2026-09-19）：原子抢占 AUDITED→DRAFT（原"先查后改"非原子，并发/双击会重复逆向库存与应付）
        if (!DocStatusGuard.claim(deliveryMapper, OutsourceDelivery::getId, id,
                OutsourceDelivery::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("仅已审核状态可以反审核");
        }
        // 反审核：逆向库存 + 回滚已发数量，回到草稿
        List<OutsourceDeliveryItem> items = getItems(id);
        reverseDeliveryStock(delivery, items);
        // 成本冲销：发料单删除入库批次并反加权
        if (DeliveryType.DELIVERY.getCode().equals(delivery.getDeliveryType())) {
            costService.reverseByBill(StockChangeType.DELIVERY_IN.getCode(), id);
        }
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
        // 2026-09-17（D1）：与通用审核统一改为原子抢占，避免双击/并发重复落库存 + 重复生成应付
        if (!DocStatusGuard.claim(deliveryMapper, OutsourceDelivery::getId, id,
                OutsourceDelivery::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
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
        if (MaterialOrderStatus.CANCELLED.getCode().equals(order.getStatus())) {
            throw new BusinessException("关联物料订单已作废，不可审核");
        }
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
                        QualityType.GOOD.getCode(), StockChangeType.RECEIVE_IN.getCode(), delivery.getCode(), delivery.getId());
            } else {
                // 退不良：维修返还、折现退款均扣减目标仓库良品库存
                changeOutsourceStock(delivery.getToWarehouseId(), item.getMaterialId(), item.getQuantity().negate(),
                        QualityType.GOOD.getCode(), StockChangeType.DEFECT_OUT.getCode(), delivery.getCode(), delivery.getId());
            }
        }

        // 2. 委外单收货：扣减子物料库存（从供应商/加工厂委外仓扣，用量×收货数，可扣至负数=强制出库）
        if (isReceive && OrderType.OUTSOURCE.getCode().equals(order.getOrderType())) {
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
                    .filter(it -> DefectHandleType.CASH_REFUND.getCode().equals(it.getHandleType()))
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
            // F7-49（2026-09-19）：改为 **SQL 原子累加**（原为 Java 侧"读-改-写"：同一物料订单明细行被两张
            // 收料单并发审核时互相覆盖，数量少记一次且不报错）。数字取自 BigDecimal.toPlainString()。
            String qtySql = item.getQuantity().toPlainString();
            materialOrderItemMapper.update(null, new LambdaUpdateWrapper<MaterialOrderItem>()
                    .eq(MaterialOrderItem::getId, oi.getId())
                    .setSql(isReceive
                            ? "received_quantity = IFNULL(received_quantity, 0) + (" + qtySql + ")"
                            : "defect_returned_qty = IFNULL(defect_returned_qty, 0) + (" + qtySql + ")"));
        }
        // 4.1 移动加权成本：收货入库按明细单价加权（退不良出库不影响成本）
        if (isReceive) {
            for (OutsourceDeliveryItem item : items) {
                costService.applyMaterial(item.getMaterialId(), item.getQuantity(), item.getUnitPrice(),
                        StockChangeType.RECEIVE_IN.getCode(), delivery.getId(), delivery.getCode());
            }
        }
        // 5. 单据置为已审核
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
        if (!DeliveryType.RECEIVE.getCode().equals(delivery.getDeliveryType())
                && !DeliveryType.DEFECT_RETURN.getCode().equals(delivery.getDeliveryType())) {
            throw new BusinessException("该单据非物料订单收货/退不良单，不可反审核");
        }
        // F7-45（2026-09-19）：原子抢占 AUDITED→DRAFT（原"先查后改"非原子）。
        // 本方法会逆向库存 + 冲回应付 + 回滚订单已收/退不良数量 + 回补 BOM 子料 + 冲销成本，
        // 并发/双击重复执行会重复冲销 ⇒ 与正向 auditMaterialDelivery 的 CAS 对齐。
        if (!DocStatusGuard.claim(deliveryMapper, OutsourceDelivery::getId, id,
                OutsourceDelivery::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("仅已审核状态可以反审核");
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
                        QualityType.GOOD.getCode(), StockChangeType.CANCEL_RECEIVE_IN.getCode(), delivery.getCode(), delivery.getId());
            } else {
                // 反审核：恢复退不良扣减的库存（+qty，维修返还、折现退款均恢复）
                changeOutsourceStock(delivery.getToWarehouseId(), item.getMaterialId(), item.getQuantity(),
                        QualityType.GOOD.getCode(), StockChangeType.CANCEL_DEFECT_OUT.getCode(), delivery.getCode(), delivery.getId());
            }
        }

        // 2. 委外单收货反审核：恢复子物料库存（对称加回）
        if (isReceive) {
            MaterialOrder order = materialOrderMapper.selectById(orderId);
            if (order != null && OrderType.OUTSOURCE.getCode().equals(order.getOrderType())) {
                restoreComponents(order, items, delivery);
            }
            // 成本冲销：删除本单收货批次并反加权
            costService.reverseByBill(StockChangeType.RECEIVE_IN.getCode(), id);
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
                    .filter(it -> DefectHandleType.CASH_REFUND.getCode().equals(it.getHandleType()))
                    .map(it -> (it.getAmount() != null ? it.getAmount() : BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        payableHelper.reversePayable(delivery.getId(), SourceBillType.OUTSOURCE_MATERIAL_DELIVERY.getCode());

        // 3. 回滚订单明细累计数量
        for (OutsourceDeliveryItem item : items) {
            if (item.getItemId() == null || item.getQuantity() == null) continue;
            MaterialOrderItem oi = materialOrderItemMapper.selectById(item.getItemId());
            if (oi == null) continue;
            // F7-49（2026-09-19）：SQL 原子扣减；GREATEST(...,0) 保留原 safeSubtract 的"结果不为负"语义
            String qtySql = item.getQuantity().toPlainString();
            materialOrderItemMapper.update(null, new LambdaUpdateWrapper<MaterialOrderItem>()
                    .eq(MaterialOrderItem::getId, oi.getId())
                    .setSql(isReceive
                            ? "received_quantity = GREATEST(IFNULL(received_quantity, 0) - (" + qtySql + "), 0)"
                            : "defect_returned_qty = GREATEST(IFNULL(defect_returned_qty, 0) - (" + qtySql + "), 0)"));
        }
        // 4. 单据回到草稿
        OutsourceDelivery up = new OutsourceDelivery();
        up.setId(id);
        up.setStatus(DocStatus.DRAFT.getCode());
        deliveryMapper.updateById(up);
    }

    /**
     * 变更外协库存并写流水日志，供物料订单收货/退不良使用。
     * <p>收敛为统一入口：委托 {@code updateStock} → {@link WarehouseStockService}（F1/F2：不再直写，且流水带单据号与单据ID）。</p>
     *
     * @param relatedBillId 关联单据ID（本收货/退不良记录ID）
     */
    private void changeOutsourceStock(Long warehouseId, Long materialId, BigDecimal delta, String qualityType,
                                      String changeType, String relatedCode, Long relatedBillId) {
        if (warehouseId == null || materialId == null) return;
        String matName = getMaterialNameById(materialId);
        if (qualityType == null) qualityType = QualityType.GOOD.getCode();
        updateStock(warehouseId, materialId, delta, qualityType, matName, changeType, relatedCode, relatedBillId);
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
        // F7-50（2026-09-19）：原子抢占 DRAFT→CANCELLED（原"先查后改"可与 audit 并发互覆，见移仓单同款注释）
        if (!DocStatusGuard.claim(deliveryMapper, OutsourceDelivery::getId, id,
                OutsourceDelivery::getStatus, DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废，已审核单据请先反审核");
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

    /** 手工可新建的单据类型（2026-09-16 流程重构）：只有 发料 / 调拨 */
    private static final List<String> MANUAL_TYPES = List.of(
            DeliveryType.DELIVERY.getCode(), DeliveryType.TRANSFER.getCode());

    /**
     * 新流程的仓库规则校验（**后端强校验**，不依赖前端下拉过滤）：
     * <ul>
     *   <li>发料：发出仓 = 我方物料仓（自有+辅料仓）；目标仓 = 所选工厂的委外仓</li>
     *   <li>调拨：两端都必须是物料相关仓库（我方物料仓 / 委外仓）、不能同仓；
     *       「我方物料仓 → 委外仓」直接拦掉（那是发料）。允许 委外仓↔委外仓、委外仓→我方仓、我方仓↔我方仓</li>
     * </ul>
     */
    private void validateWarehouses(OutsourceDelivery delivery) {
        boolean isDelivery = DeliveryType.DELIVERY.getCode().equals(delivery.getDeliveryType());
        if (delivery.getFromWarehouseId() == null) throw new BusinessException(isDelivery ? "发出仓库不能为空" : "来源仓库不能为空");
        if (delivery.getToWarehouseId() == null) throw new BusinessException(isDelivery ? "目标委外仓不能为空" : "目标仓库不能为空");
        Warehouse from = warehouseMapper.selectById(delivery.getFromWarehouseId());
        Warehouse to = warehouseMapper.selectById(delivery.getToWarehouseId());
        if (from == null) throw new BusinessException("发出/来源仓库不存在");
        if (to == null) throw new BusinessException("目标仓库不存在");
        if (isDelivery) {
            if (delivery.getFactoryId() == null) throw new BusinessException("收货工厂不能为空");
            if (!isMaterialOwnWarehouse(from)) throw new BusinessException("发料的发出仓库只能是我方物料仓");
            if (!isOutsourceWarehouse(to)) throw new BusinessException("发料的目标仓库必须是委外仓");
            if (!delivery.getFactoryId().equals(to.getFactoryId())) throw new BusinessException("目标委外仓不属于所选工厂");
        } else {
            if (!isMaterialWarehouse(from) || !isMaterialWarehouse(to))
                throw new BusinessException("调拨的两端必须是物料相关仓库（我方物料仓 / 委外仓）");
            if (from.getId().equals(to.getId())) throw new BusinessException("来源仓库与目标仓库不能相同");
            if (isMaterialOwnWarehouse(from) && isOutsourceWarehouse(to))
                throw new BusinessException("我方物料仓 → 委外仓 请使用「发料」单据");
            // 调拨不再手选工厂：按委外仓自动带出（两端皆我方仓时保持 null）
            if (isOutsourceWarehouse(from)) delivery.setFactoryId(from.getFactoryId());
            else if (isOutsourceWarehouse(to)) delivery.setFactoryId(to.getFactoryId());
        }
    }

    /** 我方物料仓 = 自有仓 + 辅料仓（物料专用仓） */
    private boolean isMaterialOwnWarehouse(Warehouse w) {
        return w != null
                && WarehouseCategory.INVENTORY.getCode().equals(w.getWarehouseCategory())
                && WarehouseType.AUXILIARY.getCode().equals(w.getWarehouseType());
    }

    /** 委外仓（供应商/工厂处） */
    private boolean isOutsourceWarehouse(Warehouse w) {
        return w != null && WarehouseCategory.OUTSOURCE.getCode().equals(w.getWarehouseCategory());
    }

    /** 物料相关仓库 = 我方物料仓 或 委外仓（收发单只允许在这两类仓库之间搬运） */
    private boolean isMaterialWarehouse(Warehouse w) {
        return isMaterialOwnWarehouse(w) || isOutsourceWarehouse(w);
    }

    // ==================== 私有方法 ====================

    /**
     * 审核通过：扣/增库存 + 写流水。
     * <p>抽取自原 create，使库存动作统一在审核时发生。</p>
     * <p>2026-09-16 口径：发料 = 我方物料仓 − / 该厂委外仓 +；调拨 = 来源仓 − / 目标仓 +；
     * 其余历史类型（退料等）保留逆向语义，仅供历史单据反审核使用。</p>
     */
    private void applyDeliveryStock(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items) {
        // 注意：字段是 Integer(0/1)，不能用 Boolean.TRUE.equals(...)（恒为 false）
        boolean allowNegative = delivery.getAllowNegative() != null && delivery.getAllowNegative() == 1;
        for (OutsourceDeliveryItem item : items) {
            BigDecimal qty = item.getQuantity();
            if (DeliveryType.DELIVERY.getCode().equals(delivery.getDeliveryType())) {
                // 发料：扣减我方物料仓（严格校验，不允许负库存）
                if (delivery.getFromWarehouseId() != null) {
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate(), getMaterialNameById(item.getMaterialId()), item.getQualityType(), StockChangeType.DELIVERY_OUT.getCode(), delivery.getCode(), false, delivery.getId());
                }
                // 增加目标委外仓（F2：原先走直写 updateStock，现统一走库存服务）
                if (delivery.getToWarehouseId() != null)
                    adjustSourceStock(delivery.getToWarehouseId(), item.getMaterialId(), qty,
                            getMaterialNameById(item.getMaterialId()), item.getQualityType(),
                            StockChangeType.DELIVERY_IN.getCode(), delivery.getCode(), true, delivery.getId());
            } else if (DeliveryType.TRANSFER.getCode().equals(delivery.getDeliveryType())) {
                // 调拨：来源仓库-，目标仓库+（是否允许扣成负数由单据上的"强制出库"开关决定）
                if (delivery.getFromWarehouseId() != null)
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate(), getMaterialNameById(item.getMaterialId()), item.getQualityType(), StockChangeType.TRANSFER_OUT.getCode(), delivery.getCode(), allowNegative, delivery.getId());
                if (delivery.getToWarehouseId() != null)
                    adjustSourceStock(delivery.getToWarehouseId(), item.getMaterialId(), qty, getMaterialNameById(item.getMaterialId()), item.getQualityType(), StockChangeType.TRANSFER_IN.getCode(), delivery.getCode(), false, delivery.getId());
            } else if (DeliveryType.RECEIVE.getCode().equals(delivery.getDeliveryType())
                    || DeliveryType.DEFECT_RETURN.getCode().equals(delivery.getDeliveryType())) {
                // 2026-09-17 防御（D1）：收料/退不良的库存动作必须走 auditMaterialDelivery（作用于 toWarehouseId 并联动应付），
                // 本方法无法正确表达，故显式报错而非静默跳过（防以后有人再从这里绕开导致"状态已审核但无库存/台账"）
                throw new BusinessException("收料/退不良单请在「物料收货」页审核（不可走通用收发单审核）");
            } else {
                // 历史类型（退料等）：仅历史单据反审核/重审会走到这里，新建入口已关闭
                String type = delivery.getDeliveryType();
                if (delivery.getFromWarehouseId() != null)
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty.negate(), getMaterialNameById(item.getMaterialId()), item.getQualityType(), type != null ? type : "RETURN", delivery.getCode(), allowNegative, delivery.getId());
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
                            StockChangeType.OUTSOURCE_CANCEL_DELIVERY.getCode(), delivery.getCode(), false, delivery.getId());
                }
                // 扣回委外仓库
                if (delivery.getToWarehouseId() != null)
                    // C5 口径（2026-09-12）：发料反审核两个仓统一用 OUTSOURCE_CANCEL_DELIVERY
                    //（原先目标仓另用 CANCEL_DELIVERY，同 label 两个 code，纯属口径不统一）
                    // F2：对称回滚用 allowNegative=true（否则"强制出库"形成的负库存会导致反审核失败）
                    adjustSourceStock(delivery.getToWarehouseId(), item.getMaterialId(), qty.negate(),
                            getMaterialNameById(item.getMaterialId()), item.getQualityType(),
                            StockChangeType.OUTSOURCE_CANCEL_DELIVERY.getCode(), delivery.getCode(), true, delivery.getId());
            } else if (DeliveryType.TRANSFER.getCode().equals(delivery.getDeliveryType())) {
                // 逆向：来源仓库+，目标仓库-
                // 2026-09-16：改走 adjustSourceStock（我方仓腿也能写标准流水+related_bill_type），
                // 且对称回滚一律 allowNegative=true —— 否则"强制出库"产生的负库存会导致反审核失败
                if (delivery.getFromWarehouseId() != null)
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty, getMaterialNameById(item.getMaterialId()), item.getQualityType(), StockChangeType.CANCEL_TRANSFER_OUT.getCode(), delivery.getCode(), true, delivery.getId());
                if (delivery.getToWarehouseId() != null)
                    adjustSourceStock(delivery.getToWarehouseId(), item.getMaterialId(), qty.negate(), getMaterialNameById(item.getMaterialId()), item.getQualityType(), StockChangeType.CANCEL_TRANSFER_IN.getCode(), delivery.getCode(), true, delivery.getId());
            } else if (DeliveryType.RECEIVE.getCode().equals(delivery.getDeliveryType())) {
                // 收料入库，取消则出库（方向同 unauditMaterialDelivery：目标仓库 -qty）
                if (delivery.getToWarehouseId() != null)
                    adjustSourceStock(delivery.getToWarehouseId(), item.getMaterialId(), qty.negate(),
                            getMaterialNameById(item.getMaterialId()), item.getQualityType(),
                            StockChangeType.CANCEL_RECEIVE_IN.getCode(), delivery.getCode(), true, delivery.getId());
            } else if (DeliveryType.DEFECT_RETURN.getCode().equals(delivery.getDeliveryType())) {
                // 退不良出库，取消则回库（方向同 unauditMaterialDelivery：目标仓库 +qty）
                if (delivery.getToWarehouseId() != null)
                    adjustSourceStock(delivery.getToWarehouseId(), item.getMaterialId(), qty,
                            getMaterialNameById(item.getMaterialId()), item.getQualityType(),
                            StockChangeType.CANCEL_DEFECT_OUT.getCode(), delivery.getCode(), true, delivery.getId());
            } else {
                // 历史退料类（以 fromWarehouseId 出库方向逆向）
                if (delivery.getFromWarehouseId() != null)
                    adjustSourceStock(delivery.getFromWarehouseId(), item.getMaterialId(), qty, getMaterialNameById(item.getMaterialId()), item.getQualityType(), StockChangeType.RETURN_IN.getCode(), delivery.getCode(), true, delivery.getId());
            }
        }
        // 已审核的收料/退不良单作废或反审核时，冲销对应应付（避免应付悬空；现金退款退不良单审核未生成应付，reversePayable 内部安全拦截）
        if (DocStatus.AUDITED.getCode().equals(delivery.getStatus())
                && (DeliveryType.RECEIVE.getCode().equals(delivery.getDeliveryType())
                    || DeliveryType.DEFECT_RETURN.getCode().equals(delivery.getDeliveryType()))) {
            payableHelper.reversePayable(delivery.getId(), SourceBillType.OUTSOURCE_MATERIAL_DELIVERY.getCode());
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
     * 更新仓库库存并写入流水日志（物料侧"允许负数"口径的兼容入口）。
     * <p><b>F2（2026-09-17 修复）</b>：本方法原先自行 selectOne/insert/updateById + 手写流水，
     * 是**绕过统一库存服务的直写通道**（架构债 A3），且不写 related_bill_no/related_bill_id（流水不可回溯）。
     * 现直接委托 {@link WarehouseStockService#changeMaterialStockAllowNegative}：
     * 口径与字段（quality_type / before / after / related_bill_*）与全站物料流水完全一致，
     * 负库存告警也由服务层统一打（不再各处重复）。</p>
     */
    private void updateStock(Long warehouseId, Long materialId, BigDecimal delta, String qualityType,
                             String materialName, String changeType, String deliveryCode) {
        updateStock(warehouseId, materialId, delta, qualityType, materialName, changeType, deliveryCode, null);
    }

    /** 同上，多带一个关联单据ID（写入流水的 related_delivery_id/related_bill_id，便于回溯） */
    private void updateStock(Long warehouseId, Long materialId, BigDecimal delta, String qualityType,
                             String materialName, String changeType, String deliveryCode, Long relatedBillId) {
        if (warehouseId == null || materialId == null || delta == null || delta.compareTo(BigDecimal.ZERO) == 0) return;
        warehouseStockService.changeMaterialStockAllowNegative(warehouseId, materialId, delta, changeType,
                deliveryCode, RelatedBillType.MATERIAL_IO, relatedBillId, null, relatedBillId);
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
                // 负向扣减，可扣至负数（F1：带单据ID，流水可回溯）
                updateStock(compWhId, c.getChildMaterialId(), demand.negate(),
                        QualityType.GOOD.getCode(), childName, StockChangeType.OUTSOURCE_COMPONENT_CONSUME.getCode(), delivery.getCode(), delivery.getId());
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
                        QualityType.GOOD.getCode(), childName, StockChangeType.CANCEL_OUTSOURCE_COMPONENT_CONSUME.getCode(), delivery.getCode(), delivery.getId());
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

    /**
     * 调整库存：统一写入口（物料库存一律按 material_id 存于 warehouse_stock，与 product_id 互斥）。
     * <ul>
     *   <li>默认（allowNegative=false）：走 {@code changeMaterialStock}，带库存不足校验，**不允许扣成负数**</li>
     *   <li>allowNegative=true（单据显式勾选「强制出库」，或反审核对称回滚）：
     *       走 {@code changeMaterialStockAllowNegative}，允许扣成负数（服务层统一打负库存告警）</li>
     * </ul>
     * 2026-09-16 流程重构：原先"委外仓一律允许负库存"改为**按单据显式开关**，避免调拨把供应商委外仓默默搬成负数。
     * <p><b>F2（2026-09-17 修复）</b>：原先 allowNegative=true 且仓库非 INVENTORY（即委外仓）时，
     * 走的是本类私有 {@code updateStock} **直写** stockMapper + stockLogMapper —— 绕过统一库存服务
     * （未来服务层新口径会漏改），且不写 related_bill_no/related_bill_id（流水不可回溯）。
     * 现全部收敛到 {@link WarehouseStockService}，并补传单据ID。</p>
     *
     * @param relatedBillId 关联单据ID（本收发单ID），写入流水的 related_bill_id/related_delivery_id 以支持回溯
     */
    private void adjustSourceStock(Long warehouseId, Long materialId, BigDecimal delta, String materialName,
                                    String qualityType, String changeType, String orderCode, boolean allowNegative,
                                    Long relatedBillId) {
        if (warehouseId == null || materialId == null) return;
        if (allowNegative) {
            warehouseStockService.changeMaterialStockAllowNegative(warehouseId, materialId, delta, changeType, orderCode,
                    RelatedBillType.MATERIAL_IO, relatedBillId, null, relatedBillId);
            return;
        }
        warehouseStockService.changeMaterialStock(warehouseId, materialId, delta, changeType, orderCode,
                RelatedBillType.MATERIAL_IO, relatedBillId, null, relatedBillId);
    }

    @Override
    public java.math.BigDecimal calcWeightedPrice(Long factoryId, Long materialId) {
        if (factoryId == null || materialId == null) return java.math.BigDecimal.ZERO;
        // 优先按工厂（供应商）维度取价
        java.math.BigDecimal price = calcWeightedPriceInternal(
            new LambdaQueryWrapper<MaterialOrder>().eq(MaterialOrder::getSupplierId, factoryId), materialId);
        // 工厂维度查不到时回退：该物料全部物料订单的加权均价（保证新增单据有默认单价）
        if (price == null || price.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            price = calcWeightedPriceInternal(null, materialId);
        }
        return price != null ? price : java.math.BigDecimal.ZERO;
    }

    /** 按指定订单范围计算某物料的加权均价；无有效数量/无订单返回 null */
    private java.math.BigDecimal calcWeightedPriceInternal(LambdaQueryWrapper<MaterialOrder> orderWrapper, Long materialId) {
        if (materialId == null) return null;
        try {
            List<MaterialOrder> orders = orderWrapper != null
                ? materialOrderMapper.selectList(orderWrapper)
                : materialOrderMapper.selectList(new LambdaQueryWrapper<>());
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
        return null;
    }
}
