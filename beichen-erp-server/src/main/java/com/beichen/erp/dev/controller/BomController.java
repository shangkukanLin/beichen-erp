package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.Bom;
import com.beichen.erp.dev.entity.dto.BomDTO;
import com.beichen.erp.dev.service.BomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dev/project/{projectId}/bom")
@RequiredArgsConstructor
public class BomController {

    private final BomService bomService;

    @GetMapping
    public R<List<Bom>> list(@PathVariable Long projectId) {
        return R.ok(bomService.listByProject(projectId));
    }

    @PutMapping
    public R<Void> save(@PathVariable Long projectId, @RequestBody List<BomDTO> items) {
        bomService.saveItems(projectId, items);
        return R.ok();
    }
}
