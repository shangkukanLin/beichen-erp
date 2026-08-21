package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.dev.service.ProjectProductSyncService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.material.common.ProductStatus;
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

    @Override
    @Transactional
    public void syncProduct(Long projectId, Long linkExistingProductId) {
        Project project = projectMapper.selectById(projectId);
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

        // 2. 未传关联产品 → 根据总成名称新建产品
        String assemblyName = project.getAssemblyName();
        if (assemblyName == null || assemblyName.isBlank()) {
            log.info("项目无总成名称，不创建产品: projectId={}", projectId);
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
        product.setName(assemblyName);
        product.setProjectId(projectId);
        product.setStatus(ProductStatus.DEVELOPING);
        product.setUnit("pcs");
        product.setSafetyStock(java.math.BigDecimal.ZERO);
        productMapper.insert(product);

        // 回写项目 product_id
        project.setProductId(product.getId());
        projectMapper.updateById(project);
        log.info("根据总成名称创建产品: projectId={}, productId={}, productName={}",
                projectId, product.getId(), product.getName());
    }

    @Override
    @Transactional
    public void syncProductNameFromProject(Long projectId, String newAssemblyName) {
        if (projectId == null) return;
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getProductId() == null) return;
        Product product = productMapper.selectById(project.getProductId());
        if (product == null) return;
        if (newAssemblyName != null && !newAssemblyName.equals(product.getName())) {
            product.setName(newAssemblyName);
            productMapper.updateById(product);
            log.info("项目总成名称变更同步更新产品名称: projectId={}, productId={}, newName={}",
                    projectId, product.getId(), newAssemblyName);
        }
    }

    @Override
    @Transactional
    public void syncAssemblyNameFromProduct(Long productId, String newName) {
        if (productId == null) return;
        Product product = productMapper.selectById(productId);
        if (product == null || product.getProjectId() == null) return;
        Project project = projectMapper.selectById(product.getProjectId());
        if (project == null) return;
        if (newName != null && !newName.equals(project.getAssemblyName())) {
            project.setAssemblyName(newName);
            projectMapper.updateById(project);
            log.info("产品名称变更同步更新项目总成名称: productId={}, projectId={}, newAssemblyName={}",
                    productId, project.getId(), newName);
        }
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
            productMapper.updateById(product);
            log.info("同步产品状态为正常: projectId={}, productId={}, productName={}",
                    projectId, product.getId(), product.getName());
        }
    }
}
