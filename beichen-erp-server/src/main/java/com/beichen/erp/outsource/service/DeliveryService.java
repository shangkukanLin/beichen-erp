package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.outsource.entity.OutsourceDelivery;
import com.beichen.erp.outsource.entity.OutsourceDeliveryItem;

import java.util.List;

public interface DeliveryService {

    Page<OutsourceDelivery> page(String deliveryType, Long factoryId, String code, int pageNum, int pageSize);

    List<OutsourceDeliveryItem> getItems(Long deliveryId);

    void create(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items);

    /** 审核：草稿态生效，扣/增库存并写流水（委外加工订单） */
    void audit(Long id);

    /** 反审核：已审核态回滚库存流水，回到草稿（委外加工订单） */
    void unaudit(Long id);

    /** 审核：委外物料订单收货/退不良草稿单生效，扣/增库存、生成应付并回写订单明细与状态 */
    void auditMaterialDelivery(Long id);

    /** 反审核：委外物料订单收货/退不良回滚库存应付，回到草稿 */
    void unauditMaterialDelivery(Long id);

    void cancel(Long id);

    void update(OutsourceDelivery delivery, List<OutsourceDeliveryItem> items);

    OutsourceDelivery getById(Long id);

    void clearAttachUrl(Long id);

    java.math.BigDecimal calcWeightedPrice(Long factoryId, Long materialId);
}
