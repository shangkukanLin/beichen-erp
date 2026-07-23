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

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/stock")
@RequiredArgsConstructor
public class InventoryStockController {

    private final InventoryWarehouseStockMapper stockMapper;
    private final InventoryWarehouseMapper warehouseMapper;
    private final InventoryStockLogMapper logMapper;

    /** 成品库存汇总查询 */
    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String productName) {
        LambdaQueryWrapper<InventoryWarehouseStock> w = new LambdaQueryWrapper<InventoryWarehouseStock>()
                .eq(warehouseId != null, InventoryWarehouseStock::getWarehouseId, warehouseId)
                .like(productName != null && !productName.isBlank(), InventoryWarehouseStock::getProductName, productName)
                .orderByDesc(InventoryWarehouseStock::getId);
        Page<InventoryWarehouseStock> raw = stockMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("warehouseId", s.getWarehouseId());
            m.put("productName", s.getProductName());
            m.put("materialId", s.getProductId());
            m.put("quantity", s.getQuantity());
            m.put("availableQuantity", s.getAvailableQuantity());
            if (s.getWarehouseId() != null) {
                InventoryWarehouse wh = warehouseMapper.selectById(s.getWarehouseId());
                m.put("warehouseName", wh != null ? wh.getWarehouseName() : "");
            }
            return m;
        }).toList());
        return R.ok(res);
    }

    /** 全量库存流水追溯 */
    @GetMapping("/log")
    public R<Page<InventoryStockLog>> log(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) String relatedBillNo,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LambdaQueryWrapper<InventoryStockLog> w = new LambdaQueryWrapper<InventoryStockLog>()
                .eq(warehouseId != null, InventoryStockLog::getWarehouseId, warehouseId)
                .like(materialName != null && !materialName.isBlank(), InventoryStockLog::getMaterialName, materialName)
                .eq(changeType != null && !changeType.isBlank(), InventoryStockLog::getChangeType, changeType)
                .like(relatedBillNo != null && !relatedBillNo.isBlank(), InventoryStockLog::getRelatedBillNo, relatedBillNo)
                .ge(startDate != null && !startDate.isBlank(), InventoryStockLog::getCreateTime, startDate)
                .le(endDate != null && !endDate.isBlank(), InventoryStockLog::getCreateTime, endDate + " 23:59:59")
                .orderByDesc(InventoryStockLog::getId);
        return R.ok(logMapper.selectPage(new Page<>(pageNum, pageSize), w));
    }
}
