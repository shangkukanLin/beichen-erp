package com.beichen.erp.purchase.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.purchase.entity.PurchaseReturn;
import com.beichen.erp.purchase.entity.PurchaseReturnItem;

import java.util.List;
import java.util.Map;

public interface PurchaseReturnService {
    Page<Map<String, Object>> page(Integer status, Long supplierId, String code, int pageNum, int pageSize);
    PurchaseReturn getById(Long id);
    PurchaseReturn create(PurchaseReturn order, List<Map<String, Object>> itemMaps);
    PurchaseReturn update(Long id, PurchaseReturn order, List<Map<String, Object>> itemMaps);
    List<PurchaseReturnItem> getItems(Long returnId);
    void audit(Long id);
    void unAudit(Long id);
    void cancel(Long id);
    void delete(Long id);
}
