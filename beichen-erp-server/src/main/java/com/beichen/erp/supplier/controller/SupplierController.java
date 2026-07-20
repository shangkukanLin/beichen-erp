package com.beichen.erp.supplier.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.entity.SupplierProduct;
import com.beichen.erp.supplier.entity.dto.SupplierDTO;
import com.beichen.erp.supplier.entity.dto.SupplierProductDTO;
import com.beichen.erp.supplier.entity.dto.SupplierQueryDTO;
import com.beichen.erp.supplier.service.SupplierProductService;
import com.beichen.erp.supplier.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/supplier")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;
    private final SupplierProductService supplierProductService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/page")
    public R<Page<Supplier>> page(SupplierQueryDTO query) {
        return R.ok(supplierService.page(query));
    }

    @GetMapping("/{id}")
    public R<Supplier> getById(@PathVariable Long id) {
        return R.ok(supplierService.getById(id));
    }

    @GetMapping("/{id}/products")
    public R<List<SupplierProduct>> listProducts(@PathVariable Long id) {
        return R.ok(supplierProductService.listBySupplierId(id));
    }

    @PostMapping
    public R<Void> add(@Valid @RequestBody SupplierDTO dto) {
        supplierService.create(dto);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@Valid @RequestBody SupplierDTO dto) {
        supplierService.update(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return R.ok();
    }

    /** 检查供应商是否有关联数据，返回可删除标志和关联明细 */
    @GetMapping("/{id}/check-delete")
    public R<Map<String, Object>> checkDelete(@PathVariable Long id) {
        return R.ok(supplierService.checkDelete(id));
    }

    @PutMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable Long id) {
        supplierService.toggleStatus(id);
        return R.ok();
    }

    @PutMapping("/{id}/products")
    public R<Void> saveProducts(@PathVariable Long id, @RequestBody List<SupplierProductDTO> products) {
        supplierProductService.saveProducts(id, products);
        return R.ok();
    }

    /** 委外加工厂物料缺料汇总（加工单 + 物料订单子物料需求） */
    @GetMapping("/{factoryId}/material-summary")
    public R<Map<String, Object>> materialSummary(@PathVariable Long factoryId) {
        // 1. 汇总生产中加工单的物料需求
        String demandSql = "SELECT om.material_id, om.material_name, om.material_type, " +
            "SUM(om.demand_quantity) AS total_demand, " +
            "SUM(COALESCE(om.delivered_quantity,0)) AS total_delivered " +
            "FROM outsource_order_material om " +
            "INNER JOIN outsource_order_product op ON om.product_id = op.id " +
            "INNER JOIN outsource_order o ON op.order_id = o.id " +
            "WHERE o.factory_id = ? AND o.status = '生产中' " +
            "GROUP BY om.material_id, om.material_name, om.material_type";
        List<Map<String, Object>> demandRows = jdbcTemplate.queryForList(demandSql, factoryId);

        // 1b. 汇总物料订单的子物料需求（带 components 的物料单）
        String compDemandSql = "SELECT mc.child_material_id AS material_id, " +
            "COALESCE(cm.material_name, '未知物料') AS material_name, " +
            "COALESCE(cm.material_type, '') AS material_type, " +
            "SUM(moi.order_quantity * mc.quantity) AS total_demand " +
            "FROM outsource_material_component mc " +
            "INNER JOIN outsource_material_order_item moi ON moi.material_id = mc.parent_material_id " +
            "INNER JOIN outsource_material_order mo ON moi.order_id = mo.id " +
            "LEFT JOIN outsource_material cm ON mc.child_material_id = cm.id " +
            "WHERE mo.supplier_id = ? AND mo.status IN ('待确认', '已确认', '收货中') " +
            "GROUP BY mc.child_material_id, cm.material_name, cm.material_type";
        List<Map<String, Object>> compDemandRows = jdbcTemplate.queryForList(compDemandSql, factoryId);

        // 合并需求：按 material_name 汇总
        Map<String, Map<String, Object>> demandMap = new LinkedHashMap<>();
        for (Map<String, Object> row : demandRows) {
            String name = row.get("material_name") != null ? row.get("material_name").toString() : "";
            Map<String, Object> m = demandMap.computeIfAbsent(name, k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("materialId", row.get("material_id"));
                x.put("materialName", name);
                x.put("materialType", row.get("material_type"));
                x.put("totalDemand", BigDecimal.ZERO);
                x.put("totalDelivered", BigDecimal.ZERO);
                return x;
            });
            BigDecimal d = row.get("total_demand") != null ? (BigDecimal) row.get("total_demand") : BigDecimal.ZERO;
            BigDecimal dv = row.get("total_delivered") != null ? (BigDecimal) row.get("total_delivered") : BigDecimal.ZERO;
            m.put("totalDemand", ((BigDecimal) m.get("totalDemand")).add(d));
            m.put("totalDelivered", ((BigDecimal) m.get("totalDelivered")).add(dv));
        }
        for (Map<String, Object> row : compDemandRows) {
            String name = row.get("material_name") != null ? row.get("material_name").toString() : "";
            Map<String, Object> m = demandMap.computeIfAbsent(name, k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("materialId", row.get("material_id"));
                x.put("materialName", name);
                x.put("materialType", row.get("material_type"));
                x.put("totalDemand", BigDecimal.ZERO);
                x.put("totalDelivered", BigDecimal.ZERO);
                return x;
            });
            BigDecimal d = row.get("total_demand") != null ? (BigDecimal) row.get("total_demand") : BigDecimal.ZERO;
            m.put("totalDemand", ((BigDecimal) m.get("totalDemand")).add(d));
        }

        // 2. 汇总该工厂委外仓库的库存
        String stockSql = "SELECT s.material_id, SUM(s.quantity) AS stock_qty " +
            "FROM outsource_warehouse_stock s " +
            "INNER JOIN outsource_warehouse w ON s.warehouse_id = w.id " +
            "WHERE w.factory_id = ? GROUP BY s.material_id";
        List<Map<String, Object>> stockRows = jdbcTemplate.queryForList(stockSql, factoryId);
        Map<Long, BigDecimal> stockMap = new HashMap<>();
        for (Map<String, Object> row : stockRows) {
            Long mid = ((Number) row.get("material_id")).longValue();
            BigDecimal qty = (BigDecimal) row.get("stock_qty");
            stockMap.put(mid, qty != null ? qty : BigDecimal.ZERO);
        }

        // 3. 每个物料的订单明细（加工单）
        String orderSql = "SELECT om.material_name, o.code AS order_code, op.product_name, om.demand_quantity " +
            "FROM outsource_order_material om " +
            "INNER JOIN outsource_order_product op ON om.product_id = op.id " +
            "INNER JOIN outsource_order o ON op.order_id = o.id " +
            "WHERE o.factory_id = ? AND o.status = '生产中'";
        List<Map<String, Object>> orderRows = jdbcTemplate.queryForList(orderSql, factoryId);
        // 加上物料订单明细
        String moOrderSql = "SELECT cm.material_name, mo.code AS order_code, " +
            "CONCAT(moi.material_name, ' ×', moi.order_quantity) AS product_name, " +
            "(moi.order_quantity * mc.quantity) AS demand_quantity " +
            "FROM outsource_material_component mc " +
            "INNER JOIN outsource_material_order_item moi ON moi.material_id = mc.parent_material_id " +
            "INNER JOIN outsource_material_order mo ON moi.order_id = mo.id " +
            "LEFT JOIN outsource_material cm ON mc.child_material_id = cm.id " +
            "WHERE mo.supplier_id = ? AND mo.status IN ('待确认', '已确认', '收货中')";
        orderRows.addAll(jdbcTemplate.queryForList(moOrderSql, factoryId));

        Map<String, List<Map<String, Object>>> orderMap = new HashMap<>();
        for (Map<String, Object> row : orderRows) {
            String name = (String) row.get("material_name");
            orderMap.computeIfAbsent(name, k -> new ArrayList<>()).add(row);
        }

        // 4. 组装结果
        List<Map<String, Object>> materials = new ArrayList<>();
        for (Map<String, Object> dm : demandMap.values()) {
            String materialName = (String) dm.get("materialName");
            Long materialId = dm.get("materialId") != null ? ((Number) dm.get("materialId")).longValue() : null;
            BigDecimal totalDemand = (BigDecimal) dm.get("totalDemand");
            BigDecimal totalDelivered = (BigDecimal) dm.getOrDefault("totalDelivered", BigDecimal.ZERO);

            BigDecimal stock = materialId != null ? stockMap.getOrDefault(materialId, BigDecimal.ZERO) : BigDecimal.ZERO;
            BigDecimal gap = totalDemand.subtract(stock);
            if (gap.compareTo(BigDecimal.ZERO) < 0) gap = BigDecimal.ZERO;

            Map<String, Object> mat = new LinkedHashMap<>();
            mat.put("materialName", materialName);
            mat.put("materialType", dm.get("materialType"));
            mat.put("totalDemand", totalDemand);
            mat.put("totalDelivered", totalDelivered);
            mat.put("warehouseStock", stock);
            mat.put("gap", gap);
            mat.put("orders", orderMap.getOrDefault(materialName, Collections.emptyList()));
            materials.add(mat);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("materials", materials);
        return R.ok(result);
    }
}
