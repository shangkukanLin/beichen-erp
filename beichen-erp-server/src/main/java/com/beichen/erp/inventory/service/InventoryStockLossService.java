package com.beichen.erp.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.inventory.entity.InventoryStockLoss;
import com.beichen.erp.inventory.entity.InventoryStockLossItem;

import java.util.List;

/** 成品报损单 Service */
public interface InventoryStockLossService {

    /** 分页查询：状态/仓库/原因/关键字（单号或备注）/报损日期范围 */
    Page<InventoryStockLoss> page(String status, Long warehouseId, String lossReason, String keyword,
                                  String startDate, String endDate, int pageNum, int pageSize);

    InventoryStockLoss getById(Long id);

    /** 明细列表（已回填品质中文名） */
    List<InventoryStockLossItem> getItems(Long lossId);

    /** 新增（草稿）：明细按产品档案冗余快照名称/规格/单位，单价为空时带出成本价 */
    void create(InventoryStockLoss loss, List<InventoryStockLossItem> items);

    /** 编辑（仅草稿） */
    void update(InventoryStockLoss loss, List<InventoryStockLossItem> items);

    /** 作废（仅草稿，草稿未扣库存，无需回滚） */
    void cancel(Long id);

    /** 审核（仅草稿）：校验库存后扣减，写报损出库流水 */
    void audit(Long id);

    /** 反审核（仅已审核）：把报损数量加回库存，回到草稿 */
    void unAudit(Long id);
}
