package com.beichen.erp.dev.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.PhaseTemplate;
import com.beichen.erp.dev.mapper.PhaseTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dev/phase-template")
@RequiredArgsConstructor
public class PhaseTemplateController {

    private final PhaseTemplateMapper mapper;

    @GetMapping("/page")
    public R<Page<PhaseTemplate>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "100") int pageSize) {
        return R.ok(mapper.selectPage(new Page<>(pageNum, pageSize),
            new LambdaQueryWrapper<PhaseTemplate>().orderByAsc(PhaseTemplate::getSortOrder)));
    }

    @GetMapping("/list")
    public R<List<PhaseTemplate>> list() {
        return R.ok(mapper.selectList(
            new LambdaQueryWrapper<PhaseTemplate>().orderByAsc(PhaseTemplate::getSortOrder)));
    }

    @PostMapping
    public R<PhaseTemplate> create(@RequestBody PhaseTemplate t) {
        if (t.getCompanyId() == null) t.setCompanyId(CompanyContext.get());
        mapper.insert(t);
        return R.ok(t);
    }

    @PutMapping
    public R<Void> update(@RequestBody PhaseTemplate t) {
        mapper.updateById(t);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return R.ok();
    }
}
