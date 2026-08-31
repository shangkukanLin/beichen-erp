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
     * 税务分析（已税/未税按月行 + 区间汇总）。
     * @param months 快捷模式的月数（start/end 为 null 时生效）
     * @param start  自定义区间开始日期（含），非空时优先生效
     * @param end    自定义区间结束日期（含）
     */
    Map<String, Object> tax(int months, LocalDate start, LocalDate end);
}
