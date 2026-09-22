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
}
