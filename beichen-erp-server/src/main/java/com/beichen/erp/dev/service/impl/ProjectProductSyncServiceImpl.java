package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    @Override
    @Transactional
    public void syncProduct(Long projectId) {
        // 项目通过Product.projectId关联产品，同步产品信息（预留）
        log.info("同步产品信息: projectId={}", projectId);
    }

    @Override
    @Transactional
    public void syncProductStatus(Long projectId) {
        // 通过projectId查找关联的产品，将"研发中"改为"正常"
        Product product = productMapper.selectOne(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getProjectId, projectId));
        if (product == null) {
            log.info("未找到项目关联的产品: projectId={}", projectId);
            return;
        }
        if (ProductStatus.DEVELOPING.getValue().equals(product.getStatus().getValue())) {
            product.setStatus(ProductStatus.NORMAL);
            productMapper.updateById(product);
            log.info("同步产品状态为正常: projectId={}, productId={}, productName={}",
                    projectId, product.getId(), product.getName());
        }
    }
}
