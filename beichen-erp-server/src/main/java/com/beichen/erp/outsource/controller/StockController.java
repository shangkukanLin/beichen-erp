package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.R;
import com.beichen.erp.outsource.entity.*;
import com.beichen.erp.outsource.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/outsource/stock")
@RequiredArgsConstructor
public class StockController {

    private final OutsourceWarehouseStockMapper stockMapper;
    private final OutsourceMaterialMapper materialMapper;
    private final OutsourceDeliveryMapper deliveryMapper;
    private final OutsourceDeliveryItemMapper itemMapper;

    @GetMapping("/by-warehouse/{warehouseId}")
    public R<List<Map<String, Object>>> byWarehouse(@PathVariable Long warehouseId) {
        List<OutsourceWarehouseStock> stocks = stockMapper.selectList(
                new LambdaQueryWrapper<OutsourceWarehouseStock>()
                        .eq(OutsourceWarehouseStock::getWarehouseId, warehouseId)
                        .gt(OutsourceWarehouseStock::getQuantity, BigDecimal.ZERO));
        return R.ok(stocks.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("materialId", s.getMaterialId());
            if (s.getMaterialId() != null) {
                OutsourceMaterial mat = materialMapper.selectById(s.getMaterialId());
                map.put("materialName", mat != null ? mat.getMaterialName() : "-");
                map.put("materialType", mat != null ? mat.getMaterialType() : "-");
            }
            map.put("quantity", s.getQuantity());
            return map;
        }).collect(Collectors.toList()));
    }

    @GetMapping("/history")
    public R<Map<String, Object>> history(
            @RequestParam Long warehouseId, @RequestParam Long materialId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        // 找出所有包含该materialId的item（不过滤delivery状态，包括已取消的）
        List<OutsourceDeliveryItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OutsourceDeliveryItem>()
                        .eq(OutsourceDeliveryItem::getMaterialId, materialId));
        List<Map<String, Object>> allRecords = new ArrayList<>();
        for (OutsourceDeliveryItem item : items) {
            OutsourceDelivery d = deliveryMapper.selectById(item.getDeliveryId());
            if (d == null) continue;
            if (!warehouseId.equals(d.getFromWarehouseId()) && !warehouseId.equals(d.getToWarehouseId())) continue;
            // 时间段筛选
            if (startDate != null && !startDate.isBlank() && d.getDeliveryDate() != null && d.getDeliveryDate().isBefore(LocalDate.parse(startDate))) continue;
            if (endDate != null && !endDate.isBlank() && d.getDeliveryDate() != null && d.getDeliveryDate().isAfter(LocalDate.parse(endDate))) continue;
            Map<String, Object> map = new HashMap<>();
            map.put("deliveryId", d.getId());
            map.put("code", d.getCode());
            map.put("deliveryType", d.getDeliveryType());
            map.put("deliveryDate", d.getDeliveryDate());
            map.put("status", d.getStatus());
            map.put("quantity", item.getQuantity());
            map.put("materialName", item.getMaterialName());
            map.put("materialType", item.getMaterialType());
            map.put("unit", item.getUnit());
            allRecords.add(map);
        }
        allRecords.sort((a,b) -> b.get("deliveryDate").toString().compareTo(a.get("deliveryDate").toString()));
        // 手动分页
        int total = allRecords.size();
        int from = (pageNum - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> pageRecords = from < total ? allRecords.subList(from, to) : new ArrayList<>();
        Map<String, Object> result = new HashMap<>();
        result.put("records", pageRecords);
        result.put("total", total);
        return R.ok(result);
    }
}
