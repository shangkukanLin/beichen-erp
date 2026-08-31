package com.beichen.erp.finance.service.impl;

import com.beichen.erp.finance.mapper.FinanceAnalysisMapper;
import com.beichen.erp.finance.service.FinanceAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 财务分析 Service 实现：纯查询聚合，不改任何业务数据。
 * 口径：收入=已审核销售单(audit_time/create_time 归月)-销售退货；
 * 成本=已审核采购单-采购退货；费用=已审核费用单(expense_date 归月)；
 * 净利润=毛利-费用；现金流=资金流水按月收-支。
 */
@Service
@RequiredArgsConstructor
public class FinanceAnalysisServiceImpl implements FinanceAnalysisService {

    private final FinanceAnalysisMapper analysisMapper;

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

    @Override
    public Map<String, Object> profit(int months, LocalDate start, LocalDate end) {
        List<String> ms = (start != null && end != null) ? monthRange(start, end) : monthList(months);
        Map<String, BigDecimal> sale = toAmtMap(analysisMapper.saleByMonth());
        Map<String, BigDecimal> purchase = toAmtMap(analysisMapper.purchaseByMonth());
        Map<String, BigDecimal> saleRet = toAmtMap(analysisMapper.saleReturnByMonth());
        Map<String, BigDecimal> purRet = toAmtMap(analysisMapper.purchaseReturnByMonth());
        Map<String, BigDecimal> exp = toAmtMap(analysisMapper.expenseByMonth());
        // 折损收款：退货环节向客户收取的补偿，并入营业收入
        Map<String, BigDecimal> loss = toAmtMap(analysisMapper.saleReturnLossByMonth());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String ym : ms) {
            BigDecimal lossAmt = loss.getOrDefault(ym, ZERO);
            BigDecimal revenue = sale.getOrDefault(ym, ZERO).subtract(saleRet.getOrDefault(ym, ZERO)).add(lossAmt);
            BigDecimal cost = purchase.getOrDefault(ym, ZERO).subtract(purRet.getOrDefault(ym, ZERO));
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
        Map<String, BigDecimal> purchase = toAmtMap(analysisMapper.purchaseByMonth());
        Map<String, BigDecimal> saleRet = toAmtMap(analysisMapper.saleReturnByMonth());
        Map<String, BigDecimal> purRet = toAmtMap(analysisMapper.purchaseReturnByMonth());
        Map<String, BigDecimal> exp = toAmtMap(analysisMapper.expenseByMonth());
        Map<String, BigDecimal> loss = toAmtMap(analysisMapper.saleReturnLossByMonth());
        Set<String> allYm = new HashSet<>();
        allYm.addAll(sale.keySet()); allYm.addAll(purchase.keySet()); allYm.addAll(exp.keySet()); allYm.addAll(loss.keySet());
        for (String ym : allYm) {
            if (!ym.startsWith(yearPrefix)) continue;
            ytdRevenue = ytdRevenue.add(sale.getOrDefault(ym, ZERO)).subtract(saleRet.getOrDefault(ym, ZERO)).add(loss.getOrDefault(ym, ZERO));
            ytdCost = ytdCost.add(purchase.getOrDefault(ym, ZERO)).subtract(purRet.getOrDefault(ym, ZERO));
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
        collectTax(analysisMapper.purchaseTaxByMonth(), "purchase", amt, tax);
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
}
