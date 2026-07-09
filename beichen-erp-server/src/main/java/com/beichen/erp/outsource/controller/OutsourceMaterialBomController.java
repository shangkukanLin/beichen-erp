package com.beichen.erp.outsource.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.outsource.entity.OutsourceMaterialBom;
import com.beichen.erp.outsource.entity.OutsourceMaterialBomVO;
import com.beichen.erp.outsource.entity.OutsourceMaterialBriefVO;
import com.beichen.erp.outsource.service.OutsourceMaterialBomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outsource/material-bom")
@RequiredArgsConstructor
public class OutsourceMaterialBomController {

    private final OutsourceMaterialBomService bomService;

    @GetMapping("/direct")
    public R<List<OutsourceMaterialBomVO>> direct(@RequestParam Long parentId) {
        return R.ok(bomService.getDirect(parentId));
    }

    @GetMapping("/tree")
    public R<List<OutsourceMaterialBomVO>> tree(@RequestParam Long materialId) {
        return R.ok(bomService.getTree(materialId));
    }

    @GetMapping("/where-used/{childId}")
    public R<List<OutsourceMaterialBriefVO>> whereUsed(@PathVariable Long childId) {
        return R.ok(bomService.getWhereUsed(childId));
    }

    @PostMapping("/children-batch")
    public R<Map<String, List<OutsourceMaterialBomVO>>> childrenBatch(@RequestBody List<String> names) {
        return R.ok(bomService.getChildrenByNames(names));
    }

    @PostMapping("/{parentId}")
    public R<Void> save(@PathVariable Long parentId, @RequestBody List<OutsourceMaterialBom> children) {
        bomService.saveChildren(parentId, children);
        return R.ok();
    }
}
