package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.outsource.entity.OutsourceStockLoss;
import com.beichen.erp.outsource.entity.OutsourceStockLossItem;

import java.util.List;

/** 委外物料报损单 Service */
public interface OutsourceStockLossService {

    /** 分页查询：状态/仓库/原因/关键字（单号或备注）/报损日期范围 */
    Page<OutsourceStockLoss> page(String status, Long warehouseId, String lossReason, String keyword,
                                  String startDate, String endDate, int pageNum, int pageSize);

    OutsourceStockLoss getById(Long id);

    /** 明细列表（已回填品质中文名） */
    List<OutsourceStockLossItem> getItems(Long lossId);

    /** 新增（草稿）：明细按物料档案冗余快照名称/规格/单位/BOM类型，单价为空时带出最近进价 */
    void create(OutsourceStockLoss loss, List<OutsourceStockLossItem> items);

    /** 编辑（仅草稿） */
    void update(OutsourceStockLoss loss, List<OutsourceStockLossItem> items);

    /** 作废（仅草稿） */
    void cancel(Long id);

    /** 审核（仅草稿）：校验库存后扣减，写报损出库流水 */
    void audit(Long id);

    /** 反审核（仅已审核）：把报损数量加回库存，回到草稿 */
    void unAudit(Long id);
}
