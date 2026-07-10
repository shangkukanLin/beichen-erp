package com.beichen.erp.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.material.entity.MaterialBom;
import com.beichen.erp.material.mapper.MaterialBomMapper;
import com.beichen.erp.material.service.MaterialBomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MaterialBomServiceImpl extends ServiceImpl<MaterialBomMapper, MaterialBom>
        implements MaterialBomService {

    @Override
    @Transactional
    public void saveChildren(Long materialId, List<MaterialBom> children) {
        // 先删除旧关系
        remove(new LambdaQueryWrapper<MaterialBom>()
                .eq(MaterialBom::getParentMaterialId, materialId));
        // 再插入新关系
        children.forEach(child -> {
            child.setParentMaterialId(materialId);
            child.setId(null);
        });
        saveBatch(children);
    }

    @Override
    @Transactional
    public void removeByMaterial(Long materialId) {
        remove(new LambdaQueryWrapper<MaterialBom>()
                .eq(MaterialBom::getParentMaterialId, materialId)
                .or()
                .eq(MaterialBom::getChildMaterialId, materialId));
    }
}
