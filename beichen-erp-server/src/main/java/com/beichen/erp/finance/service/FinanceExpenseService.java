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
     * <p>供物料页「新增物料 → 同时登记研发支出」这类跨模块入口复用：同一来源对象不得重复建单，
     * 但**已作废的不算**（作废后可重新登记）。未命中返回 {@code null}。</p>
     *
     * @param sourceBillType 来源类型（见 {@code ExpenseSourceType}）
     * @param sourceId       来源对象 ID（如 {@code outsource_material.id}）
     */
    FinanceExpense findActiveBySource(String sourceBillType, Long sourceId);

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
