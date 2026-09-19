package com.beichen.erp.sale.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.beichen.erp.sale.entity.SaleReturn;
import com.beichen.erp.sale.entity.SaleReturnItem;

import java.util.List;
import java.util.Map;

public interface SaleReturnService {

    /**
     * 分页列表：status 为字符串编码（DRAFT/AUDITED/CANCELLED），与 sale_return.status(varchar) 一致
     * @param saleOrderId 非空时只返回关联该销售单的退货单（供销售单详情展示售后记录）
     */
    IPage<Map<String, Object>> page(String status, Long customerId, String code, Long saleOrderId, int pageNum, int pageSize);

    SaleReturn getById(Long id);

    List<SaleReturnItem> getItems(Long returnId);

    SaleReturn create(SaleReturn order, List<Map<String, Object>> itemMaps);

    SaleReturn update(Long id, SaleReturn order, List<Map<String, Object>> itemMaps);

    void audit(Long id);

    void unAudit(Long id);

    void cancel(Long id);

    void delete(Long id);

    /** 查询销售单明细（含已退/可退数量，供退货带入） */
    List<Map<String, Object>> saleOrderItems(Long saleOrderId);
}
