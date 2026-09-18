package com.beichen.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
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
    private final com.beichen.erp.supplier.mapper.SupplierTypeRefMapper typeRefMapper;
    private final com.beichen.erp.finance.service.PayableHelper payableHelper;

    @Override
    public Page<Map<String, Object>> page(Long supplierId, String supplierType, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<FinancePayment> w = new LambdaQueryWrapper<FinancePayment>()
                .eq(supplierId != null, FinancePayment::getSupplierId, supplierId)
                .eq(supplierType != null && !supplierType.isBlank(), FinancePayment::getSupplierType, supplierType)
                .eq(status != null && !status.isBlank(), FinancePayment::getStatus, status)
                .orderByDesc(FinancePayment::getId);
        Page<FinancePayment> raw = paymentMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId()); m.put("code", p.getCode());
            m.put("supplierId", p.getSupplierId()); m.put("supplierName", p.getSupplierName());
            m.put("supplierType", p.getSupplierType());
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
        // 主体类型固化：优先取供应商标签的第一个类型（与供应商详情展示一致），付款列表可按类型筛选
        if (payment.getSupplierType() == null || payment.getSupplierType().isBlank()) {
            payment.setSupplierType(resolveSupplierType(payment.getSupplierId()));
        }
        FinanceAccount acc = accountMapper.selectById(payment.getAccountId());
        payment.setAccountName(acc != null ? acc.getAccountName() : "");
        payment.setCode(gen(BillPrefix.PAYMENT, paymentMapper));
        payment.setStatus(DocStatus.DRAFT.getCode());
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
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败
        if (!DocStatusGuard.claim(paymentMapper, FinancePayment::getId, id, FinancePayment::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废");
        FinancePayment u = new FinancePayment(); u.setId(id); u.setStatus(DocStatus.CANCELLED.getCode()); paymentMapper.updateById(u);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        FinancePayment payment = paymentMapper.selectById(id);
        if (payment == null) throw new BusinessException("付款单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败 —— 否则会重复核销应付、重复写核销与资金流水
        if (!DocStatusGuard.claim(paymentMapper, FinancePayment::getId, id, FinancePayment::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        List<FinancePaymentItem> items = itemMapper.selectList(new LambdaQueryWrapper<FinancePaymentItem>().eq(FinancePaymentItem::getPaymentId, id));
        // 核销应付：更新台账 + 写入核销流水（双向可追溯），超额部分生成负数应付（预付）
        for (FinancePaymentItem it : items) {
            if (it.getPayableId() == null) continue;
            FinancePayable p = payableMapper.selectById(it.getPayableId());
            if (p == null) continue;
            // I27（2026-09-18 修复，与应收侧对称）：预付台账（ADVANCE，负数应付）不可作为付款核销目标，
            // 否则会生成"预付的预付"（语义错误 + 单号被层层追加后缀）
            if (SettlementStatus.ADVANCE.getCode().equals(p.getStatus()))
                throw new BusinessException("应付单「" + p.getBillNo() + "」是预付台账（多付款待抵扣），不能作为付款核销的目标；如需冲回预付款请走退款或冲销流程");
            BigDecimal amt = it.getThisAmount() != null ? it.getThisAmount() : BigDecimal.ZERO;
            BigDecimal unpaid = p.getUnpaidAmount() != null ? p.getUnpaidAmount() : BigDecimal.ZERO;
            BigDecimal newUnpaid = unpaid.subtract(amt);
            if (newUnpaid.compareTo(BigDecimal.ZERO) < 0) {
                // 超额付款：原应付全额结清，超额部分生成负数应付（预付，供应商欠我方）
                BigDecimal over = newUnpaid.negate();
                BigDecimal total = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
                // 台账用「未付额 CAS」原子更新（P2-29）：与读取时的未付额一致才更新，防止与其它付款单并发核销同一笔时互相覆盖
                int rows = payableMapper.update(null, new LambdaUpdateWrapper<FinancePayable>()
                        .eq(FinancePayable::getId, p.getId())
                        .apply("IFNULL(unpaid_amount, 0) = {0}", unpaid)
                        .set(FinancePayable::getPaidAmount, total)
                        .set(FinancePayable::getUnpaidAmount, BigDecimal.ZERO)
                        .set(FinancePayable::getStatus, SettlementStatus.SETTLED.getCode()));
                if (rows == 0) throw new BusinessException("应付台账已被其他单据核销，请刷新后重试");
                // 生成负数应付（预付单），sourceBillType=ADVANCE + sourceId=原付款单id 用于反审核精确定位
                // D5 口径（2026-09-12）：台账号走 YF- 流水号，不再拼接"付款单号-ADVANCE" —— 与 D1「应付台账号一律 YF-」统一；
                // 与来源的关联由 source_bill_type/source_bill_no/source_id 承载（反审核按 source_id 精确冲回）
                FinancePayable advance = new FinancePayable();
                advance.setBillNo(payableHelper.newBillNo());
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
                // 反审核只把预付单置 CANCELLED 留痕，重新审核复用同一行（bill_no 唯一），避免撞唯一键
                FinancePayable existAdv = payableMapper.selectOne(
                        new LambdaQueryWrapper<FinancePayable>()
                                .eq(FinancePayable::getBillNo, advance.getBillNo()).last("LIMIT 1"));
                if (existAdv != null) {
                    advance.setId(existAdv.getId());
                    payableMapper.updateById(advance);
                } else {
                    payableMapper.insert(advance);
                }
                // 核销流水记录实际核销额 = 原未付额（全额结清）
                FinanceSettlement st = new FinanceSettlement();
                st.setReceiptPaymentId(id);
                st.setPayableReceivableId(it.getPayableId());
                st.setAmount(unpaid);
                st.setDirection(SettlementDirection.PAY.getCode());
                st.setSourceType(SettlementSourceType.PAYMENT.getCode());
                st.setSourceId(id);
                st.setStatus(SettlementRecordStatus.NORMAL.getCode());
                st.setCompanyId(CompanyContext.get());
                settlementMapper.insert(st);
            } else {
                // 正常核销：台账改为「原子增减 + SQL 内推导状态」（P2-29）——
                // 增量更新天然可并发（不会互相覆盖）；MySQL 的 SET 从左到右求值，故 status 必须放在金额赋值之前（用更新前的金额判断）
                String amtSql = amt.toPlainString();
                int rows = payableMapper.update(null, new LambdaUpdateWrapper<FinancePayable>()
                        .eq(FinancePayable::getId, p.getId())
                        .setSql("status = CASE WHEN IFNULL(unpaid_amount, 0) - (" + amtSql + ") <= 0 THEN '"
                                + SettlementStatus.SETTLED.getCode() + "' ELSE '" + SettlementStatus.PARTIAL.getCode() + "' END")
                        .setSql("paid_amount = IFNULL(paid_amount, 0) + (" + amtSql + ")")
                        .setSql("unpaid_amount = IFNULL(unpaid_amount, 0) - (" + amtSql + ")"));
                if (rows == 0) throw new BusinessException("应付台账不存在，核销失败");
                FinanceSettlement st = new FinanceSettlement();
                st.setReceiptPaymentId(id);
                st.setPayableReceivableId(it.getPayableId());
                st.setAmount(amt);
                st.setDirection(SettlementDirection.PAY.getCode());
                st.setSourceType(SettlementSourceType.PAYMENT.getCode());
                st.setSourceId(id);
                st.setStatus(SettlementRecordStatus.NORMAL.getCode());
                st.setCompanyId(CompanyContext.get());
                settlementMapper.insert(st);
            }
        }
        // 账单联动：核销后反向更新账单明细已付金额（账单=结算快照，随核销进度同步）
        syncBillProgress(id);
        // 写资金流水（账户余额由流水实时累计，不再维护余额快照）
        FinanceCashflow cf = new FinanceCashflow();
        cf.setFlowNo(gen(BillPrefix.CASHFLOW, cashflowMapper));
        cf.setAccountId(payment.getAccountId());
        cf.setAccountName(payment.getAccountName());
        cf.setFlowType(CashflowType.PAYMENT.getCode());
        cf.setRelatedBillNo(payment.getCode());
        cf.setRelatedBillType(CashflowRelatedType.PAYMENT.getCode());
        cf.setIncome(BigDecimal.ZERO);
        cf.setExpense(payment.getAmount());
        cashflowMapper.insert(cf);
        // 更新付款单状态
        FinancePayment u = new FinancePayment(); u.setId(id); u.setStatus(DocStatus.AUDITED.getCode()); paymentMapper.updateById(u);
    }

    /** 账单进度联动：按核销流水反查账单明细，同步已付金额并重算账单主表（只算有效核销） */
    private void syncBillProgress(Long paymentId) {
        List<FinanceSettlement> sts = settlementMapper.selectList(
                new LambdaQueryWrapper<FinanceSettlement>()
                        .eq(FinanceSettlement::getReceiptPaymentId, paymentId)
                        .eq(FinanceSettlement::getDirection, SettlementDirection.PAY.getCode())
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
                        .eq(FinanceSettlement::getDirection, SettlementDirection.PAY.getCode())
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
        FinancePayment payment = paymentMapper.selectById(id);
        if (payment == null) throw new BusinessException("付款单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免重复反核销与重复冲正流水
        if (!DocStatusGuard.claim(paymentMapper, FinancePayment::getId, id, FinancePayment::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已审核的付款单可反审核");
        // 1) 反向扣减账单明细已付金额（必须在删除核销流水之前调用，否则流水已被删无法反查）
        reverseBillProgress(id);
        // 2) 反向核销应付台账：按核销流水精确冲销（双向可追溯；只处理有效核销，已冲销的不重复冲）
        List<FinanceSettlement> settlements = settlementMapper.selectList(
                new LambdaQueryWrapper<FinanceSettlement>()
                        .eq(FinanceSettlement::getReceiptPaymentId, id)
                        .eq(FinanceSettlement::getDirection, SettlementDirection.PAY.getCode())
                        .eq(FinanceSettlement::getStatus, SettlementRecordStatus.NORMAL.getCode()));
        for (FinanceSettlement st : settlements) {
            FinancePayable p = payableMapper.selectById(st.getPayableReceivableId());
            if (p == null) continue;
            BigDecimal amt = st.getAmount() != null ? st.getAmount() : BigDecimal.ZERO;
            // 冲销台账：原子增减 + SQL 内推导状态（P2-29），与其它并发核销/冲销互不覆盖
            String amtSql = amt.toPlainString();
            int rows = payableMapper.update(null, new LambdaUpdateWrapper<FinancePayable>()
                    .eq(FinancePayable::getId, p.getId())
                    .setSql("status = CASE WHEN IFNULL(unpaid_amount, 0) + (" + amtSql + ") <= 0 THEN '"
                            + SettlementStatus.SETTLED.getCode() + "' ELSE '" + SettlementStatus.UNSETTLED.getCode() + "' END")
                    .setSql("paid_amount = GREATEST(IFNULL(paid_amount, 0) - (" + amtSql + "), 0)")
                    .setSql("unpaid_amount = IFNULL(unpaid_amount, 0) + (" + amtSql + ")"));
            if (rows == 0) throw new BusinessException("应付台账不存在，反核销失败");
            // 冲销核销流水：置 CANCELLED 留痕，不物理删除——否则"这笔款曾核销过哪些应付/多少金额"永久丢失
            FinanceSettlement upSt = new FinanceSettlement();
            upSt.setId(st.getId());
            upSt.setStatus(SettlementRecordStatus.CANCELLED.getCode());
            settlementMapper.updateById(upSt);
        }
        // 3) 冲销本付款单产生的预付单（负数应付）：置 CANCELLED 留痕（重新审核复用同一行，故不删除）
        List<FinancePayable> advances = payableMapper.selectList(
                new LambdaQueryWrapper<FinancePayable>()
                        .eq(FinancePayable::getSourceBillType, SettlementStatus.ADVANCE.getCode())
                        .eq(FinancePayable::getSourceId, id));
        for (FinancePayable adv : advances) {
            // I29 口径（2026-09-18）：作废预付台账时**金额一并清零**（原金额记入备注留痕），
            // 与应付反审核冲销保持同一口径（旧实现只置状态，作废行仍带金额）
            payableHelper.cancelLedger(adv);
        }
        // 4) 写冲正资金流水（保留审计轨迹，不删除原流水；账户余额由流水实时累计）
        FinanceCashflow cf = new FinanceCashflow();
        cf.setFlowNo(gen(BillPrefix.CASHFLOW, cashflowMapper));
        cf.setAccountId(payment.getAccountId());
        cf.setAccountName(payment.getAccountName());
        cf.setFlowType(CashflowType.PAYMENT_REVERSE.getCode());
        cf.setRelatedBillNo(payment.getCode());
        cf.setRelatedBillType(CashflowRelatedType.PAYMENT.getCode());
        cf.setIncome(payment.getAmount());
        cf.setExpense(BigDecimal.ZERO);
        cf.setRemark("反审核冲正");
        cashflowMapper.insert(cf);
        FinancePayment u = new FinancePayment(); u.setId(id); u.setStatus(DocStatus.DRAFT.getCode()); paymentMapper.updateById(u);
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

    /** 按供应商标签取主体类型（多标签取字典序第一个），无标签返回 null */
    private String resolveSupplierType(Long supplierId) {
        if (supplierId == null) return null;
        var refs = typeRefMapper.selectList(new LambdaQueryWrapper<com.beichen.erp.supplier.entity.SupplierTypeRef>()
                .eq(com.beichen.erp.supplier.entity.SupplierTypeRef::getSupplierId, supplierId)
                .orderByAsc(com.beichen.erp.supplier.entity.SupplierTypeRef::getTypeCode));
        return refs.isEmpty() ? null : refs.get(0).getTypeCode();
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
