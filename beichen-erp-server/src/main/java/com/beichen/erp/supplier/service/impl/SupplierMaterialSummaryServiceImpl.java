package com.beichen.erp.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.supplier.service.SupplierMaterialSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商（加工厂）委外物料缺料汇总实现
 * 统一以 outsource_material_id 为聚合口径，避免同名物料被错误合并
 */
@Slf4j
@Service
public class SupplierMaterialSummaryServiceImpl implements SupplierMaterialSummaryService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private OutsourceMaterialMapper outsourceMaterialMapper;

    @Resource
    private BomTypeMapper bomTypeMapper;

    @Override
    public Map<String, Object> materialSummary(Long factoryId) {
        // 1. 汇总生产中加工单的物料需求
        String demandSql = "SELECT om.outsource_material_id, SUM(om.demand_quantity) AS total_demand " +
                "FROM outsource_order_material om " +
                "INNER JOIN outsource_order_product op ON om.product_id = op.id " +
                "INNER JOIN outsource_order o ON op.order_id = o.id " +
                "WHERE o.factory_id = ? AND o.status = '生产中' " +
                "GROUP BY om.outsource_material_id";
        List<Map<String, Object>> demandRows = jdbcTemplate.queryForList(demandSql, factoryId);

        // 1b. 汇总物料订单的子物料需求
        String compDemandSql = "SELECT mc.child_outsource_material_id AS material_id, " +
                "SUM(moi.order_quantity * mc.quantity) AS total_demand " +
                "FROM outsource_material_component mc " +
                "INNER JOIN outsource_material_order_item moi ON moi.outsource_material_id = mc.parent_outsource_material_id " +
                "INNER JOIN outsource_material_order mo ON moi.order_id = mo.id " +
                "WHERE mo.supplier_id = ? AND mo.status IN ('待确认', '已确认', '收货中') " +
                "GROUP BY mc.child_outsource_material_id";
        List<Map<String, Object>> compDemandRows = jdbcTemplate.queryForList(compDemandSql, factoryId);

        // 合并需求：按 materialId 汇总
        Map<Long, BigDecimal> demandMap = new LinkedHashMap<>();
        for (Map<String, Object> row : demandRows) {
            Long mid = toLong(row.get("outsource_material_id"));
            if (mid == null) continue;
            demandMap.merge(mid, toBigDecimal(row.get("total_demand")), BigDecimal::add);
        }
        for (Map<String, Object> row : compDemandRows) {
            Long mid = toLong(row.get("material_id"));
            if (mid == null) continue;
            demandMap.merge(mid, toBigDecimal(row.get("total_demand")), BigDecimal::add);
        }

        // 1c. 已送料：收发单发料/收料到该厂仓（按 material_id 聚合）
        String deliveredSql = "SELECT di.material_id, SUM(di.quantity) AS delivered_qty " +
                "FROM outsource_delivery_item di " +
                "INNER JOIN outsource_delivery d ON di.delivery_id = d.id " +
                "INNER JOIN warehouse w ON d.to_warehouse_id = w.id " +
                "WHERE w.factory_id = ? AND d.status = '已确认' " +
                "AND (d.delivery_type IN ('发料', '收料') OR d.delivery_type IS NULL OR d.delivery_type = '') " +
                "GROUP BY di.material_id";
        List<Map<String, Object>> deliveredRows = jdbcTemplate.queryForList(deliveredSql, factoryId);
        Map<Long, BigDecimal> deliveredMap = new LinkedHashMap<>();
        for (Map<String, Object> row : deliveredRows) {
            Long mid = toLong(row.get("material_id"));
            if (mid == null) continue;
            deliveredMap.merge(mid, toBigDecimal(row.get("delivered_qty")), BigDecimal::add);
        }

        // 2. 该厂委外仓库库存（按 material_id 聚合）
        String stockSql = "SELECT s.material_id, SUM(s.quantity) AS stock_qty " +
                "FROM warehouse_stock s " +
                "INNER JOIN warehouse w ON s.warehouse_id = w.id " +
                "WHERE w.factory_id = ? GROUP BY s.material_id";
        List<Map<String, Object>> stockRows = jdbcTemplate.queryForList(stockSql, factoryId);
        Map<Long, BigDecimal> stockMap = new LinkedHashMap<>();
        for (Map<String, Object> row : stockRows) {
            Long mid = toLong(row.get("material_id"));
            if (mid == null) continue;
            stockMap.merge(mid, toBigDecimal(row.get("stock_qty")), BigDecimal::add);
        }

        // 3. 每个物料的订单明细（加工单 + 物料订单，按 materialId 关联）
        String orderSql = "SELECT om.outsource_material_id AS material_id, o.code AS order_code, op.product_name, om.demand_quantity " +
                "FROM outsource_order_material om " +
                "INNER JOIN outsource_order_product op ON om.product_id = op.id " +
                "INNER JOIN outsource_order o ON op.order_id = o.id " +
                "WHERE o.factory_id = ? AND o.status = '生产中'";
        List<Map<String, Object>> orderRows = jdbcTemplate.queryForList(orderSql, factoryId);
        String moOrderSql = "SELECT mc.child_outsource_material_id AS material_id, mo.code AS order_code, " +
                "CONCAT(moi.material_name, ' ×', moi.order_quantity) AS product_name, " +
                "(moi.order_quantity * mc.quantity) AS demand_quantity " +
                "FROM outsource_material_component mc " +
                "INNER JOIN outsource_material_order_item moi ON moi.outsource_material_id = mc.parent_outsource_material_id " +
                "INNER JOIN outsource_material_order mo ON moi.order_id = mo.id " +
                "LEFT JOIN outsource_material cm ON mc.child_outsource_material_id = cm.id " +
                "WHERE mo.supplier_id = ? AND mo.status IN ('待确认', '已确认', '收货中')";
        orderRows.addAll(jdbcTemplate.queryForList(moOrderSql, factoryId));
        Map<Long, List<Map<String, Object>>> orderMap = new LinkedHashMap<>();
        for (Map<String, Object> row : orderRows) {
            Long mid = toLong(row.get("material_id"));
            if (mid == null) continue;
            orderMap.computeIfAbsent(mid, k -> new ArrayList<>()).add(row);
        }

        // 4. 批量预取物料名称与 BOM 类型名称（避免 N+1）
        Set<Long> allIds = new java.util.HashSet<>();
        allIds.addAll(demandMap.keySet());
        allIds.addAll(deliveredMap.keySet());
        allIds.addAll(stockMap.keySet());
        allIds.addAll(orderMap.keySet());
        Map<Long, String> materialNameMap = new LinkedHashMap<>();
        Map<Long, Long> materialBomMap = new LinkedHashMap<>();
        if (!allIds.isEmpty()) {
            List<Long> ids = new ArrayList<>(allIds);
            Map<Long, OutsourceMaterial> matEntities =
                    outsourceMaterialMapper.selectBatchIds(ids).stream()
                            .collect(Collectors.toMap(OutsourceMaterial::getId, m -> m, (a, b) -> a));
            for (Long id : ids) {
                OutsourceMaterial m = matEntities.get(id);
                materialNameMap.put(id, m != null ? m.getMaterialName() : "未知物料");
                materialBomMap.put(id, m != null ? m.getBomTypeId() : null);
            }
        }
        // BOM 类型名批量查
        Set<Long> bomIds = materialBomMap.values().stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> bomNameMap = new LinkedHashMap<>();
        if (!bomIds.isEmpty()) {
            List<BomType> boms = bomTypeMapper.selectBatchIds(new ArrayList<>(bomIds));
            for (BomType b : boms) {
                bomNameMap.put(b.getId(), b.getTypeName());
            }
        }

        // 5. 组装结果：缺口 = 需求 - 已送料（统一按 materialId 计算）
        List<Map<String, Object>> materials = new ArrayList<>();
        Set<Long> allMaterialIds = new java.util.HashSet<>(demandMap.keySet());
        allMaterialIds.addAll(stockMap.keySet());
        allMaterialIds.addAll(orderMap.keySet());
        allMaterialIds.addAll(deliveredMap.keySet());
        for (Long mid : allMaterialIds) {
            BigDecimal totalDemand = demandMap.getOrDefault(mid, BigDecimal.ZERO);
            BigDecimal totalDelivered = deliveredMap.getOrDefault(mid, BigDecimal.ZERO);
            BigDecimal stock = stockMap.getOrDefault(mid, BigDecimal.ZERO);

            BigDecimal consumed = totalDelivered.subtract(stock);
            if (consumed.compareTo(BigDecimal.ZERO) < 0) consumed = BigDecimal.ZERO;
            BigDecimal gap = totalDemand.subtract(stock);
            if (gap.compareTo(BigDecimal.ZERO) < 0) gap = BigDecimal.ZERO;

            Map<String, Object> mat = new LinkedHashMap<>();
            mat.put("materialId", mid);
            mat.put("materialName", materialNameMap.getOrDefault(mid, "未知物料"));
            Long bomTypeId = materialBomMap.get(mid);
            mat.put("bomTypeId", bomTypeId);
            mat.put("bomTypeName", bomTypeId != null ? bomNameMap.getOrDefault(bomTypeId, "-") : "-");
            mat.put("totalDemand", totalDemand);
            mat.put("totalDelivered", totalDelivered);
            mat.put("warehouseStock", stock);
            mat.put("consumed", consumed);
            mat.put("gap", gap);
            mat.put("orders", orderMap.getOrDefault(mid, java.util.Collections.emptyList()));
            materials.add(mat);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("materials", materials);
        return result;
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        try {
            return new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
