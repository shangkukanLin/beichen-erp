package com.beichen.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.entity.*;
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
        receipt.setStatus("草稿");
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
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        FinanceReceipt u = new FinanceReceipt(); u.setId(id); u.setStatus("已作废"); receiptMapper.updateById(u);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        FinanceReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null || !"草稿".equals(receipt.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<FinanceReceiptItem> items = itemMapper.selectList(new LambdaQueryWrapper<FinanceReceiptItem>().eq(FinanceReceiptItem::getReceiptId, id));
        // 核销应收
        for (FinanceReceiptItem it : items) {
            if (it.getReceivableId() == null) continue;
            FinanceReceivable rec = receivableMapper.selectById(it.getReceivableId());
            if (rec == null) continue;
            BigDecimal amt = it.getThisAmount() != null ? it.getThisAmount() : BigDecimal.ZERO;
            BigDecimal newPaid = (rec.getPaidAmount() != null ? rec.getPaidAmount() : BigDecimal.ZERO).add(amt);
            BigDecimal newUnpaid = (rec.getUnpaidAmount() != null ? rec.getUnpaidAmount() : BigDecimal.ZERO).subtract(amt);
            rec.setPaidAmount(newPaid);
            rec.setUnpaidAmount(newUnpaid.max(BigDecimal.ZERO));
            rec.setStatus(newUnpaid.compareTo(BigDecimal.ZERO) <= 0 ? "已结清" : "部分结清");
            receivableMapper.updateById(rec);
        }
        // 更新账户余额
        FinanceAccount acc = accountMapper.selectById(receipt.getAccountId());
        if (acc != null) {
            acc.setBalance(acc.getBalance().add(receipt.getAmount()));
            accountMapper.updateById(acc);
        }
        // 写资金流水
        FinanceCashflow cf = new FinanceCashflow();
        cf.setFlowNo(genFlowNo());
        cf.setAccountId(receipt.getAccountId());
        cf.setAccountName(receipt.getAccountName());
        cf.setFlowType("收款");
        cf.setRelatedBillNo(receipt.getCode());
        cf.setRelatedBillType("收款单");
        cf.setIncome(receipt.getAmount());
        cf.setExpense(BigDecimal.ZERO);
        cf.setBalance(acc != null ? acc.getBalance() : receipt.getAmount());
        cashflowMapper.insert(cf);
        // 更新客户应收余额
        if (receipt.getCustomerId() != null) {
            Customer c = customerMapper.selectById(receipt.getCustomerId());
            if (c != null) {
                Customer u = new Customer();
                u.setId(c.getId());
                u.setReceivableBalance((c.getReceivableBalance() != null ? c.getReceivableBalance() : BigDecimal.ZERO).subtract(receipt.getAmount()));
                customerMapper.updateById(u);
            }
        }
        FinanceReceipt u = new FinanceReceipt(); u.setId(id); u.setStatus("已审核"); receiptMapper.updateById(u);
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
