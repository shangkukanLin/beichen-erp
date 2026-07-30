package com.beichen.erp.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.inventory.entity.InventoryProductReclassify;
import com.beichen.erp.inventory.entity.InventoryProductReclassifyItem;

import java.util.List;
import java.util.Map;

public interface ReclassifyService {

    Page<Map<String, Object>> page(String status, Long warehouseId, int pageNum, int pageSize);

    InventoryProductReclassify getById(Long id);

    List<InventoryProductReclassifyItem> getItems(Long reclassifyId);

    void create(InventoryProductReclassify rc, List<InventoryProductReclassifyItem> items);

    void update(InventoryProductReclassify rc, List<InventoryProductReclassifyItem> items);

    void audit(Long id);

    void cancel(Long id);
}
