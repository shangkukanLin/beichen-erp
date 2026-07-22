package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.Bom;
import com.beichen.erp.dev.entity.dto.BomDTO;
import com.beichen.erp.dev.mapper.BomMapper;
import com.beichen.erp.dev.service.BomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BomServiceImpl extends ServiceImpl<BomMapper, Bom> implements BomService {

    private final BomMapper bomMapper;

    @Override
    public List<Bom> listByProject(Long projectId) {
        LambdaQueryWrapper<Bom> wrapper = new LambdaQueryWrapper<Bom>()
                .eq(Bom::getProjectId, projectId)
                .orderByAsc(Bom::getId);
        return bomMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long projectId, List<BomDTO> items) {
        bomMapper.delete(new LambdaQueryWrapper<Bom>()
                .eq(Bom::getProjectId, projectId));

        if (items == null || items.isEmpty()) {
            return;
        }

        List<Bom> list = new ArrayList<>();
        Long cid = CompanyContext.get();
        for (BomDTO dto : items) {
            Bom bom = new Bom();
            bom.setProjectId(projectId);
            bom.setSupplierId(dto.getSupplierId());
            bom.setMaterialId(dto.getMaterialId());
            bom.setMaterialName(dto.getMaterialName());
            bom.setSpec(dto.getSpec());
            bom.setUnit(dto.getUnit());
            bom.setQuantityPerSet(dto.getQuantityPerSet());
            bom.setLossRate(dto.getLossRate());
            bom.setMaterialType(dto.getMaterialType());
            bom.setRemark(dto.getRemark());
            if (cid != null && cid > 0) bom.setCompanyId(cid);
            list.add(bom);
        }
        this.saveBatch(list);
    }
}
