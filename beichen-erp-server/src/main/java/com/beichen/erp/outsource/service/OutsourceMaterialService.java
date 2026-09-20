package com.beichen.erp.outsource.service;

import java.util.List;
import java.util.Map;

/**
 * 委外物料**主数据写路径**（2026-09-20 · F7-70）。
 *
 * <p>原实现把这四个写操作直接放在 {@code OutsourceMaterialController} 里直连 Mapper，
 * 既没有 {@code @Transactional}（物料主表 + 供应商居间表 + 子物料组成是三处写入，中途失败会留半套），
 * 也没有删除前的**引用校验**。本接口把它们下沉到 Service 层，与其它模块的分层约定保持一致。</p>
 */
public interface OutsourceMaterialService {

    /** 新增物料（主表 + supplier_material 居间表，同一事务） */
    Long create(Map<String, Object> body);

    /** 修改物料（主表 + supplier_material 居间表差量，同一事务） */
    void update(Map<String, Object> body);

    /**
     * 删除物料（级联删除其子物料组成，同一事务）。
     * <p>**被引用（库存 / 物料订单明细 / 退货明细 / 收发明细 / 子物料组成）时拒绝删除**，
     * 避免历史单据上的物料与库存记录悬空。</p>
     */
    void delete(Long id);

    /** 保存物料的子物料组成（全量替换，同一事务） */
    void saveComponents(Long materialId, List<Map<String, Object>> items);
}
