package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.dev.common.ProjectStatus;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.ProjectTimeline;
import com.beichen.erp.dev.mapper.DevPurchaseItemMapper;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.dev.mapper.ProjectTimelineMapper;
import com.beichen.erp.dev.service.ProjectProductSyncService;
import com.beichen.erp.dev.service.ProjectService;
import com.beichen.erp.dev.service.ProjectTimelineService;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.mapper.OutsourceOrderMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderProductMapper;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectTimelineMapper projectTimelineMapper;
    private final ProjectProductSyncService projectProductSyncService;
    private final ProjectTimelineService projectTimelineService;
    private final DevPurchaseItemMapper devPurchaseItemMapper;
    private final OutsourceOrderProductMapper outsourceOrderProductMapper;
    private final OutsourceOrderMapper outsourceOrderMapper;
    private final SupplierMapper supplierMapper;

    @Override
    public Project getById(Serializable id) {
        Project p = projectMapper.selectById(id);
        if (p != null) fillFactoryNames(List.of(p));
        return p;
    }

    public Page<Project> page(PageParam param, String keyword, String status) {
        LambdaQueryWrapper<Project> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(wr -> wr.like(Project::getName, keyword)
                    .or().like(Project::getCode, keyword)
                    .or().like(Project::getAssemblyName, keyword));
        }
        if (status != null && !status.isBlank()) {
            w.eq(Project::getStatus, status);
        }
        w.orderByDesc(Project::getId);
        Page<Project> page = projectMapper.selectPage(new Page<>(param.getPageNum(), param.getPageSize()), w);
        fillFactoryNames(page.getRecords());
        return page;
    }

    /**
     * 批量回填打样工厂/委外工厂名称：一次性 IN 查询 supplier，避免 N+1。
     * 名称随详情/列表接口一起返回，前端无需再发请求解析。
     */
    private void fillFactoryNames(List<Project> list) {
        if (list == null || list.isEmpty()) return;
        java.util.Set<Long> ids = new java.util.LinkedHashSet<>();
        for (Project p : list) {
            if (p.getSampleFactoryId() != null) ids.add(p.getSampleFactoryId());
            if (p.getOutsourceFactoryId() != null) ids.add(p.getOutsourceFactoryId());
        }
        if (ids.isEmpty()) return;
        List<Supplier> suppliers = supplierMapper.selectBatchIds(ids);
        Map<Long, String> nameMap = suppliers.stream()
                .collect(java.util.stream.Collectors.toMap(Supplier::getId, Supplier::getName, (a, b) -> a));
        for (Project p : list) {
            if (p.getSampleFactoryId() != null) p.setSampleFactoryName(nameMap.get(p.getSampleFactoryId()));
            if (p.getOutsourceFactoryId() != null) p.setOutsourceFactoryName(nameMap.get(p.getOutsourceFactoryId()));
        }
    }

    @Override
    @Transactional
    public Project create(Project project, Long linkExistingProductId) {
        project.setCode(generateProjectCode());
        project.setStatus(ProjectStatus.IN_PROGRESS.getCode());
        project.setCreateTime(LocalDateTime.now());
        projectMapper.insert(project);

        // 创建时间线，第一个阶段自动激活（复用 ProjectTimelineService 统一初始化逻辑）
        projectTimelineService.initTimeline(project.getId());

        // 根据总成名称生成/关联产品（若项目配置了总成名称）
        projectProductSyncService.syncProduct(project.getId(), linkExistingProductId);
        return project;
    }

    @Override
    @Transactional
    public void cancel(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) return;
        project.setStatus(ProjectStatus.CANCELLED.getCode());
        project.setCancelledAt(LocalDateTime.now());
        projectMapper.updateById(project);
        log.info("项目已取消: projectId={}", projectId);
    }

    @Override
    @Transactional
    public void reactivate(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) return;
        project.setStatus(ProjectStatus.IN_PROGRESS.getCode());
        project.setCancelledAt(null);
        projectMapper.updateById(project);
        log.info("项目已重新激活: projectId={}", projectId);
    }

    @Override
    public List<Project> listByStatus(String status) {
        LambdaQueryWrapper<Project> w = new LambdaQueryWrapper<>();
        w.eq(Project::getStatus, status);
        return projectMapper.selectList(w);
    }

    // ===== 内部方法 =====

    private String generateProjectCode() {
        String date = LocalDate.now().toString().replace("-", "").substring(2);
        LambdaQueryWrapper<Project> w = new LambdaQueryWrapper<>();
        w.likeRight(Project::getCode, BillPrefix.DEV_PROJECT + date);
        long count = projectMapper.selectCount(w);
        return BillPrefix.DEV_PROJECT + date + "-" + String.format("%03d", count + 1);
    }

    @Override
    @Transactional
    public void updateProject(Project project) {
        // 总成名称变更时，同步改名关联产品，确保两处名称一致
        Project old = projectMapper.selectById(project.getId());
        projectMapper.updateById(project);
        if (old != null
                && old.getAssemblyName() != null
                && !old.getAssemblyName().equals(project.getAssemblyName())) {
            projectProductSyncService.syncProductNameFromProject(
                    project.getId(), project.getAssemblyName());
        }
    }

    @Override
    public Map<Long, List<ProjectTimeline>> batchTimelines(List<Long> projectIds) {
        Map<Long, List<ProjectTimeline>> result = new LinkedHashMap<>();
        if (projectIds == null || projectIds.isEmpty()) {
            return result;
        }
        // 一次 in 查询取回所有项目的时间线，避免循环内逐个查询（N+1）
        List<ProjectTimeline> all = projectTimelineMapper.selectList(
                new LambdaQueryWrapper<ProjectTimeline>().in(ProjectTimeline::getProjectId, projectIds));
        return buildTimelineMap(all);
    }

    private Map<Long, List<ProjectTimeline>> buildTimelineMap(List<ProjectTimeline> all) {
        Map<Long, List<ProjectTimeline>> map = new HashMap<>();
        for (ProjectTimeline t : all) {
            map.computeIfAbsent(t.getProjectId(), k -> new java.util.ArrayList<>()).add(t);
        }
        return map;
    }

    @Override
    public Map<String, Object> getRelatedOrders(Long projectId) {
        Map<String, Object> result = new LinkedHashMap<>();
        // 研发物料
        List<?> devMaterial = devPurchaseItemMapper.selectList(
                new LambdaQueryWrapper<com.beichen.erp.dev.entity.DevPurchaseItem>()
                        .eq(com.beichen.erp.dev.entity.DevPurchaseItem::getProjectId, projectId)
                        .orderByDesc(com.beichen.erp.dev.entity.DevPurchaseItem::getId));
        result.put("devMaterial", devMaterial);
        // 委外加工单（通过 outsource_order_product.project_id 关联）
        List<OutsourceOrderProduct> products = outsourceOrderProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderProduct>()
                        .eq(OutsourceOrderProduct::getProjectId, projectId));
        if (!products.isEmpty()) {
            Set<Long> orderIds = products.stream().map(OutsourceOrderProduct::getOrderId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!orderIds.isEmpty()) {
                List<OutsourceOrder> orders = outsourceOrderMapper.selectBatchIds(orderIds);
                List<Map<String, Object>> orderList = new ArrayList<>();
                for (OutsourceOrder o : orders) {
                    Map<String, Object> om = new LinkedHashMap<>();
                    om.put("id", o.getId()); om.put("code", o.getCode());
                    om.put("status", o.getStatus()); om.put("createTime", o.getCreateTime());
                    // 取该订单关联的产品名称
                    String pn = products.stream()
                            .filter(p -> Objects.equals(p.getOrderId(), o.getId()))
                            .map(OutsourceOrderProduct::getProductName)
                            .filter(Objects::nonNull).findFirst().orElse("");
                    om.put("productName", pn);
                    orderList.add(om);
                }
                result.put("outsourceOrders", orderList);
            }
        }
        return result;
    }

    @Override
    public List<ProjectTimeline> listByProject(Long projectId) {
        return projectTimelineService.listByProject(projectId);
    }
}
