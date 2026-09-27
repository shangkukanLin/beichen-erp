package com.beichen.erp.finance.common;

/**
 * 费用单来源类型（{@code finance_expense.source_bill_type}，2026-09-27 新增）。
 *
 * <p>用途：标记"这笔费用是由哪个业务对象带出来的"，用于**幂等**与**可追溯**。
 * 目前只有一个来源 —— 物料信息管理「新增物料」时顺带登记的**研发支出**（{@code source_id = outsource_material.id}）。</p>
 *
 * <p>⚠️ 刻意**不复用** {@link SourceBillType}：那个枚举管理的是应收/应付的来源**单据**类型（且前端有 Label 映射、
 * 还被 web-check 的枚举守卫比对）；物料不是单据，混进去会让"来源单据类型"这个语义变脏。</p>
 */
public enum ExpenseSourceType {

    /** 研发支出：来源是**物料**（{@code source_id = outsource_material.id}） */
    RD_MATERIAL("物料研发支出");

    private final String label;

    ExpenseSourceType(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }

    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }
}
