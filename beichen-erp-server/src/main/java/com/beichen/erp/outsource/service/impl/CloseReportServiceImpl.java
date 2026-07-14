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

    @Override
    public Map<String, Object> getOrCreateReport(Long orderId) {
        OutsourceOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");

        Map<String, Object> result = new LinkedHashMap<>();

        // 基本信息
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

        // 获取所有产品对应的项目ID，查 dev_bom
        Long projectId = null;
        for (OutsourceOrderProduct p : products) {
            if (p.getProjectId() != null) { projectId = p.getProjectId(); break; }
        }

        // 物料明细
        List<Map<String, Object>> items = new ArrayList<>();
        if (projectId != null) {
            List<Bom> bomList = bomMapper.selectList(
                new LambdaQueryWrapper<Bom>().eq(Bom::getProjectId, projectId));
            for (Bom bom : bomList) {
                Map<String, Object> item = buildMaterialRow(order, bom, deliveryList, products, deliveredByProduct);
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

    private Map<String, Object> buildMaterialRow(OutsourceOrder order, Bom bom,
                                                  List<OutsourceOrderDelivery> deliveryList,
                                                  List<OutsourceOrderProduct> products,
                                                  Map<String, BigDecimal> deliveredByProduct) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("materialName", bom.getMaterialName());
        item.put("materialType", bom.getMaterialType());
        item.put("unit", bom.getUnit());
        item.put("quantityPerSet", bom.getQuantityPerSet());
        // 加工良率 = 100% - 损耗率
        BigDecimal lossRate = bom.getLossRate() != null ? bom.getLossRate() : BigDecimal.ZERO;
        item.put("targetYieldRate", new BigDecimal(100).subtract(lossRate));

        // 发料数量 = 该物料所有发料收发单的数量汇总
        BigDecimal deliveredQty = sumDeliveryQuantity(order.getFactoryId(), bom.getMaterialName(), "发料");
        item.put("deliveredQuantity", deliveredQty);

        // 退料数量 = 该物料所有退料收发单的数量汇总
        BigDecimal returnedQty = sumDeliveryQuantity(order.getFactoryId(), bom.getMaterialName(), "退料");
        item.put("returnedQuantity", returnedQty);

        // 出货消耗 = SUM(产品交货数 × BOM单套用量)
        BigDecimal shippedTotal = BigDecimal.ZERO;
        for (OutsourceOrderProduct p : products) {
            BigDecimal pDelivered = deliveredByProduct.getOrDefault(p.getProductName() != null ? p.getProductName() : "", BigDecimal.ZERO);
            String pn = p.getProductName();
            // 检查该产品的BOM是否包含此物料
            if (p.getProjectId() != null) {
                List<Bom> productBom = bomMapper.selectList(
                    new LambdaQueryWrapper<Bom>().eq(Bom::getProjectId, p.getProjectId()));
                boolean hasMaterial = productBom.stream().anyMatch(b -> Objects.equals(b.getMaterialName(), bom.getMaterialName()));
                if (hasMaterial && pDelivered.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal perSet = bom.getQuantityPerSet() != null ? bom.getQuantityPerSet() : BigDecimal.ZERO;
                    shippedTotal = shippedTotal.add(pDelivered.multiply(perSet));
                }
            }
        }
        item.put("shippedQuantity", shippedTotal);

        // 良品退料/不良退料默认=0（用户可修改）
        item.put("goodReturnQty", BigDecimal.ZERO);
        item.put("defectReturnQty", BigDecimal.ZERO);
        item.put("remark", "");

        recalcItem(item);
        return item;
    }

    /** 汇总某工厂某物料的收发数量 */
    private BigDecimal sumDeliveryQuantity(Long factoryId, String materialName, String deliveryType) {
        // 查出该工厂所有对应类型的收发单
        List<OutsourceDelivery> deliveries = deliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceDelivery>()
                .eq(OutsourceDelivery::getFactoryId, factoryId)
                .eq(OutsourceDelivery::getDeliveryType, deliveryType)
                .eq(OutsourceDelivery::getStatus, "已确认"));
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
        BigDecimal targetYield = toBD(item.get("targetYieldRate"));

        // 生产良率 = (出货 + 良品退料) / 发料 × 100
        if (delivered.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal actualYield = shipped.add(goodReturn)
                .divide(delivered, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(100));
            item.put("actualYieldRate", actualYield);
            // 良率超损 = 加工良率 - 生产良率
            item.put("yieldLoss", targetYield.subtract(actualYield));
            // 超损数量 = 发料×(1-损耗率/100) - 出货 - 良品退料
            BigDecimal lossRate = new BigDecimal(100).subtract(targetYield);
            BigDecimal expectedGood = delivered.multiply(
                BigDecimal.ONE.subtract(lossRate.divide(new BigDecimal(100), 6, RoundingMode.HALF_UP)));
            BigDecimal excess = expectedGood.subtract(shipped).subtract(goodReturn);
            if (excess.compareTo(BigDecimal.ZERO) < 0) excess = BigDecimal.ZERO;
            item.put("excessLossQty", excess);
        } else {
            item.put("actualYieldRate", BigDecimal.ZERO);
            item.put("yieldLoss", BigDecimal.ZERO);
            item.put("excessLossQty", BigDecimal.ZERO);
        }
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
}
