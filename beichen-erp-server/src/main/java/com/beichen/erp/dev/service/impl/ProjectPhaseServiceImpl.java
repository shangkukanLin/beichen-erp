package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.beichen.erp.exception.BusinessException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.dev.common.ProjectStatus;
import com.beichen.erp.dev.common.PhaseStatus;
import com.beichen.erp.dev.entity.PhaseTemplate;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.ProjectPhase;
import com.beichen.erp.dev.mapper.PhaseTemplateMapper;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.dev.mapper.ProjectPhaseMapper;
import com.beichen.erp.dev.service.ProjectProductSyncService;
import com.beichen.erp.dev.service.ProjectPhaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectPhaseServiceImpl extends ServiceImpl<ProjectPhaseMapper, ProjectPhase>
        implements ProjectPhaseService {

    private final ProjectPhaseMapper projectPhaseMapper;
    private final PhaseTemplateMapper phaseTemplateMapper;
    private final ProjectMapper projectMapper;
    private final ProjectProductSyncService projectProductSyncService;

    @Override
    public List<ProjectPhase> listByProject(Long projectId) {
        return projectPhaseMapper.selectList(
                new LambdaQueryWrapper<ProjectPhase>()
                        .eq(ProjectPhase::getProjectId, projectId)
                        .orderByAsc(ProjectPhase::getSortOrder));
    }

    @Override
    @Transactional
    public void initPhase(Long projectId) {
        List<PhaseTemplate> templates = phaseTemplateMapper.selectList(
                new LambdaQueryWrapper<PhaseTemplate>().orderByAsc(PhaseTemplate::getSortOrder));
        LocalDate today = LocalDate.now();
        for (int i = 0; i < templates.size(); i++) {
            PhaseTemplate tpl = templates.get(i);
            ProjectPhase tl = new ProjectPhase();
            tl.setProjectId(projectId);
            tl.setPhaseName(tpl.getName());
            tl.setDefaultDays(tpl.getDefaultDays());
            tl.setSortOrder(tpl.getSortOrder());
            tl.setStatus(i == 0 ? PhaseStatus.IN_PROGRESS.getCode() : PhaseStatus.NOT_STARTED.getCode());
            // 立项阶段（第一个阶段）默认计划完成日期为创建当天
            if (i == 0) {
                tl.setPlannedEnd(today);
            }
            tl.setCreateTime(LocalDateTime.now());
            projectPhaseMapper.insert(tl);
        }
    }

    /**
     * F7-139（2026-09-20）：**项目级行锁** —— 把"同一项目的阶段操作"串行化。
     *
     * <p>本类原先**整套状态机无一处 CAS**：`completePhase` / `skipPhase` / `revertPhase` / `savePhaseRow` /
     * `updatePlanned(AndShift)` / `recalcAllPlannedEnds` 全部用 `updateById` 直改阶段与项目状态。
     * 单个方法重复执行多为幂等，但**并发双击**或**跨动作竞争**（如"完成当前阶段"与"撤销上一阶段"同时）
     * 会让阶段状态与项目状态互相覆盖、且不留痕。入口加项目行锁后，同项目的阶段操作天然串行。</p>
     *
     * <p>同事务内 MySQL 行锁可重入，故内部方法（`activateNextPhase` / `syncProjectStatus`）无需重复加锁。
     * 项目不存在时**不抛错**（行不存在也无从加锁），交由各方法原有的"不存在"分支处理，保持原语义。</p>
     */
    private void lockProject(Long projectId) {
        if (projectId == null) return;
        Long cid = com.beichen.erp.config.CompanyContext.get();
        if (cid != null && cid <= 0) cid = null;
        projectMapper.selectForUpdate(projectId, cid);
    }

    @Override
    @Transactional
    public void completePhase(Long projectId, Long phaseId) {
        lockProject(projectId);
        ProjectPhase current = projectPhaseMapper.selectById(phaseId);
        if (current == null) return;
        // 已取消项目不允许再推进阶段，避免阶段状态与项目状态脱节（必须抛错：静默 return 会让前端误报"完成成功"）
        if (isProjectCancelled(projectId)) {
            log.warn("项目已取消，禁止推进阶段: projectId={}, phaseId={}", projectId, phaseId);
            throw new BusinessException("项目已取消，无法推进阶段；请先重新激活项目");
        }

        current.setStatus(PhaseStatus.FINISHED.getCode());
        // 保留用户设置的实际完成日期，未设置则默认当天
        if (current.getActualEnd() == null) {
            current.setActualEnd(LocalDate.now());
        }
        projectPhaseMapper.updateById(current);

        checkProductStatusSync(current.getPhaseName(), projectId);
        activateNextPhase(projectId, current.getSortOrder());
        syncProjectStatus(projectId);
    }

    /**
     * 一键完成（2026-09-21 新增，用户需求）：把本项目所有未完成阶段一次性标记为已完成。
     *
     * <p>与逐个调用 {@link #completePhase} 相比，**结果完全一致、只有过程不同**：</p>
     * <ul>
     *   <li>同一事务内批量完成 ⇒ 不产生 14 次请求与 14 段中间态（"下一个进行中"）；</li>
     *   <li>产品状态同步**最多调一次**（逐个调用会按每个阶段的模板各判一次、可能重复同步）；</li>
     *   <li>**刻意不调用 `activateNextPhase`** —— 一推到底不需要中间"进行中"态，
     *       否则会把后续阶段改成进行中又被立即完成，纯属多余写库；</li>
     *   <li>最后调一次 {@link #syncProjectStatus}：全完成后 ⇒ **项目自动结项 + 同步产品状态**，
     *       该方法是全量重算，故与逐个点击的终态相同。</li>
     * </ul>
     *
     * <p><b>口径</b>（与 {@link #completePhase} 保持一致）：{@code actualEnd} 已有值保留、为空才补今天；
     * {@code plannedEnd} **一律不改**（计划是历史快照，改动会污染"计划 vs 实际"统计）。
     * 已取消项目抛错；已完成/已跳过阶段不动 ⇒ **幂等**，重复点击无副作用。</p>
     */
    @Override
    @Transactional
    public int completeAllPhases(Long projectId) {
        lockProject(projectId);
        if (isProjectCancelled(projectId)) {
            log.warn("项目已取消，禁止一键完成阶段: projectId={}", projectId);
            throw new BusinessException("项目已取消，无法推进阶段；请先重新激活项目");
        }
        List<ProjectPhase> all = listByProject(projectId);
        if (all.isEmpty()) {
            // 不静默成功：否则前端会提示"完成 0 个"让人误以为已处理
            throw new BusinessException("该项目没有阶段记录，无法一键完成");
        }

        LocalDate today = LocalDate.now();
        int done = 0;
        boolean needProductSync = false;
        for (ProjectPhase p : all) {
            String st = p.getStatus();
            // 已完成 / 已跳过：原样不动（幂等的关键，也保住它原有的 actualEnd）
            if (PhaseStatus.FINISHED.getCode().equals(st) || PhaseStatus.SKIPPED.getCode().equals(st)) {
                continue;
            }
            p.setStatus(PhaseStatus.FINISHED.getCode());
            if (p.getActualEnd() == null) {
                p.setActualEnd(today);
            }
            projectPhaseMapper.updateById(p);
            done++;
            // 逐个调用时每个阶段都会判一次；这里只记录"是否需要同步"，循环结束后统一同步一次
            if (needsProductStatusSync(p.getPhaseName(), projectId)) {
                needProductSync = true;
            }
        }
        if (done == 0) {
            log.info("一键完成：所有阶段本就已完成，无操作 projectId={}", projectId);
            return 0;
        }
        if (needProductSync) {
            projectProductSyncService.syncProductStatus(projectId);
        }
        // 全完成后自动结项（内部在结项时还会同步一次产品状态，与逐个点击的调用序列一致）
        syncProjectStatus(projectId);
        log.info("一键完成：projectId={} 本次完成 {} 个阶段", projectId, done);
        return done;
    }

    @Override
    @Transactional
    public void skipPhase(Long projectId, Long phaseId) {
        lockProject(projectId);
        ProjectPhase current = projectPhaseMapper.selectById(phaseId);
        if (current == null) return;
        // 已取消项目不允许再推进阶段（必须抛错，避免静默成功）
        if (isProjectCancelled(projectId)) {
            log.warn("项目已取消，禁止推进阶段: projectId={}, phaseId={}", projectId, phaseId);
            throw new BusinessException("项目已取消，无法跳过阶段；请先重新激活项目");
        }

        current.setStatus(PhaseStatus.SKIPPED.getCode());
        if (current.getActualEnd() == null) {
            current.setActualEnd(LocalDate.now());
        }
        projectPhaseMapper.updateById(current);

        checkProductStatusSync(current.getPhaseName(), projectId);
        activateNextPhase(projectId, current.getSortOrder());
        syncProjectStatus(projectId);
    }

    @Override
    @Transactional
    public void revertPhase(Long projectId, Long phaseId) {
        lockProject(projectId);
        ProjectPhase current = projectPhaseMapper.selectById(phaseId);
        if (current == null) return;
        // F7-96（2026-09-19）：补上"已取消项目"护栏 —— complete/skip/savePhaseRow 三处都有，
        // 这里原先漏了，导致已取消项目仍可撤销阶段（还会把 CLOSED 改回 IN_PROGRESS）
        if (isProjectCancelled(projectId)) {
            log.warn("项目已取消，禁止撤销阶段: projectId={}, phaseId={}", projectId, phaseId);
            throw new BusinessException("项目已取消，无法撤销阶段；请先重新激活项目");
        }

        String oldStatus = current.getStatus();
        // 只有已完成或已跳过的阶段才能撤销
        if (!PhaseStatus.FINISHED.getCode().equals(oldStatus)
                && !PhaseStatus.SKIPPED.getCode().equals(oldStatus)) {
            log.warn("阶段状态不允许撤销: projectId={}, phaseId={}, status={}", projectId, phaseId, oldStatus);
            return;
        }

        // 1. 将当前阶段恢复为进行中（actual_end 必须用 UpdateWrapper 显式置 null：updateById 忽略 null）
        projectPhaseMapper.update(null, new LambdaUpdateWrapper<ProjectPhase>()
                .eq(ProjectPhase::getId, phaseId)
                .set(ProjectPhase::getStatus, PhaseStatus.IN_PROGRESS.getCode())
                .set(ProjectPhase::getActualEnd, null));

        // 2. 将排序在当前之后的所有阶段重置为未开始，并清空实际完成日期
        List<ProjectPhase> all = listByProject(projectId);
        for (ProjectPhase t : all) {
            if (t.getSortOrder() > current.getSortOrder()) {
                projectPhaseMapper.update(null, new LambdaUpdateWrapper<ProjectPhase>()
                        .eq(ProjectPhase::getId, t.getId())
                        .set(ProjectPhase::getStatus, PhaseStatus.NOT_STARTED.getCode())
                        .set(ProjectPhase::getActualEnd, null));
            }
        }

        // 3. 如果项目已结项，恢复为进行中
        Project project = projectMapper.selectById(projectId);
        if (project != null && ProjectStatus.CLOSED.getCode().equals(project.getStatus())) {
            project.setStatus(ProjectStatus.IN_PROGRESS.getCode());
            projectMapper.updateById(project);
            log.info("项目撤销结项: projectId={}", projectId);
        }
    }

    @Override
    @Transactional
    public void recalcAllPlannedEnds(Long projectId) {
        lockProject(projectId);
        List<ProjectPhase> all = listByProject(projectId);
        if (all.isEmpty()) return;

        // 找到第一个进行中的阶段，作为推算起点
        ProjectPhase inProgress = null;
        for (ProjectPhase t : all) {
            if (PhaseStatus.IN_PROGRESS.getCode().equals(t.getStatus())) {
                inProgress = t;
                break;
            }
        }
        if (inProgress == null) {
            // 没有进行中的阶段，不做重算
            return;
        }

        // 进行中阶段基准：实际完成日期优先，否则今天
        LocalDate baseDate = inProgress.getActualEnd() != null ? inProgress.getActualEnd() : LocalDate.now();
        // 进行中阶段自身计划完成日期为空时回填基准
        if (inProgress.getPlannedEnd() == null) {
            inProgress.setPlannedEnd(baseDate);
            projectPhaseMapper.updateById(inProgress);
        }

        // 顺序推算后续阶段，跳过已完成/已跳过的阶段（其计划日期为历史快照不应改写）
        boolean found = false;
        for (ProjectPhase next : all) {
            if (!found) {
                // 先定位到进行中阶段之后再开始推算
                if (next.getId().equals(inProgress.getId())) found = true;
                continue;
            }
            if (PhaseStatus.FINISHED.getCode().equals(next.getStatus())
                    || PhaseStatus.SKIPPED.getCode().equals(next.getStatus())) {
                // 历史阶段保持原计划日期，仅刷新基准为它的结束，保证后续推算连续
                if (next.getPlannedEnd() != null) baseDate = next.getPlannedEnd();
                continue;
            }
            int days = next.getDefaultDays() != null ? next.getDefaultDays() : 7;
            next.setPlannedEnd(baseDate.plusDays(days));
            projectPhaseMapper.updateById(next);
            baseDate = next.getPlannedEnd();
        }
    }

    @Override
    @Transactional
    public void savePhaseRow(Long projectId, ProjectPhase row) {
        lockProject(projectId);
        ProjectPhase existing = projectPhaseMapper.selectById(row.getId());
        if (existing == null) return;
        // 已取消项目不允许通过手动编辑阶段推进状态（必须抛错，避免静默成功）
        if (isProjectCancelled(projectId)) {
            log.warn("项目已取消，禁止编辑阶段: projectId={}, phaseId={}", projectId, row.getId());
            throw new BusinessException("项目已取消，无法编辑阶段；请先重新激活项目");
        }

        String oldStatus = existing.getStatus();
        String newStatus = row.getStatus();
        // F7-96（2026-09-19）：① status 白名单（原先可直接写任意字符串，之后 syncProjectStatus 的
        // allMatch 会把该阶段视为"未完成" ⇒ 项目永远无法结项）；② 用 Objects.equals 避免
        // oldStatus 为 null（历史脏数据）时 NPE
        if (newStatus != null && PhaseStatus.fromCode(newStatus) == null) {
            throw new BusinessException("阶段状态非法：" + newStatus);
        }
        boolean statusChanged = !java.util.Objects.equals(oldStatus, newStatus);

        existing.setStatus(newStatus);
        existing.setPlannedEnd(row.getPlannedEnd());
        existing.setActualEnd(row.getActualEnd());
        existing.setRemark(row.getRemark());
        // 状态变为完成/跳过且未设实际日期时，默认当天
        if ((PhaseStatus.FINISHED.getCode().equals(newStatus) || PhaseStatus.SKIPPED.getCode().equals(newStatus))
                && existing.getActualEnd() == null) {
            existing.setActualEnd(LocalDate.now());
        }
        projectPhaseMapper.updateById(existing);

        if (statusChanged) {
            if (PhaseStatus.FINISHED.getCode().equals(newStatus)
                    || PhaseStatus.SKIPPED.getCode().equals(newStatus)) {
                checkProductStatusSync(existing.getPhaseName(), projectId);
                activateNextPhase(projectId, existing.getSortOrder());
                syncProjectStatus(projectId);
            }
        }
    }

    @Override
    @Transactional
    public void updatePlanned(Long projectId, String phaseName, LocalDate plannedEnd) {
        lockProject(projectId);
        // F7-96（2026-09-19）：补上"已取消项目"护栏（原先只在 complete/skip/savePhaseRow 三处做了）
        if (isProjectCancelled(projectId)) {
            throw new BusinessException("项目已取消，无法修改阶段计划日期；请先重新激活项目");
        }
        ProjectPhase tl = findByPhaseName(projectId, phaseName);
        if (tl != null) {
            tl.setPlannedEnd(plannedEnd);
            projectPhaseMapper.updateById(tl);
        }
    }

    @Override
    @Transactional
    public void updatePlannedAndShift(Long projectId, String phaseName, LocalDate plannedEnd) {
        lockProject(projectId);
        // F7-96（2026-09-19）：补上"已取消项目"护栏（该方法还会**级联后推后续所有阶段**，
        // 对已取消项目执行相当于篡改历史计划）
        if (isProjectCancelled(projectId)) {
            throw new BusinessException("项目已取消，无法推移阶段计划日期；请先重新激活项目");
        }
        ProjectPhase tl = findByPhaseName(projectId, phaseName);
        if (tl == null) return;

        // 原计划完成日期为空时（非首阶段默认不填），以今天为基准计算偏移，避免空指针
        LocalDate oldPlannedEnd = tl.getPlannedEnd() != null ? tl.getPlannedEnd() : LocalDate.now();
        long daysDiff = plannedEnd.toEpochDay() - oldPlannedEnd.toEpochDay();
        tl.setPlannedEnd(plannedEnd);
        projectPhaseMapper.updateById(tl);

        List<ProjectPhase> all = listByProject(projectId);
        boolean found = false;
        for (ProjectPhase t : all) {
            if (found && t.getPlannedEnd() != null) {
                t.setPlannedEnd(t.getPlannedEnd().plusDays(daysDiff));
                projectPhaseMapper.updateById(t);
            }
            if (t.getId().equals(tl.getId())) found = true;
        }
    }

    // F7-93（2026-09-19）：原 updateStatus(projectId, phaseName, status) 已删除 ——
    // ProjectPhaseController 从未暴露该端点、全库也没有调用方（死代码），且它直接 setStatus
    // 且无枚举白名单，留着容易被误用。阶段状态变更请走 completePhase / skipPhase / savePhaseRow。

    // ===== 内部方法 =====

    /**
     * 判断项目是否已取消（取消后阶段不可再推进/编辑）。
     * <p>按 <b>status</b> 判定：{@code cancelled_at} 只是取消留痕，若用它判断，
     * 一旦取消标记未清理（如历史数据残留），项目会"看着已激活但阶段全部失效"。</p>
     */
    private boolean isProjectCancelled(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        return project != null && ProjectStatus.CANCELLED.getCode().equals(project.getStatus());
    }

    private ProjectPhase findByPhaseName(Long projectId, String phaseName) {
        return projectPhaseMapper.selectOne(
                new LambdaQueryWrapper<ProjectPhase>()
                        .eq(ProjectPhase::getProjectId, projectId)
                        .eq(ProjectPhase::getPhaseName, phaseName));
    }

    private void activateNextPhase(Long projectId, int currentSortOrder) {
        List<ProjectPhase> all = listByProject(projectId);
        for (ProjectPhase t : all) {
            if (t.getSortOrder() > currentSortOrder
                    && PhaseStatus.NOT_STARTED.getCode().equals(t.getStatus())) {
                t.setStatus(PhaseStatus.IN_PROGRESS.getCode());
                if (t.getPlannedEnd() == null) {
                    t.setPlannedEnd(LocalDate.now().plusDays(t.getDefaultDays() != null ? t.getDefaultDays() : 7));
                }
                projectPhaseMapper.updateById(t);
                break;
            }
        }
    }

    /** 从项目阶段推导项目状态 */
    private void syncProjectStatus(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) return;
        // F7-89（2026-09-19）：判据由 cancelledAt 改为 status，与阶段护栏 isProjectCancelled
        // 保持一致。原先按 cancelledAt 判断时，一旦出现"status=IN_PROGRESS 但 cancelled_at 有
        // 残留"的数据（旧版 updateProject 整实体写回即可造成），这里会永久 return ⇒
        // 阶段全部完成后项目永不结项（静默死锁）。
        if (ProjectStatus.CANCELLED.getCode().equals(project.getStatus())) return;

        List<ProjectPhase> all = listByProject(projectId);
        boolean allDone = all.stream().allMatch(t ->
                PhaseStatus.FINISHED.getCode().equals(t.getStatus())
                        || PhaseStatus.SKIPPED.getCode().equals(t.getStatus()));
        if (allDone && !ProjectStatus.CLOSED.getCode().equals(project.getStatus())) {
            project.setStatus(ProjectStatus.CLOSED.getCode());
            // 结项同时落实际结束日期，供研发周期（计划 vs 实际）统计
            project.setActualEndDate(LocalDate.now());
            projectMapper.updateById(project);
            log.info("项目自动结项: projectId={}", projectId);
            // 结项时同步关联产品状态（研发中→正常），避免产品停留在研发中
            projectProductSyncService.syncProductStatus(projectId);
        }
    }

    private void checkProductStatusSync(String phaseName, Long projectId) {
        if (needsProductStatusSync(phaseName, projectId)) {
            projectProductSyncService.syncProductStatus(projectId);
        }
    }

    /**
     * 该阶段（按名称匹配模板）完成/跳过时**是否需要**同步产品状态。
     * <p>2026-09-21 从 {@link #checkProductStatusSync} 抽出 —— 一键完成需要"先判断、最后只同步一次"，
     * 若沿用原方法会在循环里同步 N 次。判断逻辑与日志口径与原方法逐字保持一致。</p>
     */
    private boolean needsProductStatusSync(String phaseName, Long projectId) {
        // F7-93（2026-09-19）：原用 selectOne(eq(name)) —— 一旦同名模板存在两条（历史脏数据，或
        // 未加唯一索引前建出来），MyBatis-Plus 会抛 TooManyResultsException ⇒ 完成/跳过/保存
        // 阶段全部 500（研发主线被脏数据打断）。改为按 id 升序取第一条兜底，入口侧另加了重名校验。
        PhaseTemplate tpl = phaseTemplateMapper.selectOne(
                new LambdaQueryWrapper<PhaseTemplate>()
                        .eq(PhaseTemplate::getName, phaseName)
                        .orderByAsc(PhaseTemplate::getId)
                        .last("LIMIT 1"));
        if (tpl == null) {
            // F7-94（2026-09-19）：原为静默跳过 —— 阶段模板被改名或删除后，本阶段不再触发产品状态
            // 同步（阶段名是建项目时复制进 dev_project_phase 的快照，模板改名不会回溯），
            // 且没有任何提示，只能靠"产品状态一直停在研发中"来间接发现
            log.warn("未找到阶段模板[{}]，跳过产品状态同步: projectId={}", phaseName, projectId);
            return false;
        }
        return PhaseTemplate.SYNC_PRODUCT_STATUS == valueOf(tpl.getProductStatusSync());
    }

    /** 将可能为 null 的 Integer 规整为语义值，避免 null 拆箱与魔法值比较 */
    private static int valueOf(Integer v) {
        return v == null ? 0 : v;
    }
}
