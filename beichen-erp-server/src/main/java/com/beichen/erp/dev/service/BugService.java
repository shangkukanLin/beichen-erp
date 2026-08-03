package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.dev.entity.Bug;

public interface BugService extends IService<Bug> {

    /** 分页查询项目Bug */
    Page<Bug> pageByProject(Long projectId, PageParam param, String bugType, String status);
}
