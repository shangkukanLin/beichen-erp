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

    /** 审核：待审核 → 生产中 */
    void audit(Long id);

    /** 反审核：生产中且无交货记录 → 待审核 */
    void unAudit(Long id);

    /**
     * 收货：校验子物料库存 → 生成收发单草稿（仅存盘，库存/应付在收发单审核时统一处理）。
     * 委外单缺料时返回含 _shortage 标记的结果 Map；正常返回收发单ID。
     */
    Object receive(Long id, Map<String, Object> body);

    /** 退不良：生成收发单草稿（维修退货/折现退款），审核时统一落账。返回收发单ID */
    Object returnDefect(Long id, Map<String, Object> body);

    /**
     * 新增退货（2026-09-29 用户口径）：把该订单**已收**的物料退回物料商，生成
     * {@code DeliveryType.RECEIVE_RETURN} **草稿**（仅存盘，库存/已收数量/应付在审核时统一落账）。
     *
     * <p>建草稿时校验：① 订单须为生产中/已结单；② 每条明细「可退数量 ≤ 已收数量」；
     * ③ 退货仓库库存足够（口径同退不良：{@code stock_form=MATERIAL} + 良品）。</p>
     *
     * <p>审核后：扣退货仓库存（{@code MATERIAL_RETURN_OUT}）、**冲减订单已收数量**、生成负应付；
     * 反审核原路回滚（见 {@code DeliveryService.auditMaterialDelivery}）。</p>
     *
     * @param id   物料订单ID
     * @param body {@code warehouseId}（退货仓库）、{@code items:[{itemId, quantity}]}
     * @return 新建退货单ID
     */
    Object returnMaterial(Long id, Map<String, Object> body);

    /** 结单：标记已完成 */
    void finish(Long id);

    /**
     * 反结单（2026-09-27 新增）：已结单 → **生产中**（曾被审核或已收过货）/ **待审核**（两者皆无），
     * 并清空结单时间。纯状态回退，无账务副作用（{@link #finish} 不动库存/应付）。
     */
    void reopen(Long id);

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
