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
    public R<IPage<Map<String, Object>>> page(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long customerId,
                                              @RequestParam(required = false) String code,
                                              @RequestParam(required = false) Long saleOrderId,
                                              @RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(status, customerId, code, saleOrderId, pageNum, pageSize));
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

    @GetMapping("/sale-orders")
    public R<List<Map<String, Object>>> saleOrders(@RequestParam(required = false) Long customerId) {
        return R.ok(service.saleOrders(customerId));
    }

    @GetMapping("/sale-order-items")
    public R<List<Map<String, Object>>> saleOrderItems(@RequestParam(required = false) Long saleOrderId) {
        return R.ok(service.saleOrderItems(saleOrderId));
    }

    @PutMapping("/{id}/audit")
    public R<String> audit(@PathVariable Long id) {
        service.audit(id);
        return R.ok("审核成功");
    }

    // E1 口径（2026-09-12）：反审核统一 /un-audit，旧路径 /unaudit 保留为别名
    @PutMapping({"/{id}/un-audit", "/{id}/unaudit"})
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
        if (body.get("saleOrderId") != null) order.setSaleOrderId(Long.valueOf(body.get("saleOrderId").toString()));
        if (body.get("saleOrderCode") != null) order.setSaleOrderCode(body.get("saleOrderCode").toString());
        if (body.get("returnDate") != null) order.setReturnDate(java.time.LocalDate.parse(body.get("returnDate").toString()));
        if (body.get("remark") != null) order.setRemark(body.get("remark").toString());
        if (body.get("totalAmount") != null) order.setTotalAmount(new java.math.BigDecimal(body.get("totalAmount").toString()));
        if (body.get("lossAmount") != null) order.setLossAmount(new java.math.BigDecimal(body.get("lossAmount").toString()));
        // 收费（与换货单一致：chargeFlag 控制，金额手工填写，审核后生成 -FEE 正向应收）
        if (body.get("chargeFlag") != null) order.setChargeFlag(Integer.valueOf(body.get("chargeFlag").toString()));
        if (body.get("chargeType") != null) order.setChargeType(body.get("chargeType").toString());
        if (body.get("chargeAmount") != null) order.setChargeAmount(new java.math.BigDecimal(body.get("chargeAmount").toString()));
        if (body.get("chargeReason") != null) order.setChargeReason(body.get("chargeReason").toString());
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
