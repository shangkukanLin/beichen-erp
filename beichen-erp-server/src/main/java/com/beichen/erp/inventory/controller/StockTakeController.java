package com.beichen.erp.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.inventory.entity.InventoryStockTake;
import com.beichen.erp.inventory.entity.InventoryStockTakeItem;
import com.beichen.erp.inventory.service.StockTakeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 库存盘点 Controller（库存管理 → 库存盘点）
 * <p>路由前缀: /api/inventory/stock-take</p>
 */
@RestController
@RequestMapping("/api/inventory/stock-take")
@RequiredArgsConstructor
public class StockTakeController {

    private final StockTakeService service;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(@RequestParam(required = false) Long warehouseId,
                                             @RequestParam(required = false) String period,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String scope,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(warehouseId, period, status, scope, pageNum, pageSize));
    }

    /** 盘点看板：各仓库当月盘点状态与超期天数（scope 过滤成品/物料范围） */
    @GetMapping("/status")
    public R<List<Map<String, Object>>> status(@RequestParam(required = false) String scope) {
        return R.ok(service.takeStatus(scope));
    }

    @GetMapping("/{id}")
    public R<InventoryStockTake> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<InventoryStockTakeItem>> items(@PathVariable Long id) {
        return R.ok(service.getItems(id));
    }

    /** 新建盘点单（按仓库+月份自动带出账面库存明细；scope=MATERIAL 时仅跟单专员可建） */
    @PostMapping
    public R<InventoryStockTake> create(@RequestBody Map<String, Object> body) {
        Long warehouseId = body.get("warehouseId") == null ? null : Long.valueOf(body.get("warehouseId").toString());
        String period = body.get("period") == null ? null : String.valueOf(body.get("period"));
        LocalDate takeDate = null;
        if (body.get("takeDate") != null) takeDate = LocalDate.parse(body.get("takeDate").toString());
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        String scope = body.get("scope") == null ? null : String.valueOf(body.get("scope"));
        return R.ok(service.create(warehouseId, period, takeDate, remark, scope));
    }

    /** 保存实盘数量（仅草稿） */
    @PutMapping("/{id}/items")
    public R<Void> saveItems(@PathVariable Long id, @RequestBody List<InventoryStockTakeItem> items) {
        service.saveItems(id, items);
        return R.ok();
    }

    // E1 口径（2026-09-12）：审核族统一 PUT（旧 POST 保留为别名）；反审核统一 /un-audit（旧 /unAudit 保留为别名）
    @RequestMapping(value = "/{id}/audit", method = {RequestMethod.PUT, RequestMethod.POST})
    public R<Void> audit(@PathVariable Long id) {
        service.audit(id);
        return R.ok();
    }

    @RequestMapping(value = {"/{id}/un-audit", "/{id}/unAudit"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public R<Void> unAudit(@PathVariable Long id) {
        service.unAudit(id);
        return R.ok();
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return R.ok();
    }
}
