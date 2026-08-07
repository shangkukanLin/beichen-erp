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

    /** 获取项目的项目物料列表 */
    @GetMapping("/project/{projectId}")
    public R<List<DevPurchaseItem>> list(@PathVariable Long projectId) {
        return R.ok(devPurchaseItemService.lambdaQuery()
                .eq(DevPurchaseItem::getProjectId, projectId)
                .orderByDesc(DevPurchaseItem::getId)
                .list());
    }

    /** 全局分页查询研发物料（支持按名称模糊、按项目、按类型过滤，projectId为空则查询全部含未关联项目） */
    @GetMapping("/page")
    public R<IPage<Map<String, Object>>> page(PageParam pageParam,
                                              @RequestParam(required = false) String name,
                                              @RequestParam(required = false) Long projectId,
                                              @RequestParam(required = false) String type) {
        return R.ok(devPurchaseItemService.pageMaterial(pageParam, name, projectId, type));
    }

    /** 获取研发物料类型枚举（基板/屏幕/测试架/其他） */
    @GetMapping("/material-types")
    public R<List<String>> materialTypes() {
        return R.ok(DevMaterialTypeEnum.allLabels());
    }

    /** 获取当前公司全部启用的仓库（自有+委外）下拉选项 */
    @GetMapping("/warehouse-options")
    public R<List<Map<String, Object>>> warehouseOptions() {
        return R.ok(devPurchaseItemService.warehouseOptions());
    }

    /** 新增项目物料（projectId可空，表示不关联研发项目） */
    @PostMapping
    public R<DevPurchaseItem> add(@RequestBody DevPurchaseItem item) {
        item.setCompanyId(CompanyContext.get());
        devPurchaseItemService.save(item);
        return R.ok(item);
    }

    /** 修改项目物料 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody DevPurchaseItem item) {
        item.setId(id);
        devPurchaseItemService.updateById(item);
        return R.ok();
    }

    /** 删除项目物料 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        devPurchaseItemService.removeById(id);
        return R.ok();
    }
}
