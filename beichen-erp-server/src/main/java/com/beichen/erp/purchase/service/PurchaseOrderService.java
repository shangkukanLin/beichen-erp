package com.beichen.erp.purchase.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.purchase.entity.PurchaseOrder;
import com.beichen.erp.purchase.entity.PurchaseOrderItem;

import java.util.List;
import java.util.Map;

public interface PurchaseOrderService {

    Page<Map<String, Object>> page(String status, Long supplierId, String code, int pageNum, int pageSize);

    PurchaseOrder getById(Long id);

    List<PurchaseOrderItem> getItems(Long orderId);

    void create(PurchaseOrder order, List<PurchaseOrderItem> items);

    void update(PurchaseOrder order, List<PurchaseOrderItem> items);

    void cancel(Long id);

    void audit(Long id);
}
