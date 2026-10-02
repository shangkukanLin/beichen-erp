package com.beichen.erp.material.service.impl;

import com.beichen.erp.material.mapper.ProductAnalysisMapper;
import com.beichen.erp.material.service.ProductAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 产品分析 Service 实现（经营分析 → 产品分析，2026-10-02 用户要求新增）。
 *
 * <p><b>口径（2026-10-02 用户拍板「冲减净额」）：</b></p>
 * <pre>
 *   净销量   = Σ已审核销售单明细数量 − Σ已审核销售退货单明细数量
 *   净销售额 = Σ销售明细金额 − Σ退货明细金额
 *   净成本   = Σ销售数量×产品移动加权成本价 − Σ退货数量×产品移动加权成本价（成本口径 B）
 *   毛利     = 净销售额 − 净成本        毛利率 = 毛利 ÷ 净销售额 × 100%（2 位）
 *   退货率   = 退货额 ÷ 销售额（金额口径）/ 退货件数 ÷ **销售件数**（件数口径）
 *   换货率   = 换货额 ÷ 销售额（金额口径）/ **换出件数** ÷ **销售件数**（件数口径）
 *             换货额/换出件数取**明细 out_amount / out_quantity**（主表 total_amount 从不回写）；
 *             换货**不进净额**（净额只冲减退货），只用于换货率。
 * </pre>
 * <p>与「客户分析」的产品行同一套算法（{@code CustomerAnalysisServiceImpl.profile()} 的 byProduct/cost/profit），
 * 与「销售分析」的差别只在**是否冲减退货**：销售分析的产品排行不冲减，产品分析按用户口径一律冲减。</p>
 *
 * <p><b>归期：</b>销售与退货**各按自己的建单日**（2026-09-15 全站统一口径）；两端都取**明细口径**
 * （Σ item.qty / Σ item.amt）⇒ 产品行合计与 KPI 合计必然自洽。</p>
 *
 * <p><b>已知取舍：</b>①区间过滤在 Java 侧（沿用兄弟模块做法，{@code SaleAnalysisMapper} 注释说明"日期参数绑进 SQL
 * 实测取不到数据"）⇒ 每次请求全表拉回内存，属既有技术债 F7-117 的同款；②换货单**不计入**（换货不是新增销售，
 * 且产品分析只回答"卖了多少/赚了多少"，换货台账在换货单模块）；该口径已与用户确认，若将来要计入需同时改本类
 * 与前端文案；③排行表含"本月只有退货、没有销售"的产品行（净额为负）—— 这样**行合计与 KPI 合计逐项自洽**
 * （反之会出现"行合计 0 / KPI −1"这种对不上的情况，2026-10-02 实测踩到）。</p>
 */
@Service
@RequiredArgsConstructor
public class ProductAnalysisServiceImpl implements ProductAnalysisService {

