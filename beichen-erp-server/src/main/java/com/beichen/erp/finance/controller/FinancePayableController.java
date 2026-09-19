package com.beichen.erp.finance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import com.beichen.erp.finance.service.PayableQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 应付台账接口（应付管理页）。
 *
 * <p>期 2（2026-09-19 读隔离）：查询口径已抽到 {@link PayableQuery}，本控制器与
 * {@code FinancePaymentController}（付款页 / 供应商付款页）**共用同一实现** ——
 * 付款页不再跨模块直读本页接口，也不会出现两份聚合口径。</p>
 */
@RestController
@RequestMapping("/api/finance/payable")
@RequiredArgsConstructor
public class FinancePayableController {

    private final PayableQuery query;
    private final FinancePayableMapper payableMapper;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplierType,
            @RequestParam(required = false) String sourceBillType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String billNo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(query.page(supplierId, supplierType, sourceBillType, status, billNo, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<FinancePayable> getById(@PathVariable Long id) {
        return R.ok(payableMapper.selectById(id));
    }

    /** 付款时可选的未结清应付（付款页 / 供应商付款页共用；口径见 {@link PayableQuery#unpaid}） */
    @GetMapping("/unpaid")
    public R<List<FinancePayable>> unpaid(@RequestParam Long supplierId) {
        return R.ok(query.unpaid(supplierId));
    }

    /** 按供应商汇总应付：总额/已付/未付/逾期（付款页 Tab1 与供应商付款页共用） */
    @GetMapping("/supplier-summary")
    public R<List<Map<String, Object>>> supplierSummary() {
        return R.ok(query.supplierSummary());
    }
}
