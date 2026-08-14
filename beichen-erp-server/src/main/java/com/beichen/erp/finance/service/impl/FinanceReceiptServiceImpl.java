package com.beichen.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.entity.*;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.mapper.*;
import com.beichen.erp.finance.service.FinanceReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FinanceReceiptServiceImpl implements FinanceReceiptService {

    private final FinanceReceiptMapper receiptMapper;
    private final FinanceReceiptItemMapper itemMapper;
    private final FinanceReceivableMapper receivableMapper;
    private final FinanceAccountMapper accountMapper;
    private final FinanceCashflowMapper cashflowMapper;
    private final CustomerMapper customerMapper;
    private final FinanceSettlementMapper settlementMapper;
    private final FinanceBillItemMapper billItemMapper;
    private final FinanceBillMapper billMapper;

    @Override
    public Page<Map<String, Object>> page(Long customerId, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<FinanceReceipt> w = new LambdaQueryWrapper<FinanceReceipt>()
                .eq(customerId != null, FinanceReceipt::getCustomerId, customerId)
                .eq(status != null && !status.isBlank(), FinanceReceipt::getStatus, status)
                .orderByDesc(FinanceReceipt::getId);
        Page<FinanceReceipt> raw = receiptMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId()); m.put("code", r.getCode());
            m.put("customerId", r.getCustomerId()); m.put("customerName", r.getCustomerName());
            m.put("accountId", r.getAccountId()); m.put("accountName", r.getAccountName());
            m.put("receiptDate", r.getReceiptDate()); m.put("amount", r.getAmount());
            m.put("status", r.getStatus()); m.put("remark", r.getRemark());
            m.put("createTime", r.getCreateTime());
            return m;
        }).toList());
        return res;
    }

    @Override public FinanceReceipt getById(Long id) { return receiptMapper.selectById(id); }
    @Override public List<FinanceReceiptItem> getItems(Long receiptId) {
        return itemMapper.selectList(new LambdaQueryWrapper<FinanceReceiptItem>().eq(FinanceReceiptItem::getReceiptId, receiptId));
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void create(FinanceReceipt receipt, List<FinanceReceiptItem> items) {
        if (receipt.getCustomerId() == null) throw new BusinessException("客户不能为空");
        if (receipt.getAccountId() == null) throw new BusinessException("收款账户不能为空");
        Customer cust = customerMapper.selectById(receipt.getCustomerId());
        receipt.setCustomerName(cust != null ? cust.getName() : "");
        FinanceAccount acc = accountMapper.selectById(receipt.getAccountId());
        receipt.setAccountName(acc != null ? acc.getAccountName() : "");
        receipt.setCode(gen());
        receipt.setStatus(DocStatus.DRAFT.name());
        Long cid = CompanyContext.get();
        BigDecimal total = BigDecimal.ZERO;
        if (cid != null && cid > 0) receipt.setCompanyId(cid);
        receiptMapper.insert(receipt);
        for (FinanceReceiptItem it : items) {
            it.setId(null); it.setReceiptId(receipt.getId());
            total = total.add(it.getThisAmount() != null ? it.getThisAmount() : BigDecimal.ZERO);
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        FinanceReceipt u = new FinanceReceipt(); u.setId(receipt.getId()); u.setAmount(total); receiptMapper.updateById(u);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        FinanceReceipt old = receiptMapper.selectById(id);
        if (old == null) throw new BusinessException("收款单不存在");
        if (!DocStatus.DRAFT.name().equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        FinanceReceipt u = new FinanceReceipt(); u.setId(id); u.setStatus(DocStatus.CANCELLED.name()); receiptMapper.updateById(u);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        FinanceReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null || !DocStatus.DRAFT.name().equals(receipt.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<FinanceReceiptItem> items = itemMapper.selectList(new LambdaQueryWrapper<FinanceReceiptItem>().eq(FinanceReceiptItem::getReceiptId, id));
        // 核销应收：更新台账 + 写入核销流水（双向可追溯），超额部分生成负数应收（预收）
        for (FinanceReceiptItem it : items) {
            if (it.getReceivableId() == null) continue;
            FinanceReceivable rec = receivableMapper.selectById(it.getReceivableId());
            if (rec == null) continue;
            BigDecimal amt = it.getThisAmount() != null ? it.getThisAmount() : BigDecimal.ZERO;
            BigDecimal unpaid = rec.getUnpaidAmount() != null ? rec.getUnpaidAmount() : BigDecimal.ZERO;
            BigDecimal newUnpaid = unpaid.subtract(amt);
            if (newUnpaid.compareTo(BigDecimal.ZERO) < 0) {
                // 超额收款：原应收全额结清，超额部分生成负数应收（预收，我方欠客户）
                BigDecimal over = newUnpaid.negate();
                BigDecimal total = rec.getAmount() != null ? rec.getAmount() : BigDecimal.ZERO;
                rec.setPaidAmount(total);
                rec.setUnpaidAmount(BigDecimal.ZERO);
                rec.setStatus(SettlementStatus.SETTLED.getCode());
                receivableMapper.updateById(rec);
                // 生成负数应收（预收单），sourceBillType=ADVANCE + sourceId=原收款单id 用于反审核精确删除
                FinanceReceivable advance = new FinanceReceivable();
                advance.setBillNo(rec.getBillNo() + "-ADV");
                advance.setCustomerId(rec.getCustomerId());
                advance.setCustomerName(rec.getCustomerName());
                advance.setSourceBillType("ADVANCE");
                advance.setSourceBillNo(rec.getSourceBillNo());
                advance.setSourceId(id);
                advance.setAmount(over.negate());
                advance.setPaidAmount(BigDecimal.ZERO);
                advance.setUnpaidAmount(over.negate());
                advance.setDueDate(rec.getDueDate());
                advance.setStatus(SettlementStatus.ADVANCE.getCode());
                advance.setRemark("收款预收（多收，我方欠客户）");
                receivableMapper.insert(advance);
                // 核销流水记录实际核销额 = 原未收额（全额结清）
                FinanceSettlement st = new FinanceSettlement();
                st.setReceiptPaymentId(id);
                st.setPayableReceivableId(it.getReceivableId());
                st.setAmount(unpaid);
                st.setDirection("RECEIVE");
                st.setSourceType("RECEIPT");
                st.setSourceId(id);
                st.setCompanyId(CompanyContext.get());
                settlementMapper.insert(st);
            } else {
                // 正常核销
                BigDecimal newPaid = (rec.getPaidAmount() != null ? rec.getPaidAmount() : BigDecimal.ZERO).add(amt);
                rec.setPaidAmount(newPaid);
                rec.setUnpaidAmount(newUnpaid);
                rec.setStatus(newUnpaid.compareTo(BigDecimal.ZERO) <= 0 ? SettlementStatus.SETTLED.getCode() : SettlementStatus.PARTIAL.getCode());
                receivableMapper.updateById(rec);
                FinanceSettlement st = new FinanceSettlement();
                st.setReceiptPaymentId(id);
                st.setPayableReceivableId(it.getReceivableId());
                st.setAmount(amt);
                st.setDirection("RECEIVE");
                st.setSourceType("RECEIPT");
                st.setSourceId(id);
                st.setCompanyId(CompanyContext.get());
                settlementMapper.insert(st);
            }
        }
        // 账单联动：核销后反向更新账单明细已收金额（账单=结算快照，随核销进度同步）
        syncBillProgress(id);
        // 写资金流水（账户余额由流水实时累计，不再维护余额快照）
        FinanceCashflow cf = new FinanceCashflow();
        cf.setFlowNo(genFlowNo());
        cf.setAccountId(receipt.getAccountId());
        cf.setAccountName(receipt.getAccountName());
        cf.setFlowType("收款");
        cf.setRelatedBillNo(receipt.getCode());
        cf.setRelatedBillType("收款单");
        cf.setIncome(receipt.getAmount());
        cf.setExpense(BigDecimal.ZERO);
        cashflowMapper.insert(cf);
        FinanceReceipt u = new FinanceReceipt(); u.setId(id); u.setStatus(DocStatus.AUDITED.name()); receiptMapper.updateById(u);
    }

    /** 账单进度联动：按核销流水反查账单明细，同步已收金额并重算账单主表 */
    private void syncBillProgress(Long receiptId) {
        List<FinanceSettlement> sts = settlementMapper.selectList(
                new LambdaQueryWrapper<FinanceSettlement>()
                        .eq(FinanceSettlement::getReceiptPaymentId, receiptId)
                        .eq(FinanceSettlement::getDirection, "RECEIVE"));
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
        FinanceReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) throw new BusinessException("收款单不存在");
        if (!DocStatus.AUDITED.name().equals(receipt.getStatus())) throw new BusinessException("只有已审核的收款单可反审核");
        // 1) 反向核销应收台账：按核销流水精确冲销（双向可追溯）
        List<FinanceSettlement> settlements = settlementMapper.selectList(
                new LambdaQueryWrapper<FinanceSettlement>()
                        .eq(FinanceSettlement::getReceiptPaymentId, id)
                        .eq(FinanceSettlement::getDirection, "RECEIVE"));
        for (FinanceSettlement st : settlements) {
            FinanceReceivable rec = receivableMapper.selectById(st.getPayableReceivableId());
            if (rec == null) continue;
            BigDecimal amt = st.getAmount() != null ? st.getAmount() : BigDecimal.ZERO;
            BigDecimal newPaid = (rec.getPaidAmount() != null ? rec.getPaidAmount() : BigDecimal.ZERO).subtract(amt);
            BigDecimal newUnpaid = (rec.getUnpaidAmount() != null ? rec.getUnpaidAmount() : BigDecimal.ZERO).add(amt);
            rec.setPaidAmount(newPaid.max(BigDecimal.ZERO));
            rec.setUnpaidAmount(newUnpaid);
            rec.setStatus(newUnpaid.compareTo(BigDecimal.ZERO) <= 0 ? SettlementStatus.SETTLED.getCode() : SettlementStatus.UNSETTLED.getCode());
            receivableMapper.updateById(rec);
            // 冲销后删除核销流水记录
            settlementMapper.deleteById(st.getId());
        }
        // 2) 删除本收款单产生的预收单（负数应收，物理删除，审计链由收款单+资金流水+核销流水保留）
        List<FinanceReceivable> advances = receivableMapper.selectList(
                new LambdaQueryWrapper<FinanceReceivable>()
                        .eq(FinanceReceivable::getSourceBillType, "ADVANCE")
                        .eq(FinanceReceivable::getSourceId, id));
        for (FinanceReceivable adv : advances) {
            receivableMapper.deleteById(adv.getId());
        }
        // 3) 写冲正资金流水（保留审计轨迹，不删除原流水；账户余额由流水实时累计）
        FinanceCashflow cf = new FinanceCashflow();
        cf.setFlowNo(genFlowNo());
        cf.setAccountId(receipt.getAccountId());
        cf.setAccountName(receipt.getAccountName());
        cf.setFlowType("收款冲正");
        cf.setRelatedBillNo(receipt.getCode());
        cf.setRelatedBillType("收款单");
        cf.setIncome(BigDecimal.ZERO);
        cf.setExpense(receipt.getAmount());
        cf.setRemark("反审核冲正");
        cashflowMapper.insert(cf);
        FinanceReceipt u = new FinanceReceipt(); u.setId(id); u.setStatus(DocStatus.DRAFT.name()); receiptMapper.updateById(u);
    }

    private String gen() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = "SK-" + d;
        LambdaQueryWrapper<FinanceReceipt> w = new LambdaQueryWrapper<FinanceReceipt>().likeRight(FinanceReceipt::getCode, pat).orderByDesc(FinanceReceipt::getCode).last("LIMIT 1");
        FinanceReceipt last = receiptMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return "SK-" + d + String.format("%03d", seq);
    }

    private String genFlowNo() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = "FL-" + d;
        LambdaQueryWrapper<FinanceCashflow> w = new LambdaQueryWrapper<FinanceCashflow>().likeRight(FinanceCashflow::getFlowNo, pat).orderByDesc(FinanceCashflow::getFlowNo).last("LIMIT 1");
        FinanceCashflow last = cashflowMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getFlowNo() != null) {
            try { seq = Integer.parseInt(last.getFlowNo().substring(last.getFlowNo().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return "FL-" + d + String.format("%03d", seq);
    }
}
