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
    /**
     * 供应商应付工作台要展示"该供应商的付款记录"（2026-09-29：工作台从 {@code /finance/payment/supplier/:id}
     * 搬到本前缀 {@code /finance/payable/supplier/:id}）。
     * <p>刻意**复用付款单服务的分页实现**（而不是另写一份查询）：口径只有一处，与付款管理页看到的是同一份数据；
     * 本端点挂应付前缀（最长前缀匹配 ⇒ 只要求 {@code finance:payable}），
     * 只被授予应付权限的用户才不会 403。</p>
     */
    private final com.beichen.erp.finance.service.FinancePaymentService paymentService;

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

    /**
     * 按供应商汇总应付：总额/已付/未付/逾期。
     * <p>2026-09-29 用户口径「汇总要显示在**应付管理**里面」：本端点成为**应付管理页视图页签
     * 「按供应商汇总」的唯一数据源**（原挂在付款管理页 Tab1，已随汇总之上的口径一起搬过来）；
     * 付款管理页自身的 {@code /finance/payment/payable-summary} 保留（老入口/回归脚本兼容，同一实现）。</p>
     */
    @GetMapping("/supplier-summary")
    public R<List<Map<String, Object>>> supplierSummary() {
        return R.ok(query.supplierSummary());
    }

    /**
     * 某供应商的付款记录（2026-09-29 供应商应付工作台「付款记录」表）。
     * <p>走应付前缀而不是 {@code /finance/payment/page}：只被授予 {@code finance:payable} 的用户
     * 也需要看到"这个供应商付过哪些款"（与 {@code /unpaid} 的读隔离处理同款）。</p>
     */
    @GetMapping("/payments")
    public R<Page<Map<String, Object>>> payments(@RequestParam Long supplierId,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "100") int pageSize) {
        return R.ok(paymentService.page(supplierId, null, status, pageNum, pageSize));
    }
}
