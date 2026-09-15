package com.beichen.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.beichen.erp.finance.entity.*;
import com.beichen.erp.finance.common.CashflowRelatedType;
import com.beichen.erp.finance.common.CashflowType;
import com.beichen.erp.finance.common.SettlementDirection;
import com.beichen.erp.finance.common.SettlementSourceType;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SettlementRecordStatus;
import com.beichen.erp.finance.common.SubjectType;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
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
    private final SupplierMapper supplierMapper;

    @Override
    public Page<Map<String, Object>> page(Long customerId, Long supplierId, String subjectType, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<FinanceReceipt> w = new LambdaQueryWrapper<FinanceReceipt>()
                .eq(customerId != null, FinanceReceipt::getCustomerId, customerId)
                .eq(supplierId != null, FinanceReceipt::getSupplierId, supplierId)
                .eq(subjectType != null && !subjectType.isBlank(), FinanceReceipt::getSubjectType, subjectType)
                .eq(status != null && !status.isBlank(), FinanceReceipt::getStatus, status)
                .orderByDesc(FinanceReceipt::getId);
        Page<FinanceReceipt> raw = receiptMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId()); m.put("code", r.getCode());
            m.put("customerId", r.getCustomerId()); m.put("customerName", r.getCustomerName());
            m.put("subjectType", r.getSubjectType());
            m.put("supplierId", r.getSupplierId()); m.put("supplierName", r.getSupplierName());
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
        // 主体类型：客户收款（默认） / 供应商收款（应付转应收的收款闭环）
        String subject = receipt.getSubjectType() == null || receipt.getSubjectType().isBlank()
                ? SubjectType.CUSTOMER.getCode() : receipt.getSubjectType();
        if (SubjectType.fromCode(subject) == null) throw new BusinessException("主体类型不合法：" + subject);
        receipt.setSubjectType(SubjectType.fromCode(subject).getCode());
        if (SubjectType.SUPPLIER.getCode().equals(receipt.getSubjectType())) {
            // 供应商收款：挂供应商（应付转应收产生的应收，向对方收回退货/扣款）
            if (receipt.getSupplierId() == null) throw new BusinessException("供应商不能为空");
            Supplier sup = supplierMapper.selectById(receipt.getSupplierId());
            receipt.setSupplierName(sup != null ? sup.getName() : "");
            receipt.setCustomerId(null);
            receipt.setCustomerName("");
        } else {
            if (receipt.getCustomerId() == null) throw new BusinessException("客户不能为空");
            Customer cust = customerMapper.selectById(receipt.getCustomerId());
            receipt.setCustomerName(cust != null ? cust.getName() : "");
            receipt.setSupplierId(null);
            receipt.setSupplierName("");
        }
        if (receipt.getAccountId() == null) throw new BusinessException("收款账户不能为空");
        FinanceAccount acc = accountMapper.selectById(receipt.getAccountId());
        receipt.setAccountName(acc != null ? acc.getAccountName() : "");
        receipt.setCode(gen());
        receipt.setStatus(DocStatus.DRAFT.getCode());
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
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败
        if (!DocStatusGuard.claim(receiptMapper, FinanceReceipt::getId, id, FinanceReceipt::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废");
        FinanceReceipt u = new FinanceReceipt(); u.setId(id); u.setStatus(DocStatus.CANCELLED.getCode()); receiptMapper.updateById(u);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        FinanceReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) throw new BusinessException("收款单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败 —— 否则会重复核销应收、重复写核销与资金流水
        if (!DocStatusGuard.claim(receiptMapper, FinanceReceipt::getId, id, FinanceReceipt::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        List<FinanceReceiptItem> items = itemMapper.selectList(new LambdaQueryWrapper<FinanceReceiptItem>().eq(FinanceReceiptItem::getReceiptId, id));
        // 核销应收：更新台账 + 写入核销流水（双向可追溯），超额部分生成负数应收（预收）
        for (FinanceReceiptItem it : items) {
            if (it.getReceivableId() == null) continue;
            FinanceReceivable rec = receivableMapper.selectById(it.getReceivableId());
            if (rec == null) continue;
            // 主体一致性：客户收款只能核销客户应收，供应商收款只能核销供应商应收（防串账）
            String recSubject = rec.getSubjectType() != null ? rec.getSubjectType() : SubjectType.CUSTOMER.getCode();
            if (!recSubject.equals(receipt.getSubjectType()))
                throw new BusinessException("收款主体与被核销应收不一致（收款：" + SubjectType.fromCode(receipt.getSubjectType()).getLabel()
                        + "，应收：" + SubjectType.fromCode(recSubject).getLabel() + "），请分开制单");
            BigDecimal amt = it.getThisAmount() != null ? it.getThisAmount() : BigDecimal.ZERO;
            BigDecimal unpaid = rec.getUnpaidAmount() != null ? rec.getUnpaidAmount() : BigDecimal.ZERO;
            BigDecimal newUnpaid = unpaid.subtract(amt);
            if (newUnpaid.compareTo(BigDecimal.ZERO) < 0) {
                // 超额收款：原应收全额结清，超额部分生成负数应收（预收，我方欠客户）
                BigDecimal over = newUnpaid.negate();
                BigDecimal total = rec.getAmount() != null ? rec.getAmount() : BigDecimal.ZERO;
                // 台账用「未收额 CAS」原子更新（P2-29）：与读取时的未收额一致才更新，防止与其它收款单并发核销同一笔时互相覆盖
                int rows = receivableMapper.update(null, new LambdaUpdateWrapper<FinanceReceivable>()
                        .eq(FinanceReceivable::getId, rec.getId())
                        .apply("IFNULL(unpaid_amount, 0) = {0}", unpaid)
                        .set(FinanceReceivable::getPaidAmount, total)
                        .set(FinanceReceivable::getUnpaidAmount, BigDecimal.ZERO)
                        .set(FinanceReceivable::getStatus, SettlementStatus.SETTLED.getCode()));
                if (rows == 0) throw new BusinessException("应收台账已被其他单据核销，请刷新后重试");
                // 生成负数应收（预收单），sourceBillType=ADVANCE + sourceId=原收款单id 用于反审核精确删除
                FinanceReceivable advance = new FinanceReceivable();
                advance.setBillNo(rec.getBillNo() + "-" + SettlementStatus.ADVANCE.getCode());
                advance.setCustomerId(rec.getCustomerId());
                advance.setCustomerName(rec.getCustomerName());
                // 预收继承主体类型与供应商信息（供应商收款多收时同样生成负数应收预收）
                advance.setSubjectType(rec.getSubjectType());
                advance.setSupplierId(rec.getSupplierId());
                advance.setSupplierName(rec.getSupplierName());
                advance.setSourceBillType(SettlementStatus.ADVANCE.getCode());
                advance.setSourceBillNo(rec.getSourceBillNo());
                advance.setSourceId(id);
                advance.setAmount(over.negate());
                advance.setPaidAmount(BigDecimal.ZERO);
                advance.setUnpaidAmount(over.negate());
                advance.setDueDate(rec.getDueDate());
                advance.setStatus(SettlementStatus.ADVANCE.getCode());
                advance.setRemark("收款预收（多收，我方欠客户）");
                // 反审核只把预收单置 CANCELLED 留痕，重新审核时复用同一行（bill_no 唯一），避免撞唯一键
                FinanceReceivable existAdv = receivableMapper.selectOne(
                        new LambdaQueryWrapper<FinanceReceivable>()
                                .eq(FinanceReceivable::getBillNo, advance.getBillNo()).last("LIMIT 1"));
                if (existAdv != null) {
                    advance.setId(existAdv.getId());
                    receivableMapper.updateById(advance);
                } else {
                    receivableMapper.insert(advance);
                }
                // 核销流水记录实际核销额 = 原未收额（全额结清）
                FinanceSettlement st = new FinanceSettlement();
                st.setReceiptPaymentId(id);
                st.setPayableReceivableId(it.getReceivableId());
                st.setAmount(unpaid);
                st.setDirection(SettlementDirection.RECEIVE.getCode());
                st.setSourceType(SettlementSourceType.RECEIPT.getCode());
                st.setSourceId(id);
                st.setStatus(SettlementRecordStatus.NORMAL.getCode());
                st.setCompanyId(CompanyContext.get());
                settlementMapper.insert(st);
            } else {
                // 正常核销：台账改为「原子增减 + SQL 内推导状态」（P2-29）——
                // 增量更新天然可并发（不会互相覆盖）；MySQL 的 SET 从左到右求值，故 status 必须放在金额赋值之前（用更新前的金额判断）
                String amtSql = amt.toPlainString();
                int rows = receivableMapper.update(null, new LambdaUpdateWrapper<FinanceReceivable>()
                        .eq(FinanceReceivable::getId, rec.getId())
                        .setSql("status = CASE WHEN IFNULL(unpaid_amount, 0) - (" + amtSql + ") <= 0 THEN '"
                                + SettlementStatus.SETTLED.getCode() + "' ELSE '" + SettlementStatus.PARTIAL.getCode() + "' END")
                        .setSql("paid_amount = IFNULL(paid_amount, 0) + (" + amtSql + ")")
                        .setSql("unpaid_amount = IFNULL(unpaid_amount, 0) - (" + amtSql + ")"));
                if (rows == 0) throw new BusinessException("应收台账不存在，核销失败");
                FinanceSettlement st = new FinanceSettlement();
                st.setReceiptPaymentId(id);
                st.setPayableReceivableId(it.getReceivableId());
                st.setAmount(amt);
                st.setDirection(SettlementDirection.RECEIVE.getCode());
                st.setSourceType(SettlementSourceType.RECEIPT.getCode());
                st.setSourceId(id);
                st.setStatus(SettlementRecordStatus.NORMAL.getCode());
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
        cf.setFlowType(CashflowType.RECEIPT.getCode());
        cf.setRelatedBillNo(receipt.getCode());
        cf.setRelatedBillType(CashflowRelatedType.RECEIPT.getCode());
        cf.setIncome(receipt.getAmount());
        cf.setExpense(BigDecimal.ZERO);
        cashflowMapper.insert(cf);
        FinanceReceipt u = new FinanceReceipt(); u.setId(id); u.setStatus(DocStatus.AUDITED.getCode()); receiptMapper.updateById(u);
    }

    /** 账单进度联动：按核销流水反查账单明细，同步已收金额并重算账单主表（只算有效核销） */
    private void syncBillProgress(Long receiptId) {
        List<FinanceSettlement> sts = settlementMapper.selectList(
                new LambdaQueryWrapper<FinanceSettlement>()
                        .eq(FinanceSettlement::getReceiptPaymentId, receiptId)
                        .eq(FinanceSettlement::getDirection, SettlementDirection.RECEIVE.getCode())
                        .eq(FinanceSettlement::getStatus, SettlementRecordStatus.NORMAL.getCode()));
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

    /** 反审核时反向扣减账单明细已收金额：按核销流水冲减，与 syncBillProgress 累加逻辑对称 */
    private void reverseBillProgress(Long receiptId) {
        List<FinanceSettlement> sts = settlementMapper.selectList(
                new LambdaQueryWrapper<FinanceSettlement>()
                        .eq(FinanceSettlement::getReceiptPaymentId, receiptId)
                        .eq(FinanceSettlement::getDirection, SettlementDirection.RECEIVE.getCode())
                        .eq(FinanceSettlement::getStatus, SettlementRecordStatus.NORMAL.getCode()));
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
        FinanceReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) throw new BusinessException("收款单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免重复反核销与重复冲正流水
        if (!DocStatusGuard.claim(receiptMapper, FinanceReceipt::getId, id, FinanceReceipt::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已审核的收款单可反审核");
        // 1) 反向扣减账单明细已收金额（必须在删除核销流水之前调用，否则流水已被删无法反查）
        reverseBillProgress(id);
        // 2) 反向核销应收台账：按核销流水精确冲销（双向可追溯；只处理有效核销，已冲销的不重复冲）
        List<FinanceSettlement> settlements = settlementMapper.selectList(
                new LambdaQueryWrapper<FinanceSettlement>()
                        .eq(FinanceSettlement::getReceiptPaymentId, id)
                        .eq(FinanceSettlement::getDirection, SettlementDirection.RECEIVE.getCode())
                        .eq(FinanceSettlement::getStatus, SettlementRecordStatus.NORMAL.getCode()));
        for (FinanceSettlement st : settlements) {
            FinanceReceivable rec = receivableMapper.selectById(st.getPayableReceivableId());
            if (rec == null) continue;
            BigDecimal amt = st.getAmount() != null ? st.getAmount() : BigDecimal.ZERO;
            // 冲销台账：原子增减 + SQL 内推导状态（P2-29），与其它并发核销/冲销互不覆盖
            String amtSql = amt.toPlainString();
            int rows = receivableMapper.update(null, new LambdaUpdateWrapper<FinanceReceivable>()
                    .eq(FinanceReceivable::getId, rec.getId())
                    .setSql("status = CASE WHEN IFNULL(unpaid_amount, 0) + (" + amtSql + ") <= 0 THEN '"
                            + SettlementStatus.SETTLED.getCode() + "' ELSE '" + SettlementStatus.UNSETTLED.getCode() + "' END")
                    .setSql("paid_amount = GREATEST(IFNULL(paid_amount, 0) - (" + amtSql + "), 0)")
                    .setSql("unpaid_amount = IFNULL(unpaid_amount, 0) + (" + amtSql + ")"));
            if (rows == 0) throw new BusinessException("应收台账不存在，反核销失败");
            // 冲销核销流水：置 CANCELLED 留痕，不物理删除——否则"这笔款曾核销过哪些应收/多少金额"永久丢失
            FinanceSettlement upSt = new FinanceSettlement();
            upSt.setId(st.getId());
            upSt.setStatus(SettlementRecordStatus.CANCELLED.getCode());
            settlementMapper.updateById(upSt);
        }
        // 3) 冲销本收款单产生的预收单（负数应收）：置 CANCELLED 留痕（重新审核会复用同一行，故不删除）
        List<FinanceReceivable> advances = receivableMapper.selectList(
                new LambdaQueryWrapper<FinanceReceivable>()
                        .eq(FinanceReceivable::getSourceBillType, SettlementStatus.ADVANCE.getCode())
                        .eq(FinanceReceivable::getSourceId, id));
        for (FinanceReceivable adv : advances) {
            FinanceReceivable upAdv = new FinanceReceivable();
            upAdv.setId(adv.getId());
            upAdv.setStatus(SettlementStatus.CANCELLED.getCode());
            receivableMapper.updateById(upAdv);
        }
        // 4) 写冲正资金流水（保留审计轨迹，不删除原流水；账户余额由流水实时累计）
        FinanceCashflow cf = new FinanceCashflow();
        cf.setFlowNo(genFlowNo());
        cf.setAccountId(receipt.getAccountId());
        cf.setAccountName(receipt.getAccountName());
        cf.setFlowType(CashflowType.RECEIPT_REVERSE.getCode());
        cf.setRelatedBillNo(receipt.getCode());
        cf.setRelatedBillType(CashflowRelatedType.RECEIPT.getCode());
        cf.setIncome(BigDecimal.ZERO);
        cf.setExpense(receipt.getAmount());
        cf.setRemark("反审核冲正");
        cashflowMapper.insert(cf);
        FinanceReceipt u = new FinanceReceipt(); u.setId(id); u.setStatus(DocStatus.DRAFT.getCode()); receiptMapper.updateById(u);
    }

    private String gen() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.RECEIPT + d;
        LambdaQueryWrapper<FinanceReceipt> w = new LambdaQueryWrapper<FinanceReceipt>().likeRight(FinanceReceipt::getCode, pat).orderByDesc(FinanceReceipt::getCode).last("LIMIT 1");
        FinanceReceipt last = receiptMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.RECEIPT + d + String.format("%03d", seq);
    }

    private String genFlowNo() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.CASHFLOW + d;
        LambdaQueryWrapper<FinanceCashflow> w = new LambdaQueryWrapper<FinanceCashflow>().likeRight(FinanceCashflow::getFlowNo, pat).orderByDesc(FinanceCashflow::getFlowNo).last("LIMIT 1");
        FinanceCashflow last = cashflowMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getFlowNo() != null) {
            try { seq = Integer.parseInt(last.getFlowNo().substring(last.getFlowNo().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.CASHFLOW + d + String.format("%03d", seq);
    }
}
