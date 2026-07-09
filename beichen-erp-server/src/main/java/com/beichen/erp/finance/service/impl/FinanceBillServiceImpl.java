package com.beichen.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.finance.entity.*;
import com.beichen.erp.finance.mapper.*;
import com.beichen.erp.finance.service.FinanceBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FinanceBillServiceImpl implements FinanceBillService {

    private final FinanceBillMapper billMapper;
    private final FinanceBillItemMapper billItemMapper;
    private final FinanceReceivableMapper receivableMapper;
    private final FinancePayableMapper payableMapper;

    @Override
    public Page<Map<String, Object>> page(String billType, Long partnerId, int pageNum, int pageSize) {
        LambdaQueryWrapper<FinanceBill> w = new LambdaQueryWrapper<FinanceBill>()
                .eq(billType != null && !billType.isBlank(), FinanceBill::getBillType, billType)
                .eq(partnerId != null, FinanceBill::getPartnerId, partnerId)
                .orderByDesc(FinanceBill::getId);
        Page<FinanceBill> raw = billMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(b -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", b.getId()); m.put("billNo", b.getBillNo());
            m.put("billType", b.getBillType()); m.put("partnerName", b.getPartnerName());
            m.put("periodStart", b.getPeriodStart()); m.put("periodEnd", b.getPeriodEnd());
            m.put("totalAmount", b.getTotalAmount()); m.put("paidAmount", b.getPaidAmount());
            m.put("unpaidAmount", b.getUnpaidAmount()); m.put("status", b.getStatus());
            m.put("createTime", b.getCreateTime());
            return m;
        }).toList());
        return res;
    }

    @Override public FinanceBill getById(Long id) { return billMapper.selectById(id); }
    @Override public List<FinanceBillItem> getItems(Long billId) {
        return billItemMapper.selectList(new LambdaQueryWrapper<FinanceBillItem>().eq(FinanceBillItem::getBillId, billId));
    }

    @Override
    public FinanceBill generate(String billType, Long partnerId, String partnerName, LocalDate periodStart, LocalDate periodEnd) {
        FinanceBill bill = new FinanceBill();
        bill.setBillNo(genCode());
        bill.setBillType(billType);
        bill.setPartnerId(partnerId);
        bill.setPartnerName(partnerName);
        bill.setPeriodStart(periodStart);
        bill.setPeriodEnd(periodEnd);
        bill.setStatus("未结清");

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal unpaid = BigDecimal.ZERO;
        List<FinanceBillItem> items = new ArrayList<>();

        if ("应收".equals(billType)) {
            List<FinanceReceivable> list = receivableMapper.selectList(new LambdaQueryWrapper<FinanceReceivable>()
                    .eq(FinanceReceivable::getCustomerId, partnerId)
                    .eq(FinanceReceivable::getStatus, "未结清").or().eq(FinanceReceivable::getStatus, "部分结清")
                    .le(FinanceReceivable::getDueDate, periodEnd));
            for (FinanceReceivable r : list) {
                FinanceBillItem it = new FinanceBillItem();
                it.setSourceBillType(r.getSourceBillType());
                it.setSourceBillNo(r.getSourceBillNo());
                it.setAmount(r.getAmount());
                it.setPaidAmount(r.getPaidAmount() != null ? r.getPaidAmount() : BigDecimal.ZERO);
                it.setUnpaidAmount(r.getUnpaidAmount() != null ? r.getUnpaidAmount() : BigDecimal.ZERO);
                it.setDueDate(r.getDueDate());
                total = total.add(r.getAmount());
                paid = paid.add(r.getPaidAmount() != null ? r.getPaidAmount() : BigDecimal.ZERO);
                unpaid = unpaid.add(r.getUnpaidAmount() != null ? r.getUnpaidAmount() : BigDecimal.ZERO);
                items.add(it);
            }
        } else {
            List<FinancePayable> list = payableMapper.selectList(new LambdaQueryWrapper<FinancePayable>()
                    .eq(FinancePayable::getSupplierId, partnerId)
                    .eq(FinancePayable::getStatus, "未结清").or().eq(FinancePayable::getStatus, "部分结清")
                    .le(FinancePayable::getDueDate, periodEnd));
            for (FinancePayable r : list) {
                FinanceBillItem it = new FinanceBillItem();
                it.setSourceBillType(r.getSourceBillType());
                it.setSourceBillNo(r.getSourceBillNo());
                it.setAmount(r.getAmount());
                it.setPaidAmount(r.getPaidAmount() != null ? r.getPaidAmount() : BigDecimal.ZERO);
                it.setUnpaidAmount(r.getUnpaidAmount() != null ? r.getUnpaidAmount() : BigDecimal.ZERO);
                it.setDueDate(r.getDueDate());
                total = total.add(r.getAmount());
                paid = paid.add(r.getPaidAmount() != null ? r.getPaidAmount() : BigDecimal.ZERO);
                unpaid = unpaid.add(r.getUnpaidAmount() != null ? r.getUnpaidAmount() : BigDecimal.ZERO);
                items.add(it);
            }
        }
        bill.setTotalAmount(total);
        bill.setPaidAmount(paid);
        bill.setUnpaidAmount(unpaid);
        billMapper.insert(bill);

        for (FinanceBillItem it : items) {
            it.setBillId(bill.getId());
            billItemMapper.insert(it);
        }
        return bill;
    }

    private String genCode() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = "ZD-" + d;
        LambdaQueryWrapper<FinanceBill> w = new LambdaQueryWrapper<FinanceBill>().likeRight(FinanceBill::getBillNo, pat).orderByDesc(FinanceBill::getBillNo).last("LIMIT 1");
        FinanceBill last = billMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getBillNo() != null) {
            try { seq = Integer.parseInt(last.getBillNo().substring(last.getBillNo().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return "ZD-" + d + String.format("%03d", seq);
    }
}
