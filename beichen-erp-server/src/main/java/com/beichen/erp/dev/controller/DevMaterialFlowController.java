package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.DevMaterialFlow;
import com.beichen.erp.dev.service.DevMaterialFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 研发物料位置流转记录管理
 */
@RestController
@RequestMapping("/api/dev/material-flow")
@RequiredArgsConstructor
public class DevMaterialFlowController {

    private final DevMaterialFlowService devMaterialFlowService;

    /** 查询某物料的流转记录列表（时间倒序） */
    @GetMapping("/list")
    public R<List<DevMaterialFlow>> list(@RequestParam Long materialId) {
        return R.ok(devMaterialFlowService.listByMaterial(materialId));
    }

    /** 新增流转记录 */
    @PostMapping
    public R<DevMaterialFlow> add(@RequestBody DevMaterialFlow flow) {
        return R.ok(devMaterialFlowService.add(flow));
    }

    /** 修改流转记录 */
    @PutMapping("/{id}")
    public R<DevMaterialFlow> update(@PathVariable Long id, @RequestBody DevMaterialFlow flow) {
        flow.setId(id);
        return R.ok(devMaterialFlowService.update(flow));
    }

    /** 删除流转记录 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        devMaterialFlowService.delete(id);
        return R.ok();
    }
}
