package com.beichen.erp.customer.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 客户分析聚合 Mapper：客户销售额排行 / 退货 / 应收欠款 / 钻取明细。
 * 登录态下多租户插件自动注入 company_id 过滤；全部为只读聚合查询。
 * 同样只在 SQL 里分组、Java 侧按区间过滤（日期参数绑定进 SQL 实测取不到数据）。
 */
@Mapper
public interface CustomerAnalysisMapper {

    /** 客户主数据（含账期与信用额度） */
    @Select("SELECT id, code, name, credit_period, credit_period_months, credit_limit, status FROM customer")
    List<Map<String, Object>> customerAll();

    /** 销售单按天 × 客户（金额与订单数；**按建单时间归日**，2026-09-15 全站统一口径） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, customer_id, IFNULL(SUM(total_amount), 0) AS amt, COUNT(*) AS cnt, MAX(DATE(create_time)) AS last_date " +
            "FROM sale_order WHERE status = 'AUDITED' AND customer_id IS NOT NULL GROUP BY d, customer_id")
    List<Map<String, Object>> saleByDayCustomer();

    /** 销售退货按天 × 客户 */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, customer_id, IFNULL(SUM(total_amount), 0) AS amt FROM sale_return WHERE status = 'AUDITED' AND customer_id IS NOT NULL GROUP BY d, customer_id")
    List<Map<String, Object>> saleReturnByDayCustomer();

    /** 客户应收欠款（未结清部分；与应收敛口径一致） */
    @Select("SELECT customer_id, IFNULL(SUM(unpaid_amount), 0) AS unpaid FROM finance_receivable WHERE status IN ('UNSETTLED', 'PARTIAL') AND customer_id IS NOT NULL GROUP BY customer_id")
    List<Map<String, Object>> receivableByCustomer();

    /** 客户首单日期（新增客户判定；按**建单日**） */
    @Select("SELECT customer_id, MIN(DATE(create_time)) AS first_date FROM sale_order WHERE status = 'AUDITED' AND customer_id IS NOT NULL GROUP BY customer_id")
    List<Map<String, Object>> customerFirstOrder();

    /** 产品档案（名称/SKU/品牌/型号/单位/移动加权成本价）；2026-09-15 规格字段已下线 */
    @Select("SELECT id, name, sku, brand_id, general_model, unit, cost_price FROM product")
    List<Map<String, Object>> productAll();

    /** 品牌档案（用于按品牌汇总销售额分布） */
    @Select("SELECT id, brand_name FROM brand")
    List<Map<String, Object>> brandAll();

    /** 销售明细行（按订单归集销售成本；quality_type 用于品质结构分析） */
    @Select("SELECT order_id, product_id, quality_type, IFNULL(quantity, 0) AS qty, IFNULL(amount, 0) AS amt FROM sale_order_item")
    List<Map<String, Object>> saleOrderItemAll();

    /** 销售退货明细行（退回冲回成本） */
    @Select("SELECT return_id, product_id, IFNULL(quantity, 0) AS qty FROM sale_return_item")
    List<Map<String, Object>> saleReturnItemAll();

    /** 已审核销售退货单（用于把退货明细归到客户并做区间过滤） */
    @Select("SELECT id, code, customer_id, total_amount, loss_amount, charge_type, charge_amount, remark, DATE_FORMAT(create_time, '%Y-%m-%d') AS d FROM sale_return WHERE status = 'AUDITED' AND customer_id IS NOT NULL")
    List<Map<String, Object>> saleReturnAll();

    /** 退货明细（含品质与金额，用于单客户分析的退货明细表） */
    @Select("SELECT i.return_id, i.product_id, i.quality_type, IFNULL(i.quantity, 0) AS qty, IFNULL(i.amount, 0) AS amt " +
            "FROM sale_return_item i JOIN sale_return r ON r.id = i.return_id WHERE r.status = 'AUDITED'")
    List<Map<String, Object>> saleReturnItemDetail();

    /** 已审核销售单明细（钻取用，含 id 供跳销售单详情；归期 = **建单日**） */
    @Select("SELECT o.id, o.code, DATE_FORMAT(o.create_time, '%Y-%m-%d') AS d, o.customer_id, c.name AS customer_name, w.warehouse_name, o.total_amount, o.remark " +
            "FROM sale_order o LEFT JOIN customer c ON c.id = o.customer_id LEFT JOIN warehouse w ON w.id = o.warehouse_id WHERE o.status = 'AUDITED' AND o.customer_id IS NOT NULL")
    List<Map<String, Object>> saleOrderRecords();
}
