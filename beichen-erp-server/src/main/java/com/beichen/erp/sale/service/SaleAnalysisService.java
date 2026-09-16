package com.beichen.erp.sale.service;

import java.util.Map;

/** 销售分析 Service（经营分析 → 销售分析）：纯查询聚合。 */
public interface SaleAnalysisService {

    /**
     * 销售分析总览。
     * @param preset 快捷区间 today/yesterday/week/month/quarter/year
     * @param start  自定义开始日期（非空时优先生效）
     * @param end    自定义结束日期
     */
    Map<String, Object> sale(String preset, String start, String end);

    /**
     * 钻取：区间内的销售单明细，可按产品/仓库/客户过滤。
     */
    Map<String, Object> records(String preset, String start, String end,
                                Long productId, Long warehouseId, Long customerId);

    /**
     * 首页「当日销售构成」：按**建单日（create_time）**统计指定日（默认今天）**已审核**销售单的
     * 产品/客户构成（数量 + 金额）。（2026-09-15 全站统一归期，原按单据日期 order_date）
     *
     * <p>与 {@link #sale} 的差异：那边按**审核日**归期（财务口径），本方法按**单据日期**（业务视角的"当日开单"），
     * 二者数字不必然相同，勿混用。</p>
     *
     * @param date yyyy-MM-dd，空则取今天
     */
    Map<String, Object> byDocDate(String date);
}
