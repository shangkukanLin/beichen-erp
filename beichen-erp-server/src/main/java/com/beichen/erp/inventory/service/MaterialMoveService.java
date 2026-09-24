package com.beichen.erp.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.inventory.entity.InventoryMaterialMove;
import com.beichen.erp.inventory.entity.InventoryMaterialMoveItem;

import java.util.List;
import java.util.Map;

/**
 * 物料移仓单（2026-09-24 新增）—— 按成品移仓单同构复刻，用于替代原「物料收发单」的手工发料/调拨。
 */
public interface MaterialMoveService {

    Page<Map<String, Object>> page(String status, Long fromWarehouseId, Long toWarehouseId, int pageNum, int pageSize);

    InventoryMaterialMove getById(Long id);

    List<InventoryMaterialMoveItem> getItems(Long moveId);

    void create(InventoryMaterialMove move, List<InventoryMaterialMoveItem> items);

    void update(InventoryMaterialMove move, List<InventoryMaterialMoveItem> items);

    void cancel(Long id);

    void audit(Long id);

    void unAudit(Long id);
}
