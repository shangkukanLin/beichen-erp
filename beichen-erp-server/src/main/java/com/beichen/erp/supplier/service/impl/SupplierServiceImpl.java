package com.beichen.erp.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.entity.SupplierTypeRef;
import com.beichen.erp.supplier.entity.dto.SupplierDTO;
import com.beichen.erp.supplier.entity.dto.SupplierQueryDTO;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.supplier.mapper.SupplierTypeRefMapper;
import com.beichen.erp.supplier.service.SupplierService;
import com.beichen.erp.outsource.entity.OutsourceWarehouse;
import com.beichen.erp.outsource.entity.OutsourceWarehouseStock;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseMapper;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements SupplierService {

    private final SupplierMapper supplierMapper;
    private final SupplierTypeRefMapper typeRefMapper;
    private final OutsourceWarehouseMapper warehouseMapper;
    private final OutsourceWarehouseStockMapper stockMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Page<Supplier> page(SupplierQueryDTO query) {
        Page<Supplier> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<Supplier>()
                .and(query.getSupplierType() != null && !query.getSupplierType().isBlank(),
                        w -> w.exists("SELECT 1 FROM supplier_type_ref r WHERE r.supplier_id = supplier.id AND r.type_code = '" + query.getSupplierType() + "'"))
                .like(query.getName() != null && !query.getName().isBlank(),
                        Supplier::getName, query.getName())
                .like(query.getPhone() != null && !query.getPhone().isBlank(),
                        Supplier::getPhone, query.getPhone())
                .eq(query.getStatus() != null, Supplier::getStatus, query.getStatus())
                .orderByDesc(Supplier::getId);
        Page<Supplier> result = supplierMapper.selectPage(page, wrapper);
        // 填充 typeCodes
        fillTypeCodes(result.getRecords());
        return result;
    }

    /** 为供应商列表填充 typeCodes */
    private void fillTypeCodes(List<Supplier> suppliers) {
        if (suppliers.isEmpty()) return;
        List<Long> ids = suppliers.stream().map(Supplier::getId).collect(Collectors.toList());
        List<SupplierTypeRef> refs = typeRefMapper.selectList(
            new LambdaQueryWrapper<SupplierTypeRef>().in(SupplierTypeRef::getSupplierId, ids));
        Map<Long, List<String>> map = new HashMap<>();
        for (SupplierTypeRef r : refs) {
            map.computeIfAbsent(r.getSupplierId(), k -> new ArrayList<>()).add(r.getTypeCode());
        }
        for (Supplier s : suppliers) {
            s.setTypeCodes(map.getOrDefault(s.getId(), Collections.emptyList()));
        }
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
            try {
                String code = last.getCode();
                String numPart = code.substring(code.length() - 3);
                seq = Integer.parseInt(numPart) + 1;
            } catch (Exception e) { seq = 1; }
        }
        return prefix + "-" + dateStr + String.format("%03d", seq);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(SupplierDTO dto) {
        List<String> typeCodes = dto.getTypeCodes() != null ? dto.getTypeCodes() : Collections.emptyList();
        String primaryType = typeCodes.isEmpty() ? "" : typeCodes.get(0);

        // 检查同名供应商
        Long cid = CompanyContext.get();
        LambdaQueryWrapper<Supplier> checkWrapper = new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getName, dto.getName());
        if (cid != null && cid > 0) checkWrapper.eq(Supplier::getCompanyId, cid);
        Supplier exist = supplierMapper.selectOne(checkWrapper);
        if (exist != null) {
            // 已存在同名：追加新类型
            List<SupplierTypeRef> existRefs = typeRefMapper.selectList(
                new LambdaQueryWrapper<SupplierTypeRef>().eq(SupplierTypeRef::getSupplierId, exist.getId()));
            Set<String> existCodes = existRefs.stream().map(SupplierTypeRef::getTypeCode).collect(Collectors.toSet());
            boolean added = false;
            for (String tc : typeCodes) {
                if (!existCodes.contains(tc)) {
                    SupplierTypeRef ref = new SupplierTypeRef();
                    ref.setSupplierId(exist.getId());
                    ref.setTypeCode(tc);
                    typeRefMapper.insert(ref);
                    added = true;
                }
            }
            if (!added) {
                throw new BusinessException("供应商名称「" + dto.getName() + "」已存在，请勿重复添加");
            }
            dto.setId(exist.getId());
            return;
        }

        Supplier supplier = new Supplier();
        BeanUtils.copyProperties(dto, supplier, "typeCodes");
        supplier.setCode(generateCode(primaryType));
        if (cid != null && cid > 0) supplier.setCompanyId(cid);
        supplierMapper.insert(supplier);

        // 保存类型关联
        saveTypeRefs(supplier.getId(), typeCodes);

        // 新建供应商自动创建委外仓库（不区分类型）
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SupplierDTO dto) {
        if (dto.getId() == null) throw new BusinessException("供应商ID不能为空");
        Supplier exist = supplierMapper.selectById(dto.getId());
        if (exist == null) throw new BusinessException("供应商不存在");

        Long cid = CompanyContext.get();
        LambdaQueryWrapper<Supplier> checkWrapper = new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getName, dto.getName())
                .ne(Supplier::getId, dto.getId());
        if (cid != null && cid > 0) checkWrapper.eq(Supplier::getCompanyId, cid);
        if (supplierMapper.selectCount(checkWrapper) > 0)
            throw new BusinessException("供应商名称「" + dto.getName() + "」已存在");

        Supplier supplier = new Supplier();
        BeanUtils.copyProperties(dto, supplier, "typeCodes", "code");
        supplier.setCode(exist.getCode());
        supplierMapper.updateById(supplier);

        // 供应商改名时同步仓库名称（仓库名 = 供应商名 + "仓库"）
        if (!Objects.equals(exist.getName(), dto.getName())) {
            OutsourceWarehouse whUpdate = new OutsourceWarehouse();
            whUpdate.setWarehouseName(dto.getName() + "仓库");
            warehouseMapper.update(whUpdate,
                    new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, dto.getId()));
        }

        // 同步类型关联
        List<String> typeCodes = dto.getTypeCodes() != null ? dto.getTypeCodes() : Collections.emptyList();
        typeRefMapper.delete(new LambdaQueryWrapper<SupplierTypeRef>().eq(SupplierTypeRef::getSupplierId, dto.getId()));
        saveTypeRefs(dto.getId(), typeCodes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) throw new BusinessException("供应商不存在");
        Map<String, Object> check = checkDelete(id);
        if (!(Boolean) check.get("canDelete")) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> associations = (Map<String, Integer>) check.get("associations");
            StringBuilder sb = new StringBuilder("该供应商有关联数据，无法删除：");
            associations.forEach((k, v) -> sb.append("\n  - ").append(k).append("：").append(v).append("条"));
            throw new BusinessException(sb.toString());
        }
        // 删除类型关联
        typeRefMapper.delete(new LambdaQueryWrapper<SupplierTypeRef>().eq(SupplierTypeRef::getSupplierId, id));
        // 级联删除该供应商的委外仓库及库存（防止孤儿数据；数据库 fk_warehouse_supplier/fk_stock_warehouse 也兜底级联）
        List<OutsourceWarehouse> whs = warehouseMapper.selectList(
                new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, id));
        for (OutsourceWarehouse wh : whs) {
            stockMapper.delete(new LambdaQueryWrapper<OutsourceWarehouseStock>().eq(OutsourceWarehouseStock::getWarehouseId, wh.getId()));
        }
        warehouseMapper.delete(new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, id));
        supplierMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) throw new BusinessException("供应商不存在");
        Supplier update = new Supplier();
        update.setId(id);
        update.setStatus(supplier.getStatus() != null && supplier.getStatus() == 1 ? 0 : 1);
        supplierMapper.updateById(update);
    }

    @Override
    public Map<String, Object> checkDelete(Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> associations = new LinkedHashMap<>();
        try {
            int pc = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM supplier_product WHERE supplier_id = ?", Integer.class, id);
            if (pc > 0) associations.put("供应产品", pc);
        } catch (Exception ignored) {}
        try {
            int oc = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outsource_order WHERE factory_id = ?", Integer.class, id);
            if (oc > 0) associations.put("委外订单", oc);
        } catch (Exception ignored) {}
        result.put("canDelete", associations.isEmpty());
        result.put("associations", associations);
        return result;
    }

    private void saveTypeRefs(Long supplierId, List<String> typeCodes) {
        Long cid = CompanyContext.get();
        for (String tc : typeCodes) {
            if (tc != null && !tc.isBlank()) {
                SupplierTypeRef ref = new SupplierTypeRef();
                ref.setSupplierId(supplierId);
                ref.setTypeCode(tc.trim());
                if (cid != null && cid > 0) ref.setCompanyId(cid);
                typeRefMapper.insert(ref);
            }
        }
    }

    private String getPrefixByType(String type) {
        if (type == null || type.isBlank()) throw new BusinessException("供应商类型不能为空");
        return switch (type.trim()) {
            case "solution" -> "SOL";
            case "factory" -> "FAC";
            case "product" -> "PRO";
            case "material" -> "MAT";
            default -> throw new BusinessException("无效的供应商类型: " + type);
        };
    }
}
