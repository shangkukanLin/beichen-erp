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
     * 占比（%）：part ÷ total × 100，保留 **2 位小数**；total 为 0 时返回 0（不做无意义除法）。
     * 2026-09-15 由 1 位改为 2 位：换货率等小额费率用 1 位会失真（真实 0.06% 被显示成 0.1%），
     * 且与 {@code FinanceAnalysisServiceImpl.rate()}（同为 2 位）保持一致。
     */
    private BigDecimal rate(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) return ZERO;
        return part.multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP);
    }

    /** 汇总为饼图分片列表：[{name, value}]，按 value 倒序（前端再做 前 8 名 + 其它） */
    private List<Map<String, Object>> toPieList(Map<Long, BigDecimal> amountByKey, Map<Long, String> nameByKey) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> en : amountByKey.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", nameByKey.getOrDefault(en.getKey(), ""));
            m.put("value", en.getValue());
            list.add(m);
        }
        list.sort((a, b) -> ((BigDecimal) b.get("value")).compareTo((BigDecimal) a.get("value")));
        return list;
    }

    /**
     * 首页「当日销售构成」：按**建单日（create_time）**统计指定日（默认今天）已审核销售单的产品/客户构成
     * （2026-09-15 全站统一归期，原按单据日期 order_date）。
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

        // ② 区间内订单（后续产品聚合与钻取共用）
        List<Map<String, Object>> orders = ordersInRange(s, e);
        Set<Long> orderIds = new HashSet<>();
        for (Map<String, Object> o : orders) orderIds.add(toBd(o.get("id")).longValue());

        // ③ 产品排行 + 区间 6 项指标（2026-09-15 新增，替换原柱状图）
        //    产品利润口径 = 成本口径 B：Σ明细金额 − Σ(明细数量 × 产品当前移动加权成本价)
        Map<Long, BigDecimal> costPrice = new HashMap<>();
        Map<Long, String> nameMap = new HashMap<>(), skuMap = new HashMap<>(), unitMap = new HashMap<>();
        for (Map<String, Object> p : analysisMapper.productAll()) {
            Long pid = toBd(p.get("id")).longValue();
            nameMap.put(pid, str(p.get("name")));
            skuMap.put(pid, str(p.get("sku")));
            unitMap.put(pid, str(p.get("unit")));
            costPrice.put(pid, toBd(p.get("cost_price")));
        }
        // 订单 → 客户（客户维度利润归集用）
        Map<Long, Long> custByOrder = new HashMap<>();
        Map<Long, String> custNameMap = new HashMap<>();
        for (Map<String, Object> o : orders) {
            if (o.get("customer_id") == null) continue;
            Long cid = toBd(o.get("customer_id")).longValue();
            custByOrder.put(toBd(o.get("id")).longValue(), cid);
            custNameMap.put(cid, str(o.get("customer_name")));
        }
        Map<Long, BigDecimal> qtyByProduct = new HashMap<>(), amtByProduct = new HashMap<>();
        Map<Long, BigDecimal> profitByCustomer = new HashMap<>();
        Map<Long, BigDecimal> qtyByCustomer = new HashMap<>(), amtByCustomer = new HashMap<>();
        BigDecimal sumQty = ZERO, sumItemAmt = ZERO, sumItemCost = ZERO;
        for (Map<String, Object> it : analysisMapper.saleOrderItemAll()) {
            Long oid = toBd(it.get("order_id")).longValue();
            if (!orderIds.contains(oid)) continue;
            // F7-120（2026-09-20）：与 byDocDate 一致地判空 —— product_id 为 NULL 时若直接
            // toBd(...).longValue() 会被归入 pid = 0，凭空多出一行"产品 0"（现网 0 条，属防御性修复）。
            if (it.get("product_id") == null) continue;
            Long pid = toBd(it.get("product_id")).longValue();
            BigDecimal q = toBd(it.get("qty")), a = toBd(it.get("amt"));
            qtyByProduct.merge(pid, q, BigDecimal::add);
            amtByProduct.merge(pid, a, BigDecimal::add);
            sumQty = sumQty.add(q);
            sumItemAmt = sumItemAmt.add(a);
            BigDecimal itemCost = costPrice.getOrDefault(pid, ZERO).multiply(q);
            sumItemCost = sumItemCost.add(itemCost);
            Long cid = custByOrder.get(oid);
            if (cid != null) {
                profitByCustomer.merge(cid, a.subtract(itemCost), BigDecimal::add);
                qtyByCustomer.merge(cid, q, BigDecimal::add);
                amtByCustomer.merge(cid, a, BigDecimal::add);
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
            // 产品利润（产品利润饼图用）= 明细金额 − 明细数量 × 产品移动加权成本价（成本口径 B）
            m.put("profit", amtByProduct.getOrDefault(pid, ZERO)
                    .subtract(costPrice.getOrDefault(pid, ZERO).multiply(qtyByProduct.getOrDefault(pid, ZERO))));
            byProduct.add(m);
        }
        byProduct.sort((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")));

        // （原「④ 仓库分布」已于 2026-09-21 删除：销售分析页的「仓库销售分布」卡片按用户要求改为
        //   「产品销售利润排行」，byWarehouse 遂无任何消费者 —— 全仓仅本页引用、无验证脚本引用。
        //   ⚠️ `records()` 的 warehouseId 过滤能力与其所需的 warehouse_id/warehouse_name 仍保留，
        //   故 SaleAnalysisMapper.saleOrderRecords() 的 SELECT 字段不变。）

        // ④ 6 项指标 + 饼图数据（2026-09-15 用户要求：原柱状图 → 6 个饼图；前 8 名 + 「其它」由前端合并）
        //    客户退货率/换货率饼图 = 各客户「退货额/换货额 ÷ 总退货额/总换货额」的占比（标题显示总比率）
        //    ⚠️ 换货金额口径 = **明细「换出金额」Σ out_amount**（换货单主表 total_amount 后端从不回写、恒为 0，
        //       原取该列会导致换货率永远 0、饼图空图；2026-09-15 修正，退货仍取 sale_return.total_amount）
        BigDecimal sumExchange = ZERO, sumExchangeQty = ZERO;
        String fromDay = s.toString(), toDay = e.toString();
        Map<String, BigDecimal> exchByDay = new HashMap<>(), exchQtyByDay = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.saleExchangeByDay()) {
            exchByDay.put(str(r.get("d")), toBd(r.get("amt")));
            exchQtyByDay.put(str(r.get("d")), toBd(r.get("qty")));
        }
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
            sumExchange = sumExchange.add(exchByDay.getOrDefault(d.toString(), ZERO));
            sumExchangeQty = sumExchangeQty.add(exchQtyByDay.getOrDefault(d.toString(), ZERO));
        }
        // 退货**件数**分子（2026-09-15 加「金额/件数」switch）：金额分子 sumReturn 已在上面按 sale_return.total_amount 算好
        BigDecimal sumReturnQty = ZERO;
        Map<String, BigDecimal> retQtyByDay = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.saleReturnQtyByDay()) {
            retQtyByDay.put(str(r.get("d")), toBd(r.get("qty")));
        }
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
            sumReturnQty = sumReturnQty.add(retQtyByDay.getOrDefault(d.toString(), ZERO));
        }
        Long topCid = null;
        BigDecimal topProfit = ZERO;
        for (Map.Entry<Long, BigDecimal> en : profitByCustomer.entrySet()) {
            if (topCid == null || en.getValue().compareTo(topProfit) > 0) {
                topCid = en.getKey();
                topProfit = en.getValue();
            }
        }
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("productQuantity", sumQty);                          // 产品销量合计（件）
        metrics.put("productProfit", sumItemAmt.subtract(sumItemCost));   // 产品利润合计（元，口径 B）
        metrics.put("customerCount", custNameMap.size());                // 下单客户数（个）
        metrics.put("topCustomerName", topCid == null ? "" : custNameMap.getOrDefault(topCid, ""));
        metrics.put("topCustomerProfit", topProfit);                     // 利润最高的客户（保留供其它处复用）
        metrics.put("returnRate", rate(sumReturn, sumAmount));           // 总退货率（%·金额口径）
        metrics.put("exchangeRate", rate(sumExchange, sumAmount));       // 总换货率（%·金额口径）
        // 件数口径费率（2026-09-15 用户要求：退货率/换货率可按「金额 / 件数」切换）= 件数 ÷ **销售件数**（sumQty）
        metrics.put("returnRateQty", rate(sumReturnQty, sumQty));        // 总退货率（%·件数口径）
        metrics.put("exchangeRateQty", rate(sumExchangeQty, sumQty));    // 总换货率（%·件数口径）

        // 客户维度分片（客户销量 / 客户利润 饼图）
        List<Map<String, Object>> byCustomer = new ArrayList<>();
        for (Long cid : custNameMap.keySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("customerId", cid);
            m.put("customerName", custNameMap.get(cid));
            m.put("quantity", qtyByCustomer.getOrDefault(cid, ZERO));
            m.put("amount", amtByCustomer.getOrDefault(cid, ZERO));
            m.put("profit", profitByCustomer.getOrDefault(cid, ZERO));
            byCustomer.add(m);
        }
        byCustomer.sort((a, b) -> ((BigDecimal) b.get("quantity")).compareTo((BigDecimal) a.get("quantity")));

        // 各客户退货额 / 换货额（客户退货率 / 客户换货率 饼图）；区间过滤在 Java 侧，与全站做法一致
        Map<Long, BigDecimal> retByCustomer = new HashMap<>();
        Map<Long, String> retNameMap = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.saleReturnByCustomerDay()) {
            String d = str(r.get("d"));
            if (d.compareTo(fromDay) < 0 || d.compareTo(toDay) > 0 || r.get("customer_id") == null) continue;
            Long cid = toBd(r.get("customer_id")).longValue();
            retByCustomer.merge(cid, toBd(r.get("amt")), BigDecimal::add);
            retNameMap.put(cid, str(r.get("customer_name")));
        }
        Map<Long, BigDecimal> exchByCustomer = new HashMap<>();
        Map<Long, BigDecimal> exchQtyByCustomer = new HashMap<>();
        Map<Long, String> exchNameMap = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.saleExchangeByCustomerDay()) {
            String d = str(r.get("d"));
            if (d.compareTo(fromDay) < 0 || d.compareTo(toDay) > 0 || r.get("customer_id") == null) continue;
            Long cid = toBd(r.get("customer_id")).longValue();
            exchByCustomer.merge(cid, toBd(r.get("amt")), BigDecimal::add);
            exchQtyByCustomer.merge(cid, toBd(r.get("qty")), BigDecimal::add);   // 件数口径（换出件数）
            exchNameMap.put(cid, str(r.get("customer_name")));
        }
        // 退货**件数**分片（2026-09-15 件数口径）：与金额口径是两条独立查询
        // —— 金额那条查的是头部 `total_amount`，若 join 明细后 SUM 会被明细行数放大，故不合并
        Map<Long, BigDecimal> retQtyByCustomer = new HashMap<>();
        Map<Long, String> retQtyNameMap = new HashMap<>();
        for (Map<String, Object> r : analysisMapper.saleReturnQtyByCustomerDay()) {
            String d = str(r.get("d"));
            if (d.compareTo(fromDay) < 0 || d.compareTo(toDay) > 0 || r.get("customer_id") == null) continue;
            Long cid = toBd(r.get("customer_id")).longValue();
            retQtyByCustomer.merge(cid, toBd(r.get("qty")), BigDecimal::add);
            retQtyNameMap.put(cid, str(r.get("customer_name")));
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("start", s.toString());
        res.put("end", e.toString());
        res.put("summary", summary);
        res.put("metrics", metrics);
        res.put("byProduct", byProduct);                                        // 产品销量/产品利润 饼图（含 profit）
        res.put("byCustomer", byCustomer);                                      // 客户销量/客户利润 饼图
        res.put("returnByCustomer", toPieList(retByCustomer, retNameMap));      // 客户退货率 饼图（各客户退货额·金额口径）
        res.put("exchangeByCustomer", toPieList(exchByCustomer, exchNameMap));  // 客户换货率 饼图（各客户换货额·金额口径）
        // 件数口径分片（2026-09-15 用户要求：两张卡各自 switch 切「金额 / 件数」；
        // 一次取数返回两套，前端切换不重发请求）
        res.put("returnByCustomerQty", toPieList(retQtyByCustomer, retQtyNameMap));      // 客户退货率 饼图（各客户退货件数）
        res.put("exchangeByCustomerQty", toPieList(exchQtyByCustomer, exchNameMap));     // 客户换货率 饼图（各客户换出件数）
        res.put("dates", dates);
        res.put("amounts", amounts);
        res.put("returns", returns);
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
