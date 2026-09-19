package com.beichen.erp.sale.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.sale.entity.ReturnSort;
import com.beichen.erp.sale.entity.ReturnSortItem;
import com.beichen.erp.sale.service.ReturnSortService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 退货整理（销售退货分选入库）
 * <p>路由前缀: /api/inventory/return-sort</p>
 */
@RestController
@RequestMapping("/api/inventory/return-sort")
@RequiredArgsConstructor
public class ReturnSortController {

    private final ReturnSortService service;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(status, warehouseId, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<ReturnSort> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<ReturnSortItem>> getItems(@PathVariable Long id) {
        return R.ok(service.getItems(id));
    }

    /** 售后仓不良品库存清单（新增时带出） */
    @GetMapping("/defect-stock")
    public R<List<Map<String, Object>>> defectStock(@RequestParam Long warehouseId) {
        return R.ok(service.defectStock(warehouseId));
    }

    /**
     * 待整理总览（2026-09-19 退货整理页优化）：跨**自有成品仓**列出待整理批次与账实差额。
     * <p>三态：SORTABLE 可整理 · SHORTAGE 实物不足（账实不符，需盘库）· CLEARED 已整理完
     * （默认不返回，includeCleared=true 时返回）。</p>
     */
    @GetMapping("/pending-overview")
    public R<Map<String, Object>> pendingOverview(@RequestParam(defaultValue = "false") boolean includeCleared) {
        return R.ok(service.pendingOverview(includeCleared));
    }

    /**
     * 批量生成整理草稿：勾选多个来源批次，按 (仓库, 客户) 分组各生成一张草稿
     * （一张整理单只能对应一个客户，折损收款才有唯一对象）。
     *
     * <p>body: {@code { pendingIds:[...], sortDate, defaultQuality:'A', targetWarehouseA/B/C/Defect,
     * lossAmount, lossRemark, remark }}；**不接受 warehouseId**（源仓库由勾选的批次决定）。</p>
     */
    @PostMapping("/batch-draft")
    public R<Map<String, Object>> batchDraft(@RequestBody Map<String, Object> body) {
        List<Long> pendingIds = new ArrayList<>();
        Object obj = body.get("pendingIds");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o != null && !o.toString().isBlank()) pendingIds.add(Long.valueOf(o.toString()));
            }
        }
        String quality = body.get("defaultQuality") != null ? body.get("defaultQuality").toString() : null;
        return R.ok(service.batchCreateDrafts(pendingIds, parseSort(body), quality));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parseSort(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ReturnSort s = parseSort(body);
        s.setId(id);
        service.update(s, parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) {
        service.audit(id);
        return R.ok();
    }

    /**
     * 反审核（E2 口径 · 2026-09-12）：退货整理的 cancel **本就是"反审核"语义**（AUDITED → DRAFT），
     * 这里补上 canonical 路径 /un-audit；/cancel 保留为兼容别名。
     * 注意：草稿的"删除"是独立的 DELETE /{id}，故本端点不存在语义重载。
     */
    @PutMapping({"/{id}/un-audit", "/{id}/cancel"})
    public R<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    private ReturnSort parseSort(Map<String, Object> body) {
        ReturnSort s = new ReturnSort();
        if (body.get("warehouseId") != null) s.setWarehouseId(Long.valueOf(body.get("warehouseId").toString()));
        if (body.get("sortDate") != null && !body.get("sortDate").toString().isBlank())
            s.setSortDate(LocalDate.parse(body.get("sortDate").toString()));
        if (body.get("targetWarehouseA") != null) s.setTargetWarehouseA(Long.valueOf(body.get("targetWarehouseA").toString()));
        if (body.get("targetWarehouseB") != null) s.setTargetWarehouseB(Long.valueOf(body.get("targetWarehouseB").toString()));
        if (body.get("targetWarehouseC") != null) s.setTargetWarehouseC(Long.valueOf(body.get("targetWarehouseC").toString()));
        if (body.get("targetWarehouseDefect") != null) s.setTargetWarehouseDefect(Long.valueOf(body.get("targetWarehouseDefect").toString()));
        if (body.get("lossAmount") != null && !body.get("lossAmount").toString().isBlank())
            s.setLossAmount(new BigDecimal(body.get("lossAmount").toString()));
        if (body.get("lossRemark") != null) s.setLossRemark(body.get("lossRemark").toString());
        s.setRemark((String) body.get("remark"));
        return s;
    }

    @SuppressWarnings("unchecked")
    private List<ReturnSortItem> parseItems(Map<String, Object> body) {
        List<ReturnSortItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    ReturnSortItem it = new ReturnSortItem();
                    if (map.get("productId") != null) it.setProductId(Long.valueOf(map.get("productId").toString()));
                    if (map.get("pendingId") != null && !map.get("pendingId").toString().isBlank())
                        it.setPendingId(Long.valueOf(map.get("pendingId").toString()));
                    if (map.get("saleReturnItemId") != null) it.setSaleReturnItemId(Long.valueOf(map.get("saleReturnItemId").toString()));
                    it.setProductName((String) map.get("productName"));
                    it.setUnit((String) map.get("unit"));
                    if (map.get("totalQuantity") != null && !map.get("totalQuantity").toString().isBlank())
                        it.setTotalQuantity(new BigDecimal(map.get("totalQuantity").toString()));
                    if (map.get("qtyA") != null && !map.get("qtyA").toString().isBlank())
                        it.setQtyA(new BigDecimal(map.get("qtyA").toString()));
                    if (map.get("qtyB") != null && !map.get("qtyB").toString().isBlank())
                        it.setQtyB(new BigDecimal(map.get("qtyB").toString()));
                    if (map.get("qtyC") != null && !map.get("qtyC").toString().isBlank())
                        it.setQtyC(new BigDecimal(map.get("qtyC").toString()));
                    if (map.get("qtyDefect") != null && !map.get("qtyDefect").toString().isBlank())
                        it.setQtyDefect(new BigDecimal(map.get("qtyDefect").toString()));
                    it.setRemark((String) map.get("remark"));
                    list.add(it);
                }
            }
        }
        return list;
    }
}
