package com.beichen.erp.brand.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.brand.entity.Brand;
import com.beichen.erp.brand.mapper.BrandMapper;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/brand")
@RequiredArgsConstructor
public class BrandController {

    private final BrandMapper brandMapper;

    /** 全部启用品牌（下拉用） */
    @GetMapping("/enabled")
    public R<List<Brand>> enabled() {
        return R.ok(brandMapper.selectList(new LambdaQueryWrapper<Brand>()
                .eq(Brand::getStatus, 1).orderByAsc(Brand::getId)));
    }

    @GetMapping("/page")
    public R<Page<Brand>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                @RequestParam(defaultValue = "20") Integer pageSize,
                                @RequestParam(required = false) String brandName) {
        return R.ok(brandMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Brand>()
                        .like(brandName != null && !brandName.isBlank(), Brand::getBrandName, brandName)
                        .orderByAsc(Brand::getId)));
    }

    @PostMapping
    public R<Void> add(@RequestBody Brand brand) {
        if (brandMapper.selectCount(new LambdaQueryWrapper<Brand>()
                .eq(Brand::getBrandName, brand.getBrandName())) > 0) {
            throw new BusinessException("品牌名称已存在");
        }
        brand.setStatus(brand.getStatus() != null ? brand.getStatus() : 1);
        brand.setCompanyId(CompanyContext.get());
        brandMapper.insert(brand);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody Brand brand) {
        brandMapper.updateById(brand);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        brandMapper.deleteById(id);
        return R.ok();
    }
}
