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
    void audit(Long id);
    /** 反审核：回退核销、账户余额、供应商应付余额、资金流水冲正 */
    void unAudit(Long id);
    void updateAttach(FinancePayment payment);
}
