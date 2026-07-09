package com.beichen.erp.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.material.entity.Material;
import com.beichen.erp.material.entity.MaterialBom;
import com.beichen.erp.material.entity.MaterialBomVO;
import com.beichen.erp.material.entity.MaterialBriefVO;
import com.beichen.erp.material.mapper.MaterialBomMapper;
import com.beichen.erp.material.mapper.MaterialMapper;
import com.beichen.erp.material.service.MaterialBomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MaterialBomServiceImpl extends ServiceImpl<MaterialBomMapper, MaterialBom> implements MaterialBomService {

    private final MaterialMapper materialMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveChildren(Long parentId, List<MaterialBom> children) {
        // 先删除该父物料下已有的直接组成关系，再整体写入（全量替换）
        remove(new LambdaQueryWrapper<MaterialBom>().eq(MaterialBom::getParentMaterialId, parentId));
        if (children == null || children.isEmpty()) {
            return;
        }
        for (MaterialBom item : children) {
            if (item.getChildMaterialId() == null) {
                continue;
            }
            // 禁止自引用（自身作为自身子物料）
            if (item.getChildMaterialId().equals(parentId)) {
                continue;
            }
            item.setId(null);
            item.setParentMaterialId(parentId);
            if (item.getQuantity() == null) {
                item.setQuantity(BigDecimal.ONE);
            }
            if (item.getLossRate() == null) {
                item.setLossRate(BigDecimal.ZERO);
            }
            save(item);
        }
    }

    @Override
    public List<MaterialBomVO> getDirect(Long parentId) {
        List<MaterialBom> list = list(new LambdaQueryWrapper<MaterialBom>()
                .eq(MaterialBom::getParentMaterialId, parentId)
                .orderByAsc(MaterialBom::getId));
        return toVOList(list);
    }

    @Override
    public List<MaterialBomVO> getTree(Long materialId) {
        return buildTree(materialId, new HashSet<>());
    }

    private List<MaterialBomVO> buildTree(Long materialId, Set<Long> visited) {
        // 防止循环引用导致无限递归
        if (visited.contains(materialId)) {
            return new ArrayList<>();
        }
        visited.add(materialId);
        List<MaterialBom> list = list(new LambdaQueryWrapper<MaterialBom>()
                .eq(MaterialBom::getParentMaterialId, materialId)
                .orderByAsc(MaterialBom::getId));
        List<MaterialBomVO> result = new ArrayList<>();
        for (MaterialBom b : list) {
            MaterialBomVO vo = toVO(b);
            vo.setChildren(buildTree(b.getChildMaterialId(), visited));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<MaterialBriefVO> getWhereUsed(Long childId) {
        List<MaterialBom> list = list(new LambdaQueryWrapper<MaterialBom>()
                .eq(MaterialBom::getChildMaterialId, childId));
        Set<Long> parentIds = new LinkedHashSet<>();
        for (MaterialBom b : list) {
            parentIds.add(b.getParentMaterialId());
        }
        List<MaterialBriefVO> result = new ArrayList<>();
        for (Long pid : parentIds) {
            Material m = materialMapper.selectById(pid);
            if (m != null) {
                MaterialBriefVO vo = new MaterialBriefVO();
                vo.setId(m.getId());
                vo.setCode(m.getCode());
                vo.setName(m.getName());
                vo.setSpec(m.getSpec());
                vo.setUnit(m.getUnit());
                vo.setCategory(m.getCategory());
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    public void removeByMaterial(Long materialId) {
        remove(new LambdaQueryWrapper<MaterialBom>()
                .eq(MaterialBom::getParentMaterialId, materialId)
                .or()
                .eq(MaterialBom::getChildMaterialId, materialId));
    }

    private List<MaterialBomVO> toVOList(List<MaterialBom> list) {
        List<MaterialBomVO> result = new ArrayList<>();
        for (MaterialBom b : list) {
            result.add(toVO(b));
        }
        return result;
    }

    private MaterialBomVO toVO(MaterialBom b) {
        MaterialBomVO vo = new MaterialBomVO();
        vo.setId(b.getId());
        vo.setParentMaterialId(b.getParentMaterialId());
        vo.setChildMaterialId(b.getChildMaterialId());
        vo.setQuantity(b.getQuantity());
        vo.setLossRate(b.getLossRate());
        vo.setRemark(b.getRemark());
        // 实时关联子物料主数据，子物料修改后BOM自动同步
        Material m = materialMapper.selectById(b.getChildMaterialId());
        if (m != null) {
            vo.setChildCode(m.getCode());
            vo.setChildName(m.getName());
            vo.setChildSpec(m.getSpec());
            vo.setChildUnit(m.getUnit());
            vo.setChildCategory(m.getCategory());
        }
        return vo;
    }
}
