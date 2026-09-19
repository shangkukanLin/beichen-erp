package com.beichen.erp.sale.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.finance.service.FinanceReceiptService;
import com.beichen.erp.sale.entity.SaleOrder;
import com.beichen.erp.sale.entity.SaleOrderItem;
import com.beichen.erp.sale.service.SaleExchangeService;
import com.beichen.erp.sale.service.SaleOrderService;
import com.beichen.erp.sale.service.SaleReturnService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/sale")
@RequiredArgsConstructor
public class SaleOrderController {

    private final SaleOrderService service;
    // 期 2（2026-09-19 读隔离）：详情页的关联摘要下沉到本页接口，不再去读别人的接口
    private final SaleReturnService saleReturnService;
    private final SaleExchangeService saleExchangeService;
    private final FinanceReceiptService financeReceiptService;
    /** 必须用 Spring 容器里的 ObjectMapper（含 JavaTimeModule，见 SystemController 的说明），不要手拼 Map */
    private final ObjectMapper objectMapper;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String code,
            // 单据日期区间（yyyy-MM-dd，可选）：首页「当日销售单」传 startDate=endDate=今天，取**全量**当日单
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(status, customerId, code, startDate, endDate, pageNum, pageSize));
    }

    /**
     * 详情（期 2·2026-09-19 读隔离：**关联摘要随详情一并返回**）。
     *
     * <p>原先销售单详情页要分别读三个**别的页面**的接口：收款单 {@code /api/finance/receipt/by-source}、
     * 退货单 {@code /api/sale/return/page}、换货单 {@code /api/sale/exchange/page} —— 只被授予
     * {@code sale:order} 的用户直调它们会 403（页面看得见、数据读不出）。现全部改由本页接口返回：
     * 响应体 = 实体原字段（JSON 路径不变，前端 {@code head.xxx} 零改动）+ 追加
     * {@code receipts / returns / exchanges}。</p>
     */
    @GetMapping("/{id}")
    public R<Map<String, Object>> getById(@PathVariable Long id) {
        SaleOrder o = service.getById(id);
        if (o == null) return R.ok(Map.of());
        Map<String, Object> m = objectMapper.convertValue(o, new TypeReference<Map<String, Object>>() {});
        // 本单自动生成的收款单（销售单现金结算联动；含已作废，与 /finance/receipt/by-source 同口径）
        m.put("receipts", financeReceiptService.findBySource("SALE_ORDER", id));
        // 本单发起的退货单 / 换货单（原前端取 pageSize=100，此处同口径）
        m.put("returns", saleReturnService.page(null, null, null, id, 1, 100).getRecords());
        m.put("exchanges", saleExchangeService.page(1, 100, Map.of("saleOrderId", id)).getRecords());
        return R.ok(m);
    }

    @GetMapping("/{id}/items")
    public R<List<SaleOrderItem>> getItems(@PathVariable Long id) { return R.ok(service.getItems(id)); }

    @PostMapping
    public R<Long> create(@RequestBody Map<String, Object> body) {
        SaleOrder o = parseOrder(body);
        service.create(o, parseItems(body));
        // 返回新单ID，避免前端"创建后再查列表取ID"导致并发下取错
        return R.ok(o.getId());
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SaleOrder order = parseOrder(body);
        order.setId(id);
        service.update(order, parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) { service.audit(id); return R.ok(); }

    @PutMapping("/{id}/un-audit")
    public R<Void> unAudit(@PathVariable Long id) { service.unAudit(id); return R.ok(); }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) { service.cancel(id); return R.ok(); }

    /** 库存检查：传入 warehouseId 和 items，返回各物料的库存对比 */
    @PostMapping("/check-stock")
    public R<List<Map<String, Object>>> checkStock(@RequestBody Map<String, Object> body) {
        Long warehouseId = body.get("warehouseId") != null ? Long.valueOf(body.get("warehouseId").toString()) : null;
        List<SaleOrderItem> items = parseItems(body);
        return R.ok(service.checkStock(warehouseId, items));
    }

    @SuppressWarnings("unchecked")
    private SaleOrder parseOrder(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("order") ? (Map<String, Object>) body.get("order") : body;
        SaleOrder o = new SaleOrder();
        if (d.get("customerId") != null) o.setCustomerId(Long.valueOf(d.get("customerId").toString()));
        if (d.get("warehouseId") != null) o.setWarehouseId(Long.valueOf(d.get("warehouseId").toString()));
        if (d.get("orderDate") != null && !d.get("orderDate").toString().isBlank())
            o.setOrderDate(LocalDate.parse(d.get("orderDate").toString()));
        if (d.get("taxIncluded") != null) o.setTaxIncluded(Integer.valueOf(d.get("taxIncluded").toString()));
        if (d.get("taxRate") != null && !d.get("taxRate").toString().isBlank())
            o.setTaxRate(new BigDecimal(d.get("taxRate").toString()));
        // 结算方式（2026-09-18 按单记）：settleType 空/非法由 Service 归一为 CREDIT（账期）
        if (d.get("settleType") != null) o.setSettleType(d.get("settleType").toString());
        if (d.get("settleAccountId") != null && !d.get("settleAccountId").toString().isBlank())
            o.setSettleAccountId(Long.valueOf(d.get("settleAccountId").toString()));
        o.setRemark((String) d.get("remark"));
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<SaleOrderItem> parseItems(Map<String, Object> body) {
        List<SaleOrderItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    SaleOrderItem it = new SaleOrderItem();
                    if (map.get("productId") != null) it.setProductId(Long.valueOf(map.get("productId").toString()));
                    if (map.get("quantity") != null && !map.get("quantity").toString().isBlank())
                        it.setQuantity(new BigDecimal(map.get("quantity").toString()));
                    if (map.get("unitPrice") != null && !map.get("unitPrice").toString().isBlank())
                        it.setUnitPrice(new BigDecimal(map.get("unitPrice").toString()));
                    it.setRemark((String) map.get("remark"));
                    it.setQualityType((String) map.get("qualityType"));
                    list.add(it);
                }
            }
        }
        return list;
    }
}
