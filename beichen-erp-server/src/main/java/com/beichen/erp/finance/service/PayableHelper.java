package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.exception.BusinessException;
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
        fp.setStatus("未结清");
        fp.setRemark(remark);
        payableMapper.insert(fp);
        return fp;
    }

    /** 按来源记录删除应付（已付款核销的阻止） */
    public void deleteBySourceId(Long sourceId) {
        if (sourceId == null) return;
        List<FinancePayable> list = payableMapper.selectList(
            new LambdaQueryWrapper<FinancePayable>().eq(FinancePayable::getSourceId, sourceId));
        for (FinancePayable fp : list) {
            if (fp.getPaidAmount() != null && fp.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)
                throw new BusinessException("应付单「" + fp.getBillNo() + "」已有付款记录，不可修改来源单据");
            payableMapper.deleteById(fp.getId());
        }
    }

    /** 按来源记录重建应付：先删旧的（未付款），再生成新的 */
    public FinancePayable replaceBySourceId(Long supplierId, String sourceBillType, String sourceBillNo,
                                            Long sourceId, BigDecimal amount, LocalDate bizDate, String remark) {
        deleteBySourceId(sourceId);
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

    private String generateBillNo() {
        String ds = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        FinancePayable last = payableMapper.selectOne(new LambdaQueryWrapper<FinancePayable>()
            .likeRight(FinancePayable::getBillNo, "YF-" + ds).orderByDesc(FinancePayable::getBillNo).last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getBillNo() != null) {
            try { seq = Integer.parseInt(last.getBillNo().substring(last.getBillNo().length() - 3)) + 1; } catch (Exception ignored) {}
        }
        return "YF-" + ds + String.format("%03d", seq);
    }
}
