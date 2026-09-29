package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.finance.entity.FinanceReceipt;
import com.beichen.erp.finance.entity.FinanceReceiptAccount;
import com.beichen.erp.finance.entity.FinanceReceiptItem;

import java.util.List;
import java.util.Map;

public interface FinanceReceiptService {
    Page<Map<String,Object>> page(Long customerId, Long supplierId, String subjectType, String status, int pageNum, int pageSize);
    FinanceReceipt getById(Long id);
    List<FinanceReceiptItem> getItems(Long receiptId);
    /** 分款明细（2026-09-29 多账户收款）：一个单可拆到多个账户，详情页按行展示 */
    List<FinanceReceiptAccount> getAccounts(Long receiptId);
    /**
     * 建单（兼容旧签名，系统自动单用）：{@code accounts} 缺省 ⇒ 由单账户字段归一化成一条分款行。
     * <p>调用方：销售单现金结算 {@code SaleOrderServiceImpl.createCashReceipt}（单账户、立即审核）。</p>
     */
    void create(FinanceReceipt receipt, List<FinanceReceiptItem> items);
    /** 建单（2026-09-29）：多账户分款 + 核销项可空（开关关闭 = 只记收款，未核销差额审核时落预收台账） */
    void create(FinanceReceipt receipt, List<FinanceReceiptAccount> accounts, List<FinanceReceiptItem> items);
    /** 草稿就地修改（2026-09-29 用户口径「加草稿可编辑」）：只允许 DRAFT，分款/核销明细整体替换 */
    void update(Long id, FinanceReceipt form, List<FinanceReceiptAccount> accounts, List<FinanceReceiptItem> items);
    void cancel(Long id);
    /**
     * 按来源单据查收款单（2026-09-18）：销售单**现金结算**审核时自动生成的草稿收款单靠它定位 ——
     * 销售单反审核联动（草稿→自动作废；已审核→拦住提示）与销售单详情页展示都用它。
     */
    List<FinanceReceipt> findBySource(String sourceBillType, Long sourceId);
    void audit(Long id);
    /** 反审核：回退核销、账户余额、资金流水冲正、客户应收余额 */
    void unAudit(Long id);
}
