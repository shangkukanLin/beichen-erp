package com.beichen.erp.sale.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 销售分析聚合 Mapper：销售额趋势 / 产品排行 / 仓库分布 / 钻取明细。
 * 登录态下多租户插件自动注入 company_id 过滤；全部为只读聚合查询。
 * 与财务分析保持一致的做法：SQL 只做全量分组，区间过滤放 Java 侧（日期参数绑定进 SQL 实测取不到数据）。
 */
@Mapper
public interface SaleAnalysisMapper {

    /** 销售单按天（金额 + 订单数；按审核时间归日，NULL 时 create_time 兜底） */
    @Select("SELECT DATE_FORMAT(COALESCE(audit_time, create_time), '%Y-%m-%d') AS d, IFNULL(SUM(total_amount), 0) AS amt, COUNT(*) AS cnt FROM sale_order WHERE status = 'AUDITED' GROUP BY d")
    List<Map<String, Object>> saleByDay();

    /** 销售退货按天（冲减销售额） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, IFNULL(SUM(total_amount), 0) AS amt, COUNT(*) AS cnt FROM sale_return WHERE status = 'AUDITED' GROUP BY d")
    List<Map<String, Object>> saleReturnByDay();

    /** 已审核销售单明细（含 id，供钻取点击跳销售单详情；带客户名与仓库名） */
    @Select("SELECT o.id, o.code, DATE_FORMAT(COALESCE(o.audit_time, o.create_time), '%Y-%m-%d') AS d, o.customer_id, c.name AS customer_name, o.warehouse_id, w.warehouse_name, o.total_amount, o.remark " +
            "FROM sale_order o LEFT JOIN customer c ON c.id = o.customer_id LEFT JOIN warehouse w ON w.id = o.warehouse_id WHERE o.status = 'AUDITED'")
    List<Map<String, Object>> saleOrderRecords();

    /** 销售明细行（产品维度，按区间内订单再聚合） */
    @Select("SELECT order_id, product_id, IFNULL(quantity, 0) AS qty, IFNULL(amount, 0) AS amt FROM sale_order_item")
    List<Map<String, Object>> saleOrderItemAll();

    /**
     * 已审核销售单（含**单据日期** order_date 与客户名），供首页「当日销售构成」按**单据日期**切片。
     * 注意与上面的 `saleOrderRecords()` 区别：那个按**审核日**归期（财务口径），本查询给的是**单据日期**（业务"当日开单"口径）。
     */
    @Select("SELECT o.id, o.customer_id, DATE_FORMAT(o.order_date, '%Y-%m-%d') AS d, c.name AS customer_name, IFNULL(o.total_amount, 0) AS amt " +
            "FROM sale_order o LEFT JOIN customer c ON c.id = o.customer_id WHERE o.status = 'AUDITED'")
    List<Map<String, Object>> saleOrderByDocDate();

    /** 客户首单日期（用于统计本期新增客户） */
    @Select("SELECT customer_id, MIN(DATE(COALESCE(audit_time, create_time))) AS first_date FROM sale_order WHERE status = 'AUDITED' AND customer_id IS NOT NULL GROUP BY customer_id")
    List<Map<String, Object>> customerFirstOrder();

    /** 产品档案（名称/SKU/单位，产品量不大，一次全量拉取后在 Java 侧匹配） */
    @Select("SELECT id, name, sku, unit FROM product")
    List<Map<String, Object>> productAll();
}