    private final ProductAnalysisMapper mapper;

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 快捷区间解析：返回 [start, end]，末日为今天（与财务/销售分析同一套口径） */
    private LocalDate[] resolvePresetRange(String preset) {
        LocalDate today = LocalDate.now();
        switch (preset == null ? "month" : preset) {
            case "yesterday": LocalDate y = today.minusDays(1); return new LocalDate[]{y, y};
            case "today":     return new LocalDate[]{today, today};
            case "week":      return new LocalDate[]{today.with(java.time.DayOfWeek.MONDAY), today};
            case "quarter":   return new LocalDate[]{today.withDayOfMonth(1).withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1), today};
            case "year":      return new LocalDate[]{today.withDayOfYear(1), today};
            case "month":
            default:          return new LocalDate[]{today.withDayOfMonth(1), today};
        }
    }

    /** 区间：自定义优先，否则走快捷区间；硬上限 400 天（超出则前移起点，与销售/财务分析一致） */
    private LocalDate[] range(String preset, String start, String end) {
        LocalDate s, e;
        if (start != null && !start.isBlank() && end != null && !end.isBlank()) {
            s = LocalDate.parse(start); e = LocalDate.parse(end);
            if (s.isAfter(e)) { LocalDate t = s; s = e; e = t; }
        } else {
            LocalDate[] r = resolvePresetRange(preset);
            s = r[0]; e = r[1];
        }
        if (s.plusDays(400).isBefore(e)) s = e.minusDays(399);
        return new LocalDate[]{s, e};
    }

    private BigDecimal toBd(Object v) { return v == null ? ZERO : new BigDecimal(v.toString()); }
    private String str(Object v) { return v == null ? "" : v.toString(); }

    /** 百分比（%）：part ÷ total × 100，保留 2 位；total 为 0 时返回 0（不做无意义除法） */
    private BigDecimal rate(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) return ZERO;
        return part.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }

    /** 产品的移动加权成本价（缺失/为空按 0，与销售分析同做法；页面另对"成本缺失"的产品做提示） */
    private BigDecimal costOf(Map<Long, Map<String, Object>> prodMap, Long pid) {
        Map<String, Object> p = prodMap.get(pid);
        return p == null ? ZERO : toBd(p.get("cost_price"));
    }

    /** 区间内每一天（用于趋势补零与归期判定） */
    private Set<String> daySet(LocalDate s, LocalDate e) {
        Set<String> days = new LinkedHashSet<>();
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) days.add(d.toString());
        return days;
    }

    @Override
    public Map<String, Object> product(String preset, String start, String end) {
        LocalDate[] re = range(preset, start, end);
        LocalDate s = re[0], e = re[1];
        Set<String> days = daySet(s, e);

        // ① 档案：产品（名称/SKU/单位/品牌/成本价）与品牌名
        Map<Long, Map<String, Object>> prodMap = new HashMap<>();
        for (Map<String, Object> p : mapper.productAll()) {
            prodMap.put(toBd(p.get("id")).longValue(), p);
        }
        Map<Long, String> brandNameMap = new HashMap<>();
        for (Map<String, Object> b : mapper.brandAll()) {
            brandNameMap.put(toBd(b.get("id")).longValue(), str(b.get("brand_name")));
        }

        // ② 区间内已审核销售单 / 退货单 → 建单日（明细行的归期由所属单据决定）
        Map<Long, String> orderDay = new HashMap<>();
        for (Map<String, Object> o : mapper.saleOrderAll()) {
            String d = str(o.get("d"));
            if (!days.contains(d)) continue;
            orderDay.put(toBd(o.get("id")).longValue(), d);
        }
        Map<Long, String> retDay = new HashMap<>();
        for (Map<String, Object> r : mapper.saleReturnAll()) {
            String d = str(r.get("d"));
            if (!days.contains(d)) continue;
            retDay.put(toBd(r.get("id")).longValue(), d);
        }

        // ③ 销售明细：按产品归集（数量/金额/成本），并按天归集销售额（趋势）
        Map<Long, BigDecimal> qtyByProduct = new HashMap<>();
        Map<Long, BigDecimal> amtByProduct = new HashMap<>();
        Map<Long, BigDecimal> costByProduct = new HashMap<>();
        Map<String, BigDecimal> saleAmtByDay = new HashMap<>();
        BigDecimal saleQty = ZERO, saleAmount = ZERO, saleCost = ZERO;
        for (Map<String, Object> it : mapper.saleOrderItemAll()) {
            String d = orderDay.get(toBd(it.get("order_id")).longValue());
            if (d == null) continue;
            // F7-120（与销售分析同口径）：product_id 为 NULL 时若直接取 longValue 会归入 pid=0，
            // 凭空多出一行"产品 0" ⇒ 直接跳过（防御性修复，现网无此数据）
            if (it.get("product_id") == null) continue;
            Long pid = toBd(it.get("product_id")).longValue();
            BigDecimal q = toBd(it.get("qty")), a = toBd(it.get("amt"));
            BigDecimal c = costOf(prodMap, pid).multiply(q);
            qtyByProduct.merge(pid, q, BigDecimal::add);
            amtByProduct.merge(pid, a, BigDecimal::add);
            costByProduct.merge(pid, c, BigDecimal::add);
            saleQty = saleQty.add(q);
            saleAmount = saleAmount.add(a);
            saleCost = saleCost.add(c);
            saleAmtByDay.merge(d, a, BigDecimal::add);
        }

        // ④ 退货明细：同口径冲减（数量/金额/成本）与按天退货额
        Map<Long, BigDecimal> retQtyByProduct = new HashMap<>();
        Map<Long, BigDecimal> retAmtByProduct = new HashMap<>();
        Map<Long, BigDecimal> retCostByProduct = new HashMap<>();
        Map<String, BigDecimal> retAmtByDay = new HashMap<>();
        BigDecimal returnQty = ZERO, returnAmount = ZERO, returnCost = ZERO;
        for (Map<String, Object> it : mapper.saleReturnItemAll()) {
            String d = retDay.get(toBd(it.get("return_id")).longValue());
            if (d == null) continue;
            if (it.get("product_id") == null) continue;
            Long pid = toBd(it.get("product_id")).longValue();
            BigDecimal q = toBd(it.get("qty")), a = toBd(it.get("amt"));
            BigDecimal c = costOf(prodMap, pid).multiply(q);
            retQtyByProduct.merge(pid, q, BigDecimal::add);
            retAmtByProduct.merge(pid, a, BigDecimal::add);
            retCostByProduct.merge(pid, c, BigDecimal::add);
            returnQty = returnQty.add(q);
            returnAmount = returnAmount.add(a);
            returnCost = returnCost.add(c);
            retAmtByDay.merge(d, a, BigDecimal::add);
        }

        BigDecimal netQty = saleQty.subtract(returnQty);
        BigDecimal netAmount = saleAmount.subtract(returnAmount);
        BigDecimal netCost = saleCost.subtract(returnCost);
        BigDecimal profit = netAmount.subtract(netCost);

        // ④b 换货（2026-10-02 用户要求「产品退货率 / 产品换货率」）：金额 = Σ明细**换出金额** out_amount，
        //     件数 = Σ**换出件数** out_quantity —— 与销售分析的客户换货率同一口径（换货单主表 total_amount
        //     后端从不回写、恒为 0，取它会让换货率永远 0）。换货**不进净额**（净额只冲减退货，用户 2026-10-02 口径），
        //     它只用于换货率：率 = 换货额 ÷ 销售额（金额口径）/ 换出件数 ÷ 销售件数（件数口径）。
        Map<Long, BigDecimal> exchAmtByProduct = new HashMap<>();
        Map<Long, BigDecimal> exchQtyByProduct = new HashMap<>();
        BigDecimal sumExchange = ZERO, sumExchangeQty = ZERO;
        for (Map<String, Object> it : mapper.exchangeItemAll()) {
            if (!days.contains(str(it.get("d")))) continue;
            if (it.get("product_id") == null) continue;
            Long pid = toBd(it.get("product_id")).longValue();
            BigDecimal a = toBd(it.get("out_amt")), q = toBd(it.get("out_qty"));
            exchAmtByProduct.merge(pid, a, BigDecimal::add);
            exchQtyByProduct.merge(pid, q, BigDecimal::add);
            sumExchange = sumExchange.add(a);
            sumExchangeQty = sumExchangeQty.add(q);
        }

        // ⑤ 逐产品净额行（排行表与饼图的唯一数据源 ⇒ 合计必然自洽）
        //    ⚠️ 必须取「有销售的产品 ∪ 有退货的产品 ∪ **有换货的产品**」：只遍历有销售的产品会漏掉
        //    "本月只有退货/只有换货、没有销售"的产品（实测 2026-10-02：产品 147 本月只有退货 1 件 ⇒
        //    若不出行，则**行合计 ≠ KPI 合计**：行 netQty 合计 0、KPI netQty −1）。这类产品的净额/毛利为负、
        //    或换货率异常，正是最该被看见的行，故照常出行。
        //    注意 `summary.productCount`（动销产品数）仍只数"有销售的产品"，不受此影响。
        Set<Long> allProductIds = new LinkedHashSet<>(amtByProduct.keySet());
        allProductIds.addAll(retAmtByProduct.keySet());
        allProductIds.addAll(exchAmtByProduct.keySet());
        List<Map<String, Object>> byProduct = new ArrayList<>();
        for (Long pid : allProductIds) {
            Map<String, Object> p = prodMap.get(pid);
            BigDecimal a = amtByProduct.getOrDefault(pid, ZERO);
            BigDecimal rQty = retQtyByProduct.getOrDefault(pid, ZERO);
            BigDecimal rAmt = retAmtByProduct.getOrDefault(pid, ZERO);
            BigDecimal nQty = qtyByProduct.getOrDefault(pid, ZERO).subtract(rQty);
            BigDecimal nAmt = a.subtract(rAmt);
            BigDecimal nCost = costByProduct.getOrDefault(pid, ZERO).subtract(retCostByProduct.getOrDefault(pid, ZERO));
            BigDecimal pf = nAmt.subtract(nCost);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", pid);
            m.put("sku", p == null ? "" : str(p.get("sku")));
            m.put("productName", p == null ? "" : str(p.get("name")));
            m.put("unit", p == null ? "" : str(p.get("unit")));
            Long bid = p == null || p.get("brand_id") == null ? 0L : toBd(p.get("brand_id")).longValue();
            m.put("brandName", brandNameMap.getOrDefault(bid, ""));
            m.put("saleQty", qtyByProduct.getOrDefault(pid, ZERO));
            m.put("returnQty", rQty);
            m.put("netQty", nQty);
            m.put("saleAmount", a);
            m.put("returnAmount", rAmt);
            m.put("netAmount", nAmt);
            m.put("cost", nCost);
            m.put("profit", pf);
            // 毛利率：净销售额为 0 或为负时不计算（负分母会让"亏损"看起来像"高毛利"）
            m.put("profitRate", nAmt.signum() > 0 ? rate(pf, nAmt) : ZERO);
            m.put("share", rate(nAmt, netAmount));
            // 换货额 / 换出件数（「产品换货率」饼图与逐行费率）
            BigDecimal exAmt = exchAmtByProduct.getOrDefault(pid, ZERO);
            BigDecimal exQty = exchQtyByProduct.getOrDefault(pid, ZERO);
            m.put("exchAmount", exAmt);
            m.put("exchQty", exQty);
            // 逐产品费率（分母都是**该产品自己的销售**；分母 ≤ 0 时不算 —— 与总费率同一口径）
            BigDecimal soldQty = qtyByProduct.getOrDefault(pid, ZERO);
            m.put("returnRate", a.signum() > 0 ? rate(rAmt, a) : ZERO);
            m.put("returnRateQty", soldQty.signum() > 0 ? rate(rQty, soldQty) : ZERO);
            m.put("exchangeRate", a.signum() > 0 ? rate(exAmt, a) : ZERO);
            m.put("exchangeRateQty", soldQty.signum() > 0 ? rate(exQty, soldQty) : ZERO);
            byProduct.add(m);
        }
        // 排行按净销售额降序（用户看的就是"净"的口径）
        byProduct.sort((x, y) -> ((BigDecimal) y.get("netAmount")).compareTo((BigDecimal) x.get("netAmount")));

        // ⑥ 趋势（按天，缺日补 0）
        List<String> dates = new ArrayList<>();
        List<BigDecimal> saleAmounts = new ArrayList<>(), returnAmounts = new ArrayList<>(), netAmounts = new ArrayList<>();
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
            String k = d.toString();
            BigDecimal a = saleAmtByDay.getOrDefault(k, ZERO);
            BigDecimal r = retAmtByDay.getOrDefault(k, ZERO);
            dates.add(k);
            saleAmounts.add(a);
            returnAmounts.add(r);
            netAmounts.add(a.subtract(r));
        }

        // ⑦ KPI
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("saleAmount", saleAmount);
        summary.put("returnAmount", returnAmount);
        summary.put("netAmount", netAmount);
        summary.put("saleQty", saleQty);
        summary.put("returnQty", returnQty);
        summary.put("netQty", netQty);
        summary.put("cost", netCost);
        summary.put("profit", profit);
        summary.put("profitRate", netAmount.signum() > 0 ? rate(profit, netAmount) : ZERO);
        // 退货率 / 换货率（2026-10-02 用户要求）：金额口径分母 = 销售额；件数口径分母 = **销售件数**
        //（与销售分析的客户退货率/换货率同一口径；分母 ≤ 0 时不算，避免负比率）
        summary.put("returnRate", saleAmount.signum() > 0 ? rate(returnAmount, saleAmount) : ZERO);
        summary.put("returnRateQty", saleQty.signum() > 0 ? rate(returnQty, saleQty) : ZERO);
        summary.put("exchangeAmount", sumExchange);
        summary.put("exchangeQty", sumExchangeQty);
        summary.put("exchangeRate", saleAmount.signum() > 0 ? rate(sumExchange, saleAmount) : ZERO);
        summary.put("exchangeRateQty", saleQty.signum() > 0 ? rate(sumExchangeQty, saleQty) : ZERO);
        // 动销产品数 = 区间内出现过销售明细的产品数
        summary.put("productCount", amtByProduct.size());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("start", s.toString());
        res.put("end", e.toString());
        res.put("dates", dates);
        res.put("saleAmounts", saleAmounts);
        res.put("returnAmounts", returnAmounts);
        res.put("netAmounts", netAmounts);
        res.put("summary", summary);
        res.put("byProduct", byProduct);
        return res;
    }

    @Override
    public Map<String, Object> records(String preset, String start, String end, Long productId) {
        LocalDate[] re = range(preset, start, end);
        Set<String> days = daySet(re[0], re[1]);

        List<Map<String, Object>> list = new ArrayList<>();
        BigDecimal saleQty = ZERO, saleAmount = ZERO, returnQty = ZERO, returnAmount = ZERO;
        String sku = "", productName = "";

        if (productId != null) {
            Map<Long, Map<String, Object>> prodMap = new HashMap<>();
            for (Map<String, Object> p : mapper.productAll()) {
                prodMap.put(toBd(p.get("id")).longValue(), p);
            }
            Map<String, Object> p = prodMap.get(productId);
            if (p != null) {
                sku = str(p.get("sku"));
                productName = str(p.get("name"));
            }

            // 销售明细行（正数）
            for (Map<String, Object> r : mapper.saleItemRecords(productId)) {
                String d = str(r.get("d"));
                if (!days.contains(d)) continue;
                BigDecimal q = toBd(r.get("qty")), a = toBd(r.get("amt"));
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "SALE");
                m.put("billId", toBd(r.get("bill_id")).longValue());
                m.put("billNo", str(r.get("bill_no")));
                m.put("date", d);
                m.put("customerName", str(r.get("customer_name")));
                m.put("warehouseName", str(r.get("warehouse_name")));
                m.put("qualityType", str(r.get("quality_type")));
                m.put("qty", q);
                m.put("unitPrice", toBd(r.get("unit_price")));
                m.put("amount", a);
                list.add(m);
                saleQty = saleQty.add(q);
                saleAmount = saleAmount.add(a);
            }
            // 退货明细行（冲减；同样以正数展示，靠「类型」列区分，合计处给净额）
            for (Map<String, Object> r : mapper.returnItemRecords(productId)) {
                String d = str(r.get("d"));
                if (!days.contains(d)) continue;
                BigDecimal q = toBd(r.get("qty")), a = toBd(r.get("amt"));
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "RETURN");
                m.put("billId", toBd(r.get("bill_id")).longValue());
                m.put("billNo", str(r.get("bill_no")));
                m.put("date", d);
                m.put("customerName", str(r.get("customer_name")));
                m.put("warehouseName", str(r.get("warehouse_name")));
                m.put("qualityType", str(r.get("quality_type")));
                m.put("qty", q);
                m.put("unitPrice", toBd(r.get("unit_price")));
                m.put("amount", a);
                list.add(m);
                returnQty = returnQty.add(q);
                returnAmount = returnAmount.add(a);
            }
            // 日期倒序；同日销售在前、退货在后（便于逐单核对冲减）
            list.sort(Comparator
                    .comparing((Map<String, Object> m) -> String.valueOf(m.get("date"))).reversed()
                    .thenComparing(m -> String.valueOf(m.get("type"))));
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("start", re[0].toString());
        res.put("end", re[1].toString());
        res.put("productId", productId);
        res.put("sku", sku);
        res.put("productName", productName);
        res.put("saleQty", saleQty);
        res.put("returnQty", returnQty);
        res.put("netQty", saleQty.subtract(returnQty));
        res.put("saleAmount", saleAmount);
        res.put("returnAmount", returnAmount);
        res.put("netAmount", saleAmount.subtract(returnAmount));
        res.put("records", list);
        return res;
    }
}
