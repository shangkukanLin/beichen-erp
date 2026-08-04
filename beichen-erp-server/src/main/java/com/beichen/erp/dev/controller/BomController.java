package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.Bom;
import com.beichen.erp.dev.service.BomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dev/project")
@RequiredArgsConstructor
public class BomController {

    private final BomService bomService;

    /** 获取项目最新版本BOM */
    @GetMapping("/{projectId}/bom")
    public R<List<Bom>> list(@PathVariable Long projectId,
                             @RequestParam(required = false) Integer version) {
        if (version != null) {
            return R.ok(bomService.listByProjectAndVersion(projectId, version));
        }
        return R.ok(bomService.listByProject(projectId));
    }

    /** 获取BOM版本列表 */
    @GetMapping("/{projectId}/bom/versions")
    public R<List<Integer>> versions(@PathVariable Long projectId) {
        return R.ok(bomService.getVersions(projectId));
    }

    /** 批量保存BOM（全量替换：先删旧数据再批量插入） */
    @PostMapping("/{projectId}/bom/batch")
    public R<Void> saveBatch(@PathVariable Long projectId, @RequestBody List<Bom> items) {
        bomService.saveBatch(projectId, items);
        return R.ok();
    }

    /** 保存BOM项 */
    @PostMapping("/{projectId}/bom")
    public R<Void> saveItem(@PathVariable Long projectId, @RequestBody Bom bom) {
        bom.setProjectId(projectId);
        if (bom.getVersion() == null) {
            bom.setVersion(bomService.getMaxVersion(projectId));
        }
        bomService.saveOrUpdate(bom);
        return R.ok();
    }

    /** 删除BOM项 */
    @DeleteMapping("/bom/{id}")
    public R<Void> delete(@PathVariable Long id) {
        bomService.removeById(id);
        return R.ok();
    }

    /** 创建新版本BOM（从当前最新版本复制） */
    @PostMapping("/{projectId}/bom/new-version")
    public R<List<Bom>> newVersion(@PathVariable Long projectId) {
        return R.ok(bomService.createNewVersion(projectId));
    }
}
