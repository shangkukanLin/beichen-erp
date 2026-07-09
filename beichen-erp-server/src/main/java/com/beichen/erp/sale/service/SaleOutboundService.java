package com.beichen.erp.sale.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.sale.entity.SaleOutbound;
import com.beichen.erp.sale.entity.SaleOutboundItem;

import java.util.List;
import java.util.Map;

public interface SaleOutboundService {

    Page<Map<String, Object>> page(String status, Long customerId, String code, int pageNum, int pageSize);

    SaleOutbound getById(Long id);

    List<SaleOutboundItem> getItems(Long outboundId);

    void create(SaleOutbound outbound, List<SaleOutboundItem> items);

    void update(SaleOutbound outbound, List<SaleOutboundItem> items);

    void cancel(Long id);

    void audit(Long id);
}
