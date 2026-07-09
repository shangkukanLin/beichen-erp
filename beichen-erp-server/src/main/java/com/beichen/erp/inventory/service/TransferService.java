package com.beichen.erp.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.inventory.entity.InventoryTransfer;
import com.beichen.erp.inventory.entity.InventoryTransferItem;

import java.util.List;
import java.util.Map;

public interface TransferService {

    Page<Map<String, Object>> page(String status, Long fromWarehouseId, Long toWarehouseId, int pageNum, int pageSize);

    InventoryTransfer getById(Long id);

    List<InventoryTransferItem> getItems(Long transferId);

    void create(InventoryTransfer transfer, List<InventoryTransferItem> items);

    void update(InventoryTransfer transfer, List<InventoryTransferItem> items);

    void cancel(Long id);

    void audit(Long id);
}
