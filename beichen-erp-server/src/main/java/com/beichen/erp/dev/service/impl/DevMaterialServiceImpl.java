package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.dev.entity.DevMaterial;
import com.beichen.erp.dev.mapper.DevMaterialMapper;
import com.beichen.erp.dev.service.DevMaterialService;
import com.beichen.erp.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DevMaterialServiceImpl extends ServiceImpl<DevMaterialMapper, DevMaterial>
        implements DevMaterialService {

    @Override
    public List<DevMaterial> listByProjectId(Long projectId) {
        return list(new LambdaQueryWrapper<DevMaterial>()
                .eq(DevMaterial::getProjectId, projectId)
                .orderByDesc(DevMaterial::getId));
    }

    @Override
    @Transactional
    public void add(DevMaterial material) {
        if (material.getMaterialName() == null || material.getMaterialName().isBlank()) {
            throw new BusinessException("物料名称不能为空");
        }
        material.setId(null);
        save(material);
    }

    @Override
    @Transactional
    public void update(DevMaterial material) {
        if (material.getId() == null) {
            throw new BusinessException("物料ID不能为空");
        }
        updateById(material);
    }

    @Override
    @Transactional
    public void remove(Long id) {
        removeById(id);
    }
}
