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
import com.beichen.erp.finance.common.SourceBillType;
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
    /** 收款单分款明细（2026-09-29 多账户收款：一单可拆到多个账户） */
    private final FinanceReceiptAccountMapper receiptAccountMapper;
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
        // 分款明细条数（2026-09-29 多账户）：列表「账户」列据此显示「CASH-01」或「N 个账户」。
        // 一次批量查询（避免 N+1），并且是**唯一**让列表知道"这单有几个账户"的途径（主表只留首行快照）。
        Map<Long, Integer> accCount = new HashMap<>();
        if (!raw.getRecords().isEmpty()) {
            List<Long> ids = raw.getRecords().stream().map(FinanceReceipt::getId).toList();
            for (FinanceReceiptAccount a : receiptAccountMapper.selectList(
                    new LambdaQueryWrapper<FinanceReceiptAccount>().in(FinanceReceiptAccount::getReceiptId, ids))) {
                accCount.merge(a.getReceiptId(), 1, Integer::sum);
            }
        }
        res.setRecords(raw.getRecords().stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId()); m.put("code", r.getCode());
            m.put("customerId", r.getCustomerId()); m.put("customerName", r.getCustomerName());
            m.put("subjectType", r.getSubjectType());
            m.put("supplierId", r.getSupplierId()); m.put("supplierName", r.getSupplierName());
            m.put("accountId", r.getAccountId()); m.put("accountName", r.getAccountName());
            m.put("accountCount", accCount.getOrDefault(r.getId(), 0));
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
    /** 分款明细（2026-09-29 多账户）：按 id 升序 —— 首行即主表 account_id/account_name 的快照来源 */
    @Override public List<FinanceReceiptAccount> getAccounts(Long receiptId) {
        return receiptAccountMapper.selectList(new LambdaQueryWrapper<FinanceReceiptAccount>()
                .eq(FinanceReceiptAccount::getReceiptId, receiptId).orderByAsc(FinanceReceiptAccount::getId));
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

    /**
     * 建单（兼容旧签名）：系统自动单（销售单现金结算：单账户、立即审核）与历史页面旧 payload 走这里 ——
     * {@code accounts} 缺省 ⇒ 由单账户字段 + 核销合计归一化成一条分款行，调用方无需改。
     */
    @Override @Transactional(rollbackFor = Exception.class)
    public void create(FinanceReceipt receipt, List<FinanceReceiptItem> items) {
        create(receipt, null, items);
    }

    /**
     * 建单（2026-09-29 用户口径：一个收款单可**多账户分款** + **核销项做成开关**）。
     *
     * <ul>
     *   <li><b>多账户分款</b>：{@code accounts} 每行 = 一个账户本次收到的钱（A 50 + B 100）；主表
     *       {@code amount} = 分款合计，{@code account_id/account_name} = **首行**（列表列/老读法兼容）。</li>
     *   <li><b>核销开关</b>：{@code items} 可为空（关闭 = 只记收款不核销）。但"没核销的钱"不能悬空 ——
     *       审核时按差额生成预收/预付台账（见 {@link #createUnsettledAdvance}），否则就是 F7-37 修掉的
     *       "钱进账、台账不动"错账。开关打开时每行仍必须关联应收台账（F7-37 口径不变）。</li>
     *   <li><b>新增护栏</b>：核销合计 ≤ 收款合计（核销的是"本次收到的钱"）。</li>
     * </ul>
     */
    @Override @Transactional(rollbackFor = Exception.class)
    public void create(FinanceReceipt receipt, List<FinanceReceiptAccount> accounts, List<FinanceReceiptItem> items) {
        applyPartner(receipt);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) receipt.setCompanyId(cid);
        receipt.setCode(gen());
        receipt.setStatus(DocStatus.DRAFT.getCode());
        receiptMapper.insert(receipt);
        BigDecimal received = saveAccounts(receipt, accounts, items, cid);
        BigDecimal settled = saveItems(receipt, items, cid);
        assertSettledWithinReceived(settled, received);
        writeMainTotals(receipt.getId(), receipt);
    }

    /**
     * 草稿就地修改（2026-09-29 用户口径「加草稿可编辑」；家规：草稿在**详情页**改+存、列表不给「编辑」）。
     *
     * <p>只允许 DRAFT（已审核要先反审核）；字段与校验、payload 与 create 完全一致；
     * 分款与核销明细**整体替换**（先删后插，与物料售后详情页同范式）；单号与来源单据
     * {@code source_bill_*} 不动 —— 那是留痕，不该被编辑改掉。</p>
     */
    @Override @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FinanceReceipt form, List<FinanceReceiptAccount> accounts, List<FinanceReceiptItem> items) {
        FinanceReceipt old = receiptMapper.selectById(id);
        if (old == null) throw new BusinessException("收款单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus()))
            throw new BusinessException("只有草稿状态的收款单可修改（已审核请先反审核）");
        FinanceReceipt upd = new FinanceReceipt();
        upd.setId(id);
        upd.setSubjectType(form.getSubjectType() != null ? form.getSubjectType() : old.getSubjectType());
        upd.setCustomerId(form.getCustomerId() != null ? form.getCustomerId() : old.getCustomerId());
        upd.setSupplierId(form.getSupplierId() != null ? form.getSupplierId() : old.getSupplierId());
        upd.setCustomerName(old.getCustomerName());
        upd.setSupplierName(old.getSupplierName());
        upd.setReceiptDate(form.getReceiptDate() != null ? form.getReceiptDate() : old.getReceiptDate());
        upd.setRemark(form.getRemark());
        applyPartner(upd);                                  // 主体类型/往来单位名称快照重算
        // 用 update wrapper 显式 set（含 null）—— updateById 默认忽略 null，清空备注/日期会写不进去
        receiptMapper.update(null, new LambdaUpdateWrapper<FinanceReceipt>()
                .eq(FinanceReceipt::getId, id)
                .set(FinanceReceipt::getSubjectType, upd.getSubjectType())
                .set(FinanceReceipt::getCustomerId, upd.getCustomerId())
                .set(FinanceReceipt::getCustomerName, upd.getCustomerName())
                .set(FinanceReceipt::getSupplierId, upd.getSupplierId())
                .set(FinanceReceipt::getSupplierName, upd.getSupplierName())
                .set(FinanceReceipt::getReceiptDate, upd.getReceiptDate())
                .set(FinanceReceipt::getRemark, upd.getRemark()));
        FinanceReceipt now = receiptMapper.selectById(id);
        receiptAccountMapper.delete(new LambdaQueryWrapper<FinanceReceiptAccount>()
                .eq(FinanceReceiptAccount::getReceiptId, id));
        itemMapper.delete(new LambdaQueryWrapper<FinanceReceiptItem>().eq(FinanceReceiptItem::getReceiptId, id));
        Long cid = now.getCompanyId();
        BigDecimal received = saveAccounts(now, accounts, items, cid);
        BigDecimal settled = saveItems(now, items, cid);
        assertSettledWithinReceived(settled, received);
        writeMainTotals(id, now);
    }

    /** 主体类型 + 往来单位解析与名称快照（客户收款 / 供应商收款二选一；与旧 create 逐字一致） */
    private void applyPartner(FinanceReceipt receipt) {
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
    }

    /**
     * 落分款明细并回写主表「收款金额 + 首行账户」，返回**收款合计**。
     *
     * <p><b>归一化</b>：{@code accounts} 为空但主表有 {@code accountId}（销售单现金结算的自动单、历史页面旧
     * payload）⇒ 用单账户 + 核销合计造一条分款行。这样"分款表即权威"没有例外，审核/反审核只看本表。</p>
     *
     * <p>校验：账户必选、同一账户不得重复行（合并为一行的口径）、金额必须 &gt; 0、账户必须存在。</p>
     */
    private BigDecimal saveAccounts(FinanceReceipt receipt, List<FinanceReceiptAccount> accounts,
                                    List<FinanceReceiptItem> items, Long cid) {
        List<FinanceReceiptAccount> rows = accounts == null ? new ArrayList<>() : new ArrayList<>(accounts);
        if (rows.isEmpty()) {
            if (receipt.getAccountId() == null) throw new BusinessException("收款账户不能为空");
            BigDecimal fallback = BigDecimal.ZERO;
            for (FinanceReceiptItem it : (items == null ? List.<FinanceReceiptItem>of() : items))
                fallback = fallback.add(it.getThisAmount() != null ? it.getThisAmount() : BigDecimal.ZERO);
            FinanceReceiptAccount only = new FinanceReceiptAccount();
            only.setAccountId(receipt.getAccountId());
            only.setAmount(fallback);
            only.setRemark("单账户（系统自动单/旧入口）");
            rows.add(only);
        }
        BigDecimal total = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        int rowNo = 0;
        for (FinanceReceiptAccount acc : rows) {
            rowNo++;
            if (acc.getAccountId() == null) throw new BusinessException("分款第 " + rowNo + " 行未选择收款账户");
            if (!seen.add(acc.getAccountId()))
                throw new BusinessException("分款第 " + rowNo + " 行的账户重复（同一账户请合并为一行）");
            BigDecimal amt = acc.getAmount() != null ? acc.getAmount() : BigDecimal.ZERO;
            if (amt.compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("分款第 " + rowNo + " 行金额必须大于 0");
            FinanceAccount fa = accountMapper.selectById(acc.getAccountId());
            if (fa == null) throw new BusinessException("分款第 " + rowNo + " 行账户不存在（ID=" + acc.getAccountId() + "）");
            acc.setId(null); acc.setReceiptId(receipt.getId()); acc.setAccountName(fa.getAccountName());
            if (cid != null && cid > 0) acc.setCompanyId(cid);
            receiptAccountMapper.insert(acc);
            total = total.add(amt);
        }
        receipt.setAmount(total);
        receipt.setAccountId(rows.get(0).getAccountId());
        receipt.setAccountName(rows.get(0).getAccountName());
        return total;
    }

    /**
     * 落核销明细（**可为空** = 核销开关关闭），返回**核销合计**。
     * <p>行校验与 F7-33（非负）/F7-37（必须关联应收台账）一致 —— 开关只决定"要不要核销"，
     * 一旦核销了某行，该行仍必须指向真实台账，否则钱进账而应收没减。</p>
     */
    private BigDecimal saveItems(FinanceReceipt receipt, List<FinanceReceiptItem> items, Long cid) {
        BigDecimal total = BigDecimal.ZERO;
        if (items == null) return total;
        int rowNo = 0;
        for (FinanceReceiptItem it : items) {
            rowNo++;
            if (it.getThisAmount() != null && it.getThisAmount().compareTo(BigDecimal.ZERO) < 0)
                throw new BusinessException("收款明细金额不能为负数");
            if (it.getReceivableId() == null)
                throw new BusinessException("收款明细第 " + rowNo + " 行未关联应收台账（不能只填金额，请从「未收款」里选择应收单）");
            it.setId(null); it.setReceiptId(receipt.getId());
            total = total.add(it.getThisAmount() != null ? it.getThisAmount() : BigDecimal.ZERO);
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        return total;
    }

    /** 新增护栏（2026-09-29）：核销合计不能超过收款合计 —— 核销的是"本次收到的钱" */
    private void assertSettledWithinReceived(BigDecimal settled, BigDecimal received) {
        if (settled.compareTo(received) > 0)
            throw new BusinessException("核销合计 " + settled.stripTrailingZeros().toPlainString()
                    + " 不能超过收款合计 " + received.stripTrailingZeros().toPlainString()
                    + "（多出的部分请不要核销，它会作为预收/未核销余额挂账）");
    }

    /** 回写主表「收款金额 + 首行账户」（分款明细落库后调用） */
    private void writeMainTotals(Long id, FinanceReceipt src) {
        FinanceReceipt u = new FinanceReceipt();
        u.setId(id);
        u.setAmount(src.getAmount());
        u.setAccountId(src.getAccountId());
        u.setAccountName(src.getAccountName());
        receiptMapper.updateById(u);
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
        // 2026-09-29 多账户：分款明细（审核按行各写一条资金流水；create 已归一化、历史单已回填，故一般非空）
        List<FinanceReceiptAccount> receiptAccounts = getAccounts(id);
        // 核销合计：用于算「未核销余额 = 收款总额 − 核销合计」（核销开关关闭 ⇒ 明细为空 ⇒ 全额未核销）
        BigDecimal settledTotal = BigDecimal.ZERO;
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
            // 2026-09-29：核销合计按"本行核销额"累加（超额那一行也算核销掉了这么多钱 —— 超出未收的部分
            // 由超额分支自己生成预收台账），故"未核销余额 = 收款总额 − Σ核销额"不会重复计预收。
            settledTotal = settledTotal.add(amt);
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
                // F7-53（2026-09-19）：来源类型必须取 SourceBillType 的合法值 —— 原写 SettlementStatus.ADVANCE
                // （用错枚举填错字段）⇒ 前端映射查不到该值、列表"来源"列显示英文 "ADVANCE"。
                advance.setSourceBillType(SourceBillType.ADVANCE_LEDGER.getCode());
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
        // 写资金流水（账户余额由流水实时累计，不再维护余额快照）：2026-09-29 多账户 ⇒ **按分款明细逐条写**
        //（A 收 50 + B 收 100 ⇒ 两条流水，各自账户余额 +50/+100 —— 一条汇总流水会让两个账户都对不上账）
        if (receiptAccounts.isEmpty()) {
            // 兜底：主表有账户但分款表为空（理论上不会 —— create 会归一化、历史单已回填）
            writeFlow(receipt.getAccountId(), receipt.getAccountName(), receipt.getAmount(), receipt,
                    CashflowType.RECEIPT, null);
        } else {
            for (FinanceReceiptAccount acc : receiptAccounts)
                writeFlow(acc.getAccountId(), acc.getAccountName(), acc.getAmount(), receipt, CashflowType.RECEIPT, null);
        }
        // 未核销余额 → 预收/预付台账（2026-09-29 用户口径①）：核销开关关闭 ⇒ 全额；部分核销 ⇒ 差额
        BigDecimal receivedSum = receipt.getAmount() != null ? receipt.getAmount() : BigDecimal.ZERO;
        BigDecimal unsettled = receivedSum.subtract(settledTotal);
        if (unsettled.compareTo(BigDecimal.ZERO) > 0) createUnsettledAdvance(receipt, unsettled);
        FinanceReceipt u = new FinanceReceipt(); u.setId(id); u.setStatus(DocStatus.AUDITED.getCode()); receiptMapper.updateById(u);
    }

    /**
     * 写一条资金流水（收款入账 / 反审核冲正共用）。
     * <p>2026-09-29 多账户改造把"一条汇总流水"改成"按分款逐条"后抽出的公共代码：收入/支出方向由
     * {@code type} 决定（RECEIPT=收入、RECEIPT_REVERSE=冲正支出），金额一律传正数。</p>
     */
    private void writeFlow(Long accountId, String accountName, BigDecimal amount, FinanceReceipt receipt,
                           CashflowType type, String remark) {
        FinanceCashflow cf = new FinanceCashflow();
        cf.setFlowNo(genFlowNo());
        cf.setAccountId(accountId);
        cf.setAccountName(accountName);
        cf.setFlowType(type.getCode());
        cf.setRelatedBillNo(receipt.getCode());
        cf.setRelatedBillType(CashflowRelatedType.RECEIPT.getCode());
        BigDecimal amt = amount != null ? amount : BigDecimal.ZERO;
        if (CashflowType.RECEIPT_REVERSE == type) {
            cf.setIncome(BigDecimal.ZERO);
            cf.setExpense(amt);
            if (remark != null) cf.setRemark(remark);
        } else {
            cf.setIncome(amt);
            cf.setExpense(BigDecimal.ZERO);
        }
        cashflowMapper.insert(cf);
    }

    /**
     * 未核销余额 → 预收/预付台账（2026-09-29 用户口径①：「生成预收/预付台账 ADVANCE」）。
     *
     * <p><b>为什么必须有这一步</b>：核销开关关闭（明细为空）或只核销了一部分时，"收到的钱"与"已核销的钱"
     * 之间会有差额。若只是记流水不动台账，就是 F7-37 修掉的错账（钱进账、应收没减）—— 所以差额必须落到
     * 台账上：生成一条 <b>负数应收</b>（{@code status=ADVANCE}，即预收：我方欠客户 / 供应商欠我方），
     * 后续可用它抵扣或走退款/冲销。</p>
     *
     * <p>单号取「收款单号 + -ADVANCE」（{@link ReceivableHelper#advanceBillNo}：归一化 + 超长护栏），
     * 来源 {@code source_bill_type=ADVANCE_LEDGER + source_id=收款单id} ⇒ 反审核按此**精确冲销**
     * （见 {@link #unAudit} 第 3 步）；重新审核复用同一 {@code bill_no} 行（唯一键），不重复建。</p>
     */
    private void createUnsettledAdvance(FinanceReceipt receipt, BigDecimal unsettled) {
        FinanceReceivable adv = new FinanceReceivable();
        adv.setBillNo(ReceivableHelper.advanceBillNo(receipt.getCode()));
        adv.setCustomerId(receipt.getCustomerId());
        adv.setCustomerName(receipt.getCustomerName());
        adv.setSubjectType(receipt.getSubjectType() != null ? receipt.getSubjectType() : SubjectType.CUSTOMER.getCode());
        adv.setSupplierId(receipt.getSupplierId());
        adv.setSupplierName(receipt.getSupplierName());
        adv.setSourceBillType(SourceBillType.ADVANCE_LEDGER.getCode());
        adv.setSourceBillNo(receipt.getCode());
        adv.setSourceId(receipt.getId());
        adv.setAmount(unsettled.negate());
        adv.setPaidAmount(BigDecimal.ZERO);
        adv.setUnpaidAmount(unsettled.negate());
        adv.setDueDate(receipt.getReceiptDate());
        adv.setStatus(SettlementStatus.ADVANCE.getCode());
        adv.setRemark(SubjectType.SUPPLIER.getCode().equals(receipt.getSubjectType())
                ? "收款未核销（差额，供应商欠我方）"
                : "收款预收（未核销差额，我方欠客户）");
        FinanceReceivable exist = receivableMapper.selectOne(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getBillNo, adv.getBillNo()).last("LIMIT 1"));
        if (exist != null) {
            adv.setId(exist.getId());
            receivableMapper.updateById(adv);
        } else {
            receivableMapper.insert(adv);
        }
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
        // ⚠️ 2026-09-29 修复：预收行的来源类型写的是 SourceBillType.ADVANCE_LEDGER（"ADVANCE_LEDGER"，F7-53 起），
        //    而这里原先过滤 SettlementStatus.ADVANCE.getCode()（"ADVANCE"）⇒ **永不命中**：反审核后预收台账
        //    不会被冲销（金额没清零、状态仍是 ADVANCE，账上凭空多出一笔"我方欠客户"）。
        //    现按 ADVANCE_LEDGER 查，并兼容早期写入 "ADVANCE" 的历史行。
        //    （实测库中现无此类残留，属**未被触发**的潜伏缺陷，与本轮"未核销余额也走预收台账"同一条路径。）
        List<FinanceReceivable> advances = receivableMapper.selectList(
                new LambdaQueryWrapper<FinanceReceivable>()
                        .in(FinanceReceivable::getSourceBillType,
                                SourceBillType.ADVANCE_LEDGER.getCode(), SettlementStatus.ADVANCE.getCode())
                        .eq(FinanceReceivable::getSourceId, id));
        for (FinanceReceivable adv : advances) {
            // I29 口径（2026-09-18）：作废预收台账时**金额一并清零**（原金额记入备注留痕），
            // 与应收反审核冲销保持同一口径（旧实现只置状态，作废行仍带金额）
            receivableHelper.cancelLedger(adv);
        }
        // 4) 写冲正资金流水（保留审计轨迹，不删除原流水；账户余额由流水实时累计）
        //    2026-09-29 多账户：**按分款明细逐条冲正**，与审核时逐条入账严格对称（否则账户余额各差一截）
        List<FinanceReceiptAccount> receiptAccounts = getAccounts(id);
        if (receiptAccounts.isEmpty()) {
            writeFlow(receipt.getAccountId(), receipt.getAccountName(), receipt.getAmount(), receipt,
                    CashflowType.RECEIPT_REVERSE, "反审核冲正");
        } else {
            for (FinanceReceiptAccount acc : receiptAccounts)
                writeFlow(acc.getAccountId(), acc.getAccountName(), acc.getAmount(), receipt,
                        CashflowType.RECEIPT_REVERSE, "反审核冲正");
        }
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
        // F7-39#3（2026-09-19）：冲突检测 + 递增重试（原为解析失败静默兜底 seq=1 ⇒ 可能重号）
        for (int i = 0; i < 999; i++) {
            String code = BillPrefix.RECEIPT + d + String.format("%03d", seq);
            if (receiptMapper.selectCount(new LambdaQueryWrapper<FinanceReceipt>().eq(FinanceReceipt::getCode, code)) == 0) return code;
            seq++;
        }
        throw new BusinessException("当日收款单编号已用尽（前缀 " + pat + "），请联系管理员");
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
        // F7-39#3（2026-09-19）：冲突检测 + 递增重试 —— finance_cashflow.flow_no **无索引**，
        // 原兜底 seq=1 会静默重号（本批同时补唯一索引，双重兜底）。
        for (int i = 0; i < 999; i++) {
            String code = BillPrefix.CASHFLOW + d + String.format("%03d", seq);
            if (cashflowMapper.selectCount(new LambdaQueryWrapper<FinanceCashflow>().eq(FinanceCashflow::getFlowNo, code)) == 0) return code;
            seq++;
        }
        throw new BusinessException("当日资金流水号已用尽（前缀 " + pat + "），请联系管理员");
    }
}
