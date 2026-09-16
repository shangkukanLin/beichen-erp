package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.outsource.common.OutsourceOrderStatus;
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
import com.beichen.erp.dev.entity.MaterialType;
import com.beichen.erp.dev.mapper.BomMapper;
import com.beichen.erp.dev.mapper.MaterialTypeMapper;
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
    private final MaterialTypeMapper materialTypeMapper;
    private final SupplierMapper supplierMapper;
    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;
    private final PayableHelper payableHelper;
    private final com.beichen.erp.material.service.ProductService productService;

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
        // SKU 是非表字段，按产品主数据ID批量回填，供界面/导出展示
        productService.fillSku(products, OutsourceOrderProduct::getProductId, OutsourceOrderProduct::setSku);
        result.put("products", products);

        // 交货记录
        List<OutsourceOrderDelivery> deliveryList = orderDeliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceOrderDelivery>().eq(OutsourceOrderDelivery::getOrderId, orderId)
                .orderByDesc(OutsourceOrderDelivery::getId));
        result.put("deliveries", deliveryList);

        // 总交货量：**按产品主数据ID汇总**（加工单编辑会重建产品行、行ID变化，主数据ID稳定），
        // 历史数据缺失主数据ID时回退按产品行ID汇总
        Map<Long, BigDecimal> deliveredByProduct = new HashMap<>();
        for (OutsourceOrderDelivery d : deliveryList) {
            Long key = d.getProductMasterId() != null ? d.getProductMasterId() : d.getProductId();
            if (key == null) continue;
            deliveredByProduct.merge(key, d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
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
            // 工厂包料（包工包料）不纳入我方超损/用料考核
            mats = mats.stream().filter(m -> !"FACTORY".equals(m.getSupplyType())).collect(java.util.stream.Collectors.toList());
            for (OutsourceOrderMaterial mat : mats) {
                Long mid = mat.getMaterialId();
                if (mid == null || !seenMaterials.add(mid)) continue;
                Map<String, Object> item = buildMaterialRow(order, mat, deliveryList, products, deliveredByProduct, factoryWhIds);
                items.add(item);
            }
        }
        result.put("items", items);

        // 退料可行性预检（2026-09-16 问题①）：把"工厂委外仓当前账面库存"一并返回，
        // 让结单页在提交前就能把"账面不足/为负"的物料标出来（不勾强制退料时，后端会直接拦住结单）
        for (Map<String, Object> item : items) {
            Object midObj = item.get("materialId");
            if (midObj == null) continue;
            Long mid = Long.valueOf(midObj.toString());
            BigDecimal stock = BigDecimal.ZERO;
            for (Long whId : factoryWhIds) {
                stock = stock.add(warehouseStockService.getMaterialQuantity(whId, mid));
            }
            item.put("factoryStockQty", stock);
        }

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
            // 未生成结单报表：状态置空（前端按空值显示「未生成」），不与 code 体系混用中文哨兵
            result.put("reportStatus", null);
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
        item.put("materialTypeId", mat.getMaterialTypeId());
        item.put("materialTypeName", getMaterialTypeNameById(mat.getMaterialTypeId()));
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

        // 发料数量（仅用于 FIFO 单价计算，不再作为展示字段）：委外发料/收货 + 其他出入库入库
        BigDecimal deliveredQty = sumDeliveryQuantity(factoryWhIds, mat.getMaterialId(), DeliveryType.DELIVERY.getCode())
                .add(sumDeliveryQuantity(factoryWhIds, mat.getMaterialId(), DeliveryType.RECEIVE.getCode()))
                .add(sumOtherIoQuantity(factoryWhIds, mat.getMaterialId()));

        // 退料数量 = 历史退料(RETURN，已下线但历史单据仍计入) + 本加工单结单产生的调拨量
        // （2026-09-16 流程重构：结单"自动退料"改生成调拨单，故口径按 source_order_id 精确归属追平）
        BigDecimal returnedQty = sumDeliveryQuantity(factoryWhIds, mat.getMaterialId(), DeliveryType.RETURN.getCode())
                .add(sumCloseReturnQuantity(order.getId(), mat.getMaterialId()));
        item.put("returnedQuantity", returnedQty);

        // 出货消耗 = SUM(该产品交货数 × 单套用量)
        BigDecimal shippedTotal = BigDecimal.ZERO;
        if (ownerProduct != null) {
            // 主数据ID优先，行ID兜底（与上面汇总口径对应）
            BigDecimal pDelivered = ownerProduct.getProductId() != null
                    ? deliveredByProduct.get(ownerProduct.getProductId()) : null;
            if (pDelivered == null) pDelivered = deliveredByProduct.getOrDefault(ownerProduct.getId(), BigDecimal.ZERO);
            if (pDelivered.compareTo(BigDecimal.ZERO) > 0) {
                // 2026-09-16：数量一律为整数 —— 单套用量 qps 是比率，乘出来的数量需取整（四舍五入）
                shippedTotal = pDelivered.multiply(qps).setScale(0, RoundingMode.HALF_UP);
            }
        }
        item.put("shippedQuantity", shippedTotal);

        // 良品退料/不良退料/留存工厂/缺失默认=0（用户可修改）
        // 物料单价：优先取系统移动加权成本（与退货计价口径一致）→ 物料主数据参考价 → 发料成本（其他出入库填写价 → 物料订单先进先出）
        BigDecimal unitPrice = BigDecimal.ZERO;
        OutsourceMaterial matInfo = outsourceMaterialMapper.selectById(mat.getMaterialId());
        if (matInfo != null && matInfo.getCostPrice() != null && matInfo.getCostPrice().compareTo(BigDecimal.ZERO) > 0) {
            unitPrice = matInfo.getCostPrice();
        } else if (matInfo != null && matInfo.getPrice() != null && matInfo.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            unitPrice = matInfo.getPrice();
        } else {
            unitPrice = calcOtherIoPrice(factoryWhIds, mat.getMaterialId());
            if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                unitPrice = calcFifoPrice(mat.getMaterialId(), null, deliveredQty);
            }
        }
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

    /**
     * 本加工单结单产生的"退料量"（2026-09-16 起为**调拨单**，按 source_order_id 精确归属）。
     * <p>只统计已审核；清算一键退料生成的调拨单没有 source_order_id，故不会误计入加工单退料。</p>
     */
    private BigDecimal sumCloseReturnQuantity(Long orderId, Long materialId) {
        if (orderId == null || materialId == null) return BigDecimal.ZERO;
        String sql = "SELECT COALESCE(SUM(di.quantity), 0) " +
            "FROM outsource_delivery_item di " +
            "INNER JOIN outsource_delivery d ON di.delivery_id = d.id " +
            "WHERE d.delivery_type = ? AND d.source_order_id = ? AND d.status = '" + DocStatus.AUDITED.getCode() + "' " +
            "AND di.outsource_material_id = ?";
        BigDecimal r = jdbcTemplate.queryForObject(sql, BigDecimal.class,
                DeliveryType.TRANSFER.getCode(), orderId, materialId);
        return r != null ? r : BigDecimal.ZERO;
    }

    private BigDecimal sumDeliveryQuantityById(List<Long> warehouseIds, String deliveryType, Long materialId) {
        // SQL 直接按 material_id 聚合，避免全量加载
        // 退料/调拨都是从工厂仓"出"，取 from_warehouse_id；发料/收料是"进"，取 to_warehouse_id
        String whColumn = (DeliveryType.RETURN.getCode().equals(deliveryType)
                || DeliveryType.TRANSFER.getCode().equals(deliveryType)) ? "from_warehouse_id" : "to_warehouse_id";
        String sql = "SELECT COALESCE(SUM(di.quantity), 0) " +
            "FROM outsource_delivery_item di " +
            "INNER JOIN outsource_delivery d ON di.delivery_id = d.id " +
            "WHERE d.delivery_type = ? AND d.status = '" + DocStatus.AUDITED.getCode() + "' " +
            "AND d." + whColumn + " IN (" + warehouseIds.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",")) + ") " +
            "AND di.outsource_material_id = ?";
        BigDecimal result = jdbcTemplate.queryForObject(sql, BigDecimal.class, deliveryType, materialId);
        return result != null ? result : BigDecimal.ZERO;
    }

    /** 其他出入库入库量：发往该工厂委外仓库的已审核 IN 单物料数量（纳入结单发料量/单价计算） */
    private BigDecimal sumOtherIoQuantity(List<Long> warehouseIds, Long materialId) {
        if (warehouseIds == null || warehouseIds.isEmpty() || materialId == null) return BigDecimal.ZERO;
        String sql = "SELECT COALESCE(SUM(ii.quantity), 0) " +
            "FROM outsource_other_io_item ii " +
            "INNER JOIN outsource_other_io io ON ii.other_io_id = io.id " +
            "WHERE io.io_type = ? AND io.status = '" + DocStatus.AUDITED.getCode() + "' " +
            "AND io.warehouse_id IN (" + warehouseIds.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",")) + ") " +
            "AND ii.outsource_material_id = ?";
        BigDecimal result = jdbcTemplate.queryForObject(sql, BigDecimal.class, IoType.IN.getCode(), materialId);
        return result != null ? result : BigDecimal.ZERO;
    }

    /** 其他出入库入库加权单价：发往该工厂委外仓库的已审核 IN 单明细（仅取单价>0）的 Σ数量×单价/Σ数量 */
    private BigDecimal calcOtherIoPrice(List<Long> warehouseIds, Long materialId) {
        if (warehouseIds == null || warehouseIds.isEmpty() || materialId == null) return BigDecimal.ZERO;
        String sql = "SELECT COALESCE(SUM(ii.quantity * ii.unit_price), 0) AS total_amount, " +
            "COALESCE(SUM(ii.quantity), 0) AS total_qty " +
            "FROM outsource_other_io_item ii " +
            "INNER JOIN outsource_other_io io ON ii.other_io_id = io.id " +
            "WHERE io.io_type = ? AND io.status = '" + DocStatus.AUDITED.getCode() + "' " +
            "AND io.warehouse_id IN (" + warehouseIds.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",")) + ") " +
            "AND ii.outsource_material_id = ? AND ii.unit_price > 0";
        Map<String, Object> row = jdbcTemplate.queryForMap(sql, IoType.IN.getCode(), materialId);
        BigDecimal amount = toBD(row.get("total_amount"));
        BigDecimal qty = toBD(row.get("total_qty"));
        if (qty.compareTo(BigDecimal.ZERO) > 0) return amount.divide(qty, 4, RoundingMode.HALF_UP);
        return BigDecimal.ZERO;
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
        // 良率超损% = 加工良率 - 生产良率（最小0，不允许负值）
        BigDecimal yieldLoss = targetYield.subtract(actualYield);
        if (yieldLoss.compareTo(BigDecimal.ZERO) < 0) yieldLoss = BigDecimal.ZERO;
        item.put("yieldLoss", yieldLoss.setScale(2, RoundingMode.HALF_UP));

        // 超损数量 = (出货消耗 + 不良退料 + 缺失) × (良率超损%/100)（最小0）；2026-09-16 数量取整为整数
        BigDecimal excessLossQty = shipped.add(defectReturn).add(missing).multiply(yieldLoss.divide(new BigDecimal(100), 6, RoundingMode.HALF_UP));
        if (excessLossQty.compareTo(BigDecimal.ZERO) < 0) excessLossQty = BigDecimal.ZERO;
        excessLossQty = excessLossQty.setScale(0, RoundingMode.HALF_UP);
        item.put("excessLossQty", excessLossQty);

        // 最大超损 = (用料总数 - 良品退料 - 工厂留存) × (1 - 加工良率/100)（最小0）；数量取整为整数
        BigDecimal maxLossRate = BigDecimal.ONE.subtract(targetYield.divide(new BigDecimal(100), 6, RoundingMode.HALF_UP));
        BigDecimal maxExcessLoss = usedTotal.subtract(goodReturn).subtract(factoryRetain).multiply(maxLossRate);
        if (maxExcessLoss.compareTo(BigDecimal.ZERO) < 0) maxExcessLoss = BigDecimal.ZERO;
        item.put("maxExcessLossQty", maxExcessLoss.setScale(0, RoundingMode.HALF_UP));

        // 超损总价 = 超损数量 × 物料单价
        BigDecimal unitPrice = toBD(item.get("unitPrice"));
        item.put("excessLossAmount", excessLossQty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
    }

    /** null 安全的数量/金额取值 */
    private BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    /**
     * 超损上限校验：超损数量不得超过「最大超损」。
     * <p>公式与报表展示口径一致（见 {@link #recalcItem}）：
     * 最大超损 = (用料总数 − 良品退料 − 留存工厂) × (1 − 加工良率/100)，
     * 用料总数 = 出货消耗 + 良品退料 + 不良退料 + 留存工厂 + 缺失。</p>
     * <p>报表已经算出该上限，但保存草稿与结单此前都不校验，可填任意超损数量直接生成超损赔偿（负应付）。</p>
     */
    private void assertExcessLossWithinLimit(List<CloseReportItem> items) {
        if (items == null) return;
        for (CloseReportItem it : items) {
            BigDecimal qty = nz(it.getExcessLossQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal shipped = nz(it.getShippedQuantity());
            BigDecimal good = nz(it.getGoodReturnQty());
            BigDecimal defect = nz(it.getDefectReturnQty());
            BigDecimal retain = nz(it.getFactoryRetainQty());
            BigDecimal missing = nz(it.getMissingQty());
            BigDecimal targetYield = it.getTargetYieldRate() != null ? it.getTargetYieldRate() : new BigDecimal(100);
            BigDecimal usedTotal = shipped.add(good).add(defect).add(retain).add(missing);
            BigDecimal maxLossRate = BigDecimal.ONE.subtract(
                    targetYield.divide(new BigDecimal(100), 6, RoundingMode.HALF_UP));
            BigDecimal max = usedTotal.subtract(good).subtract(retain).multiply(maxLossRate);
            if (max.compareTo(BigDecimal.ZERO) < 0) max = BigDecimal.ZERO;
            max = max.setScale(2, RoundingMode.HALF_UP);
            if (qty.compareTo(max) > 0) {
                throw new BusinessException("物料[" + getMaterialNameById(it.getMaterialId()) + "]超损数量 "
                        + qty.stripTrailingZeros().toPlainString() + " 超过上限 " + max.stripTrailingZeros().toPlainString()
                        + "（用料总数 " + usedTotal.stripTrailingZeros().toPlainString()
                        + " − 良品退料 " + good.stripTrailingZeros().toPlainString()
                        + " − 留存工厂 " + retain.stripTrailingZeros().toPlainString()
                        + "）× (1 − 加工良率 " + targetYield.stripTrailingZeros().toPlainString() + "%)");
            }
        }
    }

    /** 生成委外其他出入库单号（前缀+日期+3位流水，与委外其他出入库模块一致） */
    private String generateOtherIoCode() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String likePattern = BillPrefix.OUTSOURCE_OTHER_IO + dateStr;
        OutsourceOtherIo last = otherIoMapper.selectOne(new LambdaQueryWrapper<OutsourceOtherIo>()
                .likeRight(OutsourceOtherIo::getCode, likePattern)
                .orderByDesc(OutsourceOtherIo::getCode).last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1;
            } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.OUTSOURCE_OTHER_IO + dateStr + String.format("%03d", seq);
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
        // 超损不得超上限，避免存下"任意超损数量"的报表并在结单时生成超额赔偿
        assertExcessLossWithinLimit(items);

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

    /**
     * 确认结单。
     *
     * @param force 2026-09-16（问题①）：委外交货领料走"允许负"口径（clamp 到负数），而结单退料原先走严格口径，
     *              导致"工厂账面无料（负/无库存行）但实际有料"时退不回来，必须先补入库单。
     *              {@code force=true}（前端「强制退料」勾选）时退料与缺失按"强制出库"口径扣减（允许负数，流水留痕）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmClose(Long orderId, Long returnWarehouseId, boolean force) {
        OutsourceOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");
        // 原子抢占加工单状态（P2-29）：并发/双击结单只会成功一次，避免重复退料与重复生成超损应付
        if (!DocStatusGuard.claim(orderMapper, OutsourceOrder::getId, orderId, OutsourceOrder::getStatus,
                OutsourceOrderStatus.PRODUCING.getCode(), OutsourceOrderStatus.FINISHED.getCode()))
            throw new BusinessException("只有生产中的加工单可结单");
        if (returnWarehouseId == null) throw new BusinessException("请选择退回仓库");

        CloseReport report = reportMapper.selectOne(
            new LambdaQueryWrapper<CloseReport>().eq(CloseReport::getOrderId, orderId));
        if (report == null) throw new BusinessException("请先保存结单报表");
        // 报表状态一并原子抢占（P2-29）：DRAFT→FINISHED，双重保护
        if (!DocStatusGuard.claim(reportMapper, CloseReport::getId, report.getId(), CloseReport::getStatus,
                CloseReportStatus.DRAFT.getCode(), CloseReportStatus.FINISHED.getCode()))
            throw new BusinessException("已结单，不可重复结单");

        List<CloseReportItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<CloseReportItem>().eq(CloseReportItem::getReportId, report.getId()));
        // 结单前再校验一次（覆盖"报表保存后才调整过明细"的历史数据）
        assertExcessLossWithinLimit(items);

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

        // 生成"退料"单据（2026-09-16 流程重构：退料下线，改生成**调拨单**：工厂委外仓 → 退回仓）
        if (!returnItems.isEmpty()) {
            OutsourceDelivery returnDelivery = new OutsourceDelivery();
            returnDelivery.setDeliveryType(DeliveryType.TRANSFER.getCode());
            returnDelivery.setFactoryId(order.getFactoryId());
            returnDelivery.setFromWarehouseId(factoryWhId);
            returnDelivery.setToWarehouseId(returnWarehouseId);
            returnDelivery.setDeliveryDate(LocalDate.now());
            returnDelivery.setStatus(DocStatus.AUDITED.getCode());
            returnDelivery.setRemark("结单自动退料（调拨）- " + order.getCode());
            returnDelivery.setCode(generateDeliveryCode());
            // 关联加工单，便于反结单精确定位退料单
            returnDelivery.setSourceOrderId(orderId);
            deliveryMapper.insert(returnDelivery);

            for (OutsourceDeliveryItem di : returnItems) {
                di.setDeliveryId(returnDelivery.getId());
                deliveryItemMapper.insert(di);
                // 退料：工厂委外仓减少（消耗）
                // 问题①（2026-09-16）：严格口径下账面不足会直接抛错（"物料[X]库存不足…"）；force=true 时
                // 按"强制出库"口径扣减（允许负数，流水以 changeType/单据号留痕），与调拨的强制出库同规格
                if (force) {
                    warehouseStockService.changeMaterialStockAllowNegative(factoryWhId, di.getMaterialId(), di.getQuantity().negate(),
                            StockChangeType.OUTSOURCE_RETURN_OUT.getCode(), order.getCode(),
                            RelatedBillType.OUTSOURCE_RETURN, returnDelivery.getId(), orderId, returnDelivery.getId());
                } else {
                    warehouseStockService.changeMaterialStock(factoryWhId, di.getMaterialId(), di.getQuantity().negate(),
                            StockChangeType.OUTSOURCE_RETURN_OUT.getCode(), order.getCode(),
                            RelatedBillType.OUTSOURCE_RETURN, returnDelivery.getId(), orderId);
                }
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
            oi.setMaterialTypeId(item.getMaterialTypeId());
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
            // E3 口径（2026-09-12）：outsource_other_io.status 一律用 DocStatus（与控制器/前端一致）；
            // 此前写 DeliveryStatus.CONFIRMED，前端按 DocStatus 渲染 → 状态列显示不出，且 /cancel 会误判为草稿
            io.setStatus(DocStatus.AUDITED.getCode());
            io.setRemark("加工厂遗失 - " + order.getCode());
            io.setCode(generateOtherIoCode());
            otherIoMapper.insert(io);

            for (OutsourceOtherIoItem oi : missingItems) {
                oi.setOtherIoId(io.getId());
                otherIoItemMapper.insert(oi);
                deductStockById(warehouses.get(0).getId(), oi.getMaterialId(), oi.getQuantity(), order.getCode(), orderId, force);
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
        // 原子抢占加工单状态（P2-29）：并发/双击反结单只会成功一次，避免重复冲回退料与超损应付
        if (!DocStatusGuard.claim(orderMapper, OutsourceOrder::getId, orderId, OutsourceOrder::getStatus,
                OutsourceOrderStatus.FINISHED.getCode(), OutsourceOrderStatus.PRODUCING.getCode()))
            throw new BusinessException("只有已完成的加工单可反结单");

        CloseReport report = reportMapper.selectOne(
            new LambdaQueryWrapper<CloseReport>().eq(CloseReport::getOrderId, orderId));
        if (report == null) throw new BusinessException("未找到结单报表");
        // 报表状态一并原子抢占（P2-29）：FINISHED→DRAFT，双重保护
        if (!DocStatusGuard.claim(reportMapper, CloseReport::getId, report.getId(), CloseReport::getStatus,
                CloseReportStatus.FINISHED.getCode(), CloseReportStatus.DRAFT.getCode()))
            throw new BusinessException("该订单尚未结单，无需反结单");

        // 1. 冲回超损应付（已付款会自动拦截）
        payableHelper.reversePayable(report.getId(), SourceBillType.OUTSOURCE_EXCESS_LOSS.getCode());

        // 2. 逆向退料单：作废 + 工厂仓加回、退回仓减回
        // （2026-09-16 起结单生成的是"调拨单"，故同时匹配历史 RETURN 与新的 TRANSFER）
        List<OutsourceDelivery> returnDeliveries = deliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceDelivery>()
                .eq(OutsourceDelivery::getSourceOrderId, orderId)
                .in(OutsourceDelivery::getDeliveryType, DeliveryType.RETURN.getCode(), DeliveryType.TRANSFER.getCode()));
        for (OutsourceDelivery rd : returnDeliveries) {
            if (DocStatus.CANCELLED.getCode().equals(rd.getStatus())) continue;
            List<OutsourceDeliveryItem> rItems = deliveryItemMapper.selectList(
                new LambdaQueryWrapper<OutsourceDeliveryItem>().eq(OutsourceDeliveryItem::getDeliveryId, rd.getId()));
            for (OutsourceDeliveryItem di : rItems) {
                // 工厂委外仓加回（结单时是减）
                // 2026-09-16 修复（问题①连带的死锁）：逆向腿必须用"允许负"口径 —— 严格口径的 SQL 带
                // quantity+delta>=0 护栏，账面为负时**连"往回加"都会被判"库存不足"**（实测：强制退料后反结单 500 卡死）
                warehouseStockService.changeMaterialStockAllowNegative(rd.getFromWarehouseId(), di.getMaterialId(), di.getQuantity(),
                        StockChangeType.OUTSOURCE_RETURN_OUT.getCode(), order.getCode(),
                        RelatedBillType.OUTSOURCE_RETURN, rd.getId(), orderId, rd.getId());
                // 退回仓减回（结单时是加）；同为纯回滚，与调拨反审核同口径（不因库存被消耗而拦死）
                warehouseStockService.changeMaterialStockAllowNegative(rd.getToWarehouseId(), di.getMaterialId(), di.getQuantity().negate(),
                        StockChangeType.SETTLEMENT_RETURN_IN.getCode(), order.getCode(),
                        RelatedBillType.OUTSOURCE_RETURN, rd.getId(), orderId, rd.getId());
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
            // E3 口径：仍用 DocStatus（与结单写入的 AUDITED 同体系）
            if (DocStatus.CANCELLED.getCode().equals(io.getStatus())) continue;
            List<OutsourceOtherIoItem> ioItems = otherIoItemMapper.selectList(
                new LambdaQueryWrapper<OutsourceOtherIoItem>().eq(OutsourceOtherIoItem::getOtherIoId, io.getId()));
            for (OutsourceOtherIoItem oi : ioItems) {
                // 缺失单逆向（加回工厂仓）：同"纯回滚"，用允许负口径（见上，避免负库存时被护栏拦死）
                warehouseStockService.changeMaterialStockAllowNegative(io.getWarehouseId(), oi.getMaterialId(), oi.getQuantity(),
                        StockChangeType.OUTSOURCE_RETURN_OUT.getCode(), order.getCode(),
                        RelatedBillType.OUTSOURCE_RETURN, io.getId(), orderId, io.getId());
            }
            // 作废缺失单
            OutsourceOtherIo updIo = new OutsourceOtherIo();
            updIo.setId(io.getId());
            updIo.setStatus(DocStatus.CANCELLED.getCode());
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
        di.setMaterialTypeId(item.getMaterialTypeId());
        di.setUnit(item.getUnit());
        di.setQuantity(qty);
        di.setQualityType(qualityType);
        return di;
    }

    /** 缺失扣库存：工厂委外仓减少（消耗）；force=true 时按强制出库口径（允许负数，与退料腿一致） */
    private void deductStockById(Long warehouseId, Long materialId, BigDecimal qty, String orderCode, Long orderId, boolean force) {
        if (materialId == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) return;
        if (force) {
            warehouseStockService.changeMaterialStockAllowNegative(warehouseId, materialId, qty.negate(),
                    StockChangeType.OUTSOURCE_RETURN_OUT.getCode(), orderCode,
                    RelatedBillType.OUTSOURCE_RETURN, null, orderId, null);
        } else {
            warehouseStockService.changeMaterialStock(warehouseId, materialId, qty.negate(),
                    StockChangeType.OUTSOURCE_RETURN_OUT.getCode(), orderCode,
                    RelatedBillType.OUTSOURCE_RETURN, null, orderId);
        }
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

    /** 根据 物料类型ID 查询类型名称，空安全返回 "-" */
    private String getMaterialTypeNameById(Long materialTypeId) {
        if (materialTypeId == null) return "-";
        MaterialType bt = materialTypeMapper.selectById(materialTypeId);
        return bt != null ? bt.getTypeName() : "-";
    }
}
