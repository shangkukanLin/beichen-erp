package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.dev.entity.Bom;
import com.beichen.erp.dev.mapper.BomMapper;
import com.beichen.erp.dev.service.BomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BomServiceImpl extends ServiceImpl<BomMapper, Bom> implements BomService {

    @Override
    public List<Bom> listByProject(Long projectId) {
        // 默认返回最新版本
        Integer maxVersion = getMaxVersion(projectId);
        return listByProjectAndVersion(projectId, maxVersion);
    }

    @Override
    public List<Bom> listByProjectAndVersion(Long projectId, Integer version) {
        LambdaQueryWrapper<Bom> w = new LambdaQueryWrapper<>();
        w.eq(Bom::getProjectId, projectId);
        if (version != null) {
            w.eq(Bom::getVersion, version);
        }
        w.orderByAsc(Bom::getId);
        return baseMapper.selectList(w);
    }

    @Override
    public List<Integer> getVersions(Long projectId) {
        return baseMapper.selectList(
                new LambdaQueryWrapper<Bom>()
                        .select(Bom::getVersion)
                        .eq(Bom::getProjectId, projectId)
                        .groupBy(Bom::getVersion)
                        .orderByDesc(Bom::getVersion))
                .stream().map(Bom::getVersion).toList();
    }

    @Override
    public Integer getMaxVersion(Long projectId) {
        Bom bom = baseMapper.selectOne(
                new LambdaQueryWrapper<Bom>()
                        .eq(Bom::getProjectId, projectId)
                        .orderByDesc(Bom::getVersion)
                        .last("LIMIT 1"));
        return bom != null ? bom.getVersion() : 1;
    }

    @Override
    @Transactional
    public List<Bom> createNewVersion(Long projectId) {
        // 获取当前最新版本的BOM
        Integer currentMax = getMaxVersion(projectId);
        List<Bom> currentBoms = listByProjectAndVersion(projectId, currentMax);

        // 复制为新版本
        int newVersion = currentMax + 1;
        for (Bom bom : currentBoms) {
            Bom newBom = new Bom();
            newBom.setProjectId(bom.getProjectId());
            newBom.setBomTypeId(bom.getBomTypeId());
            newBom.setOutsourceMaterialId(bom.getOutsourceMaterialId());
            newBom.setSupplierId(bom.getSupplierId());
            newBom.setQuantity(bom.getQuantity());
            newBom.setLossRate(bom.getLossRate());
            newBom.setSpecification(bom.getSpecification());
            newBom.setUnit(bom.getUnit());
            newBom.setVersion(newVersion);
            baseMapper.insert(newBom);
        }
        return listByProjectAndVersion(projectId, newVersion);
    }
}
