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

    /** 按采购单查询关联的退货单列表 */
    List<Map<String, Object>> byOrder(Long purchaseOrderId);

    /** 查询采购单明细（含已退/可退数量，供退货带入） */
    List<Map<String, Object>> purchaseOrderItems(Long purchaseOrderId);
}
