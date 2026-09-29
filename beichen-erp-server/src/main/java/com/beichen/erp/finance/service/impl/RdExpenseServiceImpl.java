package com.beichen.erp.finance.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.common.ExpenseSourceType;
import com.beichen.erp.finance.common.ExpenseType;
import com.beichen.erp.finance.entity.FinanceExpense;
import com.beichen.erp.finance.service.FinanceExpenseService;
import com.beichen.erp.finance.service.RdExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link RdExpenseService} 实现。
 *
 * <p>2026-09-28 从 {@code OutsourceMaterialServiceImpl.createRdExpense} **原样搬来**并参数化
 * （来源类型 / 来源ID / 默认备注 由调用方给）—— 用户口径把该功能从「物料信息管理」移到「研发管理的研发物料」，
 * 与其把这段口径复制到新模块，不如收敛成一处共享实现（校验 / 幂等 / 权限闸门 / 自动审核与回滚全在一起）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RdExpenseServiceImpl implements RdExpenseService {

    /** 研发支出（费用单）落库复用财务侧现成能力：生成 FY 单号 / 置草稿 / 回填账户名 —— 见 {@code create} */
    private final FinanceExpenseService financeExpenseService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(ExpenseSourceType sourceType, Long sourceId, String defaultRemark, Map<String, Object> body) {
        if (sourceType == null) throw new BusinessException("费用来源类型不能为空");
        if (sourceId == null) throw new BusinessException("费用来源对象ID不能为空");
        // body 允许为空（controller 的 @RequestBody(required=false)）⇒ 统一成空表，后续取值不做 null 判断
        Map<String, Object> b = body == null ? Map.of() : body;

        // 2026-09-27（用户口径「新增物料时勾选，需要自动审核」）：`autoAudit=true` ⇒ 建单后**立即审核** ——
        // 当场写「费用支出」流水、扣支出账户余额（不再是草稿）。审核内含账户行锁 + 余额校验：
        // 余额不足会抛错 ⇒ 本方法同事务**整体回滚**，不会留下"对象建了、费用只落个草稿"的半成品。
        //
        // 同日追加（用户选方案 A）：自动审核 = **动钱** ⇒ 还须持**费用审核权限**（与 /api/finance/expense
        // 的收口号同源：finance:expense 或 finance:cashflow，见 ApiPermGuard）。业务页用户通常没有这两个码
        // ⇒ **降级为草稿**（不越权动钱），由财务在费用管理审核；响应带 downgraded 让前端说清原因。
        boolean wantAudit = asBool(b.get("autoAudit"));
        boolean canAudit = canAuditExpense();
        boolean autoAudit = wantAudit && canAudit;
        Map<String, Object> res = new LinkedHashMap<>();
        if (wantAudit && !canAudit) {
            res.put("downgraded", true);
            log.info("来源 {}#{} 的研发支出要求自动审核，但当前用户无费用审核权限（finance:expense / finance:cashflow）⇒ 降级为草稿",
                    sourceType.getCode(), sourceId);
        }
        // 幂等：同一来源已有**未作废**的研发支出 ⇒ 回原单（作废后可重新登记）
        FinanceExpense exists = financeExpenseService.findActiveBySource(sourceType.getCode(), sourceId);
        if (exists != null) {
            boolean audited = DocStatus.AUDITED.getCode().equals(exists.getStatus());
            // 勾选路径的语义是"登记即审核"：原单若还是草稿（例如先前用列表行操作补登记、尚未去财务审核）
            // ⇒ 补审核，否则用户以为已扣款其实没扣。已审核的不再动账（幂等，绝不重复扣款）。
            if (autoAudit && !audited) {
                financeExpenseService.audit(exists.getId());
                audited = true;
                log.info("来源 {}#{} 的研发支出 {} 原为草稿，本次按勾选口径补审核", sourceType.getCode(), sourceId, exists.getExpenseNo());
            }
            res.put("expenseId", exists.getId());
            res.put("expenseNo", exists.getExpenseNo());
            res.put("existing", true);
            res.put("audited", audited);
            log.info("来源 {}#{} 已有未作废的研发支出 {}（状态 {}），本次不重复建单",
                    sourceType.getCode(), sourceId, exists.getExpenseNo(), exists.getStatus());
            return res;
        }

        // 金额 / 账户：与费用单 validate 同口径，但在这里给出可读报错（前端已校验，后端不信任前端）
        Object amtObj = b.get("amount");
        String amt = amtObj == null ? "" : String.valueOf(amtObj).trim();
        if (amt.isBlank()) throw new BusinessException("研发支出金额不能为空");
        BigDecimal amount;
        try { amount = new BigDecimal(amt); } catch (Exception ex) { throw new BusinessException("研发支出金额格式不正确：" + amt); }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("研发支出金额必须大于 0");

        Object accObj = b.get("accountId");
        String acc = accObj == null ? "" : String.valueOf(accObj).trim();
        if (acc.isBlank()) throw new BusinessException("研发支出必须选择支出账户");
        Long accountId;
        try { accountId = Long.valueOf(acc); } catch (Exception ex) { throw new BusinessException("支出账户不正确：" + acc); }

        String date = b.get("expenseDate") == null ? "" : String.valueOf(b.get("expenseDate")).trim();
        LocalDate expenseDate = LocalDate.now();
        if (!date.isBlank()) {
            try { expenseDate = LocalDate.parse(date); }
            catch (Exception ex) { throw new BusinessException("研发支出日期格式不正确（应为 yyyy-MM-dd）：" + date); }
        }
        String remark = b.get("remark") == null ? "" : String.valueOf(b.get("remark")).trim();
        if (remark.isBlank()) remark = (defaultRemark == null || defaultRemark.isBlank()) ? "研发支出" : defaultRemark;

        FinanceExpense e = new FinanceExpense();
        e.setExpenseType(ExpenseType.RND.getCode());
        e.setAmount(amount);
        e.setAccountId(accountId);
        e.setExpenseDate(expenseDate);
        e.setRemark(remark);
        e.setSourceBillType(sourceType.getCode());
        e.setSourceId(sourceId);
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

    /**
     * 当前用户是否持有**费用审核权限** —— 与 {@code /api/finance/expense} 的收口号同源
     * （{@code finance:expense} 或 {@code finance:cashflow}，任一即可；见 ApiPermGuard 的
     * {@code rule("/api/finance/expense", ...)}）⇒ **"能在费用管理页点审核的人"才允许自动审核**。
     *
     * <p>无登录上下文（内部调用/异步任务）按无权限处理：宁降级草稿，不越权动钱。</p>
     */
    private boolean canAuditExpense() {
        try {
            return StpUtil.hasPermission("finance:expense") || StpUtil.hasPermission("finance:cashflow");
        } catch (Exception e) {
            return false;
        }
    }

    /** body 里布尔值的容错解析（后端不信任前端：可能传 true / "true" / 1） */
    private boolean asBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean bb) return bb;
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }
}
