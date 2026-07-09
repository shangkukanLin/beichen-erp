package com.beichen.erp.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.inventory.entity.InventoryOtherIo;
import com.beichen.erp.inventory.entity.InventoryOtherIoItem;

import java.util.List;
import java.util.Map;

public interface OtherIoService {

    Page<Map<String, Object>> page(String status, Long warehouseId, String ioType, int pageNum, int pageSize);

    InventoryOtherIo getById(Long id);

    List<InventoryOtherIoItem> getItems(Long otherIoId);

    void create(InventoryOtherIo otherIo, List<InventoryOtherIoItem> items);

    void update(InventoryOtherIo otherIo, List<InventoryOtherIoItem> items);

    void cancel(Long id);

    void audit(Long id);
}
