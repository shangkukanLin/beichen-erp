package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.outsource.entity.MaterialOrder;

import java.util.List;
import java.util.Map;

/**
 * 委外物料订单业务层
 * <p>采购/委外物料订单：创建-审核-收货(生成收发单草稿)-结单-作废状态机；
 * 收货与退不良仅存盘收发单草稿，库存/应付在收发单审核时统一落账。</p>
 */
public interface MaterialOrderService {

    /** 分页查询 */
    Page<Map<String, Object>> page(int pageNum, int pageSize, String code, String status, String statuses, Long supplierId);

    /** 详情 */
    Map<String, Object> detail(Long id);

    /** 创建（返回新单ID） */
    Long create(MaterialOrder order, List<Map<String, Object>> itemsRaw);

    /** 编辑（重置明细） */
    void update(Long id, MaterialOrder order, List<Map<String, Object>> itemsRaw);

    /** 审核：待审核 → 收货中 */
    void audit(Long id);

    /** 反审核：收货中且无交货记录 → 待审核 */
    void unAudit(Long id);

    /**
     * 收货：校验子物料库存 → 生成收发单草稿（仅存盘，库存/应付在收发单审核时统一处理）。
     * 委外单缺料时返回含 _shortage 标记的结果 Map；正常返回收发单ID。
     */
    Object receive(Long id, Map<String, Object> body);

    /** 退不良：生成收发单草稿（维修退货/折现退款），审核时统一落账。返回收发单ID */
    Object returnDefect(Long id, Map<String, Object> body);

    /** 结单：标记已完成 */
    void finish(Long id);

    /** 作废（有交货记录时禁止） */
    void cancel(Long id);

    /** 查询该订单物料发到过哪些委外仓（用于退不良选择仓库） */
    List<Map<String, Object>> defectWarehouses(Long id);

    /** 查询该订单的收发单列表（含明细） */
    List<Map<String, Object>> deliveries(Long id);

    /** 清空合同附件 */
    void deleteAttach(Long id);

    /** 删除（仅逻辑删除） */
    void delete(Long id);
}
