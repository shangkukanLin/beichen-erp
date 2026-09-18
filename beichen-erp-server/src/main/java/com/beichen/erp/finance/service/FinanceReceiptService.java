package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.finance.entity.FinanceReceipt;
import com.beichen.erp.finance.entity.FinanceReceiptItem;

import java.util.List;
import java.util.Map;

public interface FinanceReceiptService {
    Page<Map<String,Object>> page(Long customerId, Long supplierId, String subjectType, String status, int pageNum, int pageSize);
    FinanceReceipt getById(Long id);
    List<FinanceReceiptItem> getItems(Long receiptId);
    void create(FinanceReceipt receipt, List<FinanceReceiptItem> items);
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
