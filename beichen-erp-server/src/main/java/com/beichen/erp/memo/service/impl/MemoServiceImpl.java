package com.beichen.erp.memo.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.memo.entity.Memo;
import com.beichen.erp.memo.entity.MemoProgress;
import com.beichen.erp.memo.mapper.MemoMapper;
import com.beichen.erp.memo.mapper.MemoProgressMapper;
import com.beichen.erp.memo.service.MemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoServiceImpl implements MemoService {

    private final MemoMapper memoMapper;
    private final MemoProgressMapper progressMapper;

    /** 当前登录用户ID */
    private Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    @Override
    public Page<Memo> page(String status, String keyword, int pageNum, int pageSize) {
        Long userId = currentUserId();
        LambdaQueryWrapper<Memo> w = new LambdaQueryWrapper<Memo>()
                .eq(Memo::getUserId, userId)
                .eq(status != null && !status.isBlank(), Memo::getStatus, status)
                .like(keyword != null && !keyword.isBlank(), Memo::getTitle, keyword)
                .orderByDesc(Memo::getId);
        return memoMapper.selectPage(new Page<>(pageNum, pageSize), w);
    }

    @Override
    public void create(Memo memo) {
        if (memo.getTitle() == null || memo.getTitle().isBlank()) {
            throw new BusinessException("标题不能为空");
        }
        memo.setId(null);
        memo.setUserId(currentUserId());
        memo.setStatus("OPEN");
        memoMapper.insert(memo);
    }

    @Override
    public void update(Memo memo) {
        Memo old = checkOwnMemo(memo.getId());
        Memo u = new Memo();
        u.setId(old.getId());
        if (memo.getTitle() != null && !memo.getTitle().isBlank()) {
            u.setTitle(memo.getTitle());
        }
        if (memo.getStatus() != null && !memo.getStatus().isBlank()) {
            u.setStatus(memo.getStatus());
        }
        memoMapper.updateById(u);
    }

    @Override
    public List<MemoProgress> progressList(Long memoId) {
        checkOwnMemo(memoId);
        return progressMapper.selectList(new LambdaQueryWrapper<MemoProgress>()
                .eq(MemoProgress::getMemoId, memoId)
                .eq(MemoProgress::getUserId, currentUserId())
                .orderByDesc(MemoProgress::getId));
    }

    @Override
    public void addProgress(Long memoId, String content) {
        checkOwnMemo(memoId);
        if (content == null || content.isBlank()) {
            throw new BusinessException("进度内容不能为空");
        }
        MemoProgress p = new MemoProgress();
        p.setMemoId(memoId);
        p.setContent(content);
        p.setUserId(currentUserId());
        progressMapper.insert(p);
    }

    @Override
    public void updateProgress(Long progressId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("进度内容不能为空");
        }
        MemoProgress old = progressMapper.selectById(progressId);
        if (old == null || !old.getUserId().equals(currentUserId())) {
            throw new BusinessException("进度不存在或无权操作");
        }
        MemoProgress u = new MemoProgress();
        u.setId(progressId);
        u.setContent(content);
        progressMapper.updateById(u);
    }

    @Override
    public void deleteProgress(Long progressId) {
        MemoProgress old = progressMapper.selectById(progressId);
        if (old == null || !old.getUserId().equals(currentUserId())) {
            throw new BusinessException("进度不存在或无权操作");
        }
        progressMapper.deleteById(progressId);
    }

    /** 校验备忘录归属本人，返回该备忘录 */
    private Memo checkOwnMemo(Long memoId) {
        Memo memo = memoMapper.selectById(memoId);
        if (memo == null || !memo.getUserId().equals(currentUserId())) {
            throw new BusinessException("备忘录不存在或无权操作");
        }
        return memo;
    }
}
