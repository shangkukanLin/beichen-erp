package com.beichen.erp.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.inventory.entity.InventoryWarehouseMove;
import com.beichen.erp.inventory.entity.InventoryWarehouseMoveItem;

import java.util.List;
import java.util.Map;

public interface WarehouseMoveService {

    Page<Map<String, Object>> page(String status, Long fromWarehouseId, Long toWarehouseId, int pageNum, int pageSize);

    InventoryWarehouseMove getById(Long id);

    List<InventoryWarehouseMoveItem> getItems(Long moveId);

    void create(InventoryWarehouseMove move, List<InventoryWarehouseMoveItem> items);

    void update(InventoryWarehouseMove move, List<InventoryWarehouseMoveItem> items);

    void cancel(Long id);

    void audit(Long id);

    void unAudit(Long id);
}
