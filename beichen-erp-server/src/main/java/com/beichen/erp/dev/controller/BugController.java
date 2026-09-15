package com.beichen.erp.dev.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
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
        // 生成Bug编号：DEV_BUG-yyyyMMdd-序号
        bug.setCode(generateBugCode());
        bugService.save(bug);
        return R.ok(bug);
    }

    /** 生成Bug编号：DEV_BUG-yyyyMMdd-三位序号 */
    private String generateBugCode() {
        String dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String likePattern = BillPrefix.DEV_BUG + dateStr;
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Bug> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Bug>()
                        .likeRight(Bug::getCode, likePattern)
                        .orderByDesc(Bug::getCode)
                        .last("LIMIT 1");
        Bug last = bugService.getOne(w, false);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                String numPart = last.getCode().substring(last.getCode().length() - 3);
                seq = Integer.parseInt(numPart) + 1;
            } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.DEV_BUG + dateStr + String.format("%03d", seq);
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

    /** Bug 类型 **code 列表**（2026-09-14：「接口只回 code」，中文由前端 `BugTypeEnumLabel` 映射） */
    @GetMapping("/bug/types")
    public R<List<String>> bugTypes() {
        return R.ok(Arrays.stream(BugTypeEnum.values())
                .map(BugTypeEnum::getCode)
                .collect(Collectors.toList()));
    }

    /** Bug 严重程度 **code 列表**（中文由前端 `SeverityTypeLabel` 映射） */
    @GetMapping("/bug/severities")
    public R<List<String>> severities() {
        return R.ok(Arrays.stream(SeverityType.values())
                .map(SeverityType::getCode)
                .collect(Collectors.toList()));
    }
}
