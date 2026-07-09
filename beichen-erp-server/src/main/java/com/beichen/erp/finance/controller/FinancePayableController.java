package com.beichen.erp.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/payable")
@RequiredArgsConstructor
public class FinancePayableController {

    private final FinancePayableMapper payableMapper;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String billNo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        LambdaQueryWrapper<FinancePayable> w = new LambdaQueryWrapper<FinancePayable>()
                .eq(supplierId != null, FinancePayable::getSupplierId, supplierId)
                .eq(status != null && !status.isBlank(), FinancePayable::getStatus, status)
                .like(billNo != null && !billNo.isBlank(), FinancePayable::getBillNo, billNo)
                .orderByDesc(FinancePayable::getId);
        Page<FinancePayable> raw = payableMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId()); m.put("billNo", r.getBillNo());
            m.put("supplierId", r.getSupplierId()); m.put("supplierName", r.getSupplierName());
            m.put("sourceBillType", r.getSourceBillType()); m.put("sourceBillNo", r.getSourceBillNo());
            m.put("amount", r.getAmount()); m.put("paidAmount", r.getPaidAmount());
            m.put("unpaidAmount", r.getUnpaidAmount()); m.put("dueDate", r.getDueDate());
            m.put("status", r.getStatus()); m.put("remark", r.getRemark());
            m.put("createTime", r.getCreateTime());
            return m;
        }).toList());
        return R.ok(res);
    }

    @GetMapping("/{id}")
    public R<FinancePayable> getById(@PathVariable Long id) {
        return R.ok(payableMapper.selectById(id));
    }

    @GetMapping("/unpaid")
    public R<?> unpaid(@RequestParam Long supplierId) {
        return R.ok(payableMapper.selectList(new LambdaQueryWrapper<FinancePayable>()
                .eq(FinancePayable::getSupplierId, supplierId)
                .ne(FinancePayable::getStatus, "已结清")
                .orderByDesc(FinancePayable::getId)));
    }
}
