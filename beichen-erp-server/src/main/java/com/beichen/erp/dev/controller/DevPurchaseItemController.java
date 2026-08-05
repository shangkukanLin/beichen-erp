package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.DevPurchaseItem;
import com.beichen.erp.dev.service.DevPurchaseItemService;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.List;

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

    /** 新增项目物料 */
    @PostMapping
    public R<DevPurchaseItem> add(@RequestBody DevPurchaseItem item) {
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
