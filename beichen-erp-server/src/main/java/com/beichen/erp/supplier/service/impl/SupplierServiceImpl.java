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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements SupplierService {

    private final SupplierMapper supplierMapper;
    private final OutsourceWarehouseMapper warehouseMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Page<Supplier> page(SupplierQueryDTO query) {
        Page<Supplier> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<Supplier>()
                .apply(query.getSupplierType() != null && !query.getSupplierType().isBlank(),
                        "FIND_IN_SET({0}, supplier_type) > 0", query.getSupplierType())
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
        // 检查同名供应商是否已存在（不再区分类型，因为供应商可以有多种类型）
        String primaryType = getPrimaryType(dto.getSupplierType());
        LambdaQueryWrapper<Supplier> checkWrapper = new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getName, dto.getName());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) checkWrapper.eq(Supplier::getCompanyId, cid);
        if (supplierMapper.selectCount(checkWrapper) > 0) {
            // 已存在同名供应商：只允许追加新类型，不允许重复创建
            Supplier exist = supplierMapper.selectOne(checkWrapper);
            if (exist != null) {
                String existingTypes = exist.getSupplierType() != null ? exist.getSupplierType() : "";
                String newTypes = dto.getSupplierType();
                // 检查是否有新类型需要合并
                boolean hasNew = false;
                for (String nt : newTypes.split(",")) {
                    if (!containsType(existingTypes, nt.trim())) {
                        hasNew = true;
                        break;
                    }
                }
                if (hasNew) {
                    String merged = mergeTypes(existingTypes, newTypes);
                    Supplier update = new Supplier();
                    update.setId(exist.getId());
                    update.setSupplierType(merged);
                    supplierMapper.updateById(update);
                    dto.setId(exist.getId()); // 返回已存在记录的ID
                    return;
                }
            }
            throw new BusinessException("供应商名称「" + dto.getName() + "」已存在，请勿重复添加");
        }

        Supplier supplier = new Supplier();
        BeanUtils.copyProperties(dto, supplier);
        // 带重试，防止并发时编码冲突
        int seq = 0;
        String prefix = getPrefixByType(primaryType);
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
                // 供应商类型包含"factory"时自动创建默认仓库（已存在则跳过）
                if (containsType(dto.getSupplierType(), "factory")) {
                    Long existCount = warehouseMapper.selectCount(
                        new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, supplier.getId()));
                    if (existCount == null || existCount == 0) {
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
                }
                return;
            } catch (DuplicateKeyException e) {
                if (i >= 2) throw e;
            }
        }
    }

    /** 取逗号分隔列表中的第一个类型作为主类型 */
    private String getPrimaryType(String types) {
        if (types == null || types.isBlank()) return "";
        return types.split(",")[0].trim();
    }

    /** 判断类型列表中是否包含指定类型 */
    private boolean containsType(String types, String type) {
        if (types == null || type == null) return false;
        for (String t : types.split(",")) {
            if (t.trim().equals(type)) return true;
        }
        return false;
    }

    /** 合并两个逗号分隔的类型列表，去重排序 */
    private String mergeTypes(String existing, String newTypes) {
        java.util.Set<String> all = new java.util.LinkedHashSet<>();
        for (String t : existing.split(",")) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) all.add(trimmed);
        }
        for (String t : newTypes.split(",")) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) all.add(trimmed);
        }
        return String.join(",", all);
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
        // 检查名称是否与其他供应商重复（不按类型区分）
        Long cid2 = CompanyContext.get();
        LambdaQueryWrapper<Supplier> checkWrapper = new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getName, dto.getName())
                .ne(Supplier::getId, dto.getId());
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
        Map<String, Object> check = checkDelete(id);
        if (!(Boolean) check.get("canDelete")) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> associations = (Map<String, Integer>) check.get("associations");
            StringBuilder sb = new StringBuilder("该供应商有关联数据，无法删除：");
            associations.forEach((k, v) -> sb.append("\n  - ").append(k).append("：").append(v).append("条"));
            throw new BusinessException(sb.toString());
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

    @Override
    public Map<String, Object> checkDelete(Long id) {
        int productCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM supplier_product WHERE supplier_id = ?", Integer.class, id);
        int warehouseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_warehouse WHERE factory_id = ?", Integer.class, id);
        int orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_order WHERE factory_id = ?", Integer.class, id);

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> associations = new LinkedHashMap<>();
        if (productCount > 0) associations.put("供应产品", productCount);
        if (warehouseCount > 0) associations.put("委外仓库", warehouseCount);
        if (orderCount > 0) associations.put("委外订单", orderCount);
        result.put("canDelete", associations.isEmpty());
        result.put("associations", associations);
        return result;
    }

    private String getPrefixByType(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException("供应商类型不能为空");
        }
        // 支持多类型（逗号分隔），取第一个作为主类型生成编码前缀
        String primaryType = type.contains(",") ? type.split(",")[0].trim() : type;
        return switch (primaryType) {
            case "solution" -> "SOL";
            case "factory" -> "FAC";
            case "product" -> "PRO";
            case "material" -> "MAT";
            default -> throw new BusinessException("无效的供应商类型: " + primaryType);
        };
    }
}
