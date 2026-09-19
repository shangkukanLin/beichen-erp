package com.beichen.erp.finance.service;

import java.time.LocalDate;
import java.util.Map;

/** 财务分析 Service：经营概览 KPI / 资金趋势 / 应收应付账龄 / 主体往来 / 税务分析。
 * （2026-09-15：「利润表」页面已下线，按月聚合方法 profit() 仍保留，供 summary() 计算本月/本年/趋势。） */
public interface FinanceAnalysisService {
    Map<String, Object> summary();

    /**
     * 利润表（按月行）。
     * @param months 快捷模式的月数（start/end 为 null 时生效）
     * @param start  自定义区间开始日期（含），非空时优先生效
     * @param end    自定义区间结束日期（含）
     */
    Map<String, Object> profit(int months, LocalDate start, LocalDate end);

    /**
     * 资金趋势与账户余额分布（2026-09-15 起支持「统计区间」）。
     * 区间优先级：start/end 自定义 &gt; preset 快捷预设；两者都没有时按 months 取"近 N 月"（兼容旧调用）。
     * 区间模式的粒度：≤62 天按天、否则按月（与首页趋势图同一规则）。
     */
    Map<String, Object> cashTrend(String preset, int months, LocalDate start, LocalDate end);
    Map<String, Object> aging();

    /**
     * 首页经营总览 KPI：所选区间的 4 个指标 + 固定「本年」的同 4 个指标。
     * 指标口径（与利润表口径一致）：销售金额 = 销售单 − 销售退货 + 折损收款；
     * 采购支出 = 已审核采购单 − 采购退货（采购入库属资产、不计入损益）；费用支出 = 费用单；
     * 净利润 = 销售金额 − 销售成本 − 费用支出。
     * @param preset 快捷区间（today/yesterday/week/month/quarter/year），start/end 非空时优先生效
     * @param start  自定义区间开始日期（含）
     * @param end    自定义区间结束日期（含）
     */
    Map<String, Object> overviewKpi(String preset, String start, String end);

    /**
     * 进货分析（2026-09-15 新增，供「经营分析 → 进货分析」）：
     * 所选区间的采购 KPI（采购金额 / 采购退货 / 净采购额 / 采购单数）+ 按天或按月的趋势序列 + 单据明细（下钻）。
     * 口径（**F7-42 · 2026-09-19 纠偏**）：采购金额 = 已审核采购单金额、采购退货 = 已审核采购退货金额，
     * **归期一律按建单日（`create_time`）** —— 原注释写"采购按审核日、退货按建单日"与实现不符
     * （`FinanceAnalysisMapper` 的所有 `xxxByMonth/ByDay` 都是 `DATE_FORMAT(create_time, ...)`）；
     * 且**审核日口径不可复现**（反审核→重审会写入新的 `audit_time`，历史月度数字会随之后的操作持续漂移）。
     * 采购入库属资产、不计入损益，故本页只做采购视角统计，不参与利润/成本。
     * @param preset 快捷区间（today/yesterday/week/month/quarter/year），start/end 非空时优先生效
     * @param start  自定义区间开始日期（含）
     * @param end    自定义区间结束日期（含）
     */
    Map<String, Object> purchaseAnalysis(String preset, String start, String end);

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
    Map<String, Object> tax(String preset, int months, LocalDate start, LocalDate end);
}
