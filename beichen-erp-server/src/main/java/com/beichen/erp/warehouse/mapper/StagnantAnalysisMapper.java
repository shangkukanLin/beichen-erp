package com.beichen.erp.warehouse.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 滞销（呆滞）分析用聚合 Mapper —— 供「成品库存详情」页与「产品分析」页的滞销块使用（2026-10-02 新增）。
 *
 * <p><b>2026-10-09 口径变更（用户）</b>：「15 天」的起算点从"最后销售日"扩展为
 * <b>「最近一次来货日」与「最后销售日」中较晚的那个</b> —— 即"来货后 15 天还没卖出去 ⇒ 滞销"
 * （原话：「15 天应该是最新来货（委外加工或者是成品购入）后，15 天这个产品没有销售记录的。就算滞销」），
 * 同时"卖了之后又 15 天没动 ⇒ 照样算滞销"（保留 2026-10-02 原口径的灵敏度）。原「统计窗口（默认 90 天）」
 * 整体取消（用户：「90 天这个不要」）⇒ 本类不再需要按天的销量明细，只需每产品的**最后销售日**。
 * 详见 {@code WarehouseStockController#stagnantPage}。</p>
 *
 * <p>只回答三个问题，都按**产品**维度：</p>
 * <ol>
 *   <li>{@link #productLastSaleByProduct()}：某产品**最后销售日**（已审核销售单，按订单**建单日**归期）。</li>
 *   <li>{@link #productLastInByProduct()}：某产品**最近一次来货日**（{@link #INBOUND_TYPES} 白名单里最晚的一次）
 *       —— 2026-10-09 新增，用于重置滞销时钟。</li>
 *   <li>{@link #productFirstInByProduct()}：某产品**最早一次入库**是哪天（白名单里最早的一次）
 *       ⇒ 用于「在库天数」与"从未销售且无来货 ⇒ 不算严重滞销（视作新品）"的判定。</li>
 * </ol>
 *
 * <p><b>为什么"卖过"只认销售单、不认库存流水：</b>滞销口径是"卖不动"（用户 2026-10-02 拍板），
 * 用销售单最贴近业务且与全站销售口径同源；改用 `warehouse_stock_log.change_quantity &lt; 0` 会把
 * 品质重分类 / 移仓 / 退货出库 / 反审核一并算成"卖过"（本库实测 13 行负向变动里只有 2 行是 SALE_OUT）
 * ⇒ 滞销清单会**漏报**。<b>同理，"来货"必须走正向白名单</b>：正向行里混着回冲与重分类，拿它们当"来货"
 * 会凭空重置滞销时钟 ⇒ 同样漏报（与 F7-279 是同一条教训，只是那次只应用在了负向侧）。</p>
 *
 * <p>多租户：登录态下插件对每张表注入 {@code company_id} —— {@code sale_order} / {@code sale_order_item} /
 * {@code warehouse_stock_log} 均已确认有该列（与 {@code ProductAnalysisMapper} 同样的 JOIN 约定）。</p>
 */
@Mapper
public interface StagnantAnalysisMapper {

    /**
     * 「来货」白名单（用户 2026-10-09 口径：**委外加工回货 + 成品购入**）。
     *
     * <p>{@code OUTSOURCE_FINISH_IN} = 委外收货入库（委外加工完成回货）；
     * {@code PURCHASE_IN} = 采购入库（成品购入）。</p>
     *
     * <p>刻意**不含**：期初 {@code INIT}、销售退货入库 {@code SALE_RETURN_IN}、换货退回 {@code EXCHANGE_IN}、
     * 采购换货入库 {@code PURCHASE_EXCHANGE_IN}、盘点盘盈 {@code STOCK_TAKE_IN}、其他入库 {@code OTHER_IN}
     * （这些都不是"新到的货"，而是退货/盘盈/期初/手工调整）、移仓入 {@code MOVE_IN}（货本来就在账上）、
     * 以及各类 {@code CANCEL_*} / {@code *_UN_AUDIT} 回冲与品质重分类。</p>
     *
     * <p>⚠️ 若日后口径要放宽（例如把委外回仓 {@code OUTSOURCE_BACK_IN}（修好成品回仓）或委外维修入库
     * {@code OUTSOURCE_REPAIR_IN} 也算"来货"），**只改这一个常量即可** ✓。</p>
     */
    String INBOUND_TYPES = "'PURCHASE_IN','OUTSOURCE_FINISH_IN'";

    /**
     * 已审核销售单的「产品 → 最后销售日」（按订单**建单日**归期）。
     * 建单日为 NULL 的行无法归期，直接排除（计入会让"最后销售日"落空）。
     *
     * <p>2026-10-09：原方法 {@code productSaleByDay()} 按「产品 × 天」聚合是为了算"近 N 天销量"；
     * 统计窗口取消后只需最后一天 ⇒ 改为按产品取 MAX（更省内存，语义不变）。</p>
     */
    @Select("SELECT i.product_id AS pid, DATE_FORMAT(MAX(o.create_time), '%Y-%m-%d') AS d " +
            "FROM sale_order o JOIN sale_order_item i ON i.order_id = o.id " +
            "WHERE o.status = 'AUDITED' AND i.product_id IS NOT NULL AND o.create_time IS NOT NULL " +
            "GROUP BY i.product_id")
    List<Map<String, Object>> productLastSaleByProduct();

    /**
     * 产品的**最近一次来货日**（{@link #INBOUND_TYPES} 白名单里最晚的一次）。
     * 没有任何来货流水的产品不出现在结果里 ⇒ 调用方按"未知"处理（此时滞销时钟只看最后销售日）。
     */
    @Select("SELECT product_id AS pid, DATE_FORMAT(MAX(create_time), '%Y-%m-%d') AS d " +
            "FROM warehouse_stock_log WHERE product_id IS NOT NULL AND change_quantity > 0 " +
            "AND change_type IN (" + INBOUND_TYPES + ") " +
            "GROUP BY product_id")
    List<Map<String, Object>> productLastInByProduct();

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
     *
     * <p>⚠️ 注意本白名单比 {@link #INBOUND_TYPES}（"来货"）**宽**：这里问的是"货最早什么时候进过我的库"
     * （含期初/退货/盘盈/其他），而"来货"只认"新买回来/委外加工回来的货"。两者口径不同，不要合并。</p>
     */
    @Select("SELECT product_id AS pid, DATE_FORMAT(MIN(create_time), '%Y-%m-%d') AS d " +
            "FROM warehouse_stock_log WHERE product_id IS NOT NULL AND change_quantity > 0 " +
            "AND change_type IN ('INIT','PURCHASE_IN','PURCHASE_EXCHANGE_IN','SALE_RETURN_IN'," +
            "'EXCHANGE_IN','OUTSOURCE_FINISH_IN','OTHER_IN','STOCK_TAKE_IN') " +
            "GROUP BY product_id")
    List<Map<String, Object>> productFirstInByProduct();
}
