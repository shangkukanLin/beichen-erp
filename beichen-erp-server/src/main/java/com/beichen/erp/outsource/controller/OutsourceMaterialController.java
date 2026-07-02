package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
            map.put("supplierName", m.getSupplierName());
            map.put("supplierIds", m.getSupplierIds());
            map.put("unit", m.getUnit());
            map.put("status", m.getStatus());
            map.put("remark", m.getRemark());
            return map;
        }).toList());
        return R.ok(result);
    }

    private String idsToNames(String ids, com.baomidou.mybatisplus.core.mapper.BaseMapper<?> mapper) {
        if (ids == null || ids.isBlank()) return "";
        return Arrays.stream(ids.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(id -> {
                    try {
                        if (mapper instanceof ProjectMapper pm) {
                            Project p = pm.selectById(Long.valueOf(id));
                            return p != null ? p.getName() : id;
                        }
                    } catch (Exception e) { }
                    return id;
                }).collect(Collectors.joining(", "));
    }

    @PostMapping
    public R<Void> add(@RequestBody Map<String, Object> body) {
        OutsourceMaterial m = new OutsourceMaterial();
        fill(m, body);
        m.setUnit(body.get("unit") != null ? body.get("unit").toString() : "PCS");
        m.setStatus(1);
        mapper.insert(m);
        return R.ok();
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
        m.setSupplierName((String) body.get("supplierName"));
        m.setSupplierIds(body.get("supplierIds") != null ? body.get("supplierIds").toString() : null);
        m.setUnit(body.get("unit") != null ? body.get("unit").toString() : "PCS");
        m.setStatus(body.get("status") != null ? Integer.valueOf(body.get("status").toString()) : 1);
        m.setRemark((String) body.get("remark"));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) { mapper.deleteById(id); return R.ok(); }
}
