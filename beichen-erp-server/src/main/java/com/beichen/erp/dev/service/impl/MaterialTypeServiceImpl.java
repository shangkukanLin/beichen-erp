package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.MaterialType;
import com.beichen.erp.dev.mapper.MaterialTypeMapper;
import com.beichen.erp.dev.service.MaterialTypeService;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialTypeServiceImpl implements MaterialTypeService {

    private final MaterialTypeMapper materialTypeMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;

    @Override
    public LambdaQueryWrapper<MaterialType> buildWrapper() {
        Long companyId = CompanyContext.get();
        LambdaQueryWrapper<MaterialType> wrapper = new LambdaQueryWrapper<>();
        // 超管验证流程 companyId=0 用于跨公司管理，不加过滤
        if (companyId != null && companyId > 0) {
            wrapper.eq(MaterialType::getCompanyId, companyId);
        }
        return wrapper;
    }

    @Override
    public List<MaterialType> enabled() {
        return materialTypeMapper.selectList(buildWrapper()
                .eq(MaterialType::getStatus, 1).orderByAsc(MaterialType::getSortOrder));
    }

    @Override
    public Page<MaterialType> page(int pageNum, int pageSize) {
        return materialTypeMapper.selectPage(new Page<>(pageNum, pageSize),
                buildWrapper().orderByAsc(MaterialType::getSortOrder));
    }

    @Override
    @Transactional
    public void add(MaterialType type) {
        // 同一公司内类型名称不可重复
        if (materialTypeMapper.selectCount(buildWrapper()
                .eq(MaterialType::getTypeName, type.getTypeName())) > 0) {
            throw new BusinessException("类型名称已存在");
        }
        if (type.getStatus() == null) type.setStatus(1);
        if (type.getSortOrder() == null) type.setSortOrder(0);
        // F7-90（2026-09-19）：仅在"有租户上下文"时写公司（超管模式下 strictInsertFill 不兜底，
        // 显式写 null/0 会让该行对所有公司都不可见）
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) type.setCompanyId(cid);
        materialTypeMapper.insert(type);
    }

    @Override
    @Transactional
    public void update(MaterialType type) {
        if (type.getId() == null) throw new BusinessException("类型ID不能为空");
        // F7-91（2026-09-19）：改名同样校验同公司重名 —— 原先 update 完全无校验，可以把 A 类型
        // 改成与 B 同名，之后 ProjectServiceImpl.materialTypeIdMap 的同名映射会在两条之间漂移
        // （改配联动可能挂到"另一个"类型上）
        if (type.getTypeName() != null && !type.getTypeName().isBlank()
                && materialTypeMapper.selectCount(buildWrapper()
                        .eq(MaterialType::getTypeName, type.getTypeName())
                        .ne(MaterialType::getId, type.getId())) > 0) {
            throw new BusinessException("类型名称已存在");
        }
        // 物料仅存 material_type_id 指向类型，类型改名不影响 ID，无需同步物料
        materialTypeMapper.updateById(type);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        MaterialType type = materialTypeMapper.selectById(id);
        if (type != null) {
            // 默认类型不可删除
            if (type.getIsDefault() != null && type.getIsDefault() == 1) {
                throw new BusinessException("默认类型不可删除");
            }
            // 按 material_type_id 统计该类型下是否还有外协物料，避免误删
            Long count = outsourceMaterialMapper.selectCount(
                    new LambdaQueryWrapper<OutsourceMaterial>().eq(OutsourceMaterial::getMaterialTypeId, type.getId()));
            if (count != null && count > 0) {
                throw new BusinessException("该类型下还有 " + count + " 个物料，请先处理后再删除");
            }
        }
        materialTypeMapper.deleteById(id);
    }
}
