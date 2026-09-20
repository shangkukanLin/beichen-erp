package com.beichen.erp.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.warehouse.common.WarehouseCategory;
import com.beichen.erp.warehouse.common.WarehouseType;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
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
import java.util.regex.Pattern;

@Slf4j
@Service
public class SupplierServiceImpl extends com.baomidou.mybatisplus.extension.service.impl.ServiceImpl<SupplierMapper, Supplier> implements SupplierService {

    @Resource
    private SupplierTypeRefMapper supplierTypeRefMapper;

    @Resource
    private SupplierProductService supplierProductService;

    @Resource
    private WarehouseMapper WarehouseMapper;

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
        // 排除指定类型（如供应商列表"全部"排除成品商）
        if (StringUtils.hasText(query.getExcludeSupplierType())) {
            w.notExists("SELECT 1 FROM supplier_type_ref r2 WHERE r2.supplier_id = supplier.id AND r2.type_code = {0}",
                    query.getExcludeSupplierType());
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
        // 应付余额实时汇总回填（废弃快照字段后，余额统一由应付台账实时 SUM 计算）
        fillPayableBalance(page.getRecords());
        return page;
    }

    @Override
    public Supplier getById(Long id) {
        Supplier s = super.getById(id);
        if (s == null) return null;
        // 回填类型编码列表（transient 字段，详情接口需显式填充）
        List<SupplierTypeRef> refs = supplierTypeRefMapper.selectList(
                Wrappers.<SupplierTypeRef>lambdaQuery().eq(SupplierTypeRef::getSupplierId, id));
        List<String> codes = new ArrayList<>();
        for (SupplierTypeRef ref : refs) {
            codes.add(ref.getTypeCode());
        }
        s.setTypeCodes(codes);
        return s;
    }

    /** 批量回填应付余额（实时汇总，避免逐供应商 N+1） */
    private void fillPayableBalance(List<Supplier> suppliers) {
        if (suppliers == null || suppliers.isEmpty()) return;
        List<Long> ids = suppliers.stream().map(Supplier::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return;
        // 只算未结清台账：排除 已结清(SETTLED)、已冲回(CANCELLED) 与 预付台账(ADVANCE，多付形成的负数应付)。
        // F7-40（2026-09-19）：三者之外正好是 UNSETTLED/PARTIAL，与应付汇总/账龄/付款下拉口径统一；
        // 原先含 ADVANCE 会让该供应商的"应付余额"被负台账冲减（实测供应商 26：3,498，应为 4,564；27：1,232，应为 2,256）。
        List<String> excludeStatuses = List.of(SettlementStatus.SETTLED.getCode(),
                SettlementStatus.CANCELLED.getCode(), SettlementStatus.ADVANCE.getCode());
        Map<Long, Map<String, Object>> balanceMap = baseMapper.sumPayableBalance(ids, excludeStatuses);
        for (Supplier s : suppliers) {
            Map<String, Object> row = balanceMap.get(s.getId());
            if (row != null && row.get("balance") != null) {
                s.setPayableBalance(new java.math.BigDecimal(row.get("balance").toString()));
            } else {
                s.setPayableBalance(java.math.BigDecimal.ZERO);
            }
        }
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
        // 同名不再自动合并（2026-08-31）：供应商/供货商是两类往来单位，允许重名各自独立建档；
        // 重名确认由前端在提交前完成（查询同名并弹窗让用户确认）
        String primaryType = dto.getTypeCodes().get(0);
        Supplier supplier = new Supplier();
        BeanUtils.copyProperties(dto, supplier, "typeCodes", "code");
        // 2026-09-21（供货SKU）：统一 trim+大写并校验格式/公司内唯一后，覆盖 BeanUtils 带过来的原值
        String supplySku = normalizeSupplySku(dto.getSupplySku());
        assertSupplySkuAvailable(supplySku, null);
        supplier.setSupplySku(supplySku);
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
        // 自动创建该供应商的委外仓库
        createDefaultWarehouse(supplier.getId(), supplier.getName());
        dto.setId(supplier.getId());
        return supplier.getId();
    }

    /** 自动为供应商创建默认委外仓库 */
    private void createDefaultWarehouse(Long supplierId, String supplierName) {
        // 检查是否已存在该供应商的委外仓库（同名供应商追加类型时跳过）
        Long count = WarehouseMapper.selectCount(
                Wrappers.<Warehouse>lambdaQuery().eq(Warehouse::getFactoryId, supplierId));
        if (count != null && count > 0) return;
        Warehouse w = new Warehouse();
        // 自动生成编码：WH-YYYYMMDD-序号（取当日最大序号 + 1，避免删除后编号空洞撞唯一索引）
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seq = nextWarehouseSeq(date);
        w.setCode(BillPrefix.WAREHOUSE + date + String.format("%03d", seq));
        w.setWarehouseCategory(WarehouseCategory.OUTSOURCE.getCode());
        // 委外仓不写仓型（2026-09-16 方案 A：仓型只对自有仓有意义；原写 AUXILIARY 会让委外仓看起来像辅料仓）
        w.setWarehouseType(null);
        w.setFactoryId(supplierId);
        w.setWarehouseName(supplierName != null ? supplierName + "委外仓库" : "委外仓库");
        w.setStatus(1);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) w.setCompanyId(cid);
        WarehouseMapper.insert(w);
        log.info("已为供应商 {}（ID={}）自动创建委外仓库 ID={}", supplierName, supplierId, w.getId());
    }

