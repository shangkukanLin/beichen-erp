package com.beichen.erp.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import com.beichen.erp.finance.service.ReceivableQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应收台账接口（应收管理页）。
 *
 * <p>期 2（2026-09-19 读隔离）：{@code /unpaid} 的口径已抽到 {@link ReceivableQuery}，
 * 本控制器与 {@code FinanceReceiptController}（收款页核销下拉）共用同一实现。</p>
 */
@RestController
@RequestMapping("/api/finance/receivable")
@RequiredArgsConstructor
public class FinanceReceivableController {

    private final ReceivableQuery query;
    private final FinanceReceivableMapper receivableMapper;
    /**
     * 客户应收工作台要展示"该客户的收款记录"（2026-09-29）。
     * <p>刻意**复用收款单服务的分页实现**（而不是另写一份查询）：口径只有一处，
     * 与收款管理页看到的是同一份数据；本页接口只要求 {@code finance:receivable}（前缀最长匹配），
     * 所以只被授予应收权限的用户也能看到自己客户的收款流水。</p>
     */
    private final com.beichen.erp.finance.service.FinanceReceiptService receiptService;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String billNo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        LambdaQueryWrapper<FinanceReceivable> w = new LambdaQueryWrapper<FinanceReceivable>()
                .eq(customerId != null, FinanceReceivable::getCustomerId, customerId)
                .eq(supplierId != null, FinanceReceivable::getSupplierId, supplierId)
                .eq(subjectType != null && !subjectType.isBlank(), FinanceReceivable::getSubjectType, subjectType)
                .eq(status != null && !status.isBlank(), FinanceReceivable::getStatus, status)
                .like(billNo != null && !billNo.isBlank(), FinanceReceivable::getBillNo, billNo)
                .orderByDesc(FinanceReceivable::getId);
        Page<FinanceReceivable> raw = receivableMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId()); m.put("billNo", r.getBillNo());
            m.put("customerId", r.getCustomerId()); m.put("customerName", r.getCustomerName());
            // 主体类型：CUSTOMER=客户应收；SUPPLIER=供应商应收（应付转应收单生成）
            m.put("subjectType", r.getSubjectType());
            m.put("supplierId", r.getSupplierId()); m.put("supplierName", r.getSupplierName());
            m.put("sourceBillType", r.getSourceBillType()); m.put("sourceBillNo", r.getSourceBillNo());
            m.put("amount", r.getAmount()); m.put("paidAmount", r.getPaidAmount());
            m.put("unpaidAmount", r.getUnpaidAmount()); m.put("dueDate", r.getDueDate());
            m.put("status", r.getStatus()); m.put("remark", r.getRemark());
            m.put("createTime", r.getCreateTime());
            // 制单人（2026-09-23 用户口径：应收详情抽屉要显示制单人；本接口返回 Map 投影，不显式带上就永远为空）
            m.put("createByName", r.getCreateByName());
            return m;
        }).toList());
        return R.ok(res);
    }

    @GetMapping("/{id}")
    public R<FinanceReceivable> getById(@PathVariable Long id) {
        return R.ok(receivableMapper.selectById(id));
    }

    /** 未结清应收（收款页核销下拉共用；口径见 {@link ReceivableQuery#unpaid}） */
    @GetMapping("/unpaid")
    public R<List<FinanceReceivable>> unpaid(@RequestParam(required = false) Long customerId,
                                             @RequestParam(required = false) Long supplierId,
                                             @RequestParam(required = false) String subjectType) {
        return R.ok(query.unpaid(customerId, supplierId, subjectType));
    }

    /**
     * 按客户汇总应收（2026-09-29 用户口径「汇总要显示在**应收管理**里面」）：
     * 应收管理页视图页签「按客户汇总」用它（口径见 {@link ReceivableQuery#customerSummary}）——
     * 与应付侧 {@code /supplier-summary} 逐条对称（同一套"未结清/逾期"定义）。
     */
    @GetMapping("/customer-summary")
    public R<List<Map<String, Object>>> customerSummary() {
        return R.ok(query.customerSummary());
    }

    /**
     * 某客户的收款记录（2026-09-29 客户应收工作台「收款记录」表）。
     * <p>走应收前缀而不是 {@code /finance/receipt/page}：只被授予 {@code finance:receivable} 的用户
     * 也需要看到"这个客户收过哪些款"（同 {@code /unpaid} 的读隔离处理）。</p>
     */
    @GetMapping("/receipts")
    public R<Page<Map<String, Object>>> receipts(@RequestParam Long customerId,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "100") int pageSize) {
        return R.ok(receiptService.page(customerId, null, null, status, pageNum, pageSize));
    }
}
