package com.beichen.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.entity.*;
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
        payment.setCode(gen("FK-", paymentMapper));
        payment.setStatus("草稿");
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
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        FinancePayment u = new FinancePayment(); u.setId(id); u.setStatus("已作废"); paymentMapper.updateById(u);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        FinancePayment payment = paymentMapper.selectById(id);
        if (payment == null || !"草稿".equals(payment.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<FinancePaymentItem> items = itemMapper.selectList(new LambdaQueryWrapper<FinancePaymentItem>().eq(FinancePaymentItem::getPaymentId, id));
        // 核销应付
        for (FinancePaymentItem it : items) {
            if (it.getPayableId() == null) continue;
            FinancePayable p = payableMapper.selectById(it.getPayableId());
            if (p == null) continue;
            BigDecimal amt = it.getThisAmount() != null ? it.getThisAmount() : BigDecimal.ZERO;
            BigDecimal newPaid = (p.getPaidAmount() != null ? p.getPaidAmount() : BigDecimal.ZERO).add(amt);
            BigDecimal newUnpaid = (p.getUnpaidAmount() != null ? p.getUnpaidAmount() : BigDecimal.ZERO).subtract(amt);
            p.setPaidAmount(newPaid);
            p.setUnpaidAmount(newUnpaid.max(BigDecimal.ZERO));
            p.setStatus(newUnpaid.compareTo(BigDecimal.ZERO) <= 0 ? "已结清" : "部分结清");
            payableMapper.updateById(p);
        }
        // 更新账户余额
        FinanceAccount acc = accountMapper.selectById(payment.getAccountId());
        if (acc != null) {
            acc.setBalance(acc.getBalance().subtract(payment.getAmount()));
            accountMapper.updateById(acc);
        }
        // 写资金流水
        FinanceCashflow cf = new FinanceCashflow();
        cf.setFlowNo(gen("FK-", cashflowMapper));
        cf.setAccountId(payment.getAccountId());
        cf.setAccountName(payment.getAccountName());
        cf.setFlowType("付款");
        cf.setRelatedBillNo(payment.getCode());
        cf.setRelatedBillType("付款单");
        cf.setIncome(BigDecimal.ZERO);
        cf.setExpense(payment.getAmount());
        cf.setBalance(acc != null ? acc.getBalance() : BigDecimal.ZERO);
        cashflowMapper.insert(cf);
        // 更新付款单状态
        FinancePayment u = new FinancePayment(); u.setId(id); u.setStatus("已审核"); paymentMapper.updateById(u);
    }

    private <T> String gen(String prefix, com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        // Simple gen using code field reflection not feasible without entity; pass in.
        // Since all our entities have code field, we use the specific mapper's code field.
        // For generic usage, we rely on each entity having 'code' column.
        return prefix + d + String.format("%03d", 1);
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
