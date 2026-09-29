package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SubjectType;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应收台账的**只读查询**（2026-09-19 期 2「读隔离」抽出）。
 *
 * <p>为什么需要这个类：收款页（{@code /api/finance/receipt}）的「核销明细」要从**未结清应收**里选，
 * 原先它直读应收页的接口 {@code /api/finance/receivable/unpaid}（需 {@code finance:receivable}）
 * ⇒ 只有 {@code finance:receipt} 的用户会 403（这是接口级权限校准后新查出的真实缺口）。</p>
 *
 * <p>查询口径收敛到本类，由 {@code FinanceReceivableController}（应收页）与
 * {@code FinanceReceiptController}（收款页）共用，方法语义与原先接口逐字一致。</p>
 */
@Component
@RequiredArgsConstructor
public class ReceivableQuery {

    private final FinanceReceivableMapper receivableMapper;

    /**
     * 未结清应收（供收款单下拉）：客户应收按 {@code customerId} 查，供应商应收（应付转应收）按 {@code supplierId} 查。
     * <p>口径：排除已结清（SETTLED）与已作废（CANCELLED）。</p>
     *
     * <p><b>2026-09-19 修复（F7-41）</b>：再排除 <b>ADVANCE（预收台账）</b> 与 <b>非正数应收</b> —— 与付款侧
     * {@code PayableQuery.unpaid} 的 {@code amount > 0} 完全对称。修复前负数/预收台账会出现在核销下拉里：
     * ① ADVANCE 选中后审核必被 I27 守卫拒绝（"可选但必然失败"）；② 负数应收（UNSETTLED，实测 -5）
     * 连 I27 都不覆盖，选中核销会走"超额"分支<b>再生成一条预收 ADVANCE</b>
     * —— 正是 I11（2026-09-18）在应付侧修掉的同型路径。</p>
     */
    public List<FinanceReceivable> unpaid(Long customerId, Long supplierId, String subjectType) {
        if (supplierId != null || (subjectType != null && "SUPPLIER".equalsIgnoreCase(subjectType))) {
            // F7-208（2026-09-29 审核批 A）：供应商分支也必须**有主体**才查 —— 原先缺 supplierId 时该条件被跳过，
            // 会返回**全公司**供应商应收（只读面过宽）。与下方客户分支 `customerId == null ⇒ List.of()` 对齐。
            if (supplierId == null) return List.of();
            return receivableMapper.selectList(new LambdaQueryWrapper<FinanceReceivable>()
                    .eq(FinanceReceivable::getSubjectType, SubjectType.SUPPLIER.getCode())
                    .eq(supplierId != null, FinanceReceivable::getSupplierId, supplierId)
                    .ne(FinanceReceivable::getStatus, SettlementStatus.SETTLED.getCode())
                    .ne(FinanceReceivable::getStatus, SettlementStatus.CANCELLED.getCode())
                    .ne(FinanceReceivable::getStatus, SettlementStatus.ADVANCE.getCode())
                    .gt(FinanceReceivable::getAmount, BigDecimal.ZERO)
                    .orderByDesc(FinanceReceivable::getId));
        }
        // F7-39#9（2026-09-19）：客户分支补 subjectType 限定 + customerId 非空护栏。
        // 原实现只 eq(customerId)：① 语义不完整（与 SUPPLIER 分支不对称）；② customerId 为 null 时
        // MyBatis-Plus 生成 customer_id = NULL（恒假）⇒ 静默返回空列表而非报错，调用方难定位。
        if (customerId == null) return List.of();
        return receivableMapper.selectList(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getSubjectType, SubjectType.CUSTOMER.getCode())
                .eq(FinanceReceivable::getCustomerId, customerId)
                .ne(FinanceReceivable::getStatus, SettlementStatus.SETTLED.getCode())
                .ne(FinanceReceivable::getStatus, SettlementStatus.CANCELLED.getCode())
                .ne(FinanceReceivable::getStatus, SettlementStatus.ADVANCE.getCode())
                .gt(FinanceReceivable::getAmount, BigDecimal.ZERO)
                .orderByDesc(FinanceReceivable::getId));
    }

    /**
     * 主体欠款汇总（2026-09-29 用户口径）：新增收款页「选完客户/供应商后显示**到期欠款 + 总欠款**」。
     *
     * <p><b>口径逐条对齐应付侧</b> {@code PayableQuery.supplierSummary()}（那是全项目**唯一**已有的"逾期"定义，
     * 跨模块必须统一）：</p>
     * <ul>
     *   <li>过滤：{@code status IN (UNSETTLED, PARTIAL)} + {@code amount > 0}
     *       ⇒ 天然排除 <b>ADVANCE 预收台账</b>（负数，我方欠对方）与冲减用的负数行 —— 与
     *       {@link #unpaid} 的收口一致，同应付侧 F7-40 的口径；</li>
     *   <li><b>到期欠款 = 未收额 &gt; 0 且 due_date &lt; 今天</b>（{@code LocalDate.now()}，<b>当天到期不算逾期</b>）；</li>
     *   <li><b>无到期日的单据不计入到期欠款</b>（另返回 {@code noDueCount / noDueAmount}，让界面能解释
     *       "为什么到期是 0"；实测客户侧 6 张单**全部没有到期日**，不解释会被当成 bug）。</li>
     * </ul>
     *
     * <p>为什么用聚合 SQL 而不是捞行再算：一个客户可能有成千上万条台账，聚合交给库更稳
     * （与 {@code CustomerAnalysisMapper.receivableByCustomer} 同思路，但这里补了
     * <b>subject_type 收口</b>（那条只靠 customer_id IS NOT NULL）、到期分桶、以及 ADVANCE/负数排除）。</p>
     */
    public Map<String, Object> partySummary(Long customerId, Long supplierId, String subjectType) {
        boolean supplier = supplierId != null || (subjectType != null && "SUPPLIER".equalsIgnoreCase(subjectType));
        Long partyId = supplier ? supplierId : customerId;
        Map<String, Object> res = new HashMap<>();
        res.put("subjectType", supplier ? SubjectType.SUPPLIER.getCode() : SubjectType.CUSTOMER.getCode());
        res.put("partyId", partyId);
        res.put("unpaidAmount", BigDecimal.ZERO);
        res.put("overdueAmount", BigDecimal.ZERO);
        res.put("noDueAmount", BigDecimal.ZERO);
        res.put("noDueCount", 0);
        res.put("billCount", 0);
        res.put("asOf", LocalDate.now().toString());
        if (partyId == null) return res;   // 没选主体 ⇒ 全 0（前端此时不展示汇总条）
        // 取行再在 Java 里累加 —— 与应付侧 {@code PayableQuery.supplierSummary()} 完全同一种做法，
        // 并且**直接复用 {@link #unpaid}**：口径只有一处实现（未结清 + 排除 ADVANCE + 金额>0），
        // 不会出现"汇总 SQL 与下拉口径各写一遍、日后悄悄分叉"的风险。
        // ⚠️ 2026-09-29 第一版曾用 selectMaps 聚合 SQL（IFNULL(SUM(CASE…))），实测接口返回全 0
        //    （同一个客户 SQL 直查是 6 张/120.00）⇒ 聚合别名映射在本项目不可靠，故改回本页既有做法。
        List<FinanceReceivable> rows = unpaid(customerId, supplierId, subjectType);
        LocalDate today = LocalDate.now();
        BigDecimal unpaidSum = BigDecimal.ZERO;
        BigDecimal overdueSum = BigDecimal.ZERO;
        BigDecimal noDueAmt = BigDecimal.ZERO;
        int noDueCount = 0;
        for (FinanceReceivable r : rows) {
            BigDecimal u = r.getUnpaidAmount() != null ? r.getUnpaidAmount() : BigDecimal.ZERO;
            unpaidSum = unpaidSum.add(u);
            // 到期欠款 = 未收额 > 0 且 due_date < 今天（当天到期不算；无到期日不计入）—— 与应付侧逐字一致
            if (u.signum() > 0 && r.getDueDate() != null && r.getDueDate().isBefore(today)) overdueSum = overdueSum.add(u);
            if (r.getDueDate() == null) { noDueAmt = noDueAmt.add(u); noDueCount++; }
        }
        res.put("unpaidAmount", unpaidSum);
        res.put("overdueAmount", overdueSum);
        res.put("noDueAmount", noDueAmt);
        res.put("noDueCount", noDueCount);
        res.put("billCount", rows.size());
        return res;
    }

    /**
     * 按客户汇总应收（2026-09-29 用户口径「汇总要显示在**应收管理**里面」）。
     *
     * <p><b>口径逐条对齐应付侧的</b> {@link PayableQuery#supplierSummary()}（同一套"未结清/逾期"定义，
     * 跨模块必须一致）：</p>
     * <ul>
     *   <li>范围：{@code subject_type = CUSTOMER}（本页汇总视图就是「按客户汇总」；供应商应收在台账页签里看）
     *       + {@code status IN (UNSETTLED, PARTIAL)} + {@code amount > 0}
     *       ⇒ 天然排除 ADVANCE 预收台账（负数）与冲减行；</li>
     *   <li><b>逾期金额 = 未收额 &gt; 0 且 due_date &lt; 今天</b>（当天到期不算逾期）；</li>
     *   <li>额外返回 {@code billCount}（未结清单据数）与 {@code noDueCount}（无到期日张数）——
     *       实测客户侧台账**到期日普遍为空**，不区分会让"逾期恒 0"被当成算错。</li>
     * </ul>
     *
     * <p>与 {@link #partySummary} 一样**在 Java 里按行累加**（不写聚合 SQL）：一个客户/供应商可能有成千上万条
     * 台账，但更关键的是"口径只有一处"—— 聚合别名映射在本项目实测不可靠（见 partySummary 的注）。</p>
     */
    public List<Map<String, Object>> customerSummary() {
        List<FinanceReceivable> all = receivableMapper.selectList(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getSubjectType, SubjectType.CUSTOMER.getCode())
                .in(FinanceReceivable::getStatus, SettlementStatus.UNSETTLED.getCode(),
                        SettlementStatus.PARTIAL.getCode())
                .gt(FinanceReceivable::getAmount, BigDecimal.ZERO));
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (FinanceReceivable r : all) {
            if (r.getCustomerId() == null) continue;
            Map<String, Object> m = map.computeIfAbsent(r.getCustomerId(), k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("customerId", k);
                x.put("customerName", r.getCustomerName());
                x.put("totalAmount", BigDecimal.ZERO);
                x.put("paidAmount", BigDecimal.ZERO);
                x.put("unpaidAmount", BigDecimal.ZERO);
                x.put("overdueAmount", BigDecimal.ZERO);
                x.put("billCount", 0);
                x.put("noDueCount", 0);
                return x;
            });
            BigDecimal amount = r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO;
            BigDecimal paid = r.getPaidAmount() != null ? r.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal unpaidAmt = r.getUnpaidAmount() != null ? r.getUnpaidAmount() : BigDecimal.ZERO;
            m.put("totalAmount", ((BigDecimal) m.get("totalAmount")).add(amount));
            m.put("paidAmount", ((BigDecimal) m.get("paidAmount")).add(paid));
            m.put("unpaidAmount", ((BigDecimal) m.get("unpaidAmount")).add(unpaidAmt));
            m.put("billCount", ((Integer) m.get("billCount")) + 1);
            if (unpaidAmt.compareTo(BigDecimal.ZERO) > 0 && r.getDueDate() != null && r.getDueDate().isBefore(today))
                m.put("overdueAmount", ((BigDecimal) m.get("overdueAmount")).add(unpaidAmt));
            if (r.getDueDate() == null) m.put("noDueCount", ((Integer) m.get("noDueCount")) + 1);
        }
        List<Map<String, Object>> list = new ArrayList<>(map.values());
        // 未收额倒序（与应付汇总一致）：欠得多的排前面
        list.sort((a, b) -> ((BigDecimal) b.get("unpaidAmount")).compareTo((BigDecimal) a.get("unpaidAmount")));
        return list;
    }
}
