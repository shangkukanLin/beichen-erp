package com.beichen.erp.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
        } else {
            // 列表默认隐藏已停用品，避免停用品仍出现在查询列表
            w.ne(Product::getStatus, ProductStatus.DISCONTINUED);
        }
        w.orderByDesc(Product::getId);
        return this.page(new Page<>(pageNum, pageSize), w);
    }

    /** 停用成品（逻辑删除替代物理删除，避免库存/销售/采购等关联表变成孤儿数据） */
    public void discontinue(Long id) {
        LambdaUpdateWrapper<Product> u = new LambdaUpdateWrapper<>();
        u.eq(Product::getId, id).set(Product::getStatus, ProductStatus.DISCONTINUED);
        this.update(u);
    }
}
