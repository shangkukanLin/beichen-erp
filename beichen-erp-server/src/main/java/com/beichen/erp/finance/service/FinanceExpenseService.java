package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.finance.entity.FinanceExpense;

import java.util.Map;

public interface FinanceExpenseService {

    Page<Map<String, Object>> page(String expenseType, String status, int pageNum, int pageSize);

    FinanceExpense getById(Long id);

    /**
     * 按**来源**查未作废的费用单（2026-09-27 新增，跨模块入口的幂等判据）。
     *
     * <p>供**研发物料页**「新增物料 → 同时登记研发支出」这类跨模块入口复用（登记口径见
     * {@link com.beichen.erp.finance.service.RdExpenseService}）：同一来源对象不得重复建单，
     * 但**已作废的不算**（作废后可重新登记）。未命中返回 {@code null}。</p>
     *
     * <p>⚠️ 来源类型必须与来源对象**一一对应**（如 {@code RD_DEV_MATERIAL} ↔ {@code dev_purchase_item.id}）：
     * 不同表的 id 空间不同，复用同一类型会让同号对象互相判定"已登记"。</p>
     *
     * @param sourceBillType 来源类型（见 {@code ExpenseSourceType}）
     * @param sourceId       来源对象 ID（如 {@code outsource_material.id}）
     */
    FinanceExpense findActiveBySource(String sourceBillType, Long sourceId);

    /** 新增费用登记（草稿） */
    void create(FinanceExpense expense);

    /** 修改草稿费用单（仅草稿可改） */
    void update(FinanceExpense expense);

    /** 审核：校验账户余额后写「费用支出」资金流水并置为已审核（**默认口径：余额不足即拒**） */
    void audit(Long id);

    /**
     * 审核（2026-10-09 用户口径「余额不足 ⇒ 提示，用户确认后可通过」）。
     *
     * <p>余额不足时：{@code allowOverdraft=false}（默认入口）⇒ 抛 {@code BusinessException(409, …)}，
     * 报错里带「当前余额 / 本次扣款 / 扣后余额」；**抛错发生在任何写库之前** ⇒ 业务码非 200 就一定没动账
     * （前端据此弹确认框，脚本/守卫不会把"没执行"误当成功）。</p>
     * <p>{@code allowOverdraft=true}（= 用户已明确确认）⇒ 放行，允许把账户扣成负数，
     * 并在资金流水备注 + warn 日志**留痕**（与委外「强制出库 ⇒ 允许负库存」同口径：不拦、但必须可查）。</p>
     */
    void audit(Long id, boolean allowOverdraft);

    /** 反审核：写「费用冲正」流水冲回并回退草稿 */
    void unAudit(Long id);

    /** 作废（仅草稿） */
    void cancel(Long id);
}
