package com.beichen.erp.purchase.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.beichen.erp.common.R;
import com.beichen.erp.purchase.entity.PurchaseExchange;
import com.beichen.erp.purchase.service.PurchaseExchangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购换货单接口（进货业务，同品换货，强关联采购单；2026-09-18 新增）
 */
@RestController
@RequestMapping("/api/inventory/purchase-exchange")
@RequiredArgsConstructor
public class PurchaseExchangeController {

    private final PurchaseExchangeService exchangeService;

    /** 分页列表 */
    @GetMapping("/page")
    public R<IPage<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize,
                                              @RequestParam Map<String, Object> q) {
        return R.ok(exchangeService.page(pageNum, pageSize, q));
    }

    /** 详情（含明细） */
    @GetMapping("/{id}")
    public R<Map<String, Object>> getById(@PathVariable Long id) {
        PurchaseExchange e = exchangeService.getById(id);
        if (e == null) return R.fail("换货单不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("head", e);
        m.put("items", exchangeService.getItems(id));
        return R.ok(m);
    }

    /** 来源采购单明细（换货选单用，含已购/已退/已换/可换数量） */
    @GetMapping("/purchase-order-items")
    public R<List<Map<String, Object>>> purchaseOrderItems(@RequestParam(required = false) Long purchaseOrderId) {
        return R.ok(exchangeService.purchaseOrderItems(purchaseOrderId));
    }

    /** 来源采购单下拉（仅已审核），供新增页选择来源采购单 */
    @GetMapping("/purchase-orders")
    public R<List<Map<String, Object>>> purchaseOrders(@RequestParam(required = false) Long supplierId,
                                                       @RequestParam(required = false) String kw) {
        return R.ok(exchangeService.purchaseOrders(supplierId, kw));
    }

    /** 新增 */
    @PostMapping
    public R<PurchaseExchange> create(@RequestBody Map<String, Object> body) {
        return R.ok(exchangeService.create(extract(body), extractItems(body)));
    }

    /** 修改 */
    @PutMapping
    public R<PurchaseExchange> update(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") != null && !body.get("id").toString().isBlank()
                ? Long.valueOf(body.get("id").toString()) : null;
        if (id == null) return R.fail("换货单ID不能为空");
        return R.ok(exchangeService.update(id, extract(body), extractItems(body)));
    }

    /** 审核：退回出库 + 换入入库 + 生成两条应付台账（退回负 / 换入正） */
    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) {
        exchangeService.audit(id);
        return R.ok();
    }

    /** 反审核：对称回滚，回到草稿 */
    @PutMapping({"/{id}/un-audit", "/{id}/unaudit"})
    public R<Void> unAudit(@PathVariable Long id) {
        exchangeService.unAudit(id);
        return R.ok();
    }

    /** 作废（仅草稿）：单据留痕，不做物理删除 */
    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        exchangeService.cancel(id);
        return R.ok();
    }

    /** 兼容旧接口：语义等同作废 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        exchangeService.delete(id);
        return R.ok();
    }

    // ==================== 参数解析 ====================

    private PurchaseExchange extract(Map<String, Object> b) {
        PurchaseExchange e = new PurchaseExchange();
        if (b.get("id") != null && !b.get("id").toString().isBlank())
            e.setId(Long.valueOf(b.get("id").toString()));
        if (b.get("supplierId") != null && !b.get("supplierId").toString().isBlank())
            e.setSupplierId(Long.valueOf(b.get("supplierId").toString()));
        if (b.get("purchaseOrderId") != null && !b.get("purchaseOrderId").toString().isBlank())
            e.setPurchaseOrderId(Long.valueOf(b.get("purchaseOrderId").toString()));
        if (b.get("purchaseOrderCode") != null) e.setPurchaseOrderCode(b.get("purchaseOrderCode").toString());
        if (b.get("warehouseOutId") != null && !b.get("warehouseOutId").toString().isBlank())
            e.setWarehouseOutId(Long.valueOf(b.get("warehouseOutId").toString()));
        if (b.get("warehouseInId") != null && !b.get("warehouseInId").toString().isBlank())
            e.setWarehouseInId(Long.valueOf(b.get("warehouseInId").toString()));
        if (b.get("exchangeDate") != null && !b.get("exchangeDate").toString().isBlank())
            e.setExchangeDate(LocalDate.parse(b.get("exchangeDate").toString()));
        if (b.get("totalReturnAmount") != null && !b.get("totalReturnAmount").toString().isBlank())
            e.setTotalReturnAmount(new BigDecimal(b.get("totalReturnAmount").toString()));
        if (b.get("totalInAmount") != null && !b.get("totalInAmount").toString().isBlank())
            e.setTotalInAmount(new BigDecimal(b.get("totalInAmount").toString()));
        if (b.get("remark") != null) e.setRemark(b.get("remark").toString());
        return e;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Map<String, Object> b) {
        Object items = b.get("items");
        if (items instanceof List<?> list) {
            List<Map<String, Object>> res = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) res.add((Map<String, Object>) m);
            }
            return res;
        }
        return Collections.emptyList();
    }
}
