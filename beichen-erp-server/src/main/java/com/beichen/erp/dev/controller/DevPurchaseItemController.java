package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.common.DevMaterialTypeEnum;
import com.beichen.erp.dev.entity.DevPurchaseItem;
import com.beichen.erp.dev.service.DevPurchaseItemService;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import java.util.Map;

/**
 * 研发项目物料管理
 */
@RestController
@RequestMapping("/api/dev/purchase-item")
@RequiredArgsConstructor
public class DevPurchaseItemController {

    private final DevPurchaseItemService devPurchaseItemService;

    /** 获取项目的项目物料列表（已回填 warehouseName/warehouseAddress） */
    @GetMapping("/project/{projectId}")
    public R<List<DevPurchaseItem>> list(@PathVariable Long projectId) {
        return R.ok(devPurchaseItemService.listByProject(projectId));
    }

    /** 获取单条研发物料详情（已回填当前位置） */
    @GetMapping("/{id}")
    public R<DevPurchaseItem> detail(@PathVariable Long id) {
        return R.ok(devPurchaseItemService.getDetail(id));
    }

    /** 全局分页查询研发物料（支持按名称模糊、按项目、按类型过滤，projectId为空则查询全部含未关联项目） */
    @GetMapping("/page")
    public R<IPage<Map<String, Object>>> page(PageParam pageParam,
                                              @RequestParam(required = false) String name,
                                              @RequestParam(required = false) Long projectId,
                                              @RequestParam(required = false) String type) {
        return R.ok(devPurchaseItemService.pageMaterial(pageParam, name, projectId, type));
    }

    /** 获取研发物料类型 **code 列表**（2026-09-14：「接口只回 code」，中文由前端 `DevMaterialTypeLabel` 映射） */
    @GetMapping("/material-types")
    public R<List<String>> materialTypes() {
        return R.ok(DevMaterialTypeEnum.allOptions());
    }

    /** 获取当前公司全部启用的仓库（自有+委外）下拉选项 */
    @GetMapping("/warehouse-options")
    public R<List<Map<String, Object>>> warehouseOptions() {
        return R.ok(devPurchaseItemService.warehouseOptions());
    }

    /** 新增项目物料（projectId 可空，表示不关联研发项目；F7-101：必填/非负校验 + companyId 判空） */
    @PostMapping
    public R<DevPurchaseItem> add(@RequestBody DevPurchaseItem item) {
        return R.ok(devPurchaseItemService.addItem(item));
    }

    /** 修改项目物料（F7-101：白名单字段更新） */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody DevPurchaseItem item) {
        item.setId(id);
        devPurchaseItemService.updateItem(item);
        return R.ok();
    }

    /** 删除项目物料（F7-101：级联清理位置流转记录） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        devPurchaseItemService.deleteItem(id);
        return R.ok();
    }

    /**
     * 为研发物料登记一笔「研发支出」费用单（2026-09-28 用户口径：该功能从「物料信息管理」**移到研发物料**）。
     *
     * <p><b>两个入口共用本端点</b>：新增研发物料弹窗里勾选「同时登记一笔研发支出」（带 {@code autoAudit=true}
     * ⇒ 建单即审核、当场扣款；无费用审核权限则降级草稿）、以及列表行「研发支出」补登记（不带 autoAudit ⇒ 落草稿）。</p>
     *
     * <p><b>幂等</b>：同一研发物料已有未作废的研发支出 ⇒ 返回原单（{@code existing=true}）；原单是草稿且本次要
     * 自动审核 ⇒ 补审核；已审核的不再动账（绝不重复扣款）。</p>
     *
     * <p>登记口径（金额/账户校验、自动审核、权限降级闸门）见
     * {@link com.beichen.erp.finance.service.RdExpenseService}；权限走本前缀（{@code dev:material}/{@code dev:project}），
     * 故研发物料页用户无需财务权限。</p>
     */
    @PostMapping("/{id}/rd-expense")
    public R<Map<String, Object>> createRdExpense(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        return R.ok(devPurchaseItemService.createRdExpense(id, body));
    }
}
