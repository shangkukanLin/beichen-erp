package com.beichen.erp.purchase.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.purchase.entity.PurchaseInbound;
import com.beichen.erp.purchase.entity.PurchaseInboundItem;

import java.util.List;
import java.util.Map;

public interface PurchaseInboundService {

    Page<Map<String, Object>> page(String status, Long supplierId, String code, int pageNum, int pageSize);

    PurchaseInbound getById(Long id);

    List<PurchaseInboundItem> getItems(Long inboundId);

    void create(PurchaseInbound inbound, List<PurchaseInboundItem> items);

    void update(PurchaseInbound inbound, List<PurchaseInboundItem> items);

    void cancel(Long id);

    void audit(Long id);
}
