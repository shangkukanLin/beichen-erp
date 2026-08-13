package com.beichen.erp.memo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.memo.entity.Memo;
import com.beichen.erp.memo.entity.MemoProgress;
import com.beichen.erp.memo.service.MemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memo")
@RequiredArgsConstructor
public class MemoController {

    private final MemoService memoService;

    /** 分页查询当前用户的备忘录 */
    @GetMapping("/page")
    public R<Page<Memo>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(memoService.page(status, keyword, pageNum, pageSize));
    }

    /** 新增备忘录 */
    @PostMapping
    public R<Void> create(@RequestBody Memo memo) {
        memoService.create(memo);
        return R.ok();
    }

    /** 修改标题/状态 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Memo memo) {
        memo.setId(id);
        memoService.update(memo);
        return R.ok();
    }

    /** 查询某备忘录进度列表 */
    @GetMapping("/{id}/progress")
    public R<List<MemoProgress>> progressList(@PathVariable Long id) {
        return R.ok(memoService.progressList(id));
    }

    /** 追加进度 */
    @PostMapping("/{id}/progress")
    public R<Void> addProgress(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String content = body.get("content") != null ? body.get("content").toString() : null;
        memoService.addProgress(id, content);
        return R.ok();
    }

    /** 编辑单条进度 */
    @PutMapping("/progress/{progressId}")
    public R<Void> updateProgress(@PathVariable Long progressId, @RequestBody Map<String, Object> body) {
        String content = body.get("content") != null ? body.get("content").toString() : null;
        memoService.updateProgress(progressId, content);
        return R.ok();
    }

    /** 删除单条进度 */
    @DeleteMapping("/progress/{progressId}")
    public R<Void> deleteProgress(@PathVariable Long progressId) {
        memoService.deleteProgress(progressId);
        return R.ok();
    }
}
