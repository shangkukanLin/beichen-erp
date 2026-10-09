package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.finance.entity.FinancePayment;
import com.beichen.erp.finance.entity.FinancePaymentAccount;
import com.beichen.erp.finance.entity.FinancePaymentItem;

import java.util.List;
import java.util.Map;

public interface FinancePaymentService {
    Page<Map<String,Object>> page(Long supplierId, String supplierType, String status, int pageNum, int pageSize);
    FinancePayment getById(Long id);
    List<FinancePaymentItem> getItems(Long paymentId);
    /** 分款明细（2026-09-29 多账户付款）：一个单可拆到多个账户，详情页/草稿编辑按行展示 */
    List<FinancePaymentAccount> getAccounts(Long paymentId);
    /**
     * 建单（兼容旧签名）：{@code accounts} 缺省 ⇒ 由单账户字段归一化成一条分款行。
     * <p>与收款侧同口径 —— 老 payload 仍可用，不必改调用方。</p>
     */
    void create(FinancePayment payment, List<FinancePaymentItem> items);
    /** 建单（2026-09-29）：多账户分款 + 核销项可空（开关关闭 = 只记付款，未核销差额审核时落预付台账） */
    void create(FinancePayment payment, List<FinancePaymentAccount> accounts, List<FinancePaymentItem> items);
    /** 草稿就地修改（2026-09-29）：只允许 DRAFT，分款/核销明细整体替换 */
    void update(Long id, FinancePayment form, List<FinancePaymentAccount> accounts, List<FinancePaymentItem> items);
    void cancel(Long id);
    /** 审核：逐账户校验余额后核销应付并写支出流水（**默认口径：余额不足即拒**） */
    void audit(Long id);

    /**
     * 审核（2026-10-09 用户口径「余额不足 ⇒ 提示，用户确认后可通过」；与费用侧 {@code FinanceExpenseService} 逐字对称）。
     *
     * <p>余额不足时：{@code allowOverdraft=false}（默认入口）⇒ 抛 {@code BusinessException(409, …)}，报错里带
     * 「账户 / 当前余额 / 本次付款 / 付款后余额」；**抛错发生在任何写库之前**（状态抢占与核销都在其后）⇒
     * 业务码非 200 就一定没动账，前端据此弹确认框。</p>
     * <p>{@code allowOverdraft=true}（= 用户已确认）⇒ 放行，允许把账户扣成负数，并在该账户的资金流水备注
     * + warn 日志留痕（与委外「强制出库 ⇒ 允许负库存」同口径：不拦、但必须可查）。</p>
     */
    void audit(Long id, boolean allowOverdraft);
    /** 反审核：回退核销、账户余额、供应商应付余额、资金流水冲正 */
    void unAudit(Long id);
    void updateAttach(FinancePayment payment);
}
