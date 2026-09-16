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

    /** 销售单按天（金额 + 订单数；**按建单时间归日**，2026-09-15 全站统一口径） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, IFNULL(SUM(total_amount), 0) AS amt, COUNT(*) AS cnt FROM sale_order WHERE status = 'AUDITED' GROUP BY d")
    List<Map<String, Object>> saleByDay();

    /** 销售退货按天（冲减销售额） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, IFNULL(SUM(total_amount), 0) AS amt, COUNT(*) AS cnt FROM sale_return WHERE status = 'AUDITED' GROUP BY d")
    List<Map<String, Object>> saleReturnByDay();

    /** 已审核销售单明细（含 id，供钻取点击跳销售单详情；带客户名与仓库名）—— 归期 = **建单日** */
    @Select("SELECT o.id, o.code, DATE_FORMAT(o.create_time, '%Y-%m-%d') AS d, o.customer_id, c.name AS customer_name, o.warehouse_id, w.warehouse_name, o.total_amount, o.remark " +
            "FROM sale_order o LEFT JOIN customer c ON c.id = o.customer_id LEFT JOIN warehouse w ON w.id = o.warehouse_id WHERE o.status = 'AUDITED'")
    List<Map<String, Object>> saleOrderRecords();

    /** 销售明细行（产品维度，按区间内订单再聚合） */
    @Select("SELECT order_id, product_id, IFNULL(quantity, 0) AS qty, IFNULL(amount, 0) AS amt FROM sale_order_item")
    List<Map<String, Object>> saleOrderItemAll();

    /**
     * 已审核销售单（含**建单日**与客户名），供首页「当日销售构成」按**建单日**切片。
     * 2026-09-15 全站统一归期：原按**单据日期** order_date，现改为 `create_time`（与首页「当日单据量」4 卡同口径）。
     */
    @Select("SELECT o.id, o.customer_id, DATE_FORMAT(o.create_time, '%Y-%m-%d') AS d, c.name AS customer_name, IFNULL(o.total_amount, 0) AS amt " +
            "FROM sale_order o LEFT JOIN customer c ON c.id = o.customer_id WHERE o.status = 'AUDITED'")
    List<Map<String, Object>> saleOrderByDocDate();

    /** 客户首单日期（用于统计本期新增客户；按**建单日**） */
    @Select("SELECT customer_id, MIN(DATE(create_time)) AS first_date FROM sale_order WHERE status = 'AUDITED' AND customer_id IS NOT NULL GROUP BY customer_id")
    List<Map<String, Object>> customerFirstOrder();

    /** 产品档案（名称/SKU/单位/**移动加权成本价**，产品量不大，一次全量拉取后在 Java 侧匹配） */
    @Select("SELECT id, name, sku, unit, IFNULL(cost_price, 0) AS cost_price FROM product")
    List<Map<String, Object>> productAll();

    /**
     * 已审核销售换货单按天（换货金额）：2026-09-15 新增，供销售分析的「客户换货率」。
     * 归期与其它销售口径一致 = **建单日**（2026-09-15 全站统一）。
     * <p><b>口径修正（2026-09-15，用户确认取 ①）：</b>金额取**明细「换出金额」Σ sale_exchange_item.out_amount**，
     * 而**不是**主表 `sale_exchange.total_amount` —— 该列后端**从不回写**（`SaleExchange` 实体里根本没有该字段，
     * `SaleExchangeServiceImpl` 保存/审核时都不计算总额 → 恒为 0），用它会让换货率永远 0、饼图为空。
     * 本口径与前端「换货单详情」展示的「换出金额」一致。无明细行的换货单（异常数据）不计入。</p>
     * <p><b>件数口径（2026-09-15 用户要求加「金额/件数」switch）：</b>同查询一并返回
     * <b>Σ 换出件数 `i.out_quantity`</b>（与金额口径对称）。</p>
     */
    @Select("SELECT DATE_FORMAT(e.create_time, '%Y-%m-%d') AS d, IFNULL(SUM(i.out_amount), 0) AS amt, IFNULL(SUM(i.out_quantity), 0) AS qty " +
            "FROM sale_exchange e JOIN sale_exchange_item i ON i.exchange_id = e.id " +
            "WHERE e.status = 'AUDITED' GROUP BY d")
    List<Map<String, Object>> saleExchangeByDay();

    /**
     * 销售退货按「天 × 客户」（2026-09-15 新增）：供销售分析「客户退货率」饼图
     * —— 各客户的退货额占总退货额的比例。归期与其它销售口径一致 = 建单日。
     */
    @Select("SELECT r.customer_id, c.name AS customer_name, DATE_FORMAT(r.create_time, '%Y-%m-%d') AS d, IFNULL(SUM(r.total_amount), 0) AS amt " +
            "FROM sale_return r LEFT JOIN customer c ON c.id = r.customer_id WHERE r.status = 'AUDITED' " +
            "GROUP BY r.customer_id, c.name, d")
    List<Map<String, Object>> saleReturnByCustomerDay();

    /**
     * 销售换货按「天 × 客户」（2026-09-15 新增）：供「客户换货率」饼图
     * —— 各客户的换货额占总换货额的比例。归期 = **建单日**（2026-09-15 全站统一）。
     * <p>金额口径同 {@link #saleExchangeByDay()}：取**明细「换出金额」out_amount**（主表 total_amount 后端从不回写、恒为 0）；
     * **件数口径**同查询一并返回 Σ 换出件数 `i.out_quantity`（2026-09-15 加）。</p>
     */
    @Select("SELECT e.customer_id, c.name AS customer_name, DATE_FORMAT(e.create_time, '%Y-%m-%d') AS d, IFNULL(SUM(i.out_amount), 0) AS amt, IFNULL(SUM(i.out_quantity), 0) AS qty " +
            "FROM sale_exchange e JOIN sale_exchange_item i ON i.exchange_id = e.id " +
            "LEFT JOIN customer c ON c.id = e.customer_id WHERE e.status = 'AUDITED' " +
            "GROUP BY e.customer_id, c.name, d")
    List<Map<String, Object>> saleExchangeByCustomerDay();

    /**
     * 销售退货**件数**按天（2026-09-15 新增）：供「客户退货率」**件数口径**的分子（Σ 明细 `quantity`）。
     * 归期与金额口径一致 = 建单日（create_time）。金额口径的分子走 {@link #saleReturnByCustomerDay()} 之外的
     * 按天查询（保持 `sale_return.total_amount` 头部口径不变，故单列一条 join 明细的查询）。
     */
    @Select("SELECT DATE_FORMAT(r.create_time, '%Y-%m-%d') AS d, IFNULL(SUM(ri.quantity), 0) AS qty " +
            "FROM sale_return r JOIN sale_return_item ri ON ri.return_id = r.id " +
            "WHERE r.status = 'AUDITED' GROUP BY d")
    List<Map<String, Object>> saleReturnQtyByDay();

    /**
     * 销售退货**件数**按「天 × 客户」（2026-09-15 新增）：供「客户退货率」**件数口径**饼图分片。
     * 归期 = 建单日。与金额口径查询分开，避免「join 明细后 SUM(头部 total_amount) 被行数放大」。
     */
    @Select("SELECT r.customer_id, c.name AS customer_name, DATE_FORMAT(r.create_time, '%Y-%m-%d') AS d, IFNULL(SUM(ri.quantity), 0) AS qty " +
            "FROM sale_return r JOIN sale_return_item ri ON ri.return_id = r.id " +
            "LEFT JOIN customer c ON c.id = r.customer_id WHERE r.status = 'AUDITED' " +
            "GROUP BY r.customer_id, c.name, d")
    List<Map<String, Object>> saleReturnQtyByCustomerDay();
}
