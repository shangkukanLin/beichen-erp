package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.DevMaterial;
import com.beichen.erp.dev.service.DevMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dev/project/{projectId}/material")
@RequiredArgsConstructor
public class DevMaterialController {

    private final DevMaterialService devMaterialService;

    @GetMapping
    public R<List<DevMaterial>> list(@PathVariable Long projectId) {
        return R.ok(devMaterialService.listByProjectId(projectId));
    }

    @PostMapping
    public R<Void> add(@PathVariable Long projectId, @RequestBody DevMaterial material) {
        material.setProjectId(projectId);
        devMaterialService.add(material);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long projectId, @PathVariable Long id, @RequestBody DevMaterial material) {
        material.setId(id);
        material.setProjectId(projectId);
        devMaterialService.update(material);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long projectId, @PathVariable Long id) {
        devMaterialService.remove(id);
        return R.ok();
    }
}
