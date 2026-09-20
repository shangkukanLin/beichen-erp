package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.MaterialType;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.mapper.MaterialTypeMapper;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceMaterialComponent;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialComponentMapper;
import com.beichen.erp.outsource.service.OutsourceMaterialService;
import com.beichen.erp.outsource.service.SupplierMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/outsource/material")
@RequiredArgsConstructor
public class OutsourceMaterialController {

    private final OutsourceMaterialMapper mapper;
    private final ProjectMapper projectMapper;
    private final MaterialTypeMapper materialTypeMapper;
    private final SupplierMaterialService supplierMaterialService;
    private final OutsourceMaterialService materialService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Long materialTypeId) {
        LambdaQueryWrapper<OutsourceMaterial> w = new LambdaQueryWrapper<OutsourceMaterial>()
                .like(materialName != null && !materialName.isBlank(), OutsourceMaterial::getMaterialName, materialName)
                .like(projectId != null && !projectId.isBlank(), OutsourceMaterial::getProjectIds, projectId)
                .eq(materialTypeId != null, OutsourceMaterial::getMaterialTypeId, materialTypeId)
                .orderByDesc(OutsourceMaterial::getId);
        Page<OutsourceMaterial> page = mapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, page.getTotal());
        // 当前页物料的两个聚合：全仓库存总和、交货中订单的未交数量总和（批量查询避免 N+1）
        List<Long> pageMaterialIds = page.getRecords().stream().map(OutsourceMaterial::getId).toList();
        Map<Long, BigDecimal> stockTotalMap = aggregateStockTotal(pageMaterialIds);
        Map<Long, BigDecimal> undeliveredMap = aggregateUndelivered(pageMaterialIds);
        result.setRecords(page.getRecords().stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("projectIds", m.getProjectIds());
            map.put("projectName", idsToNames(m.getProjectIds(), projectMapper));
            map.put("materialName", m.getMaterialName());
            map.put("materialTypeId", m.getMaterialTypeId());
            map.put("materialTypeName", getMaterialTypeNameById(m.getMaterialTypeId()));
            map.put("spec", m.getSpec());
            // supplierIds 统一由 supplier_material 居间表联查生成（弃用字段 outsource_material.supplier_ids）
            map.put("supplierIds", supplierMaterialService.listSupplierIdsByMaterial(m.getId()));
            map.put("unit", m.getUnit());
            map.put("status", m.getStatus());
            map.put("remark", m.getRemark());
            map.put("price", m.getPrice());
            map.put("stockTotal", stockTotalMap.getOrDefault(m.getId(), BigDecimal.ZERO));
            map.put("undeliveredTotal", undeliveredMap.getOrDefault(m.getId(), BigDecimal.ZERO));
            return map;
        }).toList());
        return R.ok(result);
    }

    /** 全部仓库的库存数量总和（不分品质/仓库，物料维度汇总） */
    private Map<Long, BigDecimal> aggregateStockTotal(List<Long> materialIds) {
        if (materialIds.isEmpty()) return Collections.emptyMap();
        String in = String.join(",", materialIds.stream().map(String::valueOf).toList());
        Map<Long, BigDecimal> map = new HashMap<>();
        jdbcTemplate.query("SELECT material_id, SUM(quantity) AS total FROM warehouse_stock WHERE material_id IN (" + in + ") GROUP BY material_id",
            rs -> { map.put(rs.getLong("material_id"), rs.getBigDecimal("total")); });
        return map;
    }

    /** 交货中（RECEIVING）订单的未交数量总和 = Σ(订购量 - 已收量)，仅累计未交完的明细 */
    private Map<Long, BigDecimal> aggregateUndelivered(List<Long> materialIds) {
        if (materialIds.isEmpty()) return Collections.emptyMap();
        String receiving = com.beichen.erp.outsource.common.MaterialOrderStatus.RECEIVING.getCode();
        String in = String.join(",", materialIds.stream().map(String::valueOf).toList());
        Map<Long, BigDecimal> map = new HashMap<>();
        jdbcTemplate.query(
            "SELECT moi.outsource_material_id AS mid, SUM(moi.order_quantity - IFNULL(moi.received_quantity, 0)) AS undelivered " +
            "FROM outsource_material_order_item moi " +
            "INNER JOIN outsource_material_order mo ON moi.order_id = mo.id " +
            "WHERE moi.deleted = 0 AND mo.status = '" + receiving + "' " +
            "AND moi.outsource_material_id IN (" + in + ") " +
            "AND moi.order_quantity > IFNULL(moi.received_quantity, 0) " +
            "GROUP BY moi.outsource_material_id",
            rs -> { map.put(rs.getLong("mid"), rs.getBigDecimal("undelivered")); });
        return map;
    }

    private String idsToNames(String ids, ProjectMapper projectMapper) {
        if (ids == null || ids.isBlank()) return "";
        return Arrays.stream(ids.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(id -> {
                    try {
                        Project p = projectMapper.selectById(Long.valueOf(id));
                        return p != null ? p.getName() : id;
                    } catch (Exception e) {
                        return id;
                    }
                }).collect(Collectors.joining(", "));
    }

    /** 根据 物料类型ID 查询类型名称，空安全返回 "-" */
    private String getMaterialTypeNameById(Long materialTypeId) {
        if (materialTypeId == null) return "-";
        MaterialType bt = materialTypeMapper.selectById(materialTypeId);
        return bt != null ? bt.getTypeName() : "-";
    }

    @PostMapping
    public R<Long> add(@RequestBody Map<String, Object> body) {
        // F7-70（2026-09-20）：写路径下沉到 OutsourceMaterialService（主表 + 供应商居间表进同一事务）
        return R.ok(materialService.create(body));
    }

    @PutMapping
    public R<Void> update(@RequestBody Map<String, Object> body) {
        materialService.update(body);
        return R.ok();
    }

    private final OutsourceMaterialComponentMapper compMapper;

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        // F7-70（2026-09-20）：下沉到 Service —— 事务内"引用校验 + 级联删子物料组成 + 删物料"
        materialService.delete(id);
        return R.ok();
    }

    /** 获取物料的子物料组成 */
    @GetMapping("/{materialId}/components")
    public R<Object> getComponents(@PathVariable Long materialId) {
        List<OutsourceMaterialComponent> comps = compMapper.selectList(
            new LambdaQueryWrapper<OutsourceMaterialComponent>()
                .eq(OutsourceMaterialComponent::getParentMaterialId, materialId));
        // 附带子物料名称
        return R.ok(comps.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("childMaterialId", c.getChildMaterialId());
            m.put("quantity", c.getQuantity());
            m.put("lossRate", c.getLossRate());
            m.put("remark", c.getRemark());
            OutsourceMaterial child = mapper.selectById(c.getChildMaterialId());
            m.put("childName", child != null ? child.getMaterialName() : "");
            return m;
        }).toList());
    }

    /** 保存物料的子物料组成（全量替换） */
    @PutMapping("/{materialId}/components")
    public R<Void> saveComponents(@PathVariable Long materialId, @RequestBody List<Map<String, Object>> items) {
        // F7-70（2026-09-20）：下沉到 Service —— 事务内"全量替换"（原先删+插分两步且无事务）
        materialService.saveComponents(materialId, items);
        return R.ok();
    }

    /** 批量查询：按物料ID获取子物料和物料名，返回 {childrenMap: Map<id, 子物料列表>, nameMap: Map<id, 物料名>} */
    @PostMapping("/components-batch-by-ids")
    public R<Map<String, Object>> componentsBatchByIds(@RequestBody List<Long> ids) {
        Map<String, Object> childrenMap = new LinkedHashMap<>();
        Map<String, Object> nameMap = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("childrenMap", childrenMap);
            result.put("nameMap", nameMap);
            return R.ok(result);
        }
        // 按 ID 批量查询物料，构建 id -> 物料名 映射
        List<OutsourceMaterial> materials = mapper.selectBatchIds(ids);
        for (OutsourceMaterial m : materials) {
            nameMap.put(String.valueOf(m.getId()), m.getMaterialName());
        }
        // 查询每个物料的子物料
        for (Long id : ids) {
            List<OutsourceMaterialComponent> comps = compMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialComponent>()
                    .eq(OutsourceMaterialComponent::getParentMaterialId, id));
            if (!comps.isEmpty()) {
                childrenMap.put(String.valueOf(id), comps.stream().map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("childMaterialId", c.getChildMaterialId());
                    OutsourceMaterial child = mapper.selectById(c.getChildMaterialId());
                    m.put("childName", child != null ? child.getMaterialName() : "");
                    m.put("childType", child != null ? getMaterialTypeNameById(child.getMaterialTypeId()) : "");
                    m.put("quantity", c.getQuantity());
                    m.put("lossRate", c.getLossRate());
                    m.put("remark", c.getRemark());
                    return m;
                }).toList());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("childrenMap", childrenMap);
        result.put("nameMap", nameMap);
        return R.ok(result);
    }
}
