package com.beichen.erp.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.entity.dto.SupplierDTO;
import com.beichen.erp.supplier.entity.dto.SupplierQueryDTO;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.supplier.service.SupplierService;
import com.beichen.erp.outsource.entity.OutsourceWarehouse;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements SupplierService {

    private final SupplierMapper supplierMapper;
    private final OutsourceWarehouseMapper warehouseMapper;

    @Override
    public Page<Supplier> page(SupplierQueryDTO query) {
        Page<Supplier> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<Supplier>()
                .eq(query.getSupplierType() != null && !query.getSupplierType().isBlank(),
                        Supplier::getSupplierType, query.getSupplierType())
                .like(query.getName() != null && !query.getName().isBlank(),
                        Supplier::getName, query.getName())
                .like(query.getPhone() != null && !query.getPhone().isBlank(),
                        Supplier::getPhone, query.getPhone())
                .eq(query.getStatus() != null, Supplier::getStatus, query.getStatus())
                .orderByDesc(Supplier::getId);
        return supplierMapper.selectPage(page, wrapper);
    }

    @Override
    public String generateCode(String type) {
        String prefix = getPrefixByType(type);
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String likePattern = prefix + "-" + dateStr;

        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<Supplier>()
                .likeRight(Supplier::getCode, likePattern)
                .orderByDesc(Supplier::getCode)
                .last("LIMIT 1");
        Supplier last = supplierMapper.selectOne(wrapper);

        int seq = 1;
        if (last != null && last.getCode() != null) {
            String code = last.getCode();
            try {
                // 取最后3位序号（编码格式: PREFIX-YYYYMMDDNNN）
                String numPart = code.substring(code.length() - 3);
                seq = Integer.parseInt(numPart) + 1;
            } catch (Exception e) {
                seq = 1;
            }
        }

        return prefix + "-" + dateStr + String.format("%03d", seq);
    }

    @Override
    public void create(SupplierDTO dto) {
        // 检查同名供应商是否已存在
        LambdaQueryWrapper<Supplier> checkWrapper = new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getName, dto.getName())
                .eq(Supplier::getSupplierType, dto.getSupplierType());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) checkWrapper.eq(Supplier::getCompanyId, cid);
        if (supplierMapper.selectCount(checkWrapper) > 0) {
            throw new BusinessException("供应商名称「" + dto.getName() + "」已存在，请勿重复添加");
        }

        Supplier supplier = new Supplier();
        BeanUtils.copyProperties(dto, supplier);
        // 带重试，防止并发时编码冲突
        int seq = 0;
        String prefix = getPrefixByType(dto.getSupplierType());
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        for (int i = 0; i < 3; i++) {
            try {
                if (i == 0) {
                    seq = getMaxSeq(prefix, dateStr) + 1;
                } else {
                    seq++;
                }
                supplier.setCode(prefix + "-" + dateStr + String.format("%03d", seq));
                // 设置公司ID
                if (cid != null && cid > 0) supplier.setCompanyId(cid);
                supplierMapper.insert(supplier);
                // 委外加工厂自动创建默认仓库
                if ("factory".equals(dto.getSupplierType())) {
                    OutsourceWarehouse wh = new OutsourceWarehouse();
                    wh.setFactoryId(supplier.getId());
                    wh.setWarehouseName(supplier.getName() + "仓库");
                    wh.setAddress(supplier.getAddress());
                    wh.setContact(supplier.getContact());
                    wh.setPhone(supplier.getPhone());
                    wh.setStatus(1);
                    if (cid != null && cid > 0) wh.setCompanyId(cid);
                    warehouseMapper.insert(wh);
                }
                return;
            } catch (DuplicateKeyException e) {
                if (i >= 2) throw e;
            }
        }
    }

    private int getMaxSeq(String prefix, String dateStr) {
        String likePattern = prefix + "-" + dateStr;
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<Supplier>()
                .likeRight(Supplier::getCode, likePattern)
                .orderByDesc(Supplier::getCode)
                .last("LIMIT 1");
        Supplier last = supplierMapper.selectOne(wrapper);
        if (last != null && last.getCode() != null) {
            try {
                String code = last.getCode();
                return Integer.parseInt(code.substring(code.length() - 3));
            } catch (Exception ignored) {}
        }
        return 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SupplierDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("供应商ID不能为空");
        }
        Supplier exist = supplierMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException("供应商不存在");
        }
        // 检查名称是否与同类型其他供应商重复
        LambdaQueryWrapper<Supplier> checkWrapper = new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getName, dto.getName())
                .eq(Supplier::getSupplierType, exist.getSupplierType())
                .ne(Supplier::getId, dto.getId());
        Long cid2 = CompanyContext.get();
        if (cid2 != null && cid2 > 0) checkWrapper.eq(Supplier::getCompanyId, cid2);
        if (supplierMapper.selectCount(checkWrapper) > 0) {
            throw new BusinessException("供应商名称「" + dto.getName() + "」已存在");
        }
        Supplier supplier = new Supplier();
        BeanUtils.copyProperties(dto, supplier);
        supplier.setCode(exist.getCode());
        supplierMapper.updateById(supplier);

        if (dto.getRelatedSupplierId() != null) {
            Supplier related = supplierMapper.selectById(dto.getRelatedSupplierId());
            if (related == null) {
                throw new BusinessException("关联供应商不存在");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BusinessException("供应商不存在");
        }
        supplierMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BusinessException("供应商不存在");
        }
        Supplier update = new Supplier();
        update.setId(id);
        update.setStatus(supplier.getStatus() != null && supplier.getStatus() == 1 ? 0 : 1);
        supplierMapper.updateById(update);
    }

    private String getPrefixByType(String type) {
        if (type == null) {
            throw new BusinessException("供应商类型不能为空");
        }
        return switch (type) {
            case "solution" -> "SOL";
            case "factory" -> "FAC";
            case "product" -> "PRO";
            case "material" -> "MAT";
            default -> throw new BusinessException("无效的供应商类型: " + type);
        };
    }
}
