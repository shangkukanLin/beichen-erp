package com.beichen.erp.finance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.entity.FinancePayment;
import com.beichen.erp.finance.entity.FinancePaymentAccount;
import com.beichen.erp.finance.entity.FinancePaymentItem;
import com.beichen.erp.finance.service.FinancePaymentService;
import com.beichen.erp.finance.service.PayableQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/payment")
@RequiredArgsConstructor
public class FinancePaymentController {

    private final FinancePaymentService service;
    // 期 2（2026-09-19 读隔离）：付款页要用的应付数据由本页接口提供（与应付页共用 PayableQuery）
    private final PayableQuery payableQuery;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplierType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(supplierId, supplierType, status, pageNum, pageSize));
    }

    /**
     * 应付汇总（付款页 Tab1「供应商汇总」）。
     * <p>期 2（2026-09-19 读隔离）：原先付款页直读 {@code /api/finance/payable/supplier-summary}
     * （需 {@code finance:payable}）⇒ 只被授予 {@code finance:payment} 的用户会 403。现由本页接口提供。</p>
     */
    @GetMapping("/payable-summary")
    public R<List<Map<String, Object>>> payableSummary() {
        return R.ok(payableQuery.supplierSummary());
    }

    /** 应付明细（供应商付款页）：原读 {@code /api/finance/payable/page} */
    @GetMapping("/payables")
    public R<Page<Map<String, Object>>> payables(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplierType,
            @RequestParam(required = false) String sourceBillType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String billNo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(payableQuery.page(supplierId, supplierType, sourceBillType, status, billNo, pageNum, pageSize));
    }

    /** 未结清应付（新增付款弹窗的核销下拉）：原读 {@code /api/finance/payable/unpaid} */
    @GetMapping("/unpaid-payables")
    public R<List<FinancePayable>> unpaidPayables(@RequestParam Long supplierId) {
        return R.ok(payableQuery.unpaid(supplierId));
    }

    /**
     * 单供应商欠款汇总（2026-09-29 用户口径）：新增付款页「选完供应商后显示**到期欠款 + 总欠款**」。
     * <p>口径（严格镜像应付侧 {@code PayableQuery.supplierSummary}）：未结清 + 未付额&gt;0
     * ⇒ 天然排除预付台账（ADVANCE，负数）；到期 = {@code due_date < 今天}（当天不算）；
     * 无到期日的单据不计入（另返回数量与金额供界面解释）。走付款页自身前缀 ⇒ 只要求 {@code finance:payment}。</p>
     */
    @GetMapping("/party-summary")
    public R<Map<String, Object>> partySummary(@RequestParam(required = false) Long supplierId,
                                               @RequestParam(required = false) String supplierType) {
        return R.ok(payableQuery.partySummary(supplierId, supplierType));
    }

    @GetMapping("/{id}")
    public R<FinancePayment> getById(@PathVariable Long id) { return R.ok(service.getById(id)); }

    @GetMapping("/{id}/items")
    public R<List<FinancePaymentItem>> getItems(@PathVariable Long id) { return R.ok(service.getItems(id)); }

    /** 分款明细（2026-09-29 多账户付款）：详情页「付款账户」卡片按行展示 */
    @GetMapping("/{id}/accounts")
    public R<List<FinancePaymentAccount>> getAccounts(@PathVariable Long id) { return R.ok(service.getAccounts(id)); }

    /**
     * 建单：{@code body = {payment:{…}, accounts:[{accountId,amount,remark}], items:[{payableId,…}]}}。
     * <p>2026-09-29：{@code accounts} 支撑**多账户分款**（A 50 + B 100）；{@code items} **可为空**
     * （核销开关关闭 = 只记付款，未核销差额审核时落预付台账）。
     * 老 payload（只带 {@code payment.accountId} + items）仍可用 —— 后端会归一化成一条分款行。</p>
     */
    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parsePayment(body), parseAccounts(body), parseItems(body));
        return R.ok();
    }

    /**
     * 草稿就地修改（2026-09-29 用户口径：付款侧与收款侧对称，家规「草稿在详情页改+存、列表不给编辑」）：
     * 字段/校验与建单完全一致，分款与核销明细整体替换；只允许 DRAFT（已审核要先反审核）。
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.update(id, parsePayment(body), parseAccounts(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) { service.audit(id); return R.ok(); }

    @PutMapping("/{id}/un-audit")
    public R<Void> unAudit(@PathVariable Long id) { service.unAudit(id); return R.ok(); }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) { service.cancel(id); return R.ok(); }

    /** 更新付款凭证 */
    @PutMapping("/{id}/attach")
    public R<Void> updateAttach(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        FinancePayment u = new FinancePayment();
        u.setId(id);
        u.setAttachUrl(body.get("attachUrl") != null ? body.get("attachUrl").toString() : "");
        service.updateAttach(u);
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    private FinancePayment parsePayment(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("payment") ? (Map<String, Object>) body.get("payment") : body;
        FinancePayment r = new FinancePayment();
        if (d.get("supplierId") != null) r.setSupplierId(Long.valueOf(d.get("supplierId").toString()));
        if (d.get("accountId") != null) r.setAccountId(Long.valueOf(d.get("accountId").toString()));
        if (d.get("paymentDate") != null && !d.get("paymentDate").toString().isBlank())
            r.setPaymentDate(LocalDate.parse(d.get("paymentDate").toString()));
        r.setRemark((String) d.get("remark"));
        r.setAttachUrl((String) d.get("attachUrl"));
        return r;
    }

    /**
     * 分款明细（2026-09-29 多账户）：{@code accounts:[{accountId, amount, remark}]}。
     * <p>为空 = 交给后端按单账户归一化（老页面旧 payload）。</p>
     */
    @SuppressWarnings("unchecked")
    private List<FinancePaymentAccount> parseAccounts(Map<String, Object> body) {
        List<FinancePaymentAccount> list = new ArrayList<>();
        Object obj = body.get("accounts");
        if (obj instanceof List<?> raw) for (Object o : raw) if (o instanceof Map<?, ?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            FinancePaymentAccount a = new FinancePaymentAccount();
            if (map.get("accountId") != null && !map.get("accountId").toString().isBlank())
                a.setAccountId(Long.valueOf(map.get("accountId").toString()));
            if (map.get("amount") != null && !map.get("amount").toString().isBlank())
                a.setAmount(new BigDecimal(map.get("amount").toString()));
            a.setRemark((String) map.get("remark"));
            list.add(a);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private List<FinancePaymentItem> parseItems(Map<String, Object> body) {
        List<FinancePaymentItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) for (Object o : raw) if (o instanceof Map<?,?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            FinancePaymentItem it = new FinancePaymentItem();
            if (map.get("payableId") != null) it.setPayableId(Long.valueOf(map.get("payableId").toString()));
            it.setPayableBillNo((String) map.get("payableBillNo"));
            if (map.get("thisAmount") != null && !map.get("thisAmount").toString().isBlank())
                it.setThisAmount(new BigDecimal(map.get("thisAmount").toString()));
            it.setRemark((String) map.get("remark"));
            list.add(it);
        }
        return list;
    }
}
