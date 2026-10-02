package com.beichen.erp.material.service;

import java.util.Map;

/**
 * 产品分析 Service（经营分析 → 产品分析）：产品维度的<b>净额</b>销售表现 + 下钻明细。
 * 纯查询聚合，不改任何业务数据；口径见 {@code ProductAnalysisMapper} 类注释。
 */
public interface ProductAnalysisService {

    /**
     * 产品分析总览：快捷区间（默认 month）或自定义 start/end。
     *
     * @return {start,end,dates,saleAmounts,returnAmounts,summary,byProduct,byBrand,pies...}
     */
    Map<String, Object> product(String preset, String start, String end);

    /**
     * 下钻明细：某产品在区间内的**销售明细行 + 退货明细行**（含单号，供点进单据详情），
     * 并返回销售额 / 退货额 / 净额合计（与排行行的净销售额逐分对得上）。
     */
    Map<String, Object> records(String preset, String start, String end, Long productId);
}
