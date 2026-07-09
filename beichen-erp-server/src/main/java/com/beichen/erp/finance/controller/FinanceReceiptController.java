package com.beichen.erp.finance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.finance.entity.FinanceReceipt;
import com.beichen.erp.finance.entity.FinanceReceiptItem;
import com.beichen.erp.finance.service.FinanceReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/receipt")
@RequiredArgsConstructor
public class FinanceReceiptController {

    private final FinanceReceiptService service;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(customerId, status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<FinanceReceipt> getById(@PathVariable Long id) { return R.ok(service.getById(id)); }

    @GetMapping("/{id}/items")
    public R<List<FinanceReceiptItem>> getItems(@PathVariable Long id) { return R.ok(service.getItems(id)); }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parseReceipt(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) { service.audit(id); return R.ok(); }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) { service.cancel(id); return R.ok(); }

    @SuppressWarnings("unchecked")
    private FinanceReceipt parseReceipt(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("receipt") ? (Map<String, Object>) body.get("receipt") : body;
        FinanceReceipt r = new FinanceReceipt();
        if (d.get("customerId") != null) r.setCustomerId(Long.valueOf(d.get("customerId").toString()));
        if (d.get("accountId") != null) r.setAccountId(Long.valueOf(d.get("accountId").toString()));
        if (d.get("receiptDate") != null && !d.get("receiptDate").toString().isBlank())
            r.setReceiptDate(LocalDate.parse(d.get("receiptDate").toString()));
        r.setRemark((String) d.get("remark"));
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<FinanceReceiptItem> parseItems(Map<String, Object> body) {
        List<FinanceReceiptItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) for (Object o : raw) if (o instanceof Map<?,?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            FinanceReceiptItem it = new FinanceReceiptItem();
            if (map.get("receivableId") != null) it.setReceivableId(Long.valueOf(map.get("receivableId").toString()));
            it.setReceivableBillNo((String) map.get("receivableBillNo"));
            if (map.get("thisAmount") != null && !map.get("thisAmount").toString().isBlank())
                it.setThisAmount(new BigDecimal(map.get("thisAmount").toString()));
            it.setRemark((String) map.get("remark"));
            list.add(it);
        }
        return list;
    }
}
