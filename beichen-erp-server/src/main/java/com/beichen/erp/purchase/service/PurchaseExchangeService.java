package com.beichen.erp.purchase.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.beichen.erp.purchase.entity.PurchaseExchange;
import com.beichen.erp.purchase.entity.PurchaseExchangeItem;

import java.util.List;
import java.util.Map;

/**
 * 采购换货单服务（进货业务，同品换货，强关联采购单；2026-09-18 新增）
 */
public interface PurchaseExchangeService {

    /** 分页列表 */
    IPage<Map<String, Object>> page(long current, long size, Map<String, Object> q);

    /** 详情（主表，含供货商名称） */
    PurchaseExchange getById(Long id);

    /** 明细列表（含 SKU 回填） */
    List<PurchaseExchangeItem> getItems(Long id);

    /** 新增（草稿） */
    PurchaseExchange create(PurchaseExchange exchange, List<Map<String, Object>> itemMaps);

    /** 修改（仅草稿） */
    PurchaseExchange update(Long id, PurchaseExchange exchange, List<Map<String, Object>> itemMaps);

    /** 审核：退回出库 + 换入入库 + 两条应付台账 */
    void audit(Long id);

    /** 反审核：对称回滚库存、作废应付台账，状态回草稿 */
    void unAudit(Long id);

    /** 作废（仅草稿）：单据留痕，不做物理删除 */
    void cancel(Long id);

    /** 兼容旧接口：语义等同作废（不做物理删除） */
    void delete(Long id);

    /**
     * 来源采购单明细（换货选单用）：返回已购/已退/已换/可换数量。
     * <p>可换量 = 已购 − 已退(TH-) − 已换(CH-)，与保存/审核时的后端校验口径完全一致。</p>
     */
    List<Map<String, Object>> purchaseOrderItems(Long purchaseOrderId);

    /**
     * 来源采购单下拉（仅已审核）：供新增页选择"来源采购单"。
     * <p>不用 {@code /inventory/purchase/page} 的 status 入参（那里是 Integer，传 AUDITED 会解析失败），
     * 故在换货单侧提供专用接口，口径与销售换货的 {@code /sale/return/sale-orders} 对称。</p>
     */
    List<Map<String, Object>> purchaseOrders(Long supplierId, String kw);
}
