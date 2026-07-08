package com.beichen.erp.dev.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.Bom;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.mapper.BomMapper;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.dev.service.BomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dev/bom")
@RequiredArgsConstructor
public class BomManageController {

    private final BomMapper bomMapper;
    private final BomService bomService;
    private final ProjectMapper projectMapper;
    private final SupplierMapper supplierMapper;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String materialType) {

        LambdaQueryWrapper<Bom> wrapper = new LambdaQueryWrapper<Bom>()
                .like(materialName != null && !materialName.isBlank(), Bom::getMaterialName, materialName)
                .eq(projectId != null, Bom::getProjectId, projectId)
                .like(materialType != null && !materialType.isBlank(), Bom::getMaterialType, materialType)
                .orderByDesc(Bom::getId);

        Page<Bom> page = bomMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 附带项目名称
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(page.getRecords().stream().map(bom -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", bom.getId());
            map.put("projectId", bom.getProjectId());
            Project proj = projectMapper.selectById(bom.getProjectId());
            map.put("projectName", proj != null ? proj.getName() : "");
            map.put("projectCode", proj != null ? proj.getCode() : "");
            Supplier supplier = bom.getSupplierId() != null ? supplierMapper.selectById(bom.getSupplierId()) : null;
            map.put("supplierId", bom.getSupplierId());
            map.put("supplierName", supplier != null ? supplier.getName() : "");
            map.put("materialName", bom.getMaterialName());
            map.put("spec", bom.getSpec());
            map.put("unit", bom.getUnit());
            map.put("quantityPerSet", bom.getQuantityPerSet());
            map.put("lossRate", bom.getLossRate());
            map.put("materialType", bom.getMaterialType());
            map.put("remark", bom.getRemark());
            return map;
        }).toList());
        return R.ok(result);
    }

    @PostMapping
    public R<Void> add(@RequestBody Map<String, Object> body) {
        Bom bom = new Bom();
        bom.setProjectId(Long.valueOf(body.get("projectId").toString()));
        if (body.get("supplierId") != null) bom.setSupplierId(Long.valueOf(body.get("supplierId").toString()));
        bom.setMaterialName((String) body.get("materialName"));
        bom.setSpec((String) body.get("spec"));
        bom.setUnit((String) body.get("unit"));
        if (body.get("quantityPerSet") != null) bom.setQuantityPerSet(new java.math.BigDecimal(body.get("quantityPerSet").toString()));
        if (body.get("lossRate") != null) bom.setLossRate(new java.math.BigDecimal(body.get("lossRate").toString()));
        bom.setMaterialType((String) body.get("materialType"));
        bom.setRemark((String) body.get("remark"));
        bomMapper.insert(bom);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody Map<String, Object> body) {
        Bom bom = new Bom();
        bom.setId(Long.valueOf(body.get("id").toString()));
        bom.setProjectId(Long.valueOf(body.get("projectId").toString()));
        if (body.get("supplierId") != null) bom.setSupplierId(Long.valueOf(body.get("supplierId").toString()));
        bom.setMaterialName((String) body.get("materialName"));
        bom.setSpec((String) body.get("spec"));
        bom.setUnit((String) body.get("unit"));
        if (body.get("quantityPerSet") != null) bom.setQuantityPerSet(new java.math.BigDecimal(body.get("quantityPerSet").toString()));
        if (body.get("lossRate") != null) bom.setLossRate(new java.math.BigDecimal(body.get("lossRate").toString()));
        bom.setMaterialType((String) body.get("materialType"));
        bom.setRemark((String) body.get("remark"));
        bomMapper.updateById(bom);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        bomMapper.deleteById(id);
        return R.ok();
    }

    @GetMapping("/project/{projectId}")
    public R<Object> getByProject(@PathVariable Long projectId) {
        return R.ok(bomMapper.selectList(new LambdaQueryWrapper<Bom>()
                .eq(Bom::getProjectId, projectId).orderByAsc(Bom::getId)));
    }
}
