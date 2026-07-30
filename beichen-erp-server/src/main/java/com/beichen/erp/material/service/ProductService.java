package com.beichen.erp.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.entity.ProductQuality;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.material.mapper.ProductQualityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService extends ServiceImpl<ProductMapper, Product> {

    private final ProductQualityMapper qualityMapper;

    public Page<Product> page(String keyword, String category, int pageNum, int pageSize) {
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(Product::getName, keyword).or().like(Product::getCode, keyword));
        }
        if (StringUtils.hasText(category)) {
            w.eq(Product::getCategory, category);
        }
        w.orderByDesc(Product::getId);
        Page<Product> page = this.page(new Page<>(pageNum, pageSize), w);
        // 联查等级库存
        fillQualities(page.getRecords());
        return page;
    }

    public Product getById(Long id) {
        Product p = super.getById(id);
        if (p != null) fillQualities(Collections.singletonList(p));
        return p;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Product entity) {
        // 先保存产品
        boolean ok = super.save(entity);
        // 保存等级库存
        saveQualities(entity);
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(Product entity) {
        boolean ok = super.updateById(entity);
        saveQualities(entity);
        return ok;
    }

    private void saveQualities(Product product) {
        if (product.getId() == null || product.getQualities() == null) return;
        // 删除旧的
        qualityMapper.delete(new LambdaQueryWrapper<ProductQuality>()
                .eq(ProductQuality::getProductId, product.getId()));
        for (ProductQuality q : product.getQualities()) {
            q.setProductId(product.getId());
            q.setId(null);
        }
        // 批量插入
        for (ProductQuality q : product.getQualities()) {
            qualityMapper.insert(q);
        }
    }

    private void fillQualities(List<Product> products) {
        if (products.isEmpty()) return;
        List<Long> ids = products.stream().map(Product::getId).collect(Collectors.toList());
        List<ProductQuality> all = qualityMapper.selectList(
                new LambdaQueryWrapper<ProductQuality>().in(ProductQuality::getProductId, ids));
        Map<Long, List<ProductQuality>> map = all.stream()
                .collect(Collectors.groupingBy(ProductQuality::getProductId));
        for (Product p : products) {
            p.setQualities(map.getOrDefault(p.getId(), Collections.emptyList()));
        }
    }
}
