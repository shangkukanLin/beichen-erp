package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.dev.service.ProjectProductSyncService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.material.common.ProductStatus;
import com.beichen.erp.material.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectProductSyncServiceImpl implements ProjectProductSyncService {

    private final ProductMapper productMapper;
    private final ProjectMapper projectMapper;
    /** 写产品走 Service（save/updateById 内统一生成/补全 SKU），不要直接 productMapper.insert */
    private final ProductService productService;

    @Override
    @Transactional
    public void syncProduct(Long projectId, Long linkExistingProductId, String productSku) {
        // F7-139（2026-09-20）：**项目行锁**。下面第 2 段的"幂等查重"是"按 projectId 查产品 → 查不到就新建"
        // （典型先查后写）：并发（双击/重试）时两个请求都查不到 ⇒ **同一项目挂出两条产品**，
        // 且 `project.product_id` 后写者胜。在项目行上串行即可闭合（带租户条件，不绕过租户过滤）。
        Long lockCid = com.beichen.erp.config.CompanyContext.get();
        if (lockCid != null && lockCid <= 0) lockCid = null;
        Project project = projectMapper.selectForUpdate(projectId, lockCid);
        if (project == null) {
            log.info("项目不存在: projectId={}", projectId);
            return;
        }

        // 1. 如果传了要关联的已有产品
        if (linkExistingProductId != null) {
            Product existing = productMapper.selectById(linkExistingProductId);
            if (existing == null) {
                log.warn("要关联的产品不存在: productId={}", linkExistingProductId);
                return;
            }
            existing.setProjectId(projectId);
            if (existing.getStatus() == null) existing.setStatus(ProductStatus.NORMAL);
            productMapper.updateById(existing);
            // 回写项目 product_id
            project.setProductId(existing.getId());
            projectMapper.updateById(project);
            log.info("项目关联已有产品: projectId={}, productId={}, productName={}",
                    projectId, existing.getId(), existing.getName());
            return;
        }

        // 2. 未传关联产品 → 根据项目「产品名称」新建产品
        String productName = project.getProductName();
        if (productName == null || productName.isBlank()) {
            log.info("项目无产品名称，不创建产品: projectId={}", projectId);
            return;
        }

        // 幂等：按 projectId 查是否已有关联产品
        Product existByProject = productMapper.selectOne(
                new LambdaQueryWrapper<Product>().eq(Product::getProjectId, projectId));
        if (existByProject != null) {
            log.info("项目已有关联产品，跳过创建: projectId={}, productId={}", projectId, existByProject.getId());
            return;
        }

        Product product = new Product();
        product.setName(productName);
        product.setProjectId(projectId);
        product.setStatus(ProductStatus.DEVELOPING);
        product.setUnit("pcs");
        product.setSafetyStock(java.math.BigDecimal.ZERO);
        // 2026-09-21（立项规格，需求「规格要和产品的规格联动」）：立项选的原配/改配直接落到产品上。
        // 产品管理的"规格必填"校验只在 ProductController 入口，走 Service 不会冲突；
        // 项目规格为空（老项目）时产品规格也保持为空，与既有行为一致。
        product.setSpecType(project.getSpecType());
        // 2026-09-21（产品SKU，需求「默认自动生成可修改，以 NS 打头」）：立项页可指定 SKU。
        // 传空 ⇒ 交给 ProductService.save 按前缀自动生成；传值 ⇒ 由其校验唯一性后采用。
        if (productSku != null && !productSku.isBlank()) {
            product.setSku(productSku.trim());
        }
        // 走 ProductService.save：SKU 统一由服务层处理（ProductMapper.insert 会绕过该逻辑导致 SKU 为空）
        productService.save(product);

        // 回写项目 product_id
        project.setProductId(product.getId());
        projectMapper.updateById(project);
        log.info("根据总成名称创建产品: projectId={}, productId={}, productName={}",
                projectId, product.getId(), product.getName());
    }

    @Override
    @Transactional
    public void syncProductNameFromProject(Long projectId, String newProductName) {
        if (projectId == null) return;
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getProductId() == null) return;
        Product product = productMapper.selectById(project.getProductId());
        if (product == null) return;
        if (newProductName != null && !newProductName.equals(product.getName())) {
            product.setName(newProductName);
            // 走 Service：历史产品 SKU 缺失时 updateById 会兜底补生成
            productService.updateById(product);
            log.info("项目产品名称变更同步更新产品名称: projectId={}, productId={}, newName={}",
                    projectId, product.getId(), newProductName);
        }
    }

    @Override
    @Transactional
    public void syncProductNameToProject(Long productId, String newName) {
        if (productId == null) return;
        Product product = productMapper.selectById(productId);
        if (product == null || product.getProjectId() == null) return;
        Project project = projectMapper.selectById(product.getProjectId());
        if (project == null) return;
        if (newName != null && !newName.equals(project.getProductName())) {
            project.setProductName(newName);
            projectMapper.updateById(project);
            log.info("产品名称变更同步更新项目产品名称: productId={}, projectId={}, newName={}",
                    productId, project.getId(), newName);
        }
    }

    /**
     * 同步项目「规格」到关联产品（2026-09-21 需求："这个字段要和产品的规格联动"）。
     * <p>项目规格为空（历史项目 / 立项前的旧数据）时**不动产品**，避免把产品上已填好的规格清掉。</p>
     */
    @Override
    @Transactional
    public void syncProductSpecFromProject(Long projectId) {
        if (projectId == null) return;
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getProductId() == null) return;
        String specType = project.getSpecType();
        if (specType == null || specType.isBlank()) return;
        Product product = productMapper.selectById(project.getProductId());
        if (product == null || specType.equals(product.getSpecType())) return;
        product.setSpecType(specType);
        // 走 Service：历史产品 SKU 缺失时 updateById 会兜底补生成
        productService.updateById(product);
        log.info("项目规格变更同步更新产品规格: projectId={}, productId={}, specType={}",
                projectId, product.getId(), specType);
    }

    /**
     * 同步立项详细页填写的「产品SKU」到关联产品（2026-09-21 需求 1）。
     * <p>刻意的保守口径：**未关联产品、或传入为空 ⇒ 什么都不做**（不清空产品 SKU），
     * 避免列表页/其它调用方不带该字段时把产品的 SKU 抹掉。</p>
     */
    @Override
    @Transactional
    public void syncProductSkuFromProject(Long projectId, String productSku) {
        if (projectId == null || productSku == null || productSku.isBlank()) return;
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getProductId() == null) return;
        Product product = productMapper.selectById(project.getProductId());
        if (product == null) return;
        String target = productSku.trim();
        if (target.equals(product.getSku())) return;
        product.setSku(target);
        // 走 Service：内部做公司内唯一校验（重复明确报错），并在历史 SKU 缺失时兜底生成
        productService.updateById(product);
        log.info("项目页修改产品SKU: projectId={}, productId={}, sku={}", projectId, product.getId(), target);
    }

    @Override
    @Transactional
    public void syncProductStatus(Long projectId) {
        // 通过projectId查找关联的产品，将"研发中"改为"正常"
        Product product = productMapper.selectOne(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getProjectId, projectId));
        // 兜底：若产品.projectId 未回写，则按项目.productId 反查关联产品
        if (product == null) {
            Project project = projectMapper.selectById(projectId);
            if (project != null && project.getProductId() != null) {
                product = productMapper.selectById(project.getProductId());
            }
        }
        if (product == null) {
            log.info("未找到项目关联的产品: projectId={}", projectId);
            return;
        }
        // 兼容存量产品 status 为 null 的情况，避免 getValue() 时空指针
        if (product.getStatus() != null
                && ProductStatus.DEVELOPING.getValue().equals(product.getStatus().getValue())) {
            product.setStatus(ProductStatus.NORMAL);
            // 走 Service：历史产品 SKU 缺失时 updateById 会兜底补生成
            productService.updateById(product);
            log.info("同步产品状态为正常: projectId={}, productId={}, productName={}",
                    projectId, product.getId(), product.getName());
        }
    }
}
