package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.Bug;
import com.beichen.erp.dev.entity.dto.BugDTO;
import com.beichen.erp.dev.service.BugService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dev/project/{projectId}/bug")
@RequiredArgsConstructor
public class BugController {

    private final BugService bugService;

    @GetMapping
    public R<List<Bug>> list(@PathVariable Long projectId) {
        return R.ok(bugService.listByProject(projectId));
    }

    @PostMapping
    public R<Void> add(@PathVariable Long projectId, @Valid @RequestBody BugDTO dto) {
        bugService.create(projectId, dto);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long projectId, @PathVariable Long id, @Valid @RequestBody BugDTO dto) {
        bugService.update(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long projectId, @PathVariable Long id) {
        bugService.delete(id);
        return R.ok();
    }
}
