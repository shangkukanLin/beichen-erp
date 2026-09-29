package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.inventory.common.LossReason;
import com.beichen.erp.outsource.entity.OutsourceStockLoss;
import com.beichen.erp.outsource.entity.OutsourceStockLossItem;
import com.beichen.erp.outsource.service.OutsourceStockLossService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 委外物料报损单
 * <p>路由前缀: /api/outsource/stock-loss</p>
 */
@RestController
@RequestMapping("/api/outsource/stock-loss")
@RequiredArgsConstructor
public class OutsourceStockLossController {

    private final OutsourceStockLossService service;

    @GetMapping("/page")
    public R<Page<OutsourceStockLoss>> page(
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

    /** 报损原因 **code 列表**（2026-09-14：「接口只回 code」，中文由前端 `LossReasonLabel` 映射） */
    @GetMapping("/loss-reasons")
    public R<List<String>> lossReasons() {
        List<String> list = new ArrayList<>();
        for (LossReason r : LossReason.values()) {
            list.add(r.getCode());
        }
        return R.ok(list);
    }

    @GetMapping("/{id}")
    public R<OutsourceStockLoss> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<OutsourceStockLossItem>> getItems(@PathVariable Long id) {
        return R.ok(service.getItems(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parseIo(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        OutsourceStockLoss loss = parseIo(body);
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

    /** 主表解析：兼容 {loss:{...},items:[...]} 与扁平结构 */
    @SuppressWarnings("unchecked")
    private OutsourceStockLoss parseIo(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("loss") ? (Map<String, Object>) body.get("loss") : body;
        OutsourceStockLoss o = new OutsourceStockLoss();
        if (d.get("warehouseId") != null) o.setWarehouseId(Long.valueOf(d.get("warehouseId").toString()));
        o.setLossReason((String) d.get("lossReason"));
        o.setRemark((String) d.get("remark"));
        if (d.get("lossDate") != null && !d.get("lossDate").toString().isBlank()) {
            o.setLossDate(LocalDate.parse(d.get("lossDate").toString()));
        }
        // 2026-09-29（用户口径「报损需要走财务流程」）：损失承担方 + 承担方供应商。
        // ⚠️ 本方法是**白名单解析** —— 新增字段必须在此登记，否则前端传了也会被静默丢弃。
        o.setLiableParty((String) d.get("liableParty"));
        if (d.get("liableSupplierId") != null && !d.get("liableSupplierId").toString().isBlank()) {
            o.setLiableSupplierId(Long.valueOf(d.get("liableSupplierId").toString()));
        }
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<OutsourceStockLossItem> parseItems(Map<String, Object> body) {
        List<OutsourceStockLossItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    OutsourceStockLossItem it = new OutsourceStockLossItem();
                    if (map.get("materialId") != null) {
                        it.setMaterialId(Long.valueOf(map.get("materialId").toString()));
                    }
                    if (map.get("materialTypeId") != null) {
                        it.setMaterialTypeId(Long.valueOf(map.get("materialTypeId").toString()));
                    }
                    if (map.get("quantity") != null && !map.get("quantity").toString().isBlank()) {
                        it.setQuantity(new BigDecimal(map.get("quantity").toString()));
                    }
                    if (map.get("unitPrice") != null && !map.get("unitPrice").toString().isBlank()) {
                        it.setUnitPrice(new BigDecimal(map.get("unitPrice").toString()));
                    }
                    it.setRemark((String) map.get("remark"));
                    list.add(it);
                }
            }
        }
        return list;
    }
}
