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
import com.beichen.erp.finance.service.ReceivableHelper;
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
    private final ReceivableHelper receivableHelper;

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
            // 来源单据（2026-09-18）：销售单现金结算自动生成的收款单，列表可显示来源单号
            m.put("sourceBillType", r.getSourceBillType()); m.put("sourceBillNo", r.getSourceBillNo());
            m.put("sourceId", r.getSourceId());
            m.put("createTime", r.getCreateTime());
            return m;
        }).toList());
        return res;
    }

    @Override public FinanceReceipt getById(Long id) { return receiptMapper.selectById(id); }
    @Override public List<FinanceReceiptItem> getItems(Long receiptId) {
        return itemMapper.selectList(new LambdaQueryWrapper<FinanceReceiptItem>().eq(FinanceReceiptItem::getReceiptId, receiptId));
    }

    /** 按来源单据查收款单（含已作废，调用方自行判状态）：2026-09-18 销售单现金结算联动用 */
    @Override
    public List<FinanceReceipt> findBySource(String sourceBillType, Long sourceId) {
        if (sourceBillType == null || sourceBillType.isBlank() || sourceId == null) return List.of();
        return receiptMapper.selectList(new LambdaQueryWrapper<FinanceReceipt>()
                .eq(FinanceReceipt::getSourceBillType, sourceBillType)
                .eq(FinanceReceipt::getSourceId, sourceId)
                .orderByAsc(FinanceReceipt::getId));
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
        int rowNo = 0;
        for (FinanceReceiptItem it : items) {
            rowNo++;
            // F7-33（2026-09-19）：建单即拦负数金额（审核侧另有兜底，覆盖历史草稿）
            if (it.getThisAmount() != null && it.getThisAmount().compareTo(BigDecimal.ZERO) < 0)
                throw new BusinessException("收款明细金额不能为负数");
            // F7-37（2026-09-19）：明细必须关联应收台账 —— 缺台账的明细在审核时会被**静默跳过**，
            // 而资金流水仍按全额入账（钱进账、应收没减）。若要收"没有对应应收"的钱，请改为对该应收**超额收款**
            // （超额分支会自动生成预收 ADVANCE 台账，口径见 ReceivableHelper.advanceBillNo）。
            if (it.getReceivableId() == null)
                throw new BusinessException("收款明细第 " + rowNo + " 行未关联应收台账（不能只填金额，请从「未收款」里选择应收单）");
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

    /**
     * F7-31（2026-09-19）：核销对象必须与收款单属于**同一往来单位**。
     *
     * <p>客户收款只核销同一客户的应收；供应商收款（如应付转应收生成的台账）只核销同一供应商的应收。
     * 修复前仅比对 {@code subjectType}，同为 {@code CUSTOMER} 时 A 客户的收款单可核销 B 客户的应收
     * （实测复现：客户 24 的收款单把客户 16 的应收核销掉），造成双向错账且不可逆。</p>
     */
    private void assertSamePartner(FinanceReceipt receipt, FinanceReceivable rec) {
        if (SubjectType.SUPPLIER.getCode().equals(rec.getSubjectType())) {
            if (receipt.getSupplierId() == null || !receipt.getSupplierId().equals(rec.getSupplierId()))
                throw new BusinessException("收款供应商与被核销应收的供应商不一致（收款供应商ID=" + receipt.getSupplierId()
                        + "，应收供应商ID=" + rec.getSupplierId() + "），请分开制单");
        } else {
            if (receipt.getCustomerId() == null || !receipt.getCustomerId().equals(rec.getCustomerId()))
                throw new BusinessException("收款客户与被核销应收的客户不一致（收款客户ID=" + receipt.getCustomerId()
                        + "，应收客户ID=" + rec.getCustomerId() + "），请分开制单");
        }
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
        int rowNo = 0;
        for (FinanceReceiptItem it : items) {
            rowNo++;
            // F7-37（2026-09-19）：不再静默跳过（跳过 ⇒ 资金流水全额入账而应收未核销）。
            // create 已拦新数据，此处兜底**历史草稿**：明细缺台账 / 台账不存在一律拒绝审核。
            if (it.getReceivableId() == null)
                throw new BusinessException("收款明细第 " + rowNo + " 行未关联应收台账，无法审核（请补全或作废重开）");
            FinanceReceivable rec = receivableMapper.selectById(it.getReceivableId());
            if (rec == null)
                throw new BusinessException("收款明细指向的应收台账不存在（第 " + rowNo + " 行，台账ID=" + it.getReceivableId() + "），请刷新后重试");
            // 主体一致性：客户收款只能核销客户应收，供应商收款只能核销供应商应收（防串账）
            String recSubject = rec.getSubjectType() != null ? rec.getSubjectType() : SubjectType.CUSTOMER.getCode();
            if (!recSubject.equals(receipt.getSubjectType()))
                throw new BusinessException("收款主体与被核销应收不一致（收款：" + SubjectType.fromCode(receipt.getSubjectType()).getLabel()
                        + "，应收：" + SubjectType.fromCode(recSubject).getLabel() + "），请分开制单");
            // I27（2026-09-18 修复）：预收台账（ADVANCE，负数应收＝多收款、我方欠客户）是"待退/待抵扣"挂账，
            // **不能作为收款核销目标** —— 旧实现允许核销它会走进"超额"分支再次生成预收，
            // 单号被层层追加 `-ADVANCE`（`X-ADVANCE-ADVANCE-…`）直到撑破列宽、单据无法审核。
            // 语义上"用收款去核销预收"等于退款，应走退款/冲销，而不是收款。
            if (SettlementStatus.ADVANCE.getCode().equals(rec.getStatus()))
                throw new BusinessException("应收单「" + rec.getBillNo() + "」是预收台账（多收款待退/待抵扣），不能作为收款核销的目标；如需退预收款请走退款或冲销流程");
            // F7-31（2026-09-19）：对象归属一致性 —— 同为主体类型还不够，必须核销**同一往来单位**的应收。
            // 修复前只比对 subjectType，A 客户的收款单可核销 B 客户的应收（已实测复现；库中历史已存在 6 条）。
            assertSamePartner(receipt, rec);
            // F7-33（2026-09-19）：金额不得为负 —— 修复前负数会反向调整台账（实测 paid 变负、unpaid 虚增）
            if (it.getThisAmount() != null && it.getThisAmount().compareTo(BigDecimal.ZERO) < 0)
                throw new BusinessException("收款明细金额不能为负数");
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
                // I27 修复：单号走归一化（去掉已有 -ADVANCE 后再追加一次 + 超长护栏），不再层层叠加
                advance.setBillNo(ReceivableHelper.advanceBillNo(rec.getBillNo()));
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
            // F7-35（2026-09-19）：反核销后补 PARTIAL —— 该台账若还有**其它**收款的核销，
            // 冲销后未收额介于 (0, amount) 之间，状态应回到 PARTIAL；原实现只有 SETTLED/UNSETTLED 两态，
            // 会把"部分结清"写成"未结清"（实测：台账 282 反审核后 PARTIAL→UNSETTLED，从部分结清列表里消失）。
            int rows = receivableMapper.update(null, new LambdaUpdateWrapper<FinanceReceivable>()
                    .eq(FinanceReceivable::getId, rec.getId())
                    .setSql("status = CASE WHEN IFNULL(unpaid_amount, 0) + (" + amtSql + ") <= 0 THEN '"
                            + SettlementStatus.SETTLED.getCode() + "' WHEN IFNULL(unpaid_amount, 0) + (" + amtSql
                            + ") >= IFNULL(amount, 0) THEN '" + SettlementStatus.UNSETTLED.getCode()
                            + "' ELSE '" + SettlementStatus.PARTIAL.getCode() + "' END")
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
            // I29 口径（2026-09-18）：作废预收台账时**金额一并清零**（原金额记入备注留痕），
            // 与应收反审核冲销保持同一口径（旧实现只置状态，作废行仍带金额）
            receivableHelper.cancelLedger(adv);
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
