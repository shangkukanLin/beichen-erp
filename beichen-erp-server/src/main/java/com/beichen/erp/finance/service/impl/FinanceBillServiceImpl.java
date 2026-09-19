package com.beichen.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.finance.entity.*;
import com.beichen.erp.finance.common.BillType;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.mapper.*;
import com.beichen.erp.finance.service.FinanceBillService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    /** 事务管理器：账单生成在「互斥锁内用显式事务落库并在放锁前提交」（O-6 幂等护栏） */
    private final PlatformTransactionManager txManager;
    /** 账单生成互斥锁（O-6）：单实例部署下串行化「查重 + 落库」，防止双击/并发重复出账 */
    private final Object generateLock = new Object();

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

    /**
     * 生成账单（草稿）。
     *
     * <p><b>幂等护栏（O-6）</b>：同一「账单类型 + 往来单位 + 账期截止日」已存在**未作废**账单时直接拒绝，
     * 避免同一账期被重复出账。手动 {@code POST /api/finance/bill/generate} 与自动任务共用本方法
     * （自动任务在计划层已做两层去重，这里是最后一道闸）；作废（CANCELLED）后可重新生成。
     *
     * <p><b>并发保护</b>：在互斥锁内先查重、再以**显式事务**落库并提交，最后才放锁。
     * 注意不能用 {@code @Transactional} + synchronized：注解事务的提交发生在方法返回（放锁）之后，
     * 排队线程仍可能读到未提交的库状态而重复出账，故这里改用 {@link TransactionTemplate}。
     * （多实例部署时互斥锁只在单 JVM 内有效，需再加数据库唯一约束。）
     */
    @Override
    public FinanceBill generate(String billType, Long partnerId, String partnerName, LocalDate periodStart, LocalDate periodEnd) {
        // F7-39#1（2026-09-19）：三参数改**必填** —— 原实现仅在 billType/partnerId/periodEnd **三者全非空**时才查重，
        // 任一为 null 就整段跳过 ⇒ 无账期/无往来单位即可重复出账（账单是快照、不动钱，但会造成同账期重复单据）。
        if (billType == null || billType.isBlank()) throw new BusinessException("账单类型不能为空");
        if (partnerId == null) throw new BusinessException("往来单位不能为空");
        if (periodEnd == null) throw new BusinessException("账期截止日不能为空");
        // F7-39#2（2026-09-19）：校验账单类型 —— 原实现"不是 RECEIVABLE 一律走应付分支"，
        // 传 PAYABLE/任意字符串都会按应付出账（静默兜底错分支）。
        if (!BillType.RECEIVABLE.getCode().equals(billType) && !BillType.PAYABLE.getCode().equals(billType))
            throw new BusinessException("不支持的账单类型：" + billType + "（仅支持 RECEIVABLE / PAYABLE）");
        synchronized (generateLock) {
            FinanceBill exist = findActiveBill(billType, partnerId, periodEnd);
            if (exist != null) {
                throw new BusinessException("该往来单位在本账期已存在账单 " + exist.getBillNo()
                        + "，请勿重复生成；如需重做请先作废原账单");
            }
            TransactionTemplate tt = new TransactionTemplate(txManager);
            return tt.execute(status -> doGenerate(billType, partnerId, partnerName, periodStart, periodEnd));
        }
    }

    /** 账期查重：同「账单类型 + 往来单位 + 账期截止日」的未作废账单（不存在返回 null） */
    private FinanceBill findActiveBill(String billType, Long partnerId, LocalDate periodEnd) {
        return billMapper.selectOne(new LambdaQueryWrapper<FinanceBill>()
                .eq(FinanceBill::getBillType, billType)
                .eq(FinanceBill::getPartnerId, partnerId)
                .eq(FinanceBill::getPeriodEnd, periodEnd)
                .ne(FinanceBill::getStatus, DocStatus.CANCELLED.getCode())
                .orderByAsc(FinanceBill::getId)
                .last("LIMIT 1"));
    }

    /** 实际生成逻辑（调用方 {@link #generate} 已保证互斥与查重，本方法不再重复校验） */
    private FinanceBill doGenerate(String billType, Long partnerId, String partnerName, LocalDate periodStart, LocalDate periodEnd) {
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
                    // 到期日为空也必须纳入：SQL 中 NULL <= x 结果为 NULL 会把整行漏掉，
                    // 造成账单静默少行（曾漏掉退货负应收 -60，账单金额虚高）
                    .and(w -> w.isNull(FinanceReceivable::getDueDate)
                            .or().le(FinanceReceivable::getDueDate, periodEnd)));
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
                    // 同应收侧：放行到期日为空的台账，避免静默漏行
                    .and(w -> w.isNull(FinancePayable::getDueDate)
                            .or().le(FinancePayable::getDueDate, periodEnd)));
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
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败
        if (!DocStatusGuard.claim(billMapper, FinanceBill::getId, id, FinanceBill::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
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
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败
        if (!DocStatusGuard.claim(billMapper, FinanceBill::getId, id, FinanceBill::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已审核状态可反审核");
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
        // 原子抢占状态（P2-29）：from 取当前状态（草稿/已审核均可作废），并发双击只生效一次
        if (!DocStatusGuard.claim(billMapper, FinanceBill::getId, id, FinanceBill::getStatus,
                bill.getStatus(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("账单已作废");
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
        // F7-39#3（2026-09-19）：改为「冲突检测 + 递增重试」—— 原实现解析失败时**静默兜底 seq=1**，
        // 对无唯一索引的单号列（finance_cashflow.flow_no / finance_expense.expense_no / finance_invoice.invoice_no）
        // 会直接生成重复编号且无 DB 兜底。现在生成后校验是否已占用，冲突则递增，用尽则报错（不再静默重号）。
        for (int i = 0; i < 999; i++) {
            String code = BillPrefix.BILL + d + String.format("%03d", seq);
            if (billMapper.selectCount(new LambdaQueryWrapper<FinanceBill>().eq(FinanceBill::getBillNo, code)) == 0) return code;
            seq++;
        }
        throw new BusinessException("当日账单编号已用尽（前缀 " + pat + "），请联系管理员");
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

    /** 同（账单类型，往来单位，账期截止日）当天是否已有非作废账单（与 generate 的幂等护栏同口径） */
    private boolean billExists(String billType, Long partnerId, LocalDate periodEnd) {
        return findActiveBill(billType, partnerId, periodEnd) != null;
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
