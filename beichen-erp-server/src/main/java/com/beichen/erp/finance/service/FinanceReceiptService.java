package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.finance.entity.FinanceReceipt;
import com.beichen.erp.finance.entity.FinanceReceiptItem;

import java.util.List;
import java.util.Map;

public interface FinanceReceiptService {
    Page<Map<String,Object>> page(Long customerId, String status, int pageNum, int pageSize);
    FinanceReceipt getById(Long id);
    List<FinanceReceiptItem> getItems(Long receiptId);
    void create(FinanceReceipt receipt, List<FinanceReceiptItem> items);
    void cancel(Long id);
    void audit(Long id);
    /** 反审核：回退核销、账户余额、资金流水冲正、客户应收余额 */
    void unAudit(Long id);
}
