package com.beichen.erp.customer.service.impl;

import com.beichen.erp.customer.mapper.CustomerAnalysisMapper;
import com.beichen.erp.customer.service.CustomerAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * 客户分析 Service 实现：纯查询聚合。
 * 口径（2026-09-15 全站统一**建单日** create_time）：销售额=已审核销售单；退货额=已审核销售退单；净额=销售额-退货额；
 * 应收余额取 finance_receivable 未结清部分（与应收台账一致）。
 */
@Service
@RequiredArgsConstructor
public class CustomerAnalysisServiceImpl implements CustomerAnalysisService {

    private final CustomerAnalysisMapper analysisMapper;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

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

    @Override
    public Map<String, Object> customer(String preset, String start, String end) {
        LocalDate[] re = range(preset, start, end);
        LocalDate s = re[0], e = re[1];
        Set<String> days = new HashSet<>();
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) days.add(d.toString());

        // 客户档案
        Map<Long, Map<String, Object>> custMap = new LinkedHashMap<>();
        for (Map<String, Object> c : analysisMapper.customerAll()) {
            custMap.put(toBd(c.get("id")).longValue(), c);
        }

        // 销售按天×客户 → 区间内按客户汇总
        Map<Long, BigDecimal> amtByCust = new HashMap<>(), retByCust = new HashMap<>();
        Map<Long, Integer> cntByCust = new HashMap<>();
        Map<Long, String> lastDateByCust = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.saleByDayCustomer()) {
            if (!days.contains(str(r.get("d")))) continue;
            Long cid = toBd(r.get("customer_id")).longValue();
            amtByCust.merge(cid, toBd(r.get("amt")), BigDecimal::add);
            cntByCust.merge(cid, toBd(r.get("cnt")).intValue(), Integer::sum);
            String last = str(r.get("last_date"));
            if (!last.isEmpty() && last.compareTo(lastDateByCust.getOrDefault(cid, "")) > 0) lastDateByCust.put(cid, last);
        }
        for (Map<String, Object> r : analysisMapper.saleReturnByDayCustomer()) {
            if (!days.contains(str(r.get("d")))) continue;
            retByCust.merge(toBd(r.get("customer_id")).longValue(), toBd(r.get("amt")), BigDecimal::add);
        }
        Map<Long, BigDecimal> unpaidByCust = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.receivableByCustomer()) {
            unpaidByCust.put(toBd(r.get("customer_id")).longValue(), toBd(r.get("unpaid")));
        }
        // 本期新增客户：首单日期落在区间内
        Set<Long> newCustomerIds = new HashSet<>();
        for (Map<String, Object> r : analysisMapper.customerFirstOrder()) {
            String first = str(r.get("first_date"));
            if (!first.isEmpty() && days.contains(first)) newCustomerIds.add(toBd(r.get("customer_id")).longValue());
        }

        // 客户销售成本：Σ(销售明细数量 × 产品移动加权成本价)；退货退回的存货按同样口径冲回
        // 说明：采购成本无法按客户归集（采购单面向供应商），故按产品当前成本估算毛利
        Map<Long, BigDecimal> costPriceMap = new HashMap<>();
        for (Map<String, Object> p : analysisMapper.productAll()) {
            costPriceMap.put(toBd(p.get("id")).longValue(), toBd(p.get("cost_price")));
        }
        Map<Long, Long> orderCustomer = new HashMap<>();   // 订单 → 客户（仅区间内订单）
        for (Map<String, Object> o : analysisMapper.saleOrderRecords()) {
            if (!days.contains(str(o.get("d")))) continue;
            orderCustomer.put(toBd(o.get("id")).longValue(), toBd(o.get("customer_id")).longValue());
        }
        Map<Long, BigDecimal> costByCust = new HashMap<>();
        for (Map<String, Object> it : analysisMapper.saleOrderItemAll()) {
            Long cid = orderCustomer.get(toBd(it.get("order_id")).longValue());
            if (cid == null) continue;
            BigDecimal cost = costPriceMap.getOrDefault(toBd(it.get("product_id")).longValue(), ZERO).multiply(toBd(it.get("qty")));
            costByCust.merge(cid, cost, BigDecimal::add);
        }
        // 退货冲回成本（仅区间内退货单）
        Map<Long, Long> returnCustomer = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.saleReturnAll()) {
            if (!days.contains(str(r.get("d")))) continue;
            returnCustomer.put(toBd(r.get("id")).longValue(), toBd(r.get("customer_id")).longValue());
        }
        Map<Long, BigDecimal> returnCostByCust = new HashMap<>();
        for (Map<String, Object> it : analysisMapper.saleReturnItemAll()) {
            Long cid = returnCustomer.get(toBd(it.get("return_id")).longValue());
            if (cid == null) continue;
            BigDecimal cost = costPriceMap.getOrDefault(toBd(it.get("product_id")).longValue(), ZERO).multiply(toBd(it.get("qty")));
            returnCostByCust.merge(cid, cost, BigDecimal::add);
        }

        // 客户明细行（含排行）
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long cid : custMap.keySet()) {
            Map<String, Object> c = custMap.get(cid);
            BigDecimal amt = amtByCust.getOrDefault(cid, ZERO);
            BigDecimal ret = retByCust.getOrDefault(cid, ZERO);
            int cnt = cntByCust.getOrDefault(cid, 0);
            // 无成交且无欠款的客户不占用列表
            if (amt.compareTo(ZERO) == 0 && ret.compareTo(ZERO) == 0 && cnt == 0
                    && toBd(unpaidByCust.get(cid)).compareTo(ZERO) == 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("customerId", cid);
            m.put("customerCode", str(c.get("code")));
            m.put("customerName", str(c.get("name")));
            m.put("amount", amt);
            m.put("returnAmount", ret);
            m.put("netAmount", amt.subtract(ret));
            // 成本与毛利：销售成本 − 退货冲回成本，按产品移动加权成本价估算
            BigDecimal cost = costByCust.getOrDefault(cid, ZERO).subtract(returnCostByCust.getOrDefault(cid, ZERO));
            BigDecimal net = amt.subtract(ret);
            m.put("cost", cost);
            m.put("profit", net.subtract(cost));
            m.put("profitRate", net.compareTo(ZERO) == 0 ? ZERO
                    : net.subtract(cost).multiply(new BigDecimal("100")).divide(net, 2, RoundingMode.HALF_UP));
            m.put("orderCount", cnt);
            m.put("unpaid", unpaidByCust.getOrDefault(cid, ZERO));
            m.put("creditPeriod", c.get("credit_period"));
            m.put("creditLimit", c.get("credit_limit"));
            m.put("lastDate", lastDateByCust.getOrDefault(cid, ""));
            m.put("isNew", newCustomerIds.contains(cid));
            m.put("status", c.get("status"));
            rows.add(m);
        }
        rows.sort((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")));

        // KPI
        BigDecimal totalAmount = ZERO, totalReturn = ZERO, totalProfit = ZERO;
        int activeCount = 0, totalOrders = 0;
        for (Map<String, Object> r : rows) {
            totalAmount = totalAmount.add(toBd(r.get("amount")));
            totalReturn = totalReturn.add(toBd(r.get("returnAmount")));
            totalProfit = totalProfit.add(toBd(r.get("profit")));
            totalOrders += (Integer) r.get("orderCount");
            if ((Integer) r.get("orderCount") > 0) activeCount++;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("customerCount", custMap.size());
        summary.put("activeCount", activeCount);
        summary.put("newCount", newCustomerIds.size());
        summary.put("totalAmount", totalAmount);
        summary.put("returnAmount", totalReturn);
        summary.put("netAmount", totalAmount.subtract(totalReturn));
        summary.put("profit", totalProfit);
        // 毛利率：毛利 / 净销售额
        summary.put("profitRate", (totalAmount.subtract(totalReturn)).compareTo(ZERO) == 0 ? ZERO
                : totalProfit.multiply(new BigDecimal("100")).divide(totalAmount.subtract(totalReturn), 2, RoundingMode.HALF_UP));
        summary.put("orderCount", totalOrders);
        summary.put("avgAmount", activeCount > 0
                ? totalAmount.divide(new BigDecimal(activeCount), 2, RoundingMode.HALF_UP) : ZERO);

        // 2026-09-15 用户要求：删除「客户销售额 TOP10」卡片 → 后端**不再产出 `top` 字段**
        // （前端 `views/analysis/customer.vue` 的条形图与 echarts 依赖已同步清理）

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("start", s.toString());
        res.put("end", e.toString());
        res.put("summary", summary);
        res.put("rows", rows);
        return res;
    }

    @Override
    public Map<String, Object> profile(Long customerId, String preset, String start, String end) {
        LocalDate[] re = range(preset, start, end);
        LocalDate s = re[0], e = re[1];
        Set<String> days = new HashSet<>();
        List<String> months = new ArrayList<>();
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
            days.add(d.toString());
            String ym = d.toString().substring(0, 7);
            if (months.isEmpty() || !months.get(months.size() - 1).equals(ym)) months.add(ym);
        }

        // 档案与应收
        Map<String, Object> cust = new LinkedHashMap<>();
        for (Map<String, Object> c : analysisMapper.customerAll()) {
            if (customerId.equals(toBd(c.get("id")).longValue())) {
                cust.put("customerId", customerId);
                cust.put("customerCode", str(c.get("code")));
                cust.put("customerName", str(c.get("name")));
                cust.put("contact", str(c.get("contact")));
                cust.put("phone", str(c.get("phone")));
                cust.put("creditPeriod", c.get("credit_period"));
                cust.put("creditLimit", c.get("credit_limit"));
                break;
            }
        }
        BigDecimal unpaid = ZERO;
        for (Map<String, Object> r : analysisMapper.receivableByCustomer()) {
            if (customerId.equals(toBd(r.get("customer_id")).longValue())) unpaid = toBd(r.get("unpaid"));
        }
        cust.put("unpaid", unpaid);

        // 产品档案与品牌
        Map<Long, Map<String, Object>> prodMap = new HashMap<>();
        for (Map<String, Object> p : analysisMapper.productAll()) {
            prodMap.put(toBd(p.get("id")).longValue(), p);
        }
        Map<Long, String> brandNameMap = new HashMap<>();
        for (Map<String, Object> b : analysisMapper.brandAll()) {
            brandNameMap.put(toBd(b.get("id")).longValue(), str(b.get("brand_name")));
        }

        // 区间内该客户的订单
        Map<Long, String> orderMonth = new HashMap<>();
        BigDecimal amount = ZERO;
        int orderCount = 0;
        for (Map<String, Object> o : analysisMapper.saleOrderRecords()) {
            if (!customerId.equals(toBd(o.get("customer_id")).longValue())) continue;
            String d = str(o.get("d"));
            if (!days.contains(d)) continue;
            orderMonth.put(toBd(o.get("id")).longValue(), d.substring(0, 7));
            amount = amount.add(toBd(o.get("total_amount")));
            orderCount++;
        }

        // 明细：按产品 / 品牌 / 品质 / 月份 归集数量、销售额、成本
        Map<Long, BigDecimal> qtyByProduct = new HashMap<>(), amtByProduct = new HashMap<>(), costByProduct = new HashMap<>();
        Map<Long, BigDecimal> qtyByBrand = new HashMap<>(), amtByBrand = new HashMap<>(), costByBrand = new HashMap<>();
        Map<String, BigDecimal> amtByQuality = new LinkedHashMap<>();
        Map<String, BigDecimal> costByMonth = new HashMap<>();
        for (Map<String, Object> it : analysisMapper.saleOrderItemAll()) {
            Long oid = toBd(it.get("order_id")).longValue();
            String ym = orderMonth.get(oid);
            if (ym == null) continue;
            Long pid = toBd(it.get("product_id")).longValue();
            Map<String, Object> p = prodMap.get(pid);
            BigDecimal qty = toBd(it.get("qty"));
            BigDecimal amt = toBd(it.get("amt"));
            BigDecimal cost = (p == null ? ZERO : toBd(p.get("cost_price"))).multiply(qty);
            qtyByProduct.merge(pid, qty, BigDecimal::add);
            amtByProduct.merge(pid, amt, BigDecimal::add);
            costByProduct.merge(pid, cost, BigDecimal::add);
            Long bid = p == null || p.get("brand_id") == null ? 0L : toBd(p.get("brand_id")).longValue();
            qtyByBrand.merge(bid, qty, BigDecimal::add);
            amtByBrand.merge(bid, amt, BigDecimal::add);
            costByBrand.merge(bid, cost, BigDecimal::add);
            String q = str(it.get("quality_type"));
            if (q.isEmpty()) q = "未分类";
            amtByQuality.merge(q, amt, BigDecimal::add);
            costByMonth.merge(ym, cost, BigDecimal::add);
        }

        // 退货：金额、冲回成本、按产品的退货量、明细行
        BigDecimal returnAmount = ZERO, lossAmount = ZERO, returnCost = ZERO;
        Map<Long, BigDecimal> retQtyByProduct = new HashMap<>(), retAmtByProduct = new HashMap<>();
        List<Map<String, Object>> returnRows = new ArrayList<>();
        Map<Long, Map<String, Object>> retBillMap = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.saleReturnAll()) {
            if (!customerId.equals(toBd(r.get("customer_id")).longValue())) continue;
            if (!days.contains(str(r.get("d")))) continue;
            returnAmount = returnAmount.add(toBd(r.get("total_amount")));
            lossAmount = lossAmount.add(toBd(r.get("loss_amount")));
            retBillMap.put(toBd(r.get("id")).longValue(), r);
        }
        for (Map<String, Object> it : analysisMapper.saleReturnItemDetail()) {
            Map<String, Object> bill = retBillMap.get(toBd(it.get("return_id")).longValue());
            if (bill == null) continue;
            Long pid = toBd(it.get("product_id")).longValue();
            Map<String, Object> p = prodMap.get(pid);
            BigDecimal qty = toBd(it.get("qty"));
            BigDecimal amt = toBd(it.get("amt"));
            returnCost = returnCost.add((p == null ? ZERO : toBd(p.get("cost_price"))).multiply(qty));
            retQtyByProduct.merge(pid, qty, BigDecimal::add);
            retAmtByProduct.merge(pid, amt, BigDecimal::add);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("billId", toBd(it.get("return_id")).longValue());
            row.put("billNo", str(bill.get("code")));
            row.put("date", str(bill.get("d")));
            row.put("sku", p == null ? "" : str(p.get("sku")));
            row.put("productName", p == null ? "" : str(p.get("name")));
            row.put("quantity", qty);
            row.put("amount", amt);
            row.put("qualityType", str(it.get("quality_type")));
            row.put("chargeType", str(bill.get("charge_type")));
            row.put("lossAmount", toBd(bill.get("loss_amount")));
            row.put("remark", str(bill.get("remark")));
            returnRows.add(row);
        }
        returnRows.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("date"))).reversed());

        // 按月趋势：销售额（缺月补 0）与利润
        Map<String, BigDecimal> amtByMonth = new HashMap<>();
        for (Map<String, Object> o : analysisMapper.saleOrderRecords()) {
            if (!customerId.equals(toBd(o.get("customer_id")).longValue())) continue;
            String d = str(o.get("d"));
            if (!days.contains(d)) continue;
            amtByMonth.merge(d.substring(0, 7), toBd(o.get("total_amount")), BigDecimal::add);
        }
        List<BigDecimal> monthAmounts = new ArrayList<>(), monthProfits = new ArrayList<>();
        for (String ym : months) {
            BigDecimal a = amtByMonth.getOrDefault(ym, ZERO);
            BigDecimal c = costByMonth.getOrDefault(ym, ZERO);
            monthAmounts.add(a);
            monthProfits.add(a.subtract(c));
        }

        // 按产品行
        List<Map<String, Object>> byProduct = new ArrayList<>();
        for (Long pid : amtByProduct.keySet()) {
            Map<String, Object> p = prodMap.get(pid);
            Long bid = p == null || p.get("brand_id") == null ? 0L : toBd(p.get("brand_id")).longValue();
            BigDecimal a = amtByProduct.getOrDefault(pid, ZERO);
            BigDecimal c = costByProduct.getOrDefault(pid, ZERO);
            BigDecimal profit = a.subtract(c);
            BigDecimal rq = retQtyByProduct.getOrDefault(pid, ZERO);
            BigDecimal sold = qtyByProduct.getOrDefault(pid, ZERO);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", pid);
            m.put("sku", p == null ? "" : str(p.get("sku")));
            m.put("productName", p == null ? "" : str(p.get("name")));
            m.put("brandName", brandNameMap.getOrDefault(bid, ""));
            m.put("model", p == null ? "" : str(p.get("general_model")));
            m.put("unit", p == null ? "" : str(p.get("unit")));
            m.put("quantity", sold);
            m.put("amount", a);
            m.put("cost", c);
            m.put("profit", profit);
            m.put("profitRate", a.compareTo(ZERO) == 0 ? ZERO
                    : profit.multiply(new BigDecimal("100")).divide(a, 2, RoundingMode.HALF_UP));
            m.put("returnQty", rq);
            // 退货率：退货量 / 销售量
            m.put("returnRate", sold.compareTo(ZERO) == 0 ? ZERO
                    : rq.multiply(new BigDecimal("100")).divide(sold, 2, RoundingMode.HALF_UP));
            byProduct.add(m);
        }
        byProduct.sort((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")));

        // 按品牌汇总
        List<Map<String, Object>> byBrand = new ArrayList<>();
        for (Long bid : amtByBrand.keySet()) {
            BigDecimal a = amtByBrand.getOrDefault(bid, ZERO);
            BigDecimal c = costByBrand.getOrDefault(bid, ZERO);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("brandId", bid);
            m.put("brandName", bid == 0 ? "未分类" : brandNameMap.getOrDefault(bid, ""));
            m.put("quantity", qtyByBrand.getOrDefault(bid, ZERO));
            m.put("amount", a);
            m.put("cost", c);
            m.put("profit", a.subtract(c));
            byBrand.add(m);
        }
        byBrand.sort((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")));

        // 品质结构
        List<Map<String, Object>> byQuality = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> en : amtByQuality.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("qualityType", en.getKey());
            m.put("amount", en.getValue());
            byQuality.add(m);
        }
        byQuality.sort((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")));

        BigDecimal net = amount.subtract(returnAmount);
        BigDecimal cost = ZERO;
        for (BigDecimal c : costByProduct.values()) cost = cost.add(c);
        cost = cost.subtract(returnCost);
        BigDecimal profit = net.subtract(cost);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("amount", amount);
        summary.put("returnAmount", returnAmount);
        summary.put("netAmount", net);
        summary.put("cost", cost);
        summary.put("profit", profit);
        summary.put("profitRate", net.compareTo(ZERO) == 0 ? ZERO
                : profit.multiply(new BigDecimal("100")).divide(net, 2, RoundingMode.HALF_UP));
        // 退货率：退货额 / 销售额
        summary.put("returnRate", amount.compareTo(ZERO) == 0 ? ZERO
                : returnAmount.multiply(new BigDecimal("100")).divide(amount, 2, RoundingMode.HALF_UP));
        summary.put("lossAmount", lossAmount);
        summary.put("orderCount", orderCount);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("start", s.toString());
        res.put("end", e.toString());
        res.put("customer", cust);
        res.put("summary", summary);
        res.put("months", months);
        res.put("monthAmounts", monthAmounts);
        res.put("monthProfits", monthProfits);
        res.put("byProduct", byProduct);
        res.put("byBrand", byBrand);
        res.put("byQuality", byQuality);
        res.put("returns", returnRows);
        return res;
    }

    @Override
    public Map<String, Object> records(Long customerId, String preset, String start, String end) {
        LocalDate[] re = range(preset, start, end);
        Set<String> days = new HashSet<>();
        for (LocalDate d = re[0]; !d.isAfter(re[1]); d = d.plusDays(1)) days.add(d.toString());

        List<Map<String, Object>> list = new ArrayList<>();
        BigDecimal sum = ZERO;
        String name = "";
        for (Map<String, Object> o : analysisMapper.saleOrderRecords()) {
            if (customerId != null && !customerId.equals(toBd(o.get("customer_id")).longValue())) continue;
            if (!days.contains(str(o.get("d")))) continue;
            name = str(o.get("customer_name"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("billId", toBd(o.get("id")).longValue());
            m.put("billNo", str(o.get("code")));
            m.put("date", str(o.get("d")));
            m.put("customerName", str(o.get("customer_name")));
            m.put("warehouseName", str(o.get("warehouse_name")));
            m.put("amount", toBd(o.get("total_amount")));
            m.put("remark", str(o.get("remark")));
            sum = sum.add(toBd(o.get("total_amount")));
            list.add(m);
        }
        list.sort((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("start", re[0].toString());
        res.put("end", re[1].toString());
        res.put("customerId", customerId);
        res.put("customerName", name);
        res.put("totalAmount", sum);
        res.put("records", list);
        return res;
    }
}
