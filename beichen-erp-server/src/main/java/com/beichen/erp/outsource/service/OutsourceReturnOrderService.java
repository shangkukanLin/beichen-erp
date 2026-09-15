package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.outsource.entity.ReturnOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 委外加工退货单业务层
 * <p>退货物料入工厂委外仓、成品出库、负向应付 + 收费应付。草稿-审核-取消审核状态机。</p>
 */
public interface OutsourceReturnOrderService {

    /** 分页查询 */
    Page<Map<String, Object>> page(int pageNum, int pageSize, String code, Long factoryId);

    /** 详情 */
    Map<String, Object> detail(Long id);

    /** 创建草稿（明细FIFO计价，草稿不动库存/应付） */
    void create(ReturnOrder order, Map<String, Object> body);

    /** 编辑草稿（E4：仅 DRAFT 可编辑，明细整体替换；草稿不动库存/应付） */
    void update(Long id, ReturnOrder order, Map<String, Object> body);

    /** 审核：退货物料入工厂委外仓 + 成品出库 + 负向应付 */
    void audit(Long id);

    /** 取消审核：物料出工厂委外仓 + 成品恢复 + 冲销应付 */
    void unAudit(Long id);

    /** 作废（仅草稿） */
    void cancel(Long id);

    /** FIFO 物料单价 */
    BigDecimal fifoPrice(Long materialId, BigDecimal qty);

    /** 获取某工厂的产品列表（含每个产品的BOM版本来源），用于退货选择 */
    List<Map<String, Object>> orderProducts(Long factoryId);

    /** 获取某产品在某加工单中的BOM快照物料 */
    List<Map<String, Object>> bomSnapshot(Long orderId, Long productId);
}
