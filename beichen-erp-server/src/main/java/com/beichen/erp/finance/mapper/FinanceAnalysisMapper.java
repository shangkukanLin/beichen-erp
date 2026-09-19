package com.beichen.erp.finance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 财务分析聚合 Mapper：利润表/资金趋势/账龄/排行 的统计 SQL。
 * 登录态下多租户插件自动注入 company_id 过滤；全部为只读聚合查询。
 */
@Mapper
public interface FinanceAnalysisMapper {

    /** 销售收入（按**建单时间**归月；2026-09-15 用户要求全站统一为建单日口径） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m') AS ym, IFNULL(SUM(total_amount), 0) AS amt FROM sale_order WHERE status = 'AUDITED' GROUP BY ym")
    List<Map<String, Object>> saleByMonth();

    /** 采购成本（按**建单时间**归月；已去掉原 `audit_time IS NOT NULL` 过滤，避免漏统计） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m') AS ym, IFNULL(SUM(total_amount), 0) AS amt FROM purchase_order WHERE status = 'AUDITED' GROUP BY ym")
    List<Map<String, Object>> purchaseByMonth();

    /** 销售退货（冲减收入，按建单时间归月） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m') AS ym, IFNULL(SUM(total_amount), 0) AS amt FROM sale_return WHERE status = 'AUDITED' GROUP BY ym")
    List<Map<String, Object>> saleReturnByMonth();

    /** 退货折损收款（已审核销售退货单的 loss_amount，向客户收取的补偿，收入性质；与退货同按建单时间归月） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m') AS ym, IFNULL(SUM(loss_amount), 0) AS amt FROM sale_return WHERE status = 'AUDITED' AND loss_amount > 0 GROUP BY ym")
    List<Map<String, Object>> saleReturnLossByMonth();

    /** 采购退货（冲减成本） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m') AS ym, IFNULL(SUM(total_amount), 0) AS amt FROM purchase_return WHERE status = 'AUDITED' GROUP BY ym")
    List<Map<String, Object>> purchaseReturnByMonth();

    /** 费用（按**建单时间**归月；2026-09-15 由 expense_date 改为 create_time 以统一口径） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m') AS ym, IFNULL(SUM(amount), 0) AS amt FROM finance_expense WHERE status = 'AUDITED' GROUP BY ym")
    List<Map<String, Object>> expenseByMonth();

    // ==================== 利润表明细（按天，口径与按月版完全一致） ====================
    // 全量按天聚合、Java 侧按区间取值：不把日期参数放进 SQL（此前 BETWEEN 参数绑定实测取不到数据）

    /** 销售收入（按**建单时间**归日） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, IFNULL(SUM(total_amount), 0) AS amt FROM sale_order WHERE status = 'AUDITED' GROUP BY d")
    List<Map<String, Object>> saleByDay();

    /** 采购成本（按**建单时间**归日） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, IFNULL(SUM(total_amount), 0) AS amt FROM purchase_order WHERE status = 'AUDITED' GROUP BY d")
    List<Map<String, Object>> purchaseByDay();

    /** 销售退货（冲减收入，按建单时间归日） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, IFNULL(SUM(total_amount), 0) AS amt FROM sale_return WHERE status = 'AUDITED' GROUP BY d")
    List<Map<String, Object>> saleReturnByDay();

    /** 退货折损收款（向客户收取的补偿，收入性质） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, IFNULL(SUM(loss_amount), 0) AS amt FROM sale_return WHERE status = 'AUDITED' AND loss_amount > 0 GROUP BY d")
    List<Map<String, Object>> saleReturnLossByDay();

    /** 采购退货（冲减成本） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, IFNULL(SUM(total_amount), 0) AS amt FROM purchase_return WHERE status = 'AUDITED' GROUP BY d")
    List<Map<String, Object>> purchaseReturnByDay();

    // ==================== 利润表成本口径（B）：销售出库成本 ====================
    // 口径：成本 = Σ(销售明细数量 − 退货明细数量) × 产品当前移动加权成本价 cost_price。
    // 与客户分析/销售分析同源（同一 cost_price），保证跨页面"毛利"可对账；
    // 采购入库属资产，不再计入利润表成本（采购金额仍可在资金/采购模块查看）。

    /** 销售出库成本按天：销售明细行数量 × 产品当前移动加权成本价（按销售单**建单日**归日） */
    @Select("SELECT DATE_FORMAT(o.create_time, '%Y-%m-%d') AS d, IFNULL(SUM(i.quantity * IFNULL(p.cost_price, 0)), 0) AS amt " +
            "FROM sale_order_item i JOIN sale_order o ON o.id = i.order_id LEFT JOIN product p ON p.id = i.product_id " +
            "WHERE o.status = 'AUDITED' GROUP BY d")
    List<Map<String, Object>> saleCostByDay();

