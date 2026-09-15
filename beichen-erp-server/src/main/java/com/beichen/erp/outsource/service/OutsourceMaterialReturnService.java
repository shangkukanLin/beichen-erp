package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.outsource.entity.OutsourceMaterialReturn;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 委外物料退货单业务层
 * <p>物料从源仓退回物料商，冲减应付。草稿-审核-取消审核状态机。</p>
 */
public interface OutsourceMaterialReturnService {

    /** 分页查询 */
    Page<Map<String, Object>> page(int pageNum, int pageSize, String code, Long supplierId, String status);

    /** 详情 */
    Map<String, Object> detail(Long id);

    /** 创建草稿 */
    void create(OutsourceMaterialReturn order, List<Map<String, Object>> itemsRaw);

    /** 编辑草稿 */
    void update(Long id, OutsourceMaterialReturn order, List<Map<String, Object>> itemsRaw);

    /** 审核：物料出源仓 + 负向应付 */
    void audit(Long id);

    /** 取消审核：物料回源仓 + 冲销应付 */
    void unAudit(Long id);

    /** 作废（仅草稿） */
    void cancel(Long id);

    /** 删除（仅草稿） */
    void delete(Long id);

    /** 可选源仓列表（启用仓库） */
    List<Map<String, Object>> warehouseOptions();

    /** 指定源仓可退物料库存（按物料聚合良品 GOOD 库存） */
    List<Map<String, Object>> materialStock(Long warehouseId);

    /** FIFO 物料单价 */
    BigDecimal fifoPrice(Long materialId, BigDecimal qty);
}
