package com.beichen.erp.purchase.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.purchase.entity.PurchaseOrder;
import com.beichen.erp.purchase.entity.PurchaseReturn;
import com.beichen.erp.purchase.entity.PurchaseReturnItem;
import com.beichen.erp.purchase.service.PurchaseOrderService;
import com.beichen.erp.purchase.service.PurchaseReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/purchase-return")
@RequiredArgsConstructor
public class PurchaseReturnController {

    private final PurchaseReturnService service;
    // 期 3（2026-09-19 读隔离）：退货页要读「来源采购单」，改由本页接口提供
    private final PurchaseOrderService purchaseOrderService;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.page(status, supplierId, code, pageNum, pageSize));
    }

    @PostMapping
    public R<PurchaseReturn> create(@RequestBody Map<String, Object> body) {
        PurchaseReturn order = parseOrder(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        return R.ok(service.create(order, items));
    }

    @PutMapping("/{id}")
    public R<PurchaseReturn> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        PurchaseReturn order = parseOrder(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        return R.ok(service.update(id, order, items));
    }

    @GetMapping("/{id}")
    public R<PurchaseReturn> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<PurchaseReturnItem>> items(@PathVariable Long id) {
        return R.ok(service.getItems(id));
    }

    /**
     * 来源采购单（退货页「从采购单带入明细」时反查供货商 / 退货仓库 / 单号）。
     * <p>期 3（2026-09-19 读隔离）：原先退货页直读 {@code /api/inventory/purchase/{id}}
     * （需 {@code purchase:order}）⇒ 只被授予 {@code purchase:return} 的用户会 403。现走本页前缀，
     * 返回体与原采购单详情**逐字段一致**（同一实体，前端取值零改动）。</p>
     */
    @GetMapping("/source-order")
    public R<PurchaseOrder> sourceOrder(@RequestParam Long purchaseOrderId) {
        return R.ok(purchaseOrderService.getById(purchaseOrderId));
    }

    /** 按采购单查询关联的退货单列表 */
    @GetMapping("/by-order")
    public R<List<Map<String, Object>>> byOrder(@RequestParam(required = false) Long purchaseOrderId) {
        return R.ok(service.byOrder(purchaseOrderId));
    }

    /** 查询采购单明细（含已退/可退数量，供退货带入） */
    @GetMapping("/purchase-order-items")
    public R<List<Map<String, Object>>> purchaseOrderItems(@RequestParam(required = false) Long purchaseOrderId) {
        return R.ok(service.purchaseOrderItems(purchaseOrderId));
    }

    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) {
        service.audit(id);
        return R.ok();
    }

    @PutMapping("/{id}/un-audit")
    public R<Void> unAudit(@PathVariable Long id) {
        service.unAudit(id);
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    private PurchaseReturn parseOrder(Map<String, Object> body) {
        PurchaseReturn o = new PurchaseReturn();
        if (body.get("id") != null) o.setId(Long.valueOf(body.get("id").toString()));
        if (body.get("supplierId") != null) o.setSupplierId(Long.valueOf(body.get("supplierId").toString()));
        if (body.get("warehouseId") != null) o.setWarehouseId(Long.valueOf(body.get("warehouseId").toString()));
        if (body.get("purchaseOrderId") != null) o.setPurchaseOrderId(Long.valueOf(body.get("purchaseOrderId").toString()));
        if (body.get("purchaseOrderCode") != null) o.setPurchaseOrderCode(body.get("purchaseOrderCode").toString());
        if (body.get("returnDate") != null) o.setReturnDate(java.time.LocalDate.parse(body.get("returnDate").toString()));
        if (body.get("totalAmount") != null) o.setTotalAmount(new java.math.BigDecimal(body.get("totalAmount").toString()));
        if (body.get("remark") != null) o.setRemark(body.get("remark").toString());
        return o;
    }
}
