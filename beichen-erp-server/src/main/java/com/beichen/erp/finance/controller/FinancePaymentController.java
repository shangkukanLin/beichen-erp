package com.beichen.erp.finance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.finance.entity.FinancePayment;
import com.beichen.erp.finance.entity.FinancePaymentItem;
import com.beichen.erp.finance.service.FinancePaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/payment")
@RequiredArgsConstructor
public class FinancePaymentController {

    private final FinancePaymentService service;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(supplierId, status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<FinancePayment> getById(@PathVariable Long id) { return R.ok(service.getById(id)); }

    @GetMapping("/{id}/items")
    public R<List<FinancePaymentItem>> getItems(@PathVariable Long id) { return R.ok(service.getItems(id)); }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parsePayment(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) { service.audit(id); return R.ok(); }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) { service.cancel(id); return R.ok(); }

    /** 更新付款凭证 */
    @PutMapping("/{id}/attach")
    public R<Void> updateAttach(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        FinancePayment u = new FinancePayment();
        u.setId(id);
        u.setAttachUrl(body.get("attachUrl") != null ? body.get("attachUrl").toString() : "");
        service.updateAttach(u);
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    private FinancePayment parsePayment(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("payment") ? (Map<String, Object>) body.get("payment") : body;
        FinancePayment r = new FinancePayment();
        if (d.get("supplierId") != null) r.setSupplierId(Long.valueOf(d.get("supplierId").toString()));
        if (d.get("accountId") != null) r.setAccountId(Long.valueOf(d.get("accountId").toString()));
        if (d.get("paymentDate") != null && !d.get("paymentDate").toString().isBlank())
            r.setPaymentDate(LocalDate.parse(d.get("paymentDate").toString()));
        r.setRemark((String) d.get("remark"));
        r.setAttachUrl((String) d.get("attachUrl"));
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<FinancePaymentItem> parseItems(Map<String, Object> body) {
        List<FinancePaymentItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) for (Object o : raw) if (o instanceof Map<?,?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            FinancePaymentItem it = new FinancePaymentItem();
            if (map.get("payableId") != null) it.setPayableId(Long.valueOf(map.get("payableId").toString()));
            it.setPayableBillNo((String) map.get("payableBillNo"));
            if (map.get("thisAmount") != null && !map.get("thisAmount").toString().isBlank())
                it.setThisAmount(new BigDecimal(map.get("thisAmount").toString()));
            it.setRemark((String) map.get("remark"));
            list.add(it);
        }
        return list;
    }
}
