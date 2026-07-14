package com.beichen.erp.outsource.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.outsource.entity.CloseReportItem;
import com.beichen.erp.outsource.service.CloseReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outsource/order/{orderId}/close-report")
@RequiredArgsConstructor
public class CloseReportController {

    private final CloseReportService reportService;

    /** 获取/生成结单报表数据 */
    @GetMapping
    public R<Map<String, Object>> getReport(@PathVariable Long orderId) {
        return R.ok(reportService.getOrCreateReport(orderId));
    }

    /** 保存草稿 */
    @PutMapping
    public R<Void> saveDraft(@PathVariable Long orderId, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsRaw = (List<Map<String, Object>>) body.get("items");
        List<CloseReportItem> items = itemsRaw != null ? itemsRaw.stream().map(m -> {
            CloseReportItem i = new CloseReportItem();
            i.setMaterialName((String) m.get("materialName"));
            i.setMaterialType((String) m.get("materialType"));
            i.setUnit((String) m.get("unit"));
            if (m.get("deliveredQuantity") != null) i.setDeliveredQuantity(new java.math.BigDecimal(m.get("deliveredQuantity").toString()));
            if (m.get("returnedQuantity") != null) i.setReturnedQuantity(new java.math.BigDecimal(m.get("returnedQuantity").toString()));
            if (m.get("goodReturnQty") != null) i.setGoodReturnQty(new java.math.BigDecimal(m.get("goodReturnQty").toString()));
            if (m.get("defectReturnQty") != null) i.setDefectReturnQty(new java.math.BigDecimal(m.get("defectReturnQty").toString()));
            if (m.get("shippedQuantity") != null) i.setShippedQuantity(new java.math.BigDecimal(m.get("shippedQuantity").toString()));
            if (m.get("targetYieldRate") != null) i.setTargetYieldRate(new java.math.BigDecimal(m.get("targetYieldRate").toString()));
            if (m.get("actualYieldRate") != null) i.setActualYieldRate(new java.math.BigDecimal(m.get("actualYieldRate").toString()));
            if (m.get("yieldLoss") != null) i.setYieldLoss(new java.math.BigDecimal(m.get("yieldLoss").toString()));
            if (m.get("excessLossQty") != null) i.setExcessLossQty(new java.math.BigDecimal(m.get("excessLossQty").toString()));
            i.setRemark((String) m.get("remark"));
            return i;
        }).toList() : List.of();

        String remark = (String) body.get("remark");
        reportService.saveDraft(orderId, items, remark);
        return R.ok();
    }

    /** 确认结单 */
    @PostMapping("/confirm")
    public R<Void> confirm(@PathVariable Long orderId) {
        reportService.confirmClose(orderId);
        return R.ok();
    }
}
