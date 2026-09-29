package com.beichen.erp.finance.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.ExcelExportHelper;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.common.BillStatementExcelBuilder;
import com.beichen.erp.finance.common.BillType;
import com.beichen.erp.finance.entity.FinanceBill;
import com.beichen.erp.finance.entity.FinanceBillItem;
import com.beichen.erp.finance.service.FinanceBillService;
import com.beichen.erp.finance.task.FinanceBillAutoTask;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.system.common.SystemConstants;
import com.beichen.erp.system.entity.Company;
import com.beichen.erp.system.mapper.CompanyMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/bill")
@RequiredArgsConstructor
public class FinanceBillController {

    private final FinanceBillService service;
    private final FinanceBillAutoTask autoTask;

    /**
     * 对账单导出要带「公司抬头」与「往来单位联系人/电话」（2026-09-29 用户口径：账单详情加导出功能）。
     * <p>只读三张主数据表（`sys_company` 抬头 / `supplier` / `customer` 联系方式）；
     * **不新增权限点** —— 本控制器挂在 `/api/finance/bill` 前缀下，按前缀最长匹配只要求 `finance:bill`。</p>
     */
    private final CompanyMapper companyMapper;
    private final SupplierMapper supplierMapper;
    private final CustomerMapper customerMapper;

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
     *
     * <p><b>F7-39#8（2026-09-19）</b>：手动入口也走**同一把跨实例锁**（{@code runAllLocked} /
     * {@code runForCompanyLocked}）—— 原先手动触发完全绕过 {@code GET_LOCK}，多实例并发点击可重复出账。</p>
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
            return R.ok(autoTask.runAllLocked(LocalDate.now()));
        }
        return R.ok(autoTask.runForCompanyLocked(CompanyContext.get(), LocalDate.now()));
    }

    // E1 口径（2026-09-12）：审核族统一 PUT（旧 POST 保留为别名）；反审核统一 /un-audit（旧 /unAudit 保留为别名）
    @RequestMapping(value = "/{id}/audit", method = {RequestMethod.PUT, RequestMethod.POST})
    public R<Void> audit(@PathVariable Long id) { service.audit(id); return R.ok(); }

    @RequestMapping(value = {"/{id}/un-audit", "/{id}/unAudit"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public R<Void> unAudit(@PathVariable Long id) { service.unAudit(id); return R.ok(); }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) { service.cancel(id); return R.ok(); }

    /**
     * 导出「应收/应付对账单」Excel（2026-09-29 用户口径「账单详情要添加导出功能」，按**专业对外单据**形态实现）。
     *
     * <p>形态与口径见 {@link BillStatementExcelBuilder}（抬头/标题/单头/明细/公式合计/大写/说明/签字区 + 打印设置）。
     * 本方法只负责取数与"往来单位联系人/电话"的兜底：账单类型=应付 ⇒ 查 `supplier`，应收 ⇒ 查 `customer`；
     * 查不到不影响导出（该格留空），**不因主数据缺失而让单据导不出来**。</p>
     *
     * <p><b>不限制单据状态</b>：库里绝大多数账单是**草稿**（只放已审核等于该功能对多数账单不可用）；
     * 草稿/作废由文件内标题后缀「（草稿）/（已作废）」+ 红色警示行**显式标注**，不会被误当生效凭证。</p>
     *
     * <p>⚠️ 前端按"文件流"接收（`responseType: 'blob'`）；失败时全局异常处理器回 JSON ⇒ 前端据 blob 类型区分
     * （与合同导出同一套写法，见 outsource/order/detail.vue 的 exportPdf）。</p>
     */
    @GetMapping("/{id}/export")
    public void export(@PathVariable Long id, HttpServletResponse resp) throws IOException {
        FinanceBill bill = service.getById(id);
        if (bill == null) throw new BusinessException("账单不存在");
        Company company = bill.getCompanyId() != null ? companyMapper.selectById(bill.getCompanyId()) : null;
        String contact = null;
        String phone = null;
        if (bill.getPartnerId() != null) {
            if (BillType.PAYABLE.name().equalsIgnoreCase(bill.getBillType())) {
                Supplier s = supplierMapper.selectById(bill.getPartnerId());
                if (s != null) { contact = s.getContact(); phone = s.getPhone(); }
            } else {
                Customer c = customerMapper.selectById(bill.getPartnerId());
                if (c != null) { contact = c.getContact(); phone = c.getPhone(); }
            }
        }
        Workbook wb = BillStatementExcelBuilder.build(bill, service.getItems(id), company, contact, phone, LocalDate.now());
        ExcelExportHelper.write(resp, wb, BillStatementExcelBuilder.fileName(bill));
    }
}
