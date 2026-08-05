package com.beichen.erp.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.inventory.entity.InventoryStockLog;
import com.beichen.erp.inventory.entity.InventoryWarehouse;
import com.beichen.erp.inventory.entity.InventoryWarehouseStock;
import com.beichen.erp.inventory.mapper.InventoryStockLogMapper;
import com.beichen.erp.inventory.mapper.InventoryWarehouseMapper;
import com.beichen.erp.inventory.mapper.InventoryWarehouseStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/inventory/stock")
@RequiredArgsConstructor
public class InventoryStockController {

    private final InventoryWarehouseStockMapper stockMapper;
    private final InventoryWarehouseMapper warehouseMapper;
    private final InventoryStockLogMapper logMapper;

    /** 成品库存汇总查询（按产品+仓库 PIVOT 品质等级聚合） */
    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String qualityType) {
        // 查询所有符合条件的库存记录
        LambdaQueryWrapper<InventoryWarehouseStock> w = new LambdaQueryWrapper<InventoryWarehouseStock>()
                .eq(warehouseId != null, InventoryWarehouseStock::getWarehouseId, warehouseId)
                .eq(productId != null, InventoryWarehouseStock::getProductId, productId)
                .eq(qualityType != null && !qualityType.isBlank(), InventoryWarehouseStock::getQualityType, qualityType);
        List<InventoryWarehouseStock> all = stockMapper.selectList(w);

        // 按 (warehouseId, productId) 分组聚合
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (InventoryWarehouseStock s : all) {
            String key = s.getWarehouseId() + "_" + (s.getProductId() != null ? s.getProductId() : "0");
            Map<String, Object> row = grouped.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("warehouseId", s.getWarehouseId());
                m.put("productId", s.getProductId());
                m.put("productName", ""); // productName 列已删除，前端通过 productId 查名
                m.put("spec", "");
                m.put("qtyA", BigDecimal.ZERO);
                m.put("qtyB", BigDecimal.ZERO);
                m.put("qtyC", BigDecimal.ZERO);
                m.put("qtyDefect", BigDecimal.ZERO);
                if (s.getWarehouseId() != null) {
                    InventoryWarehouse wh = warehouseMapper.selectById(s.getWarehouseId());
                    m.put("warehouseName", wh != null ? wh.getWarehouseName() : "");
                }
                return m;
            });
            BigDecimal qty = s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
            String qt = s.getQualityType() != null ? s.getQualityType() : "A";
            switch (qt) {
                case "A": row.put("qtyA", ((BigDecimal) row.get("qtyA")).add(qty)); break;
                case "B": row.put("qtyB", ((BigDecimal) row.get("qtyB")).add(qty)); break;
                case "C": row.put("qtyC", ((BigDecimal) row.get("qtyC")).add(qty)); break;
                case "DEFECT": row.put("qtyDefect", ((BigDecimal) row.get("qtyDefect")).add(qty)); break;
            }
        }

        List<Map<String, Object>> records = new ArrayList<>(grouped.values());
        // 手动分页
        int total = records.size();
        int from = (pageNum - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> paged = from < total ? records.subList(from, to) : Collections.emptyList();

        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, total);
        res.setRecords(paged);
        return R.ok(res);
    }

    /** 全量库存流水追溯 */
    @GetMapping("/log")
    public R<Page<InventoryStockLog>> log(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) String relatedBillNo,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LambdaQueryWrapper<InventoryStockLog> w = new LambdaQueryWrapper<InventoryStockLog>()
                .eq(warehouseId != null, InventoryStockLog::getWarehouseId, warehouseId)
                .eq(productId != null, InventoryStockLog::getProductId, productId)
                .eq(changeType != null && !changeType.isBlank(), InventoryStockLog::getChangeType, changeType)
                .like(relatedBillNo != null && !relatedBillNo.isBlank(), InventoryStockLog::getRelatedBillNo, relatedBillNo)
                .ge(startDate != null && !startDate.isBlank(), InventoryStockLog::getCreateTime, startDate)
                .le(endDate != null && !endDate.isBlank(), InventoryStockLog::getCreateTime, endDate + " 23:59:59")
                .orderByDesc(InventoryStockLog::getId);
        return R.ok(logMapper.selectPage(new Page<>(pageNum, pageSize), w));
    }
}
