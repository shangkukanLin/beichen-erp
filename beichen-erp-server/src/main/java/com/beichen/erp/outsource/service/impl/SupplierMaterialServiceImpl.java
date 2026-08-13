package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.SupplierMaterial;
import com.beichen.erp.outsource.entity.dto.SupplierMaterialDTO;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.mapper.SupplierMaterialMapper;
import com.beichen.erp.outsource.service.SupplierMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierMaterialServiceImpl extends ServiceImpl<SupplierMaterialMapper, SupplierMaterial> implements SupplierMaterialService {

    private final SupplierMaterialMapper supplierMaterialMapper;
    private final OutsourceMaterialMapper materialMapper;
    private final BomTypeMapper bomTypeMapper;

    @Override
    public List<SupplierMaterial> listBySupplierId(Long supplierId) {
        LambdaQueryWrapper<SupplierMaterial> wrapper = new LambdaQueryWrapper<SupplierMaterial>()
                .eq(SupplierMaterial::getSupplierId, supplierId)
                .orderByAsc(SupplierMaterial::getId);
        List<SupplierMaterial> list = supplierMaterialMapper.selectList(wrapper);
        // 联查 outsource_material 填充物料名称/规格/BOM类型名
        fillMaterialInfo(list);
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMaterials(Long supplierId, List<SupplierMaterialDTO> materials) {
        if (materials == null || materials.isEmpty()) {
            // 入参为空仍保留历史关联（差量更新语义：不清空）
            return;
        }
        // 差量更新：按 materialId 比对，新增的 insert、已存在的 update、多余的 delete
        List<SupplierMaterial> existing = supplierMaterialMapper.selectList(
                new LambdaQueryWrapper<SupplierMaterial>().eq(SupplierMaterial::getSupplierId, supplierId));
        Map<Long, SupplierMaterial> existMap = existing.stream()
                .collect(Collectors.toMap(SupplierMaterial::getMaterialId, m -> m, (a, b) -> a));

        List<SupplierMaterial> toInsert = new ArrayList<>();
        List<SupplierMaterial> toUpdate = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        Long cid = CompanyContext.get();
        for (SupplierMaterialDTO dto : materials) {
            if (dto.getMaterialId() == null) continue;
            seen.add(dto.getMaterialId());
            SupplierMaterial exist = existMap.get(dto.getMaterialId());
            if (exist != null) {
                exist.setUnitPrice(dto.getUnitPrice());
                exist.setRemark(dto.getRemark());
                toUpdate.add(exist);
            } else {
                SupplierMaterial sm = new SupplierMaterial();
                sm.setSupplierId(supplierId);
                sm.setMaterialId(dto.getMaterialId());
                sm.setUnitPrice(dto.getUnitPrice());
                sm.setRemark(dto.getRemark());
                if (cid != null && cid > 0) sm.setCompanyId(cid);
                toInsert.add(sm);
            }
        }
        List<Long> toDelete = existing.stream()
                .filter(m -> !seen.contains(m.getMaterialId()))
                .map(SupplierMaterial::getId)
                .collect(Collectors.toList());

        if (!toInsert.isEmpty()) this.saveBatch(toInsert);
        if (!toUpdate.isEmpty()) this.updateBatchById(toUpdate);
        if (!toDelete.isEmpty()) this.removeByIds(toDelete);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMaterialsByMaterial(Long materialId, List<SupplierMaterialDTO> materials) {
        List<SupplierMaterial> existing = supplierMaterialMapper.selectList(
                new LambdaQueryWrapper<SupplierMaterial>().eq(SupplierMaterial::getMaterialId, materialId));
        Map<Long, SupplierMaterial> existMap = existing.stream()
                .collect(Collectors.toMap(SupplierMaterial::getSupplierId, m -> m, (a, b) -> a));

        List<SupplierMaterial> toInsert = new ArrayList<>();
        List<SupplierMaterial> toUpdate = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        Long cid = CompanyContext.get();
        for (SupplierMaterialDTO dto : materials) {
            if (dto.getSupplierId() == null) continue;
            seen.add(dto.getSupplierId());
            SupplierMaterial exist = existMap.get(dto.getSupplierId());
            if (exist != null) {
                exist.setUnitPrice(dto.getUnitPrice());
                exist.setRemark(dto.getRemark());
                toUpdate.add(exist);
            } else {
                SupplierMaterial sm = new SupplierMaterial();
                sm.setSupplierId(dto.getSupplierId());
                sm.setMaterialId(materialId);
                sm.setUnitPrice(dto.getUnitPrice());
                sm.setRemark(dto.getRemark());
                if (cid != null && cid > 0) sm.setCompanyId(cid);
                toInsert.add(sm);
            }
        }
        List<Long> toDelete = existing.stream()
                .filter(m -> !seen.contains(m.getSupplierId()))
                .map(SupplierMaterial::getId)
                .collect(Collectors.toList());

        if (!toInsert.isEmpty()) this.saveBatch(toInsert);
        if (!toUpdate.isEmpty()) this.updateBatchById(toUpdate);
        if (!toDelete.isEmpty()) this.removeByIds(toDelete);
    }

    /** 按物料ID生成供应商ID逗号串（供 outsource_material 列表回填 supplierIds 字段，保持前端兼容） */
    public String listSupplierIdsByMaterial(Long materialId) {
        if (materialId == null) return "";
        List<SupplierMaterial> list = supplierMaterialMapper.selectList(
                new LambdaQueryWrapper<SupplierMaterial>()
                        .eq(SupplierMaterial::getMaterialId, materialId)
                        .orderByAsc(SupplierMaterial::getId));
        return list.stream().map(m -> String.valueOf(m.getSupplierId()))
                .distinct().collect(Collectors.joining(","));
    }

    /** 通过 materialId 批量填充物料名称/规格/BOM类型名 */
    private void fillMaterialInfo(List<SupplierMaterial> list) {
        List<Long> materialIds = list.stream().map(SupplierMaterial::getMaterialId)
                .filter(id -> id != null).distinct().collect(Collectors.toList());
        if (materialIds.isEmpty()) return;
        List<OutsourceMaterial> materials = materialMapper.selectBatchIds(materialIds);
        Map<Long, OutsourceMaterial> materialMap = materials.stream()
                .collect(Collectors.toMap(OutsourceMaterial::getId, m -> m));
        for (SupplierMaterial sm : list) {
            if (sm.getMaterialId() != null) {
                OutsourceMaterial m = materialMap.get(sm.getMaterialId());
                if (m != null) {
                    sm.setMaterialName(m.getMaterialName());
                    sm.setSpec(m.getSpec());
                    if (m.getBomTypeId() != null) {
                        BomType bt = bomTypeMapper.selectById(m.getBomTypeId());
                        sm.setBomTypeName(bt != null ? bt.getTypeName() : "-");
                    } else {
                        sm.setBomTypeName("-");
                    }
                }
            }
        }
    }
}
