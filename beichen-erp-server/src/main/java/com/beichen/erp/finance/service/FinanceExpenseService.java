package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.finance.entity.FinanceExpense;

import java.util.Map;

public interface FinanceExpenseService {

    Page<Map<String, Object>> page(String expenseType, String status, int pageNum, int pageSize);

    FinanceExpense getById(Long id);

    /** 新增费用登记（草稿） */
    void create(FinanceExpense expense);

    /** 修改草稿费用单（仅草稿可改） */
    void update(FinanceExpense expense);

    /** 审核：校验账户余额后写「费用支出」资金流水并置为已审核 */
    void audit(Long id);

    /** 反审核：写「费用冲正」流水冲回并回退草稿 */
    void unAudit(Long id);

    /** 作废（仅草稿） */
    void cancel(Long id);
}
