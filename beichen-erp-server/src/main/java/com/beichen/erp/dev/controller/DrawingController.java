package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
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

    /**
     * 删除图纸。
     * <p>F7-98 + F7-104（2026-09-19）：原映射为 {@code /drawing/{id}}，而前端调用的是
     * {@code DELETE /api/dev/project/{projectId}/drawing/{id}} ⇒ **前后端路径不一致，删除恒 404**
     * （界面上点"删除"没有任何效果）。现改为与前端一致，并补上存在性 + 归属校验。</p>
     */
    @DeleteMapping("/{projectId}/drawing/{id}")
    public R<Void> delete(@PathVariable Long projectId, @PathVariable Long id) {
        Drawing d = drawingService.getById(id);
        if (d == null) throw new BusinessException("图纸不存在");
        if (projectId != null && !projectId.equals(d.getProjectId())) {
            throw new BusinessException("该图纸不属于当前项目");
        }
        drawingService.removeById(id);
        return R.ok();
    }
}
