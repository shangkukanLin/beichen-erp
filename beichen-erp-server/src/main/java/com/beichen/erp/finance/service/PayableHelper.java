package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.entity.SupplierTypeRef;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 应付台账辅助：生成/冲减/删除应付，账期计算 */
@Component
@RequiredArgsConstructor
public class PayableHelper {

    private final FinancePayableMapper payableMapper;
    private final SupplierMapper supplierMapper;
    /**
     * F7-217（2026-09-29 审核批 B）：主体类型兜底改查本表（原先读 {@code Supplier.typeCodes}，而该字段
     * {@code @TableField(exist=false)}、经 {@code selectById} 加载恒为空 ⇒ 兜底失效）。
     */
    private final com.beichen.erp.supplier.mapper.SupplierTypeRefMapper typeRefMapper;

    /** 台账单号列宽上限（与 `finance_payable.bill_no` 的 varchar(50) 一致；与 ReceivableHelper 同名常量对称） */
    private static final int BILL_NO_MAX = 50;

    /**
     * 生成应付（amount 可为负数表示冲减）
     * @param sourceId 来源记录ID（交货/收货记录），用于后续编辑删除定位
     * @param bizDate  业务日期（收货/交货日期），用于计算到期日
     */
    public FinancePayable createPayable(Long supplierId, String sourceBillType, String sourceBillNo,
                                        Long sourceId, BigDecimal amount, LocalDate bizDate, String remark) {
        if (supplierId == null) throw new BusinessException("应付缺少供应商");
        Supplier s = supplierMapper.selectById(supplierId);
        FinancePayable fp = new FinancePayable();
        fp.setBillNo(generateBillNo());
        fp.setSupplierId(supplierId);
        fp.setSupplierName(s != null ? s.getName() : "");
        fp.setSourceBillType(sourceBillType);
        fp.setSourceBillNo(sourceBillNo);
        fp.setSourceId(sourceId);
        fp.setAmount(amount);
        fp.setPaidAmount(BigDecimal.ZERO);
        fp.setUnpaidAmount(amount);
        fp.setDueDate(calcDueDate(s, bizDate));
        fp.setStatus(SettlementStatus.UNSETTLED.getCode());
        fp.setRemark(remark);
        // 主体类型按业务场景固化（不实时取 supplier_type_ref）：供应商类型后续变更不能篡改历史账务
        fp.setSupplierType(resolveSupplierType(sourceBillType, s));
        fp.setTransferredToReceivable(0);
        payableMapper.insert(fp);
        return fp;
    }

    /**
     * 按 bill_no 保存应付台账：**存在则显式重置各字段，不存在才插入**。
     * <p>反审核是「冲销」——仅把台账置为 CANCELLED、记录保留，因此再次审核时直接 insert 同 bill_no
     * 会撞 uk_bill_no（采购单/采购退货的 bill_no 就是单据号，必须走本方法）。
     * 与销售侧 FinanceReceivable 的 saveReceivable() 同范式：不用 updateById 局部更新，
     * 而是显式 set 全部关键字段，避免字段策略导致漏更新（出现"再审核成功但台账仍是已冲销"）。</p>
     */
    public void saveByBillNo(FinancePayable fp) {
        if (fp == null || fp.getBillNo() == null || fp.getBillNo().isBlank()) {
            throw new BusinessException("应付台账缺少单号");
        }
        // 主体类型缺失时按业务场景补齐；供应商类型后续变更不得篡改历史账务
        if (fp.getSupplierType() == null || fp.getSupplierType().isBlank()) {
            Supplier s = fp.getSupplierId() != null ? supplierMapper.selectById(fp.getSupplierId()) : null;
            fp.setSupplierType(resolveSupplierType(fp.getSourceBillType(), s));
        }
        FinancePayable exist = payableMapper.selectOne(new LambdaQueryWrapper<FinancePayable>()
                .eq(FinancePayable::getBillNo, fp.getBillNo()));
        if (exist == null) {
            payableMapper.insert(fp);
            return;
        }
        // F7-224（2026-09-29 审核批 B）：复用既有行前必须过**与 reversePayable/deleteBySourceId 相同的两道护栏** ——
        // 本方法会把 paid_amount 重置为 0、把 transferred_to_receivable 复位为 0，若被"已付款/已转应收"的台账
        // 走到，就会抹掉付款进度、并让已转应收的应付**二次可转**（供应商应收悬空/双算）。
        // 说明：现有调用方（采购单/采购退货/采购换货）都传**新生成的** YF- 单号 ⇒ 命中率极低，本护栏属**防御性**补强。
        if (exist.getPaidAmount() != null && exist.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)
            throw new BusinessException("应付单「" + exist.getBillNo() + "」已有付款记录，不可重建来源台账（请先处理付款或反审核付款单）");
        if (Integer.valueOf(1).equals(exist.getTransferredToReceivable()))
            throw new BusinessException("应付单「" + exist.getBillNo() + "」已转应收，请先反审核对应的转应收单");
        payableMapper.update(null, new LambdaUpdateWrapper<FinancePayable>()
                .eq(FinancePayable::getId, exist.getId())
                .set(FinancePayable::getSupplierId, fp.getSupplierId())
                .set(FinancePayable::getSupplierName, fp.getSupplierName())
                .set(FinancePayable::getSupplierType, fp.getSupplierType())
                .set(FinancePayable::getSourceBillType, fp.getSourceBillType())
                .set(FinancePayable::getSourceBillNo, fp.getSourceBillNo())
                .set(FinancePayable::getSourceId, fp.getSourceId())
                .set(FinancePayable::getAmount, fp.getAmount())
                .set(FinancePayable::getPaidAmount, BigDecimal.ZERO)
                .set(FinancePayable::getUnpaidAmount, fp.getAmount())
                .set(FinancePayable::getDueDate, fp.getDueDate())
                .set(FinancePayable::getStatus, SettlementStatus.UNSETTLED.getCode())
                .set(FinancePayable::getTransferredToReceivable, 0)
                .set(FinancePayable::getRemark, fp.getRemark()));
    }