    /** 退货冲回成本按天：销售退货明细行数量 × 产品当前移动加权成本价（按退货单建单日归日） */
    @Select("SELECT DATE_FORMAT(r.create_time, '%Y-%m-%d') AS d, IFNULL(SUM(i.quantity * IFNULL(p.cost_price, 0)), 0) AS amt " +
            "FROM sale_return_item i JOIN sale_return r ON r.id = i.return_id LEFT JOIN product p ON p.id = i.product_id " +
            "WHERE r.status = 'AUDITED' GROUP BY d")
    List<Map<String, Object>> saleReturnCostByDay();

    /** 费用（按**建单时间**归日） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, IFNULL(SUM(amount), 0) AS amt FROM finance_expense WHERE status = 'AUDITED' GROUP BY d")
    List<Map<String, Object>> expenseByDay();

    // ==================== 进货分析（2026-09-15 新增，供「经营分析 → 进货分析」） ====================
    // 归期口径（2026-09-15 全站统一）：采购单、采购退货**均按建单日** create_time

    /** 采购单数（按**建单日**归日） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, COUNT(*) AS amt FROM purchase_order WHERE status = 'AUDITED' GROUP BY d")
    List<Map<String, Object>> purchaseCountByDay();

    /** 采购单明细（下钻用：d=**建单日**、partner=供应商名称） */
    @Select("SELECT o.id, o.code, DATE_FORMAT(o.create_time, '%Y-%m-%d') AS d, s.name AS partner, o.total_amount, o.remark FROM purchase_order o LEFT JOIN supplier s ON s.id = o.supplier_id WHERE o.status = 'AUDITED'")
    List<Map<String, Object>> purchaseOrderRecords();

    /** 采购退货单明细（下钻用：d=建单日、partner=供应商名称） */
    @Select("SELECT o.id, o.code, DATE_FORMAT(o.create_time, '%Y-%m-%d') AS d, s.name AS partner, o.total_amount, o.remark FROM purchase_return o LEFT JOIN supplier s ON s.id = o.supplier_id WHERE o.status = 'AUDITED'")
    List<Map<String, Object>> purchaseReturnRecords();

    // ==================== 进货分析的 2 个饼图（2026-09-15 新增） ====================
    // 均为「按产品」分片；金额与件数一次查回（前端每卡一个「金额/件数」switch）

    /**
     * **直接采购成品**明细按「产品 × **建单日**」（饼图用）：已审核采购单。
     * 区间过滤在 Java 侧（与全站做法一致）；`d` = 建单日。
     */
    @Select("SELECT i.product_id, p.name AS product_name, DATE_FORMAT(o.create_time, '%Y-%m-%d') AS d, " +
            "IFNULL(SUM(i.amount), 0) AS amt, IFNULL(SUM(i.quantity), 0) AS qty " +
            "FROM purchase_order o JOIN purchase_order_item i ON i.order_id = o.id " +
            "LEFT JOIN product p ON p.id = i.product_id " +
            "WHERE o.status = 'AUDITED' " +
            "GROUP BY i.product_id, p.name, d")
    List<Map<String, Object>> purchaseItemByProduct();

