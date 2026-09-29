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
            m.put("billType", b.getBillType());
            // 2026-09-26 B6：补回往来单位 id —— 前端「往来单位」列要按账单类型分流到客户/供应商详情，
            // 原先只回了 partnerName ⇒ 列不可点。
            m.put("partnerId", b.getPartnerId());
            m.put("partnerName", b.getPartnerName());
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
     * （自动任务在计划层已做两层去重，这里是最后一道闸）；作废（CANCELLED）后可重新生成。</p>
     *
     * <p><b>并发保护</b>：① JVM 内 {@code synchronized}（单实例串行化）；② **事务内**对往来单位行加锁
     * （{@link #lockPartnerForGenerate}）实现跨实例串行化 —— 查重键含 partnerId，锁住同一往来单位即覆盖整个键；
     * ③ 查重与落库同事务提交后才放锁。注意不能用 {@code @Transactional} + {@code synchronized}：
     * 注解事务的提交发生在方法返回（放锁）之后，排队线程仍可能读到未提交状态而重复出账。</p>
     *
     * <p><b>F7-242（2026-09-29 批 D）</b>：原实现的 {@code lockPartnerForGenerate} 在事务**之外**调用 ⇒
     * `SELECT ... FOR UPDATE` 走自动提交、**语句一结束锁就释放**，所谓"跨实例互斥"实际无效（只剩 JVM 锁）。
     * 现把行锁移入事务内，锁随事务提交/回滚释放。</p>
     *
     * <p><b>F7-244</b>：抬头 {@code partnerName} 不再信任客户端值，一律按 {@code partnerId} 回查主数据。</p>
     */
    @Override
    public FinanceBill generate(String billType, Long partnerId, String partnerName, LocalDate periodStart,
                                LocalDate periodEnd, String source) {
        // F7-39#1（2026-09-19）：三参数改**必填** —— 原实现仅在 billType/partnerId/periodEnd **三者全非空**时才查重，
        // 任一为 null 就整段跳过 ⇒ 无账期/无往来单位即可重复出账（账单是快照、不动钱，但会造成同账期重复单据）。
        if (billType == null || billType.isBlank()) throw new BusinessException("账单类型不能为空");
        if (partnerId == null) throw new BusinessException("往来单位不能为空");
        if (periodEnd == null) throw new BusinessException("账期截止日不能为空");
        // F7-39#2（2026-09-19）：校验账单类型 —— 原实现"不是 RECEIVABLE 一律走应付分支"，
        // 传 PAYABLE/任意字符串都会按应付出账（静默兜底错分支）。
        if (!BillType.RECEIVABLE.getCode().equals(billType) && !BillType.PAYABLE.getCode().equals(billType))
            throw new BusinessException("不支持的账单类型：" + billType + "（仅支持 RECEIVABLE / PAYABLE）");
        // F7-244（2026-09-29 批 D）：抬头以主数据为准（入参 partnerName 仅作兼容保留，不再采信客户端值）。
        // 主数据缺失时 partnerName() 返回空串，但紧接着的 lockPartnerForGenerate 会抛"客户/供应商不存在" ⇒ 不会落空抬头。
        String serverName = partnerName(null, BillType.RECEIVABLE.getCode().equals(billType), partnerId);
        synchronized (generateLock) {
            TransactionTemplate tt = new TransactionTemplate(txManager);
            return tt.execute(status -> {
                // F7-242：行锁**必须在事务内** —— 否则 autocommit 下语句一结束就释放（跨实例互斥失效）
                lockPartnerForGenerate(billType, partnerId);
                FinanceBill exist = findActiveBill(billType, partnerId, periodEnd);
                if (exist != null) {
                    throw new BusinessException("该往来单位在本账期已存在账单 " + exist.getBillNo()
                            + "，请勿重复生成；如需重做请先作废原账单");
                }
                return doGenerate(billType, partnerId, serverName, periodStart, periodEnd, source);
            });
        }
    }

    /**
     * F7-139（2026-09-20）：按账单类型**锁定往来单位行**（RECEIVABLE → 客户 / PAYABLE → 供应商）。
     *
     * <p>作用是把"查重 + 落库"从"仅在单 JVM 内互斥"升级为**跨实例互斥**：账单查重键含 partnerId，
     * 同一往来单位的出账被串行化即覆盖整个查重键 ⇒ 多实例部署下也不会重复出账。</p>
     *
     * <p>用行锁而非"唯一索引"的原因：查重口径含 `status <> 'CANCELLED'`（作废后可重新出账），
     * 而 MySQL **不支持带条件的唯一索引** ⇒ 唯一索引会误伤"作废后重出"的合法场景。</p>
     */
    private void lockPartnerForGenerate(String billType, Long partnerId) {
        Long cid = com.beichen.erp.config.CompanyContext.get();
        if (cid != null && cid <= 0) cid = null;
        if (BillType.RECEIVABLE.getCode().equals(billType)) {
            if (customerMapper.selectForUpdate(partnerId, cid) == null) throw new BusinessException("客户不存在");
        } else {
            if (supplierMapper.selectForUpdate(partnerId, cid) == null) throw new BusinessException("供应商不存在");
        }
    }

    /** 账期查重：同「账单类型 + 往来单位 + 账期截止日」的未作废账单（不存在返回 null） */    private FinanceBill findActiveBill(String billType, Long partnerId, LocalDate periodEnd) {
        return billMapper.selectOne(new LambdaQueryWrapper<FinanceBill>()
                .eq(FinanceBill::getBillType, billType)
                .eq(FinanceBill::getPartnerId, partnerId)
                .eq(FinanceBill::getPeriodEnd, periodEnd)
                .ne(FinanceBill::getStatus, DocStatus.CANCELLED.getCode())
                .orderByAsc(FinanceBill::getId)
                .last("LIMIT 1"));
    }

    /** 实际生成逻辑（调用方 {@link #generate} 已保证互斥与查重，本方法不再重复校验） */
    private FinanceBill doGenerate(String billType, Long partnerId, String partnerName, LocalDate periodStart,
                                   LocalDate periodEnd, String source) {
        FinanceBill bill = new FinanceBill();
        bill.setBillNo(genCode());
        bill.setBillType(billType);
        bill.setPartnerId(partnerId);
        bill.setPartnerName(partnerName);
        // D-19（2026-09-29）：出账来源（MANUAL 手工 / AUTO 自动任务），供事后追溯
        bill.setSource(source == null || source.isBlank() ? "MANUAL" : source);
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
            // F7-241（2026-09-29 批 D）：**剔除已被其它未作废账单引用的台账** —— 只做"是否出账"的门禁不够：
            // 自动任务即便入选，这里重查仍会把已覆盖的台账再收一遍（同一台账出现在两张未作废账单里 = 重复对账）。
            Set<Long> covered = coveredSourceIds(list.stream().map(FinanceReceivable::getId).toList());
            list = list.stream().filter(r -> !covered.contains(r.getId())).toList();
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
            // F7-241：与应收侧对称 —— 剔除已被其它未作废账单引用的台账（防止同一台账被两张账单重复覆盖）
            Set<Long> covered = coveredSourceIds(list.stream().map(FinancePayable::getId).toList());
            list = list.stream().filter(r -> !covered.contains(r.getId())).toList();
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
        // F7-243（2026-09-29 批 D）：**拒绝空账单** —— 原先取不到任何未结清台账也照样落一张 0 明细/0 金额账单，
        // 且可审核、可导出"0 元对账单"。库中账单 58 `ZD-20260918002`（RECEIVABLE / AUDITED / 0 明细 / 金额 0）
        // 就是该路径的痕迹。口径与批 A `F7-205`（0 元收款单）一致：**没有内容就不建单**。
        if (items.isEmpty())
            throw new BusinessException("该往来单位在账期 " + periodEnd
                    + " 内没有可出账的未结清应收/应付（可能已全部包含在其它未作废账单中），无需生成账单");
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
            // F7-241（2026-09-29 批 D）：**逐张剔除已覆盖台账**后再判断 —— 原先"组内任一单据已被未作废账单引用
            // ⇒ 整组 continue"，会把同客户**新到期**的台账连坐跳过，且不自愈（periodEnd=today 天天变、覆盖判定
            // 不变）⇒ 库中客户 A10 的 9 张到期未结清里 2 张已覆盖，其余 7 张永不自动出账。
            Set<Long> covered = coveredSourceIds(batch.stream().map(FinanceReceivable::getId).toList());
            List<FinanceReceivable> open = batch.stream().filter(r -> !covered.contains(r.getId())).toList();
            if (open.isEmpty()) continue;
            // 防重2：该客户当日已有非作废账单（手动生成过）→ 跳过
            if (billExists(BillType.RECEIVABLE.getCode(), e.getKey(), today)) continue;
            LocalDate start = open.stream().map(FinanceReceivable::getDueDate)
                    .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(today);
            plan.add(new AutoBillCommand(BillType.RECEIVABLE.getCode(), e.getKey(),
                    partnerName(open.get(0).getCustomerName(), true, e.getKey()), start, today));
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
            // F7-241：与应收侧对称 —— 逐张剔除已覆盖台账，剩余为空才跳过整组
            Set<Long> covered = coveredSourceIds(batch.stream().map(FinancePayable::getId).toList());
            List<FinancePayable> open = batch.stream().filter(p -> !covered.contains(p.getId())).toList();
            if (open.isEmpty()) continue;
            if (billExists(BillType.PAYABLE.getCode(), e.getKey(), today)) continue;
            LocalDate start = open.stream().map(FinancePayable::getDueDate)
                    .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(today);
            plan.add(new AutoBillCommand(BillType.PAYABLE.getCode(), e.getKey(),
                    partnerName(open.get(0).getSupplierName(), false, e.getKey()), start, today));
        }
        return plan;
    }

    /**
     * F7-241（2026-09-29 批 D）：**哪些**来源台账已被"未作废账单"的明细引用。
     *
     * <p>原实现（{@code coveredByActiveBill}）只回答"组内有没有被覆盖"，调用方据此整组跳过；现返回**待剔除的
     * source_id 集合**，让调用方把已覆盖的逐张剔掉、其余照常出账（作废账单不计入覆盖 ⇒ 作废后可重出）。</p>
     */
    private Set<Long> coveredSourceIds(List<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) return Set.of();
        List<FinanceBillItem> items = billItemMapper.selectList(new LambdaQueryWrapper<FinanceBillItem>()
                .in(FinanceBillItem::getSourceId, sourceIds));
        if (items.isEmpty()) return Set.of();
        Set<Long> billIds = items.stream().map(FinanceBillItem::getBillId).collect(Collectors.toSet());
        Set<Long> activeBills = billMapper.selectList(new LambdaQueryWrapper<FinanceBill>()
                        .in(FinanceBill::getId, billIds)
                        .ne(FinanceBill::getStatus, DocStatus.CANCELLED.getCode()))
                .stream().map(FinanceBill::getId).collect(Collectors.toSet());
        return items.stream().filter(i -> activeBills.contains(i.getBillId()))
                .map(FinanceBillItem::getSourceId).collect(Collectors.toSet());
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
