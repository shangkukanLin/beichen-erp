package com.beichen.erp.dev.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/dev/bom-type")
@RequiredArgsConstructor
public class BomTypeController {

    private final BomTypeMapper bomTypeMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构建带公司过滤的条件构造器。
     * 登录时已选定公司(session 存 companyId)，直接用它过滤。
     * 超管验证接口(/api/company/admin/verify)设置 companyId=0 用于跨公司管理，
     * 此处对 0 不加过滤（仅 /company-manage 页使用该流程）。
     */
    private LambdaQueryWrapper<BomType> buildWrapper() {
        Long companyId = CompanyContext.get();
        LambdaQueryWrapper<BomType> wrapper = new LambdaQueryWrapper<>();
        if (companyId != null && companyId > 0) {
            wrapper.eq(BomType::getCompanyId, companyId);
        }
        return wrapper;
    }

    @GetMapping("/enabled")
    public R<List<BomType>> enabled() {
        return R.ok(bomTypeMapper.selectList(buildWrapper()
                .eq(BomType::getStatus, 1).orderByAsc(BomType::getSortOrder)));
    }

    @GetMapping("/page")
    public R<Page<BomType>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                  @RequestParam(defaultValue = "20") Integer pageSize) {
        return R.ok(bomTypeMapper.selectPage(new Page<>(pageNum, pageSize),
                buildWrapper().orderByAsc(BomType::getSortOrder)));
    }

    @PostMapping
    public R<Void> add(@RequestBody BomType type) {
        // 同一公司内类型名称不可重复
        if (bomTypeMapper.selectCount(buildWrapper()
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
        // 更新BOM类型表（物料仅存 bom_type_id 指向类型，类型改名不影响 ID，无需同步物料）
        bomTypeMapper.updateById(type);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        BomType type = bomTypeMapper.selectById(id);
        if (type != null) {
            // 按 bom_type_id 统计该类型下是否还有外协物料
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outsource_material WHERE bom_type_id = ?",
                Long.class, type.getId());
            if (count != null && count > 0) {
                throw new BusinessException("该类型下还有 " + count + " 个物料，请先处理后再删除");
            }
        }
        bomTypeMapper.deleteById(id);
        return R.ok();
    }
}
