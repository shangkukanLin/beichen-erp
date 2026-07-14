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

    /** 委外加工厂物料缺料汇总 */
    @GetMapping("/{factoryId}/material-summary")
    public R<Map<String, Object>> materialSummary(@PathVariable Long factoryId) {
        // 1. 汇总生产中订单的物料需求（按 material_id + material_name 聚合）
        String demandSql = "SELECT om.material_id, om.material_name, om.material_type, " +
            "SUM(om.demand_quantity) AS total_demand, " +
            "SUM(COALESCE(om.delivered_quantity,0)) AS total_delivered " +
            "FROM outsource_order_material om " +
            "INNER JOIN outsource_order_product op ON om.product_id = op.id " +
            "INNER JOIN outsource_order o ON op.order_id = o.id " +
            "WHERE o.factory_id = ? AND o.status = '生产中' " +
            "GROUP BY om.material_id, om.material_name, om.material_type";
        List<Map<String, Object>> demandRows = jdbcTemplate.queryForList(demandSql, factoryId);

        // 2. 汇总该工厂委外仓库的库存（按 material_id）
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

        // 3. 每个物料的订单明细
        String orderSql = "SELECT om.material_name, o.code AS order_code, op.product_name, om.demand_quantity " +
            "FROM outsource_order_material om " +
            "INNER JOIN outsource_order_product op ON om.product_id = op.id " +
            "INNER JOIN outsource_order o ON op.order_id = o.id " +
            "WHERE o.factory_id = ? AND o.status = '生产中'";
        List<Map<String, Object>> orderRows = jdbcTemplate.queryForList(orderSql, factoryId);
        Map<String, List<Map<String, Object>>> orderMap = new HashMap<>();
        for (Map<String, Object> row : orderRows) {
            String name = (String) row.get("material_name");
            orderMap.computeIfAbsent(name, k -> new ArrayList<>()).add(row);
        }

        // 4. 组装结果
        List<Map<String, Object>> materials = new ArrayList<>();
        for (Map<String, Object> dm : demandRows) {
            String materialName = (String) dm.get("material_name");
            Long materialId = dm.get("material_id") != null ? ((Number) dm.get("material_id")).longValue() : null;
            BigDecimal totalDemand = (BigDecimal) dm.get("total_demand");
            BigDecimal totalDelivered = (BigDecimal) dm.get("total_delivered");
            if (totalDelivered == null) totalDelivered = BigDecimal.ZERO;

            BigDecimal stock = materialId != null ? stockMap.getOrDefault(materialId, BigDecimal.ZERO) : BigDecimal.ZERO;
            BigDecimal available = totalDelivered.add(stock);
            BigDecimal gap = totalDemand.subtract(available);
            if (gap.compareTo(BigDecimal.ZERO) < 0) gap = BigDecimal.ZERO;

            Map<String, Object> mat = new LinkedHashMap<>();
            mat.put("materialName", materialName);
            mat.put("materialType", dm.get("material_type"));
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
