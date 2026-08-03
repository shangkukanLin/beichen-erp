package com.beichen.erp.dev.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.Bom;
import com.beichen.erp.dev.service.BomService;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dev/bom")
@RequiredArgsConstructor
public class BomManageController {

    private final BomService bomService;
    private final OutsourceMaterialMapper outsourceMaterialMapper;

    /** BOM分页查询（仪表盘用） */
    @GetMapping("/page")
    public R<Page<Bom>> page(PageParam param) {
        return R.ok(bomService.lambdaQuery()
                .orderByDesc(Bom::getId)
                .page(new Page<>(param.getPageNum(), param.getPageSize())));
    }

    /** 获取所有有BOM的项目列表（带物料名称） */
    @GetMapping("/projects")
    public R<List<Map<String, Object>>> projectsWithBom() {
        List<Bom> allBoms = bomService.list();

        Map<Long, Long> projectBomCount = allBoms.stream()
                .collect(Collectors.groupingBy(Bom::getProjectId, Collectors.counting()));

        Set<Long> materialIds = allBoms.stream()
                .map(Bom::getOutsourceMaterialId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> materialNameMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            List<OutsourceMaterial> materials = outsourceMaterialMapper.selectBatchIds(materialIds);
            for (OutsourceMaterial m : materials) {
                materialNameMap.put(m.getId(), m.getMaterialName());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : projectBomCount.entrySet()) {
            Map<String, Object> map = new HashMap<>();
            map.put("projectId", entry.getKey());
            map.put("bomCount", entry.getValue());

            List<String> materialNames = allBoms.stream()
                    .filter(b -> b.getProjectId().equals(entry.getKey())
                            && b.getOutsourceMaterialId() != null)
                    .map(b -> materialNameMap.getOrDefault(b.getOutsourceMaterialId(), ""))
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
            map.put("materialNames", materialNames);
            result.add(map);
        }
        return R.ok(result);
    }
}
