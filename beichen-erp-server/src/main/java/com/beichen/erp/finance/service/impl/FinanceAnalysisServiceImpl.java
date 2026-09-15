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
import java.util.*;

/**
 * 财务分析 Service 实现：纯查询聚合，不改任何业务数据。
 * <p>口径（2026-09-11 起，P2-27 定稿为"销售成本"口径）：
 * 收入 = 已审核销售单(audit_time/create_time 归月) − 销售退货 + 退货折损收款；
 * 成本 = Σ(销售明细数量 − 退货明细数量) × 产品当前移动加权成本价 {@code product.cost_price}
 * （与客户分析/销售分析同源，保证跨页面"毛利"可对账）；
 * 费用 = 已审核费用单(expense_date 归月)；净利润 = 毛利 − 费用；现金流 = 资金流水按月收 − 支。
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

    /** 近 months 个月（含本月，升序） */
    private List<String> monthList(int months) {
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

    @Override
    public Map<String, Object> profitDetail(String preset, String start, String end) {
        // 区间：自定义 start/end 优先，否则按快捷预设解析；start > end 自动交换
        LocalDate s, e;
        if (start != null && !start.isBlank() && end != null && !end.isBlank()) {
            s = LocalDate.parse(start); e = LocalDate.parse(end);
            if (s.isAfter(e)) { LocalDate t = s; s = e; e = t; }
        } else {
            LocalDate[] r = resolvePresetRange(preset);
            s = r[0]; e = r[1];
        }
        // 防滥用：明细按天展开，最长 400 天
        if (s.plusDays(400).isBefore(e)) s = e.minusDays(399);

        // 按天聚合（口径与按月版一致）：全量按天分组，Java 侧按区间取值
        Map<String, BigDecimal> sale = toDayMap(analysisMapper.saleByDay());
        Map<String, BigDecimal> saleRet = toDayMap(analysisMapper.saleReturnByDay());
        Map<String, BigDecimal> loss = toDayMap(analysisMapper.saleReturnLossByDay());
        // 成本（口径 B）= 销售出库成本 − 退货冲回成本 = 净销售数量 × 产品当前移动加权成本价
        Map<String, BigDecimal> saleCost = toDayMap(analysisMapper.saleCostByDay());
        Map<String, BigDecimal> retCost = toDayMap(analysisMapper.saleReturnCostByDay());
        Map<String, BigDecimal> exp = toDayMap(analysisMapper.expenseByDay());

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal sumRevenue = ZERO, sumCost = ZERO, sumExpense = ZERO;
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
            String key = d.toString();
            BigDecimal revenue = sale.getOrDefault(key, ZERO).subtract(saleRet.getOrDefault(key, ZERO)).add(loss.getOrDefault(key, ZERO));
            BigDecimal cost = saleCost.getOrDefault(key, ZERO).subtract(retCost.getOrDefault(key, ZERO));
            BigDecimal expense = exp.getOrDefault(key, ZERO);
            BigDecimal profit = revenue.subtract(cost).subtract(expense);
            sumRevenue = sumRevenue.add(revenue);
            sumCost = sumCost.add(cost);
            sumExpense = sumExpense.add(expense);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", key);
            row.put("revenue", revenue);
            row.put("cost", cost);
            row.put("expense", expense);
            // 支出合并口径（前端列表只展示收入/支出/利润三列）：销售成本 + 运营费用
            row.put("expenseTotal", cost.add(expense));
            row.put("profit", profit);
            row.put("grossRate", rate(revenue.subtract(cost), revenue));
            rows.add(row);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("income", sumRevenue);
        summary.put("cost", sumCost);
        summary.put("expense", sumExpense);
        summary.put("expenseTotal", sumCost.add(sumExpense));
        summary.put("profit", sumRevenue.subtract(sumCost).subtract(sumExpense));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("start", s.toString());
        res.put("end", e.toString());
        res.put("preset", (start != null && !start.isBlank() && end != null && !end.isBlank()) ? "custom" : (preset == null ? "today" : preset));
        res.put("summary", summary);
        res.put("rows", rows);
        return res;
    }

    /**
     * 明细行的展示顺序：销售 → 折损收款 → 销售退货（收入组）→ 销售出库成本 → 退货冲回成本（成本组）→ 费用。
     * 成本组已按 P2-27 定稿口径改为「销售出库成本/退货冲回成本」，采购单不再进入利润表。
     */
    private static final Map<String, Integer> RECORD_ORDER = Map.of(
            "SALE", 0, "LOSS_INCOME", 1, "SALE_RETURN", 2,
            "SALE_COST", 3, "SALE_RETURN_COST", 4, "EXPENSE", 5);

    private Map<String, Object> rec(String bizType, String subType, String category, Object billId,
                                    String billNo, String partner, BigDecimal signedAmount, String remark) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bizType", bizType);
        // subType：细分类型 code（目前仅费用行使用，存 expense_type 的 code）；中文一律由前端按 code 映射
        m.put("subType", subType);
        m.put("category", category);
        // 单据主键：明细页的单号点击进入对应单据详情需要它
        m.put("billId", billId);
        m.put("billNo", billNo);
        m.put("partner", partner);
        m.put("amount", signedAmount);
        m.put("remark", remark);
        return m;
    }

    /**
     * 利润明细钻取：某一天的每一条单据记录（口径与 profitDetail 聚合完全一致）。
     * 收入 = 销售单 − 销售退货 + 折损收款；成本 = 销售单出库成本 − 退货冲回成本（口径 B）；费用 = 费用单。
     */
    @Override
    public Map<String, Object> profitDetailRecords(String date) {
        String key = LocalDate.parse(date).toString();

        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> r : analysisMapper.saleOrderRecords()) {
            if (!key.equals(r.get("d"))) continue;
            records.add(rec("SALE", null, "REVENUE", r.get("id"),
                    str(r.get("code")), str(r.get("partner")), toBd(r.get("total_amount")), str(r.get("remark"))));
        }
        // 折损收款：退货环节向客户收取的补偿，收入性质（与聚合口径同为 loss_amount > 0）
        for (Map<String, Object> r : analysisMapper.saleReturnRecords()) {
            if (!key.equals(r.get("d"))) continue;
            BigDecimal total = toBd(r.get("total_amount"));
            if (total.compareTo(ZERO) != 0) {
                records.add(rec("SALE_RETURN", null, "REVENUE", r.get("id"),
                        str(r.get("code")), str(r.get("partner")), total.negate(), str(r.get("remark"))));
            }
            BigDecimal loss = toBd(r.get("loss_amount"));
            if (loss.compareTo(ZERO) > 0) {
                records.add(rec("LOSS_INCOME", null, "REVENUE", r.get("id"),
                        str(r.get("code")), str(r.get("partner")), loss, str(r.get("remark"))));
            }
        }
        // 成本（口径 B）：销售单的出库成本（明细数量 × 产品当前移动加权成本价）；与收入行同单号，便于对照
        for (Map<String, Object> r : analysisMapper.saleCostRecords()) {
            if (!key.equals(r.get("d"))) continue;
            BigDecimal c = toBd(r.get("amt"));
            if (c.compareTo(ZERO) == 0) continue; // 产品未维护成本价 → 该单无成本行，避免占位空行
            records.add(rec("SALE_COST", null, "COST", r.get("id"),
                    str(r.get("code")), str(r.get("partner")), c, str(r.get("remark"))));
        }
        // 退货入库会把成本退回，故冲减成本（负数）
        for (Map<String, Object> r : analysisMapper.saleReturnCostRecords()) {
            if (!key.equals(r.get("d"))) continue;
            BigDecimal c = toBd(r.get("amt"));
            if (c.compareTo(ZERO) == 0) continue;
            records.add(rec("SALE_RETURN_COST", null, "COST", r.get("id"),
                    str(r.get("code")), str(r.get("partner")), c.negate(), str(r.get("remark"))));
        }
        for (Map<String, Object> r : analysisMapper.expenseRecords()) {
            if (!key.equals(r.get("d"))) continue;
            // subType 存费用类型 code（OFFICE/RENT/...），前端按 ExpenseTypeLabel 映射中文，避免后端硬编码文案
            records.add(rec("EXPENSE", str(r.get("expense_type")), "EXPENSE", r.get("id"),
                    str(r.get("code")), str(r.get("partner")), toBd(r.get("amount")), str(r.get("remark"))));
        }

        records.sort(Comparator
                .comparing((Map<String, Object> m) -> RECORD_ORDER.getOrDefault(String.valueOf(m.get("bizType")), 99))
                .thenComparing(m -> String.valueOf(m.get("billNo"))));

        // 区间小计（与 profitDetail 同日聚合值对账）
        BigDecimal income = ZERO, cost = ZERO, expense = ZERO;
        for (Map<String, Object> r : records) {
            BigDecimal a = toBd(r.get("amount"));
            switch (String.valueOf(r.get("category"))) {
                case "REVENUE" -> income = income.add(a);
                case "COST" -> cost = cost.add(a);
                case "EXPENSE" -> expense = expense.add(a);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("income", income);
        summary.put("cost", cost);
        summary.put("expense", expense);
        summary.put("expenseTotal", cost.add(expense));
        summary.put("profit", income.subtract(cost).subtract(expense));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("date", key);
        res.put("summary", summary);
        res.put("records", records);
        return res;
    }

    private String str(Object v) { return v == null ? "" : v.toString(); }

    @Override
    public Map<String, Object> profit(int months, LocalDate start, LocalDate end) {
        List<String> ms = (start != null && end != null) ? monthRange(start, end) : monthList(months);
        Map<String, BigDecimal> sale = toAmtMap(analysisMapper.saleByMonth());
        Map<String, BigDecimal> saleRet = toAmtMap(analysisMapper.saleReturnByMonth());
        Map<String, BigDecimal> exp = toAmtMap(analysisMapper.expenseByMonth());
        // 折损收款：退货环节向客户收取的补偿，并入营业收入
        Map<String, BigDecimal> loss = toAmtMap(analysisMapper.saleReturnLossByMonth());
        // 成本（口径 B）：按天聚合结果汇总到月，保证按月与按天两条链路完全一致
        Map<String, BigDecimal> saleCost = dayToMonthMap(analysisMapper.saleCostByDay());
        Map<String, BigDecimal> retCost = dayToMonthMap(analysisMapper.saleReturnCostByDay());
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
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("months", ms);
        res.put("rows", rows);
        return res;
    }

    @Override
    public Map<String, Object> summary() {
        Map<String, Object> p = profit(2, null, null);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) (List<?>) p.get("rows");
        Map<String, Object> cur = !rows.isEmpty() ? rows.get(rows.size() - 1) : new HashMap<>();
        Map<String, Object> prev = rows.size() > 1 ? rows.get(0) : new HashMap<>();
        Map<String, BigDecimal> cashNet = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.cashflowByMonth()) {
            Object ym = r.get("ym");
            if (ym != null) cashNet.put(ym.toString(), toBd(r.get("income")).subtract(toBd(r.get("expense"))));
        }
        List<String> ms = monthList(2);

        // 本年累计（YTD）：按月聚合结果中累加本年（yyyy-*）月份
        String yearPrefix = LocalDate.now().getYear() + "-";
        BigDecimal ytdRevenue = ZERO, ytdCost = ZERO, ytdExpense = ZERO;
        Map<String, BigDecimal> sale = toAmtMap(analysisMapper.saleByMonth());
        Map<String, BigDecimal> saleRet = toAmtMap(analysisMapper.saleReturnByMonth());
        Map<String, BigDecimal> exp = toAmtMap(analysisMapper.expenseByMonth());
        Map<String, BigDecimal> loss = toAmtMap(analysisMapper.saleReturnLossByMonth());
        // 成本（口径 B，与利润表同源）
        Map<String, BigDecimal> saleCost = dayToMonthMap(analysisMapper.saleCostByDay());
        Map<String, BigDecimal> retCost = dayToMonthMap(analysisMapper.saleReturnCostByDay());
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

        // 近 6 月经营趋势（销售额 / 净利润 / 净现金流）
        Map<String, Object> p6 = profit(6, null, null);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map<String, Object> row : (List<Map<String, Object>>) (List<?>) p6.get("rows")) {
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

    @Override
    public Map<String, Object> cashTrend(int months) {
        List<String> ms = monthList(months);
        Map<String, BigDecimal> income = new HashMap<>(), expense = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.cashflowByMonth()) {
            Object ym = r.get("ym");
            if (ym == null) continue;
            income.put(ym.toString(), toBd(r.get("income")));
            expense.put(ym.toString(), toBd(r.get("expense")));
        }
        List<BigDecimal> in = new ArrayList<>(), out = new ArrayList<>(), net = new ArrayList<>();
        for (String ym : ms) {
            BigDecimal i = income.getOrDefault(ym, ZERO), e = expense.getOrDefault(ym, ZERO);
            in.add(i); out.add(e); net.add(i.subtract(e));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("months", ms);
        res.put("income", in);
        res.put("expense", out);
        res.put("net", net);
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
    public Map<String, Object> tax(int months, LocalDate start, LocalDate end) {
        List<String> ms = (start != null && end != null) ? monthRange(start, end) : monthList(months);
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
        List<FinancePayable> payables = payableMapper.selectList(new LambdaQueryWrapper<FinancePayable>()
                .ne(FinancePayable::getStatus, SettlementStatus.CANCELLED.getCode())
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
        List<FinanceReceivable> recv = receivableMapper.selectList(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getSubjectType, "SUPPLIER")
                .ne(FinanceReceivable::getStatus, SettlementStatus.CANCELLED.getCode()));
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
