package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.common.ExpenseSourceType;
import com.beichen.erp.finance.common.ExpenseType;
import com.beichen.erp.finance.entity.FinanceExpense;
import com.beichen.erp.finance.service.FinanceExpenseService;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceMaterialComponent;
import com.beichen.erp.outsource.entity.dto.SupplierMaterialDTO;
import com.beichen.erp.outsource.mapper.OutsourceMaterialComponentMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.service.OutsourceMaterialService;
import com.beichen.erp.outsource.service.SupplierMaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link OutsourceMaterialService} 实现（2026-09-20 · F7-70）。
 *
 * <p>把原来散落在控制器里的四处写入收敛到事务内，并补上删除前的引用校验。
 * 多租户字段（`company_id`）沿用原实现的写入口径（{@link CompanyContext}）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutsourceMaterialServiceImpl implements OutsourceMaterialService {

    private final OutsourceMaterialMapper mapper;
    private final OutsourceMaterialComponentMapper compMapper;
    private final SupplierMaterialService supplierMaterialService;
    private final JdbcTemplate jdbcTemplate;
    /** 研发支出（费用单）落库复用财务侧现成能力：生成 FY 单号 / 置草稿 / 回填账户名 —— 见 createRdExpense */
    private final FinanceExpenseService financeExpenseService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createRdExpense(Long materialId, Map<String, Object> body) {
        if (materialId == null) throw new BusinessException("物料ID不能为空");
        OutsourceMaterial material = mapper.selectById(materialId);
        if (material == null) throw new BusinessException("物料不存在：" + materialId);

        // 2026-09-27（用户口径「新增物料时勾选，需要自动审核」）：`autoAudit=true` ⇒ 建单后**立即审核** ——
        // 当场写「费用支出」流水、扣支出账户余额（不再是草稿）。审核内含账户行锁 + 余额校验：
        // 余额不足会抛错 ⇒ 本方法同事务**整体回滚**，不会留下"料建了、费用只落个草稿"的半成品。
        boolean autoAudit = asBool(body == null ? null : body.get("autoAudit"));
        Map<String, Object> res = new LinkedHashMap<>();
        // 幂等：同一物料已有**未作废**的研发支出 ⇒ 回原单（作废后可重新登记）
        FinanceExpense exists = financeExpenseService.findActiveBySource(
                ExpenseSourceType.RD_MATERIAL.getCode(), materialId);
        if (exists != null) {
            boolean audited = DocStatus.AUDITED.getCode().equals(exists.getStatus());
            // 勾选路径的语义是"登记即审核"：原单若还是草稿（例如先前用列表行操作补登记、尚未去财务审核）
            // ⇒ 补审核，否则用户以为已扣款其实没扣。已审核的不再动账（幂等，绝不重复扣款）。
            if (autoAudit && !audited) {
                financeExpenseService.audit(exists.getId());
                audited = true;
                log.info("物料 {} 的研发支出 {} 原为草稿，本次按勾选口径补审核", materialId, exists.getExpenseNo());
            }
            res.put("expenseId", exists.getId());
            res.put("expenseNo", exists.getExpenseNo());
            res.put("existing", true);
            res.put("audited", audited);
            log.info("物料 {} 已有未作废的研发支出 {}（状态 {}），本次不重复建单", materialId, exists.getExpenseNo(), exists.getStatus());
            return res;
        }

        // 金额 / 账户：与费用单 validate 同口径，但在这里给出可读报错（前端已校验，后端不信任前端）
        Object amtObj = body == null ? null : body.get("amount");
        String amt = amtObj == null ? "" : String.valueOf(amtObj).trim();
        if (amt.isBlank()) throw new BusinessException("研发支出金额不能为空");
        BigDecimal amount;
        try { amount = new BigDecimal(amt); } catch (Exception ex) { throw new BusinessException("研发支出金额格式不正确：" + amt); }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("研发支出金额必须大于 0");

        Object accObj = body.get("accountId");
        String acc = accObj == null ? "" : String.valueOf(accObj).trim();
        if (acc.isBlank()) throw new BusinessException("研发支出必须选择支出账户");
        Long accountId;
        try { accountId = Long.valueOf(acc); } catch (Exception ex) { throw new BusinessException("支出账户不正确：" + acc); }

        String date = body.get("expenseDate") == null ? "" : String.valueOf(body.get("expenseDate")).trim();
        LocalDate expenseDate = LocalDate.now();
        if (!date.isBlank()) {
            try { expenseDate = LocalDate.parse(date); }
            catch (Exception ex) { throw new BusinessException("研发支出日期格式不正确（应为 yyyy-MM-dd）：" + date); }
        }
        String remark = body.get("remark") == null ? "" : String.valueOf(body.get("remark")).trim();
        if (remark.isBlank()) remark = "研发支出：" + material.getMaterialName();

        FinanceExpense e = new FinanceExpense();
        e.setExpenseType(ExpenseType.RND.getCode());
        e.setAmount(amount);
        e.setAccountId(accountId);
        e.setExpenseDate(expenseDate);
        e.setRemark(remark);
        e.setSourceBillType(ExpenseSourceType.RD_MATERIAL.getCode());
        e.setSourceId(materialId);
        // 复用费用单 create：校验金额/账户 → 回填账户名 → 生成 FY 单号 → 置 DRAFT → 落库（不回填则无法拿到单号）
        financeExpenseService.create(e);

        res.put("expenseId", e.getId());
        res.put("expenseNo", e.getExpenseNo());
        res.put("existing", false);
        if (autoAudit) {
            // 复用费用单 audit：原子抢状态 → 账户行锁 → 当前读余额校验 → 写「费用支出」流水 → 置 AUDITED。
            // 失败（如余额不足）抛 BusinessException ⇒ 连同上面刚插入的草稿一起回滚（用户可换账户重试）。
            financeExpenseService.audit(e.getId());
            res.put("audited", true);
        } else {
            // 列表行操作「补登记」路径：仍落草稿，由财务在费用管理页审核（钱此时不动）
            res.put("audited", false);
        }
        return res;
    }

    /** body 里布尔值的容错解析（后端不信任前端：可能传 true / "true" / 1） */
    private boolean asBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Map<String, Object> body) {
        OutsourceMaterial m = new OutsourceMaterial();
        fill(m, body);
        m.setUnit(body.get("unit") != null ? body.get("unit").toString() : "PCS");
        m.setStatus(1);
        mapper.insert(m);
        // 供应商关联统一写入 supplier_material 居间表（弃用 outsource_material.supplier_ids 字段）
        syncSupplierMaterials(m.getId(), body);
        return m.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Map<String, Object> body) {
        if (body.get("id") == null) throw new BusinessException("物料ID不能为空");
        OutsourceMaterial m = new OutsourceMaterial();
        m.setId(Long.valueOf(body.get("id").toString()));
        fill(m, body);
        mapper.updateById(m);
        // 供应商关联统一写入 supplier_material 居间表（差量更新）
        syncSupplierMaterials(m.getId(), body);
    }

    /**
     * 删除物料。F7-70（2026-09-20）：**先做引用校验** —— 原实现直接
     * "删子物料组成 + 删物料本身"，被单据 / BOM / 库存引用的物料一旦删除，
     * 历史单据上的物料名与库存记录都会悬空。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OutsourceMaterial m = mapper.selectById(id);
        if (m == null) throw new BusinessException("物料不存在");
        String name = m.getMaterialName() != null ? m.getMaterialName() : ("#" + id);

        int stockRows = count("SELECT COUNT(*) FROM warehouse_stock WHERE material_id = ?", id);
        if (stockRows > 0)
            throw new BusinessException("物料「" + name + "」在仓库中仍有库存记录（" + stockRows + " 条），不能删除");

        int orderRows = count("SELECT COUNT(*) FROM outsource_material_order_item WHERE outsource_material_id = ? AND deleted = 0", id);
        if (orderRows > 0)
            throw new BusinessException("物料「" + name + "」已被 " + orderRows + " 条物料订单明细引用，不能删除");

        int returnRows = count("SELECT COUNT(*) FROM outsource_material_return_item WHERE outsource_material_id = ?", id);
        if (returnRows > 0)
            throw new BusinessException("物料「" + name + "」已被 " + returnRows + " 条退货单明细引用，不能删除");

        int ioRows = count("SELECT COUNT(*) FROM outsource_delivery_item WHERE outsource_material_id = ?", id);
        if (ioRows > 0)
            throw new BusinessException("物料「" + name + "」已被 " + ioRows + " 条收发单明细引用，不能删除");

        // 作为「子料」被别的物料的组成引用 ⇒ 拦；作为「父料」的组成则随本物料一起清理（保持原行为）
        // 注意：该表的真实列名是 parent_/child_outsource_material_id（不是 parent_/child_material_id）
        int childRows = count("SELECT COUNT(*) FROM outsource_material_component WHERE child_outsource_material_id = ?", id);
        if (childRows > 0)
            throw new BusinessException("物料「" + name + "」被 " + childRows + " 条子物料组成引用（作为子料），不能删除");

        compMapper.delete(new LambdaQueryWrapper<OutsourceMaterialComponent>()
                .eq(OutsourceMaterialComponent::getParentMaterialId, id));
        mapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveComponents(Long materialId, List<Map<String, Object>> items) {
        compMapper.delete(new LambdaQueryWrapper<OutsourceMaterialComponent>()
                .eq(OutsourceMaterialComponent::getParentMaterialId, materialId));
        if (items == null) return;
        for (Map<String, Object> it : items) {
            if (it.get("childMaterialId") == null) continue;
            OutsourceMaterialComponent c = new OutsourceMaterialComponent();
            c.setParentMaterialId(materialId);
            c.setChildMaterialId(Long.valueOf(it.get("childMaterialId").toString()));
            if (it.get("quantity") != null) c.setQuantity(new BigDecimal(it.get("quantity").toString()));
            if (it.get("lossRate") != null) c.setLossRate(new BigDecimal(it.get("lossRate").toString()));
            c.setRemark((String) it.get("remark"));
            compMapper.insert(c);
        }
    }

    // ==================== 私有 ====================

    /** 将前端传入的 supplierIds 逗号串同步到 supplier_material 居间表 */
    private void syncSupplierMaterials(Long materialId, Map<String, Object> body) {
        List<SupplierMaterialDTO> dtos = new ArrayList<>();
        Object idsObj = body.get("supplierIds");
        if (idsObj != null) {
            String ids = String.valueOf(idsObj);
            for (String sid : ids.split(",")) {
                sid = sid.trim();
                if (sid.isEmpty()) continue;
                try {
                    SupplierMaterialDTO dto = new SupplierMaterialDTO();
                    dto.setMaterialId(materialId);
                    dto.setSupplierId(Long.valueOf(sid));
                    dtos.add(dto);
                } catch (NumberFormatException ignore) {
                    // 跳过非数字项
                }
            }
        }
        supplierMaterialService.saveMaterialsByMaterial(materialId, dtos);
    }

    private void fill(OutsourceMaterial m, Map<String, Object> body) {
        // 2026-09-21：「所属项目」字段已下线（project_ids 列已 DROP）⇒ 不再接收 body 里的 projectIds
        m.setMaterialName((String) body.get("materialName"));
        // 仅存储 物料类型ID，类型名称在展示时关联 material_type 查名
        if (body.get("materialTypeId") != null) {
            m.setMaterialTypeId(Long.valueOf(body.get("materialTypeId").toString()));
        }
        // F7-125（2026-09-20）：不再接收 `spec`（产品规格已全站下线；实体字段与 DB 列同步移除）
        // 注意：supplierIds 不再写入 outsource_material 实体，改由 supplier_material 居间表维护
        m.setUnit(body.get("unit") != null ? body.get("unit").toString() : "PCS");
        m.setStatus(body.get("status") != null ? Integer.valueOf(body.get("status").toString()) : 1);
        m.setRemark((String) body.get("remark"));
        m.setPrice(body.get("price") != null ? new BigDecimal(body.get("price").toString()) : null);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) m.setCompanyId(cid);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n != null ? n : 0;
    }
}
