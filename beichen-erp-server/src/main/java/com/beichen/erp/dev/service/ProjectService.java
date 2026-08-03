package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.dev.entity.Project;

import java.util.List;

public interface ProjectService extends IService<Project> {

    /** 分页查询项目 */
    Page<Project> page(PageParam param, String keyword, String status);

    /** 新增项目（含时间线初始化，第一个阶段自动激活） */
    Project create(Project project);

    /** 取消项目 */
    void cancel(Long projectId);

    /** 重新激活项目 */
    void reactivate(Long projectId);

    /** 按状态查询项目列表 */
    List<Project> listByStatus(String status);
}
