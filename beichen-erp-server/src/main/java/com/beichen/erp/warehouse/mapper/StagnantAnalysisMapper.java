package com.beichen.erp.warehouse.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 滞销（呆滞）分析用聚合 Mapper —— 供「成品库存情况」页的滞销块使用（2026-10-02 用户要求新增）。
 *
 * <p>只回答两个问题，都按**产品**维度：</p>
 * <ol>
 *   <li>{@link #productSaleByDay()}：某产品**哪一天卖了多少**（已审核销售单，按订单**建单日**归期）
 *       ⇒ 由调用方在 Java 侧求「最后销售日」与「近 N 天销量」（本仓既定做法：区位/日期运算放 Java，
 *       与 {@code ProductAnalysisMapper} 一致）。</li>
 *   <li>{@link #productFirstInByProduct()}：某产品**最早一次入库**是哪天（流水正向变动的最小时间）
 *       ⇒ 用于「在库天数」与"从未销售但刚入库 ⇒ 不算严重滞销"的判定。写法与首页
 *       {@code DashboardService.countOverduePendingSort()} 的"首次入库"子查询同源。</li>
 * </ol>
 *
 * <p><b>为什么只取销售单，不取库存流水：</b>滞销口径是"卖不动"（用户 2026-10-02 拍板：默认 15 天没有销售），
 * 用销售单最贴近业务且与全站销售口径同源；改用 `warehouse_stock_log.change_quantity &lt; 0` 会把
 * 品质重分类 / 移仓 / 退货出库 / 反审核一并算成"卖过"（本库实测 13 行负向变动里只有 2 行是 SALE_OUT）
 * ⇒ 滞销清单会**漏报**。详见 {@code WarehouseStockController#stagnantPage} 的口径说明。</p>
 *
 * <p>多租户：登录态下插件对每张表注入 {@code company_id} —— {@code sale_order} / {@code sale_order_item} /
 * {@code warehouse_stock_log} 均已确认有该列（与 {@code ProductAnalysisMapper} 同样的 JOIN 约定）。</p>
 */
@Mapper
public interface StagnantAnalysisMapper {

    /**
     * 已审核销售单的「产品 × 建单日」销量（只做全量 GROUP BY，区间与"最后一天"放 Java 侧算）。
     * 建单日为 NULL 的行无法归期，直接排除（计入会让"最后销售日"落空）。
     */
    @Select("SELECT i.product_id AS pid, DATE_FORMAT(o.create_time, '%Y-%m-%d') AS d, " +
            "IFNULL(SUM(i.quantity), 0) AS qty " +
            "FROM sale_order o JOIN sale_order_item i ON i.order_id = o.id " +
            "WHERE o.status = 'AUDITED' AND i.product_id IS NOT NULL AND o.create_time IS NOT NULL " +
            "GROUP BY i.product_id, DATE_FORMAT(o.create_time, '%Y-%m-%d')")
    List<Map<String, Object>> productSaleByDay();

    /**
     * 产品的**首次入库日**（真入库类变动的最早时间）。没有入库流水的产品不出现在结果里 ⇒ 调用方按"未知"处理。
     *
     * <p><b>⚠️ 2026-10-05 F7-279 修复：必须限定 {@code change_type} 白名单，不能只看 {@code change_quantity > 0}。</b>
     * 正向行里混着**回冲**与**重分类** —— 本库实测 {@code SALE_OUT_UN_AUDIT}（销售反审核回冲）、
     * {@code RECLASSIFY_IN}（品质重分类入）、{@code CANCEL_RECLASSIFY_OUT} 都是正数；拿它们当"首次入库"
     * 会把在库天数算小 ⇒ "从未销售但入库未满 180 天 ⇒ 不算严重滞销"误判 ⇒ **严重滞销漏报**
     * （实测：产品 149 的最早正向行正是 {@code SALE_OUT_UN_AUDIT}）。这与本类上面那条"不能用负向流水判断
     * 『卖过』"是**同一条教训** —— 那一版只把它应用在了负向侧。</p>
     *
     * <p>白名单 = 真正让货进入我方库存的变动类型（对照 {@code StockChangeType} 枚举）：期初 {@code INIT}、
     * 采购入库 {@code PURCHASE_IN}、采购换货入库 {@code PURCHASE_EXCHANGE_IN}、销售退货入库 {@code SALE_RETURN_IN}、
     * 换货退回入库 {@code EXCHANGE_IN}、委外完成入库 {@code OUTSOURCE_FINISH_IN}、其他入库 {@code OTHER_IN}、
     * 盘点盘盈 {@code STOCK_TAKE_IN}。刻意**排除**移仓入（{@code MOVE_IN}，货本来就在账上、只是换了个仓）
     * 与重分类入、各类 {@code CANCEL_*} / {@code *_UN_AUDIT} 回冲。</p>
     */
    @Select("SELECT product_id AS pid, DATE_FORMAT(MIN(create_time), '%Y-%m-%d') AS d " +
            "FROM warehouse_stock_log WHERE product_id IS NOT NULL AND change_quantity > 0 " +
            "AND change_type IN ('INIT','PURCHASE_IN','PURCHASE_EXCHANGE_IN','SALE_RETURN_IN'," +
            "'EXCHANGE_IN','OUTSOURCE_FINISH_IN','OTHER_IN','STOCK_TAKE_IN') " +
            "GROUP BY product_id")
    List<Map<String, Object>> productFirstInByProduct();
}
