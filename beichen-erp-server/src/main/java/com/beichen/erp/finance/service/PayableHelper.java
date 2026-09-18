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
                case OUTSOURCE_MATERIAL_DELIVERY, OUTSOURCE_MATERIAL_RETURN -> { return "material"; }
                default -> { /* 其他场景走下面的兜底 */ }
            }
        }
        if (s != null && s.getTypeCodes() != null && !s.getTypeCodes().isEmpty()) return s.getTypeCodes().get(0);
        return null;
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

    private String generateBillNo() {
        String ds = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        FinancePayable last = payableMapper.selectOne(new LambdaQueryWrapper<FinancePayable>()
            .likeRight(FinancePayable::getBillNo, BillPrefix.PAYABLE + ds).orderByDesc(FinancePayable::getBillNo).last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getBillNo() != null) {
            try { seq = Integer.parseInt(last.getBillNo().substring(last.getBillNo().length() - 3)) + 1; } catch (Exception ignored) {}
        }
        return BillPrefix.PAYABLE + ds + String.format("%03d", seq);
    }
}
