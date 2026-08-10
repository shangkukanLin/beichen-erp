package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.service.BomTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dev/bom-type")
@RequiredArgsConstructor
public class BomTypeController {

    private final BomTypeService bomTypeService;

    @GetMapping("/enabled")
    public R<List<BomType>> enabled() {
        return R.ok(bomTypeService.enabled());
    }

    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") Integer pageNum,
                     @RequestParam(defaultValue = "20") Integer pageSize) {
        return R.ok(bomTypeService.page(pageNum, pageSize));
    }

    @PostMapping
    public R<Void> add(@RequestBody BomType type) {
        bomTypeService.add(type);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody BomType type) {
        bomTypeService.update(type);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        bomTypeService.delete(id);
        return R.ok();
    }
}
