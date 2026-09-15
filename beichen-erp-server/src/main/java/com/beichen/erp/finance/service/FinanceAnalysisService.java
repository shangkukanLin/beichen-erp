package com.beichen.erp.finance.service;

import java.time.LocalDate;
import java.util.Map;

/** 财务分析 Service：经营概览 / 利润表 / 资金趋势 / 应收应付账龄。 */
public interface FinanceAnalysisService {
    Map<String, Object> summary();

    /**
     * 利润表（按月行）。
     * @param months 快捷模式的月数（start/end 为 null 时生效）
     * @param start  自定义区间开始日期（含），非空时优先生效
     * @param end    自定义区间结束日期（含）
     */
    Map<String, Object> profit(int months, LocalDate start, LocalDate end);

    Map<String, Object> cashTrend(int months);
    Map<String, Object> aging();

    /**
     * 利润表明细（按天）：快捷区间（today/yesterday/week/month/quarter/year）或自定义日期区间。
     * 返回 start/end、summary（收入/成本/支出/利润合计）与按天的 rows。
     */
    Map<String, Object> profitDetail(String preset, String start, String end);

    /**
     * 利润明细钻取：某一天的每一条单据记录（销售/退货/折损/采购/采购退货/费用，口径与 profitDetail 一致）。
     * @param date 归期日期 yyyy-MM-dd
     */
    Map<String, Object> profitDetailRecords(String date);

    /**
     * 主体往来统计：供应商应付（按主体类型）、供应商应收未收（应付转应收）、报损损失。
     * 数据源：应付/应收台账 + 两张报损单（只读聚合，不改业务数据）。
     */
    Map<String, Object> subject();

    /**
     * 税务分析（已税/未税按月行 + 区间汇总）。
     * @param months 快捷模式的月数（start/end 为 null 时生效）
     * @param start  自定义区间开始日期（含），非空时优先生效
     * @param end    自定义区间结束日期（含）
     */
    Map<String, Object> tax(int months, LocalDate start, LocalDate end);
}
