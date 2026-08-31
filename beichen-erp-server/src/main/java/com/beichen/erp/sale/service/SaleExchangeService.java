package com.beichen.erp.sale.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.beichen.erp.sale.entity.SaleExchange;
import com.beichen.erp.sale.entity.SaleExchangeItem;

import java.util.List;
import java.util.Map;

/**
 * 销售换货单服务（同品换货，强关联销售单）
 */
public interface SaleExchangeService {

    /** 分页列表 */
    IPage<Map<String, Object>> page(long current, long size, Map<String, Object> q);

    /** 详情（主表） */
    SaleExchange getById(Long id);

    /** 明细列表 */
    List<SaleExchangeItem> getItems(Long id);

    /** 新增 */
    SaleExchange create(SaleExchange exchange, List<Map<String, Object>> itemMaps);

    /** 修改（仅草稿） */
    SaleExchange update(SaleExchange exchange, List<Map<String, Object>> itemMaps);

    /** 审核：退回入售后仓(待分类) + 换出从成品仓扣减 */
    void audit(Long id);

    /** 反审核：对称回滚，状态回到草稿 */
    void unAudit(Long id);

    /** 删除（仅草稿） */
    void delete(Long id);

    /**
     * 来源销售单明细（换货选单用）：返回已售/已退/已换/可换数量。
     * <p>可换量 = 已售 − 已退 − 已换，与保存/审核时的后端校验口径完全一致。</p>
     */
    List<Map<String, Object>> saleOrderItems(Long saleOrderId);
}
