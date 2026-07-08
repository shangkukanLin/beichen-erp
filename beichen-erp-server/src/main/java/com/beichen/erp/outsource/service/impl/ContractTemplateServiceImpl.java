package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.entity.ContractTemplate;
import com.beichen.erp.outsource.mapper.ContractTemplateMapper;
import com.beichen.erp.outsource.service.ContractTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Service
@RequiredArgsConstructor
public class ContractTemplateServiceImpl implements ContractTemplateService {

    private final ContractTemplateMapper templateMapper;

    @Override
    public List<ContractTemplate> list() {
        return templateMapper.selectList(new LambdaQueryWrapper<ContractTemplate>()
                .orderByDesc(ContractTemplate::getId));
    }

    @Override
    public ContractTemplate getById(Long id) {
        return templateMapper.selectById(id);
    }

    @Override
    public ContractTemplate getDefault() {
        return templateMapper.selectOne(new LambdaQueryWrapper<ContractTemplate>()
                .eq(ContractTemplate::getIsDefault, 1)
                .eq(ContractTemplate::getStatus, 1)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ContractTemplate template) {
        if (template.getTemplateName() == null || template.getTemplateName().isBlank()) {
            throw new BusinessException("模板名称不能为空");
        }
        templateMapper.insert(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ContractTemplate template) {
        if (template.getId() == null) throw new BusinessException("模板ID不能为空");
        if (template.getTemplateName() == null || template.getTemplateName().isBlank()) {
            throw new BusinessException("模板名称不能为空");
        }
        templateMapper.updateById(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        templateMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        // 清除所有默认
        templateMapper.update(null, new LambdaUpdateWrapper<ContractTemplate>()
                .set(ContractTemplate::getIsDefault, 0));
        // 设置当前为默认
        ContractTemplate update = new ContractTemplate();
        update.setId(id);
        update.setIsDefault(1);
        templateMapper.updateById(update);
    }
}
