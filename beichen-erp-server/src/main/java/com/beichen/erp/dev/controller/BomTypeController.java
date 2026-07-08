package com.beichen.erp.dev.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dev/bom-type")
@RequiredArgsConstructor
public class BomTypeController {

    private final BomTypeMapper bomTypeMapper;

    @GetMapping("/enabled")
    public R<List<BomType>> enabled() {
        return R.ok(bomTypeMapper.selectList(new LambdaQueryWrapper<BomType>()
                .eq(BomType::getStatus, 1).orderByAsc(BomType::getSortOrder)));
    }

    @GetMapping("/page")
    public R<Page<BomType>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                  @RequestParam(defaultValue = "20") Integer pageSize) {
        return R.ok(bomTypeMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<BomType>().orderByAsc(BomType::getSortOrder)));
    }

    @PostMapping
    public R<Void> add(@RequestBody BomType type) {
        if (bomTypeMapper.selectCount(new LambdaQueryWrapper<BomType>()
                .eq(BomType::getTypeName, type.getTypeName())) > 0) {
            throw new BusinessException("类型名称已存在");
        }
        if (type.getStatus() == null) type.setStatus(1);
        if (type.getSortOrder() == null) type.setSortOrder(0);
        type.setCompanyId(CompanyContext.get());
        bomTypeMapper.insert(type);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody BomType type) {
        BomType old = bomTypeMapper.selectById(type.getId());
        if (old != null && ("码片IC".equals(old.getTypeName()) || "触摸IC".equals(old.getTypeName()))) {
            if (type.getTypeName() != null && !type.getTypeName().equals(old.getTypeName())) {
                throw new BusinessException("码片IC和触摸IC不可改名");
            }
        }
        bomTypeMapper.updateById(type);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        BomType bt = bomTypeMapper.selectById(id);
        if (bt != null && bt.getBuiltIn() != null && bt.getBuiltIn() == 1) {
            throw new BusinessException("内置BOM类型不可删除");
        }
        bomTypeMapper.deleteById(id);
        return R.ok();
    }
}
