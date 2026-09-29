package com.beichen.erp.finance.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.common.SubjectType;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.entity.PayableTransfer;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import com.beichen.erp.finance.mapper.PayableTransferMapper;
import com.beichen.erp.finance.service.PayableTransferService;
import com.beichen.erp.finance.service.ReceivableHelper;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayableTransferServiceImpl implements PayableTransferService {

    private final PayableTransferMapper transferMapper;
    private final FinancePayableMapper payableMapper;
    private final FinanceReceivableMapper receivableMapper;
    private final SupplierMapper supplierMapper;
    private final UserMapper userMapper;
    private final ReceivableHelper receivableHelper;

    @Override
    public Page<Map<String, Object>> page(String code, Long supplierId, String supplierType, String status,
                                          String startDate, String endDate, int pageNum, int pageSize) {
        LambdaQueryWrapper<PayableTransfer> w = new LambdaQueryWrapper<PayableTransfer>()
                .like(code != null && !code.isBlank(), PayableTransfer::getCode, code)
                .eq(supplierId != null, PayableTransfer::getSupplierId, supplierId)
                .eq(supplierType != null && !supplierType.isBlank(), PayableTransfer::getSupplierType, supplierType)
                .eq(status != null && !status.isBlank(), PayableTransfer::getStatus, status)
                .ge(startDate != null && !startDate.isBlank(), PayableTransfer::getTransferDate, startDate)
                .le(endDate != null && !endDate.isBlank(), PayableTransfer::getTransferDate, endDate)
                .orderByDesc(PayableTransfer::getId);
        Page<PayableTransfer> raw = transferMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId()); m.put("code", r.getCode());
            m.put("payableId", r.getPayableId()); m.put("payableBillNo", r.getPayableBillNo());
            m.put("supplierId", r.getSupplierId()); m.put("supplierName", r.getSupplierName());
            m.put("supplierType", r.getSupplierType());
            m.put("amount", r.getAmount()); m.put("transferDate", r.getTransferDate());
            m.put("status", r.getStatus()); m.put("remark", r.getRemark());
            m.put("auditorName", r.getAuditorName()); m.put("auditTime", r.getAuditTime());
            m.put("createTime", r.getCreateTime());
            return m;
        }).toList());
        return res;
    }

    @Override
    public PayableTransfer getById(Long id) { return transferMapper.selectById(id); }

    @Override
    public List<Map<String, Object>> transferablePayables(Long supplierId, String keyword, Long payableId) {
        // F7-222（2026-09-29 审核批 B）：**必须指定主体**（supplierId 或 payableId）才返回 ——
        // 原先无 supplierId 时该条件被跳过 ⇒ 直调本端点可拿到**全公司**可转应付（只读面过宽）。
        // payableId 分支服务于"应付列表点「转应收」带 ?payableId="与编辑态回填：只回那一条，读面同样收窄。
        // 判定口径与 PayableQuery.isTransferable 一致（此处为同一谓词的 SQL 版）。
        if (supplierId == null && payableId == null) return List.of();
        LambdaQueryWrapper<FinancePayable> w = new LambdaQueryWrapper<FinancePayable>()
                // 只有负数（退货/扣款冲减项）才需要转应收；正数应付是货款，本来就要付给对方
                .lt(FinancePayable::getAmount, BigDecimal.ZERO)
                .eq(FinancePayable::getTransferredToReceivable, 0)
                .eq(FinancePayable::getStatus, SettlementStatus.UNSETTLED.getCode())
                .eq(payableId != null, FinancePayable::getId, payableId)
                .eq(supplierId != null, FinancePayable::getSupplierId, supplierId)
                .like(keyword != null && !keyword.isBlank(), FinancePayable::getBillNo, keyword)
                .orderByDesc(FinancePayable::getId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (FinancePayable p : payableMapper.selectList(w)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("billNo", p.getBillNo());
            m.put("supplierId", p.getSupplierId());
            m.put("supplierName", p.getSupplierName());
            m.put("supplierType", p.getSupplierType());
            m.put("sourceBillType", p.getSourceBillType());
            m.put("sourceBillNo", p.getSourceBillNo());
            m.put("amount", p.getAmount());
            // 可转金额 = 应付金额的绝对值
            m.put("transferableAmount", p.getAmount() != null ? p.getAmount().abs() : BigDecimal.ZERO);
            m.put("dueDate", p.getDueDate());
            list.add(m);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(PayableTransfer transfer) {
        FinancePayable payable = requireTransferable(transfer.getPayableId());
        transfer.setId(null);
        transfer.setCode(gen());
        transfer.setStatus(DocStatus.DRAFT.getCode());
        // 主体信息全部从来源应付带出，避免前端传错；金额强制取绝对值（当前只支持整笔转出）
        transfer.setSupplierId(payable.getSupplierId());
        transfer.setSupplierName(payable.getSupplierName());
        transfer.setSupplierType(payable.getSupplierType());
        transfer.setPayableBillNo(payable.getBillNo());
        transfer.setAmount(payable.getAmount() != null ? payable.getAmount().abs() : BigDecimal.ZERO);
        if (transfer.getTransferDate() == null) transfer.setTransferDate(LocalDate.now());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) transfer.setCompanyId(cid);
        transferMapper.insert(transfer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(PayableTransfer transfer) {
        PayableTransfer old = transferMapper.selectById(transfer.getId());
        if (old == null) throw new BusinessException("转应收单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        // 来源应付不允许改（换一笔等于另一笔业务，应先作废重建），仅日期/备注可改
        PayableTransfer u = new PayableTransfer();
        u.setId(transfer.getId());
        u.setTransferDate(transfer.getTransferDate() != null ? transfer.getTransferDate() : old.getTransferDate());
        u.setRemark(transfer.getRemark());
        transferMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        PayableTransfer t = transferMapper.selectById(id);
        if (t == null) throw new BusinessException("转应收单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免重复生成供应商应收
        if (!DocStatusGuard.claim(transferMapper, PayableTransfer::getId, id, PayableTransfer::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("仅草稿状态可审核");
        // 审核时重新校验来源应付：下单后可能已被付款核销或转出
        FinancePayable payable = requireTransferable(t.getPayableId());

        // 1) 生成供应商应收（单号用转应收单号，保证唯一，反审核按单号精确冲销）
        //    台账按 bill_no 唯一：反审核是置 CANCELLED（记录保留），再审核必须复用同条记录重置，
        //    直接 insert 会撞 uk_bill_no；重置须用显式 UpdateWrapper（updateById 会静默漏更新）
        BigDecimal amount = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
        FinanceReceivable exist = receivableMapper.selectOne(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getBillNo, t.getCode()));
        if (exist != null) {
            if (exist.getPaidAmount() != null && exist.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)
                throw new BusinessException("该转应收单已有收款记录，不可重新审核");
            receivableMapper.update(null, new LambdaUpdateWrapper<FinanceReceivable>()
                    .eq(FinanceReceivable::getId, exist.getId())
                    .set(FinanceReceivable::getSubjectType, SubjectType.SUPPLIER.getCode())
                    .set(FinanceReceivable::getSupplierId, t.getSupplierId())
                    .set(FinanceReceivable::getSupplierName, t.getSupplierName())
                    .set(FinanceReceivable::getSourceBillType, SourceBillType.PAYABLE_TRANSFER.getCode())
                    .set(FinanceReceivable::getSourceBillNo, t.getCode())
                    .set(FinanceReceivable::getSourceId, t.getId())
                    .set(FinanceReceivable::getAmount, amount)
                    .set(FinanceReceivable::getPaidAmount, BigDecimal.ZERO)
                    .set(FinanceReceivable::getUnpaidAmount, amount)
                    .set(FinanceReceivable::getDueDate, t.getTransferDate())
                    .set(FinanceReceivable::getStatus, SettlementStatus.UNSETTLED.getCode())
                    .set(FinanceReceivable::getRemark, t.getRemark()));
        } else {
            FinanceReceivable fr = new FinanceReceivable();
            fr.setBillNo(t.getCode());
            fr.setSubjectType(SubjectType.SUPPLIER.getCode());
            fr.setSupplierId(t.getSupplierId());
            fr.setSupplierName(t.getSupplierName());
            fr.setSourceBillType(SourceBillType.PAYABLE_TRANSFER.getCode());
            fr.setSourceBillNo(t.getCode());
            fr.setSourceId(t.getId());
            fr.setAmount(amount);
            fr.setPaidAmount(BigDecimal.ZERO);
            fr.setUnpaidAmount(amount);
            fr.setDueDate(t.getTransferDate());
            fr.setStatus(SettlementStatus.UNSETTLED.getCode());
            fr.setRemark(t.getRemark());
            Long cid = CompanyContext.get();
            if (cid != null && cid > 0) fr.setCompanyId(cid);
            receivableMapper.insert(fr);
        }

        // 2) 来源应付标记已转：付款抵扣时跳过，避免同一笔钱既抵扣又向对方收款
        //    条件更新（P2-29）：只有"尚未转出"才置 1，防止两张转应收单指向同一笔应付时重复生成供应商应收
        int rows = payableMapper.update(null, new LambdaUpdateWrapper<FinancePayable>()
                .eq(FinancePayable::getId, payable.getId())
                .apply("IFNULL(transferred_to_receivable, 0) = 0")
                .set(FinancePayable::getTransferredToReceivable, 1));
        if (rows == 0) throw new BusinessException("该应付已转应收，请勿重复操作");

        PayableTransfer u = new PayableTransfer();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditorId(getCurrentUserId());
        u.setAuditorName(getCurrentUserName());
        u.setAuditTime(LocalDateTime.now());
        transferMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        PayableTransfer t = transferMapper.selectById(id);
        if (t == null) throw new BusinessException("转应收单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免重复冲销供应商应收
        if (!DocStatusGuard.claim(transferMapper, PayableTransfer::getId, id, PayableTransfer::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("仅已审核状态可反审核");
        // 已收款会在此拦截（资金安全护栏）
        receivableHelper.reverseReceivable(t.getCode());

        if (t.getPayableId() != null) {
            FinancePayable up = new FinancePayable();
            up.setId(t.getPayableId());
            up.setTransferredToReceivable(0);
            payableMapper.updateById(up);
        }
        // 审核信息必须用 UpdateWrapper 显式置 null：updateById 忽略 null 字段，反审核后仍显示审核人/时间
        transferMapper.update(null, new LambdaUpdateWrapper<PayableTransfer>()
                .eq(PayableTransfer::getId, id)
                .set(PayableTransfer::getStatus, DocStatus.DRAFT.getCode())
                .set(PayableTransfer::getAuditorId, null)
                .set(PayableTransfer::getAuditorName, null)
                .set(PayableTransfer::getAuditTime, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        PayableTransfer old = transferMapper.selectById(id);
        if (old == null) throw new BusinessException("转应收单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败
        if (!DocStatusGuard.claim(transferMapper, PayableTransfer::getId, id, PayableTransfer::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("仅草稿状态可作废");
        PayableTransfer u = new PayableTransfer();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        transferMapper.updateById(u);
    }

    // ==================== 私有辅助 ====================

    /**
     * 校验来源应付可转应收：存在、金额为负（冲减项）、未转出、未结清。
     * 创建与审核两处都要过一遍——下单到审核之间，该笔可能已被付款核销。
     */
    private FinancePayable requireTransferable(Long payableId) {
        if (payableId == null) throw new BusinessException("请选择要转应收的应付记录");
        FinancePayable p = payableMapper.selectById(payableId);
        if (p == null) throw new BusinessException("应付记录不存在");
        if (p.getAmount() == null || p.getAmount().compareTo(BigDecimal.ZERO) >= 0)
            throw new BusinessException("仅负数应付（退货/扣款冲减项）可转应收，该笔金额为 " + p.getAmount());
        if (Integer.valueOf(1).equals(p.getTransferredToReceivable()))
            throw new BusinessException("该笔应付已转应收，不可重复转出");
        if (!SettlementStatus.UNSETTLED.getCode().equals(p.getStatus()))
            throw new BusinessException("该笔应付已核销或已作废，不可转应收（当前状态：" + p.getStatus() + "）");
        return p;
    }

    /** 单号：PZ-yyyyMMdd-NNN */
    private String gen() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.PAYABLE_TRANSFER + d;
        PayableTransfer last = transferMapper.selectOne(new LambdaQueryWrapper<PayableTransfer>()
                .likeRight(PayableTransfer::getCode, pat).orderByDesc(PayableTransfer::getCode).last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception ignored) { seq = 1; }
        }
        // F7-39#3（2026-09-19）：冲突检测 + 递增重试（原解析失败静默兜底 seq=1 ⇒ 可能重号）
        for (int i = 0; i < 999; i++) {
            String code = pat + String.format("%03d", seq);
            if (transferMapper.selectCount(new LambdaQueryWrapper<PayableTransfer>().eq(PayableTransfer::getCode, code)) == 0) return code;
            seq++;
        }
        throw new BusinessException("当日转应收单编号已用尽（前缀 " + pat + "），请联系管理员");
    }

    private Long getCurrentUserId() {
        try { return StpUtil.getLoginIdAsLong(); } catch (Exception e) { return null; }
    }

    private String getCurrentUserName() {
        try {
            User user = userMapper.selectById(StpUtil.getLoginIdAsLong());
            return user != null ? user.getUsername() : null;
        } catch (Exception e) { return null; }
    }
}
