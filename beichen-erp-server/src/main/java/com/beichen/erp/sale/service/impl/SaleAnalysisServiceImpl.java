package com.beichen.erp.sale.service.impl;

import com.beichen.erp.sale.mapper.SaleAnalysisMapper;
import com.beichen.erp.sale.service.SaleAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * 销售分析 Service 实现：纯查询聚合，不改任何业务数据。
 * 口径：销售额=已审核销售单（审核日归期）合计；退货额=已审核销售退单（建单日归期）合计；净销售额=销售额-退货额。
 */
@Service
@RequiredArgsConstructor
public class SaleAnalysisServiceImpl implements SaleAnalysisService {

    private final SaleAnalysisMapper analysisMapper;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** 快捷区间解析：返回 [start, end]，末日为今天（与财务分析利润表同一套口径） */
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

    /**
     * 首页「当日销售构成」：按**单据日期（order_date）**统计指定日（默认今天）已审核销售单的产品/客户构成。
     * <p>口径：① 仅 AUDITED（饼图是销售构成，草稿/作废不计入）② 数量/金额均取**明细行**（Σ item.qty / Σ item.amt），
     * 故产品与客户两张饼图的总额**自洽** ③ 另返回 orderAmount = Σ 订单 total_amount，供与"明细口径"核对。</p>
     */
    @Override
    public Map<String, Object> byDocDate(String date) {
        String day = (date == null || date.isBlank()) ? LocalDate.now().toString() : LocalDate.parse(date).toString();

        // ① 单据日期 = day 的已审核销售单：订单号 + 客户 + 订单金额
        Set<Long> orderIds = new LinkedHashSet<>();
        Map<Long, Long> custByOrder = new HashMap<>();
        Map<Long, String> custNameMap = new HashMap<>();
        Map<Long, Integer> cntByCustomer = new HashMap<>();
        BigDecimal orderAmount = ZERO;
        for (Map<String, Object> o : analysisMapper.saleOrderByDocDate()) {
            if (!day.equals(str(o.get("d")))) continue;
            Long oid = toBd(o.get("id")).longValue();
            orderIds.add(oid);
            orderAmount = orderAmount.add(toBd(o.get("amt")));
            if (o.get("customer_id") == null) continue;
            Long cid = toBd(o.get("customer_id")).longValue();
            custByOrder.put(oid, cid);
            custNameMap.put(cid, str(o.get("customer_name")));
            cntByCustomer.merge(cid, 1, Integer::sum);
        }

        // ② 一次遍历明细行，同时累计 产品 / 客户 两个维度
        Map<Long, BigDecimal> qtyByProduct = new HashMap<>(), amtByProduct = new HashMap<>();
        Map<Long, BigDecimal> qtyByCustomer = new HashMap<>(), amtByCustomer = new HashMap<>();
        BigDecimal quantity = ZERO, amount = ZERO;
        for (Map<String, Object> it : analysisMapper.saleOrderItemAll()) {
            Long oid = toBd(it.get("order_id")).longValue();
            if (!orderIds.contains(oid)) continue;
            BigDecimal q = toBd(it.get("qty")), a = toBd(it.get("amt"));
            quantity = quantity.add(q);
            amount = amount.add(a);
            if (it.get("product_id") != null) {
                Long pid = toBd(it.get("product_id")).longValue();
                qtyByProduct.merge(pid, q, BigDecimal::add);
                amtByProduct.merge(pid, a, BigDecimal::add);
            }
            Long cid = custByOrder.get(oid);
            if (cid != null) {
                qtyByCustomer.merge(cid, q, BigDecimal::add);
                amtByCustomer.merge(cid, a, BigDecimal::add);
            }
        }

        // ③ 产品维度（批量补产品档案，避免逐条查库）
        Map<Long, String> nameMap = new HashMap<>(), skuMap = new HashMap<>(), unitMap = new HashMap<>();
        if (!amtByProduct.isEmpty()) {
            for (Map<String, Object> p : analysisMapper.productAll()) {
                Long pid = toBd(p.get("id")).longValue();
                nameMap.put(pid, str(p.get("name")));
                skuMap.put(pid, str(p.get("sku")));
                unitMap.put(pid, str(p.get("unit")));
            }
        }
        List<Map<String, Object>> byProduct = new ArrayList<>();
        for (Long pid : amtByProduct.keySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", pid);
            m.put("sku", skuMap.getOrDefault(pid, ""));
            m.put("productName", nameMap.getOrDefault(pid, ""));
            m.put("unit", unitMap.getOrDefault(pid, ""));
            m.put("quantity", qtyByProduct.getOrDefault(pid, ZERO));
            m.put("amount", amtByProduct.getOrDefault(pid, ZERO));
            byProduct.add(m);
        }
        byProduct.sort((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")));

        // ④ 客户维度
        List<Map<String, Object>> byCustomer = new ArrayList<>();
        for (Long cid : amtByCustomer.keySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("customerId", cid);
            m.put("customerName", custNameMap.getOrDefault(cid, ""));
            m.put("quantity", qtyByCustomer.getOrDefault(cid, ZERO));
            m.put("amount", amtByCustomer.getOrDefault(cid, ZERO));
            m.put("orderCount", cntByCustomer.getOrDefault(cid, 0));
            byCustomer.add(m);
        }
        byCustomer.sort((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("orderCount", orderIds.size());
        summary.put("quantity", quantity);
        summary.put("amount", amount);            // Σ 明细金额（与产品/客户两张饼图一致）
        summary.put("orderAmount", orderAmount);  // Σ 订单金额（供核对）

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("date", day);
        res.put("summary", summary);
        res.put("byProduct", byProduct);
        res.put("byCustomer", byCustomer);
        return res;
    }

    /** 区间内的已审核销售单（按归期日过滤），供各维度聚合与钻取复用 */
    private List<Map<String, Object>> ordersInRange(LocalDate s, LocalDate e) {
        Set<String> days = new HashSet<>();
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) days.add(d.toString());
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : analysisMapper.saleOrderRecords()) {
            if (days.contains(str(r.get("d")))) list.add(r);
        }
        return list;
    }

    @Override
    public Map<String, Object> sale(String preset, String start, String end) {
        LocalDate[] re = range(preset, start, end);
        LocalDate s = re[0], e = re[1];

        // ① 趋势：按天销售额 / 订单数 / 退货额
        Map<String, BigDecimal> amtByDay = new HashMap<>();
        Map<String, BigDecimal> cntByDay = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.saleByDay()) {
            amtByDay.put(str(r.get("d")), toBd(r.get("amt")));
            cntByDay.put(str(r.get("d")), toBd(r.get("cnt")));
        }
        Map<String, BigDecimal> retByDay = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.saleReturnByDay()) {
            retByDay.put(str(r.get("d")), toBd(r.get("amt")));
        }

        List<String> dates = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>(), returns = new ArrayList<>();
        BigDecimal sumAmount = ZERO, sumReturn = ZERO;
        int sumOrders = 0;
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
            String k = d.toString();
            BigDecimal a = amtByDay.getOrDefault(k, ZERO);
            BigDecimal rt = retByDay.getOrDefault(k, ZERO);
            dates.add(k);
            amounts.add(a);
            returns.add(rt);
            sumAmount = sumAmount.add(a);
            sumReturn = sumReturn.add(rt);
            sumOrders += cntByDay.getOrDefault(k, ZERO).intValue();
        }
        BigDecimal net = sumAmount.subtract(sumReturn);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("amount", sumAmount);
        summary.put("returnAmount", sumReturn);
        summary.put("netAmount", net);
        summary.put("orderCount", sumOrders);
        // 客单价：净销售额 / 订单数
        summary.put("avgOrder", sumOrders > 0 ? net.divide(new BigDecimal(sumOrders), 2, RoundingMode.HALF_UP) : ZERO);

