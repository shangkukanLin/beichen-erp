package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
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
        if (t.getCompanyId() == null) t.setCompanyId(CompanyContext.get());
        mapper.insert(t);
        return t;
    }

    @Override
    @Transactional
    public void update(PhaseTemplate t) {
        mapper.updateById(t);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
