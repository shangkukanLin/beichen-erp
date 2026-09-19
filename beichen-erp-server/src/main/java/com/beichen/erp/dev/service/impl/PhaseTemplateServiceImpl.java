package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.dev.entity.PhaseTemplate;
import com.beichen.erp.dev.mapper.PhaseTemplateMapper;
import com.beichen.erp.dev.service.PhaseTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PhaseTemplateServiceImpl implements PhaseTemplateService {

    private final PhaseTemplateMapper mapper;

    @Override
    public Page<PhaseTemplate> page(int pageNum, int pageSize) {
        return mapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<PhaseTemplate>().orderByAsc(PhaseTemplate::getSortOrder));
    }

    @Override
    public List<PhaseTemplate> list() {
        return mapper.selectList(
                new LambdaQueryWrapper<PhaseTemplate>().orderByAsc(PhaseTemplate::getSortOrder));
    }

    @Override
    @Transactional
    public PhaseTemplate create(PhaseTemplate t) {
        // F7-90（2026-09-19）：仅在"有租户上下文"时补公司 —— 超管模式（0/null）下
        // strictInsertFill 不兜底，显式写 null 会让该行对所有公司都不可见
        if (t.getCompanyId() == null) {
            Long cid = CompanyContext.get();
            if (cid != null && cid > 0) t.setCompanyId(cid);
        }
        // F7-93（2026-09-19）：同一公司内阶段名称不可重复。
        // 重名会让 ProjectPhaseServiceImpl.checkProductStatusSync 按 name 回查模板时命中多行
        // （那里已改为 LIMIT 1 兜底，但脏数据本身应当从入口拦住），模板维护页也会出现两个同名节点。
        if (mapper.selectCount(buildWrapper().eq(PhaseTemplate::getName, t.getName())) > 0) {
            throw new BusinessException("阶段名称已存在");
        }
        mapper.insert(t);
        return t;
    }

    @Override
    @Transactional
    public void update(PhaseTemplate t) {
        if (t.getId() == null) throw new BusinessException("模板ID不能为空");
        // F7-93：改名时同样校验重名（需排除自身，否则"只改 defaultDays 不动名字"也会被拦下）
        if (t.getName() != null && !t.getName().isBlank()
                && mapper.selectCount(buildWrapper()
                        .eq(PhaseTemplate::getName, t.getName())
                        .ne(PhaseTemplate::getId, t.getId())) > 0) {
            throw new BusinessException("阶段名称已存在");
        }
        mapper.updateById(t);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    /**
     * 租户过滤（与 {@code MaterialTypeServiceImpl.buildWrapper} 同口径）：
     * 超管模式（companyId = 0/null）用于跨公司管理，不加过滤。
     */
    private LambdaQueryWrapper<PhaseTemplate> buildWrapper() {
        Long cid = CompanyContext.get();
        LambdaQueryWrapper<PhaseTemplate> w = new LambdaQueryWrapper<>();
        if (cid != null && cid > 0) {
            w.eq(PhaseTemplate::getCompanyId, cid);
        }
        return w;
    }
}
