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
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.common.SettlementRecordStatus;
import com.beichen.erp.finance.mapper.*;
import com.beichen.erp.finance.service.FinancePaymentService;
import com.beichen.erp.finance.service.PayableHelper;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FinancePaymentServiceImpl implements FinancePaymentService {

    private final FinancePaymentMapper paymentMapper;
    private final FinancePaymentItemMapper itemMapper;
    /** 付款单分款明细（2026-09-29 多账户付款：一单可拆到多个账户，与收款侧 FinanceReceiptAccountMapper 对称） */
    private final FinancePaymentAccountMapper paymentAccountMapper;
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
        // 分款明细条数（2026-09-29 多账户）：列表「账户」列据此显示「CASH-01」或「N 个账户」。
        // 一次批量查询（避免 N+1），并且是**唯一**让列表知道"这单有几个账户"的途径（主表只留首行快照）。
        Map<Long, Integer> accCount = new HashMap<>();
        if (!raw.getRecords().isEmpty()) {
            List<Long> ids = raw.getRecords().stream().map(FinancePayment::getId).toList();
            for (FinancePaymentAccount a : paymentAccountMapper.selectList(
                    new LambdaQueryWrapper<FinancePaymentAccount>().in(FinancePaymentAccount::getPaymentId, ids))) {
                accCount.merge(a.getPaymentId(), 1, Integer::sum);
            }
        }
        res.setRecords(raw.getRecords().stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId()); m.put("code", p.getCode());
            m.put("supplierId", p.getSupplierId()); m.put("supplierName", p.getSupplierName());
            m.put("supplierType", p.getSupplierType());
            m.put("accountId", p.getAccountId()); m.put("accountName", p.getAccountName());
            m.put("accountCount", accCount.getOrDefault(p.getId(), 0));
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
    /** 分款明细（2026-09-29 多账户）：按 id 升序 —— 首行即主表 account_id/account_name 的快照来源 */
    @Override public List<FinancePaymentAccount> getAccounts(Long paymentId) {
        return paymentAccountMapper.selectList(new LambdaQueryWrapper<FinancePaymentAccount>()
                .eq(FinancePaymentAccount::getPaymentId, paymentId).orderByAsc(FinancePaymentAccount::getId));
    }

    /**
     * 建单（兼容旧签名）：老 payload（只带 {@code payment.accountId} + items）与内部调用走这里 ——
     * {@code accounts} 缺省 ⇒ 由单账户字段 + 核销合计归一化成一条分款行，调用方无需改。
     */
    @Override @Transactional(rollbackFor = Exception.class)
    public void create(FinancePayment payment, List<FinancePaymentItem> items) {
        create(payment, null, items);
    }

    /**
     * 建单（2026-09-29 用户口径：付款侧与收款侧对称 —— 一个付款单可**多账户分款** + **核销项做成开关**）。
     *
     * <ul>
     *   <li><b>多账户分款</b>：{@code accounts} 每行 = 一个账户本次付出的钱（A 50 + B 100）；主表
     *       {@code amount} = 分款合计，{@code account_id/account_name} = **首行**（列表列/老读法兼容）。</li>
     *   <li><b>核销开关</b>：{@code items} 可为空（关闭 = 只记付款不核销）。但"多付出去的钱"不能悬空 ——
     *       审核时按差额生成预付台账（见 {@link #createUnsettledAdvance}），否则就是 F7-37 修掉的
     *       "钱出去、台账不动"错账。开关打开时每行仍必须关联应付台账（F7-37 口径不变）。</li>
     *   <li><b>新增护栏</b>：核销合计 ≤ 付款合计（核销的是"本次付出去的钱"）。</li>
     * </ul>
     */
    @Override @Transactional(rollbackFor = Exception.class)
    public void create(FinancePayment payment, List<FinancePaymentAccount> accounts, List<FinancePaymentItem> items) {
        if (payment.getSupplierId() == null) throw new BusinessException("供应商不能为空");
        Supplier s = supplierMapper.selectById(payment.getSupplierId());
        payment.setSupplierName(s != null ? s.getName() : "");
        // 主体类型固化：优先取供应商标签的第一个类型（与供应商详情展示一致），付款列表可按类型筛选
        if (payment.getSupplierType() == null || payment.getSupplierType().isBlank()) {
            payment.setSupplierType(resolveSupplierType(payment.getSupplierId()));
        }
        payment.setCode(gen(BillPrefix.PAYMENT, paymentMapper));
        payment.setStatus(DocStatus.DRAFT.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) payment.setCompanyId(cid);
        paymentMapper.insert(payment);
        BigDecimal paid = saveAccounts(payment, accounts, items, cid);
        BigDecimal settled = saveItems(payment, items, cid);
        assertSettledWithinPaid(settled, paid);
        writeMainTotals(payment.getId(), payment);
    }

    /**
     * 草稿就地修改（2026-09-29 用户口径：付款侧与收款侧对称，「草稿在**详情页**改+存、列表不给编辑」）。
     *
     * <p>只允许 DRAFT（已审核要先反审核）；字段与校验、payload 与 create 完全一致；
     * 分款与核销明细**整体替换**（先删后插，与收款单/物料售后详情页同范式）；单号不动。
     * 付款凭证 {@code attach_url} 不在此处覆盖 —— 详情页有自己的上传入口（{@code PUT /{id}/attach}）。</p>
     */
    @Override @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FinancePayment form, List<FinancePaymentAccount> accounts, List<FinancePaymentItem> items) {
        FinancePayment old = paymentMapper.selectById(id);
        if (old == null) throw new BusinessException("付款单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus()))
            throw new BusinessException("只有草稿状态的付款单可修改（已审核请先反审核）");
        Long supplierId = form.getSupplierId() != null ? form.getSupplierId() : old.getSupplierId();
        Supplier s = supplierId != null ? supplierMapper.selectById(supplierId) : null;
        String supplierName = s != null ? s.getName() : old.getSupplierName();
        LocalDate payDate = form.getPaymentDate() != null ? form.getPaymentDate() : old.getPaymentDate();
        // F7-218（2026-09-29 审核批 B）：换供应商后必须**重算 supplier_type** —— 原先只 set supplierId/supplierName，
        // 类型标签保持旧供应商的值 ⇒ 付款列表「主体类型」标签与按类型筛选、以及汇总的类型回填全部与实际不符。
        // 口径与 create 一致：按 supplier_type_ref 字典序第一个标签固化。
        String supplierType = supplierId != null ? resolveSupplierType(supplierId) : old.getSupplierType();
        // 用 update wrapper 显式 set（含 null）—— updateById 默认忽略 null，清空备注会写不进去
        paymentMapper.update(null, new LambdaUpdateWrapper<FinancePayment>()
                .eq(FinancePayment::getId, id)
                .set(FinancePayment::getSupplierId, supplierId)
                .set(FinancePayment::getSupplierName, supplierName)
                .set(FinancePayment::getSupplierType, supplierType)
                .set(FinancePayment::getPaymentDate, payDate)
                .set(FinancePayment::getRemark, form.getRemark()));
        FinancePayment now = paymentMapper.selectById(id);
        paymentAccountMapper.delete(new LambdaQueryWrapper<FinancePaymentAccount>()
                .eq(FinancePaymentAccount::getPaymentId, id));
        itemMapper.delete(new LambdaQueryWrapper<FinancePaymentItem>().eq(FinancePaymentItem::getPaymentId, id));
        Long cid = now.getCompanyId();
        BigDecimal paid = saveAccounts(now, accounts, items, cid);
        BigDecimal settled = saveItems(now, items, cid);
        assertSettledWithinPaid(settled, paid);
        writeMainTotals(id, now);
    }

    /**
     * 落分款明细并回写主表「付款金额 + 首行账户」，返回**付款合计**。
     *
     * <p><b>归一化</b>：{@code accounts} 为空但主表有 {@code accountId}（老 payload / 内部调用）
     * ⇒ 用单账户 + 核销合计造一条分款行。这样"分款表即权威"没有例外，审核/反审核只看本表。</p>
     *
     * <p>校验：账户必选、同一账户不得重复行（合并为一行的口径）、金额必须 &gt; 0、账户必须存在。</p>
     */
    private BigDecimal saveAccounts(FinancePayment payment, List<FinancePaymentAccount> accounts,
                                    List<FinancePaymentItem> items, Long cid) {
        List<FinancePaymentAccount> rows = accounts == null ? new ArrayList<>() : new ArrayList<>(accounts);
        if (rows.isEmpty()) {
            if (payment.getAccountId() == null) throw new BusinessException("付款账户不能为空");
            BigDecimal fallback = BigDecimal.ZERO;
            for (FinancePaymentItem it : (items == null ? List.<FinancePaymentItem>of() : items))
                fallback = fallback.add(it.getThisAmount() != null ? it.getThisAmount() : BigDecimal.ZERO);
            // ⚠️ F7-213（2026-09-29 审核批 B，镜像收款侧 F7-205）：**没有分款行、也没有核销明细 ⇒ 金额只会是 0**。
            //    建单接口从不接收 payload 的 amount（金额一律由分款明细推导），故这种请求先前会**静默落一张
            //    0 元付款单**并可通过审核。这里显式拒绝。
            if (fallback.compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("未填付款金额：请填写「付款账户 + 金额」，或在「本次核销」里选择核销明细（两者不能同时为空）");
            FinancePaymentAccount only = new FinancePaymentAccount();
            only.setAccountId(payment.getAccountId());
            only.setAmount(fallback);
            only.setRemark("单账户（旧入口）");
            rows.add(only);
        }
        BigDecimal total = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        int rowNo = 0;
        for (FinancePaymentAccount acc : rows) {
            rowNo++;
            if (acc.getAccountId() == null) throw new BusinessException("分款第 " + rowNo + " 行未选择付款账户");
            if (!seen.add(acc.getAccountId()))
                throw new BusinessException("分款第 " + rowNo + " 行的账户重复（同一账户请合并为一行）");
            BigDecimal amt = acc.getAmount() != null ? acc.getAmount() : BigDecimal.ZERO;
            if (amt.compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("分款第 " + rowNo + " 行金额必须大于 0");
            FinanceAccount fa = accountMapper.selectById(acc.getAccountId());
            if (fa == null) throw new BusinessException("分款第 " + rowNo + " 行账户不存在（ID=" + acc.getAccountId() + "）");
            // ⚠️ F7-215（2026-09-29 审核批 B，镜像收款侧 F7-207）：**停用账户（status=0）不能付款** ——
            //    付款是**出账**，比收款更该拦（对照现金销售结算 `SaleOrderServiceImpl.normalizeSettle:200-201` 已拦）。
            //    status 为 null 的历史行不拦（保守：只拒绝显式停用）。
            if (fa.getStatus() != null && fa.getStatus() == 0)
                throw new BusinessException("分款第 " + rowNo + " 行账户「" + fa.getAccountName() + "」已停用，不能用于付款");
            acc.setId(null); acc.setPaymentId(payment.getId()); acc.setAccountName(fa.getAccountName());
            if (cid != null && cid > 0) acc.setCompanyId(cid);
            paymentAccountMapper.insert(acc);
            total = total.add(amt);
        }
        payment.setAmount(total);
        payment.setAccountId(rows.get(0).getAccountId());
        payment.setAccountName(rows.get(0).getAccountName());
        return total;
    }

    /**
     * 落核销明细（**可为空** = 核销开关关闭），返回**核销合计**。
     * <p>行校验与 F7-33（非负）/F7-37（必须关联应付台账）一致 —— 开关只决定"要不要核销"，
     * 一旦核销了某行，该行仍必须指向真实台账，否则钱付出而应付没减。</p>
     */
    private BigDecimal saveItems(FinancePayment payment, List<FinancePaymentItem> items, Long cid) {
        BigDecimal total = BigDecimal.ZERO;
        if (items == null) return total;
        int rowNo = 0;
        for (FinancePaymentItem it : items) {
            rowNo++;
            // F7-33（2026-09-19）：建单即拦负数金额（审核侧另有兜底，覆盖历史草稿）
            // ⚠️ F7-214（2026-09-29 审核批 B，镜像收款侧 F7-206）：**核销金额必须为正** —— 原先只拦负数，
            //    0 金额行会写一条 0 元核销流水并走正常核销分支（台账 ±0），纯噪音。
            //    口径：**新数据从严（建单/改单即拒）**；审核处只兜底历史草稿的**负数**，保留可审计性。
            if (it.getThisAmount() == null || it.getThisAmount().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("付款明细第 " + rowNo + " 行核销金额必须大于 0");
            // F7-37（2026-09-19）：明细必须关联应付台账 —— 缺台账的明细在审核时会被**静默跳过**，
            // 而资金流水仍按全额入账（钱付出、应付没减）。多付的部分走"对该应付**超额付款**"（自动生成预付台账）。
            if (it.getPayableId() == null)
                throw new BusinessException("付款明细第 " + rowNo + " 行未关联应付台账（不能只填金额，请从「未付款」里选择应付单）");
            it.setId(null); it.setPaymentId(payment.getId());
            total = total.add(it.getThisAmount() != null ? it.getThisAmount() : BigDecimal.ZERO);
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        return total;
    }

    /** 新增护栏（2026-09-29）：核销合计不能超过付款合计 —— 核销的是"本次付出去的钱" */
    private void assertSettledWithinPaid(BigDecimal settled, BigDecimal paid) {
        if (settled.compareTo(paid) > 0)
            throw new BusinessException("核销合计 " + settled.stripTrailingZeros().toPlainString()
                    + " 不能超过付款合计 " + paid.stripTrailingZeros().toPlainString()
                    + "（多出的部分请不要核销，它会作为预付/未核销余额挂账）");
    }

    /** 回写主表「付款金额 + 首行账户」（分款明细落库后调用） */
    private void writeMainTotals(Long id, FinancePayment src) {
        FinancePayment u = new FinancePayment();
        u.setId(id);
        u.setAmount(src.getAmount());
        u.setAccountId(src.getAccountId());
        u.setAccountName(src.getAccountName());
        paymentMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAttach(FinancePayment payment) {
        // F7-39#4（2026-09-19）：补事务与状态校验 —— 原实现直接 updateById，可给**已作废**的付款单改附件
        // （作废单据应冻结，否则附件可被事后替换，审计留痕失真）。
        FinancePayment old = paymentMapper.selectById(payment.getId());
        if (old == null) throw new BusinessException("付款单不存在");
        if (DocStatus.CANCELLED.getCode().equals(old.getStatus()))
            throw new BusinessException("已作废的付款单不可修改附件");
        paymentMapper.updateById(payment);
    }

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
    public void audit(Long id) { audit(id, false); }

    @Override @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, boolean allowOverdraft) {
        FinancePayment payment = paymentMapper.selectById(id);
        if (payment == null) throw new BusinessException("付款单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败 —— 否则会重复核销应付、重复写核销与资金流水
        if (!DocStatusGuard.claim(paymentMapper, FinancePayment::getId, id, FinancePayment::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        List<FinancePaymentItem> items = itemMapper.selectList(new LambdaQueryWrapper<FinancePaymentItem>().eq(FinancePaymentItem::getPaymentId, id));
        // 2026-09-29 多账户（与收款侧对称）：分款明细即权威 —— 审核按行逐账户校验余额 + 逐账户写流水。
        // 历史草稿（无分款行）由 accountsForAudit 按主表账户 + 金额兜底成一行，故下面没有分支判断。
        List<FinancePaymentAccount> payAccounts = accountsForAudit(payment);
        // F7-219（2026-09-29 审核批 B）：审核前**复核「核销合计 ≤ 付款合计」** —— 该护栏原先只在 create/update
        // 调用，历史草稿（或直调 API）可"核销额 > 付款额"：多核销应付且 `unsettled ≤ 0` ⇒ 既不生成预付台账、
        // 资金流水又少于核销额（钱没付那么多、应付却核销了那么多）。此处按"明细声明额"先算一次再进循环。
        BigDecimal declaredSettled = BigDecimal.ZERO;
        for (FinancePaymentItem it : items)
            declaredSettled = declaredSettled.add(it.getThisAmount() != null ? it.getThisAmount() : BigDecimal.ZERO);
        assertSettledWithinPaid(declaredSettled, payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO);
        // 核销合计：用于算「未核销余额 = 付款总额 − 核销合计」（核销开关关闭 ⇒ 明细为空 ⇒ 全额未核销）
        BigDecimal settledTotal = BigDecimal.ZERO;
        // F7-36（2026-09-19）：账户余额校验 —— 与费用单对齐（FinanceExpenseServiceImpl.audit:96-101）。
        // 修复前付款审核不校验余额、直接写支出流水（账户余额由流水实时累计）⇒ 账户可被透支（实测余额 200 付 500 成功）。
        // F7-140（2026-09-20）：**账户行锁**。下面"读余额校验 → 写支出流水"是两步（余额是 Σ 流水的派生值），
        // 并发两笔付款会各自读到相同的旧余额、双双通过校验 ⇒ **账户被透支**（报告 §7 已把这处非原子性记为 P3 残留）。
        // 同一账户的审核串行化即可闭合；费用单（FinanceExpenseServiceImpl.audit）为同款写法，已一并加锁。
        // 2026-09-29：校验口径由「payment.amount」细化到**逐账户**（A 付 50 + B 付 100 ⇒ 各自要够），
        // 否则"总余额够、单账户不够"会被放过，写流水时该账户余额变负。
        // 2026-10-09（用户口径「扣款时余额不足 ⇒ 提示，用户确认后可通过」）：记录"确认后仍透支"的账户 ⇒ 写进该账户那条流水备注留痕
        Map<Long, String> overdraftNotes = new HashMap<>();
        for (FinancePaymentAccount acc : payAccounts) {
            lockAccount(acc.getAccountId());
            BigDecimal need = acc.getAmount() != null ? acc.getAmount() : BigDecimal.ZERO;
            // F7-140：用**当前读**取余额 —— 一致性读会读到事务开始时的旧快照，导致并发第二笔仍通过校验
            // F7-239（2026-09-29 审核批 C）：补显式租户条件（口径同 lockAccount / 费用侧）
            Long balCid = CompanyContext.get();
            if (balCid != null && balCid <= 0) balCid = null;
            Map<String, Object> balRow = accountMapper.sumBalanceForUpdate(acc.getAccountId(), balCid);
            BigDecimal accountBal = (balRow == null || balRow.get("balance") == null)
                    ? BigDecimal.ZERO : new BigDecimal(balRow.get("balance").toString());
            if (accountBal.subtract(need).compareTo(BigDecimal.ZERO) < 0) {
                // ① **未确认** ⇒ 抛业务码 409。抛错在任何写库之前（状态未置 AUDITED、应付未核销、流水未写）
                //    ⇒ "业务码非 200"就一定**钱没动**，前端据此弹确认框，脚本也不会把"没执行"误当成功。
                // ② **已确认** ⇒ 放行（该账户可被扣成负数），但必须留痕（warn 日志 + 该账户那条流水备注），
                //    与委外「强制出库 ⇒ 允许负库存」同口径：不拦，但事后必须查得到。
                if (!allowOverdraft)
                    throw new BusinessException(409, "账户「" + acc.getAccountName() + "」余额不足：当前余额 " + accountBal
                            + "，本次付款 " + need + "，付款后余额将为 " + accountBal.subtract(need)
                            + "。确认后将继续付款（该账户将透支）。");
                overdraftNotes.put(acc.getAccountId(),
                        "[余额不足已确认｜余额 " + accountBal + " → " + accountBal.subtract(need) + "]");
                log.warn("付款审核：账户余额不足已由用户确认，允许透支付款 —— paymentCode={}, accountId={}, 余额={}, 本次付款={}, 付款后={}",
                        payment.getCode(), acc.getAccountId(), accountBal, need, accountBal.subtract(need));
            }
        }
        // 核销应付：更新台账 + 写入核销流水（双向可追溯），超额部分生成负数应付（预付）
        int rowNo = 0;
        for (FinancePaymentItem it : items) {
            rowNo++;
            // F7-37（2026-09-19）：不再静默跳过（跳过 ⇒ 资金流水全额入账而应付未核销）。
            // create 已拦新数据，此处兜底**历史草稿**：明细缺台账 / 台账不存在一律拒绝审核。
            if (it.getPayableId() == null)
                throw new BusinessException("付款明细第 " + rowNo + " 行未关联应付台账，无法审核（请补全或作废重开）");
            FinancePayable p = payableMapper.selectById(it.getPayableId());
            if (p == null)
                throw new BusinessException("付款明细指向的应付台账不存在（第 " + rowNo + " 行，台账ID=" + it.getPayableId() + "），请刷新后重试");
            // I27（2026-09-18 修复，与应收侧对称）：预付台账（ADVANCE，负数应付）不可作为付款核销目标，
            // 否则会生成"预付的预付"（语义错误 + 单号被层层追加后缀）
            if (SettlementStatus.ADVANCE.getCode().equals(p.getStatus()))
                throw new BusinessException("应付单「" + p.getBillNo() + "」是预付台账（多付款待抵扣），不能作为付款核销的目标；如需冲回预付款请走退款或冲销流程");
            // F7-32（2026-09-19）：对象归属一致性 —— 必须核销**同一供应商**的应付。
            // 修复前只按 payableId 取台账、不校验归属（实测：供应商 34 的付款单把供应商 26 的应付核销掉）。
            if (payment.getSupplierId() == null || !payment.getSupplierId().equals(p.getSupplierId()))
                throw new BusinessException("付款供应商与被核销应付的供应商不一致（付款供应商ID=" + payment.getSupplierId()
                        + "，应付供应商ID=" + p.getSupplierId() + "），请分开制单");
            // F7-33（2026-09-19）：金额不得为负 —— 修复前负数会反向调整台账（实测 paid 变负、unpaid 虚增）
            if (it.getThisAmount() != null && it.getThisAmount().compareTo(BigDecimal.ZERO) < 0)
                throw new BusinessException("付款明细金额不能为负数");
            BigDecimal amt = it.getThisAmount() != null ? it.getThisAmount() : BigDecimal.ZERO;
            // 2026-09-29：核销合计按"本行核销额"累加（超额那一行也算核销掉了这么多钱 —— 超出未付的部分
            // 由超额分支自己生成预付台账），故"未核销余额 = 付款总额 − Σ核销额"不会重复计预付。
            settledTotal = settledTotal.add(amt);
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
                // 生成负数应付（预付单），sourceBillType=ADVANCE_LEDGER + sourceId=原付款单id 用于反审核精确定位
                // ⚠️ F7-216（2026-09-29 审核批 B）：单号改为 `advanceBillNo(付款单号)`，与下方
                //    createUnsettledAdvance（未核销差额）**及收款侧 F7-203 修复后的口径统一**。
                //    原先此处走 `payableHelper.newBillNo()`（YF- 日流水，每次审核恒为新号）⇒ 紧接着按该号查
                //    "是否已存在"**永远查不到** ⇒ 下面 existAdv 分支是死代码，"反审核后重审复用同一行"从不成立，
                //    每轮反审核→重审都多留一条 CANCELLED 预付行。
                //    （D5「台账号一律 YF-」自 2026-09-29 预收/预付台账落地起已对**预付行**让位：键必须稳定。）
                FinancePayable advance = new FinancePayable();
                advance.setBillNo(PayableHelper.advanceBillNo(payment.getCode()));
                advance.setSupplierId(p.getSupplierId());
                advance.setSupplierName(p.getSupplierName());
                // 主体类型随付款单固化（与 createUnsettledAdvance 一致；应付列表/汇总要按类型筛）
                advance.setSupplierType(payment.getSupplierType());
                // F7-53（2026-09-19）：来源类型必须取 SourceBillType 的合法值（原写 SettlementStatus.ADVANCE）
                advance.setSourceBillType(SourceBillType.ADVANCE_LEDGER.getCode());
                // F7-216：来源单号 = **本付款单号**（原写"被核销应付的来源单"⇒ 应付列表「来源」列对两种预付
                // 显示不同语义：一处是采购退货单号、一处是付款单号）
                advance.setSourceBillNo(payment.getCode());
                advance.setSourceId(id);
                advance.setTransferredToReceivable(0);
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
                // ⚠️ F7-212（2026-09-29 审核批 B）：**补 CAS 条件** —— 与上方超额分支（`:349-355`）及收款侧 F7-204 同一手法。
                //    原先正常分支没有余额前置条件：两笔并发付款（**不同账户**、同一应付）各自 `unpaid -= amt`
                //    ⇒ 未付额可转**负**、状态被写成 SETTLED，且**不会生成预付台账**（只有"读取时 newUnpaid<0"的
                //    超额分支才生成）⇒ 钱付出去了、台账却没有任何"多付"的痕迹，无法自动对账。
                int rows = payableMapper.update(null, new LambdaUpdateWrapper<FinancePayable>()
                        .eq(FinancePayable::getId, p.getId())
                        .apply("IFNULL(unpaid_amount, 0) >= {0}", amt)
                        .setSql("status = CASE WHEN IFNULL(unpaid_amount, 0) - (" + amtSql + ") <= 0 THEN '"
                                + SettlementStatus.SETTLED.getCode() + "' ELSE '" + SettlementStatus.PARTIAL.getCode() + "' END")
                        .setSql("paid_amount = IFNULL(paid_amount, 0) + (" + amtSql + ")")
                        .setSql("unpaid_amount = IFNULL(unpaid_amount, 0) - (" + amtSql + ")"));
                if (rows == 0) throw new BusinessException("应付台账「" + p.getBillNo()
                        + "」的未付额已发生变化（可能被其它单据并发核销），请刷新后重试");
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
        // 写资金流水（账户余额由流水实时累计，不再维护余额快照）：2026-09-29 多账户 ⇒ **按分款明细逐条写**
        //（A 付 50 + B 付 100 ⇒ 两条流水，各自账户余额 -50/-100 —— 一条汇总流水会让两个账户都对不上账）
        for (FinancePaymentAccount acc : payAccounts)
            // 2026-10-09：若该账户是"余额不足已确认"后放行的 ⇒ 把留痕写进它**自己那条**流水（多账户时不误标到别的账户）
            writeFlow(acc.getAccountId(), acc.getAccountName(), acc.getAmount(), payment, CashflowType.PAYMENT,
                    overdraftNotes.get(acc.getAccountId()));
        // 未核销余额 → 预付台账（2026-09-29 用户口径②）：核销开关关闭 ⇒ 全额；部分核销 ⇒ 差额
        BigDecimal paidSum = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
        BigDecimal unsettled = paidSum.subtract(settledTotal);
        if (unsettled.compareTo(BigDecimal.ZERO) > 0) createUnsettledAdvance(payment, unsettled);
        // 更新付款单状态
        FinancePayment u = new FinancePayment(); u.setId(id); u.setStatus(DocStatus.AUDITED.getCode()); paymentMapper.updateById(u);
    }

    /** F7-140（2026-09-20）：账户行锁（带租户条件）—— 让"余额校验 + 写流水"在同一账户上串行，防并发透支 */
    private void lockAccount(Long accountId) {
        Long cid = CompanyContext.get();
        if (cid != null && cid <= 0) cid = null;
        if (accountMapper.selectForUpdate(accountId, cid) == null) throw new BusinessException("付款账户不存在");
    }

    /** F7-36（2026-09-19）：账户实时余额 = Σ(流水 income - expense)（口径与 FinanceExpenseServiceImpl.accountBalance 一致） */
    private BigDecimal accountBalance(Long accountId) {
        Map<Long, Map<String, Object>> map = accountMapper.sumBalance(List.of(accountId));
        Map<String, Object> row = map.get(accountId);
        if (row == null || row.get("balance") == null) return BigDecimal.ZERO;
        return new BigDecimal(row.get("balance").toString());
    }

    /**
     * 审核/反审核用的分款行：历史草稿（无分款行）按主表账户 + 金额兜底成一行，
     * 保证"**分款表即权威**"没有例外 —— 审核与反审核走同一份行，流水才能严格对称。
     */
    private List<FinancePaymentAccount> accountsForAudit(FinancePayment payment) {
        List<FinancePaymentAccount> rows = getAccounts(payment.getId());
        if (!rows.isEmpty()) return rows;
        if (payment.getAccountId() == null) throw new BusinessException("付款账户不能为空");
        FinancePaymentAccount only = new FinancePaymentAccount();
        only.setPaymentId(payment.getId());
        only.setAccountId(payment.getAccountId());
        only.setAccountName(payment.getAccountName());
        only.setAmount(payment.getAmount());
        only.setRemark("单账户（历史草稿兜底）");
        return List.of(only);
    }

    /**
     * 写一条资金流水（付款出账 / 反审核冲正共用）。
     * <p>2026-09-29 多账户改造把"一条汇总流水"改成"按分款逐条"后抽出的公共代码（与收款侧 writeFlow 对称）：
     * 方向由 {@code type} 决定（PAYMENT=支出、PAYMENT_REVERSE=冲正收入），金额一律传正数。</p>
     */
    private void writeFlow(Long accountId, String accountName, BigDecimal amount, FinancePayment payment,
                           CashflowType type, String remark) {
        FinanceCashflow cf = new FinanceCashflow();
        cf.setFlowNo(gen(BillPrefix.CASHFLOW, cashflowMapper));
        cf.setAccountId(accountId);
        cf.setAccountName(accountName);
        cf.setFlowType(type.getCode());
        cf.setRelatedBillNo(payment.getCode());
        cf.setRelatedBillType(CashflowRelatedType.PAYMENT.getCode());
        BigDecimal amt = amount != null ? amount : BigDecimal.ZERO;
        if (CashflowType.PAYMENT_REVERSE == type) {
            cf.setIncome(amt);
            cf.setExpense(BigDecimal.ZERO);
            if (remark != null) cf.setRemark(remark);
        } else {
            cf.setIncome(BigDecimal.ZERO);
            cf.setExpense(amt);
            // 2026-10-09：支出侧也支持备注 —— 用于「余额不足已确认 ⇒ 允许透支扣款」的留痕。
            // 其它调用点传 null（含反审核的冲正走上面那条分支）⇒ 行为与改造前完全一致，普通付款流水不带备注。
            if (remark != null) cf.setRemark(remark);
        }
        cashflowMapper.insert(cf);
    }

    /**
     * 未核销余额 → 预付台账（2026-09-29 用户口径②：「核销项做成开关」；与收款侧 createUnsettledAdvance 对称）。
     *
     * <p><b>为什么必须有这一步</b>：核销开关关闭（明细为空）或只核销了一部分时，"付出去的钱"与"已核销的钱"
     * 之间会有差额。若只是记流水不动台账，就是 F7-37 修掉的错账（钱出去、应付没减）—— 所以差额必须落到
     * 台账上：生成一条 <b>负数应付</b>（{@code status=ADVANCE}，即预付：我方多付，供应商欠我方），
     * 后续可用它抵扣或走退款/冲销。</p>
     *
     * <p>单号取「付款单号 + -ADVANCE」（{@link PayableHelper#advanceBillNo}：归一化 + 超长护栏），
     * 来源 {@code source_bill_type=ADVANCE_LEDGER + source_id=付款单id} ⇒ 反审核按此**精确冲销**
     * （见 {@link #unAudit} 第 3 步）；重新审核复用同一 {@code bill_no} 行（唯一键），不重复建。</p>
     */
    private void createUnsettledAdvance(FinancePayment payment, BigDecimal unsettled) {
        FinancePayable adv = new FinancePayable();
        adv.setBillNo(PayableHelper.advanceBillNo(payment.getCode()));
        adv.setSupplierId(payment.getSupplierId());
        adv.setSupplierName(payment.getSupplierName());
        // 主体类型随付款单固化（应付汇总/列表要按类型筛）
        adv.setSupplierType(payment.getSupplierType());
        adv.setSourceBillType(SourceBillType.ADVANCE_LEDGER.getCode());
        adv.setSourceBillNo(payment.getCode());
        adv.setSourceId(payment.getId());
        adv.setAmount(unsettled.negate());
        adv.setPaidAmount(BigDecimal.ZERO);
        adv.setUnpaidAmount(unsettled.negate());
        adv.setDueDate(payment.getPaymentDate());
        adv.setStatus(SettlementStatus.ADVANCE.getCode());
        adv.setTransferredToReceivable(0);
        adv.setRemark("付款预付（未核销差额，供应商欠我方）");
        FinancePayable exist = payableMapper.selectOne(new LambdaQueryWrapper<FinancePayable>()
                .eq(FinancePayable::getBillNo, adv.getBillNo()).last("LIMIT 1"));
        if (exist != null) {
            adv.setId(exist.getId());
            payableMapper.updateById(adv);
        } else {
            payableMapper.insert(adv);
        }
    }

    /**
     * 账单进度联动（**以台账为准的同源重算**）—— F7-240（2026-09-29 批 D 修复，与收款侧同款）。
     *
     * <p>原实现把核销流水金额**增量累加**到账单明细（`item.paid += st.amount`），台账侧却是原子 SET
     * ⇒ 绕过核销流水的订正（直接作废结算流水）账单不会回退、也不自愈（库中应付侧已 1 条：明细 10 vs 台账 0）。
     * 现改为直接取被引用台账的 `paid/unpaid`（台账=唯一真相），后续任何核销都会顺带抹平既有漂移。</p>
     */
    private void syncBillProgress(Long paymentId) {
        List<FinanceSettlement> sts = settlementMapper.selectList(
                new LambdaQueryWrapper<FinanceSettlement>()
                        .eq(FinanceSettlement::getReceiptPaymentId, paymentId)
                        .eq(FinanceSettlement::getDirection, SettlementDirection.PAY.getCode())
                        .eq(FinanceSettlement::getStatus, SettlementRecordStatus.NORMAL.getCode()));
        Set<Long> billIds = new HashSet<>();
        for (FinanceSettlement st : sts) {
            Long ledgerId = st.getPayableReceivableId();
            if (ledgerId == null) continue;
            FinancePayable ledger = payableMapper.selectById(ledgerId);
            if (ledger == null) continue;
            // F7-240：以台账当前值为准（不再对明细自身的历史值做加减）
            BigDecimal paid = ledger.getPaidAmount() != null ? ledger.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal unpaid = ledger.getUnpaidAmount() != null ? ledger.getUnpaidAmount() : BigDecimal.ZERO;
            List<FinanceBillItem> items = billItemMapper.selectList(
                    new LambdaQueryWrapper<FinanceBillItem>().eq(FinanceBillItem::getSourceId, ledgerId));
            for (FinanceBillItem item : items) {
                item.setPaidAmount(paid);
                item.setUnpaidAmount(unpaid);
                billItemMapper.updateById(item);
                billIds.add(item.getBillId());
            }
        }
        // 重算账单主表 paidAmount/unpaidAmount
        for (Long billId : billIds) {
            recalcBill(billId);
        }
    }

    /**
     * 反审核时的账单进度回退（**同源重算**）—— F7-240，与收款侧同款。
     *
     * <p>调用点在"冲销台账/作废流水"**之前**，故取「台账当前值 − 本单对该台账的有效核销合计」
     * = 冲销之后台账将变成的值，直接写进明细 ⇒ 与 {@link #syncBillProgress} 完全同源。</p>
     */
    private void reverseBillProgress(Long paymentId) {
        List<FinanceSettlement> sts = settlementMapper.selectList(
                new LambdaQueryWrapper<FinanceSettlement>()
                        .eq(FinanceSettlement::getReceiptPaymentId, paymentId)
                        .eq(FinanceSettlement::getDirection, SettlementDirection.PAY.getCode())
                        .eq(FinanceSettlement::getStatus, SettlementRecordStatus.NORMAL.getCode()));
        java.util.Map<Long, BigDecimal> deltaByLedger = new java.util.HashMap<>();
        for (FinanceSettlement st : sts) {
            if (st.getPayableReceivableId() == null) continue;
            deltaByLedger.merge(st.getPayableReceivableId(),
                    st.getAmount() != null ? st.getAmount() : BigDecimal.ZERO, BigDecimal::add);
        }
        Set<Long> billIds = new HashSet<>();
        for (java.util.Map.Entry<Long, BigDecimal> e : deltaByLedger.entrySet()) {
            FinancePayable ledger = payableMapper.selectById(e.getKey());
            if (ledger == null) continue;
            BigDecimal paid = (ledger.getPaidAmount() != null ? ledger.getPaidAmount() : BigDecimal.ZERO).subtract(e.getValue());
            BigDecimal unpaid = (ledger.getUnpaidAmount() != null ? ledger.getUnpaidAmount() : BigDecimal.ZERO).add(e.getValue());
            List<FinanceBillItem> items = billItemMapper.selectList(
                    new LambdaQueryWrapper<FinanceBillItem>().eq(FinanceBillItem::getSourceId, e.getKey()));
            for (FinanceBillItem item : items) {
                item.setPaidAmount(paid);
                item.setUnpaidAmount(unpaid);
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
            // F7-35（2026-09-19）：反核销后补 PARTIAL —— 该台账若还有**其它**付款的核销，
            // 冲销后未付额介于 (0, amount) 之间，状态应回到 PARTIAL；原实现只有 SETTLED/UNSETTLED 两态，
            // 会把"部分结清"写成"未结清"（与应收侧对称修复）。
            int rows = payableMapper.update(null, new LambdaUpdateWrapper<FinancePayable>()
                    .eq(FinancePayable::getId, p.getId())
                    .setSql("status = CASE WHEN IFNULL(unpaid_amount, 0) + (" + amtSql + ") <= 0 THEN '"
                            + SettlementStatus.SETTLED.getCode() + "' WHEN IFNULL(unpaid_amount, 0) + (" + amtSql
                            + ") >= IFNULL(amount, 0) THEN '" + SettlementStatus.UNSETTLED.getCode()
                            + "' ELSE '" + SettlementStatus.PARTIAL.getCode() + "' END")
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
        // ⚠️ 2026-09-29 修复（与收款侧同一条潜伏缺陷）：预付行的来源类型写的是 SourceBillType.ADVANCE_LEDGER
        //    （"ADVANCE_LEDGER"，F7-53 起），而这里原先过滤 SettlementStatus.ADVANCE.getCode()（"ADVANCE"）
        //    ⇒ **永不命中**：反审核后预付台账不会被冲销（金额没清零、状态仍是 ADVANCE，账上凭空多出一笔
        //    "供应商欠我方"）。现按 ADVANCE_LEDGER 查，并兼容早期写入 "ADVANCE" 的历史行。
        //    （实测库中现无此类残留，属**未被触发**的潜伏缺陷，与本轮"未核销余额也走预付台账"同一条路径。）
        List<FinancePayable> advances = payableMapper.selectList(
                new LambdaQueryWrapper<FinancePayable>()
                        .in(FinancePayable::getSourceBillType,
                                SourceBillType.ADVANCE_LEDGER.getCode(), SettlementStatus.ADVANCE.getCode())
                        .eq(FinancePayable::getSourceId, id));
        for (FinancePayable adv : advances) {
            // I29 口径（2026-09-18）：作废预付台账时**金额一并清零**（原金额记入备注留痕），
            // 与应付反审核冲销保持同一口径（旧实现只置状态，作废行仍带金额）
            payableHelper.cancelLedger(adv);
        }
        // 4) 写冲正资金流水（保留审计轨迹，不删除原流水；账户余额由流水实时累计）
        //    2026-09-29 多账户：**按分款明细逐条冲正**，与审核时逐条入账严格对称（否则账户余额各差一截）
        for (FinancePaymentAccount acc : accountsForAudit(payment))
            writeFlow(acc.getAccountId(), acc.getAccountName(), acc.getAmount(), payment,
                    CashflowType.PAYMENT_REVERSE, "反审核冲正");
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
        // F7-39#3（2026-09-19）：冲突检测 + 递增重试（原为解析失败静默兜底 seq=1 ⇒ 可能重号）
        for (int i = 0; i < 999; i++) {
            String code = prefix + d + String.format("%03d", seq);
            if (mapper.selectCount(new LambdaQueryWrapper<FinancePayment>().eq(FinancePayment::getCode, code)) == 0) return code;
            seq++;
        }
        throw new BusinessException("当日付款单编号已用尽（前缀 " + pat + "），请联系管理员");
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
        // F7-39#3（2026-09-19）：冲突检测 + 递增重试（finance_cashflow.flow_no 无索引，原会静默重号）
        for (int i = 0; i < 999; i++) {
            String code = prefix + d + String.format("%03d", seq);
            if (mapper.selectCount(new LambdaQueryWrapper<FinanceCashflow>().eq(FinanceCashflow::getFlowNo, code)) == 0) return code;
            seq++;
        }
        throw new BusinessException("当日资金流水号已用尽（前缀 " + pat + "），请联系管理员");
    }
}
