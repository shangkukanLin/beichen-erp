package com.beichen.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.entity.*;
import com.beichen.erp.finance.common.CashflowType;
import com.beichen.erp.finance.common.SettlementDirection;
import com.beichen.erp.finance.common.SettlementSourceType;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.mapper.*;
import com.beichen.erp.finance.service.FinancePaymentService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FinancePaymentServiceImpl implements FinancePaymentService {

    private final FinancePaymentMapper paymentMapper;
    private final FinancePaymentItemMapper itemMapper;
    private final FinancePayableMapper payableMapper;
    private final FinanceAccountMapper accountMapper;
    private final FinanceCashflowMapper cashflowMapper;
    private final SupplierMapper supplierMapper;
    private final FinanceSettlementMapper settlementMapper;
    private final FinanceBillItemMapper billItemMapper;
    private final FinanceBillMapper billMapper;

    @Override
    public Page<Map<String, Object>> page(Long supplierId, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<FinancePayment> w = new LambdaQueryWrapper<FinancePayment>()
                .eq(supplierId != null, FinancePayment::getSupplierId, supplierId)
                .eq(status != null && !status.isBlank(), FinancePayment::getStatus, status)
                .orderByDesc(FinancePayment::getId);
        Page<FinancePayment> raw = paymentMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId()); m.put("code", p.getCode());
            m.put("supplierId", p.getSupplierId()); m.put("supplierName", p.getSupplierName());
            m.put("accountId", p.getAccountId()); m.put("accountName", p.getAccountName());
            m.put("paymentDate", p.getPaymentDate()); m.put("amount", p.getAmount());
            m.put("status", p.getStatus()); m.put("remark", p.getRemark());
            m.put("createTime", p.getCreateTime());
            return m;
        }).toList());
        return res;
    }

    @Override public FinancePayment getById(Long id) { return paymentMapper.selectById(id); }
    @Override public List<FinancePaymentItem> getItems(Long paymentId) {
        return itemMapper.selectList(new LambdaQueryWrapper<FinancePaymentItem>().eq(FinancePaymentItem::getPaymentId, paymentId));
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void create(FinancePayment payment, List<FinancePaymentItem> items) {
        if (payment.getSupplierId() == null) throw new BusinessException("供应商不能为空");
        if (payment.getAccountId() == null) throw new BusinessException("付款账户不能为空");
        Supplier s = supplierMapper.selectById(payment.getSupplierId());
        payment.setSupplierName(s != null ? s.getName() : "");
        FinanceAccount acc = accountMapper.selectById(payment.getAccountId());
        payment.setAccountName(acc != null ? acc.getAccountName() : "");
        payment.setCode(gen(BillPrefix.PAYMENT, paymentMapper));
        payment.setStatus(DocStatus.DRAFT.name());
        Long cid = CompanyContext.get();
        BigDecimal total = BigDecimal.ZERO;
        if (cid != null && cid > 0) payment.setCompanyId(cid);
        paymentMapper.insert(payment);
        for (FinancePaymentItem it : items) {
            it.setId(null); it.setPaymentId(payment.getId());
            total = total.add(it.getThisAmount() != null ? it.getThisAmount() : BigDecimal.ZERO);
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        FinancePayment u = new FinancePayment(); u.setId(payment.getId()); u.setAmount(total); paymentMapper.updateById(u);
    }

    @Override
    public void updateAttach(FinancePayment payment) { paymentMapper.updateById(payment); }

    @Override @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        FinancePayment old = paymentMapper.selectById(id);
        if (old == null) throw new BusinessException("付款单不存在");
        if (!DocStatus.DRAFT.name().equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        FinancePayment u = new FinancePayment(); u.setId(id); u.setStatus(DocStatus.CANCELLED.name()); paymentMapper.updateById(u);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        FinancePayment payment = paymentMapper.selectById(id);
        if (payment == null || !DocStatus.DRAFT.name().equals(payment.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<FinancePaymentItem> items = itemMapper.selectList(new LambdaQueryWrapper<FinancePaymentItem>().eq(FinancePaymentItem::getPaymentId, id));
        // 核销应付：更新台账 + 写入核销流水（双向可追溯），超额部分生成负数应付（预付）
        for (FinancePaymentItem it : items) {
            if (it.getPayableId() == null) continue;
            FinancePayable p = payableMapper.selectById(it.getPayableId());
            if (p == null) continue;
            BigDecimal amt = it.getThisAmount() != null ? it.getThisAmount() : BigDecimal.ZERO;
            BigDecimal unpaid = p.getUnpaidAmount() != null ? p.getUnpaidAmount() : BigDecimal.ZERO;
            BigDecimal newUnpaid = unpaid.subtract(amt);
            if (newUnpaid.compareTo(BigDecimal.ZERO) < 0) {
                // 超额付款：原应付全额结清，超额部分生成负数应付（预付，供应商欠我方）
                BigDecimal over = newUnpaid.negate();
                BigDecimal total = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
                p.setPaidAmount(total);
                p.setUnpaidAmount(BigDecimal.ZERO);
                p.setStatus(SettlementStatus.SETTLED.getCode());
                payableMapper.updateById(p);
                // 生成负数应付（预付单），sourceBillType=ADVANCE + sourceId=原付款单id 用于反审核精确删除
                FinancePayable advance = new FinancePayable();
                advance.setBillNo(p.getBillNo() + "-" + SettlementStatus.ADVANCE.getCode());
                advance.setSupplierId(p.getSupplierId());
                advance.setSupplierName(p.getSupplierName());
                advance.setSourceBillType(SettlementStatus.ADVANCE.getCode());
                advance.setSourceBillNo(p.getSourceBillNo());
                advance.setSourceId(id);
                advance.setAmount(over.negate());
                advance.setPaidAmount(BigDecimal.ZERO);
                advance.setUnpaidAmount(over.negate());
                advance.setDueDate(p.getDueDate());
                advance.setStatus(SettlementStatus.ADVANCE.getCode());
                advance.setRemark("付款预付（多付，供应商欠我方）");
                payableMapper.insert(advance);
                // 核销流水记录实际核销额 = 原未付额（全额结清）
                FinanceSettlement st = new FinanceSettlement();
                st.setReceiptPaymentId(id);
                st.setPayableReceivableId(it.getPayableId());
                st.setAmount(unpaid);
                st.setDirection(SettlementDirection.PAY.getCode());
                st.setSourceType(SettlementSourceType.PAYMENT.getCode());
                st.setSourceId(id);
                st.setCompanyId(CompanyContext.get());
                settlementMapper.insert(st);
            } else {
                // 正常核销
                BigDecimal newPaid = (p.getPaidAmount() != null ? p.getPaidAmount() : BigDecimal.ZERO).add(amt);
                p.setPaidAmount(newPaid);
                p.setUnpaidAmount(newUnpaid);
                p.setStatus(newUnpaid.compareTo(BigDecimal.ZERO) <= 0 ? SettlementStatus.SETTLED.getCode() : SettlementStatus.PARTIAL.getCode());
                payableMapper.updateById(p);
                FinanceSettlement st = new FinanceSettlement();
                st.setReceiptPaymentId(id);
                st.setPayableReceivableId(it.getPayableId());
                st.setAmount(amt);
                st.setDirection(SettlementDirection.PAY.getCode());
                st.setSourceType(SettlementSourceType.PAYMENT.getCode());
                st.setSourceId(id);
                st.setCompanyId(CompanyContext.get());
                settlementMapper.insert(st);
            }
        }
        // 账单联动：核销后反向更新账单明细已付金额（账单=结算快照，随核销进度同步）
        syncBillProgress(id);
        // 写资金流水（账户余额由流水实时累计，不再维护余额快照）
        FinanceCashflow cf = new FinanceCashflow();
        cf.setFlowNo(gen(BillPrefix.PAYMENT, cashflowMapper));
        cf.setAccountId(payment.getAccountId());
        cf.setAccountName(payment.getAccountName());
        cf.setFlowType(CashflowType.PAYMENT.getCode());
        cf.setRelatedBillNo(payment.getCode());
        cf.setRelatedBillType("付款单");
        cf.setIncome(BigDecimal.ZERO);
        cf.setExpense(payment.getAmount());
        cashflowMapper.insert(cf);
        // 更新付款单状态
        FinancePayment u = new FinancePayment(); u.setId(id); u.setStatus(DocStatus.AUDITED.name()); paymentMapper.updateById(u);
    }

    /** 账单进度联动：按核销流水反查账单明细，同步已付金额并重算账单主表 */
    private void syncBillProgress(Long paymentId) {
        List<FinanceSettlement> sts = settlementMapper.selectList(
                new LambdaQueryWrapper<FinanceSettlement>()
                        .eq(FinanceSettlement::getReceiptPaymentId, paymentId)
                        .eq(FinanceSettlement::getDirection, SettlementDirection.PAY.getCode()));
        Set<Long> billIds = new HashSet<>();
        for (FinanceSettlement st : sts) {
            List<FinanceBillItem> items = billItemMapper.selectList(
                    new LambdaQueryWrapper<FinanceBillItem>().eq(FinanceBillItem::getSourceId, st.getPayableReceivableId()));
            for (FinanceBillItem item : items) {
                BigDecimal newPaid = (item.getPaidAmount() != null ? item.getPaidAmount() : BigDecimal.ZERO).add(st.getAmount());
                BigDecimal newUnpaid = (item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO).subtract(newPaid);
                item.setPaidAmount(newPaid);
                item.setUnpaidAmount(newUnpaid.max(BigDecimal.ZERO));
                billItemMapper.updateById(item);
                billIds.add(item.getBillId());
            }
        }
        // 重算账单主表 paidAmount/unpaidAmount
        for (Long billId : billIds) {
            recalcBill(billId);
        }
    }

    /** 反审核时反向扣减账单明细已付金额：按核销流水冲减，与 syncBillProgress 累加逻辑对称 */
    private void reverseBillProgress(Long paymentId) {
        List<FinanceSettlement> sts = settlementMapper.selectList(
                new LambdaQueryWrapper<FinanceSettlement>()
                        .eq(FinanceSettlement::getReceiptPaymentId, paymentId)
                        .eq(FinanceSettlement::getDirection, SettlementDirection.PAY.getCode()));
        Set<Long> billIds = new HashSet<>();
        for (FinanceSettlement st : sts) {
            List<FinanceBillItem> items = billItemMapper.selectList(
                    new LambdaQueryWrapper<FinanceBillItem>().eq(FinanceBillItem::getSourceId, st.getPayableReceivableId()));
            for (FinanceBillItem item : items) {
                BigDecimal newPaid = (item.getPaidAmount() != null ? item.getPaidAmount() : BigDecimal.ZERO).subtract(st.getAmount());
                item.setPaidAmount(newPaid.max(BigDecimal.ZERO));
                item.setUnpaidAmount((item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO).subtract(item.getPaidAmount()).max(BigDecimal.ZERO));
                billItemMapper.updateById(item);
                billIds.add(item.getBillId());
            }
        }
        for (Long billId : billIds) {
            recalcBill(billId);
        }
    }

    /** 重算账单主表金额 */
    private void recalcBill(Long billId) {
        List<FinanceBillItem> items = billItemMapper.selectList(
                new LambdaQueryWrapper<FinanceBillItem>().eq(FinanceBillItem::getBillId, billId));
        BigDecimal total = BigDecimal.ZERO, paid = BigDecimal.ZERO, unpaid = BigDecimal.ZERO;
        for (FinanceBillItem it : items) {
            total = total.add(it.getAmount() != null ? it.getAmount() : BigDecimal.ZERO);
            paid = paid.add(it.getPaidAmount() != null ? it.getPaidAmount() : BigDecimal.ZERO);
            unpaid = unpaid.add(it.getUnpaidAmount() != null ? it.getUnpaidAmount() : BigDecimal.ZERO);
        }
        FinanceBill b = new FinanceBill();
        b.setId(billId);
        b.setTotalAmount(total);
        b.setPaidAmount(paid);
        b.setUnpaidAmount(unpaid);
        billMapper.updateById(b);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        FinancePayment payment = paymentMapper.selectById(id);
        if (payment == null) throw new BusinessException("付款单不存在");
        if (!DocStatus.AUDITED.name().equals(payment.getStatus())) throw new BusinessException("只有已审核的付款单可反审核");
        // 1) 反向扣减账单明细已付金额（必须在删除核销流水之前调用，否则流水已被删无法反查）
        reverseBillProgress(id);
        // 2) 反向核销应付台账：按核销流水精确冲销（双向可追溯）
        List<FinanceSettlement> settlements = settlementMapper.selectList(
                new LambdaQueryWrapper<FinanceSettlement>()
                        .eq(FinanceSettlement::getReceiptPaymentId, id)
                        .eq(FinanceSettlement::getDirection, SettlementDirection.PAY.getCode()));
        for (FinanceSettlement st : settlements) {
            FinancePayable p = payableMapper.selectById(st.getPayableReceivableId());
            if (p == null) continue;
            BigDecimal amt = st.getAmount() != null ? st.getAmount() : BigDecimal.ZERO;
            BigDecimal newPaid = (p.getPaidAmount() != null ? p.getPaidAmount() : BigDecimal.ZERO).subtract(amt);
            BigDecimal newUnpaid = (p.getUnpaidAmount() != null ? p.getUnpaidAmount() : BigDecimal.ZERO).add(amt);
            p.setPaidAmount(newPaid.max(BigDecimal.ZERO));
            p.setUnpaidAmount(newUnpaid);
            p.setStatus(newUnpaid.compareTo(BigDecimal.ZERO) <= 0 ? SettlementStatus.SETTLED.getCode() : SettlementStatus.UNSETTLED.getCode());
            payableMapper.updateById(p);
            // 冲销后删除核销流水记录
            settlementMapper.deleteById(st.getId());
        }
        // 3) 删除本付款单产生的预付单（负数应付，物理删除，审计链由付款单+资金流水+核销流水保留）
        List<FinancePayable> advances = payableMapper.selectList(
                new LambdaQueryWrapper<FinancePayable>()
                        .eq(FinancePayable::getSourceBillType, SettlementStatus.ADVANCE.getCode())
                        .eq(FinancePayable::getSourceId, id));
        for (FinancePayable adv : advances) {
            payableMapper.deleteById(adv.getId());
        }
        // 4) 写冲正资金流水（保留审计轨迹，不删除原流水；账户余额由流水实时累计）
        FinanceCashflow cf = new FinanceCashflow();
        cf.setFlowNo(gen(BillPrefix.PAYMENT, cashflowMapper));
        cf.setAccountId(payment.getAccountId());
        cf.setAccountName(payment.getAccountName());
        cf.setFlowType(CashflowType.PAYMENT_REVERSE.getCode());
        cf.setRelatedBillNo(payment.getCode());
        cf.setRelatedBillType("付款单");
        cf.setIncome(payment.getAmount());
        cf.setExpense(BigDecimal.ZERO);
        cf.setRemark("反审核冲正");
        cashflowMapper.insert(cf);
        FinancePayment u = new FinancePayment(); u.setId(id); u.setStatus(DocStatus.DRAFT.name()); paymentMapper.updateById(u);
    }

    private String gen(String prefix, FinancePaymentMapper mapper) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        LambdaQueryWrapper<FinancePayment> w = new LambdaQueryWrapper<FinancePayment>()
                .likeRight(FinancePayment::getCode, pat).orderByDesc(FinancePayment::getCode).last("LIMIT 1");
        FinancePayment last = mapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return prefix + d + String.format("%03d", seq);
    }

    private String gen(String prefix, FinanceCashflowMapper mapper) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        LambdaQueryWrapper<FinanceCashflow> w = new LambdaQueryWrapper<FinanceCashflow>()
                .likeRight(FinanceCashflow::getFlowNo, pat).orderByDesc(FinanceCashflow::getFlowNo).last("LIMIT 1");
        FinanceCashflow last = mapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getFlowNo() != null) {
            try { seq = Integer.parseInt(last.getFlowNo().substring(last.getFlowNo().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return prefix + d + String.format("%03d", seq);
    }
}
