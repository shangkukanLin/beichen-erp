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
import com.beichen.erp.exception.BusinessException;
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
import com.beichen.erp.outsource.common.OutsourceOrderStatus;
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
                    .or().like(Project::getProductName, keyword));
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

        // 根据项目「产品名称」生成/关联产品（若项目配置了产品名称）；
        // 2026-09-21：立项页指定的 productSku（默认 NS- 打头、可改）一并传入，作为新建产品的 SKU。
        projectProductSyncService.syncProduct(project.getId(), linkExistingProductId, project.getProductSku());

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
        // F7-90（2026-09-19）：下面三处自动创建的物料 / 子物料组成只在"有租户上下文"时显式写公司。
        // 超管模式（companyId = 0/null）下 MetaObjectHandler 的 strictInsertFill 也不会兜底，
        // 若显式 setCompanyId(CompanyContext.get()) 会把 company_id 落成 NULL ⇒ 该行对所有公司都查不到。
        final Long cid = CompanyContext.get();
        final boolean hasCid = cid != null && cid > 0;
        Map<String, Long> typeMap = materialTypeIdMap();
        Long paixianTypeId = typeMap.get("排线");
        Long touchTypeId = typeMap.get("触摸IC");
        Long codeTypeId = typeMap.get("码片IC");
        if (paixianTypeId == null) {
            // F7-94（2026-09-19）：原为静默 return —— 物料类型改名为别的叫法后，
            // 触摸IC/码片IC 的改配联动会整体失效却毫无提示（关联键是中文类型名）
            log.warn("未找到物料类型[排线]，触摸IC/码片IC 改配联动跳过: projectId={}", project.getId());
            return;
        }
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
                if (hasCid) mat.setCompanyId(cid);
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
            if (hasCid) n.setCompanyId(cid);
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
            if (hasCid) n.setCompanyId(cid);
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

    /**
     * 物料类型名 -> id 映射（改配联动用：驱动IC / 排线 / 触摸IC / 码片IC）。
     * <p>F7-91（2026-09-19）：① 改为按 id 升序扫描 + {@code putIfAbsent} —— 原 {@code put} 在出现同名
     * 类型时"最后一个胜出"，取值随插入顺序漂移（超管模式下租户插件不过滤时尤为明显）；
     * ② 命中同名类型记 {@code log.warn}，把"静默取错"变成"有迹可查"。</p>
     * <p>备注：本方法仍以**中文类型名**作关联键（沿用既有设计），彻底解决需要给类型加稳定编码列，
     * 见报告 §32-F7-91 的结构性建议。</p>
     */
    private Map<String, Long> materialTypeIdMap() {
        Map<String, Long> m = new HashMap<>();
        for (MaterialType t : materialTypeMapper.selectList(
                new LambdaQueryWrapper<MaterialType>().orderByAsc(MaterialType::getId))) {
            if (m.putIfAbsent(t.getTypeName(), t.getId()) != null) {
                log.warn("存在同名物料类型，改配联动固定取 id 较小的一条: typeName={}, ignoredId={}",
                        t.getTypeName(), t.getId());
            }
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
        if (project == null) throw new BusinessException("项目不存在");
        // F7-92（2026-09-19）：三处加固 ——
        // ① 先校验当前状态（原先对"已取消"的项目再取消会刷新 cancelled_at，掩盖真实取消时间）；
        // ② 校验下游单据：存在未完成的委外加工单时不允许取消（否则加工单会挂在一个已取消的项目上）；
        // ③ 用 CAS 更新替代"先查后改"，避免与 reactivate 并发时互相覆盖（后写者胜）。
        if (ProjectStatus.CANCELLED.getCode().equals(project.getStatus())) {
            throw new BusinessException("项目已取消");
        }
        Long running = countRunningOutsourceOrders(projectId);
        if (running != null && running > 0) {
            throw new BusinessException("该项目下还有 " + running + " 张未完成的委外加工单，请先结单或取消后再取消项目");
        }
        int updated = projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                .eq(Project::getId, projectId)
                .eq(Project::getStatus, project.getStatus())
                .set(Project::getStatus, ProjectStatus.CANCELLED.getCode())
                .set(Project::getCancelledAt, LocalDateTime.now()));
        if (updated == 0) throw new BusinessException("项目状态已被其他操作变更，请刷新后重试");
        log.info("项目已取消: projectId={}", projectId);
    }

    /**
     * 项目下"未完成"的委外加工单数量（F7-92）。
     * <p>关联方式与 {@link #getRelatedOrders} 一致：走 {@code outsource_order_product.project_id}
     * （而不是用 {@code remark LIKE} 那种展示字段串单）。</p>
     */
    private Long countRunningOutsourceOrders(Long projectId) {
        List<OutsourceOrderProduct> products = outsourceOrderProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderProduct>()
                        .eq(OutsourceOrderProduct::getProjectId, projectId));
        if (products.isEmpty()) return 0L;
        Set<Long> orderIds = products.stream().map(OutsourceOrderProduct::getOrderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (orderIds.isEmpty()) return 0L;
        return outsourceOrderMapper.selectCount(new LambdaQueryWrapper<OutsourceOrder>()
                .in(OutsourceOrder::getId, orderIds)
                .notIn(OutsourceOrder::getStatus,
                        OutsourceOrderStatus.FINISHED.getCode(),
                        OutsourceOrderStatus.CANCELLED.getCode()));
    }

    @Override
    @Transactional
    public void reactivate(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) return;
        // cancelled_at 必须显式置 null：updateById 会忽略 null 字段，
        // 残留的取消标记会让阶段护栏继续把项目当"已取消"，导致阶段永远推不动（死锁）
        // F7-139（2026-09-20）：与 {@link #cancel} 构成**对称的 CAS**。原为无条件 update ⇒
        // ① 与 cancel 并发时"后写者胜"（cancel 刚置 CANCELLED，reactivate 又改回 IN_PROGRESS，且不留痕）；
        // ② 对**进行中/已结项**的项目也会盲目改成 IN_PROGRESS 并清掉 cancelled_at。
        // 现限定"只有已取消的项目可激活"，并判影响行数。
        int updated = projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                .eq(Project::getId, projectId)
                .eq(Project::getStatus, ProjectStatus.CANCELLED.getCode())
                .set(Project::getStatus, ProjectStatus.IN_PROGRESS.getCode())
                .set(Project::getCancelledAt, null));
        if (updated == 0) throw new BusinessException("只有已取消的项目可以重新激活，请刷新后重试");
        log.info("项目已重新激活: projectId={}", projectId);
    }

    @Override
    public List<Project> listByStatus(String status) {
        LambdaQueryWrapper<Project> w = new LambdaQueryWrapper<>();
        w.eq(Project::getStatus, status);
        return projectMapper.selectList(w);
    }

    // ===== 内部方法 =====

    /**
     * 生成项目编号：{@code DEV-yyMMdd-三位序号}。
     * <p>F7-87（2026-09-19）：原实现用 {@code selectCount(当日前缀) + 1} 取号 —— 删除任意一条后
     * 下一个编号必然落在**已占用区间**（count 小于当日最大序号），并发/双击提交也会取到同一个
     * count；而 {@code dev_project.code} 过去没有唯一索引，重号可以直接落库。现改为「取当日
     * **最大**编号 + 1」（与同模块 {@code BugController.generateBugCode} 的正确写法一致），
     * 并配合 {@code uk_code} 唯一索引兜底（并发撞号时由数据库拦下，而不是写脏数据）。</p>
     */
    private String generateProjectCode() {
        String date = LocalDate.now().toString().replace("-", "").substring(2);
        String prefix = BillPrefix.DEV_PROJECT + date;
        Project last = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .likeRight(Project::getCode, prefix)
                .orderByDesc(Project::getCode)
                .last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getCode() != null) {
            // 取最后一个 '-' 之后的部分（比 substring(len-3) 更稳：序号超过 999 时不会截错）
            String numPart = last.getCode().substring(last.getCode().lastIndexOf('-') + 1);
            try {
                seq = Integer.parseInt(numPart) + 1;
            } catch (Exception e) {
                log.warn("项目编号序号解析失败，本次回退为 1: lastCode={}", last.getCode());
                seq = 1;
            }
        }
        return prefix + "-" + String.format("%03d", seq);
    }

    /**
     * 更新项目基础信息（**白名单字段更新**）。
     * <p>F7-89（2026-09-19）：原实现是 {@code updateById(project)}（整实体）—— {@code project}
     * 直接来自 {@code @RequestBody}，前端编辑页会把表单里的 {@code status} 一并回填并提交，
     * 于是**陈旧的 status 会覆盖状态机推导出来的值**；一旦出现"status 被改回 IN_PROGRESS、
     * cancelled_at 仍有残留"的组合，{@code syncProjectStatus} 会永久 return，而阶段护栏按
     * status 判定 ⇒ 阶段全部完成后项目**永不结项**（静默死锁）。</p>
     * <p>现改为只写入"允许编辑"的字段：{@code status}/{@code cancelledAt}/{@code code}/
     * {@code productId}/{@code companyId}/{@code actualEndDate}/{@code createTime} 等派生或系统
     * 字段一律不落库。这里借用 MyBatis-Plus 默认的 {@code NOT_NULL} 更新策略：未赋值的字段不会
     * 出现在 UPDATE 语句里，因此"前端未提交该字段" == "保持原值"。</p>
     */
    @Override
    @Transactional
    public void updateProject(Project project) {
        Project old = projectMapper.selectById(project.getId());
        if (old == null) throw new BusinessException("项目不存在");

        Project patch = new Project();
        patch.setId(project.getId());
        // ↓ 只允许这些字段被编辑（需与前端 edit.vue 的表单字段保持一致）
        patch.setName(project.getName());
        // 2026-09-21：assemblyName 已更名为 productName（DB 列 assembly_name → product_name）
        patch.setProductName(project.getProductName());
        // 2026-09-21 新增：立项「规格」（原配/改配），与关联产品联动；空白视为"未提交"以免覆盖成空串
        if (project.getSpecType() != null && !project.getSpecType().isBlank()) {
            patch.setSpecType(project.getSpecType().trim());
        }
        patch.setBrandId(project.getBrandId());
        patch.setDisplaySupplierName(project.getDisplaySupplierName());
        patch.setTouchSupplierName(project.getTouchSupplierName());
        patch.setAdaptModel(project.getAdaptModel());
        patch.setOriginalSize(project.getOriginalSize());
        patch.setOriginalResolution(project.getOriginalResolution());
        patch.setOriginalDriveIc(project.getOriginalDriveIc());
        patch.setOriginalTouchIc(project.getOriginalTouchIc());
        patch.setGlassSize(project.getGlassSize());
        patch.setGlassResolution(project.getGlassResolution());
        patch.setConfigDriveIcId(project.getConfigDriveIcId());
        patch.setConfigTouchIcId(project.getConfigTouchIcId());
        patch.setConfigCodeIcId(project.getConfigCodeIcId());
        patch.setSampleFactoryId(project.getSampleFactoryId());
        patch.setOutsourceFactoryId(project.getOutsourceFactoryId());
        patch.setProjectLeaderId(project.getProjectLeaderId());
        patch.setStartDate(project.getStartDate());
        patch.setExpectedEndDate(project.getExpectedEndDate());
        patch.setRemark(project.getRemark());
        projectMapper.updateById(patch);

        // 改配信息（驱动IC/触摸IC/码片IC）同步写回项目 BOM 对应独立行
        syncConfigToBom(project);
        // 产品名称变更时，同步改名关联产品，确保两处名称一致
        if (old.getProductName() != null
                && !old.getProductName().equals(project.getProductName())) {
            projectProductSyncService.syncProductNameFromProject(
                    project.getId(), project.getProductName());
        }
        // 2026-09-21（需求「规格要和产品的规格联动」）：立项详细页改规格时同步到关联产品。
        // 项目规格为空时**不动产品**（syncProductSpecFromProject 内部已兜底），避免误清产品已有规格。
        projectProductSyncService.syncProductSpecFromProject(project.getId());
        // 2026-09-21（需求 1）：立项详细页也允许改「产品SKU」（前端已二次确认）。只在确有变化时写。
        projectProductSyncService.syncProductSkuFromProject(project.getId(), project.getProductSku());
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
