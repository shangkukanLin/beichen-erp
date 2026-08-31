package com.beichen.erp.sale.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.sale.entity.ReturnSort;
import com.beichen.erp.sale.entity.ReturnSortItem;

import java.util.List;
import java.util.Map;

/**
 * 退货整理（销售退货分选入库）
 */
public interface ReturnSortService {

    Page<Map<String, Object>> page(String status, Long warehouseId, int pageNum, int pageSize);

    ReturnSort getById(Long id);

    List<ReturnSortItem> getItems(Long sortId);

    /** 售后仓不良品库存清单（新增时带出，按产品聚合） */
    List<Map<String, Object>> defectStock(Long warehouseId);

    void create(ReturnSort s, List<ReturnSortItem> items);

    void update(ReturnSort s, List<ReturnSortItem> items);

    /** 审核：售后仓扣 DEFECT，A/B/C/不良 分别入目标仓库 */
    void audit(Long id);

    /** 反审核：逆向回滚 */
    void cancel(Long id);

    void delete(Long id);
}
