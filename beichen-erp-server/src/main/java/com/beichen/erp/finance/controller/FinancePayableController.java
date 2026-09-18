package com.beichen.erp.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/payable")
@RequiredArgsConstructor
public class FinancePayableController {

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
        LambdaQueryWrapper<FinancePayable> w = new LambdaQueryWrapper<FinancePayable>()
                .eq(supplierId != null, FinancePayable::getSupplierId, supplierId)
                .eq(supplierType != null && !supplierType.isBlank(), FinancePayable::getSupplierType, supplierType)
                .eq(sourceBillType != null && !sourceBillType.isBlank(), FinancePayable::getSourceBillType, sourceBillType)
                .eq(status != null && !status.isBlank(), FinancePayable::getStatus, status)
                .like(billNo != null && !billNo.isBlank(), FinancePayable::getBillNo, billNo)
                .orderByDesc(FinancePayable::getId);
        Page<FinancePayable> raw = payableMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId()); m.put("billNo", r.getBillNo());
            m.put("supplierId", r.getSupplierId()); m.put("supplierName", r.getSupplierName());
            m.put("supplierType", r.getSupplierType());
            m.put("sourceBillType", r.getSourceBillType()); m.put("sourceBillNo", r.getSourceBillNo()); m.put("sourceId", r.getSourceId());
            m.put("amount", r.getAmount()); m.put("paidAmount", r.getPaidAmount());
            m.put("unpaidAmount", r.getUnpaidAmount()); m.put("dueDate", r.getDueDate());
            m.put("status", r.getStatus()); m.put("remark", r.getRemark());
            m.put("transferredToReceivable", r.getTransferredToReceivable());
            m.put("createTime", r.getCreateTime());
            return m;
        }).toList());
        return R.ok(res);
    }

    @GetMapping("/{id}")
    public R<FinancePayable> getById(@PathVariable Long id) {
        return R.ok(payableMapper.selectById(id));
    }

    /** 付款时可选的未结清应付：排除已转应收（避免同一笔既抵扣又向对方收款）与**负数应付**（I11 修复 2026-09-18） */
    @GetMapping("/unpaid")
    public R<?> unpaid(@RequestParam Long supplierId) {
        return R.ok(payableMapper.selectList(new LambdaQueryWrapper<FinancePayable>()
                .eq(FinancePayable::getSupplierId, supplierId)
                .ne(FinancePayable::getStatus, SettlementStatus.SETTLED.getCode())
                // 已作废(反审核冲销留痕)的台账不可再被选中抵扣；口径与应付汇总/账龄一致（应收侧同样已排除 CANCELLED）
                .ne(FinancePayable::getStatus, SettlementStatus.CANCELLED.getCode())
                .ne(FinancePayable::getTransferredToReceivable, 1)
                // I11 修复（2026-09-18，用户确认口径）：核销下拉**只列正数未付**。负数应付是「退货/超损冲减」项，
                // 正常应在付款时**净额抵扣**；若被单独选中核销，会生成 ADVANCE（预付）台账、把负数越滚越大
                // （实测付 500 后产生 -566 预付）。需要向对方收款时走「应付转应收」，不是把它当付款核销目标。
                .gt(FinancePayable::getAmount, BigDecimal.ZERO)
                .orderByDesc(FinancePayable::getId)));
    }

    /** 按供应商汇总应付：总额/已付/未付/逾期 */
    @GetMapping("/supplier-summary")
    public R<?> supplierSummary() {
        List<FinancePayable> all = payableMapper.selectList(new LambdaQueryWrapper<FinancePayable>()
                .ne(FinancePayable::getStatus, DocStatus.CANCELLED.getCode())
                // 已转应收的冲减项不再参与付款抵扣，应付汇总口径必须同步排除，否则与应收双算
                .ne(FinancePayable::getTransferredToReceivable, 1));
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (FinancePayable p : all) {
            if (p.getSupplierId() == null) continue;
            Map<String, Object> m = map.computeIfAbsent(p.getSupplierId(), k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("supplierId", k);
                x.put("supplierName", p.getSupplierName());
                x.put("totalAmount", BigDecimal.ZERO);
                x.put("paidAmount", BigDecimal.ZERO);
                x.put("unpaidAmount", BigDecimal.ZERO);
                x.put("overdueAmount", BigDecimal.ZERO);
                return x;
            });
            BigDecimal amount = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
            BigDecimal paid = p.getPaidAmount() != null ? p.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal unpaidAmt = p.getUnpaidAmount() != null ? p.getUnpaidAmount() : BigDecimal.ZERO;
            m.put("totalAmount", ((BigDecimal) m.get("totalAmount")).add(amount));
            m.put("paidAmount", ((BigDecimal) m.get("paidAmount")).add(paid));
            m.put("unpaidAmount", ((BigDecimal) m.get("unpaidAmount")).add(unpaidAmt));
            if (unpaidAmt.compareTo(BigDecimal.ZERO) > 0 && p.getDueDate() != null && p.getDueDate().isBefore(today))
                m.put("overdueAmount", ((BigDecimal) m.get("overdueAmount")).add(unpaidAmt));
        }
        // 回填往来主体类型：优先取台账上固化的 supplier_type，多类型时取字典序第一个标签
        java.util.Set<Long> sids = map.keySet();
        if (!sids.isEmpty()) {
            Map<Long, String> typeBySupplier = new HashMap<>();
            for (FinancePayable p : all) {
                if (p.getSupplierId() != null && p.getSupplierType() != null)
                    typeBySupplier.merge(p.getSupplierId(), p.getSupplierType(), (a, b) -> a.compareTo(b) <= 0 ? a : b);
            }
            for (Map.Entry<Long, Map<String, Object>> e : map.entrySet()) {
                e.getValue().put("supplierType", typeBySupplier.get(e.getKey()));
            }
        }
        List<Map<String, Object>> list = new ArrayList<>(map.values());
        list.sort((a, b) -> ((BigDecimal) b.get("unpaidAmount")).compareTo((BigDecimal) a.get("unpaidAmount")));
        return R.ok(list);
    }
}
