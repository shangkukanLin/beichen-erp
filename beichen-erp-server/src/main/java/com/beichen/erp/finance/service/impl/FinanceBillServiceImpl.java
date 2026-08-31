package com.beichen.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.entity.*;
import com.beichen.erp.finance.common.BillType;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.mapper.*;
import com.beichen.erp.finance.service.FinanceBillService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinanceBillServiceImpl implements FinanceBillService {

    private final FinanceBillMapper billMapper;
    private final FinanceBillItemMapper billItemMapper;
    private final FinanceReceivableMapper receivableMapper;
    private final FinancePayableMapper payableMapper;
    private final CustomerMapper customerMapper;
    private final SupplierMapper supplierMapper;

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
    @Transactional(rollbackFor = Exception.class)
    public FinanceBill generate(String billType, Long partnerId, String partnerName, LocalDate periodStart, LocalDate periodEnd) {
        FinanceBill bill = new FinanceBill();
        bill.setBillNo(genCode());
        bill.setBillType(billType);
        bill.setPartnerId(partnerId);
        bill.setPartnerName(partnerName);
        bill.setPeriodStart(periodStart);
        bill.setPeriodEnd(periodEnd);
        // 账单生成后为草稿，需审核后生效（生命周期：草稿→已审核→已作废）
        bill.setStatus(DocStatus.DRAFT.getCode());

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal unpaid = BigDecimal.ZERO;
        List<FinanceBillItem> items = new ArrayList<>();

        if (BillType.RECEIVABLE.getCode().equals(billType)) {
            List<FinanceReceivable> list = receivableMapper.selectList(new LambdaQueryWrapper<FinanceReceivable>()
                    .eq(FinanceReceivable::getCustomerId, partnerId)
                    .and(w -> w.eq(FinanceReceivable::getStatus, SettlementStatus.UNSETTLED.getCode())
                            .or().eq(FinanceReceivable::getStatus, SettlementStatus.PARTIAL.getCode()))
                    .le(FinanceReceivable::getDueDate, periodEnd));
            for (FinanceReceivable r : list) {
                FinanceBillItem it = new FinanceBillItem();
                it.setSourceBillType(r.getSourceBillType());
                it.setSourceBillNo(r.getSourceBillNo());
                it.setSourceId(r.getId());
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
                    .and(w -> w.eq(FinancePayable::getStatus, SettlementStatus.UNSETTLED.getCode())
                            .or().eq(FinancePayable::getStatus, SettlementStatus.PARTIAL.getCode()))
                    .le(FinancePayable::getDueDate, periodEnd));
            for (FinancePayable r : list) {
                FinanceBillItem it = new FinanceBillItem();
                it.setSourceBillType(r.getSourceBillType());
                it.setSourceBillNo(r.getSourceBillNo());
                it.setSourceId(r.getId());
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        FinanceBill bill = billMapper.selectById(id);
        if (bill == null) throw new BusinessException("账单不存在");
        if (!DocStatus.DRAFT.getCode().equals(bill.getStatus())) throw new BusinessException("只有草稿状态可审核");
        FinanceBill u = new FinanceBill();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        billMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        FinanceBill bill = billMapper.selectById(id);
        if (bill == null) throw new BusinessException("账单不存在");
        if (!DocStatus.AUDITED.getCode().equals(bill.getStatus())) throw new BusinessException("只有已审核状态可反审核");
        FinanceBill u = new FinanceBill();
        u.setId(id);
        u.setStatus(DocStatus.DRAFT.getCode());
        billMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        FinanceBill bill = billMapper.selectById(id);
        if (bill == null) throw new BusinessException("账单不存在");
        if (DocStatus.CANCELLED.getCode().equals(bill.getStatus())) throw new BusinessException("账单已作废");
        FinanceBill u = new FinanceBill();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        billMapper.updateById(u);
    }

    private String genCode() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.BILL + d;
        LambdaQueryWrapper<FinanceBill> w = new LambdaQueryWrapper<FinanceBill>().likeRight(FinanceBill::getBillNo, pat).orderByDesc(FinanceBill::getBillNo).last("LIMIT 1");
        FinanceBill last = billMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getBillNo() != null) {
            try { seq = Integer.parseInt(last.getBillNo().substring(last.getBillNo().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.BILL + d + String.format("%03d", seq);
    }

    // ==================== 自动账单（方案A：到期即出账） ====================

    @Override
    public List<AutoBillCommand> autoGeneratePlan(LocalDate today) {
        List<AutoBillCommand> plan = new ArrayList<>();

        // === 应收：按客户分组「到期未结清」的应收 ===
        List<FinanceReceivable> recs = receivableMapper.selectList(new LambdaQueryWrapper<FinanceReceivable>()
                .le(FinanceReceivable::getDueDate, today)
                .isNotNull(FinanceReceivable::getCustomerId)
                .and(w -> w.eq(FinanceReceivable::getStatus, SettlementStatus.UNSETTLED.getCode())
                        .or().eq(FinanceReceivable::getStatus, SettlementStatus.PARTIAL.getCode())));
        Map<Long, List<FinanceReceivable>> byCustomer = recs.stream()
                .collect(Collectors.groupingBy(FinanceReceivable::getCustomerId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Long, List<FinanceReceivable>> e : byCustomer.entrySet()) {
            List<FinanceReceivable> batch = e.getValue();
            // 防重1：本批任一单据已被未作废账单引用 → 跳过（避免与已有账单重复覆盖）
            if (coveredByActiveBill(batch.stream().map(FinanceReceivable::getId).toList())) continue;
            // 防重2：该客户当日已有非作废账单（手动生成过）→ 跳过
            if (billExists(BillType.RECEIVABLE.getCode(), e.getKey(), today)) continue;
            LocalDate start = batch.stream().map(FinanceReceivable::getDueDate)
                    .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(today);
            plan.add(new AutoBillCommand(BillType.RECEIVABLE.getCode(), e.getKey(),
                    partnerName(batch.get(0).getCustomerName(), true, e.getKey()), start, today));
        }

        // === 应付：按供应商分组「到期未结清」的应付（对称逻辑） ===
        List<FinancePayable> pays = payableMapper.selectList(new LambdaQueryWrapper<FinancePayable>()
                .le(FinancePayable::getDueDate, today)
                .isNotNull(FinancePayable::getSupplierId)
                .and(w -> w.eq(FinancePayable::getStatus, SettlementStatus.UNSETTLED.getCode())
                        .or().eq(FinancePayable::getStatus, SettlementStatus.PARTIAL.getCode())));
        Map<Long, List<FinancePayable>> bySupplier = pays.stream()
                .collect(Collectors.groupingBy(FinancePayable::getSupplierId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Long, List<FinancePayable>> e : bySupplier.entrySet()) {
            List<FinancePayable> batch = e.getValue();
            if (coveredByActiveBill(batch.stream().map(FinancePayable::getId).toList())) continue;
            if (billExists(BillType.PAYABLE.getCode(), e.getKey(), today)) continue;
            LocalDate start = batch.stream().map(FinancePayable::getDueDate)
                    .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(today);
            plan.add(new AutoBillCommand(BillType.PAYABLE.getCode(), e.getKey(),
                    partnerName(batch.get(0).getSupplierName(), false, e.getKey()), start, today));
        }
        return plan;
    }

    /** 本批应收/应付单据是否已被未作废账单的明细引用（source_id 关联） */
    private boolean coveredByActiveBill(List<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) return false;
        List<FinanceBillItem> items = billItemMapper.selectList(new LambdaQueryWrapper<FinanceBillItem>()
                .in(FinanceBillItem::getSourceId, sourceIds));
        if (items.isEmpty()) return false;
        Set<Long> billIds = items.stream().map(FinanceBillItem::getBillId).collect(Collectors.toSet());
        Long active = billMapper.selectCount(new LambdaQueryWrapper<FinanceBill>()
                .in(FinanceBill::getId, billIds)
                .ne(FinanceBill::getStatus, DocStatus.CANCELLED.getCode()));
        return active != null && active > 0;
    }

    /** 同（账单类型，往来单位，账期截止日）当天是否已有非作废账单 */
    private boolean billExists(String billType, Long partnerId, LocalDate periodEnd) {
        Long dup = billMapper.selectCount(new LambdaQueryWrapper<FinanceBill>()
                .eq(FinanceBill::getBillType, billType)
                .eq(FinanceBill::getPartnerId, partnerId)
                .eq(FinanceBill::getPeriodEnd, periodEnd)
                .ne(FinanceBill::getStatus, DocStatus.CANCELLED.getCode()));
        return dup != null && dup > 0;
    }

    /** 往来单位名称：优先用台账冗余名，缺失时回查客户/供应商表 */
    private String partnerName(String redundantName, boolean isCustomer, Long partnerId) {
        if (redundantName != null && !redundantName.isBlank()) return redundantName;
        if (isCustomer) {
            Customer c = customerMapper.selectById(partnerId);
            return c != null && c.getName() != null ? c.getName() : "";
        }
        Supplier s = supplierMapper.selectById(partnerId);
        return s != null && s.getName() != null ? s.getName() : "";
    }
}
