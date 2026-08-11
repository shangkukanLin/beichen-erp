package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceMaterialComponent;
import com.beichen.erp.outsource.entity.dto.SupplierMaterialDTO;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialComponentMapper;
import com.beichen.erp.outsource.service.SupplierMaterialService;
import lombok.RequiredArgsConstructor;
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
    private final BomTypeMapper bomTypeMapper;
    private final SupplierMaterialService supplierMaterialService;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Long bomTypeId,
            @RequestParam(required = false) Long warehouseId) {
        LambdaQueryWrapper<OutsourceMaterial> w = new LambdaQueryWrapper<OutsourceMaterial>()
                .like(materialName != null && !materialName.isBlank(), OutsourceMaterial::getMaterialName, materialName)
                .like(projectId != null && !projectId.isBlank(), OutsourceMaterial::getProjectIds, projectId)
                .eq(bomTypeId != null, OutsourceMaterial::getBomTypeId, bomTypeId)
                .eq(warehouseId != null, OutsourceMaterial::getWarehouseId, warehouseId)
                .orderByDesc(OutsourceMaterial::getId);
        Page<OutsourceMaterial> page = mapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(page.getRecords().stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("projectIds", m.getProjectIds());
            map.put("warehouseId", m.getWarehouseId());
            map.put("projectName", idsToNames(m.getProjectIds(), projectMapper));
            map.put("materialName", m.getMaterialName());
            map.put("bomTypeId", m.getBomTypeId());
            map.put("bomTypeName", getBomTypeNameById(m.getBomTypeId()));
            map.put("spec", m.getSpec());
            // supplierIds 统一由 supplier_material 居间表联查生成（弃用字段 outsource_material.supplier_ids）
            map.put("supplierIds", supplierMaterialService.listSupplierIdsByMaterial(m.getId()));
            map.put("unit", m.getUnit());
            map.put("status", m.getStatus());
            map.put("remark", m.getRemark());
            return map;
        }).toList());
        return R.ok(result);
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

    /** 根据 BOM 类型ID 查询类型名称，空安全返回 "-" */
    private String getBomTypeNameById(Long bomTypeId) {
        if (bomTypeId == null) return "-";
        BomType bt = bomTypeMapper.selectById(bomTypeId);
        return bt != null ? bt.getTypeName() : "-";
    }

    @PostMapping
    public R<Long> add(@RequestBody Map<String, Object> body) {
        OutsourceMaterial m = new OutsourceMaterial();
        fill(m, body);
        m.setUnit(body.get("unit") != null ? body.get("unit").toString() : "PCS");
        m.setStatus(1);
        mapper.insert(m);
        // 供应商关联统一写入 supplier_material 居间表（弃用 outsource_material.supplier_ids 字段）
        syncSupplierMaterials(m.getId(), body);
        return R.ok(m.getId());
    }

    @PutMapping
    public R<Void> update(@RequestBody Map<String, Object> body) {
        OutsourceMaterial m = new OutsourceMaterial();
        m.setId(Long.valueOf(body.get("id").toString()));
        fill(m, body);
        mapper.updateById(m);
        // 供应商关联统一写入 supplier_material 居间表（差量更新）
        syncSupplierMaterials(m.getId(), body);
        return R.ok();
    }

    /** 将前端传入的 supplierIds 逗号串同步到 supplier_material 居间表 */
    private void syncSupplierMaterials(Long materialId, Map<String, Object> body) {
        List<SupplierMaterialDTO> dtos = new ArrayList<>();
        Object idsObj = body.get("supplierIds");
        if (idsObj != null) {
            String ids = String.valueOf(idsObj);
            for (String sid : ids.split(",")) {
                sid = sid.trim();
                if (sid.isEmpty()) continue;
                try {
                    SupplierMaterialDTO dto = new SupplierMaterialDTO();
                    dto.setMaterialId(materialId);
                    dto.setSupplierId(Long.valueOf(sid));
                    dtos.add(dto);
                } catch (NumberFormatException ignore) {
                    // 跳过非数字项
                }
            }
        }
        supplierMaterialService.saveMaterialsByMaterial(materialId, dtos);
    }

    private void fill(OutsourceMaterial m, Map<String, Object> body) {
        m.setProjectIds(body.get("projectIds") != null ? body.get("projectIds").toString() : null);
        if (body.get("warehouseId") != null) m.setWarehouseId(Long.valueOf(body.get("warehouseId").toString()));
        m.setMaterialName((String) body.get("materialName"));
        // 仅存储 BOM 类型ID，类型名称在展示时关联 dev_bom_type 查名
        if (body.get("bomTypeId") != null) {
            m.setBomTypeId(Long.valueOf(body.get("bomTypeId").toString()));
        }
        m.setSpec((String) body.get("spec"));
        // 注意：supplierIds 不再写入 outsource_material 实体，改由 supplier_material 居间表维护
        m.setUnit(body.get("unit") != null ? body.get("unit").toString() : "PCS");
        m.setStatus(body.get("status") != null ? Integer.valueOf(body.get("status").toString()) : 1);
        m.setRemark((String) body.get("remark"));
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) m.setCompanyId(cid);
    }

    private final OutsourceMaterialComponentMapper compMapper;

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        // 级联删除子物料组成
        compMapper.delete(new LambdaQueryWrapper<OutsourceMaterialComponent>()
            .eq(OutsourceMaterialComponent::getParentMaterialId, id));
        mapper.deleteById(id);
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
        compMapper.delete(new LambdaQueryWrapper<OutsourceMaterialComponent>()
            .eq(OutsourceMaterialComponent::getParentMaterialId, materialId));
        if (items != null) {
            for (Map<String, Object> it : items) {
                OutsourceMaterialComponent c = new OutsourceMaterialComponent();
                c.setParentMaterialId(materialId);
                c.setChildMaterialId(Long.valueOf(it.get("childMaterialId").toString()));
                if (it.get("quantity") != null) c.setQuantity(new BigDecimal(it.get("quantity").toString()));
                if (it.get("lossRate") != null) c.setLossRate(new BigDecimal(it.get("lossRate").toString()));
                c.setRemark((String) it.get("remark"));
                compMapper.insert(c);
            }
        }
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
                    m.put("childType", child != null ? getBomTypeNameById(child.getBomTypeId()) : "");
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
