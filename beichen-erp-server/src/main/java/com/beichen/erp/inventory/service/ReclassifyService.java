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

    /** 反审核（E2：已审核 → CANCELLED 并逆向库存；原由 cancel 兼任，2026-09-12 拆出） */
    void unAudit(Long id);

    /** 作废（仅草稿） */
    void cancel(Long id);
}
