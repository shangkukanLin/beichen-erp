package com.beichen.erp.warehouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.warehouse.common.WarehouseCategory;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统一仓库管理 Controller（合并 inventory_warehouse + outsource_warehouse）
 * <p>路由前缀: /api/warehouse</p>
 */
@RestController
@RequestMapping("/api/warehouse")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseMapper warehouseMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SupplierMapper supplierMapper;

    /** 仓库分页查询，支持按名称、类别、类型过滤 */
    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String warehouseName,
            @RequestParam(required = false) String warehouseCategory,
            @RequestParam(required = false) String warehouseType) {
        Page<Warehouse> mpPage = warehouseMapper.selectPage(new Page<>(pageNum, pageSize),
            new LambdaQueryWrapper<Warehouse>()
                .like(warehouseName != null && !warehouseName.isBlank(), Warehouse::getWarehouseName, warehouseName)
                .eq(warehouseCategory != null && !warehouseCategory.isBlank(), Warehouse::getWarehouseCategory, warehouseCategory)
                .eq(warehouseType != null && !warehouseType.isBlank(), Warehouse::getWarehouseType, warehouseType)
                .orderByDesc(Warehouse::getId));

        // 批量查询供应商名称
        Set<Long> factoryIds = mpPage.getRecords().stream()
                .map(Warehouse::getFactoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> supplierNameMap = new HashMap<>();
        if (!factoryIds.isEmpty()) {
            supplierMapper.selectBatchIds(factoryIds).forEach(s -> supplierNameMap.put(s.getId(), s.getName()));
        }

        // 转为 Map 列表，补充 supplierName
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Warehouse w : mpPage.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.getId());
            m.put("code", w.getCode());
            m.put("warehouseName", w.getWarehouseName());
            m.put("warehouseCategory", w.getWarehouseCategory());
            m.put("warehouseType", w.getWarehouseType());
            m.put("factoryId", w.getFactoryId());
            m.put("factoryName", w.getFactoryId() != null ? supplierNameMap.getOrDefault(w.getFactoryId(), "") : "");
            m.put("address", w.getAddress());
            m.put("contact", w.getContact());
            m.put("phone", w.getPhone());
            m.put("status", w.getStatus());
            m.put("remark", w.getRemark());
            m.put("companyId", w.getCompanyId());
            m.put("createTime", w.getCreateTime());
            m.put("updateTime", w.getUpdateTime());
            rows.add(m);
        }

        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, mpPage.getTotal());
        result.setRecords(rows);
        return R.ok(result);
    }

    /** 新增仓库（成品仓库管理页默认自有仓库） */
    @PostMapping
    public R<Void> add(@RequestBody Warehouse w) {
        if (w.getCode() == null || w.getCode().isBlank()) {
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            // 取当日最大序号 + 1（避免用 selectCount 导致删除后编号空洞撞唯一索引）
            w.setCode(BillPrefix.WAREHOUSE + date + String.format("%03d", nextWarehouseSeq(date)));
        }
        // 未指定仓库类别时默认为自有仓库（委外仓库由供应商创建时显式设为 OUTSOURCE）
        if (w.getWarehouseCategory() == null || w.getWarehouseCategory().isBlank()) {
            w.setWarehouseCategory(WarehouseCategory.INVENTORY.getCode());
        }
        if (w.getStatus() == null) w.setStatus(1);
        warehouseMapper.insert(w);
        return R.ok();
    }

    /** 计算当日仓库编码最大序号 + 1（避免删除后编号空洞撞唯一索引） */
    private int nextWarehouseSeq(String date) {
        List<Warehouse> list = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().likeRight(Warehouse::getCode, BillPrefix.WAREHOUSE + date)
                        .orderByDesc(Warehouse::getCode).last("LIMIT 1"));
        if (list == null || list.isEmpty() || list.get(0).getCode() == null) return 1;
        String code = list.get(0).getCode();
        try {
            return Integer.parseInt(code.substring(code.length() - 3)) + 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** 编辑仓库 */
    @PutMapping
    public R<Void> update(@RequestBody Warehouse w) {
        warehouseMapper.updateById(w);
        return R.ok();
    }

    /** 删除仓库（检查关联数据） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Map<String, Object> check = checkDelete(id).getData();
        if (!(Boolean) check.get("canDelete")) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> associations = (Map<String, Integer>) check.get("associations");
            StringBuilder sb = new StringBuilder("该仓库有关联数据，无法删除：");
            associations.forEach((k, v) -> sb.append("\n  - ").append(k).append("：").append(v).append("条"));
            throw new BusinessException(sb.toString());
        }
        warehouseMapper.deleteById(id);
        return R.ok();
    }

    /** 检查仓库是否可删除 */
    @GetMapping("/{id}/check-delete")
    public R<Map<String, Object>> checkDelete(@PathVariable Long id) {
        Map<String, Integer> associations = new LinkedHashMap<>();

        int cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM purchase_order WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("采购订单", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sale_order WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("销售订单", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sale_outbound WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("销售出库单", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("库存记录", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM warehouse_stock_log WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("库存流水", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_warehouse_move WHERE from_warehouse_id = ? OR to_warehouse_id = ?", Integer.class, id, id);
        if (cnt > 0) associations.put("移仓单", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_other_io WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("其他出入库单", cnt);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_order_delivery WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.merge("委外加工", cnt, Integer::sum);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_material WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.merge("委外加工", cnt, Integer::sum);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_delivery WHERE from_warehouse_id = ? OR to_warehouse_id = ?", Integer.class, id, id);
        if (cnt > 0) associations.merge("委外加工", cnt, Integer::sum);

        cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dev_purchase_item WHERE warehouse_id = ?", Integer.class, id);
        if (cnt > 0) associations.put("研发物料", cnt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canDelete", associations.isEmpty());
        result.put("associations", associations);
        return R.ok(result);
    }

    /** 按加工厂ID查询委外仓库 */
    @GetMapping("/by-factory/{factoryId}")
    public R<List<Warehouse>> byFactory(@PathVariable Long factoryId) {
        return R.ok(warehouseMapper.selectList(
            new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getFactoryId, factoryId)
                .eq(Warehouse::getWarehouseCategory, WarehouseCategory.OUTSOURCE.getCode())));
    }

    /** 查询所有启用的自有仓库 */
    @GetMapping("/inventory")
    public R<List<Warehouse>> inventory() {
        return R.ok(warehouseMapper.selectList(
            new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getWarehouseCategory, WarehouseCategory.INVENTORY.getCode())
                .eq(Warehouse::getStatus, 1)
                .orderByAsc(Warehouse::getId)));
    }
}
