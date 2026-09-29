package com.beichen.erp.finance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.entity.FinanceReceipt;
import com.beichen.erp.finance.entity.FinanceReceiptAccount;
import com.beichen.erp.finance.entity.FinanceReceiptItem;
import com.beichen.erp.finance.service.FinanceReceiptService;
import com.beichen.erp.finance.service.ReceivableQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/receipt")
@RequiredArgsConstructor
public class FinanceReceiptController {

    private final FinanceReceiptService service;
    // 期 2（2026-09-19 读隔离）：收款页核销下拉要用的未结清应收由本页接口提供（与应收页共用 ReceivableQuery）
    private final ReceivableQuery receivableQuery;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(customerId, supplierId, subjectType, status, pageNum, pageSize));
    }

    /**
     * 按来源单据查收款单（2026-09-18）：销售单**现金结算**审核时自动生成的草稿收款单，
     * 销售单详情页据此显示「收款单 SK-…（草稿/已审核）」并可点击跳转。
     */
    @GetMapping("/by-source")
    public R<List<FinanceReceipt>> bySource(@RequestParam String sourceBillType, @RequestParam Long sourceId) {
        return R.ok(service.findBySource(sourceBillType, sourceId));
    }

    /**
     * 未结清应收（收款单「核销明细」下拉：客户收款按 customerId，供应商收款按 supplierId）。
     * <p>期 2（2026-09-19 读隔离）：原先前端直读 {@code /api/finance/receivable/unpaid}
     * （需 {@code finance:receivable}）⇒ 只被授予 {@code finance:receipt} 的用户会 403
     * —— 这是接口级权限校准工具修好后新查出的真实缺口。现由本页接口提供，口径复用应收页同一查询。</p>
     */
    @GetMapping("/unpaid-receivables")
    public R<List<FinanceReceivable>> unpaidReceivables(@RequestParam(required = false) Long customerId,
                                                        @RequestParam(required = false) Long supplierId,
                                                        @RequestParam(required = false) String subjectType) {
        return R.ok(receivableQuery.unpaid(customerId, supplierId, subjectType));
    }

    /**
     * 主体欠款汇总（2026-09-29 用户口径）：新增收款页/详情页草稿态「选完客户/供应商后显示**到期欠款 + 总欠款**」。
     * <p>口径（严格镜像应付侧的 {@code PayableQuery.supplierSummary}）：未结清 + 未收额&gt;0；
     * 到期 = {@code due_date < 今天}（当天不算）；无到期日的单据不计入到期（另返回数量与金额供界面解释）。</p>
     * <p>与 {@code /unpaid-receivables} 同一原因挂**本页前缀**：只要求 {@code finance:receipt}
     * （挂 {@code /finance/receivable/*} 会要求 {@code finance:receivable}，只有收款权限的用户会 403）。</p>
     */
    @GetMapping("/party-summary")
    public R<Map<String, Object>> partySummary(@RequestParam(required = false) Long customerId,
                                               @RequestParam(required = false) Long supplierId,
                                               @RequestParam(required = false) String subjectType) {
        return R.ok(receivableQuery.partySummary(customerId, supplierId, subjectType));
    }

    @GetMapping("/{id}")
    public R<FinanceReceipt> getById(@PathVariable Long id) { return R.ok(service.getById(id)); }

    @GetMapping("/{id}/items")
    public R<List<FinanceReceiptItem>> getItems(@PathVariable Long id) { return R.ok(service.getItems(id)); }

    /** 分款明细（2026-09-29 多账户收款）：详情页「收款账户」卡片按行展示 */
    @GetMapping("/{id}/accounts")
    public R<List<FinanceReceiptAccount>> getAccounts(@PathVariable Long id) { return R.ok(service.getAccounts(id)); }

    /**
     * 建单：{@code body = {receipt:{…}, accounts:[{accountId,amount,remark}], items:[{receivableId,…}]}}。
     * <p>2026-09-29：{@code accounts} 支撑**多账户分款**（A 50 + B 100）；{@code items} **可为空**
     * （核销开关关闭 = 只记收款，未核销差额审核时落预收/预付台账）。
     * 老 payload（只带 {@code receipt.accountId} + items）仍可用 —— 后端会归一化成一条分款行。</p>
     */
    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        service.create(parseReceipt(body), parseAccounts(body), parseItems(body));
        return R.ok();
    }

    /**
     * 草稿就地修改（2026-09-29 用户口径「加草稿可编辑」）：字段/校验与建单完全一致，
     * 分款与核销明细整体替换；只允许 DRAFT（已审核要先反审核）。
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.update(id, parseReceipt(body), parseAccounts(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) { service.audit(id); return R.ok(); }

    @PutMapping("/{id}/un-audit")
    public R<Void> unAudit(@PathVariable Long id) { service.unAudit(id); return R.ok(); }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) { service.cancel(id); return R.ok(); }

    @SuppressWarnings("unchecked")
    private FinanceReceipt parseReceipt(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("receipt") ? (Map<String, Object>) body.get("receipt") : body;
        FinanceReceipt r = new FinanceReceipt();
        if (d.get("customerId") != null) r.setCustomerId(Long.valueOf(d.get("customerId").toString()));
        if (d.get("subjectType") != null) r.setSubjectType(d.get("subjectType").toString());
        if (d.get("supplierId") != null) r.setSupplierId(Long.valueOf(d.get("supplierId").toString()));
        if (d.get("accountId") != null) r.setAccountId(Long.valueOf(d.get("accountId").toString()));
        if (d.get("receiptDate") != null && !d.get("receiptDate").toString().isBlank())
            r.setReceiptDate(LocalDate.parse(d.get("receiptDate").toString()));
        r.setRemark((String) d.get("remark"));
        return r;
    }

    /**
     * 分款明细（2026-09-29 多账户）：{@code accounts:[{accountId, amount, remark}]}。
     * <p>为空 = 交给后端按单账户归一化（销售单现金结算的自动单 / 历史页面旧 payload）。</p>
     */
    @SuppressWarnings("unchecked")
    private List<FinanceReceiptAccount> parseAccounts(Map<String, Object> body) {
        List<FinanceReceiptAccount> list = new ArrayList<>();
        Object obj = body.get("accounts");
        if (obj instanceof List<?> raw) for (Object o : raw) if (o instanceof Map<?, ?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            FinanceReceiptAccount a = new FinanceReceiptAccount();
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
    private List<FinanceReceiptItem> parseItems(Map<String, Object> body) {
        List<FinanceReceiptItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) for (Object o : raw) if (o instanceof Map<?,?> m) {
            Map<String, Object> map = (Map<String, Object>) m;
            FinanceReceiptItem it = new FinanceReceiptItem();
            if (map.get("receivableId") != null) it.setReceivableId(Long.valueOf(map.get("receivableId").toString()));
            it.setReceivableBillNo((String) map.get("receivableBillNo"));
            if (map.get("thisAmount") != null && !map.get("thisAmount").toString().isBlank())
                it.setThisAmount(new BigDecimal(map.get("thisAmount").toString()));
            it.setRemark((String) map.get("remark"));
            list.add(it);
        }
        return list;
    }
}
