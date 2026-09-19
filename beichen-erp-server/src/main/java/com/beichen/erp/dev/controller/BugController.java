package com.beichen.erp.dev.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.common.BugStatus;
import com.beichen.erp.dev.common.BugTypeEnum;
import com.beichen.erp.dev.common.SeverityType;
import com.beichen.erp.dev.entity.Bug;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.dev.service.BugService;
import com.beichen.erp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 研发项目缺陷（Bug）管理。
 * <p>F7-100（2026-09-19）修正三处：① {@code projectId} 路径参数原先在 update/delete 里声明后
 * <b>从未使用</b>（知道 id 即可跨项目改/删）⇒ 现统一走 {@link #requireOwned} 归属校验；
 * ② {@code bugType}/{@code severity}/{@code status} 原先直接采用请求体内容（三个枚举的
 * {@code fromCode} 都已存在却未使用）⇒ 现做白名单校验；③ 编号原先用
 * {@code substring(len-3)} + {@code catch → seq = 1} 静默回退，且格式与注释不符
 * （注释写 {@code DEV_BUG-20260815-001}，实际生成 {@code DEV_BUG-20260919001}）⇒ 现统一为
 * {@code DEV_BUG-yyyyMMdd-三位序号}，序号按最后一个 {@code -} 之后解析（同 dev_project 的 F7-87 写法）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/dev/project")
@RequiredArgsConstructor
public class BugController {

    private final BugService bugService;
    /** F7-100：新增 Bug 时校验项目存在，避免挂到无效 projectId 上 */
    private final ProjectMapper projectMapper;

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
        if (projectId == null || projectMapper.selectById(projectId) == null) {
            throw new BusinessException("研发项目不存在");
        }
        validateEnums(bug);
        bug.setProjectId(projectId);
        bug.setCode(generateBugCode());
        bugService.save(bug);
        return R.ok(bug);
    }

    /** 修改Bug（仅允许改内容字段；projectId / code 以库中值为准，防止把 Bug 挪到别的项目） */
    @PutMapping("/{projectId}/bug/{id}")
    public R<Void> update(@PathVariable Long projectId, @PathVariable Long id, @RequestBody Bug bug) {
        Bug exist = requireOwned(projectId, id);
        validateEnums(bug);
        bug.setId(id);
        bug.setProjectId(exist.getProjectId());
        bug.setCode(exist.getCode());
        bugService.updateById(bug);
        return R.ok();
    }

    /** 删除Bug */
    @DeleteMapping("/{projectId}/bug/{id}")
    public R<Void> delete(@PathVariable Long projectId, @PathVariable Long id) {
        requireOwned(projectId, id);
        bugService.removeById(id);
        return R.ok();
    }

    /**
     * F7-100：校验 Bug 存在且属于路径里的 projectId。
     * <p>原先 {@code @PathVariable Long projectId} 在 update/delete 里声明后从未使用（明显漏写），
     * 只要知道自己公司内任意 Bug 的 id 就能改/删。</p>
     */
    private Bug requireOwned(Long projectId, Long id) {
        Bug exist = bugService.getById(id);
        if (exist == null) throw new BusinessException("Bug 不存在");
        if (projectId != null && !projectId.equals(exist.getProjectId())) {
            throw new BusinessException("该 Bug 不属于当前项目");
        }
        return exist;
    }

    /**
     * F7-100：bugType / severity / status 白名单校验（空值放行，由库默认或前端补默认值）。
     * <p>非法值写入后前端 {@code BugStatusLabel}/{@code SeverityTypeLabel}/{@code BugTypeEnumLabel}
     * 映射不到中文，列表会显示空白。</p>
     */
    private void validateEnums(Bug bug) {
        if (bug.getBugType() != null && BugTypeEnum.fromCode(bug.getBugType()) == null) {
            throw new BusinessException("Bug 类型非法：" + bug.getBugType());
        }
        if (bug.getSeverity() != null && SeverityType.fromCode(bug.getSeverity()) == null) {
            throw new BusinessException("严重程度非法：" + bug.getSeverity());
        }
        if (bug.getStatus() != null && BugStatus.fromCode(bug.getStatus()) == null) {
            throw new BusinessException("Bug 状态非法：" + bug.getStatus());
        }
    }

    /** 生成Bug编号：DEV_BUG-yyyyMMdd-三位序号 */
    private String generateBugCode() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = BillPrefix.DEV_BUG + dateStr + "-";
        Bug last = bugService.getOne(new LambdaQueryWrapper<Bug>()
                .likeRight(Bug::getCode, prefix)
                .orderByDesc(Bug::getCode)
                .last("LIMIT 1"), false);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            String numPart = last.getCode().substring(last.getCode().lastIndexOf('-') + 1);
            try {
                seq = Integer.parseInt(numPart) + 1;
            } catch (Exception e) {
                log.warn("Bug 编号序号解析失败，本次回退为 1: lastCode={}", last.getCode());
                seq = 1;
            }
        }
        return prefix + String.format("%03d", seq);
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
