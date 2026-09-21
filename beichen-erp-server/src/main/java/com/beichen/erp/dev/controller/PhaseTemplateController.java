package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.PhaseTemplate;
import com.beichen.erp.dev.service.PhaseTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dev/phase-template")
@RequiredArgsConstructor
public class PhaseTemplateController {

    private final PhaseTemplateService service;

    /**
     * 分页查询阶段模板。
     *
     * @param specType 适用规格（MATCHED 原配 / MODIFIED 改配）；**不传则返回两套**（向后兼容）
     */
    @GetMapping("/page")
    public R<?> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(required = false) String specType) {
        return R.ok(service.page(pageNum, pageSize, specType));
    }

    /**
     * 阶段模板列表。
     *
     * @param specType 适用规格（MATCHED 原配 / MODIFIED 改配）；**不传则返回两套**（向后兼容）
     */
    @GetMapping("/list")
    public R<List<PhaseTemplate>> list(@RequestParam(required = false) String specType) {
        return R.ok(service.list(specType));
    }

    @PostMapping
    public R<PhaseTemplate> create(@RequestBody PhaseTemplate t) {
        return R.ok(service.create(t));
    }

    @PutMapping
    public R<Void> update(@RequestBody PhaseTemplate t) {
        service.update(t);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
