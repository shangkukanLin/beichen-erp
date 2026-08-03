package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.dev.entity.Bug;
import com.beichen.erp.dev.mapper.BugMapper;
import com.beichen.erp.dev.service.BugService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BugServiceImpl extends ServiceImpl<BugMapper, Bug> implements BugService {

    @Override
    public Page<Bug> pageByProject(Long projectId, PageParam param, String bugType, String status) {
        LambdaQueryWrapper<Bug> w = new LambdaQueryWrapper<>();
        w.eq(Bug::getProjectId, projectId);
        if (bugType != null && !bugType.isBlank()) {
            w.eq(Bug::getBugType, bugType);
        }
        if (status != null && !status.isBlank()) {
            w.eq(Bug::getStatus, status);
        }
        w.orderByDesc(Bug::getId);
        return baseMapper.selectPage(new Page<>(param.getPageNum(), param.getPageSize()), w);
    }
}
