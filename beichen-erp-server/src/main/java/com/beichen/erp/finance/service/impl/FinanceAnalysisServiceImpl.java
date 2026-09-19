package com.beichen.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceAnalysisMapper;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import com.beichen.erp.finance.service.FinanceAnalysisService;
import com.beichen.erp.inventory.entity.InventoryStockLoss;
import com.beichen.erp.inventory.mapper.InventoryStockLossMapper;
import com.beichen.erp.outsource.entity.OutsourceStockLoss;
import com.beichen.erp.outsource.mapper.OutsourceStockLossMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 财务分析 Service 实现：纯查询聚合，不改任何业务数据。
 * <p>口径（2026-09-11 起，P2-27 定稿为"销售成本"口径；**归期 2026-09-15 全站统一为「建单日」create_time**）：
 * 收入 = 已审核销售单(create_time 归月) − 销售退货(create_time) + 退货折损收款；
 * 成本 = Σ(销售明细数量 − 退货明细数量) × 产品当前移动加权成本价 {@code product.cost_price}
 * （与客户分析/销售分析同源，保证跨页面"毛利"可对账）；
 * 费用 = 已审核费用单(create_time 归月)；净利润 = 毛利 − 费用；现金流 = 资金流水按月收 − 支。
 * <p>注：采购入库属资产、不是当期损益，故利润表**不再把采购金额计为成本**（采购金额见采购与资金模块）。
 */
@Service
@RequiredArgsConstructor
public class FinanceAnalysisServiceImpl implements FinanceAnalysisService {

    private final FinanceAnalysisMapper analysisMapper;
    private final FinancePayableMapper payableMapper;
    private final FinanceReceivableMapper receivableMapper;
    private final InventoryStockLossMapper inventoryLossMapper;
    private final OutsourceStockLossMapper outsourceLossMapper;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** List<Map{ym,amt}> → Map<月, 金额> */
    private Map<String, BigDecimal> toAmtMap(List<Map<String, Object>> list) {
        Map<String, BigDecimal> m = new HashMap<>();
        if (list != null) for (Map<String, Object> r : list) {
            Object ym = r.get("ym");
            if (ym != null) m.put(ym.toString(), toBd(r.get("amt")));
        }
        return m;
    }

    private BigDecimal toBd(Object v) { return v == null ? ZERO : new BigDecimal(v.toString()); }

    /** List<Map{d,amt}> → Map<日期, 金额>（按天版，key 是 d 而非 ym） */
    private Map<String, BigDecimal> toDayMap(List<Map<String, Object>> list) {
        Map<String, BigDecimal> m = new HashMap<>();
        if (list != null) for (Map<String, Object> r : list) {
            Object d = r.get("d");
            if (d != null) m.put(d.toString(), toBd(r.get("amt")));
        }
        return m;
    }

    /** List<Map{d,amt}> → Map<月, 金额>（把按天聚合结果汇总到月，使按月与按天两条链路口径完全一致） */
    private Map<String, BigDecimal> dayToMonthMap(List<Map<String, Object>> list) {
        Map<String, BigDecimal> m = new HashMap<>();
        if (list != null) for (Map<String, Object> r : list) {
            Object d = r.get("d");
            if (d != null) m.merge(d.toString().substring(0, 7), toBd(r.get("amt")), BigDecimal::add);
        }
        return m;
    }

    /**
     * 区间防滥用上限（天）。F7-43#3（2026-09-19）**统一阈值** —— 原先 {@code overviewKpi}/{@code purchaseAnalysis}
     * 截到 400 天、{@code cashTrend} 截到 200 天，且都**静默**（用户看不到被截断）。现在统一走本常量，
     * 并在返回结构里回带 {@code truncated} / {@code maxDays} 提示。
     */
    private static final long MAX_RANGE_DAYS = 400;
    /** 月份序列上限（月）：与 {@code monthRange} 既有的 60 护栏一致（F7-43#2） */
    private static final int MAX_RANGE_MONTHS = 60;
    /** F7-43#4：饼图「其它（未指定产品）」分片的伪 key（{@code product_id} 为空的行归此片，不再静默丢弃） */
    private static final Long OTHER_PID = -1L;
    private static final String OTHER_NAME = "其它（未指定产品）";

