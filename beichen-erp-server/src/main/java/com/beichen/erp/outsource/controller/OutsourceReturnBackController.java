package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.outsource.entity.OutsourceReturnBack;
import com.beichen.erp.outsource.entity.OutsourceReturnBackItem;
import com.beichen.erp.outsource.service.OutsourceReturnBackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 加工返回单 Controller（委外加工 → 加工退货页「加工返回单」页签）
 * <p>路由前缀: /api/outsource/return-back（ApiPermGuard 复用 outsource:return-order 码）。
 * Restful：GET 查询 / POST 新增 / PUT 修改·审核·反审核 / DELETE 删除 / POST 作废。</p>
 */
@RestController
@RequestMapping("/api/outsource/return-back")
@RequiredArgsConstructor
public class OutsourceReturnBackController {

    private final OutsourceReturnBackService service;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String code,
                                             @RequestParam(required = false) Long factoryId,
                                             @RequestParam(required = false) String status) {
        return R.ok(service.page(pageNum, pageSize, code, factoryId, status));
    }

    @GetMapping("/{id}")
    public R<OutsourceReturnBack> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<OutsourceReturnBackItem>> items(@PathVariable Long id) {
        return R.ok(service.getItems(id));
    }

    @PostMapping
    public R<OutsourceReturnBack> create(@RequestBody Map<String, Object> body) {
        return R.ok(service.create(body));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.update(id, body);
        return R.ok();
    }

    // 审核族统一 PUT（E1 口径）；反审核统一 /un-audit
    @RequestMapping(value = "/{id}/audit", method = {RequestMethod.PUT, RequestMethod.POST})
    public R<Void> audit(@PathVariable Long id) {
        service.audit(id);
        return R.ok();
    }

    @RequestMapping(value = {"/{id}/un-audit", "/{id}/unAudit"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public R<Void> unAudit(@PathVariable Long id) {
        service.unAudit(id);
        return R.ok();
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.deleteDraft(id);
        return R.ok();
    }
}
