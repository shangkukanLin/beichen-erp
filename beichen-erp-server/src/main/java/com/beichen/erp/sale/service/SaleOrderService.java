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

    /**
     * 某客户**已审核**的销售单列表（供售后退货 / 换货关联选择）。
     * <p>2026-09-19 期 3「读隔离」：该方法从 {@code SaleReturnService} 上移到销售单模块（数据属主）——
     * 退货页与换货页各自用**自己前缀**的接口取这份列表，不再互相跨模块读（也避免两处口径漂移）。</p>
     */
    List<Map<String, Object>> auditedOrdersOfCustomer(Long customerId);

    void create(SaleOrder order, List<SaleOrderItem> items);

    void update(SaleOrder order, List<SaleOrderItem> items);

    void cancel(Long id);

    void audit(Long id);

    /** 反审核：冲回应收、回退客户余额、库存回补，状态回退草稿 */
    void unAudit(Long id);

    /** 库存检查：返回每个物料的库存量和缺货信息 */
    List<Map<String, Object>> checkStock(Long warehouseId, List<SaleOrderItem> items);
}