    /**
     * 按来源单据推导往来主体类型：供货商(成品) / 加工厂 / 辅料商 / 方案商。
     * 先按业务场景判定（准确），判定不出时回退取供应商身上的类型标签（兜底）。
     */
    private String resolveSupplierType(String sourceBillType, Supplier s) {
        SourceBillType t = SourceBillType.fromCode(sourceBillType);
        if (t != null) {
            switch (t) {
                case PURCHASE_ORDER, PURCHASE_INBOUND, PURCHASE_RETURN,
                     PURCHASE_EXCHANGE_RETURN, PURCHASE_EXCHANGE_IN -> { return "product"; }
                case OUTSOURCE_DELIVERY, OUTSOURCE_EXCESS_LOSS, OUTSOURCE_RETURN, OUTSOURCE_RETURN_CHARGE,
                     OUTSOURCE_REPAIR_CHARGE -> { return "factory"; }
                case OUTSOURCE_MATERIAL_DELIVERY, OUTSOURCE_MATERIAL_RETURN,
                     OUTSOURCE_MATERIAL_REPAIR_FEE -> { return "material"; }
                default -> { /* 其他场景走下面的兜底 */ }
            }
        }
        // ⚠️ F7-217（2026-09-29 审核批 B）：原兜底读 `s.getTypeCodes()` —— 该字段是 `@TableField(exist = false)`
        // （见 Supplier.java:33-35，`SupplierService` 亦注释"selectById 不会填充"）⇒ 经
        // `supplierMapper.selectById` 加载的实体**该字段恒为空** ⇒ 场景未在 switch 中列出时（实测：
        // PURCHASE_EXCHANGE_CHARGE / PURCHASE_RETURN_CHARGE）返回 null，库中 33 条在用台账因此缺 supplier_type
        // （按类型筛选漏行、汇总类型回填失效）。
        // 现与付款单侧 `FinancePaymentServiceImpl.resolveSupplierType` **同源**：查 supplier_type_ref，
        // 取字典序第一个标签；无标签则返回 null（保持"查不到就是没有"的语义）。
        if (s == null || s.getId() == null) return null;
        List<SupplierTypeRef> refs = typeRefMapper.selectList(new LambdaQueryWrapper<SupplierTypeRef>()
                .eq(SupplierTypeRef::getSupplierId, s.getId())
                .orderByAsc(SupplierTypeRef::getTypeCode));
        return refs.isEmpty() ? null : refs.get(0).getTypeCode();
    }

