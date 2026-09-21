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
    public Page<PhaseTemplate> page(int pageNum, int pageSize, String specType) {
        return mapper.selectPage(new Page<>(pageNum, pageSize),
                buildWrapper(specType).orderByAsc(PhaseTemplate::getSortOrder));
    }

    @Override
    public List<PhaseTemplate> list(String specType) {
        return mapper.selectList(
                buildWrapper(specType).orderByAsc(PhaseTemplate::getSortOrder));
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
        // 2026-09-21：规格归一（空 / 非法 ⇒ 改配），保证新行一定带规格
        String specType = PhaseTemplate.normalizeSpecType(t.getSpecType());
        t.setSpecType(specType);
        // F7-93（2026-09-19）：阶段名称不可重复。2026-09-21 起口径变为 **同公司 + 同规格内**唯一
        // （跨规格允许同名 —— 这正是"两套模板"成立的前提，DB 侧 uk_company_spec_name 兜底）。
        // 重名会让 needsProductStatusSync 回查模板时命中多行，模板维护页也会出现两个同名节点。
        if (mapper.selectCount(buildWrapper(specType).eq(PhaseTemplate::getName, t.getName())) > 0) {
            throw new BusinessException("该规格下阶段名称已存在：" + t.getName());
        }
        mapper.insert(t);
        return t;
    }

    @Override
    @Transactional
    public void update(PhaseTemplate t) {
        if (t.getId() == null) throw new BusinessException("模板ID不能为空");
        // 规格：没传就沿用库中该行的（编辑弹窗理论上都会带，但接口要自洽）；传了就归一
        String specType = t.getSpecType();
        if (specType == null || specType.isBlank()) {
            PhaseTemplate old = mapper.selectById(t.getId());
            specType = old == null ? null : old.getSpecType();
        } else {
            t.setSpecType(PhaseTemplate.normalizeSpecType(specType));
        }
        // F7-93：改名时同样校验重名（需排除自身，否则"只改 defaultDays 不动名字"也会被拦下）；
        // 2026-09-21 起同样限定在**同一规格**内
        if (t.getName() != null && !t.getName().isBlank() && specType != null) {
            if (mapper.selectCount(buildWrapper(specType)
                    .eq(PhaseTemplate::getName, t.getName())
                    .ne(PhaseTemplate::getId, t.getId())) > 0) {
                throw new BusinessException("该规格下阶段名称已存在：" + t.getName());
            }
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
     *
     * @param specType 为空 ⇒ **不按规格过滤**（返回两套：原配 + 改配），保持旧调用方语义不变
     */
    private LambdaQueryWrapper<PhaseTemplate> buildWrapper(String specType) {
        Long cid = CompanyContext.get();
        LambdaQueryWrapper<PhaseTemplate> w = new LambdaQueryWrapper<>();
        if (cid != null && cid > 0) {
            w.eq(PhaseTemplate::getCompanyId, cid);
        }
        if (specType != null && !specType.isBlank()) {
            w.eq(PhaseTemplate::getSpecType, PhaseTemplate.normalizeSpecType(specType));
        }
        return w;
    }
}
