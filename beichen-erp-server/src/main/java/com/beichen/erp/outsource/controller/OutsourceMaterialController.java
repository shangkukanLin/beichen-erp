package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.MaterialType;
import com.beichen.erp.dev.mapper.MaterialTypeMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceMaterialComponent;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialComponentMapper;
import com.beichen.erp.outsource.service.OutsourceMaterialService;
import com.beichen.erp.outsource.service.SupplierMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/outsource/material")
@RequiredArgsConstructor
public class OutsourceMaterialController {

    private final OutsourceMaterialMapper mapper;
    private final MaterialTypeMapper materialTypeMapper;
    private final SupplierMaterialService supplierMaterialService;
    private final OutsourceMaterialService materialService;

    /**
     * 分页查询。
     * <p>2026-09-21（用户：「物料信息管理的列表，去掉库存/未交」+「不要所属项目了，这个字段没什么用」）
     * 随列表改版一并清理，不再返回/计算以下 4 个字段：</p>
     * <ul>
     *   <li>{@code projectIds} / {@code projectName}：「所属项目」整字段下线（现网 30 行填充率 0%），
     *       实体字段与 DB 列已同步移除；删列前的备份见
     *       {@code tools/db-archive/before-drop-outsource-material-project-ids.txt}；</li>
     *   <li>{@code stockTotal} / {@code undeliveredTotal}：列表已不再展示这两列。</li>
     * </ul>
     * <p>⇒ 附带收益：本接口**每次分页请求少跑 2 条聚合 SQL**（全仓库存合计 / 交货中未交合计），
     * 所有调用方（供应商详情的供应物料、销售出库物料下拉、研发项目 BOM 下拉等）一并受益。</p>
     */
    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) Long materialTypeId) {
        LambdaQueryWrapper<OutsourceMaterial> w = new LambdaQueryWrapper<OutsourceMaterial>()
                .like(materialName != null && !materialName.isBlank(), OutsourceMaterial::getMaterialName, materialName)
                .eq(materialTypeId != null, OutsourceMaterial::getMaterialTypeId, materialTypeId)
                .orderByDesc(OutsourceMaterial::getId);
        Page<OutsourceMaterial> page = mapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(page.getRecords().stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("materialName", m.getMaterialName());
            map.put("materialTypeId", m.getMaterialTypeId());
            map.put("materialTypeName", getMaterialTypeNameById(m.getMaterialTypeId()));
            // F7-125（2026-09-20）：不再返回 `spec`（规格已全站下线，实体字段与 DB 列同步移除）
            // supplierIds 统一由 supplier_material 居间表联查生成（弃用字段 outsource_material.supplier_ids）
            map.put("supplierIds", supplierMaterialService.listSupplierIdsByMaterial(m.getId()));
            map.put("unit", m.getUnit());
            map.put("status", m.getStatus());
            map.put("remark", m.getRemark());
            map.put("price", m.getPrice());
            return map;
        }).toList());
        return R.ok(result);
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

    /**
     * 为物料登记一笔「研发支出」**草稿**费用单（2026-09-27 用户需求：新增物料时提示"要不要根据物料新增研发支出"）。
     *
     * <p>由「新增物料」保存成功后调用（勾选时）；**只落草稿**，资金在「财务管理 → 费用管理」审核时才动。
     * 幂等：同一物料已有未作废的研发支出 ⇒ 直接返回原单（{@code existing=true}），不重复建。
     * 权限走本前缀（{@code outsource:material-info}），故物料页用户无需财务权限。</p>
     */
    @PostMapping("/{materialId}/rd-expense")
    public R<Map<String, Object>> createRdExpense(@PathVariable Long materialId,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        return R.ok(materialService.createRdExpense(materialId, body));
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
