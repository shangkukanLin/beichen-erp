package com.beichen.erp.sale.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.sale.entity.SaleOrder;
import com.beichen.erp.sale.entity.SaleOrderItem;

import java.util.List;
import java.util.Map;

public interface SaleOrderService {

    /**
     * 分页列表：status / customerId / code 为可选过滤；
     * startDate~endDate 为**单据日期（order_date）**区间过滤（yyyy-MM-dd，可选）
     * —— 首页「当日销售单」用它取全量当日单，避免前端从"最近 N 条"里筛导致少算。
     */
    Page<Map<String, Object>> page(String status, Long customerId, String code,
                                   String startDate, String endDate, int pageNum, int pageSize);

    SaleOrder getById(Long id);

    List<SaleOrderItem> getItems(Long orderId);

    void create(SaleOrder order, List<SaleOrderItem> items);

    void update(SaleOrder order, List<SaleOrderItem> items);

    void cancel(Long id);

    void audit(Long id);

    /** 反审核：冲回应收、回退客户余额、库存回补，状态回退草稿 */
    void unAudit(Long id);

    /** 库存检查：返回每个物料的库存量和缺货信息 */
    List<Map<String, Object>> checkStock(Long warehouseId, List<SaleOrderItem> items);
}
