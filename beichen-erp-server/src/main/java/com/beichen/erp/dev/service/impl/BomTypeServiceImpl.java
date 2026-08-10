package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.dev.service.BomTypeService;
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
public class BomTypeServiceImpl implements BomTypeService {

    private final BomTypeMapper bomTypeMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;

    @Override
    public LambdaQueryWrapper<BomType> buildWrapper() {
        Long companyId = CompanyContext.get();
        LambdaQueryWrapper<BomType> wrapper = new LambdaQueryWrapper<>();
        // 超管验证流程 companyId=0 用于跨公司管理，不加过滤
        if (companyId != null && companyId > 0) {
            wrapper.eq(BomType::getCompanyId, companyId);
        }
        return wrapper;
    }

    @Override
    public List<BomType> enabled() {
        return bomTypeMapper.selectList(buildWrapper()
                .eq(BomType::getStatus, 1).orderByAsc(BomType::getSortOrder));
    }

    @Override
    public Page<BomType> page(int pageNum, int pageSize) {
        return bomTypeMapper.selectPage(new Page<>(pageNum, pageSize),
                buildWrapper().orderByAsc(BomType::getSortOrder));
    }

    @Override
    @Transactional
    public void add(BomType type) {
        // 同一公司内类型名称不可重复
        if (bomTypeMapper.selectCount(buildWrapper()
                .eq(BomType::getTypeName, type.getTypeName())) > 0) {
            throw new BusinessException("类型名称已存在");
        }
        if (type.getStatus() == null) type.setStatus(1);
        if (type.getSortOrder() == null) type.setSortOrder(0);
        type.setCompanyId(CompanyContext.get());
        bomTypeMapper.insert(type);
    }

    @Override
    @Transactional
    public void update(BomType type) {
        // 物料仅存 bom_type_id 指向类型，类型改名不影响 ID，无需同步物料
        bomTypeMapper.updateById(type);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        BomType type = bomTypeMapper.selectById(id);
        if (type != null) {
            // 按 bom_type_id 统计该类型下是否还有外协物料，避免误删
            Long count = outsourceMaterialMapper.selectCount(
                    new LambdaQueryWrapper<OutsourceMaterial>().eq(OutsourceMaterial::getBomTypeId, type.getId()));
            if (count != null && count > 0) {
                throw new BusinessException("该类型下还有 " + count + " 个物料，请先处理后再删除");
            }
        }
        bomTypeMapper.deleteById(id);
    }
}
