package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.outsource.entity.OutsourceReturnBack;
import com.beichen.erp.outsource.entity.OutsourceReturnBackItem;

import java.util.List;
import java.util.Map;

/**
 * 委外加工返回单（P1-2，2026-09-25）
 * <p>核销在厂成品（PRODUCT_DEFECT）+ 修好成品回我方仓 + 实际用料多行（可超 BOM）从委外仓扣减
 * + 料款按 FIFO 生成对加工厂的应收（工厂赔料）+ 成本结转。草稿-审核-反审核-作废状态机。</p>
 */
public interface OutsourceReturnBackService {

    /** 分页列表（含在厂/回仓仓库名、加工厂名等展示字段） */
    Page<Map<String, Object>> page(int pageNum, int pageSize, String code, Long factoryId, String status);

    /** 详情 */
    OutsourceReturnBack getById(Long id);

    /** 用料明细 */
    List<OutsourceReturnBackItem> getItems(Long id);

    /**
     * 「实际用料」**候选集**（2026-09-27 用户口径）：只能从来源无单加工退货单的 **BOM 快照**里选
     * （来源单没绑快照时，现场按"该产品在该工厂最近一次被加工单用过的快照"兜底；都拿不到 ⇒ 空数组）。
     * <p>同一份逻辑也用于提交时的**范围校验**（前端限制 + 后端拦截成对）。</p>
     */
    List<Map<String, Object>> materialCandidates(Long id);

    /**
     * 同上，但按**入参**给候选（2026-09-27）：**新增**返回单时还没有单 ID，前端只能带
     * {@code sourceDeliveryId}（可空：存量/未绑来源）+ {@code factoryId} + {@code productId} 来取候选。
     * <p>解析与校验口径完全一致（来源单快照 → 现场兜底 → 空）。</p>
     */
    List<Map<String, Object>> materialCandidates(Long sourceDeliveryId, Long factoryId, Long productMasterId);

    /** 新建草稿（body: factoryId/productId/quantity/defectQualityType/returnQualityType/inWarehouseId/returnDate/remark/items[]） */
    OutsourceReturnBack create(Map<String, Object> body);

    /**
     * **登记返回**（2026-09-27 用户口径：加工返回单不再单独开单 ⇒ 改成在**无单加工退货详情页**登记）。
     * <p>`create` + `audit` 合成一个事务：**登记即生效**（无草稿态、无二次审核）；来源单由路径决定并强制绑定
     * （防止前端把返回登记到别的来源单上）。账务与老流程完全一致：核销在厂成品 + 修好成品回仓 + 实际用料扣料
     * + 料款对加工厂的赔料应收 + FIFO 成本结转。</p>
     *
     * @return 生效后的返回记录（含单号 ORB-）
     */
    OutsourceReturnBack register(Long sourceDeliveryId, Map<String, Object> body);

    /** 撤销登记（`unAudit` + `deleteDraft` 合成一个事务）：库存/应收/成本对称逆回后删除该记录 */
    void revoke(Long id);

    /** 某来源无单加工退货单的返回记录（详情页「返回记录」表 + 已返回/未返回进度） */
    List<Map<String, Object>> listBySource(Long sourceDeliveryId);

    /** 修改草稿（整单替换明细） */
    void update(Long id, Map<String, Object> body);

    /** 审核：三腿落账 + 应收 + 成本结转 */
    void audit(Long id);

    /** 反审核：三腿对称回滚 + 冲应收 + 反结转成本 */
    void unAudit(Long id);

    /** 作废（仅草稿） */
    void cancel(Long id);

    /** 删除草稿 */
    void deleteDraft(Long id);
}