    /** 计算当日仓库编码最大序号 + 1（避免删除后编号空洞撞唯一索引） */
    private int nextWarehouseSeq(String date) {
        List<Warehouse> list = WarehouseMapper.selectList(
                Wrappers.<Warehouse>lambdaQuery().likeRight(Warehouse::getCode, BillPrefix.WAREHOUSE + date)
                        .orderByDesc(Warehouse::getCode).last("LIMIT 1"));
        if (list == null || list.isEmpty() || list.get(0).getCode() == null) return 1;
        String code = list.get(0).getCode();
        try {
            return Integer.parseInt(code.substring(code.length() - 3)) + 1;
        } catch (NumberFormatException e) {
            return 1;
        }
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
        // 2026-09-21（供货SKU）：⚠️ 本方法是**逐字段赋值**（不像 create 走 BeanUtils）⇒ 新增字段必须显式带上，
        // 否则编辑保存时前端传的值会被静默丢弃。传空 ⇒ null = 取消前缀（此后新产品走默认 SKU-，已生成的 SKU 不变）。
        String supplySku = normalizeSupplySku(dto.getSupplySku());
        assertSupplySkuAvailable(supplySku, exist.getId());
        exist.setSupplySku(supplySku);
        updateById(exist);
        // ⚠️ 同一坑的第二处：updateById 跳过 null 字段 ⇒ 上面那句 setSupplySku(null) 落不了库。
        // 「清空供货SKU」（改为不启用前缀）必须再显式写一次 null，否则旧前缀会一直留着。
        if (supplySku == null) {
            update(Wrappers.<Supplier>lambdaUpdate()
                    .eq(Supplier::getId, exist.getId())
                    .set(Supplier::getSupplySku, null));
        }

        // 类型编码：全量同步（删除旧的重新插入，保证取消勾选生效）
        saveTypeRefsFull(exist.getId(), dto.getTypeCodes());
    }

    // ==================== 供货SKU（2026-09-21 新增字段） ====================

    /** 供货SKU 允许的字符与长度：1-24 位字母/数字/短横线（还要拼上 '-' + 6 位流水进 product.sku VARCHAR(64)，留足余量） */
    private static final Pattern SUPPLY_SKU_PATTERN = Pattern.compile("^[A-Z0-9-]{1,24}$");

    /**
     * 规范化「供货SKU」：空白 ⇒ {@code null}（不启用前缀，该供货商的产品仍走默认 {@code SKU-}）；
     * 否则 trim + 转大写后校验格式，不合法直接拒绝（避免空格/中文等混进 SKU）。
     * <p>⚠️ 统一转大写是**刻意的**：MySQL 唯一索引在 utf8mb4 默认排序规则下**大小写不敏感**，
     * 若原样保存，应用层"精确比对"会放过 {@code abc} 与 {@code ABC}，随后由 DB 抛唯一键冲突
     * ⇒ 用户看到「系统异常」而非可读提示。归一为大写后应用层与 DB 口径一致，
     * 也与既有编码风格（{@code SKU-} / {@code CG-}）统一。</p>
     */
    private String normalizeSupplySku(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String v = raw.trim().toUpperCase();
        if (!SUPPLY_SKU_PATTERN.matcher(v).matches()) {
            throw new BusinessException("供货SKU 只能由 1-24 位字母、数字或短横线组成（如 ABC、GYS-1）：" + raw.trim());
        }
        return v;
    }

    /**
     * 校验「供货SKU」公司内唯一（{@code excludeId} 用于编辑时排除自身）。
     * <p>两个供货商共用同一前缀会让各自产品的 SKU 流水号**交叉占用**（唯一键只能挡住完全相同的 SKU，
     * 挡不住编号交错），故在入口直接拒绝。DB 侧 {@code uk_company_supply_sku} 兜底。</p>
     */
    private void assertSupplySkuAvailable(String supplySku, Long excludeId) {
        if (supplySku == null) return;
        LambdaQueryWrapper<Supplier> w = Wrappers.<Supplier>lambdaQuery().eq(Supplier::getSupplySku, supplySku);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) w.eq(Supplier::getCompanyId, cid);
        if (excludeId != null) w.ne(Supplier::getId, excludeId);
        if (count(w) > 0) {
            throw new BusinessException("供货SKU 已被其他供货商占用：" + supplySku + "，请更换（共用同一前缀会导致产品 SKU 编号交叉）");
        }
    }

    /** 全量同步类型引用（先删后插，编辑保存时取消勾选即生效） */
    private void saveTypeRefsFull(Long supplierId, List<String> typeCodes) {
        supplierTypeRefMapper.delete(Wrappers.<SupplierTypeRef>lambdaQuery().eq(SupplierTypeRef::getSupplierId, supplierId));
        if (typeCodes != null) {
            for (String code : typeCodes) {
                if (code == null || code.isBlank()) continue;
                SupplierTypeRef ref = new SupplierTypeRef();
                ref.setSupplierId(supplierId);
                ref.setTypeCode(code);
                supplierTypeRefMapper.insert(ref);
            }
        }
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
        // purchase_order.supplier_id / purchase_return.supplier_id
        // finance_payable.supplier_id / warehouse_stock 通过 warehouse.factory_id 关联
        Object[][] tables = {
                {"supplier_product", "supplier_id"},
                {"outsource_order", "factory_id"},
                {"outsource_material_order", "supplier_id"},
                {"purchase_order", "supplier_id"},
                {"purchase_return", "supplier_id"},
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
                    "SELECT COUNT(*) FROM warehouse_stock s "
                            + "JOIN warehouse w ON s.warehouse_id = w.id "
                            + "WHERE w.factory_id = ? AND s.quantity > 0"
                            + (cid != null ? " AND s.company_id = " + cid : ""), Integer.class, id);
            if (stockCnt != null && stockCnt > 0) {
                canDelete = false;
                details.add("warehouse_stock 存在 " + stockCnt + " 条正库存记录");
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
