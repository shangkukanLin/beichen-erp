package com.beichen.erp.material.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.R;
import com.beichen.erp.material.entity.MaterialBom;
import com.beichen.erp.material.entity.MaterialBomVO;
import com.beichen.erp.material.entity.MaterialBriefVO;
import com.beichen.erp.material.service.MaterialBomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/material-bom")
@RequiredArgsConstructor
public class MaterialBomController {

    private final MaterialBomService materialBomService;

    /** 直接子物料（编辑物料弹窗时加载） */
    @GetMapping("/direct")
    public R<List<MaterialBomVO>> direct(@RequestParam Long parentId) {
        return R.ok(materialBomService.getDirect(parentId));
    }

    /** 多级BOM树（BOM表展示） */
    @GetMapping("/tree")
    public R<List<MaterialBomVO>> tree(@RequestParam Long materialId) {
        return R.ok(materialBomService.getTree(materialId));
    }

    /** 某子物料被哪些成品/半成品使用（向上追溯） */
    @GetMapping("/where-used/{childId}")
    public R<List<MaterialBriefVO>> whereUsed(@PathVariable Long childId) {
        return R.ok(materialBomService.getWhereUsed(childId));
    }

    /** 保存(替换)某父物料的直接子物料组成 */
    @PostMapping("/{parentId}")
    public R<Void> save(@PathVariable Long parentId, @RequestBody List<MaterialBom> children) {
        materialBomService.saveChildren(parentId, children);
        return R.ok();
    }

    /** 删除某父物料的全部直接组成 */
    @DeleteMapping("/parent/{parentId}")
    public R<Void> deleteByParent(@PathVariable Long parentId) {
        materialBomService.remove(new LambdaQueryWrapper<MaterialBom>().eq(MaterialBom::getParentMaterialId, parentId));
        return R.ok();
    }
}
