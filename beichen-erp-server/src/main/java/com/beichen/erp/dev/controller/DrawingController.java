package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.Drawing;
import com.beichen.erp.dev.service.DrawingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dev/project/{projectId}/drawing")
@RequiredArgsConstructor
public class DrawingController {

    private final DrawingService drawingService;

    @GetMapping
    public R<List<Drawing>> list(@PathVariable Long projectId) {
        return R.ok(drawingService.listByProject(projectId));
    }

    @PostMapping
    public R<Void> add(@PathVariable Long projectId, @RequestBody Drawing drawing) {
        drawing.setProjectId(projectId);
        drawingService.addDrawing(drawing);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long projectId, @PathVariable Long id) {
        drawingService.deleteDrawing(id);
        return R.ok();
    }
}
