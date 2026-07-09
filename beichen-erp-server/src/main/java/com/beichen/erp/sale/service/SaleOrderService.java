package com.beichen.erp.sale.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.sale.entity.SaleOrder;
import com.beichen.erp.sale.entity.SaleOrderItem;

import java.util.List;
import java.util.Map;

public interface SaleOrderService {

    Page<Map<String, Object>> page(String status, Long customerId, String code, int pageNum, int pageSize);

    SaleOrder getById(Long id);

    List<SaleOrderItem> getItems(Long orderId);

    void create(SaleOrder order, List<SaleOrderItem> items);

    void update(SaleOrder order, List<SaleOrderItem> items);

    void cancel(Long id);

    void audit(Long id);
}
