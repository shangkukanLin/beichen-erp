package com.beichen.erp.memo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.memo.entity.Memo;
import com.beichen.erp.memo.entity.MemoProgress;

import java.util.List;

public interface MemoService {

    /** 分页查询当前用户的备忘录 */
    Page<Memo> page(String status, String keyword, int pageNum, int pageSize);

    /** 新增备忘录 */
    void create(Memo memo);

    /** 修改标题/状态（仅本人） */
    void update(Memo memo);

    /** 查询某备忘录的进度列表（倒序） */
    List<MemoProgress> progressList(Long memoId);

    /** 追加进度 */
    void addProgress(Long memoId, String content);

    /** 编辑单条进度（仅本人） */
    void updateProgress(Long progressId, String content);

    /** 删除单条进度（仅本人） */
    void deleteProgress(Long progressId);
}