    /**
     * **采购退货**明细按「产品 × 建单日」（饼图用，作为净额的**冲减项**）：归期 = 建单日，
     * 与 `purchaseReturnByDay()` 口径一致。仅金额/件数取正数，冲减由 Service 侧取负。
     */
    @Select("SELECT i.product_id, p.name AS product_name, DATE_FORMAT(r.create_time, '%Y-%m-%d') AS d, " +
            "IFNULL(SUM(i.amount), 0) AS amt, IFNULL(SUM(i.quantity), 0) AS qty " +
            "FROM purchase_return r JOIN purchase_return_item i ON i.return_id = r.id " +
            "LEFT JOIN product p ON p.id = i.product_id " +
            "WHERE r.status = 'AUDITED' " +
            "GROUP BY i.product_id, p.name, d")
    List<Map<String, Object>> purchaseReturnItemByProduct();

    /**
     * **委外加工成品入库**按「成品产品 × 交货日期」（饼图用）：已审核交货记录（审核后成品入库 + 生成应付）。
     * <p>该表**无金额字段**：金额按 `Σ(交货数量 × 加工单价)` 计算（`outsource_order_product.unit_price`），
     * 与审核时生成的应付金额口径一致（见 OutsourceOrderDeliveryServiceImpl#createDeliveryPayable）。</p>
     * <p>归期 = **建单日**（2026-09-15 全站统一，原为交货日期）；退不良记录数量为负 → 自动冲减；成品取 `product_master_id`。</p>
     */
    @Select("SELECT od.product_master_id, p.name AS product_name, DATE_FORMAT(od.create_time, '%Y-%m-%d') AS d, " +
            "IFNULL(SUM(od.quantity * IFNULL(op.unit_price, 0)), 0) AS amt, IFNULL(SUM(od.quantity), 0) AS qty " +
            "FROM outsource_order_delivery od " +
            "LEFT JOIN outsource_order_product op ON op.id = od.product_id " +
            "LEFT JOIN product p ON p.id = od.product_master_id " +
            "WHERE od.status = 'AUDITED' " +
            "GROUP BY od.product_master_id, p.name, d")
    List<Map<String, Object>> outsourceInByProduct();

    // ==================== 税务分析（已税/未税） ====================

    /**
     * 销售额按月 × 税状态聚合（已税=tax_included=1，从中拆出税额；未税=tax_included=0）。
     * 已审核单据，按**建单时间**归月（2026-09-15 全站统一）。
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m') AS ym, tax_included, IFNULL(SUM(total_amount), 0) AS amt, IFNULL(SUM(tax_amount), 0) AS tax FROM sale_order WHERE status = 'AUDITED' GROUP BY ym, tax_included")
    List<Map<String, Object>> saleTaxByMonth();

    /** 采购额按月 × 税状态聚合（进项视角；归期 = **建单时间**） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m') AS ym, tax_included, IFNULL(SUM(total_amount), 0) AS amt, IFNULL(SUM(tax_amount), 0) AS tax FROM purchase_order WHERE status = 'AUDITED' GROUP BY ym, tax_included")
    List<Map<String, Object>> purchaseTaxByMonth();

    /**
     * 销售退货按月 × 税状态聚合（**冲减销项**，返回正数，由 Java 侧取负）。
     * <p>退货单本身没有含税标记与税额字段，故**跟随原销售单**：`tax_included` 取原单，
     * 税额按原单实际税负比例 `tax_amount / total_amount` 折算（原单不存在或无金额时按 0，即视同未税）。
     */
    @Select("SELECT DATE_FORMAT(r.create_time, '%Y-%m') AS ym, IFNULL(o.tax_included, 0) AS tax_included, " +
            "IFNULL(SUM(r.total_amount), 0) AS amt, " +
            "IFNULL(SUM(r.total_amount * IFNULL(o.tax_amount / NULLIF(o.total_amount, 0), 0)), 0) AS tax " +
            "FROM sale_return r LEFT JOIN sale_order o ON o.id = r.sale_order_id " +
            "WHERE r.status = 'AUDITED' GROUP BY ym, tax_included")
    List<Map<String, Object>> saleReturnTaxByMonth();

