package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.Bom;
import com.beichen.erp.dev.entity.dto.BomDTO;
import com.beichen.erp.dev.mapper.BomMapper;
import com.beichen.erp.dev.service.BomService;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BomServiceImpl extends ServiceImpl<BomMapper, Bom> implements BomService {

    private final BomMapper bomMapper;
    private final OutsourceMaterialMapper materialMapper;

    @Override
    public List<Bom> listByProject(Long projectId) {
        LambdaQueryWrapper<Bom> wrapper = new LambdaQueryWrapper<Bom>()
                .eq(Bom::getProjectId, projectId)
                .orderByAsc(Bom::getParentId, Bom::getSortOrder, Bom::getId);
        return bomMapper.selectList(wrapper);
    }

    /**
     * 从平铺列表构建树（递归）
     */
    public List<Bom> buildTree(List<Bom> flatList) {
        List<Bom> roots = flatList.stream()
                .filter(b -> b.getParentId() == null || b.getParentId() == 0)
                .collect(Collectors.toList());
        Map<Long, List<Bom>> childrenMap = flatList.stream()
                .filter(b -> b.getParentId() != null && b.getParentId() > 0)
                .collect(Collectors.groupingBy(Bom::getParentId));
        for (Bom root : flatList) {
            root.setChildren(childrenMap.getOrDefault(root.getId(), new ArrayList<>()));
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long projectId, List<BomDTO> items) {
        if (items == null || items.isEmpty()) {
            bomMapper.delete(new LambdaQueryWrapper<Bom>()
                    .eq(Bom::getProjectId, projectId));
            return;
        }

        Long cid = CompanyContext.get();

        // 收集已有 ID 列表和新增项
        Set<Long> existingIds = items.stream()
                .filter(d -> d.getId() != null)
                .map(BomDTO::getId)
                .collect(Collectors.toSet());

        // 删除该项目中不在请求列表里的 BOM
        LambdaQueryWrapper<Bom> deleteWrapper = new LambdaQueryWrapper<Bom>()
                .eq(Bom::getProjectId, projectId);
        if (!existingIds.isEmpty()) {
            deleteWrapper.notIn(Bom::getId, existingIds);
        }
        bomMapper.delete(deleteWrapper);

        List<Bom> toInsert = new ArrayList<>();
        for (BomDTO dto : items) {
            if (dto.getId() != null) {
                // 已存在 → 更新
                Bom bom = new Bom();
                bom.setId(dto.getId());
                bom.setParentId(dto.getParentId());
                bom.setSortOrder(dto.getSortOrder());
                bom.setSupplierId(dto.getSupplierId());
                bom.setMaterialName(dto.getMaterialName());
                bom.setUnit(dto.getUnit());
                bom.setQuantityPerSet(dto.getQuantityPerSet());
                bom.setLossRate(dto.getLossRate());
                bom.setMaterialType(dto.getMaterialType());
                bom.setRemark(dto.getRemark());
                if (cid != null && cid > 0) bom.setCompanyId(cid);
                bomMapper.updateById(bom);
            } else {
                // 新增
                Bom bom = new Bom();
                bom.setProjectId(projectId);
                bom.setParentId(dto.getParentId());
                bom.setSortOrder(dto.getSortOrder());
                bom.setSupplierId(dto.getSupplierId());
                bom.setMaterialName(dto.getMaterialName());
                bom.setUnit(dto.getUnit());
                bom.setQuantityPerSet(dto.getQuantityPerSet());
                bom.setLossRate(dto.getLossRate());
                bom.setMaterialType(dto.getMaterialType());
                bom.setRemark(dto.getRemark());
                if (cid != null && cid > 0) bom.setCompanyId(cid);
                toInsert.add(bom);
            }
        }
        if (!toInsert.isEmpty()) {
            this.saveBatch(toInsert);
        }
        // 双向同步：BOM 树 → 物料信息的 parentMaterialId + spec/unit
        syncToOutsourceMaterial(projectId, items, toInsert);
    }

    private void syncToOutsourceMaterial(Long projectId, List<BomDTO> items, List<Bom> inserted) {
        List<OutsourceMaterial> materials = materialMapper.selectList(null);
        Map<String, OutsourceMaterial> nameTypeMap = new HashMap<>();
        for (OutsourceMaterial m : materials) {
            nameTypeMap.put(m.getMaterialName() + "|" + (m.getMaterialType() != null ? m.getMaterialType() : ""), m);
        }

        // 用 BOM 数据更新物料信息：spec/unit + parentMaterialId
        // 构建名称→BOM parentId 映射
        Map<String, Long> nameToParentBomId = new HashMap<>();
        for (BomDTO dto : items) {
            String k = dto.getMaterialName() + "|" + (dto.getMaterialType() != null ? dto.getMaterialType() : "");
            nameToParentBomId.put(k, dto.getParentId() != null ? dto.getParentId() : 0L);
        }

        for (BomDTO dto : items) {
            String key = dto.getMaterialName() + "|" + (dto.getMaterialType() != null ? dto.getMaterialType() : "");
            OutsourceMaterial mat = nameTypeMap.get(key);
            if (mat == null) continue;

            boolean ch = false;
            if (dto.getUnit() != null && !dto.getUnit().equals(mat.getUnit())) { mat.setUnit(dto.getUnit()); ch = true; }

            // 从 BOM 父节点找到物料信息的父 ID
            Long bomPid = dto.getParentId() != null && dto.getParentId() > 0 ? dto.getParentId() : 0L;
            Long expPid = 0L;
            if (bomPid > 0) {
                // 找到 parentId == bomPid 的 DTO
                String parentName = null, parentType = null;
                for (BomDTO p : items) {
                    if (p.getId() != null && p.getId().equals(bomPid)) {
                        parentName = p.getMaterialName(); parentType = p.getMaterialType(); break;
                    }
                }
                if (parentName != null) {
                    String pk = parentName + "|" + (parentType != null ? parentType : "");
                    OutsourceMaterial pm = nameTypeMap.get(pk);
                    if (pm != null) expPid = pm.getId();
                }
            }
            Long curPid = mat.getParentMaterialId() != null ? mat.getParentMaterialId() : 0L;
            if (!expPid.equals(curPid)) { mat.setParentMaterialId(expPid); ch = true; }

            if (ch) materialMapper.updateById(mat);
        }
    }
}
