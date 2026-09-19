package com.beichen.erp.finance.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.entity.FinanceBill;
import com.beichen.erp.finance.entity.FinanceBillItem;
import com.beichen.erp.finance.service.FinanceBillService;
import com.beichen.erp.finance.task.FinanceBillAutoTask;
import com.beichen.erp.system.common.SystemConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/bill")
@RequiredArgsConstructor
public class FinanceBillController {

    private final FinanceBillService service;
    private final FinanceBillAutoTask autoTask;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(billType, partnerId, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<FinanceBill> getById(@PathVariable Long id) { return R.ok(service.getById(id)); }

    @GetMapping("/{id}/items")
    public R<List<FinanceBillItem>> getItems(@PathVariable Long id) { return R.ok(service.getItems(id)); }

    @PostMapping("/generate")
    public R<FinanceBill> generate(@RequestBody Map<String, Object> body) {
        String billType = (String) body.get("billType");
        Long partnerId = body.get("partnerId") != null ? Long.valueOf(body.get("partnerId").toString()) : null;
        String partnerName = (String) body.get("partnerName");
        LocalDate periodStart = body.get("periodStart") != null ? LocalDate.parse(body.get("periodStart").toString()) : null;
        LocalDate periodEnd = body.get("periodEnd") != null ? LocalDate.parse(body.get("periodEnd").toString()) : null;
        return R.ok(service.generate(billType, partnerId, partnerName, periodStart, periodEnd));
    }

    /**
     * 手动触发一轮自动账单生成（与每日凌晨 2 点定时任务同逻辑，供"立即执行"与排查使用）。
     *
     * <p><b>F7-38（2026-09-19）</b>：默认**只跑当前公司** —— 页面"立即执行"的语义本就是生成本租户账单；
     * 原先直接调 {@code autoTask.run(...)} 会**遍历所有公司**（跨租户批量写），而权限只要求
     * {@code finance:bill} 页面码。跨租户全量现收口给平台超管，且必须显式传 {@code all=true}；
     * 定时任务（{@code runScheduled}）不受影响，仍按实例锁全公司跑。</p>
     */
    @PostMapping("/auto-generate")
    public R<String> autoGenerate(@RequestParam(defaultValue = "false") boolean all) {
        if (all) {
            boolean superAdmin;
            try {
                superAdmin = StpUtil.hasRole(SystemConstants.SUPER_ADMIN_ROLE_CODE);
            } catch (Exception e) {
                superAdmin = false; // 无登录上下文 → 从严拒绝
            }
            if (!superAdmin) throw new BusinessException(403, "仅超级管理员可触发全公司账单生成");
            return R.ok(autoTask.run(LocalDate.now()));
        }
        return R.ok(autoTask.runForCompany(CompanyContext.get(), LocalDate.now()));
    }

    // E1 口径（2026-09-12）：审核族统一 PUT（旧 POST 保留为别名）；反审核统一 /un-audit（旧 /unAudit 保留为别名）
    @RequestMapping(value = "/{id}/audit", method = {RequestMethod.PUT, RequestMethod.POST})
    public R<Void> audit(@PathVariable Long id) { service.audit(id); return R.ok(); }

    @RequestMapping(value = {"/{id}/un-audit", "/{id}/unAudit"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public R<Void> unAudit(@PathVariable Long id) { service.unAudit(id); return R.ok(); }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) { service.cancel(id); return R.ok(); }
}