    /**
     * 采购退货按月 × 税状态聚合（**冲减进项**，返回正数，由 Java 侧取负）。
     * 同销售退货：含税标记与税负比例均跟随原采购单。
     */
    @Select("SELECT DATE_FORMAT(r.create_time, '%Y-%m') AS ym, IFNULL(o.tax_included, 0) AS tax_included, " +
            "IFNULL(SUM(r.total_amount), 0) AS amt, " +
            "IFNULL(SUM(r.total_amount * IFNULL(o.tax_amount / NULLIF(o.total_amount, 0), 0)), 0) AS tax " +
            "FROM purchase_return r LEFT JOIN purchase_order o ON o.id = r.purchase_order_id " +
            "WHERE r.status = 'AUDITED' GROUP BY ym, tax_included")
    List<Map<String, Object>> purchaseReturnTaxByMonth();

    /** 委外加工费按月 × 税状态聚合（无审核时间，按建单时间归月；已审核=生产中/已完成） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m') AS ym, tax_included, IFNULL(SUM(total_amount), 0) AS amt, IFNULL(SUM(tax_amount), 0) AS tax FROM outsource_order WHERE status IN ('PRODUCING', 'FINISHED') GROUP BY ym, tax_included")
    List<Map<String, Object>> outsourceTaxByMonth();

    /** 发票汇总（税务口径：已登记发票按方向合计不含税金额与税额） */
    @Select("SELECT direction, IFNULL(SUM(amount), 0) AS amt, IFNULL(SUM(tax_amount), 0) AS tax, COUNT(*) AS cnt FROM finance_invoice WHERE status = 'REGISTERED' GROUP BY direction")
    List<Map<String, Object>> invoiceSummary();

    /** 资金收支（按流水时间归月） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m') AS ym, IFNULL(SUM(income), 0) AS income, IFNULL(SUM(expense), 0) AS expense FROM finance_cashflow GROUP BY ym")
    List<Map<String, Object>> cashflowByMonth();

    /** 资金收支（按流水时间归日；2026-09-15 新增：资金趋势支持「统计区间」后，短区间需按天展示） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, IFNULL(SUM(income), 0) AS income, IFNULL(SUM(expense), 0) AS expense FROM finance_cashflow GROUP BY d")
    List<Map<String, Object>> cashflowByDay();

    /** 应收账龄分桶（未结清部分；none=无到期日） */
    @Select("SELECT CASE WHEN due_date IS NULL THEN 'none' WHEN due_date >= CURDATE() THEN 'not_due' WHEN due_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) THEN 'd30' WHEN due_date >= DATE_SUB(CURDATE(), INTERVAL 60 DAY) THEN 'd60' ELSE 'd60p' END AS bucket, IFNULL(SUM(unpaid_amount), 0) AS amt, COUNT(*) AS cnt FROM finance_receivable WHERE status IN ('UNSETTLED', 'PARTIAL') GROUP BY bucket")
    List<Map<String, Object>> receivableAging();

    /** 应付账龄分桶（对称；2026-09-19 F7-34：剔除已转应收的冲减项，避免与应收双算） */
    @Select("SELECT CASE WHEN due_date IS NULL THEN 'none' WHEN due_date >= CURDATE() THEN 'not_due' WHEN due_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) THEN 'd30' WHEN due_date >= DATE_SUB(CURDATE(), INTERVAL 60 DAY) THEN 'd60' ELSE 'd60p' END AS bucket, IFNULL(SUM(unpaid_amount), 0) AS amt, COUNT(*) AS cnt FROM finance_payable WHERE status IN ('UNSETTLED', 'PARTIAL') AND IFNULL(transferred_to_receivable, 0) <> 1 GROUP BY bucket")
    List<Map<String, Object>> payableAging();

