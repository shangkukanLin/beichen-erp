package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.outsource.entity.OutsourceDelivery;
import com.beichen.erp.outsource.entity.OutsourceDeliveryItem;

import java.util.List;

public interface DeliveryService {

    Page<OutsourceDelivery> page(String deliveryType, Long factoryId, String code, int pageNum, int pageSize);

    List<OutsourceDeliveryItem> getItems(Long deliveryId);

    void create(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items);

    /** 审核：草稿态生效，扣/增库存并写流水 */
    void audit(Long id);

    /** 反审核：已审核态回滚库存流水，回到草稿 */
    void unaudit(Long id);

    void cancel(Long id);

    void update(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items);

    OutsourceDelivery getById(Long id);

    void clearAttachUrl(Long id);

    java.math.BigDecimal calcWeightedPrice(Long factoryId, Long materialId);
}
