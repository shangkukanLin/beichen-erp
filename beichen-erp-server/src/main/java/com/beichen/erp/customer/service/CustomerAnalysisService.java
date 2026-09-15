package com.beichen.erp.customer.service;

import java.util.Map;

/** 客户分析 Service（经营分析 → 客户分析）：纯查询聚合。 */
public interface CustomerAnalysisService {

    /** 客户分析总览：KPI + 排行 + 客户明细表 */
    Map<String, Object> customer(String preset, String start, String end);

    /** 钻取：某客户在区间的销售单明细 */
    Map<String, Object> records(Long customerId, String preset, String start, String end);

    /**
     * 单客户分析：客户档案 + 经营 KPI + 月度趋势 + 按产品/品牌/品质分布 + 退货明细。
     * @param customerId 客户主键
     */
    Map<String, Object> profile(Long customerId, String preset, String start, String end);
}
