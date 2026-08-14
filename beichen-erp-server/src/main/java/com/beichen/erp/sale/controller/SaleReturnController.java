package com.beichen.erp.sale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.beichen.erp.common.R;
import com.beichen.erp.sale.entity.SaleReturn;
import com.beichen.erp.sale.entity.SaleReturnItem;
import com.beichen.erp.sale.service.SaleReturnService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 销售退货单接口
 */
@RestController
@RequestMapping("/api/sale/return")
public class SaleReturnController {

    private final SaleReturnService service;

    public SaleReturnController(SaleReturnService service) {
        this.service = service;
    }

    @GetMapping("/page")
    public R<IPage<Map<String, Object>>> page(@RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) Long customerId,
                                              @RequestParam(required = false) String code,
                                              @RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(status, customerId, code, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<SaleReturn> detail(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<SaleReturnItem>> items(@PathVariable Long id) {
        return R.ok(service.getItems(id));
    }

    @PostMapping
    public R<SaleReturn> create(@RequestBody Map<String, Object> body) {
        SaleReturn order = extractOrder(body);
        List<Map<String, Object>> items = extractItems(body);
        return R.ok(service.create(order, items));
    }

    @PutMapping("/{id}")
    public R<SaleReturn> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SaleReturn order = extractOrder(body);
        List<Map<String, Object>> items = extractItems(body);
        return R.ok(service.update(id, order, items));
    }

    @PutMapping("/{id}/audit")
    public R<String> audit(@PathVariable Long id) {
        service.audit(id);
        return R.ok("审核成功");
    }

    @PutMapping("/{id}/unaudit")
    public R<String> unAudit(@PathVariable Long id) {
        service.unAudit(id);
        return R.ok("反审核成功");
    }

    @PutMapping("/{id}/cancel")
    public R<String> cancel(@PathVariable Long id) {
        service.cancel(id);
        return R.ok("作废成功");
    }

    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok("删除成功");
    }

    private SaleReturn extractOrder(Map<String, Object> body) {
        SaleReturn order = new SaleReturn();
        if (body.get("id") != null) order.setId(Long.valueOf(body.get("id").toString()));
        if (body.get("code") != null) order.setCode(body.get("code").toString());
        if (body.get("customerId") != null) order.setCustomerId(Long.valueOf(body.get("customerId").toString()));
        if (body.get("warehouseId") != null) order.setWarehouseId(Long.valueOf(body.get("warehouseId").toString()));
        if (body.get("returnDate") != null) order.setReturnDate(java.time.LocalDate.parse(body.get("returnDate").toString()));
        if (body.get("remark") != null) order.setRemark(body.get("remark").toString());
        if (body.get("totalAmount") != null) order.setTotalAmount(new java.math.BigDecimal(body.get("totalAmount").toString()));
        return order;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Map<String, Object> body) {
        Object items = body.get("items");
        if (items instanceof List) {
            return (List<Map<String, Object>>) items;
        }
        return List.of();
    }
}
