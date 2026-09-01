package com.beichen.erp.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.inventory.entity.InventoryStockTake;
import com.beichen.erp.inventory.entity.InventoryStockTakeItem;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 库存盘点 Service：每月每仓盘点，审核按实盘调整库存（盘盈入库/盘亏出库），反审核对称回滚。 */
public interface StockTakeService {

    Page<Map<String, Object>> page(Long warehouseId, String period, String status, int pageNum, int pageSize);

    InventoryStockTake getById(Long id);

    List<InventoryStockTakeItem> getItems(Long takeId);

    /** 新建盘点单：按仓库+月份自动生成明细（带出账面数量快照） */
    InventoryStockTake create(Long warehouseId, String period, LocalDate takeDate, String remark);

    /** 保存实盘数量（仅草稿；差异在保存时计算） */
    void saveItems(Long takeId, List<InventoryStockTakeItem> items);

    void audit(Long id);

    void unAudit(Long id);

    void cancel(Long id);

    /**
     * 盘点看板：每个启用仓库的当月盘点状态。
     * 返回行：warehouseId / warehouseName / warehouseType / period(当前月) / taken(是否已盘点)
     * / lastTakeDate(上次已审核盘点日期) / dueDate(应盘日=当月最后一天) / overdueDays(超期天数,<=0 未超期)
     * / remind(是否进入本月提醒窗口，即今天 >= 当月 25 号)
     */
    List<Map<String, Object>> takeStatus();
}
