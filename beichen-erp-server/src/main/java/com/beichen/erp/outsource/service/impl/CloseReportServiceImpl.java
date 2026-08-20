package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.common.OutsourceOrderStatus;
import com.beichen.erp.outsource.common.DeliveryStatus;
import com.beichen.erp.inventory.common.IoType;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.outsource.common.DeliveryType;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.outsource.common.CloseReportStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.outsource.entity.CloseReport;
import com.beichen.erp.outsource.entity.CloseReportItem;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.entity.OutsourceOrderMaterial;
import com.beichen.erp.outsource.entity.OutsourceOrderDelivery;
import com.beichen.erp.outsource.entity.OutsourceDelivery;
import com.beichen.erp.outsource.entity.OutsourceDeliveryItem;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceOtherIo;
import com.beichen.erp.outsource.entity.OutsourceOtherIoItem;
import com.beichen.erp.outsource.entity.MaterialOrder;
import com.beichen.erp.outsource.entity.MaterialOrderItem;
import com.beichen.erp.outsource.mapper.CloseReportMapper;
import com.beichen.erp.outsource.mapper.CloseReportItemMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderProductMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderDeliveryMapper;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryMapper;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryItemMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceOtherIoMapper;
import com.beichen.erp.outsource.mapper.OutsourceOtherIoItemMapper;
import com.beichen.erp.outsource.mapper.MaterialOrderMapper;
import com.beichen.erp.outsource.mapper.MaterialOrderItemMapper;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.outsource.service.CloseReportService;
import com.beichen.erp.dev.entity.Bom;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.mapper.BomMapper;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.finance.service.PayableHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloseReportServiceImpl extends ServiceImpl<CloseReportMapper, CloseReport> implements CloseReportService {

    private final CloseReportMapper reportMapper;
    private final CloseReportItemMapper itemMapper;
    private final OutsourceOrderMapper orderMapper;
    private final OutsourceOrderProductMapper productMapper;
    private final OutsourceOrderMaterialMapper orderMaterialMapper;
    private final OutsourceOrderDeliveryMapper orderDeliveryMapper;
    private final OutsourceDeliveryMapper deliveryMapper;
    private final OutsourceDeliveryItemMapper deliveryItemMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseStockMapper warehouseStockMapper;
    private final WarehouseStockService warehouseStockService;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final OutsourceOtherIoMapper otherIoMapper;
    private final OutsourceOtherIoItemMapper otherIoItemMapper;
    private final JdbcTemplate jdbcTemplate;
    private final BomMapper bomMapper;
    private final BomTypeMapper bomTypeMapper;
    private final SupplierMapper supplierMapper;
    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;
    private final PayableHelper payableHelper;

    @Override
    public Map<String, Object> getOrCreateReport(Long orderId) {
        OutsourceOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");

        Map<String, Object> result = new LinkedHashMap<>();

        // 基础信息
        result.put("orderId", order.getId());
        result.put("orderCode", order.getCode());
        if (order.getFactoryId() != null) {
            Supplier f = supplierMapper.selectById(order.getFactoryId());
            result.put("factoryName", f != null ? f.getName() : "");
        }

        // 产品信息
        List<OutsourceOrderProduct> products = productMapper.selectList(
            new LambdaQueryWrapper<OutsourceOrderProduct>().eq(OutsourceOrderProduct::getOrderId, orderId));
        result.put("products", products);

        // 交货记录
        List<OutsourceOrderDelivery> deliveryList = orderDeliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceOrderDelivery>().eq(OutsourceOrderDelivery::getOrderId, orderId)
                .orderByDesc(OutsourceOrderDelivery::getId));
        result.put("deliveries", deliveryList);

        // 总交货量（按产品ID汇总，再通过产品列表查名）
        Map<Long, BigDecimal> deliveredByProduct = new HashMap<>();
        for (OutsourceOrderDelivery d : deliveryList) {
            if (d.getProductId() == null) continue;
            deliveredByProduct.merge(d.getProductId(), d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
        }

        // 获取该工厂的所有委外仓库ID
        List<Long> factoryWhIds = new ArrayList<>();
        if (order.getFactoryId() != null) {
            List<Warehouse> whs = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getFactoryId, order.getFactoryId()));
            for (Warehouse wh : whs) factoryWhIds.add(wh.getId());
        }

        // 物料明细：取加工单的 BOM 快照（outsource_order_material），非实时 dev_bom
        List<Map<String, Object>> items = new ArrayList<>();
        java.util.Set<Long> seenMaterials = new java.util.HashSet<>();
        for (OutsourceOrderProduct p : products) {
            List<OutsourceOrderMaterial> mats = orderMaterialMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderMaterial>().eq(OutsourceOrderMaterial::getProductId, p.getId()));
            for (OutsourceOrderMaterial mat : mats) {
                Long mid = mat.getMaterialId();
                if (mid == null || !seenMaterials.add(mid)) continue;
                Map<String, Object> item = buildMaterialRow(order, mat, deliveryList, products, deliveredByProduct, factoryWhIds);
                items.add(item);
            }
        }
        result.put("items", items);

        // 已保存的报表数据（如果有）
        CloseReport existing = reportMapper.selectOne(
            new LambdaQueryWrapper<CloseReport>().eq(CloseReport::getOrderId, orderId));
        if (existing != null) {
            result.put("reportId", existing.getId());
            result.put("reportStatus", existing.getStatus());
            result.put("reportRemark", existing.getRemark());
            result.put("closeDate", existing.getCloseDate());
            List<CloseReportItem> savedItems = itemMapper.selectList(
                new LambdaQueryWrapper<CloseReportItem>().eq(CloseReportItem::getReportId, existing.getId()));
            // 将保存的编辑值合并到物料行
            for (Map<String, Object> item : items) {
                for (CloseReportItem si : savedItems) {
                    if (Objects.equals(item.get("materialId"), si.getMaterialId())) {
                        item.put("goodReturnQty", si.getGoodReturnQty());
                        item.put("defectReturnQty", si.getDefectReturnQty());
                        item.put("remark", si.getRemark());
                        if (si.getMaterialPrice() != null && si.getMaterialPrice().compareTo(BigDecimal.ZERO) > 0)
                            item.put("unitPrice", si.getMaterialPrice());
                        if (si.getFactoryRetainQty() != null)
                            item.put("factoryRetainQty", si.getFactoryRetainQty());
                        if (si.getMissingQty() != null)
                            item.put("missingQty", si.getMissingQty());
                        // 重新计算
                        recalcItem(item);
                    }
                }
            }
        } else {
            result.put("reportStatus", "未生成");
        }

        return result;
    }

    private Map<String, Object> buildMaterialRow(OutsourceOrder order, OutsourceOrderMaterial mat,
                                                  List<OutsourceOrderDelivery> deliveryList,
                                                  List<OutsourceOrderProduct> products,
                                                  Map<Long, BigDecimal> deliveredByProduct,
                                                  List<Long> factoryWhIds) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("materialName", getMaterialNameById(mat.getMaterialId()));
        item.put("materialId", mat.getMaterialId());
        item.put("bomTypeId", mat.getBomTypeId());
        item.put("bomTypeName", getBomTypeNameById(mat.getBomTypeId()));
        item.put("unit", mat.getUnit());
        BigDecimal perSet = mat.getDemandQuantity(); // 该物料在此产品中的总需求
        // 找到所属产品，计算单套用量 = 总需求 / 产品数量
        OutsourceOrderProduct ownerProduct = products.stream()
            .filter(p -> p.getId().equals(mat.getProductId())).findFirst().orElse(null);
        BigDecimal productQty = ownerProduct != null && ownerProduct.getQuantity() != null
            ? ownerProduct.getQuantity() : BigDecimal.ONE;
        BigDecimal qps = perSet != null ? perSet.divide(productQty, 10, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
        item.put("quantityPerSet", qps);
        // 加工良率 = 100% - 损耗率（取快照中的 lossRate）
        BigDecimal lossRate = mat.getLossRate() != null ? mat.getLossRate() : BigDecimal.ZERO;
        item.put("targetYieldRate", new BigDecimal(100).subtract(lossRate));

        // 发料数量（仅用于 FIFO 单价计算，不再作为展示字段）
        BigDecimal deliveredQty = sumDeliveryQuantity(factoryWhIds, mat.getMaterialId(), DeliveryType.DELIVERY.getCode())
                .add(sumDeliveryQuantity(factoryWhIds, mat.getMaterialId(), DeliveryType.RECEIVE.getCode()));

        // 退料数量 = 从该工厂仓库退出的退料总和
        BigDecimal returnedQty = sumDeliveryQuantity(factoryWhIds, mat.getMaterialId(), DeliveryType.RETURN.getCode());
        item.put("returnedQuantity", returnedQty);

        // 出货消耗 = SUM(该产品交货数 × 单套用量)
        BigDecimal shippedTotal = BigDecimal.ZERO;
        if (ownerProduct != null) {
            BigDecimal pDelivered = deliveredByProduct.getOrDefault(ownerProduct.getId(), BigDecimal.ZERO);
            if (pDelivered.compareTo(BigDecimal.ZERO) > 0) {
                shippedTotal = pDelivered.multiply(qps);
            }
        }
        item.put("shippedQuantity", shippedTotal);

        // 良品退料/不良退料/留存工厂/缺失默认=0（用户可修改）
        // 物料单价：先进先出，按交期升序取最早订单的单价
        BigDecimal unitPrice = calcFifoPrice(mat.getMaterialId(), null, deliveredQty);
        item.put("unitPrice", unitPrice);

        item.put("goodReturnQty", BigDecimal.ZERO);
        item.put("defectReturnQty", BigDecimal.ZERO);
        item.put("factoryRetainQty", BigDecimal.ZERO);
        item.put("missingQty", BigDecimal.ZERO);
        item.put("remark", "");

        recalcItem(item);
        return item;
    }

    /** 汇总某工厂仓库中某物料的收发数量（按物料ID精确聚合） */
    private BigDecimal sumDeliveryQuantity(List<Long> warehouseIds, Long materialId, String deliveryType) {
        if (warehouseIds == null || warehouseIds.isEmpty() || materialId == null) return BigDecimal.ZERO;
        return sumDeliveryQuantityById(warehouseIds, deliveryType, materialId);
    }

    private BigDecimal sumDeliveryQuantityById(List<Long> warehouseIds, String deliveryType, Long materialId) {
        // SQL 直接按 material_id 聚合，避免全量加载
        String whColumn = DeliveryType.RETURN.getCode().equals(deliveryType) ? "from_warehouse_id" : "to_warehouse_id";
        String sql = "SELECT COALESCE(SUM(di.quantity), 0) " +
            "FROM outsource_delivery_item di " +
            "INNER JOIN outsource_delivery d ON di.delivery_id = d.id " +
            "WHERE d.delivery_type = ? AND d.status = '" + DocStatus.AUDITED.getCode() + "' " +
            "AND d." + whColumn + " IN (" + warehouseIds.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",")) + ") " +
            "AND di.outsource_material_id = ?";
        BigDecimal result = jdbcTemplate.queryForObject(sql, BigDecimal.class, deliveryType, materialId);
        return result != null ? result : BigDecimal.ZERO;
    }

    /** 重新计算生产良率、超损等 */
    private void recalcItem(Map<String, Object> item) {
        BigDecimal shipped = toBD(item.get("shippedQuantity"));
        BigDecimal goodReturn = toBD(item.get("goodReturnQty"));
        BigDecimal defectReturn = toBD(item.get("defectReturnQty"));
        BigDecimal targetYield = toBD(item.get("targetYieldRate"));
        BigDecimal factoryRetain = toBD(item.get("factoryRetainQty"));
        // 缺失为手动填写，直接读取（不再自动推导）
        BigDecimal missing = toBD(item.get("missingQty"));

        // 退料总计 = 良品退料 + 不良退料
        BigDecimal totalReturn = goodReturn.add(defectReturn);
        item.put("totalReturnQty", totalReturn);

        // 用料总数 = 出货消耗 + 良品退料 + 不良退料 + 留存工厂 + 缺失（物料全部去向之和）
        BigDecimal usedTotal = shipped.add(goodReturn).add(defectReturn).add(factoryRetain).add(missing);
        item.put("usedTotalQuantity", usedTotal);

        // 生产良率% = 出货消耗 / (用料总数 - 留存 - 良退) × 100
        BigDecimal denom = usedTotal.subtract(factoryRetain).subtract(goodReturn);
        BigDecimal actualYield = BigDecimal.ZERO;
        if (denom.compareTo(BigDecimal.ZERO) > 0) {
            actualYield = shipped.divide(denom, 6, RoundingMode.HALF_UP).multiply(new BigDecimal(100));
        }
        item.put("actualYieldRate", actualYield.setScale(2, RoundingMode.HALF_UP));
        // 良率超损% = 加工良率 - 生产良率
        BigDecimal yieldLoss = targetYield.subtract(actualYield);
        item.put("yieldLoss", yieldLoss.setScale(2, RoundingMode.HALF_UP));

        // 超损数量 = (出货消耗 + 不良退料 + 缺失) × (良率超损%/100)（最小0）
        BigDecimal excessLossQty = shipped.add(defectReturn).add(missing).multiply(yieldLoss.divide(new BigDecimal(100), 6, RoundingMode.HALF_UP));
        if (excessLossQty.compareTo(BigDecimal.ZERO) < 0) excessLossQty = BigDecimal.ZERO;
        item.put("excessLossQty", excessLossQty.setScale(2, RoundingMode.HALF_UP));

        // 最大超损 = (用料总数 - 良品退料 - 工厂留存) × (1 - 加工良率/100)（最小0）
        BigDecimal maxLossRate = BigDecimal.ONE.subtract(targetYield.divide(new BigDecimal(100), 6, RoundingMode.HALF_UP));
        BigDecimal maxExcessLoss = usedTotal.subtract(goodReturn).subtract(factoryRetain).multiply(maxLossRate);
        if (maxExcessLoss.compareTo(BigDecimal.ZERO) < 0) maxExcessLoss = BigDecimal.ZERO;
        item.put("maxExcessLossQty", maxExcessLoss.setScale(2, RoundingMode.HALF_UP));

        // 超损总价 = 超损数量 × 物料单价
        BigDecimal unitPrice = toBD(item.get("unitPrice"));
        item.put("excessLossAmount", excessLossQty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal toBD(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        return new BigDecimal(v.toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDraft(Long orderId, List<CloseReportItem> items, String remark) {
        OutsourceOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");

        CloseReport report = reportMapper.selectOne(
            new LambdaQueryWrapper<CloseReport>().eq(CloseReport::getOrderId, orderId));

        if (report == null) {
            report = new CloseReport();
            report.setOrderId(orderId);
            report.setCloseDate(LocalDate.now());
            report.setStatus(DocStatus.DRAFT.getCode());
        }
        report.setRemark(remark);
        if (report.getId() == null) {
            reportMapper.insert(report);
        } else {
            reportMapper.updateById(report);
        }

        // 删除旧明细
        itemMapper.delete(new LambdaQueryWrapper<CloseReportItem>()
            .eq(CloseReportItem::getReportId, report.getId()));
        // 插入新明细
        if (items != null) {
            for (CloseReportItem item : items) {
                item.setId(null);
                item.setReportId(report.getId());
                itemMapper.insert(item);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmClose(Long orderId, Long returnWarehouseId) {
        OutsourceOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");
        if (!OutsourceOrderStatus.PRODUCING.getCode().equals(order.getStatus())) throw new BusinessException("只有生产中的加工单可结单");
        if (returnWarehouseId == null) throw new BusinessException("请选择退回仓库");

        CloseReport report = reportMapper.selectOne(
            new LambdaQueryWrapper<CloseReport>().eq(CloseReport::getOrderId, orderId));
        if (report == null) throw new BusinessException("请先保存结单报表");
        if (CloseReportStatus.FINISHED.getCode().equals(report.getStatus())) throw new BusinessException("已结单，不可重复结单");

        List<CloseReportItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<CloseReportItem>().eq(CloseReportItem::getReportId, report.getId()));

        // 找到该工厂的委外仓库
        List<Warehouse> warehouses = warehouseMapper.selectList(
            new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getFactoryId, order.getFactoryId()));
        if (warehouses.isEmpty()) throw new BusinessException("该加工厂未配置委外仓库");
        Long factoryWhId = warehouses.get(0).getId();
        // 退回仓库不能是当前工厂委外仓本身（否则退料无意义）
        if (factoryWhId.equals(returnWarehouseId)) throw new BusinessException("退回仓库不能是该工厂委外仓库");

        // 收集需要退料的物料
        List<OutsourceDeliveryItem> returnItems = new ArrayList<>();
        for (CloseReportItem item : items) {
            BigDecimal goodQty = item.getGoodReturnQty() != null ? item.getGoodReturnQty() : BigDecimal.ZERO;
            BigDecimal defectQty = item.getDefectReturnQty() != null ? item.getDefectReturnQty() : BigDecimal.ZERO;

            if (goodQty.compareTo(BigDecimal.ZERO) > 0) {
                OutsourceDeliveryItem di = buildReturnItem(item, goodQty, QualityType.GOOD.getCode());
                returnItems.add(di);
            }
            if (defectQty.compareTo(BigDecimal.ZERO) > 0) {
                OutsourceDeliveryItem di = buildReturnItem(item, defectQty, QualityType.DEFECT.getCode());
                returnItems.add(di);
            }
        }

        // 生成退料单
        if (!returnItems.isEmpty()) {
            OutsourceDelivery returnDelivery = new OutsourceDelivery();
            returnDelivery.setDeliveryType(DeliveryType.RETURN.getCode());
            returnDelivery.setFactoryId(order.getFactoryId());
            returnDelivery.setFromWarehouseId(factoryWhId);
            returnDelivery.setToWarehouseId(returnWarehouseId);
            returnDelivery.setDeliveryDate(LocalDate.now());
            returnDelivery.setStatus(DocStatus.AUDITED.getCode());
            returnDelivery.setRemark("结单自动退料 - " + order.getCode());
            returnDelivery.setCode(generateDeliveryCode());
            // 关联加工单，便于反结单精确定位退料单
            returnDelivery.setSourceOrderId(orderId);
            deliveryMapper.insert(returnDelivery);

            for (OutsourceDeliveryItem di : returnItems) {
                di.setDeliveryId(returnDelivery.getId());
                deliveryItemMapper.insert(di);
                // 退料：工厂委外仓减少（消耗）
                warehouseStockService.changeMaterialStock(factoryWhId, di.getMaterialId(), di.getQuantity().negate(),
                        StockChangeType.OUTSOURCE_RETURN_OUT.getCode(), order.getCode(),
                        RelatedBillType.OUTSOURCE_RETURN, returnDelivery.getId(), orderId);
                // 退回仓库增加
                warehouseStockService.changeMaterialStock(returnWarehouseId, di.getMaterialId(), di.getQuantity(),
                        StockChangeType.SETTLEMENT_RETURN_IN.getCode(), order.getCode(),
                        RelatedBillType.OUTSOURCE_RETURN, returnDelivery.getId(), orderId);
            }
        }

        // 缺失 → 生成物料其他出入库（出库），扣减工厂仓库存
        List<OutsourceOtherIoItem> missingItems = new ArrayList<>();
        for (CloseReportItem item : items) {
            // 缺失为手动填写，直接读取（不再由发料推导）
            BigDecimal missing = item.getMissingQty() != null ? item.getMissingQty() : BigDecimal.ZERO;
            if (missing.compareTo(BigDecimal.ZERO) <= 0) continue;

            OutsourceOtherIoItem oi = new OutsourceOtherIoItem();
            oi.setMaterialId(item.getMaterialId());
            oi.setBomTypeId(item.getBomTypeId());
            oi.setUnit(item.getUnit());
            oi.setQuantity(missing);
            oi.setRemark("加工厂遗失-" + order.getCode());
            missingItems.add(oi);
        }
        if (!missingItems.isEmpty()) {
            OutsourceOtherIo io = new OutsourceOtherIo();
            io.setWarehouseId(warehouses.get(0).getId());
            io.setIoType(IoType.OUT.getCode());
            io.setIoDate(LocalDate.now());
            io.setStatus(DeliveryStatus.CONFIRMED.getCode());
            io.setRemark("加工厂遗失 - " + order.getCode());
            io.setCode(BillPrefix.OTHER_IO + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + (System.currentTimeMillis() % 100000));
            otherIoMapper.insert(io);

            for (OutsourceOtherIoItem oi : missingItems) {
                oi.setOtherIoId(io.getId());
                otherIoItemMapper.insert(oi);
                deductStockById(warehouses.get(0).getId(), oi.getMaterialId(), oi.getQuantity(), order.getCode(), orderId);
            }
            log.info("加工单(ID={}) 结单生成缺失出库{}项", orderId, missingItems.size());
        }

        // 超损赔偿 → 生成负应付（冲减加工厂应付）；sourceId 用报表ID避免与交货应付(orderId)冲突
        // 超损总价 = Σ(超损数量 × 物料单价)
        BigDecimal totalExcessLoss = BigDecimal.ZERO;
        for (CloseReportItem it : items) {
            BigDecimal qty = it.getExcessLossQty() != null ? it.getExcessLossQty() : BigDecimal.ZERO;
            BigDecimal price = it.getMaterialPrice() != null ? it.getMaterialPrice() : BigDecimal.ZERO;
            totalExcessLoss = totalExcessLoss.add(qty.multiply(price));
        }
        if (totalExcessLoss.compareTo(BigDecimal.ZERO) > 0) {
            payableHelper.createPayable(order.getFactoryId(),
                    SourceBillType.OUTSOURCE_EXCESS_LOSS.getCode(),
                    order.getCode(), report.getId(), totalExcessLoss.negate(),
                    LocalDate.now(), "委外超损赔偿 - " + order.getCode());
        }

        // 更新加工单状态
        OutsourceOrder updateOrder = new OutsourceOrder();
        updateOrder.setId(orderId);
        updateOrder.setStatus(OutsourceOrderStatus.FINISHED.getCode());
        updateOrder.setActualEndDate(LocalDate.now());
        orderMapper.updateById(updateOrder);

        // 更新报表状态
        report.setStatus(CloseReportStatus.FINISHED.getCode());
        report.setCloseDate(LocalDate.now());
        reportMapper.updateById(report);

        log.info("加工单(ID={}) 已结单，生成退料{}项", orderId, returnItems.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reopenClose(Long orderId) {
        OutsourceOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");
        if (!OutsourceOrderStatus.FINISHED.getCode().equals(order.getStatus()))
            throw new BusinessException("只有已完成的加工单可反结单");

        CloseReport report = reportMapper.selectOne(
            new LambdaQueryWrapper<CloseReport>().eq(CloseReport::getOrderId, orderId));
        if (report == null) throw new BusinessException("未找到结单报表");
        if (!CloseReportStatus.FINISHED.getCode().equals(report.getStatus()))
            throw new BusinessException("该订单尚未结单，无需反结单");

        // 1. 冲回超损应付（已付款会自动拦截）
        payableHelper.reversePayable(report.getId());

        // 2. 逆向退料单：作废 + 工厂仓加回、退回仓减回
        List<OutsourceDelivery> returnDeliveries = deliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceDelivery>()
                .eq(OutsourceDelivery::getSourceOrderId, orderId)
                .eq(OutsourceDelivery::getDeliveryType, DeliveryType.RETURN.getCode()));
        for (OutsourceDelivery rd : returnDeliveries) {
            if (DocStatus.CANCELLED.getCode().equals(rd.getStatus())) continue;
            List<OutsourceDeliveryItem> rItems = deliveryItemMapper.selectList(
                new LambdaQueryWrapper<OutsourceDeliveryItem>().eq(OutsourceDeliveryItem::getDeliveryId, rd.getId()));
            for (OutsourceDeliveryItem di : rItems) {
                // 工厂委外仓加回（结单时是减）
                warehouseStockService.changeMaterialStock(rd.getFromWarehouseId(), di.getMaterialId(), di.getQuantity(),
                        StockChangeType.OUTSOURCE_RETURN_OUT.getCode(), order.getCode(),
                        RelatedBillType.OUTSOURCE_RETURN, rd.getId(), orderId);
                // 退回仓减回（结单时是加）
                warehouseStockService.changeMaterialStock(rd.getToWarehouseId(), di.getMaterialId(), di.getQuantity().negate(),
                        StockChangeType.SETTLEMENT_RETURN_IN.getCode(), order.getCode(),
                        RelatedBillType.OUTSOURCE_RETURN, rd.getId(), orderId);
            }
            // 作废退料单
            OutsourceDelivery updD = new OutsourceDelivery();
            updD.setId(rd.getId());
            updD.setStatus(DocStatus.CANCELLED.getCode());
            deliveryMapper.updateById(updD);
        }

        // 3. 逆向缺失出库单：作废 + 工厂仓加回（结单时是减）
        List<OutsourceOtherIo> missingIos = otherIoMapper.selectList(
            new LambdaQueryWrapper<OutsourceOtherIo>()
                .eq(OutsourceOtherIo::getRemark, "加工厂遗失 - " + order.getCode()));
        for (OutsourceOtherIo io : missingIos) {
            if (DeliveryStatus.CANCELLED.getCode().equals(io.getStatus())) continue;
            List<OutsourceOtherIoItem> ioItems = otherIoItemMapper.selectList(
                new LambdaQueryWrapper<OutsourceOtherIoItem>().eq(OutsourceOtherIoItem::getOtherIoId, io.getId()));
            for (OutsourceOtherIoItem oi : ioItems) {
                warehouseStockService.changeMaterialStock(io.getWarehouseId(), oi.getMaterialId(), oi.getQuantity(),
                        StockChangeType.OUTSOURCE_RETURN_OUT.getCode(), order.getCode(),
                        RelatedBillType.OUTSOURCE_RETURN, io.getId(), orderId);
            }
            // 作废缺失单
            OutsourceOtherIo updIo = new OutsourceOtherIo();
            updIo.setId(io.getId());
            updIo.setStatus(DeliveryStatus.CANCELLED.getCode());
            otherIoMapper.updateById(updIo);
        }

        // 4. 订单回退到生产中，清空实际结束日期（用 update 显式 set null，因 MP 默认忽略 null）
        orderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OutsourceOrder>()
                .eq(OutsourceOrder::getId, orderId)
                .set(OutsourceOrder::getStatus, OutsourceOrderStatus.PRODUCING.getCode())
                .set(OutsourceOrder::getActualEndDate, null));

        // 5. 报表回退草稿，清空结单日期，保留明细供重新编辑
        reportMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CloseReport>()
                .eq(CloseReport::getId, report.getId())
                .set(CloseReport::getStatus, CloseReportStatus.DRAFT.getCode())
                .set(CloseReport::getCloseDate, null));

        log.info("加工单(ID={}) 已反结单，退回生产中", orderId);
    }

    private OutsourceDeliveryItem buildReturnItem(CloseReportItem item, BigDecimal qty, String qualityType) {
        OutsourceDeliveryItem di = new OutsourceDeliveryItem();
        di.setMaterialId(item.getMaterialId());
        di.setBomTypeId(item.getBomTypeId());
        di.setUnit(item.getUnit());
        di.setQuantity(qty);
        di.setQualityType(qualityType);
        return di;
    }

    /** 缺失扣库存：工厂委外仓减少（消耗） */
    private void deductStockById(Long warehouseId, Long materialId, BigDecimal qty, String orderCode, Long orderId) {
        if (materialId == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) return;
        warehouseStockService.changeMaterialStock(warehouseId, materialId, qty.negate(),
                StockChangeType.OUTSOURCE_RETURN_OUT.getCode(), orderCode,
                RelatedBillType.OUTSOURCE_RETURN, null, orderId);
    }

    private String generateDeliveryCode() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String likePattern = BillPrefix.OUTSOURCE_DELIVERY + dateStr;
        LambdaQueryWrapper<OutsourceDelivery> w = new LambdaQueryWrapper<OutsourceDelivery>()
            .likeRight(OutsourceDelivery::getCode, likePattern)
            .orderByDesc(OutsourceDelivery::getCode).last("LIMIT 1");
        OutsourceDelivery last = deliveryMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                String numPart = last.getCode().substring(last.getCode().length() - 3);
                seq = Integer.parseInt(numPart) + 1;
            } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.OUTSOURCE_DELIVERY + dateStr + String.format("%03d", seq);
    }

    /** 加权平均单价：该工厂所有物料订单中该物料的 总金额/总数量 */
    /** 先进先出计算单价：按交期升序累计订单，直到满足需求量，计算加权均价 */
    private BigDecimal calcFifoPrice(Long materialId, String materialName, BigDecimal requiredQty) {
        if (materialId == null || requiredQty == null || requiredQty.compareTo(BigDecimal.ZERO) <= 0)
            return BigDecimal.ZERO;
        try {
            List<MaterialOrder> orders = materialOrderMapper.selectList(
                new LambdaQueryWrapper<MaterialOrder>().orderByAsc(MaterialOrder::getDeliveryDate));
            BigDecimal accumulatedAmount = BigDecimal.ZERO;
            BigDecimal accumulatedQty = BigDecimal.ZERO;
            for (MaterialOrder o : orders) {
                LambdaQueryWrapper<MaterialOrderItem> itemW = new LambdaQueryWrapper<MaterialOrderItem>()
                    .eq(MaterialOrderItem::getOrderId, o.getId())
                    .eq(MaterialOrderItem::getMaterialId, materialId);
                List<MaterialOrderItem> items = materialOrderItemMapper.selectList(itemW);
                for (MaterialOrderItem it : items) {
                    BigDecimal qty = it.getOrderQuantity() != null ? it.getOrderQuantity() : BigDecimal.ZERO;
                    BigDecimal price = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
                    if (qty.compareTo(BigDecimal.ZERO) <= 0 || price.compareTo(BigDecimal.ZERO) <= 0) continue;
                    BigDecimal need = requiredQty.subtract(accumulatedQty);
                    if (need.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal useQty = qty.min(need);
                    accumulatedAmount = accumulatedAmount.add(useQty.multiply(price));
                    accumulatedQty = accumulatedQty.add(useQty);
                }
                if (accumulatedQty.compareTo(requiredQty) >= 0) break;
            }
            if (accumulatedQty.compareTo(BigDecimal.ZERO) > 0)
                return accumulatedAmount.divide(accumulatedQty, 4, RoundingMode.HALF_UP);
        } catch (Exception e) { log.warn("FIFO单价计算失败: {}", e.getMessage()); }
        return BigDecimal.ZERO;
    }

    /** 根据委外物料ID查询名称，用于展示回填（ID关联查询替代冗余name字段） */
    private String getMaterialNameById(Long materialId) {
        if (materialId == null) return "";
        OutsourceMaterial m = outsourceMaterialMapper.selectById(materialId);
        return m != null ? m.getMaterialName() : "";
    }

    /** 根据 BOM 类型ID 查询类型名称，空安全返回 "-" */
    private String getBomTypeNameById(Long bomTypeId) {
        if (bomTypeId == null) return "-";
        BomType bt = bomTypeMapper.selectById(bomTypeId);
        return bt != null ? bt.getTypeName() : "-";
    }
}
