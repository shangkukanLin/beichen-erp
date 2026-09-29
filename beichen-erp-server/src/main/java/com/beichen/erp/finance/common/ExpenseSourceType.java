package com.beichen.erp.finance.common;

/**
 * 费用单来源类型（{@code finance_expense.source_bill_type}，2026-09-27 新增）。
 *
 * <p>用途：标记"这笔费用是由哪个业务对象带出来的"，用于**幂等**与**可追溯**。</p>
 *
 * <p>⚠️ **每种来源对象必须有自己的枚举值**：幂等键 = 来源类型 + sourceId，而不同来源对象的 id 空间不同
 * （如 {@code outsource_material.id} 与 {@code dev_purchase_item.id}）—— 复用同一类型会让两张表里
 * 同号对象互相判定"已登记过"（串号）。</p>
 *
 * <p>⚠️ 刻意**不复用** {@link SourceBillType}：那个枚举管理的是应收/应付的来源**单据**类型（且前端有 Label 映射、
 * 还被 web-check 的枚举守卫比对）；物料不是单据，混进去会让"来源单据类型"这个语义变脏。</p>
 */
public enum ExpenseSourceType {

    /**
     * 研发支出：来源是**物料信息管理的物料**（{@code source_id = outsource_material.id}）。
     * <p>2026-09-28（用户口径）：「登记研发支出」属**研发管理的研发物料**，物料信息管理不再提供入口 ⇒
     * 本值只剩**历史数据**语义（存量费用单的来源显示/追溯），不再有新写入。</p>
     */
    RD_MATERIAL("物料研发支出"),

    /** 研发支出：来源是**研发物料**（{@code source_id = dev_purchase_item.id}）—— 2026-09-28 起的新写入口径 */
    RD_DEV_MATERIAL("研发物料研发支出");

    private final String label;

    ExpenseSourceType(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }

    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }
}