        // ② 区间内订单（后续产品/仓库聚合与钻取共用）
        List<Map<String, Object>> orders = ordersInRange(s, e);
        Set<Long> orderIds = new HashSet<>();
        for (Map<String, Object> o : orders) orderIds.add(toBd(o.get("id")).longValue());

        // ③ 产品排行：按区间内订单的明细行聚合
        Map<Long, BigDecimal> qtyByProduct = new HashMap<>(), amtByProduct = new HashMap<>();
        for (Map<String, Object> it : analysisMapper.saleOrderItemAll()) {
            Long oid = toBd(it.get("order_id")).longValue();
            if (!orderIds.contains(oid)) continue;
            Long pid = toBd(it.get("product_id")).longValue();
            qtyByProduct.merge(pid, toBd(it.get("qty")), BigDecimal::add);
            amtByProduct.merge(pid, toBd(it.get("amt")), BigDecimal::add);
        }
        // 批量补产品档案
        Map<Long, String> nameMap = new HashMap<>(), skuMap = new HashMap<>(), unitMap = new HashMap<>();
        if (!qtyByProduct.isEmpty()) {
            for (Map<String, Object> p : analysisMapper.productAll()) {
                Long pid = toBd(p.get("id")).longValue();
                nameMap.put(pid, str(p.get("name")));
                skuMap.put(pid, str(p.get("sku")));
                unitMap.put(pid, str(p.get("unit")));
            }
        }
        List<Map<String, Object>> byProduct = new ArrayList<>();
        for (Long pid : amtByProduct.keySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", pid);
            m.put("sku", skuMap.getOrDefault(pid, ""));
            m.put("productName", nameMap.getOrDefault(pid, ""));
            m.put("unit", unitMap.getOrDefault(pid, ""));
            m.put("quantity", qtyByProduct.getOrDefault(pid, ZERO));
            m.put("amount", amtByProduct.getOrDefault(pid, ZERO));
            byProduct.add(m);
        }
        byProduct.sort((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")));

        // ④ 仓库分布
        Map<Long, BigDecimal> amtByWh = new LinkedHashMap<>();
        Map<Long, Integer> cntByWh = new HashMap<>();
        Map<Long, String> whNameMap = new HashMap<>();
        for (Map<String, Object> o : orders) {
            Long wid = o.get("warehouse_id") == null ? 0L : toBd(o.get("warehouse_id")).longValue();
            amtByWh.merge(wid, toBd(o.get("total_amount")), BigDecimal::add);
            cntByWh.merge(wid, 1, Integer::sum);
            whNameMap.putIfAbsent(wid, str(o.get("warehouse_name")));
        }
        List<Map<String, Object>> byWarehouse = new ArrayList<>();
        for (Long wid : amtByWh.keySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("warehouseId", wid);
            m.put("warehouseName", whNameMap.getOrDefault(wid, ""));
            m.put("orderCount", cntByWh.getOrDefault(wid, 0));
            m.put("amount", amtByWh.get(wid));
            byWarehouse.add(m);
        }
        byWarehouse.sort((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("start", s.toString());
        res.put("end", e.toString());
        res.put("summary", summary);
        res.put("dates", dates);
        res.put("amounts", amounts);
        res.put("returns", returns);
        res.put("byProduct", byProduct);
        res.put("byWarehouse", byWarehouse);
        return res;
    }

    @Override
    public Map<String, Object> records(String preset, String start, String end,
                                       Long productId, Long warehouseId, Long customerId) {
        LocalDate[] re = range(preset, start, end);
        List<Map<String, Object>> orders = ordersInRange(re[0], re[1]);

        // 按产品过滤：先取包含该产品的订单号集合
        Set<Long> orderIdsOfProduct = null;
        if (productId != null) {
            orderIdsOfProduct = new HashSet<>();
            for (Map<String, Object> it : analysisMapper.saleOrderItemAll()) {
                if (productId.equals(toBd(it.get("product_id")).longValue())) {
                    orderIdsOfProduct.add(toBd(it.get("order_id")).longValue());
                }
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        BigDecimal sum = ZERO;
        for (Map<String, Object> o : orders) {
            Long oid = toBd(o.get("id")).longValue();
            if (orderIdsOfProduct != null && !orderIdsOfProduct.contains(oid)) continue;
            if (warehouseId != null && !warehouseId.equals(toBd(o.get("warehouse_id")).longValue())) continue;
            if (customerId != null && !customerId.equals(toBd(o.get("customer_id")).longValue())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("billId", oid);
            m.put("billNo", str(o.get("code")));
            m.put("date", str(o.get("d")));
            m.put("customerId", o.get("customer_id"));
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
        res.put("totalAmount", sum);
        res.put("records", list);
        return res;
    }

}
