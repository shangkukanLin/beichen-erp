package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.entity.*;
import com.beichen.erp.outsource.mapper.*;
import com.beichen.erp.outsource.service.CloseReportService;
import com.beichen.erp.dev.entity.Bom;
import com.beichen.erp.dev.mapper.BomMapper;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final OutsourceWarehouseMapper warehouseMapper;
    private final OutsourceWarehouseStockMapper warehouseStockMapper;
    private final BomMapper bomMapper;
    private final SupplierMapper supplierMapper;
    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;

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

        // 总交货量（按产品名汇总）
        Map<String, BigDecimal> deliveredByProduct = new HashMap<>();
        for (OutsourceOrderDelivery d : deliveryList) {
            String pn = d.getProductName() != null ? d.getProductName() : "";
            deliveredByProduct.merge(pn, d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
        }

        // 获取该工厂的所有委外仓库ID
        List<Long> factoryWhIds = new ArrayList<>();
        if (order.getFactoryId() != null) {
            List<OutsourceWarehouse> whs = warehouseMapper.selectList(
                new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, order.getFactoryId()));
            for (OutsourceWarehouse wh : whs) factoryWhIds.add(wh.getId());
        }

        // 物料明细：取加工单的 BOM 快照（outsource_order_material），非实时 dev_bom
        List<Map<String, Object>> items = new ArrayList<>();
        java.util.Set<String> seenMaterials = new java.util.HashSet<>();
        for (OutsourceOrderProduct p : products) {
            List<OutsourceOrderMaterial> mats = orderMaterialMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderMaterial>().eq(OutsourceOrderMaterial::getProductId, p.getId()));
            for (OutsourceOrderMaterial mat : mats) {
                String mn = mat.getMaterialName();
                if (mn == null || mn.isBlank() || !seenMaterials.add(mn)) continue;
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
                    if (Objects.equals(item.get("materialName"), si.getMaterialName())) {
                        item.put("goodReturnQty", si.getGoodReturnQty());
                        item.put("defectReturnQty", si.getDefectReturnQty());
                        item.put("remark", si.getRemark());
                        if (si.getMaterialPrice() != null && si.getMaterialPrice().compareTo(BigDecimal.ZERO) > 0)
                            item.put("unitPrice", si.getMaterialPrice());
                        if (si.getFactoryRetainQty() != null)
                            item.put("factoryRetainQty", si.getFactoryRetainQty());
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
                                                  Map<String, BigDecimal> deliveredByProduct,
                                                  List<Long> factoryWhIds) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("materialName", mat.getMaterialName());
        item.put("materialType", mat.getMaterialType());
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

        // 发料数量 = 发到该工厂仓库的所有发料+收料的总和（含物料订单入库）
        BigDecimal deliveredQty = sumDeliveryQuantity(factoryWhIds, mat.getMaterialName(), "发料")
                .add(sumDeliveryQuantity(factoryWhIds, mat.getMaterialName(), "收料"));
        item.put("deliveredQuantity", deliveredQty);

        // 退料数量 = 从该工厂仓库退出的退料总和
        BigDecimal returnedQty = sumDeliveryQuantity(factoryWhIds, mat.getMaterialName(), "退料");
        item.put("returnedQuantity", returnedQty);

        // 出货消耗 = SUM(该产品交货数 × 单套用量)
        BigDecimal shippedTotal = BigDecimal.ZERO;
        if (ownerProduct != null) {
            BigDecimal pDelivered = deliveredByProduct.getOrDefault(ownerProduct.getProductName() != null ? ownerProduct.getProductName() : "", BigDecimal.ZERO);
            if (pDelivered.compareTo(BigDecimal.ZERO) > 0) {
                shippedTotal = pDelivered.multiply(qps);
            }
        }
        item.put("shippedQuantity", shippedTotal);

        // 良品退料/不良退料默认=0（用户可修改）
        // 物料单价：先进先出，按交期升序取最早订单的单价
        BigDecimal unitPrice = calcFifoPrice(mat.getMaterialName(), deliveredQty);
        item.put("unitPrice", unitPrice);

        item.put("goodReturnQty", BigDecimal.ZERO);
        item.put("defectReturnQty", BigDecimal.ZERO);
        item.put("factoryRetainQty", BigDecimal.ZERO);
        item.put("remark", "");

        recalcItem(item);
        return item;
    }

    /** 汇总某工厂仓库中某物料的收发数量 */
    private BigDecimal sumDeliveryQuantity(List<Long> warehouseIds, String materialName, String deliveryType) {
        if (warehouseIds == null || warehouseIds.isEmpty()) return BigDecimal.ZERO;
        // 查出目标仓库属于该工厂的对应类型收发单
        LambdaQueryWrapper<OutsourceDelivery> w = new LambdaQueryWrapper<OutsourceDelivery>()
                .eq(OutsourceDelivery::getDeliveryType, deliveryType)
                .eq(OutsourceDelivery::getStatus, "已确认");
        if ("退料".equals(deliveryType)) {
            w.in(OutsourceDelivery::getFromWarehouseId, warehouseIds);
        } else {
            w.in(OutsourceDelivery::getToWarehouseId, warehouseIds);
        }
        List<OutsourceDelivery> deliveries = deliveryMapper.selectList(w);
        BigDecimal total = BigDecimal.ZERO;
        for (OutsourceDelivery d : deliveries) {
            List<OutsourceDeliveryItem> items = deliveryItemMapper.selectList(
                new LambdaQueryWrapper<OutsourceDeliveryItem>()
                    .eq(OutsourceDeliveryItem::getDeliveryId, d.getId()));
            for (OutsourceDeliveryItem item : items) {
                if (Objects.equals(materialName, item.getMaterialName())) {
                    if (item.getQuantity() != null) total = total.add(item.getQuantity());
                }
            }
        }
        return total;
    }

    /** 重新计算生产良率、超损等 */
    private void recalcItem(Map<String, Object> item) {
        BigDecimal delivered = toBD(item.get("deliveredQuantity"));
        BigDecimal shipped = toBD(item.get("shippedQuantity"));
        BigDecimal goodReturn = toBD(item.get("goodReturnQty"));
        BigDecimal defectReturn = toBD(item.get("defectReturnQty"));
        BigDecimal targetYield = toBD(item.get("targetYieldRate"));
        BigDecimal factoryRetain = toBD(item.get("factoryRetainQty"));

        // 退料总计 = 良品退料 + 不良退料
        BigDecimal totalReturn = goodReturn.add(defectReturn);
        item.put("totalReturnQty", totalReturn);
        // 缺失 = 发料 - 退料总计 - 出货消耗 - 留存工厂
        BigDecimal missing = delivered.subtract(totalReturn).subtract(shipped).subtract(factoryRetain);
        item.put("missingQty", missing);

        // 生产良率% = 出货消耗 / (发料 - 留存 - 良退) × 100
        BigDecimal denom = delivered.subtract(factoryRetain).subtract(goodReturn);
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

        // 最大超损 = (发料 - 良品退料 - 工厂留存) × (1 - 加工良率/100)（最小0）
        BigDecimal maxLossRate = BigDecimal.ONE.subtract(targetYield.divide(new BigDecimal(100), 6, RoundingMode.HALF_UP));
        BigDecimal maxExcessLoss = delivered.subtract(goodReturn).subtract(factoryRetain).multiply(maxLossRate);
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
            report.setStatus("草稿");
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
    public void confirmClose(Long orderId) {
        OutsourceOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");
        if (!"生产中".equals(order.getStatus())) throw new BusinessException("只有生产中的加工单可结单");

        CloseReport report = reportMapper.selectOne(
            new LambdaQueryWrapper<CloseReport>().eq(CloseReport::getOrderId, orderId));
        if (report == null) throw new BusinessException("请先保存结单报表");
        if ("已结单".equals(report.getStatus())) throw new BusinessException("已结单，不可重复结单");

        List<CloseReportItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<CloseReportItem>().eq(CloseReportItem::getReportId, report.getId()));

        // 找到该工厂的委外仓库
        List<OutsourceWarehouse> warehouses = warehouseMapper.selectList(
            new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, order.getFactoryId()));
        if (warehouses.isEmpty()) throw new BusinessException("该加工厂未配置委外仓库");

        // 收集需要退料的物料
        List<OutsourceDeliveryItem> returnItems = new ArrayList<>();
        for (CloseReportItem item : items) {
            BigDecimal goodQty = item.getGoodReturnQty() != null ? item.getGoodReturnQty() : BigDecimal.ZERO;
            BigDecimal defectQty = item.getDefectReturnQty() != null ? item.getDefectReturnQty() : BigDecimal.ZERO;

            if (goodQty.compareTo(BigDecimal.ZERO) > 0) {
                OutsourceDeliveryItem di = buildReturnItem(item, goodQty, "良品");
                returnItems.add(di);
            }
            if (defectQty.compareTo(BigDecimal.ZERO) > 0) {
                OutsourceDeliveryItem di = buildReturnItem(item, defectQty, "不良品");
                returnItems.add(di);
            }
        }

        // 生成退料单
        if (!returnItems.isEmpty()) {
            OutsourceDelivery returnDelivery = new OutsourceDelivery();
            returnDelivery.setDeliveryType("退料");
            returnDelivery.setFactoryId(order.getFactoryId());
            returnDelivery.setFromWarehouseId(warehouses.get(0).getId());
            returnDelivery.setDeliveryDate(LocalDate.now());
            returnDelivery.setStatus("已确认");
            returnDelivery.setRemark("结单自动退料 - " + order.getCode());
            returnDelivery.setCode(generateDeliveryCode());
            deliveryMapper.insert(returnDelivery);

            for (OutsourceDeliveryItem di : returnItems) {
                di.setDeliveryId(returnDelivery.getId());
                deliveryItemMapper.insert(di);
                // 退料 = 来源仓库减少（消耗）
                updateReturnStock(warehouses.get(0).getId(), di.getMaterialId(), di.getQuantity(), di.getQualityType());
            }
        }

        // 更新加工单状态
        OutsourceOrder updateOrder = new OutsourceOrder();
        updateOrder.setId(orderId);
        updateOrder.setStatus("已完成");
        updateOrder.setActualEndDate(LocalDate.now());
        orderMapper.updateById(updateOrder);

        // 更新报表状态
        report.setStatus("已结单");
        report.setCloseDate(LocalDate.now());
        reportMapper.updateById(report);

        log.info("加工单(ID={}) 已结单，生成退料{}项", orderId, returnItems.size());
    }

    private OutsourceDeliveryItem buildReturnItem(CloseReportItem item, BigDecimal qty, String qualityType) {
        OutsourceDeliveryItem di = new OutsourceDeliveryItem();
        di.setMaterialId(item.getMaterialId());
        di.setMaterialName(item.getMaterialName());
        di.setMaterialType(item.getMaterialType());
        di.setUnit(item.getUnit());
        di.setQuantity(qty);
        di.setQualityType(qualityType);
        return di;
    }

    private void updateReturnStock(Long warehouseId, Long materialId, BigDecimal qty, String qualityType) {
        LambdaQueryWrapper<OutsourceWarehouseStock> w = new LambdaQueryWrapper<OutsourceWarehouseStock>()
            .eq(OutsourceWarehouseStock::getWarehouseId, warehouseId)
            .eq(OutsourceWarehouseStock::getMaterialId, materialId)
            .eq(OutsourceWarehouseStock::getQualityType, qualityType != null ? qualityType : "良品");
        OutsourceWarehouseStock stock = warehouseStockMapper.selectOne(w);
        if (stock == null) {
            stock = new OutsourceWarehouseStock();
            stock.setWarehouseId(warehouseId);
            stock.setMaterialId(materialId);
            stock.setQualityType(qualityType != null ? qualityType : "良品");
            stock.setQuantity(qty.negate());
            warehouseStockMapper.insert(stock);
        } else {
            stock.setQuantity(stock.getQuantity().subtract(qty));
            warehouseStockMapper.updateById(stock);
        }
    }

    private String generateDeliveryCode() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String likePattern = "DEL-" + dateStr;
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
        return "DEL-" + dateStr + String.format("%03d", seq);
    }

    /** 加权平均单价：该工厂所有物料订单中该物料的 总金额/总数量 */
    /** 先进先出计算单价：按交期升序累计订单，直到满足需求量，计算加权均价 */
    private BigDecimal calcFifoPrice(String materialName, BigDecimal requiredQty) {
        if (materialName == null || requiredQty == null || requiredQty.compareTo(BigDecimal.ZERO) <= 0)
            return BigDecimal.ZERO;
        try {
            // 查该物料所有订单，按交期升序
            List<MaterialOrder> orders = materialOrderMapper.selectList(
                new LambdaQueryWrapper<MaterialOrder>().orderByAsc(MaterialOrder::getDeliveryDate));
            BigDecimal accumulatedAmount = BigDecimal.ZERO;
            BigDecimal accumulatedQty = BigDecimal.ZERO;
            for (MaterialOrder o : orders) {
                List<MaterialOrderItem> items = materialOrderItemMapper.selectList(
                    new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, o.getId()));
                for (MaterialOrderItem it : items) {
                    if (!Objects.equals(materialName, it.getMaterialName())) continue;
                    BigDecimal qty = it.getOrderQuantity() != null ? it.getOrderQuantity() : BigDecimal.ZERO;
                    BigDecimal price = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
                    if (qty.compareTo(BigDecimal.ZERO) <= 0 || price.compareTo(BigDecimal.ZERO) <= 0) continue;
                    // 只需要用到满足 requiredQty 为止
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
}
