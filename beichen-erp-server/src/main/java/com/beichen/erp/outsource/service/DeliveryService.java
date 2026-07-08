package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.outsource.entity.OutsourceDelivery;
import com.beichen.erp.outsource.entity.OutsourceDeliveryItem;

import java.util.List;

public interface DeliveryService {

    Page<OutsourceDelivery> page(String deliveryType, Long factoryId, String code, int pageNum, int pageSize);

    List<OutsourceDeliveryItem> getItems(Long deliveryId);

    void create(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items);

    void cancel(Long id);

    void update(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items);

    OutsourceDelivery getById(Long id);

    void clearAttachUrl(Long id);
}
