package com.beichen.erp.finance.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.entity.PayableTransfer;
import com.beichen.erp.finance.service.PayableTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应付转应收单
 * <p>路由前缀：/api/finance/payable-transfer</p>
 */
@RestController
@RequestMapping("/api/finance/payable-transfer")
@RequiredArgsConstructor
public class PayableTransferController {

    private final PayableTransferService service;

    @GetMapping("/page")
    public R<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> page(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplierType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(code, supplierId, supplierType, status, startDate, endDate, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<PayableTransfer> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    /**
     * 可转应收的应付列表（负数、未转、未结清），供新增页下拉。
     *
     * <p>F7-222（2026-09-29 审核批 B）：**必须带 {@code supplierId} 或 {@code payableId}** ——
     * 无主体时返回空列表（原先会返回全公司可转应付，只读面过宽）。
     * {@code payableId} 服务于"应付列表点「转应收」带参进来"或"编辑态回填"，只回那一条。</p>
     */
    @GetMapping("/transferable")
    public R<List<Map<String, Object>>> transferable(@RequestParam(required = false) Long supplierId,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Long payableId) {
        return R.ok(service.transferablePayables(supplierId, keyword, payableId));
    }

    /** 来源单据类型 **code 列表**（与台账存值一致；2026-09-14：「接口只回 code」，中文由前端 `SourceBillTypeLabel` 映射） */
    @GetMapping("/source-bill-types")
    public R<List<String>> sourceBillTypes() {
        List<String> list = new ArrayList<>();
        for (SourceBillType t : SourceBillType.values()) {
            list.add(t.getCode());
        }
        return R.ok(list);
    }

    @PostMapping
    public R<Void> create(@RequestBody PayableTransfer transfer) {
        service.create(transfer);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody PayableTransfer transfer) {
        service.update(transfer);
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
}
