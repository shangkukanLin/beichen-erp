package com.beichen.erp.dev.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.common.BugTypeEnum;
import com.beichen.erp.dev.common.SeverityType;
import com.beichen.erp.dev.entity.Bug;
import com.beichen.erp.dev.service.BugService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dev/project")
@RequiredArgsConstructor
public class BugController {

    private final BugService bugService;

    /** 获取项目Bug列表 */
    @GetMapping("/{projectId}/bug")
    public R<Page<Bug>> list(@PathVariable Long projectId, PageParam param,
                             @RequestParam(required = false) String bugType,
                             @RequestParam(required = false) String status) {
        return R.ok(bugService.pageByProject(projectId, param, bugType, status));
    }

    /** 新增Bug */
    @PostMapping("/{projectId}/bug")
    public R<Bug> add(@PathVariable Long projectId, @RequestBody Bug bug) {
        bug.setProjectId(projectId);
        bugService.save(bug);
        return R.ok(bug);
    }

    /** 修改Bug */
    @PutMapping("/{projectId}/bug/{id}")
    public R<Void> update(@PathVariable Long projectId, @PathVariable Long id, @RequestBody Bug bug) {
        bug.setId(id);
        bugService.updateById(bug);
        return R.ok();
    }

    /** 删除Bug */
    @DeleteMapping("/{projectId}/bug/{id}")
    public R<Void> delete(@PathVariable Long projectId, @PathVariable Long id) {
        bugService.removeById(id);
        return R.ok();
    }

    /** Bug类型枚举 */
    @GetMapping("/bug/types")
    public R<List<Map<String, String>>> bugTypes() {
        List<Map<String, String>> list = Arrays.stream(BugTypeEnum.values())
                .map(t -> Map.of("code", t.getCode(), "label", t.getLabel()))
                .collect(Collectors.toList());
        return R.ok(list);
    }

    /** Bug严重程度枚举 */
    @GetMapping("/bug/severities")
    public R<List<Map<String, String>>> severities() {
        List<Map<String, String>> list = Arrays.stream(SeverityType.values())
                .map(s -> Map.of("code", s.getCode(), "label", s.getLabel()))
                .collect(Collectors.toList());
        return R.ok(list);
    }
}
