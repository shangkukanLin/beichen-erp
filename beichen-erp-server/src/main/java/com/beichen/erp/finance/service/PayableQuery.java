package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应付台账的**只读查询**（2026-09-19 期 2「读隔离」抽出）。
 *
 * <p>为什么需要这个类：付款页（{@code /api/finance/payment}，含供应商付款子页）需要
 * 「应付汇总 / 应付明细 / 未结清应付」三类数据，原先它直接去读**应付页**的接口
 * {@code /api/finance/payable/*}（需 {@code finance:payable}）——只被授予
 * {@code finance:payment} 的用户直调会 403（页面看得见、数据读不出）。</p>
 *
 * <p>现把查询口径收敛到本类，由 {@code FinancePayableController}（应付页）与
 * {@code FinancePaymentController}（付款页）**共用同一实现**：既满足"付款页只读自己的接口"，
 * 又不会出现两套聚合口径（金额口径必须唯一）。方法语义与原先接口逐字一致。</p>
 */
@Component
@RequiredArgsConstructor
public class PayableQuery {

    private final FinancePayableMapper payableMapper;

    /** 应付明细分页（原 {@code GET /api/finance/payable/page}，字段与口径不变） */
    public Page<Map<String, Object>> page(Long supplierId, String supplierType, String sourceBillType,
                                          String status, String billNo, int pageNum, int pageSize) {
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
        return res;
    }

    /**
     * 付款时可选的未结清应付：排除已转应收（避免同一笔既抵扣又向对方收款）与**负数应付**（I11 修复 2026-09-18）。
     * <p>核销下拉**只列正数未付**：负数应付是「退货/超损冲减」项，正常应在付款时净额抵扣；若被单独选中核销，
     * 会生成 ADVANCE（预付）台账、把负数越滚越大。需要向对方收款时走「应付转应收」。</p>
     */
    public List<FinancePayable> unpaid(Long supplierId) {
        return payableMapper.selectList(new LambdaQueryWrapper<FinancePayable>()
                .eq(FinancePayable::getSupplierId, supplierId)
                .ne(FinancePayable::getStatus, SettlementStatus.SETTLED.getCode())
                // 已作废(反审核冲销留痕)的台账不可再被选中抵扣；口径与应付汇总/账龄一致（应收侧同样已排除 CANCELLED）
                .ne(FinancePayable::getStatus, SettlementStatus.CANCELLED.getCode())
                .ne(FinancePayable::getTransferredToReceivable, 1)
                .gt(FinancePayable::getAmount, BigDecimal.ZERO)
                .orderByDesc(FinancePayable::getId));
    }

    /** 按供应商汇总应付：总额/已付/未付/逾期（原 {@code GET /api/finance/payable/supplier-summary}） */
    public List<Map<String, Object>> supplierSummary() {
        List<FinancePayable> all = payableMapper.selectList(new LambdaQueryWrapper<FinancePayable>()
                .ne(FinancePayable::getStatus, DocStatus.CANCELLED.getCode())
                // 已转应收的冲减项不再参与付款抵扣，应付汇总口径必须同步排除，否则与应收双算
                .ne(FinancePayable::getTransferredToReceivable, 1));
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
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
        for (FinancePayable p : all) {
            if (p.getSupplierId() == null || p.getSupplierType() == null) continue;
            Map<String, Object> m = map.get(p.getSupplierId());
            if (m == null) continue;
            Object cur = m.get("supplierType");
            if (cur == null || p.getSupplierType().compareTo(cur.toString()) <= 0) m.put("supplierType", p.getSupplierType());
        }
        List<Map<String, Object>> list = new ArrayList<>(map.values());
        list.sort((a, b) -> ((BigDecimal) b.get("unpaidAmount")).compareTo((BigDecimal) a.get("unpaidAmount")));
        return list;
    }
}
