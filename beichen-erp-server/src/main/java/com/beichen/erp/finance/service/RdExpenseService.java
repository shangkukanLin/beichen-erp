package com.beichen.erp.finance.service;

import com.beichen.erp.finance.common.ExpenseSourceType;

import java.util.Map;

/**
 * 「研发支出」登记（2026-09-27 立；**2026-09-28 收敛为共享服务**）。
 *
 * <p>业务模块（研发物料 / …）只管"点一下登记"，登记口径全部收敛在这里：</p>
 * <ul>
 *   <li><b>幂等</b>：同一来源（{@code 来源类型 + sourceId}）已有**未作废**的研发支出 ⇒ 回原单，不重复建、不重复扣款；</li>
 *   <li><b>校验</b>：金额 &gt; 0、支出账户必填、日期可空=今天（前端已校验，后端不信任前端）；</li>
 *   <li><b>审核</b>：{@code autoAudit=true} ⇒ 建单后立即审核（写「费用支出」流水 + 扣账户余额，余额不足整体回滚）；
 *       但**须持费用审核权限**（{@code finance:expense} / {@code finance:cashflow}，与 {@code /api/finance/expense}
 *       的收口号同源）⇒ 无权限时**降级为草稿**（响应 {@code downgraded=true}），不越权动钱。</li>
 * </ul>
 *
 * <p>⚠️ 调用方必须为**自己的来源对象**传对应的 {@link ExpenseSourceType}（见该枚举的说明：复用会串号），
 * 并保证 {@code sourceId} 指向的对象已存在。</p>
 */
public interface RdExpenseService {

    /**
     * 登记一笔研发支出费用单。
     *
     * @param sourceType    来源类型（决定幂等键）
     * @param sourceId      来源对象 ID（调用方须先确认对象存在）
     * @param defaultRemark 备注留空时的默认文案（一般 = "研发支出：{对象名}"）
     * @param body          {@code amount} 必填 &gt;0；{@code accountId} 必填；{@code expenseDate}（空=今天）；
     *                      {@code remark}（空=defaultRemark）；{@code autoAudit}（true=建单即审核并扣款）
     * @return {@code {expenseId, expenseNo, existing, audited, downgraded?}}
     */
    Map<String, Object> register(ExpenseSourceType sourceType, Long sourceId, String defaultRemark, Map<String, Object> body);
}