    /** 近 months 个月（含本月，升序）；F7-43#2（2026-09-19）：加 60 个月上限（原 months 由请求直传、无上限） */
    private List<String> monthList(int months) {
        if (months < 1) months = 1;
        if (months > MAX_RANGE_MONTHS) months = MAX_RANGE_MONTHS;
        List<String> list = new ArrayList<>();
        LocalDate cur = LocalDate.now().withDayOfMonth(1);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int i = months - 1; i >= 0; i--) list.add(cur.minusMonths(i).format(fmt));
        return list;
    }

    /** 自定义日期区间 → 月份序列（含首尾月，升序；上限 60 个月防滥用） */
    private List<String> monthRange(LocalDate start, LocalDate end) {
        List<String> list = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        LocalDate s = start.withDayOfMonth(1);
        LocalDate e = end.withDayOfMonth(1);
        if (s.isAfter(e)) { LocalDate t = s; s = e; e = t; }
        int guard = 0;
        while (!s.isAfter(e) && guard < 60) {
            list.add(s.format(fmt));
            s = s.plusMonths(1);
            guard++;
        }
        return list;
    }

    private BigDecimal rate(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(ZERO) == 0) return ZERO;
        return part.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }

    // ==================== 利润表明细（按天） ====================

    /** 快捷区间解析：返回 [start, end]，末日为今天（统计已发生数据） */
    private LocalDate[] resolvePresetRange(String preset) {
        LocalDate today = LocalDate.now();
        switch (preset == null ? "today" : preset) {
            case "yesterday": LocalDate y = today.minusDays(1); return new LocalDate[]{y, y};
            case "week":      return new LocalDate[]{today.with(java.time.DayOfWeek.MONDAY), today};
            case "month":     return new LocalDate[]{today.withDayOfMonth(1), today};
            case "quarter":   return new LocalDate[]{today.withDayOfMonth(1).withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1), today};
            case "year":      return new LocalDate[]{today.withDayOfYear(1), today};
            case "today":
            default:          return new LocalDate[]{today, today};
        }
    }

    // ==================== 首页经营总览 KPI（区间 4 指标 + 固定本年） ====================

    /**
     * 首页经营总览 KPI（2026-09-15 新增）：所选区间的 4 指标 + 固定「本年 1/1~今天」的同 4 指标。
     * 8 张按天聚合表**只查一次**，两个区间在同一份数据上求和（口径与利润表 profitDetail 同源）。
     */
    @Override
    public Map<String, Object> overviewKpi(String preset, String start, String end) {
        // 区间：自定义 start/end 优先，否则按快捷预设解析；start > end 自动交换（与 profitDetail 同规则）
        LocalDate s, e;
        boolean custom = start != null && !start.isBlank() && end != null && !end.isBlank();
        if (custom) {
            s = LocalDate.parse(start);
            e = LocalDate.parse(end);
            if (s.isAfter(e)) { LocalDate t = s; s = e; e = t; }
        } else {
            LocalDate[] r = resolvePresetRange(preset);
            s = r[0]; e = r[1];
        }
        // 防滥用：按天展开，最长 MAX_RANGE_DAYS 天（F7-43#3：统一阈值 + 回带 truncated 提示，不再静默截断）
        boolean truncated = s.plusDays(MAX_RANGE_DAYS).isBefore(e);
        if (truncated) s = e.minusDays(MAX_RANGE_DAYS - 1);

        Map<String, Map<String, BigDecimal>> dayMaps = kpiDayMaps();
        LocalDate today = LocalDate.now();

        Map<String, Object> range = new LinkedHashMap<>();
        range.put("start", s.toString());
        range.put("end", e.toString());
        range.put("preset", custom ? "custom" : (preset == null ? "today" : preset));
        range.put("truncated", truncated);          // F7-43#3：区间被截断时前端需提示用户
        range.put("maxDays", MAX_RANGE_DAYS);

        // 趋势图区间（2026-09-15 用户确认）：默认与所选区间一致；**不足 7 天**（今日/昨日/短自定义）
        // 时以结束日为终点向前补足 7 天，避免曲线只有 1 个点。注意卡片数值仍严格按所选区间（range）。
        LocalDate seriesStart = ChronoUnit.DAYS.between(s, e) + 1 < 7 ? e.minusDays(6) : s;

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("range", range);
        res.put("kpi", sumKpi(dayMaps, s, e));
        res.put("year", sumKpi(dayMaps, today.withDayOfYear(1), today));
        res.put("series", buildSeries(dayMaps, seriesStart, e));
        return res;
    }

    /** 首页 KPI 所需的 8 张按天聚合表（key 含义见 sumKpi） */
    private Map<String, Map<String, BigDecimal>> kpiDayMaps() {
        Map<String, Map<String, BigDecimal>> m = new LinkedHashMap<>();
        m.put("sale", toDayMap(analysisMapper.saleByDay()));
        m.put("saleRet", toDayMap(analysisMapper.saleReturnByDay()));
        m.put("loss", toDayMap(analysisMapper.saleReturnLossByDay()));
        m.put("saleCost", toDayMap(analysisMapper.saleCostByDay()));
        m.put("retCost", toDayMap(analysisMapper.saleReturnCostByDay()));
        m.put("expense", toDayMap(analysisMapper.expenseByDay()));
        m.put("purchase", toDayMap(analysisMapper.purchaseByDay()));
        m.put("purchaseRet", toDayMap(analysisMapper.purchaseReturnByDay()));
        return m;
    }

    /**
     * 单日 4 指标（**区间汇总 sumKpi 与趋势序列 buildSeries 共用**，避免两处口径漂移）：
     * 销售金额 = 销售单 − 销售退货 + 折损收款；采购支出 = 采购单 − 采购退货（采购入库属资产、不计入损益）；
     * 费用支出 = 费用单；净利润 = 销售金额 − 销售成本 − 费用支出（销售成本 = 销售出库成本 − 退货冲回成本）。
     */
    private Map<String, BigDecimal> dayKpi(Map<String, Map<String, BigDecimal>> dm, LocalDate d) {
        String k = d.toString();
        BigDecimal sale = dm.get("sale").getOrDefault(k, ZERO)
                .subtract(dm.get("saleRet").getOrDefault(k, ZERO))
                .add(dm.get("loss").getOrDefault(k, ZERO));
        BigDecimal cost = dm.get("saleCost").getOrDefault(k, ZERO).subtract(dm.get("retCost").getOrDefault(k, ZERO));
        BigDecimal purchase = dm.get("purchase").getOrDefault(k, ZERO).subtract(dm.get("purchaseRet").getOrDefault(k, ZERO));
        BigDecimal expense = dm.get("expense").getOrDefault(k, ZERO);
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        m.put("saleAmount", sale);
        m.put("purchaseSpend", purchase);
        m.put("expenseSpend", expense);
        m.put("netProfit", sale.subtract(cost).subtract(expense));
        return m;
    }

    /**
     * 区间 4 指标合计（卡片用；口径与利润表 profitDetail 完全一致）。
     * 采购入库属资产、不计入损益 → 采购支出与净利润**互不相减**，仅并列展示。
     */
    private Map<String, Object> sumKpi(Map<String, Map<String, BigDecimal>> dm, LocalDate s, LocalDate e) {
        BigDecimal sale = ZERO, purchase = ZERO, expense = ZERO, netProfit = ZERO;
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
            Map<String, BigDecimal> p = dayKpi(dm, d);
            sale = sale.add(p.get("saleAmount"));
            purchase = purchase.add(p.get("purchaseSpend"));
            expense = expense.add(p.get("expenseSpend"));
            netProfit = netProfit.add(p.get("netProfit"));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("saleAmount", sale);
        m.put("purchaseSpend", purchase);
        m.put("expenseSpend", expense);
        m.put("netProfit", netProfit);
        return m;
    }

    /** 趋势粒度阈值（天）：区间 ≤ 该值按天，否则按月（2026-09-15 用户确认 62 天） */
    private static final long SERIES_DAY_MAX = 62;

    /**
     * 趋势序列（2026-09-15 新增，供首页曲线图**随统计区间变化**）：与卡片同一批按天数据、同一口径。
     * 粒度：区间 ≤ {@link #SERIES_DAY_MAX} 天 → 按天（label=yyyy-MM-dd）；否则 → 按月（label=yyyy-MM）。
     */
    private Map<String, Object> buildSeries(Map<String, Map<String, BigDecimal>> dm, LocalDate s, LocalDate e) {
        boolean byMonth = ChronoUnit.DAYS.between(s, e) + 1 > SERIES_DAY_MAX;
        List<Map<String, Object>> points = new ArrayList<>();
        if (!byMonth) {
            for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
                Map<String, Object> pt = new LinkedHashMap<>();
                pt.put("label", d.toString());
                pt.putAll(dayKpi(dm, d));
                points.add(pt);
            }
        } else {
            String cur = null;
            BigDecimal sale = ZERO, purchase = ZERO, expense = ZERO, netProfit = ZERO;
            for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
                String ym = d.toString().substring(0, 7);
                if (cur != null && !ym.equals(cur)) {
                    points.add(monthPoint(cur, sale, purchase, expense, netProfit));
                    sale = ZERO; purchase = ZERO; expense = ZERO; netProfit = ZERO;
                }
                cur = ym;
                Map<String, BigDecimal> p = dayKpi(dm, d);
                sale = sale.add(p.get("saleAmount"));
                purchase = purchase.add(p.get("purchaseSpend"));
                expense = expense.add(p.get("expenseSpend"));
                netProfit = netProfit.add(p.get("netProfit"));
            }
            if (cur != null) points.add(monthPoint(cur, sale, purchase, expense, netProfit));
        }
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("granularity", byMonth ? "month" : "day");
        series.put("start", s.toString());
        series.put("end", e.toString());
        series.put("points", points);
        return series;
    }

    /** 按月聚合的趋势点（label=yyyy-MM） */
    private Map<String, Object> monthPoint(String ym, BigDecimal sale, BigDecimal purchase,
                                           BigDecimal expense, BigDecimal netProfit) {
        Map<String, Object> pt = new LinkedHashMap<>();
        pt.put("label", ym);
        pt.put("saleAmount", sale);
        pt.put("purchaseSpend", purchase);
        pt.put("expenseSpend", expense);
        pt.put("netProfit", netProfit);
        return pt;
    }

    // ==================== 进货分析（2026-09-15 新增，供「经营分析 → 进货分析」） ====================

    /**
     * 进货分析：所选区间的采购 KPI + 趋势序列 + 单据明细（下钻）。
     * 口径（与 overview-kpi 的采购项完全一致；**F7-42 · 2026-09-19 纠偏**）：采购金额 = 已审核采购单、
     * 采购退货 = 已审核采购退货，**归期一律按建单日（{@code create_time}）**（原写"按审核日"与实现不符）；
     * 净采购额 = 采购金额 − 采购退货；采购单数 = 已审核采购单笔数。
     * 注：采购入库属资产、不计入损益，本页只做采购视角统计。
     */
    @Override
    public Map<String, Object> purchaseAnalysis(String preset, String start, String end) {
        LocalDate s, e;
        boolean custom = start != null && !start.isBlank() && end != null && !end.isBlank();
        if (custom) {
            s = LocalDate.parse(start); e = LocalDate.parse(end);
            if (s.isAfter(e)) { LocalDate t = s; s = e; e = t; }
        } else {
            LocalDate[] r = resolvePresetRange(preset);
            s = r[0]; e = r[1];
        }
        // 防滥用：按天展开，最长 MAX_RANGE_DAYS 天（F7-43#3：统一阈值 + 回带 truncated 提示）
        boolean truncated = s.plusDays(MAX_RANGE_DAYS).isBefore(e);
        if (truncated) s = e.minusDays(MAX_RANGE_DAYS - 1);

        Map<String, Map<String, BigDecimal>> dm = new LinkedHashMap<>();
        dm.put("pur", toDayMap(analysisMapper.purchaseByDay()));
        dm.put("purRet", toDayMap(analysisMapper.purchaseReturnByDay()));
        dm.put("purCnt", toDayMap(analysisMapper.purchaseCountByDay()));

        BigDecimal amount = ZERO, retAmount = ZERO, cnt = ZERO;
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
            String k = d.toString();
            amount = amount.add(dm.get("pur").getOrDefault(k, ZERO));
            retAmount = retAmount.add(dm.get("purRet").getOrDefault(k, ZERO));
            cnt = cnt.add(dm.get("purCnt").getOrDefault(k, ZERO));
        }

        Map<String, Object> range = new LinkedHashMap<>();
        range.put("start", s.toString());
        range.put("end", e.toString());
        range.put("preset", custom ? "custom" : (preset == null ? "month" : preset));
        range.put("truncated", truncated);          // F7-43#3：区间被截断时前端需提示用户
        range.put("maxDays", MAX_RANGE_DAYS);

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("purchaseAmount", amount);
        kpi.put("purchaseReturnAmount", retAmount);
        kpi.put("netPurchase", amount.subtract(retAmount));
        kpi.put("purchaseOrderCount", cnt);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("range", range);
        res.put("kpi", kpi);
        res.put("series", purchaseSeries(dm, s, e));
        // 两个饼图（2026-09-15 用户要求）：直接采购成品 / 委外加工成品入库，放在「采购单据明细（下钻）」上方
        fillPurchasePies(res, s, e);
        res.put("details", purchaseDetails(s, e));
        return res;
    }

    /**
     * 进货分析的两个饼图（用户口径：**两张分开做**，各按**产品**分片，每卡一个「金额 / 件数」switch）：
     * <ul>
     *   <li>① **直接采购成品**（**净额**口径）= Σ采购单明细 − Σ采购退货明细，按产品；
     *       归期与采购聚合一致（**统一按建单日** {@code create_time}；F7-42 纠偏）。</li>
     *   <li>② **委外加工成品入库** = Σ(交货数量 × 加工单价)，按**成品产品**分片；归期 = **建单日**
     *       （2026-09-15 全站统一，原为交货日期）；退不良数量为负 → 自动冲减（与生成的应付同口径）。</li>
     * </ul>
     * 金额与件数**两套一次返回** → 前端切换不重发请求。
     * ⚠️ 净额为负（退货 &gt; 采购）的产品**不出分片**，但合计仍按净额算（可能小于分片合计），
     * 故不要用「分片合计 = 合计」做断言。
     */
    private void fillPurchasePies(Map<String, Object> res, LocalDate s, LocalDate e) {
        String from = s.toString(), to = e.toString();

        // ① 直接采购成品（净额 = 采购明细 − 采购退货明细）
        Map<Long, BigDecimal> purAmt = new HashMap<>(), purQty = new HashMap<>();
        Map<Long, String> purName = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.purchaseItemByProduct()) {
            if (!inRange(txt(r.get("d")), from, to)) continue;
            // F7-43#4（2026-09-19）：product_id 为空的行**归入「其它」分片**，不再静默丢弃 ——
            // 原实现直接 continue，这些行既不出分片也不进合计（合计与分片同源 ⇒ 与业务量合计不等）。
            Long pid = r.get("product_id") == null ? OTHER_PID : toBd(r.get("product_id")).longValue();
            if (OTHER_PID.equals(pid)) purName.putIfAbsent(pid, OTHER_NAME);
            else purName.put(pid, txt(r.get("product_name")));
            purAmt.merge(pid, toBd(r.get("amt")), BigDecimal::add);
            purQty.merge(pid, toBd(r.get("qty")), BigDecimal::add);
        }
        for (Map<String, Object> r : analysisMapper.purchaseReturnItemByProduct()) {
            if (!inRange(txt(r.get("d")), from, to)) continue;
            Long pid = r.get("product_id") == null ? OTHER_PID : toBd(r.get("product_id")).longValue();
            if (OTHER_PID.equals(pid)) purName.putIfAbsent(pid, OTHER_NAME);
            else purName.putIfAbsent(pid, txt(r.get("product_name")));
            purAmt.merge(pid, toBd(r.get("amt")).negate(), BigDecimal::add);
            purQty.merge(pid, toBd(r.get("qty")).negate(), BigDecimal::add);
        }

        // ② 委外加工成品入库（Σ 数量 × 加工单价；退不良数量为负 → 自动冲减）
        Map<Long, BigDecimal> outAmt = new HashMap<>(), outQty = new HashMap<>();
        Map<Long, String> outName = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.outsourceInByProduct()) {
            if (!inRange(txt(r.get("d")), from, to)) continue;
            // F7-43#4：product_master_id 为空同样归入「其它」分片（理由同直接采购侧）
            Long pid = r.get("product_master_id") == null ? OTHER_PID : toBd(r.get("product_master_id")).longValue();
            if (OTHER_PID.equals(pid)) outName.putIfAbsent(pid, OTHER_NAME);
            else outName.put(pid, txt(r.get("product_name")));
            outAmt.merge(pid, toBd(r.get("amt")), BigDecimal::add);
            outQty.merge(pid, toBd(r.get("qty")), BigDecimal::add);
        }

        res.put("directPurchaseByProduct", toProductPie(purAmt, purName));        // 直接采购成品 饼图（金额口径）
        res.put("directPurchaseByProductQty", toProductPie(purQty, purName));     // 直接采购成品 饼图（件数口径）
        res.put("outsourceInByProduct", toProductPie(outAmt, outName));           // 委外加工成品入库 饼图（金额口径）
        res.put("outsourceInByProductQty", toProductPie(outQty, outName));        // 委外加工成品入库 饼图（件数口径）
        Map<String, Object> pieTotals = new LinkedHashMap<>();
        pieTotals.put("directPurchaseAmount", sumAll(purAmt));                    // 净额合计（含冲减）
        pieTotals.put("directPurchaseQuantity", sumAll(purQty));
        pieTotals.put("outsourceInAmount", sumAll(outAmt));
        pieTotals.put("outsourceInQuantity", sumAll(outQty));
        res.put("pieTotals", pieTotals);
    }

    /** 日期是否落在区间内（空串视为不在区间） */
    private boolean inRange(String d, String from, String to) {
        return d != null && !d.isEmpty() && d.compareTo(from) >= 0 && d.compareTo(to) <= 0;
    }

    /** 累加 map 中所有金额（含负数） */
    private BigDecimal sumAll(Map<Long, BigDecimal> m) {
        BigDecimal t = ZERO;
        for (BigDecimal v : m.values()) t = t.add(v == null ? ZERO : v);
        return t;
    }

    /**
     * 产品维度 map → 饼图分片 `[{name, value}]`（按 value 倒序）。
     * **值 ≤ 0 的产品不产出分片**（饼图不能有负片；净额为负 = 退货大于采购，其影响已体现在合计里）。
     */
    private List<Map<String, Object>> toProductPie(Map<Long, BigDecimal> valueByKey, Map<Long, String> nameByKey) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> en : valueByKey.entrySet()) {
            BigDecimal v = en.getValue();
            if (v == null || v.signum() <= 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", nameByKey.getOrDefault(en.getKey(), "（未知）"));
            m.put("value", v);
            list.add(m);
        }
        list.sort((a, b) -> ((BigDecimal) b.get("value")).compareTo((BigDecimal) a.get("value")));
        return list;
    }

    /** 进货趋势序列：区间 ≤ 62 天按天、否则按月（与首页/经营概览同一粒度规则） */
    private Map<String, Object> purchaseSeries(Map<String, Map<String, BigDecimal>> dm, LocalDate s, LocalDate e) {
        boolean byMonth = ChronoUnit.DAYS.between(s, e) + 1 > SERIES_DAY_MAX;
        List<Map<String, Object>> points = new ArrayList<>();
        Map<String, BigDecimal> pur = dm.get("pur"), purRet = dm.get("purRet");
        if (!byMonth) {
            for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
                String k = d.toString();
                points.add(purchasePoint(k, pur.getOrDefault(k, ZERO), purRet.getOrDefault(k, ZERO)));
            }
        } else {
            String cur = null;
            BigDecimal a = ZERO, r = ZERO;
            for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
                String ym = d.toString().substring(0, 7);
                if (cur != null && !ym.equals(cur)) {
                    points.add(purchasePoint(cur, a, r));
                    a = ZERO; r = ZERO;
                }
                cur = ym;
                String k = d.toString();
                a = a.add(pur.getOrDefault(k, ZERO));
                r = r.add(purRet.getOrDefault(k, ZERO));
            }
            if (cur != null) points.add(purchasePoint(cur, a, r));
        }
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("granularity", byMonth ? "month" : "day");
        series.put("start", s.toString());
        series.put("end", e.toString());
        series.put("points", points);
        return series;
    }

    /** 按月/按天的进货趋势点 */
    private Map<String, Object> purchasePoint(String label, BigDecimal amount, BigDecimal retAmount) {
        Map<String, Object> pt = new LinkedHashMap<>();
        pt.put("label", label);
        pt.put("purchaseAmount", amount);
        pt.put("purchaseReturnAmount", retAmount);
        pt.put("netPurchase", amount.subtract(retAmount));
        return pt;
    }

    /**
     * 进货单据明细（下钻用）：区间内的已审核采购单 + 采购退货单，按日期倒序。
     * 归期与聚合口径一致（**统一按建单日** {@code create_time}；F7-42 纠偏）；金额一律取正数，由前端按类型展示与冲减。
     */
    private List<Map<String, Object>> purchaseDetails(LocalDate s, LocalDate e) {
        String from = s.toString(), to = e.toString();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : analysisMapper.purchaseOrderRecords()) {
            String d = txt(r.get("d"));
            if (d.isEmpty() || d.compareTo(from) < 0 || d.compareTo(to) > 0) continue;
            list.add(purchaseDetailRow("PURCHASE", r, d));
        }
        for (Map<String, Object> r : analysisMapper.purchaseReturnRecords()) {
            String d = txt(r.get("d"));
            if (d.isEmpty() || d.compareTo(from) < 0 || d.compareTo(to) > 0) continue;
            list.add(purchaseDetailRow("PURCHASE_RETURN", r, d));
        }
        list.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("date"))).reversed()
                .thenComparing(m -> String.valueOf(m.get("code"))));
        return list;
    }

    /** 组装一行进货明细 */
    private Map<String, Object> purchaseDetailRow(String billType, Map<String, Object> r, String date) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("billType", billType);
        m.put("billId", r.get("id"));
        m.put("code", txt(r.get("code")));
        m.put("date", date);
        m.put("supplier", txt(r.get("partner")));
        m.put("amount", toBd(r.get("total_amount")));
        m.put("remark", txt(r.get("remark")));
        return m;
    }

    /** 空安全的 toString（金额/文本字段取值） */
    private String txt(Object v) { return v == null ? "" : v.toString(); }

    @Override
    public Map<String, Object> profit(int months, LocalDate start, LocalDate end) {
        List<String> ms = (start != null && end != null) ? monthRange(start, end) : monthList(months);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("months", ms);
        res.put("rows", profitRows(ms, loadProfitMaps()));
        return res;
    }

    /**
     * F7-43#1（2026-09-19）：利润表 6 张聚合的**一次性取数**。
     *
     * <p>抽出的原因：{@code summary()} 原先对**完全相同**的数据跑 3 遍全表聚合
     * （{@code profit(2)} + 显式 6 条 + {@code profit(6)}）—— 因为所有 {@code xxxByMonth/ByDay}
     * 都是全表 GROUP BY、不带日期参数（见 {@code FinanceAnalysisMapper} 注释），三遍结果逐字相同。</p>
     */
    private Map<String, Map<String, BigDecimal>> loadProfitMaps() {
        Map<String, Map<String, BigDecimal>> dm = new LinkedHashMap<>();
        dm.put("sale", toAmtMap(analysisMapper.saleByMonth()));
        dm.put("saleRet", toAmtMap(analysisMapper.saleReturnByMonth()));
        dm.put("exp", toAmtMap(analysisMapper.expenseByMonth()));
        // 折损收款：退货环节向客户收取的补偿，并入营业收入
        dm.put("loss", toAmtMap(analysisMapper.saleReturnLossByMonth()));
        // 成本（口径 B）：按天聚合结果汇总到月，保证按月与按天两条链路完全一致
        dm.put("saleCost", dayToMonthMap(analysisMapper.saleCostByDay()));
        dm.put("retCost", dayToMonthMap(analysisMapper.saleReturnCostByDay()));
        return dm;
    }

    /** 由已取好的 6 张聚合生成利润表行（逐月公式与重构前的 {@code profit()} **逐字一致**，纯提取） */
    private List<Map<String, Object>> profitRows(List<String> ms, Map<String, Map<String, BigDecimal>> dm) {
        Map<String, BigDecimal> sale = dm.get("sale"), saleRet = dm.get("saleRet"), exp = dm.get("exp"),
                loss = dm.get("loss"), saleCost = dm.get("saleCost"), retCost = dm.get("retCost");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String ym : ms) {
            BigDecimal lossAmt = loss.getOrDefault(ym, ZERO);
            BigDecimal revenue = sale.getOrDefault(ym, ZERO).subtract(saleRet.getOrDefault(ym, ZERO)).add(lossAmt);
            BigDecimal cost = saleCost.getOrDefault(ym, ZERO).subtract(retCost.getOrDefault(ym, ZERO));
            BigDecimal gross = revenue.subtract(cost);
            BigDecimal expense = exp.getOrDefault(ym, ZERO);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", ym);
            row.put("revenue", revenue);
            row.put("loss", lossAmt);
            row.put("cost", cost);
            row.put("grossProfit", gross);
            row.put("grossRate", rate(gross, revenue));
            row.put("expense", expense);
            row.put("netProfit", gross.subtract(expense));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public Map<String, Object> summary() {
        // F7-43#1（2026-09-19）：6 张全表聚合**只取一次**，供 cur/prev、YTD、近 6 月趋势共用 ——
        // 原实现跑 3 遍完全相同的数据（profit(2) + 下面显式 6 条 + profit(6)），单次请求多打 12 条聚合 SQL。
        Map<String, Map<String, BigDecimal>> dm = loadProfitMaps();
        List<String> ms = monthList(2);
        List<Map<String, Object>> rows = profitRows(ms, dm);
        Map<String, Object> cur = !rows.isEmpty() ? rows.get(rows.size() - 1) : new HashMap<>();
        Map<String, Object> prev = rows.size() > 1 ? rows.get(0) : new HashMap<>();
        Map<String, BigDecimal> cashNet = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.cashflowByMonth()) {
            Object ym = r.get("ym");
            if (ym != null) cashNet.put(ym.toString(), toBd(r.get("income")).subtract(toBd(r.get("expense"))));
        }

        // 本年累计（YTD）：按月聚合结果中累加本年（yyyy-*）月份
        String yearPrefix = LocalDate.now().getYear() + "-";
        BigDecimal ytdRevenue = ZERO, ytdCost = ZERO, ytdExpense = ZERO;
        Map<String, BigDecimal> sale = dm.get("sale");
        Map<String, BigDecimal> saleRet = dm.get("saleRet");
        Map<String, BigDecimal> exp = dm.get("exp");
        Map<String, BigDecimal> loss = dm.get("loss");
        // 成本（口径 B，与利润表同源）
        Map<String, BigDecimal> saleCost = dm.get("saleCost");
        Map<String, BigDecimal> retCost = dm.get("retCost");
        Set<String> allYm = new HashSet<>();
        allYm.addAll(sale.keySet()); allYm.addAll(saleCost.keySet()); allYm.addAll(exp.keySet()); allYm.addAll(loss.keySet());
        for (String ym : allYm) {
            if (!ym.startsWith(yearPrefix)) continue;
            ytdRevenue = ytdRevenue.add(sale.getOrDefault(ym, ZERO)).subtract(saleRet.getOrDefault(ym, ZERO)).add(loss.getOrDefault(ym, ZERO));
            ytdCost = ytdCost.add(saleCost.getOrDefault(ym, ZERO)).subtract(retCost.getOrDefault(ym, ZERO));
            ytdExpense = ytdExpense.add(exp.getOrDefault(ym, ZERO));
        }
        BigDecimal ytdGross = ytdRevenue.subtract(ytdCost);
        Map<String, Object> ytd = new LinkedHashMap<>();
        ytd.put("revenue", ytdRevenue);
        ytd.put("cost", ytdCost);
        ytd.put("grossProfit", ytdGross);
        ytd.put("expense", ytdExpense);
        ytd.put("netProfit", ytdGross.subtract(ytdExpense));

        // 资金与往来健康度
        BigDecimal cashTotal = ZERO;
        for (Map<String, Object> a : analysisMapper.accountDistribution()) cashTotal = cashTotal.add(toBd(a.get("balance")));
        BigDecimal recOverdue = ZERO;
        for (Map<String, Object> b : analysisMapper.receivableAging()) {
            String bucket = String.valueOf(b.get("bucket"));
            if ("d30".equals(bucket) || "d60".equals(bucket) || "d60p".equals(bucket)) recOverdue = recOverdue.add(toBd(b.get("amt")));
        }
        Map<String, Object> recSum = analysisMapper.receivableSummary();
        Map<String, Object> paySum = analysisMapper.payableSummary();
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("cashTotal", cashTotal);
        health.put("receivableUnpaid", toBd(recSum.get("unpaid")));
        health.put("receivableOverdue", recOverdue);
        health.put("payableUnpaid", toBd(paySum.get("unpaid")));

        // 近 6 月经营趋势（销售额 / 净利润 / 净现金流）—— F7-43#1：复用同一批 dm，不再重跑 6 条聚合
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map<String, Object> row : profitRows(monthList(6), dm)) {
            String ym = String.valueOf(row.get("month"));
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("month", ym);
            t.put("revenue", row.get("revenue"));
            t.put("netProfit", row.get("netProfit"));
            t.put("cashNet", cashNet.getOrDefault(ym, ZERO));
            trend.add(t);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("cur", cur);
        res.put("prev", prev);
        res.put("curCashNet", cashNet.getOrDefault(ms.get(1), ZERO));
        res.put("prevCashNet", cashNet.getOrDefault(ms.get(0), ZERO));
        res.put("ytd", ytd);
        res.put("health", health);
        res.put("trend", trend);
        return res;
    }

    /**
     * 区间解析（2026-09-15）：start/end 自定义优先 → preset 快捷预设 → 都没有返回 null（调用方走 months 旧逻辑）。
     * start &gt; end 自动交换；与 profitDetail / overviewKpi 同一套规则。
     */
    private LocalDate[] resolveRange(String preset, LocalDate start, LocalDate end) {
        if (start != null && end != null) {
            LocalDate s = start, e = end;
            if (s.isAfter(e)) { LocalDate t = s; s = e; e = t; }
            return new LocalDate[]{s, e};
        }
        if (preset != null && !preset.isBlank()) return resolvePresetRange(preset);
        return null;
    }

    /** 往趋势序列追加一个点（label + 收入 / 支出 / 净额） */
    private void pushPoint(List<String> labels, List<BigDecimal> in, List<BigDecimal> out, List<BigDecimal> net,
                           String label, BigDecimal income, BigDecimal expense) {
        labels.add(label);
        in.add(income);
        out.add(expense);
        net.add(income.subtract(expense));
    }

    /**
     * 资金趋势（2026-09-15 起支持「统计区间」，供经营分析 → 资金与往来使用）。
     * 区间模式粒度：≤62 天按天、否则按月（与首页趋势图同一规则，常量 {@link #SERIES_DAY_MAX}）；
     * 若既无 start/end 也无 preset → 退回"近 N 月按月"（兼容旧调用）。
     */
    @Override
    public Map<String, Object> cashTrend(String preset, int months, LocalDate start, LocalDate end) {
        LocalDate[] r = resolveRange(preset, start, end);
        List<String> labels = new ArrayList<>();
        List<BigDecimal> in = new ArrayList<>(), out = new ArrayList<>(), net = new ArrayList<>();
        String granularity;
        boolean truncated = false;      // F7-43#3：区间被截断时回带提示
        if (r == null) {
            granularity = "month";
            Map<String, BigDecimal> income = new HashMap<>(), expense = new HashMap<>();
            for (Map<String, Object> x : analysisMapper.cashflowByMonth()) {
                Object ym = x.get("ym");
                if (ym == null) continue;
                income.put(ym.toString(), toBd(x.get("income")));
                expense.put(ym.toString(), toBd(x.get("expense")));
            }
            for (String ym : monthList(months)) {
                pushPoint(labels, in, out, net, ym, income.getOrDefault(ym, ZERO), expense.getOrDefault(ym, ZERO));
            }
        } else {
            LocalDate s = r[0], e = r[1];
            // 防滥用：按天展开，最长 MAX_RANGE_DAYS 天
            // F7-43#3（2026-09-19）：原为 200 天（与其它接口的 400 天不一致）且**静默**截断 ⇒ 统一阈值 + 回带提示
            truncated = s.plusDays(MAX_RANGE_DAYS).isBefore(e);
            if (truncated) s = e.minusDays(MAX_RANGE_DAYS - 1);
            Map<String, BigDecimal> income = new HashMap<>(), expense = new HashMap<>();
            for (Map<String, Object> x : analysisMapper.cashflowByDay()) {
                Object d = x.get("d");
                if (d == null) continue;
                income.put(d.toString(), toBd(x.get("income")));
                expense.put(d.toString(), toBd(x.get("expense")));
            }
            boolean byMonth = ChronoUnit.DAYS.between(s, e) + 1 > SERIES_DAY_MAX;
            granularity = byMonth ? "month" : "day";
            if (byMonth) {
                // 日 → 月 汇总：始终基于同一份按天数据，避免与按天口径分叉
                Map<String, BigDecimal> mi = new LinkedHashMap<>(), me = new LinkedHashMap<>();
                for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
                    String ym = d.toString().substring(0, 7);
                    mi.merge(ym, income.getOrDefault(d.toString(), ZERO), BigDecimal::add);
                    me.merge(ym, expense.getOrDefault(d.toString(), ZERO), BigDecimal::add);
                }
                for (Map.Entry<String, BigDecimal> en : mi.entrySet()) {
                    pushPoint(labels, in, out, net, en.getKey(), en.getValue(), me.getOrDefault(en.getKey(), ZERO));
                }
            } else {
                for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
                    String k = d.toString();
                    pushPoint(labels, in, out, net, k, income.getOrDefault(k, ZERO), expense.getOrDefault(k, ZERO));
                }
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("labels", labels);
        res.put("months", labels);          // 兼容旧字段名（历史调用方按 months 读标签）
        res.put("granularity", granularity);
        res.put("income", in);
        res.put("expense", out);
        res.put("net", net);
        res.put("truncated", truncated);            // F7-43#3：区间被截断时前端需提示用户
        res.put("maxDays", MAX_RANGE_DAYS);
        res.put("accounts", analysisMapper.accountDistribution());
        return res;
    }

    @Override
    public Map<String, Object> aging() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("receivable", agingSide(analysisMapper.receivableAging(), analysisMapper.receivableSummary()));
        res.put("payable", agingSide(analysisMapper.payableAging(), analysisMapper.payableSummary()));
        res.put("topCustomers", analysisMapper.topCustomers());
        res.put("topSuppliers", analysisMapper.topSuppliers());
        return res;
    }

    @Override
    public Map<String, Object> tax(String preset, int months, LocalDate start, LocalDate end) {
        // 税务天然按月统计：区间只决定"取哪些月"（自定义区间按整月计入），故与前端口径一致
        LocalDate[] rg = resolveRange(preset, start, end);
        List<String> ms = rg != null ? monthRange(rg[0], rg[1]) : monthList(months);
        // 月 × 业务 × 税状态 → 金额/税额（key: ym|biz|taxed）
        Map<String, BigDecimal> amt = new HashMap<>();
        Map<String, BigDecimal> tax = new HashMap<>();
        collectTax(analysisMapper.saleTaxByMonth(), "sale", amt, tax);
        // 销售退货冲减销项（取负）：含税标记与税负比例跟随原销售单（P2-26 口径 A）
        collectTaxNegated(analysisMapper.saleReturnTaxByMonth(), "sale", amt, tax);
        collectTax(analysisMapper.purchaseTaxByMonth(), "purchase", amt, tax);
        // 采购退货冲减进项（取负）：同上，跟随原采购单
        collectTaxNegated(analysisMapper.purchaseReturnTaxByMonth(), "purchase", amt, tax);
        collectTax(analysisMapper.outsourceTaxByMonth(), "outsource", amt, tax);

        // 区间合计（汇总卡用）
        BigDecimal sT = ZERO, sTT = ZERO, sU = ZERO, pT = ZERO, pTT = ZERO, pU = ZERO, oT = ZERO, oTT = ZERO, oU = ZERO;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String ym : ms) {
            BigDecimal saleT = amt.getOrDefault(key(ym, "sale", true), ZERO);
            BigDecimal saleU = amt.getOrDefault(key(ym, "sale", false), ZERO);
            BigDecimal purT = amt.getOrDefault(key(ym, "purchase", true), ZERO);
            BigDecimal purU = amt.getOrDefault(key(ym, "purchase", false), ZERO);
            BigDecimal outT = amt.getOrDefault(key(ym, "outsource", true), ZERO);
            BigDecimal outU = amt.getOrDefault(key(ym, "outsource", false), ZERO);
            sT = sT.add(saleT); sTT = sTT.add(tax.getOrDefault(key(ym, "sale", true), ZERO)); sU = sU.add(saleU);
            pT = pT.add(purT); pTT = pTT.add(tax.getOrDefault(key(ym, "purchase", true), ZERO)); pU = pU.add(purU);
            oT = oT.add(outT); oTT = oTT.add(tax.getOrDefault(key(ym, "outsource", true), ZERO)); oU = oU.add(outU);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", ym);
            row.put("saleTaxed", saleT);
            row.put("saleTaxedTax", tax.getOrDefault(key(ym, "sale", true), ZERO));
            row.put("saleUntaxed", saleU);
            row.put("saleRate", rate(saleT, saleT.add(saleU)));
            row.put("purchaseTaxed", purT);
            row.put("purchaseTaxedTax", tax.getOrDefault(key(ym, "purchase", true), ZERO));
            row.put("purchaseUntaxed", purU);
            row.put("purchaseRate", rate(purT, purT.add(purU)));
            row.put("outsourceTaxed", outT);
            row.put("outsourceTaxedTax", tax.getOrDefault(key(ym, "outsource", true), ZERO));
            row.put("outsourceUntaxed", outU);
            row.put("outsourceRate", rate(outT, outT.add(outU)));
            rows.add(row);
        }

        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("saleTaxed", sT); sum.put("saleTaxedTax", sTT); sum.put("saleUntaxed", sU); sum.put("saleRate", rate(sT, sT.add(sU)));
        sum.put("purchaseTaxed", pT); sum.put("purchaseTaxedTax", pTT); sum.put("purchaseUntaxed", pU); sum.put("purchaseRate", rate(pT, pT.add(pU)));
        sum.put("outsourceTaxed", oT); sum.put("outsourceTaxedTax", oTT); sum.put("outsourceUntaxed", oU); sum.put("outsourceRate", rate(oT, oT.add(oU)));

        // 发票汇总（税务口径，与区间无关：全部已登记发票按方向合计）
        BigDecimal invSaleTax = ZERO, invSaleAmt = ZERO, invPurchaseTax = ZERO, invPurchaseAmt = ZERO;
        long invSaleCnt = 0, invPurchaseCnt = 0;
        for (Map<String, Object> r : analysisMapper.invoiceSummary()) {
            String d = String.valueOf(r.get("direction"));
            if ("SALE".equals(d)) { invSaleAmt = toBd(r.get("amt")); invSaleTax = toBd(r.get("tax")); invSaleCnt = ((Number) r.get("cnt")).longValue(); }
            else if ("PURCHASE".equals(d)) { invPurchaseAmt = toBd(r.get("amt")); invPurchaseTax = toBd(r.get("tax")); invPurchaseCnt = ((Number) r.get("cnt")).longValue(); }
        }
        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("saleAmount", invSaleAmt); inv.put("saleTax", invSaleTax); inv.put("saleCount", invSaleCnt);
        inv.put("purchaseAmount", invPurchaseAmt); inv.put("purchaseTax", invPurchaseTax); inv.put("purchaseCount", invPurchaseCnt);
        inv.put("payable", invSaleTax.subtract(invPurchaseTax)); // 应纳增值税 = 销项税额 − 进项税额

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("months", ms);
        res.put("rows", rows);
        res.put("summary", sum);
        res.put("invoice", inv);
        return res;
    }

    /** "月|业务|税状态" 组合键 */
    private String key(String ym, String biz, boolean taxed) {
        return ym + "|" + biz + "|" + taxed;
    }

    /** 聚合结果 List<Map{ym,tax_included,amt,tax}> 摊平到组合键 */
    private void collectTax(List<Map<String, Object>> list, String biz, Map<String, BigDecimal> amt, Map<String, BigDecimal> tax) {
        if (list == null) return;
        for (Map<String, Object> r : list) {
            Object ym = r.get("ym");
            if (ym == null) continue;
            boolean taxed = r.get("tax_included") != null && ((Number) r.get("tax_included")).intValue() == 1;
            String k = key(ym.toString(), biz, taxed);
            amt.merge(k, toBd(r.get("amt")), BigDecimal::add);
            tax.merge(k, toBd(r.get("tax")), BigDecimal::add);
        }
    }

    /**
     * 同 {@link #collectTax}，但金额与税额取负 —— 用于退货冲减销项/进项（P2-26 口径 A）。
     * 退货单的含税标记与税负比例已在 SQL 内跟随原单，这里只需统一取负后合并。
     */
    private void collectTaxNegated(List<Map<String, Object>> list, String biz, Map<String, BigDecimal> amt, Map<String, BigDecimal> tax) {
        if (list == null) return;
        for (Map<String, Object> r : list) {
            Object ym = r.get("ym");
            if (ym == null) continue;
            boolean taxed = r.get("tax_included") != null && ((Number) r.get("tax_included")).intValue() == 1;
            String k = key(ym.toString(), biz, taxed);
            amt.merge(k, toBd(r.get("amt")).negate(), BigDecimal::add);
            tax.merge(k, toBd(r.get("tax")).negate(), BigDecimal::add);
        }
    }

    /** 账龄分桶结果汇总（paid/total 即回款率或付款率） */
    private Map<String, Object> agingSide(List<Map<String, Object>> buckets, Map<String, Object> summary) {
        Map<String, Object> m = new LinkedHashMap<>();
        BigDecimal notDue = ZERO, d30 = ZERO, d60 = ZERO, d60p = ZERO, none = ZERO;
        for (Map<String, Object> b : buckets) {
            String bucket = String.valueOf(b.get("bucket"));
            switch (bucket) {
                case "not_due" -> notDue = toBd(b.get("amt"));
                case "d30" -> d30 = toBd(b.get("amt"));
                case "d60" -> d60 = toBd(b.get("amt"));
                case "d60p" -> d60p = toBd(b.get("amt"));
                default -> none = toBd(b.get("amt"));
            }
        }
        m.put("notDue", notDue);
        m.put("d30", d30);
        m.put("d60", d60);
        m.put("d60p", d60p);
        m.put("none", none);
        BigDecimal total = toBd(summary.get("total"));
        BigDecimal paid = toBd(summary.get("paid"));
        BigDecimal unpaid = toBd(summary.get("unpaid"));
        m.put("total", total);
        m.put("paid", paid);
        m.put("unpaid", unpaid);
        m.put("settledRate", rate(paid, total));
        return m;
    }

    /** 未付金额（NULL 安全） */
    private BigDecimal unpaidOf(FinancePayable p) {
        return p.getUnpaidAmount() != null ? p.getUnpaidAmount() : ZERO;
    }

    @Override
    public Map<String, Object> subject() {
        Map<String, Object> res = new LinkedHashMap<>();

        // 1) 供应商应付（未付）按主体类型分组；已转应收的负数冲减项剔除，避免与应收重复统计
        // F7-40（2026-09-19）：口径与账龄/应付汇总统一 —— 只计未结清（UNSETTLED/PARTIAL），
        // 预付台账（ADVANCE，负数应付）不计入"未付"（原实现含它，实测 other 类型出现 -2,090）。
        List<FinancePayable> payables = payableMapper.selectList(new LambdaQueryWrapper<FinancePayable>()
                .in(FinancePayable::getStatus, SettlementStatus.UNSETTLED.getCode(),
                        SettlementStatus.PARTIAL.getCode())
                .ne(FinancePayable::getTransferredToReceivable, 1));
        Map<String, BigDecimal> payableByType = new LinkedHashMap<>();
        for (String t : new String[]{"product", "factory", "material", "solution", "other"}) payableByType.put(t, ZERO);
        for (FinancePayable p : payables) {
            String t = p.getSupplierType() != null && payableByType.containsKey(p.getSupplierType())
                    ? p.getSupplierType() : "other";
            payableByType.merge(t, unpaidOf(p), BigDecimal::add);
        }
        res.put("payableByType", payableByType);

        // 2) 供应商应收未收（应付转应收单生成，尚未被收款核销的金额）
        // F7-40（同上）：只计未结清（UNSETTLED/PARTIAL），预收台账（ADVANCE，负数应收）不计入"未收"
        List<FinanceReceivable> recv = receivableMapper.selectList(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getSubjectType, "SUPPLIER")
                .in(FinanceReceivable::getStatus, SettlementStatus.UNSETTLED.getCode(),
                        SettlementStatus.PARTIAL.getCode()));
        BigDecimal supplierRecvUnpaid = recv.stream()
                .map(r -> r.getUnpaidAmount() != null ? r.getUnpaidAmount() : ZERO)
                .reduce(ZERO, BigDecimal::add);
        res.put("supplierReceivableUnpaid", supplierRecvUnpaid);

        // 3) 报损损失：已审核报损单金额（成品 + 委外物料）
        BigDecimal lossTotal = ZERO;
        for (InventoryStockLoss l : inventoryLossMapper.selectList(new LambdaQueryWrapper<InventoryStockLoss>()
                .eq(InventoryStockLoss::getStatus, "AUDITED"))) {
            lossTotal = lossTotal.add(l.getTotalAmount() != null ? l.getTotalAmount() : ZERO);
        }
        for (OutsourceStockLoss l : outsourceLossMapper.selectList(new LambdaQueryWrapper<OutsourceStockLoss>()
                .eq(OutsourceStockLoss::getStatus, "AUDITED"))) {
            lossTotal = lossTotal.add(l.getTotalAmount() != null ? l.getTotalAmount() : ZERO);
        }
        res.put("lossTotal", lossTotal);

        return res;
    }
}
