package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.brand.entity.Brand;
import com.beichen.erp.brand.mapper.BrandMapper;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.common.ProjectStatus;
import com.beichen.erp.dev.entity.Bom;
import com.beichen.erp.dev.entity.MaterialType;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.ProjectPhase;
import com.beichen.erp.dev.mapper.BomMapper;
import com.beichen.erp.dev.mapper.MaterialTypeMapper;
import com.beichen.erp.dev.mapper.DevPurchaseItemMapper;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.dev.mapper.ProjectPhaseMapper;
import com.beichen.erp.dev.service.ProjectProductSyncService;
import com.beichen.erp.dev.service.ProjectService;
import com.beichen.erp.dev.service.ProjectPhaseService;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceMaterialComponent;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.mapper.OutsourceMaterialComponentMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
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
    private final ProjectPhaseMapper projectPhaseMapper;
    private final ProjectProductSyncService projectProductSyncService;
    private final ProjectPhaseService projectPhaseService;
    private final DevPurchaseItemMapper devPurchaseItemMapper;
    private final OutsourceOrderProductMapper outsourceOrderProductMapper;
    private final OutsourceOrderMapper outsourceOrderMapper;
    private final SupplierMapper supplierMapper;
    private final BrandMapper brandMapper;
    private final BomMapper bomMapper;
    private final MaterialTypeMapper materialTypeMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final OutsourceMaterialComponentMapper outsourceMaterialComponentMapper;

    @Override
    public Project getById(Serializable id) {
        Project p = projectMapper.selectById(id);
        if (p != null) {
            fillFactoryNames(List.of(p));
            fillBrandNames(List.of(p));
            // 改配信息以 BOM 为准：驱动IC 取 BOM 独立行，触摸IC/码片IC 取排线物料子物料
            fillConfigFromBom(p);
        }
        return p;
    }

    public Page<Project> page(PageParam param, String keyword, String status, Long brandId) {
        LambdaQueryWrapper<Project> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(wr -> wr.like(Project::getName, keyword)
                    .or().like(Project::getCode, keyword)
                    .or().like(Project::getAssemblyName, keyword));
        }
        if (status != null && !status.isBlank()) {
            w.eq(Project::getStatus, status);
        }
        if (brandId != null) {
            w.eq(Project::getBrandId, brandId);
        }
        w.orderByDesc(Project::getId);
        Page<Project> page = projectMapper.selectPage(new Page<>(param.getPageNum(), param.getPageSize()), w);
        fillFactoryNames(page.getRecords());
        fillBrandNames(page.getRecords());
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

    /** 批量回填品牌名称：一次性 IN 查询 brand，避免 N+1 */
    private void fillBrandNames(List<Project> list) {
        if (list == null || list.isEmpty()) return;
        java.util.Set<Long> ids = new java.util.LinkedHashSet<>();
        for (Project p : list) {
            if (p.getBrandId() != null) ids.add(p.getBrandId());
        }
        if (ids.isEmpty()) return;
        Map<Long, String> nameMap = brandMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Brand::getId, Brand::getBrandName, (a, b) -> a));
        for (Project p : list) {
            if (p.getBrandId() != null) p.setBrandName(nameMap.get(p.getBrandId()));
        }
    }

    @Override
    @Transactional
    public Project create(Project project, Long linkExistingProductId) {
        project.setCode(generateProjectCode());
        project.setStatus(ProjectStatus.IN_PROGRESS.getCode());
        project.setCreateTime(LocalDateTime.now());
        projectMapper.insert(project);

        // 创建项目阶段，第一个阶段自动激活（复用 ProjectPhaseService 统一初始化逻辑）
        projectPhaseService.initPhase(project.getId());

        // 根据总成名称生成/关联产品（若项目配置了总成名称）
        projectProductSyncService.syncProduct(project.getId(), linkExistingProductId);

        // 改配信息（驱动IC/触摸IC/码片IC）联动写入项目 BOM
        syncConfigToBom(project);
        return project;
    }

    /**
     * 改配信息同步到项目 BOM：
     * 驱动IC 为 BOM 独立行；触摸IC/码片IC 挂在"排线"物料的子物料上（排线物料不存在时自动创建）。
     */
    private void syncConfigToBom(Project project) {
        if (project == null || project.getId() == null) return;
        Map<String, Long> typeMap = materialTypeIdMap();
        int version = maxBomVersion(project.getId());
        upsertBomRow(project.getId(), typeMap.get("驱动IC"), project.getConfigDriveIcId(), version);
        syncPaixianComponents(project, version);
    }

    /** 更新/新增 BOM 独立行（物料未选择时不处理） */
    private void upsertBomRow(Long projectId, Long materialTypeId, Long materialId, int version) {
        if (projectId == null || materialTypeId == null || materialId == null) return;
        Bom row = bomMapper.selectOne(new LambdaQueryWrapper<Bom>()
                .eq(Bom::getProjectId, projectId)
                .eq(Bom::getMaterialTypeId, materialTypeId)
                .orderByDesc(Bom::getVersion)
                .last("LIMIT 1"));
        if (row != null) {
            row.setOutsourceMaterialId(materialId);
            row.setUpdateTime(LocalDateTime.now());
            bomMapper.updateById(row);
        } else {
            Bom nb = new Bom();
            nb.setProjectId(projectId);
            nb.setMaterialTypeId(materialTypeId);
            nb.setOutsourceMaterialId(materialId);
            nb.setQuantity(java.math.BigDecimal.ONE);
            nb.setVersion(version);
            nb.setCreateTime(LocalDateTime.now());
            nb.setUpdateTime(LocalDateTime.now());
            bomMapper.insert(nb);
        }
    }

    /**
     * 触摸IC/码片IC 同步到排线物料的子物料组成。
     * 排线物料不存在时自动创建（outsource_material + BOM 排线行）。
     * 保留排线上原有的其他子物料，仅更新触摸IC/码片IC。
     */
    private void syncPaixianComponents(Project project, int version) {
        Map<String, Long> typeMap = materialTypeIdMap();
        Long paixianTypeId = typeMap.get("排线");
        Long touchTypeId = typeMap.get("触摸IC");
        Long codeTypeId = typeMap.get("码片IC");
        if (paixianTypeId == null) return;
        Long projectId = project.getId();

        // 1. 确保排线物料存在（BOM 排线行 + outsource_material 记录）
        //    排线行被删除、或排线行引用的物料被删除时，自动重建/复用，保证触摸IC/码片IC 有宿主
        Bom paixianRow = bomMapper.selectOne(new LambdaQueryWrapper<Bom>()
                .eq(Bom::getProjectId, projectId)
                .eq(Bom::getMaterialTypeId, paixianTypeId)
                .orderByDesc(Bom::getVersion)
                .last("LIMIT 1"));
        Long paixianMatId = paixianRow != null ? paixianRow.getOutsourceMaterialId() : null;
        if (paixianMatId != null && outsourceMaterialMapper.selectById(paixianMatId) == null) {
            paixianMatId = null; // 排线行引用的物料已被删除，标记重建
        }
        if (paixianMatId == null) {
            // 项目名称兜底：更新请求可能不携带 name，从库中查
            String pname = project.getName();
            if (pname == null || pname.isBlank()) {
                Project db = projectMapper.selectById(projectId);
                if (db != null) pname = db.getName();
            }
            String matName = "排线-" + (pname == null ? "" : pname);
            // 优先复用同名排线物料（如 BOM 排线行被删但物料还在），避免重复创建
            OutsourceMaterial existing = outsourceMaterialMapper.selectOne(
                    new LambdaQueryWrapper<OutsourceMaterial>()
                            .eq(OutsourceMaterial::getMaterialName, matName)
                            .eq(OutsourceMaterial::getMaterialTypeId, paixianTypeId)
                            .last("LIMIT 1"));
            if (existing != null) {
                paixianMatId = existing.getId();
            } else {
                OutsourceMaterial mat = new OutsourceMaterial();
                mat.setMaterialName(matName);
                mat.setMaterialTypeId(paixianTypeId);
                mat.setStatus(1);
                mat.setUnit("PCS");
                mat.setCompanyId(CompanyContext.get());
                outsourceMaterialMapper.insert(mat);
                paixianMatId = mat.getId();
            }
            if (paixianRow != null) {
                paixianRow.setOutsourceMaterialId(paixianMatId);
                paixianRow.setUpdateTime(LocalDateTime.now());
                bomMapper.updateById(paixianRow);
            } else {
                Bom nb = new Bom();
                nb.setProjectId(projectId);
                nb.setMaterialTypeId(paixianTypeId);
                nb.setOutsourceMaterialId(paixianMatId);
                nb.setQuantity(java.math.BigDecimal.ONE);
                nb.setVersion(version);
                nb.setCreateTime(LocalDateTime.now());
                nb.setUpdateTime(LocalDateTime.now());
                bomMapper.insert(nb);
            }
        }

        // 2. 触摸IC/码片IC 改由排线子物料承载，清理 BOM 中旧的独立行
        if (project.getConfigTouchIcId() != null && touchTypeId != null) {
            bomMapper.delete(new LambdaQueryWrapper<Bom>()
                    .eq(Bom::getProjectId, projectId)
                    .eq(Bom::getMaterialTypeId, touchTypeId)
                    .eq(Bom::getVersion, version));
        }
        if (project.getConfigCodeIcId() != null && codeTypeId != null) {
            bomMapper.delete(new LambdaQueryWrapper<Bom>()
                    .eq(Bom::getProjectId, projectId)
                    .eq(Bom::getMaterialTypeId, codeTypeId)
                    .eq(Bom::getVersion, version));
        }

        // 3. 全量重建排线子物料：保留非触摸IC/码片IC 子物料 + 改配的触摸IC/码片IC
        List<OutsourceMaterialComponent> existing = outsourceMaterialComponentMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialComponent>()
                        .eq(OutsourceMaterialComponent::getParentMaterialId, paixianMatId));
        List<Long> childIds = existing.stream()
                .map(OutsourceMaterialComponent::getChildMaterialId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Long> childType = new HashMap<>();
        if (!childIds.isEmpty()) {
            for (OutsourceMaterial c : outsourceMaterialMapper.selectBatchIds(childIds)) {
                childType.put(c.getId(), c.getMaterialTypeId());
            }
        }
        List<OutsourceMaterialComponent> keep = existing.stream()
                .filter(c -> !Objects.equals(childType.get(c.getChildMaterialId()), touchTypeId)
                        && !Objects.equals(childType.get(c.getChildMaterialId()), codeTypeId))
                .toList();
        outsourceMaterialComponentMapper.delete(new LambdaQueryWrapper<OutsourceMaterialComponent>()
                .eq(OutsourceMaterialComponent::getParentMaterialId, paixianMatId));
        for (OutsourceMaterialComponent k : keep) {
            OutsourceMaterialComponent n = new OutsourceMaterialComponent();
            n.setParentMaterialId(paixianMatId);
            n.setChildMaterialId(k.getChildMaterialId());
            n.setQuantity(k.getQuantity());
            n.setLossRate(k.getLossRate());
            n.setRemark(k.getRemark());
            n.setCompanyId(CompanyContext.get());
            outsourceMaterialComponentMapper.insert(n);
        }
        Long[] touchCode = {project.getConfigTouchIcId(), project.getConfigCodeIcId()};
        for (Long mid : touchCode) {
            if (mid == null) continue;
            OutsourceMaterial m = outsourceMaterialMapper.selectById(mid);
            if (m == null) continue;
            OutsourceMaterialComponent n = new OutsourceMaterialComponent();
            n.setParentMaterialId(paixianMatId);
            n.setChildMaterialId(mid);
            n.setQuantity(java.math.BigDecimal.ONE);
            n.setLossRate(java.math.BigDecimal.ZERO);
            n.setCompanyId(CompanyContext.get());
            outsourceMaterialComponentMapper.insert(n);
        }
    }

    /**
     * 详情返回时以 BOM 为准，反查改配信息字段：
     * 驱动IC 取 BOM 独立行；触摸IC/码片IC 取排线物料子物料中对应类型的子物料。
     */
    private void fillConfigFromBom(Project p) {
        if (p == null || p.getId() == null) return;
        List<Bom> boms = bomMapper.selectList(new LambdaQueryWrapper<Bom>().eq(Bom::getProjectId, p.getId()));
        Map<String, Long> typeMap = materialTypeIdMap();
        p.setConfigDriveIcId(null);
        p.setConfigTouchIcId(null);
        p.setConfigCodeIcId(null);
        if (boms.isEmpty()) return;
        int maxVersion = boms.stream().mapToInt(Bom::getVersion).max().orElse(1);
        Map<Long, Long> typeToMat = new HashMap<>();
        for (Bom b : boms) {
            if (Objects.equals(b.getVersion(), maxVersion) && b.getOutsourceMaterialId() != null) {
                typeToMat.putIfAbsent(b.getMaterialTypeId(), b.getOutsourceMaterialId());
            }
        }
        p.setConfigDriveIcId(typeToMat.get(typeMap.get("驱动IC")));
        // 触摸IC/码片IC：从排线物料的子物料中按类型取
        Long paixianMatId = typeToMat.get(typeMap.get("排线"));
        Long touchTypeId = typeMap.get("触摸IC");
        Long codeTypeId = typeMap.get("码片IC");
        if (paixianMatId == null) return;
        List<OutsourceMaterialComponent> comps = outsourceMaterialComponentMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialComponent>()
                        .eq(OutsourceMaterialComponent::getParentMaterialId, paixianMatId));
        List<Long> childIds = comps.stream()
                .map(OutsourceMaterialComponent::getChildMaterialId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Long> childType = new HashMap<>();
        if (!childIds.isEmpty()) {
            for (OutsourceMaterial c : outsourceMaterialMapper.selectBatchIds(childIds)) {
                childType.put(c.getId(), c.getMaterialTypeId());
            }
        }
        for (OutsourceMaterialComponent c : comps) {
            Long t = childType.get(c.getChildMaterialId());
            if (p.getConfigTouchIcId() == null && Objects.equals(t, touchTypeId)) {
                p.setConfigTouchIcId(c.getChildMaterialId());
            }
            if (p.getConfigCodeIcId() == null && Objects.equals(t, codeTypeId)) {
                p.setConfigCodeIcId(c.getChildMaterialId());
            }
        }
    }

    /** 物料类型名 -> id 映射 */
    private Map<String, Long> materialTypeIdMap() {
        Map<String, Long> m = new HashMap<>();
        for (MaterialType t : materialTypeMapper.selectList(null)) {
            m.put(t.getTypeName(), t.getId());
        }
        return m;
    }

    /** 项目最新 BOM 版本号，无 BOM 则 1 */
    private int maxBomVersion(Long projectId) {
        Bom latest = bomMapper.selectOne(new LambdaQueryWrapper<Bom>()
                .eq(Bom::getProjectId, projectId)
                .orderByDesc(Bom::getVersion)
                .last("LIMIT 1"));
        return latest != null ? latest.getVersion() : 1;
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
        // cancelled_at 必须显式置 null：updateById 会忽略 null 字段，
        // 残留的取消标记会让阶段护栏继续把项目当"已取消"，导致阶段永远推不动（死锁）
        projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                .eq(Project::getId, projectId)
                .set(Project::getStatus, ProjectStatus.IN_PROGRESS.getCode())
                .set(Project::getCancelledAt, null));
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
        // 改配信息（驱动IC/触摸IC/码片IC）同步写回项目 BOM 对应独立行
        syncConfigToBom(project);
        if (old != null
                && old.getAssemblyName() != null
                && !old.getAssemblyName().equals(project.getAssemblyName())) {
            projectProductSyncService.syncProductNameFromProject(
                    project.getId(), project.getAssemblyName());
        }
    }

    @Override
    public Map<Long, List<ProjectPhase>> batchPhases(List<Long> projectIds) {
        Map<Long, List<ProjectPhase>> result = new LinkedHashMap<>();
        if (projectIds == null || projectIds.isEmpty()) {
            return result;
        }
        // 一次 in 查询取回所有项目的项目阶段，避免循环内逐个查询（N+1）
        List<ProjectPhase> all = projectPhaseMapper.selectList(
                new LambdaQueryWrapper<ProjectPhase>().in(ProjectPhase::getProjectId, projectIds));
        return buildPhaseMap(all);
    }

    private Map<Long, List<ProjectPhase>> buildPhaseMap(List<ProjectPhase> all) {
        Map<Long, List<ProjectPhase>> map = new HashMap<>();
        for (ProjectPhase t : all) {
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
    public List<ProjectPhase> listByProject(Long projectId) {
        return projectPhaseService.listByProject(projectId);
    }
}
