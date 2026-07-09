package com.beichen.erp.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.inventory.entity.InventoryStockTake;
import com.beichen.erp.inventory.entity.InventoryStockTakeItem;

import java.util.List;
import java.util.Map;

public interface StockTakeService {

    Page<Map<String, Object>> page(String status, Long warehouseId, String code, int pageNum, int pageSize);

    InventoryStockTake getById(Long id);

    List<InventoryStockTakeItem> getItems(Long takeId);

    void create(InventoryStockTake take, List<InventoryStockTakeItem> items);

    void update(InventoryStockTake take, List<InventoryStockTakeItem> items);

    void cancel(Long id);

    void audit(Long id);
}
