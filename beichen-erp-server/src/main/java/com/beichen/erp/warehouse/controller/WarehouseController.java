package com.beichen.erp.warehouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.service.StockTakeService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.warehouse.common.WarehouseCategory;
import com.beichen.erp.warehouse.common.WarehouseType;
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
    // 期 2（2026-09-19 读隔离）：仓库管理页的「本月盘点 / 上次盘点」列改由本页接口提供
    private final StockTakeService stockTakeService;

    /**
     * 各仓库当月盘点状态与超期天数（仓库管理页表格用；scope 可过滤成品/物料范围）。
     * <p>原先该页直读 {@code /api/inventory/stock-take/status}（需 {@code stock:stock-take}）
     * ⇒ 只被授予 {@code stock:warehouse} 的用户会 403。现走本页前缀，并复用盘点模块的**同一查询**
     * （{@link StockTakeService#takeStatus}），口径与「仓库盘点」页完全一致。</p>
     */
    @GetMapping("/stock-take-status")
    public R<List<Map<String, Object>>> stockTakeStatus(@RequestParam(required = false) String scope) {
        return R.ok(stockTakeService.takeStatus(scope));
    }

    /**
     * 仓库分页查询，支持按名称、类别、类型、**所属供应商/加工厂**过滤。
     *
     * <p>2026-10-09（§7.26 幽灵字段）：补 `factoryId` —— 委外仓库管理页的「供应商」筛选一直把 `factoryId`
     * 当查询参数发过来，而本方法原先**没有这个形参** ⇒ Spring 静默忽略未知请求参数 ⇒ 该筛选**完全无效**
     * （列表看起来正常，因为 {@code toMap} 本来就回 {@code factoryId/factoryName}）。</p>
     */
    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String warehouseName,
            @RequestParam(required = false) String warehouseCategory,
            @RequestParam(required = false) String warehouseType,
            @RequestParam(required = false) Long factoryId) {
        Page<Warehouse> mpPage = warehouseMapper.selectPage(new Page<>(pageNum, pageSize),
            new LambdaQueryWrapper<Warehouse>()
                .like(warehouseName != null && !warehouseName.isBlank(), Warehouse::getWarehouseName, warehouseName)
                .eq(warehouseCategory != null && !warehouseCategory.isBlank(), Warehouse::getWarehouseCategory, warehouseCategory)
                .eq(warehouseType != null && !warehouseType.isBlank(), Warehouse::getWarehouseType, warehouseType)
                .eq(factoryId != null, Warehouse::getFactoryId, factoryId)
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
            rows.add(toMap(w, supplierNameMap));
        }

        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, mpPage.getTotal());
        result.setRecords(rows);
        return R.ok(result);
    }

    /**
     * F7-131（2026-09-20）：**按 id 精确查单个仓库**（含所属加工厂名）。
     *
     * <p>背景：委外仓库详情页（`outsource/warehouse-detail.vue`）原实现是"先请求 {@code /by-factory/{id}}
     * （该接口按 **factoryId** 过滤，这里传的是 warehouseId ⇒ 语义错、且返回值被直接丢弃）+ 再拉
     * {@code /page?pageSize=100} 在前端 {@code find} 出自己" ⇒ **仓库总数超过 100 时找不到 ⇒ 页面基础信息区静默空白**。
     * 本端点一次取单体，返回结构与 {@link #page} 的每一行**完全一致**（共用 {@link #toMap}）。</p>
     */
    @GetMapping("/{id}")
    public R<Map<String, Object>> getById(@PathVariable Long id) {
        Warehouse w = warehouseMapper.selectById(id);
        if (w == null) throw new BusinessException("仓库不存在");
        Map<Long, String> supplierNameMap = new HashMap<>();
        if (w.getFactoryId() != null) {
            var s = supplierMapper.selectById(w.getFactoryId());
            if (s != null) supplierNameMap.put(s.getId(), s.getName());
        }
        return R.ok(toMap(w, supplierNameMap));
    }

    /** 仓库实体 → 列表/详情共用的 Map（唯一实现，避免两处拼装漂移） */
    private Map<String, Object> toMap(Warehouse w, Map<Long, String> supplierNameMap) {
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
        return m;
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
        // 仓型（2026-09-16 方案 A）：仅**自有仓必填**（下拉按仓型过滤）；委外仓一律不写仓型；
        // 且仓型必须在枚举内 —— 防止接口绕过写入已取消的 DEFECT/AFTER_SALE
        if (WarehouseCategory.OUTSOURCE.getCode().equals(w.getWarehouseCategory())) {
            w.setWarehouseType(null);
        } else {
            if (w.getWarehouseType() == null || w.getWarehouseType().isBlank()) throw new BusinessException("仓型不能为空");
            assertValidType(w.getWarehouseType());
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
        // 显式传空串=要把仓型清空，需拦截；未传(null)视为不修改，由 MP 忽略
        if (w.getWarehouseType() != null) {
            if (w.getWarehouseType().isBlank()) throw new BusinessException("仓型不能为空");
            assertValidType(w.getWarehouseType());
        }
        warehouseMapper.updateById(w);
        return R.ok();
    }

    /** 仓型必须在枚举内（2026-09-16 方案 A：仓型收敛为 成品仓/辅料仓，防止接口绕过写入已取消的 DEFECT/AFTER_SALE） */
    private void assertValidType(String type) {
        try {
            WarehouseType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("仓型非法：" + type);
        }
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

        // F7-126（2026-09-20）：原此处还有一条
        //   `SELECT COUNT(*) FROM outsource_material WHERE warehouse_id = ?`
        // —— 但 `outsource_material.warehouse_id` 是**死列**（实体无该字段；现网 30 行全 NULL），
        // 该检查**恒为 0、形同虚设**（会让人误以为"物料与仓库有关联"）⇒ 随 DDL 删列一并移除。
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
