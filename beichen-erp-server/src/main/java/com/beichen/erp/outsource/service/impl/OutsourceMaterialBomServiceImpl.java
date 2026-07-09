package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceMaterialBom;
import com.beichen.erp.outsource.entity.OutsourceMaterialBomVO;
import com.beichen.erp.outsource.entity.OutsourceMaterialBriefVO;
import com.beichen.erp.outsource.mapper.OutsourceMaterialBomMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.service.OutsourceMaterialBomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OutsourceMaterialBomServiceImpl extends ServiceImpl<OutsourceMaterialBomMapper, OutsourceMaterialBom>
        implements OutsourceMaterialBomService {

    private final OutsourceMaterialMapper materialMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveChildren(Long parentId, List<OutsourceMaterialBom> children) {
        remove(new LambdaQueryWrapper<OutsourceMaterialBom>().eq(OutsourceMaterialBom::getParentMaterialId, parentId));
        if (children == null || children.isEmpty()) return;
        for (OutsourceMaterialBom item : children) {
            if (item.getChildMaterialId() == null || item.getChildMaterialId().equals(parentId)) continue;
            item.setId(null);
            item.setParentMaterialId(parentId);
            if (item.getQuantity() == null) item.setQuantity(BigDecimal.ONE);
            if (item.getLossRate() == null) item.setLossRate(BigDecimal.ZERO);
            save(item);
        }
    }

    @Override
    public List<OutsourceMaterialBomVO> getDirect(Long parentId) {
        return toVOList(list(new LambdaQueryWrapper<OutsourceMaterialBom>()
                .eq(OutsourceMaterialBom::getParentMaterialId, parentId).orderByAsc(OutsourceMaterialBom::getId)));
    }

    @Override
    public List<OutsourceMaterialBomVO> getTree(Long materialId) {
        return buildTree(materialId, new HashSet<>());
    }

    private List<OutsourceMaterialBomVO> buildTree(Long id, Set<Long> visited) {
        if (visited.contains(id)) return new ArrayList<>();
        visited.add(id);
        List<OutsourceMaterialBom> list = list(new LambdaQueryWrapper<OutsourceMaterialBom>()
                .eq(OutsourceMaterialBom::getParentMaterialId, id).orderByAsc(OutsourceMaterialBom::getId));
        List<OutsourceMaterialBomVO> result = new ArrayList<>();
        for (OutsourceMaterialBom b : list) {
            OutsourceMaterialBomVO vo = toVO(b);
            vo.setChildren(buildTree(b.getChildMaterialId(), visited));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<OutsourceMaterialBriefVO> getWhereUsed(Long childId) {
        List<OutsourceMaterialBom> list = list(new LambdaQueryWrapper<OutsourceMaterialBom>()
                .eq(OutsourceMaterialBom::getChildMaterialId, childId));
        Set<Long> ids = new LinkedHashSet<>();
        for (OutsourceMaterialBom b : list) ids.add(b.getParentMaterialId());
        List<OutsourceMaterialBriefVO> result = new ArrayList<>();
        for (Long pid : ids) {
            OutsourceMaterial m = materialMapper.selectById(pid);
            if (m != null) {
                OutsourceMaterialBriefVO vo = new OutsourceMaterialBriefVO();
                vo.setId(m.getId()); vo.setMaterialName(m.getMaterialName());
                vo.setMaterialType(m.getMaterialType()); vo.setSpec(m.getSpec()); vo.setUnit(m.getUnit());
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    public void removeByMaterial(Long materialId) {
        remove(new LambdaQueryWrapper<OutsourceMaterialBom>()
                .eq(OutsourceMaterialBom::getParentMaterialId, materialId).or()
                .eq(OutsourceMaterialBom::getChildMaterialId, materialId));
    }

    @Override
    public Map<String, List<OutsourceMaterialBomVO>> getChildrenByNames(List<String> materialNames) {
        Map<String, List<OutsourceMaterialBomVO>> result = new LinkedHashMap<>();
        for (String name : materialNames) {
            Long id = materialMapper.findIdByName(name);
            if (id != null) {
                List<OutsourceMaterialBomVO> children = getDirect(id);
                if (!children.isEmpty()) {
                    result.put(name, children);
                }
            }
        }
        return result;
    }

    private List<OutsourceMaterialBomVO> toVOList(List<OutsourceMaterialBom> list) {
        List<OutsourceMaterialBomVO> r = new ArrayList<>();
        for (OutsourceMaterialBom b : list) r.add(toVO(b));
        return r;
    }

    private OutsourceMaterialBomVO toVO(OutsourceMaterialBom b) {
        OutsourceMaterialBomVO vo = new OutsourceMaterialBomVO();
        vo.setId(b.getId()); vo.setParentMaterialId(b.getParentMaterialId());
        vo.setChildMaterialId(b.getChildMaterialId());
        vo.setQuantity(b.getQuantity()); vo.setLossRate(b.getLossRate()); vo.setRemark(b.getRemark());
        OutsourceMaterial m = materialMapper.selectById(b.getChildMaterialId());
        if (m != null) {
            vo.setChildName(m.getMaterialName()); vo.setChildType(m.getMaterialType());
            vo.setChildSpec(m.getSpec()); vo.setChildUnit(m.getUnit());
            vo.setChildSupplierName(m.getSupplierName());
        }
        return vo;
    }
}