    /**
     * 按「来源单据类型 + 来源记录ID」定位应付台账。
     * <p><b>必须带 sourceBillType</b>：各业务单据表的 id 都从 1 开始自增，若只按 sourceId 过滤，
     * 反审核/编辑一张单据会把其它单据类型下同 id 的应付一并误冲销（串号）。
     * 例如物料收发单 id=2 与采购单 id=2 会互相误伤。</p>
     */
    private List<FinancePayable> listBySource(Long sourceId, String... sourceBillTypes) {
        if (sourceId == null) return List.of();
        if (sourceBillTypes == null || sourceBillTypes.length == 0) {
            // 防止调用方漏传类型导致退化为「按 id 全局匹配」的历史缺陷
            throw new BusinessException("应付操作缺少来源单据类型（sourceBillType）");
        }
        return payableMapper.selectList(new LambdaQueryWrapper<FinancePayable>()
            .eq(FinancePayable::getSourceId, sourceId)
            .in(FinancePayable::getSourceBillType, List.of(sourceBillTypes)));
    }

    /**
     * 冲回应付（反审核时使用）：将来源记录关联的未付款应付置为已作废，不可恢复。
     * <p>同一张单据可能产生多笔不同 source_bill_type 的应付（如加工退货的「退料冲减」
     * 与「退货收费」共用退货单ID），可按需传入多个类型一并冲销。</p>
     */
    public void reversePayable(Long sourceId, String... sourceBillTypes) {
        if (sourceId == null) return;
        List<FinancePayable> list = listBySource(sourceId, sourceBillTypes);
        for (FinancePayable fp : list) {
            if (fp.getPaidAmount() != null && fp.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)
                throw new BusinessException("应付单「" + fp.getBillNo() + "」已有付款记录，不可反审核");
            // 已转应收的冲减项挂着对应的应收台账，作废会造成应收悬空（对方债务凭空消失），必须先反审核转应收单
            if (Integer.valueOf(1).equals(fp.getTransferredToReceivable()))
                throw new BusinessException("应付单「" + fp.getBillNo() + "」已转应收，请先反审核对应的转应收单");
            cancelLedger(fp);
        }
    }

    /**
     * 作废台账（I29 口径，2026-09-18）：**已作废的应付不计金额**。
     * <p>把状态置 CANCELLED 的同时把 {@code amount} **一并清零**，使「amount = paid + unpaid」在**所有行**上恒成立
     * （作废行 paid/unpaid 均为 0）。旧实现只清零 {@code unpaid_amount}，作废行仍保留原金额（如 -24），
     * 导致以行为单位的对账/稽核把「作废留痕行」误判为异常（I29）。</p>
     * <p>原金额写入 {@code remark} 留痕（`[已作废] 原金额=-24`），避免"金额凭空消失"无法追溯；
     * 各统计/汇总口径均按状态排除 CANCELLED，故清零不影响账务结果。</p>
     */
    public void cancelLedger(FinancePayable fp) {
        if (fp == null) return;
        BigDecimal old = fp.getAmount();
        fp.setStatus(SettlementStatus.CANCELLED.getCode());
        fp.setUnpaidAmount(BigDecimal.ZERO);
        if (old != null && old.compareTo(BigDecimal.ZERO) != 0) {
            String mark = "[已作废] 原金额=" + old.stripTrailingZeros().toPlainString();
            String r = fp.getRemark();
            fp.setRemark(r == null || r.isBlank() ? mark : r + " " + mark);
        }
        fp.setAmount(BigDecimal.ZERO);
        payableMapper.updateById(fp);
    }

    /** 按来源记录删除应付（已付款核销的阻止）；类型维度同 {@link #reversePayable(Long, String...)} */
    public void deleteBySourceId(Long sourceId, String... sourceBillTypes) {
        if (sourceId == null) return;
        List<FinancePayable> list = listBySource(sourceId, sourceBillTypes);
        for (FinancePayable fp : list) {
            if (fp.getPaidAmount() != null && fp.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)
                throw new BusinessException("应付单「" + fp.getBillNo() + "」已有付款记录，不可修改来源单据");
            // 同上：已转应收的冲减项不能直接删，否则应收台账失去来源依据
            if (Integer.valueOf(1).equals(fp.getTransferredToReceivable()))
                throw new BusinessException("应付单「" + fp.getBillNo() + "」已转应收，请先反审核对应的转应收单");
            payableMapper.deleteById(fp.getId());
        }
    }

