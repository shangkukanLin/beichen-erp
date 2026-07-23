package com.beichen.erp.brand.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.brand.entity.Brand;
import com.beichen.erp.brand.mapper.BrandMapper;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/brand")
@RequiredArgsConstructor
public class BrandController {

    private final BrandMapper brandMapper;
    private final JdbcTemplate jdbcTemplate;

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
        Map<String, Object> check = checkDelete(id).getData();
        if (!(Boolean) check.get("canDelete")) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> associations = (Map<String, Integer>) check.get("associations");
            StringBuilder sb = new StringBuilder("该品牌有关联数据，无法删除：");
            associations.forEach((k, v) -> sb.append("\n  - ").append(k).append("：").append(v).append("条"));
            throw new BusinessException(sb.toString());
        }
        brandMapper.deleteById(id);
        return R.ok();
    }

    @GetMapping("/{id}/check-delete")
    public R<Map<String, Object>> checkDelete(@PathVariable Long id) {
        int materialCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE brand_id = ?", Integer.class, id);
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> associations = new LinkedHashMap<>();
        if (materialCount > 0) associations.put("物料", materialCount);
        result.put("canDelete", associations.isEmpty());
        result.put("associations", associations);
        return R.ok(result);
    }
}
