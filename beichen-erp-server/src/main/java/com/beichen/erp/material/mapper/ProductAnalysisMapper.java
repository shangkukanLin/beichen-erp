package com.beichen.erp.material.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 产品分析聚合 Mapper（经营分析 → 产品分析，2026-10-02 用户要求新增）。
 *
 * <p><b>与销售分析的差别（本页存在的理由）：</b>销售分析的 byProduct 只看"卖得好不好"，且
 * **不冲减退货**；产品分析按用户口径（2026-10-02）一律**冲减净额**：净销量 = 销售明细数量 − 退货明细数量、
 * 净销售额 = 销售明细金额 − 退货明细金额、毛利 = 净销售额 − 净成本
 * （净成本 = Σ销售数量×成本 − Σ退货数量×成本）。口径与「客户分析」的产品行
 * （{@code CustomerAnalysisServiceImpl.profile()} 的 byProduct/cost/profit）一致。</p>
 *
 * <p><b>口径约定（沿用本仓既定做法，勿擅自改）：</b></p>
 * <ol>
 *   <li>只算 <b>AUDITED</b>；归期一律 <b>建单日 create_time</b>（2026-09-15 全站统一）；</li>
 *   <li><b>两端都取明细口径</b>（Σ item.qty / Σ item.amt）—— 这样"产品行合计"与"KPI 合计"必然自洽，
 *       不会出现"join 明细后 SUM(头部 total_amount) 被行数放大"（见 {@code SaleAnalysisMapper} 的同类注释）；</li>
 *   <li>SQL 只做全量 GROUP BY，<b>区间过滤放 Java 侧</b>（日期参数绑进 SQL 实测取不到数据，同销售/财务分析）；</li>
 *   <li>登录态下多租户插件对**每张表**注入 {@code company_id} ⇒ 本文件的 JOIN 各表都有该列
 *       （{@code customer} / {@code warehouse} / {@code sale_order_item} / {@code sale_return_item} 均已确认）。</li>
 * </ol>
 */
@Mapper
public interface ProductAnalysisMapper {

    /** 产品档案（名称 / SKU / 单位 / 品牌 / 移动加权成本价）；产品量不大 ⇒ 一次全量拉取后在 Java 侧匹配 */
    @Select("SELECT id, name, sku, unit, brand_id, IFNULL(cost_price, 0) AS cost_price FROM product")
    List<Map<String, Object>> productAll();

    /** 品牌档案（按品牌汇总用） */
    @Select("SELECT id, brand_name FROM brand")
    List<Map<String, Object>> brandAll();

    /** 已审核销售单（含**建单日**，供明细归期与区间过滤） */
    @Select("SELECT id, DATE_FORMAT(create_time, '%Y-%m-%d') AS d FROM sale_order WHERE status = 'AUDITED'")
    List<Map<String, Object>> saleOrderAll();

    /** 销售明细行（产品维度；无状态列，归期取决于所属订单的建单日） */
    @Select("SELECT order_id, product_id, IFNULL(quantity, 0) AS qty, IFNULL(amount, 0) AS amt FROM sale_order_item")
    List<Map<String, Object>> saleOrderItemAll();

    /** 已审核销售退货单（含**建单日**；冲减销售额 / 销量 / 毛利） */
    @Select("SELECT id, DATE_FORMAT(create_time, '%Y-%m-%d') AS d FROM sale_return WHERE status = 'AUDITED'")
    List<Map<String, Object>> saleReturnAll();

    /** 销售退货明细行（产品维度；冲减口径的分子） */
    @Select("SELECT return_id, product_id, IFNULL(quantity, 0) AS qty, IFNULL(amount, 0) AS amt FROM sale_return_item")
    List<Map<String, Object>> saleReturnItemAll();

    /**
     * 已审核销售换货单明细（产品维度），供「产品换货率」。
     *
     * <p><b>金额口径 = Σ 明细 {@code out_amount}（换出金额）</b>，件数口径 = Σ {@code out_quantity}：
     * 与 {@code SaleAnalysisMapper.saleExchangeByDay()} 完全一致 —— 换货单**主表 total_amount 后端从不回写**（恒为 0），
     * 用它会让换货率永远 0、饼图为空（2026-09-15 已修正过一次，此处沿用同一口径）。</p>
     * <p>归期取换货单 {@code create_time}（建单日，全站统一）；区间过滤放 Java 侧（同兄弟模块）。</p>
     */
    @Select("SELECT e.id AS exchange_id, DATE_FORMAT(e.create_time, '%Y-%m-%d') AS d, i.product_id, " +
            "IFNULL(i.out_quantity, 0) AS out_qty, IFNULL(i.out_amount, 0) AS out_amt " +
            "FROM sale_exchange e JOIN sale_exchange_item i ON i.exchange_id = e.id WHERE e.status = 'AUDITED'")
    List<Map<String, Object>> exchangeItemAll();

    /**
     * 下钻：某产品的销售明细行（带单号 / 客户 / 仓库 / 品质，供页面点单号进销售单详情）。
     * 本查询已把 productId 与状态下推到 SQL —— 单产品数据量小，不必再全表拉回。
     */
    @Select("SELECT o.id AS bill_id, o.code AS bill_no, DATE_FORMAT(o.create_time, '%Y-%m-%d') AS d, " +
            "c.name AS customer_name, w.warehouse_name, i.quality_type, " +
            "IFNULL(i.quantity, 0) AS qty, IFNULL(i.unit_price, 0) AS unit_price, IFNULL(i.amount, 0) AS amt " +
            "FROM sale_order o JOIN sale_order_item i ON i.order_id = o.id " +
            "LEFT JOIN customer c ON c.id = o.customer_id LEFT JOIN warehouse w ON w.id = o.warehouse_id " +
            "WHERE o.status = 'AUDITED' AND i.product_id = #{productId}")
    List<Map<String, Object>> saleItemRecords(@Param("productId") Long productId);

    /** 下钻：某产品的退货明细行（退货入库仓不固定 ⇒ 仓库列留空） */
    @Select("SELECT r.id AS bill_id, r.code AS bill_no, DATE_FORMAT(r.create_time, '%Y-%m-%d') AS d, " +
            "c.name AS customer_name, '' AS warehouse_name, i.quality_type, " +
            "IFNULL(i.quantity, 0) AS qty, IFNULL(i.unit_price, 0) AS unit_price, IFNULL(i.amount, 0) AS amt " +
            "FROM sale_return r JOIN sale_return_item i ON i.return_id = r.id " +
            "LEFT JOIN customer c ON c.id = r.customer_id " +
            "WHERE r.status = 'AUDITED' AND i.product_id = #{productId}")
    List<Map<String, Object>> returnItemRecords(@Param("productId") Long productId);
}
