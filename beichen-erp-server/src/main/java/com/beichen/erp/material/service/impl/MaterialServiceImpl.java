package com.beichen.erp.material.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.material.entity.Material;
import com.beichen.erp.material.mapper.MaterialMapper;
import com.beichen.erp.material.service.MaterialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class MaterialServiceImpl extends ServiceImpl<MaterialMapper, Material> implements MaterialService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public boolean updateById(Material entity) {
        // 更新前加载旧数据，判断名称是否变更
        Material old = entity.getId() != null ? getById(entity.getId()) : null;
        boolean result = super.updateById(entity);
        // 如果产品关联了研发项目且名称发生了变更，同步更新项目的总成名称
        if (result && old != null && old.getProjectId() != null
                && entity.getName() != null && !entity.getName().equals(old.getName())) {
            jdbcTemplate.update("UPDATE dev_project SET assembly_name = ? WHERE id = ?",
                    entity.getName(), old.getProjectId());
            log.info("产品名称变更，已同步项目(ID={})的总成名称: {} → {}", old.getProjectId(), old.getName(), entity.getName());
        }
        return result;
    }
}
