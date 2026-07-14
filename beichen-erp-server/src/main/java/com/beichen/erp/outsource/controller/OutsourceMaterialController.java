package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceMaterialComponent;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialComponentMapper;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
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
    private final SupplierMapper supplierMapper;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String materialType,
            @RequestParam(required = false) Long warehouseId) {
        LambdaQueryWrapper<OutsourceMaterial> w = new LambdaQueryWrapper<OutsourceMaterial>()
                .like(materialName != null && !materialName.isBlank(), OutsourceMaterial::getMaterialName, materialName)
                .like(projectId != null && !projectId.isBlank(), OutsourceMaterial::getProjectIds, projectId)
                .like(materialType != null && !materialType.isBlank(), OutsourceMaterial::getMaterialType, materialType)
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
            map.put("materialType", m.getMaterialType());
            map.put("spec", m.getSpec());
            map.put("supplierName", m.getSupplierName());
            map.put("supplierIds", m.getSupplierIds());
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

    @PostMapping
    public R<Long> add(@RequestBody Map<String, Object> body) {
        OutsourceMaterial m = new OutsourceMaterial();
        fill(m, body);
        m.setUnit(body.get("unit") != null ? body.get("unit").toString() : "PCS");
        m.setStatus(1);
        mapper.insert(m);
        return R.ok(m.getId());
    }

    @PutMapping
    public R<Void> update(@RequestBody Map<String, Object> body) {
        OutsourceMaterial m = new OutsourceMaterial();
        m.setId(Long.valueOf(body.get("id").toString()));
        fill(m, body);
        mapper.updateById(m);
        return R.ok();
    }

    private void fill(OutsourceMaterial m, Map<String, Object> body) {
        m.setProjectIds(body.get("projectIds") != null ? body.get("projectIds").toString() : null);
        if (body.get("warehouseId") != null) m.setWarehouseId(Long.valueOf(body.get("warehouseId").toString()));
        m.setMaterialName((String) body.get("materialName"));
        m.setMaterialType((String) body.get("materialType"));
        m.setSpec((String) body.get("spec"));
        m.setSupplierName((String) body.get("supplierName"));
        m.setSupplierIds(body.get("supplierIds") != null ? body.get("supplierIds").toString() : null);
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

    /** 批量查询：按物料名获取子物料，返回 Map<物料名, 子物料列表> */
    @PostMapping("/components-batch")
    public R<Map<String, Object>> componentsBatch(@RequestBody List<String> names) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : names) {
            Long id = mapper.findIdByName(name);
            if (id != null) {
                List<OutsourceMaterialComponent> comps = compMapper.selectList(
                    new LambdaQueryWrapper<OutsourceMaterialComponent>()
                        .eq(OutsourceMaterialComponent::getParentMaterialId, id));
                if (!comps.isEmpty()) {
                    result.put(name, comps.stream().map(c -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("childMaterialId", c.getChildMaterialId());
                        OutsourceMaterial child = mapper.selectById(c.getChildMaterialId());
                        m.put("childName", child != null ? child.getMaterialName() : "");
                        m.put("childType", child != null ? child.getMaterialType() : "");
                        m.put("quantity", c.getQuantity());
                        m.put("lossRate", c.getLossRate());
                        m.put("remark", c.getRemark());
                        return m;
                    }).toList());
                }
            }
        }
        return R.ok(result);
    }
}
