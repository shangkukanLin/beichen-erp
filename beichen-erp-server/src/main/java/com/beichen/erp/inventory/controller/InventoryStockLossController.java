package com.beichen.erp.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.inventory.common.LossReason;
import com.beichen.erp.inventory.entity.InventoryStockLoss;
import com.beichen.erp.inventory.entity.InventoryStockLossItem;
import com.beichen.erp.inventory.service.InventoryStockLossService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成品报损单
 * <p>路由前缀: /api/inventory/stock-loss</p>
 */
@RestController
@RequestMapping("/api/inventory/stock-loss")
@RequiredArgsConstructor
public class InventoryStockLossController {

    private final InventoryStockLossService service;

    @GetMapping("/page")
    public R<Page<InventoryStockLoss>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String lossReason,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(status, warehouseId, lossReason, keyword, startDate, endDate, pageNum, pageSize));
    }

    /**
     * 报损原因 **code 列表**（与写入数据库的值一致）。
     * <p>2026-09-14：「接口只回 code」，中文由前端 `LossReasonLabel` 映射。</p>
     */
    @GetMapping("/loss-reasons")
    public R<List<String>> lossReasons() {
        List<String> list = new ArrayList<>();
        for (LossReason r : LossReason.values()) {
            list.add(r.getCode());
        }
        return R.ok(list);
    }

    @GetMapping("/{id}")
    public R<InventoryStockLoss> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<InventoryStockLossItem>> getItems(@PathVariable Long id) {
        return R.ok(service.getItems(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parseIo(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        InventoryStockLoss loss = parseIo(body);
        loss.setId(id);
        service.update(loss, parseItems(body));
        return R.ok();
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

    /** 主表解析：同时兼容 {loss:{...},items:[...]} 与扁平 {warehouseId:...} 两种提交结构 */
    @SuppressWarnings("unchecked")
    private InventoryStockLoss parseIo(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("loss") ? (Map<String, Object>) body.get("loss") : body;
        InventoryStockLoss o = new InventoryStockLoss();
        if (d.get("warehouseId") != null) o.setWarehouseId(Long.valueOf(d.get("warehouseId").toString()));
        o.setLossReason((String) d.get("lossReason"));
        o.setRemark((String) d.get("remark"));
        if (d.get("lossDate") != null && !d.get("lossDate").toString().isBlank()) {
            o.setLossDate(LocalDate.parse(d.get("lossDate").toString()));
        }
        // 2026-09-29（用户口径「报损需要走财务流程」）：损失承担方 + 承担方供应商。
        // ⚠️ 本方法是**白名单解析** —— 新增字段必须在此登记，否则前端传了也会被静默丢弃
        //    （等于按默认「内部损失」落账，正是本次实证发现的缺陷）。
        o.setLiableParty((String) d.get("liableParty"));
        if (d.get("liableSupplierId") != null && !d.get("liableSupplierId").toString().isBlank()) {
            o.setLiableSupplierId(Long.valueOf(d.get("liableSupplierId").toString()));
        }
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<InventoryStockLossItem> parseItems(Map<String, Object> body) {
        List<InventoryStockLossItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    InventoryStockLossItem it = new InventoryStockLossItem();
                    if (map.get("productId") != null) {
                        it.setProductId(Long.valueOf(map.get("productId").toString()));
                    }
                    if (map.get("quantity") != null && !map.get("quantity").toString().isBlank()) {
                        it.setQuantity(new BigDecimal(map.get("quantity").toString()));
                    }
                    if (map.get("unitPrice") != null && !map.get("unitPrice").toString().isBlank()) {
                        it.setUnitPrice(new BigDecimal(map.get("unitPrice").toString()));
                    }
                    it.setQualityType((String) map.get("qualityType"));
                    it.setRemark((String) map.get("remark"));
                    list.add(it);
                }
            }
        }
        return list;
    }
}
