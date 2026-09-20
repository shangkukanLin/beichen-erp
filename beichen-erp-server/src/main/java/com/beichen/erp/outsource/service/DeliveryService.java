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

    /**
     * F7-81-③（2026-09-20）：**调拨仓库规则校验**（供"结单自动退料"等**非手工创建**调拨单的场景复用）。
     *
     * <p>结单退料在事务内直接生成 `AUDITED` 调拨单并当场落账（为保结单原子性），原实现**跳过了**
     * {@code DeliveryServiceImpl} 的仓库规则 ⇒ 可把退料"退回仓"选成成品仓等非物料仓（前端下拉也确实未过滤）。
     * 本方法把那份规则**抽成唯一实现**并暴露出来，手工创建与结单退料**共用同一份判定**。</p>
     *
     * @throws com.beichen.erp.exception.BusinessException 两端非物料相关仓 / 同仓 / 我方物料仓→委外仓（应走发料）
     */
    void assertTransferWarehouses(Long fromWarehouseId, Long toWarehouseId);

    void clearAttachUrl(Long id);

    java.math.BigDecimal calcWeightedPrice(Long factoryId, Long materialId);
}
