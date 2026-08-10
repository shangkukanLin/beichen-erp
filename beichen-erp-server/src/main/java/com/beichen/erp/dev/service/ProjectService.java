package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.ProjectTimeline;

import java.util.List;
import java.util.Map;

public interface ProjectService extends IService<Project> {

    /** 分页查询项目 */
    Page<Project> page(PageParam param, String keyword, String status);

    /** 新增项目（含时间线初始化，第一个阶段自动激活）
     *  @param linkExistingProductId 可选：关联已有产品ID（重名时用户选择关联），null=按总成名称新建 */
    Project create(Project project, Long linkExistingProductId);

    /** 取消项目 */
    void cancel(Long projectId);

    /** 重新激活项目 */
    void reactivate(Long projectId);

    /** 按状态查询项目列表 */
    List<Project> listByStatus(String status);

    /** 更新项目，并同步总成名称变更到关联产品（同一事务，避免部分成功） */
    void updateProject(Project project);

    /** 批量查询多个项目的时间线，一次 in 查询后按 projectId 分组（消除 N+1） */
    Map<Long, List<ProjectTimeline>> batchTimelines(List<Long> projectIds);

    /** 聚合项目相关的销售/采购/委外/研发物料单据，按模块分组返回 */
    Map<String, Object> getRelatedOrders(Long projectId);

    /** 查询单个项目的时间线列表（委托时间线服务） */
    List<ProjectTimeline> listByProject(Long projectId);
}
