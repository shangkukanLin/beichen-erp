package com.beichen.erp.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.supplier.common.SupplierTypeEnum;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.entity.SupplierTypeRef;
import com.beichen.erp.supplier.entity.dto.SupplierDTO;
import com.beichen.erp.supplier.entity.dto.SupplierQueryDTO;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.supplier.mapper.SupplierTypeRefMapper;
import com.beichen.erp.supplier.service.SupplierProductService;
import com.beichen.erp.supplier.service.SupplierService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SupplierServiceImpl extends com.baomidou.mybatisplus.extension.service.impl.ServiceImpl<SupplierMapper, Supplier> implements SupplierService {

    @Resource
    private SupplierTypeRefMapper supplierTypeRefMapper;

    @Resource
    private SupplierProductService supplierProductService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public Page<Supplier> page(SupplierQueryDTO query) {
        LambdaQueryWrapper<Supplier> w = Wrappers.lambdaQuery();
        if (StringUtils.hasText(query.getName())) {
            w.like(Supplier::getName, query.getName());
        }
        if (StringUtils.hasText(query.getPhone())) {
            w.like(Supplier::getPhone, query.getPhone());
        }
        if (query.getStatus() != null) {
            w.eq(Supplier::getStatus, query.getStatus());
        }
        // 按类型编码过滤（使用子查询，参数占位符防止 SQL 注入）
        if (StringUtils.hasText(query.getSupplierType())) {
            w.exists("SELECT 1 FROM supplier_type_ref r WHERE r.supplier_id = supplier.id AND r.type_code = {0}",
                    query.getSupplierType());
        }
        w.orderByDesc(Supplier::getCreateTime);
        Page<Supplier> page = new Page<>(query.getPageNum(), query.getPageSize());
        page(page, w);
        // 回填类型编码
        for (Supplier s : page.getRecords()) {
            List<SupplierTypeRef> refs = supplierTypeRefMapper.selectList(
                    Wrappers.<SupplierTypeRef>lambdaQuery().eq(SupplierTypeRef::getSupplierId, s.getId()));
            List<String> codes = new ArrayList<>();
            for (SupplierTypeRef ref : refs) {
                codes.add(ref.getTypeCode());
            }
            s.setTypeCodes(codes);
        }
        return page;
    }

    @Override
    public String generateCode(String type) {
        if (!StringUtils.hasText(type)) {
            throw new IllegalArgumentException("供应商类型不能为空");
        }
        SupplierTypeEnum typeEnum = SupplierTypeEnum.fromCode(type);
        String prefix = typeEnum.getPrefix();
        String ymd = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 同前缀+日期下取最大流水号
        String like = prefix + "-" + ymd + "-%";
        String sql = "SELECT code FROM supplier WHERE code LIKE ? ORDER BY code DESC LIMIT 1";
        List<String> codes = jdbcTemplate.queryForList(sql, String.class, like);
        int seq = 1;
        if (!codes.isEmpty()) {
            String last = codes.get(0);
            String[] parts = last.split("-");
            if (parts.length == 3) {
                try {
                    seq = Integer.parseInt(parts[2]) + 1;
                } catch (NumberFormatException ignored) {
                    seq = 1;
                }
            }
        }
        return prefix + "-" + ymd + "-" + String.format("%03d", seq);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SupplierDTO dto) {
        // 校验同名（同公司内）
        Long cid = CompanyContext.get();
        LambdaQueryWrapper<Supplier> existW = Wrappers.lambdaQuery();
        existW.eq(Supplier::getName, dto.getName());
        if (cid != null) {
            existW.eq(Supplier::getCompanyId, cid);
        }
        Supplier exist = getOne(existW);
        if (exist != null) {
            // 同名供应商：仅追加类型，返回已存在的ID
            saveTypeRefs(exist.getId(), dto.getTypeCodes());
            dto.setId(exist.getId());
            return exist.getId();
        }

        String primaryType = dto.getTypeCodes().get(0);
        Supplier supplier = new Supplier();
        BeanUtils.copyProperties(dto, supplier, "typeCodes", "code");
        if (StringUtils.hasText(dto.getCode())) {
            supplier.setCode(dto.getCode());
        } else {
            supplier.setCode(generateCode(primaryType));
        }
        // code 唯一索引冲突重试，最多 3 次
        int retry = 0;
        while (true) {
            try {
                save(supplier);
                break;
            } catch (DuplicateKeyException e) {
                retry++;
                if (retry >= 3) {
                    throw new RuntimeException("供应商编码生成冲突，请稍后重试");
                }
                supplier.setCode(generateCode(primaryType));
            }
        }
        saveTypeRefs(supplier.getId(), dto.getTypeCodes());
        dto.setId(supplier.getId());
        return supplier.getId();
    }

    private void saveTypeRefs(Long supplierId, List<String> typeCodes) {
        // 去重插入
        LambdaQueryWrapper<SupplierTypeRef> refW = Wrappers.lambdaQuery();
        refW.eq(SupplierTypeRef::getSupplierId, supplierId);
        List<SupplierTypeRef> olds = supplierTypeRefMapper.selectList(refW);
        List<String> oldCodes = new ArrayList<>();
        for (SupplierTypeRef r : olds) {
            oldCodes.add(r.getTypeCode());
        }
        for (String code : typeCodes) {
            if (!oldCodes.contains(code)) {
                SupplierTypeRef ref = new SupplierTypeRef();
                ref.setSupplierId(supplierId);
                ref.setTypeCode(code);
                supplierTypeRefMapper.insert(ref);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SupplierDTO dto) {
        if (dto.getId() == null) {
            throw new IllegalArgumentException("供应商ID不能为空");
        }
        Supplier exist = getById(dto.getId());
        if (exist == null) {
            throw new IllegalArgumentException("供应商不存在");
        }
        // 逐字段赋值，避免覆盖 payableBalance / companyId / createTime 等敏感字段
        if (StringUtils.hasText(dto.getName())) {
            exist.setName(dto.getName());
        }
        exist.setContact(dto.getContact());
        exist.setPhone(dto.getPhone());
        exist.setAddress(dto.getAddress());
        exist.setHasDisplay(dto.getHasDisplay());
        exist.setHasTouch(dto.getHasTouch());
        exist.setRelatedSupplierId(dto.getRelatedSupplierId());
        exist.setCreditPeriodMonths(dto.getCreditPeriodMonths());
        exist.setCreditPeriod(dto.getCreditPeriod());
        exist.setRemark(dto.getRemark());
        updateById(exist);

        // 类型编码：差量更新（新增缺失的，其余保留）
        saveTypeRefs(exist.getId(), dto.getTypeCodes());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Supplier supplier = getById(id);
        if (supplier == null) {
            throw new IllegalArgumentException("供应商不存在");
        }
        // 删除改为停用：仅置 status=0，不再物理删除，避免采购单/应付单变孤儿数据
        supplier.setStatus(0);
        updateById(supplier);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id) {
        Supplier supplier = getById(id);
        if (supplier == null) {
            throw new IllegalArgumentException("供应商不存在");
        }
        supplier.setStatus(supplier.getStatus() == null || supplier.getStatus() == 0 ? 1 : 0);
        updateById(supplier);
    }

    @Override
    public Map<String, Object> checkDelete(Long id) {
        Long cid = CompanyContext.get();
        String cidCond = (cid != null) ? " AND company_id = " + cid : "";
        // 关联表清单（含各表引用供应商的字段名）
        // supplier_product.supplier_id / outsource_order.factory_id / outsource_material_order.supplier_id
        // purchase_order.supplier_id / purchase_return.supplier_id / purchase_inbound.supplier_id
        // finance_payable.supplier_id / outsource_warehouse_stock 通过 outsource_warehouse.factory_id 关联
        Object[][] tables = {
                {"supplier_product", "supplier_id"},
                {"outsource_order", "factory_id"},
                {"outsource_material_order", "supplier_id"},
                {"purchase_order", "supplier_id"},
                {"purchase_return", "supplier_id"},
                {"purchase_inbound", "supplier_id"},
                {"finance_payable", "supplier_id"},
        };
        Map<String, Object> result = new LinkedHashMap<>();
        boolean canDelete = true;
        List<String> details = new ArrayList<>();
        for (Object[] t : tables) {
            String table = (String) t[0];
            String col = (String) t[1];
            String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + col + " = ?" + cidCond;
            try {
                Integer cnt = jdbcTemplate.queryForObject(sql, Integer.class, id);
                if (cnt != null && cnt > 0) {
                    canDelete = false;
                    details.add(table + " 存在 " + cnt + " 条关联数据");
                }
            } catch (Exception e) {
                // 查询异常视为不可删除，并向上抛出，避免"查不到=可删"误判
                log.error("检查供应商关联数据异常，表={}", table, e);
                throw new RuntimeException("检查供应商关联数据失败: " + table, e);
            }
        }
        // 委外仓库库存：有正库存时拦截
        try {
            Integer stockCnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outsource_warehouse_stock s "
                            + "JOIN outsource_warehouse w ON s.warehouse_id = w.id "
                            + "WHERE w.factory_id = ? AND s.quantity > 0" + cidCond, Integer.class, id);
            if (stockCnt != null && stockCnt > 0) {
                canDelete = false;
                details.add("outsource_warehouse_stock 存在 " + stockCnt + " 条正库存记录");
            }
        } catch (Exception e) {
            log.error("检查供应商委外库存异常", e);
            throw new RuntimeException("检查供应商委外库存失败", e);
        }
        result.put("canDelete", canDelete);
        result.put("details", details);
        return result;
    }
}
