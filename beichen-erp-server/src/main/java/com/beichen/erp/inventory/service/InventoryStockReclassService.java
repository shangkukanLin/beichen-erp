package com.beichen.erp.inventory.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.common.R;
import com.beichen.erp.inventory.entity.InventoryStockReclass;
import com.beichen.erp.inventory.entity.InventoryStockReclassItem;
import java.util.List;
import java.util.Map;

/** 品质重分类单业务接口 */
public interface InventoryStockReclassService extends IService<InventoryStockReclass> {

    /** 分页查询 */
    IPage<InventoryStockReclass> pageList(Map<String, Object> params);

    /** 新增（草稿）或更新草稿 */
    Long saveDraft(InventoryStockReclass header, List<InventoryStockReclassItem> items);

    /** 加载明细 */
    List<InventoryStockReclassItem> loadItems(Long id);

    /** 审核：按明细对每条进行源等级扣减 + 目标等级增加 */
    void review(Long id);

    /** 反审核：回滚库存 */
    void unreview(Long id);

    /** 作废 */
    void discard(Long id);
}