    /**
     * 应收汇总（回款率）。
     * <p><b>2026-09-19 修复（F7-40）</b>：`unpaid` 只计<b>未结清</b>（UNSETTLED/PARTIAL）—— 预收台账（ADVANCE，
     * 多收款形成的负数应收）必须剔除，否则「应收未收」会被负台账冲减（实测 -4,260，业务上不可解释），
     * 且与本页账龄分桶（只取 UNSETTLED/PARTIAL）不一致。
     * `total`/`paid` <b>保持全量</b>（含已结清）—— 它们是回款率的分母/分子，收紧会把已结清历史排除、把回款率算坏。</p>
     */
    @Select("SELECT IFNULL(SUM(amount), 0) AS total, IFNULL(SUM(paid_amount), 0) AS paid, IFNULL(SUM(CASE WHEN status IN ('UNSETTLED', 'PARTIAL') THEN unpaid_amount ELSE 0 END), 0) AS unpaid FROM finance_receivable WHERE status != 'CANCELLED'")
    Map<String, Object> receivableSummary();

    /**
     * 应付汇总（付款率；2026-09-19 F7-34：剔除已转应收的冲减项）。
     * <p><b>2026-09-19 修复（F7-40）</b>：`unpaid` 只计<b>未结清</b>（UNSETTLED/PARTIAL）—— 预付台账（ADVANCE，
     * 多付款形成的负数应付）剔除，与同页账龄分桶口径一致（实测修前 63,000 vs 分桶 65,090，差 2,090 即该台账）。
     * `total`/`paid` 保持全量（付款率的分母/分子）。</p>
     */
    @Select("SELECT IFNULL(SUM(amount), 0) AS total, IFNULL(SUM(paid_amount), 0) AS paid, IFNULL(SUM(CASE WHEN status IN ('UNSETTLED', 'PARTIAL') THEN unpaid_amount ELSE 0 END), 0) AS unpaid FROM finance_payable WHERE status != 'CANCELLED' AND IFNULL(transferred_to_receivable, 0) <> 1")
    Map<String, Object> payableSummary();

    /** TOP 客户欠款 */
    @Select("SELECT customer_name AS name, IFNULL(SUM(unpaid_amount), 0) AS unpaid FROM finance_receivable WHERE status IN ('UNSETTLED', 'PARTIAL') GROUP BY customer_name ORDER BY unpaid DESC LIMIT 5")
    List<Map<String, Object>> topCustomers();

    /** TOP 供应商应付（2026-09-19 F7-34：剔除已转应收的冲减项，避免负值行挤占 TOP 榜并虚增总额） */
    @Select("SELECT supplier_name AS name, IFNULL(SUM(unpaid_amount), 0) AS unpaid FROM finance_payable WHERE status IN ('UNSETTLED', 'PARTIAL') AND IFNULL(transferred_to_receivable, 0) <> 1 GROUP BY supplier_name ORDER BY unpaid DESC LIMIT 5")
    List<Map<String, Object>> topSuppliers();

    /** 资金账户余额分布（余额=期初+收支流水累计） */
    @Select("SELECT a.account_name AS name, a.account_type AS type, IFNULL(SUM(IFNULL(cf.income, 0) - IFNULL(cf.expense, 0)), 0) AS balance FROM finance_account a LEFT JOIN finance_cashflow cf ON cf.account_id = a.id WHERE a.status = 1 GROUP BY a.id, a.account_name, a.account_type ORDER BY balance DESC")
    List<Map<String, Object>> accountDistribution();
}