    /** 按来源记录重建应付：先删旧的（未付款），再生成新的 */
    public FinancePayable replaceBySourceId(Long supplierId, String sourceBillType, String sourceBillNo,
                                            Long sourceId, BigDecimal amount, LocalDate bizDate, String remark) {
        deleteBySourceId(sourceId, sourceBillType);
        return createPayable(supplierId, sourceBillType, sourceBillNo, sourceId, amount, bizDate, remark);
    }

    /** 到期日 = 业务日期 + 供应商账期（月+天），未设置或0月0天均为当天 */
    public LocalDate calcDueDate(Supplier s, LocalDate bizDate) {
        if (bizDate == null) bizDate = LocalDate.now();
        int months = 0, days = 0;
        if (s != null) {
            if (s.getCreditPeriodMonths() != null) months = s.getCreditPeriodMonths();
            if (s.getCreditPeriod() != null) days = s.getCreditPeriod();
        }
        return bizDate.plusMonths(months).plusDays(days);
    }

    /**
     * 生成一个新的应付台账单号（`YF-` + 日期 + 3 位流水）。
     * <p>D1 口径（2026-09-12）：应付台账单号**一律** `YF-`，与委外类应付同体系；
     * 采购单/采购退货等来源单号写入 `source_bill_no` 便于按来源单检索。
     * 采购侧原先直接拿单据号当台账号（`CG-`/`TH-`），因 uk_bill_no 唯一而被迫用 {@link #saveByBillNo}
     * 复用行；改用本方法后每次审核都是新号 → 反审核把旧行置 CANCELLED 留痕、重审新建一行，与委外一致。</p>
     */
    public String newBillNo() {
        return generateBillNo();
    }

    /**
     * 预付台账单号（2026-09-29；与 {@link ReceivableHelper#advanceBillNo} 逐字对称）。
     *
     * <p>用于「付款未核销差额 / 核销开关关闭」生成的负数应付（预付：我方多付，供应商欠我方）。
     * 取「付款单号 + -ADVANCE」而不是每次新取 YF- 流水号，是为了让<b>反审核后重新审核复用同一行</b>
     * （bill_no 唯一键）—— 否则每审一次就多留一条 CANCELLED 的预付行。</p>
     *
     * <p>两个护栏（I27 修复口径）：① <b>幂等归一化</b> —— 先把入参末尾所有 {@code -ADVANCE} 去掉再追加一次，
     * 避免 {@code X-ADVANCE-ADVANCE-…} 越叠越长撑破 varchar(50)；② <b>超长护栏</b> —— 归一化后仍超列宽时
     * 退化为稳定短号 {@code ADV-<hash>}（同一基础单号恒得同一值，重新审核仍复用同一行）。</p>
     */
    public static String advanceBillNo(String billNo) {
        String base = billNo == null ? "" : billNo.trim();
        String suffix = "-" + SettlementStatus.ADVANCE.getCode();
        while (base.endsWith(suffix)) base = base.substring(0, base.length() - suffix.length());
        if (base.isEmpty()) base = "AP";
        String candidate = base + suffix;
        if (candidate.length() > BILL_NO_MAX) {
            candidate = "ADV-" + Integer.toHexString(base.hashCode() & 0x7fffffff).toUpperCase();
        }
        return candidate;
    }

    private String generateBillNo() {
        String ds = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        FinancePayable last = payableMapper.selectOne(new LambdaQueryWrapper<FinancePayable>()
            .likeRight(FinancePayable::getBillNo, BillPrefix.PAYABLE + ds).orderByDesc(FinancePayable::getBillNo).last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getBillNo() != null) {
            try { seq = Integer.parseInt(last.getBillNo().substring(last.getBillNo().length() - 3)) + 1; } catch (Exception ignored) {}
        }
        // F7-39#3（2026-09-19）：冲突检测 + 递增重试（原 catch 为空 ⇒ 解析失败静默从 001 重来，可能重号）
        for (int i = 0; i < 999; i++) {
            String no = BillPrefix.PAYABLE + ds + String.format("%03d", seq);
            if (payableMapper.selectCount(new LambdaQueryWrapper<FinancePayable>().eq(FinancePayable::getBillNo, no)) == 0) return no;
            seq++;
        }
        throw new BusinessException("当日应付单编号已用尽（前缀 " + BillPrefix.PAYABLE + ds + "），请联系管理员");
    }
}
