package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.Drawing;
import com.beichen.erp.dev.service.DrawingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dev/project")
@RequiredArgsConstructor
public class DrawingController {

    private final DrawingService drawingService;

    /** 获取项目图纸列表 */
    @GetMapping("/{projectId}/drawing")
    public R<List<Drawing>> list(@PathVariable Long projectId) {
        return R.ok(drawingService.listByProject(projectId));
    }

    /** 新增图纸（版本自动递增） */
    @PostMapping("/{projectId}/drawing")
    public R<Drawing> add(@PathVariable Long projectId, @RequestBody Drawing drawing) {
        drawing.setProjectId(projectId);
        return R.ok(drawingService.upload(drawing));
    }

    /** 删除图纸 */
    @DeleteMapping("/drawing/{id}")
    public R<Void> delete(@PathVariable Long id) {
        drawingService.removeById(id);
        return R.ok();
    }
}
