package com.beichen.erp.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.material.common.ProductStatus;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductService extends ServiceImpl<ProductMapper, Product> {

    public Page<Product> page(String keyword, String category, ProductStatus status, int pageNum, int pageSize) {
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.like(Product::getName, keyword);
        }
        if (StringUtils.hasText(category)) {
            w.eq(Product::getCategory, category);
        }
        if (status != null) {
            w.eq(Product::getStatus, status);
        }
        w.orderByDesc(Product::getId);
        return this.page(new Page<>(pageNum, pageSize), w);
    }
}
