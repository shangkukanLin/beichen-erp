package com.beichen.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.entity.FinanceInvoice;
import com.beichen.erp.finance.mapper.FinanceInvoiceMapper;
import com.beichen.erp.finance.service.FinanceInvoiceService;
import com.beichen.erp.config.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 发票登记 Service（税务口径）：
 * - 登记即生效（REGISTERED），作废仅标记不删除
 * - 发票号码在未作废范围内唯一（同公司）
 * - 金额联动兜底：传价税合计按「价税合计×税率/(100+税率)」拆税（与收税开关同口径）；
 *   传不含税金额则价税合计 = 不含税 × (100+税率)/100
 */
@Service
@RequiredArgsConstructor
public class FinanceInvoiceServiceImpl implements FinanceInvoiceService {

    private static final String STATUS_REGISTERED = "REGISTERED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final FinanceInvoiceMapper invoiceMapper;

    @Override
    public Page<FinanceInvoice> page(String direction, String status, String keyword,
                                     LocalDate start, LocalDate end, int pageNum, int pageSize) {
        LambdaQueryWrapper<FinanceInvoice> w = new LambdaQueryWrapper<FinanceInvoice>()
                .eq(direction != null && !direction.isBlank(), FinanceInvoice::getDirection, direction)
                .eq(status != null && !status.isBlank(), FinanceInvoice::getStatus, status)
                .ge(start != null, FinanceInvoice::getInvoiceDate, start)
                .le(end != null, FinanceInvoice::getInvoiceDate, end)
                .and(keyword != null && !keyword.isBlank(), q -> q
                        .like(FinanceInvoice::getInvoiceNo, keyword)
                        .or().like(FinanceInvoice::getPartnerName, keyword)
                        .or().like(FinanceInvoice::getSourceBillCode, keyword))
                .orderByDesc(FinanceInvoice::getInvoiceDate)
                .orderByDesc(FinanceInvoice::getId);
        return invoiceMapper.selectPage(new Page<>(pageNum, pageSize), w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(FinanceInvoice invoice) {
        validate(invoice);
        calcAmount(invoice);
        assertNoDuplicate(invoice.getInvoiceNo(), null);
        invoice.setStatus(STATUS_REGISTERED);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) invoice.setCompanyId(cid);
        invoiceMapper.insert(invoice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FinanceInvoice invoice) {
        FinanceInvoice old = invoiceMapper.selectById(invoice.getId());
        if (old == null) throw new BusinessException("发票不存在");
        if (STATUS_CANCELLED.equals(old.getStatus())) throw new BusinessException("已作废的发票不可修改");
        validate(invoice);
        calcAmount(invoice);
        assertNoDuplicate(invoice.getInvoiceNo(), old.getId());
        invoice.setStatus(old.getStatus());
        invoice.setCompanyId(old.getCompanyId());
        invoiceMapper.updateById(invoice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        FinanceInvoice old = invoiceMapper.selectById(id);
        if (old == null) throw new BusinessException("发票不存在");
        if (STATUS_CANCELLED.equals(old.getStatus())) throw new BusinessException("该发票已作废");
        FinanceInvoice u = new FinanceInvoice();
        u.setId(id);
        u.setStatus(STATUS_CANCELLED);
        invoiceMapper.updateById(u);
    }

    // ==================== 私有辅助 ====================

    private void validate(FinanceInvoice invoice) {
        if (invoice.getInvoiceNo() == null || invoice.getInvoiceNo().isBlank()) throw new BusinessException("发票号码不能为空");
        if (!"SALE".equals(invoice.getDirection()) && !"PURCHASE".equals(invoice.getDirection()))
            throw new BusinessException("发票方向无效（SALE=销项 / PURCHASE=进项）");
        if (invoice.getInvoiceDate() == null) invoice.setInvoiceDate(LocalDate.now());
        if (invoice.getTaxRate() == null) invoice.setTaxRate(BigDecimal.ZERO);
        if (invoice.getTotalAmount() == null && invoice.getAmount() == null)
            throw new BusinessException("价税合计与不含税金额至少填一项");
        if ((invoice.getAmount() != null && invoice.getAmount().compareTo(BigDecimal.ZERO) < 0)
                || (invoice.getTotalAmount() != null && invoice.getTotalAmount().compareTo(BigDecimal.ZERO) < 0))
            throw new BusinessException("金额不能为负数");
    }

    /** 金额联动兜底：优先按价税合计拆税（与收税开关口径一致），否则按不含税金额正算 */
    private void calcAmount(FinanceInvoice invoice) {
        BigDecimal rate = invoice.getTaxRate() != null ? invoice.getTaxRate() : BigDecimal.ZERO;
        BigDecimal hundred = new BigDecimal("100");
        if (invoice.getTotalAmount() != null && invoice.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal tax = invoice.getTotalAmount().multiply(rate).divide(hundred.add(rate), 2, RoundingMode.HALF_UP);
            invoice.setTaxAmount(tax);
            invoice.setAmount(invoice.getTotalAmount().subtract(tax).setScale(2, RoundingMode.HALF_UP));
        } else if (invoice.getAmount() != null) {
            BigDecimal total = invoice.getAmount().multiply(hundred.add(rate)).divide(hundred, 2, RoundingMode.HALF_UP);
            invoice.setTotalAmount(total);
            invoice.setTaxAmount(total.subtract(invoice.getAmount()).setScale(2, RoundingMode.HALF_UP));
        } else {
            invoice.setAmount(BigDecimal.ZERO);
            invoice.setTotalAmount(BigDecimal.ZERO);
            invoice.setTaxAmount(BigDecimal.ZERO);
        }
    }

    /** 发票号码唯一：同公司、未作废范围内（作废后号码可重新登记） */
    private void assertNoDuplicate(String invoiceNo, Long excludeId) {
        Long cid = CompanyContext.get();
        LambdaQueryWrapper<FinanceInvoice> w = new LambdaQueryWrapper<FinanceInvoice>()
                .eq(FinanceInvoice::getInvoiceNo, invoiceNo)
                .ne(FinanceInvoice::getStatus, STATUS_CANCELLED)
                .ne(excludeId != null, FinanceInvoice::getId, excludeId);
        if (cid != null && cid > 0) w.eq(FinanceInvoice::getCompanyId, cid);
        List<FinanceInvoice> dup = invoiceMapper.selectList(w);
        if (!dup.isEmpty()) throw new BusinessException("发票号码已存在：" + invoiceNo);
    }
}
