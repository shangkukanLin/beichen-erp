package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.outsource.entity.OutsourceMaterialReturn;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 委外物料退货单业务层
 * <p>两种类型（2026-09-17 定稿，见 {@link com.beichen.erp.outsource.common.MaterialReturnType}）：
 * <b>退货退款</b>=物料退回物料商并冲减应付；<b>维修返还</b>=退回物料商维修、修好后登记维修返回入库（不冲应付）。
 * 草稿-审核-取消审核状态机。</p>
 * <p><b>维修返还闭环（2026-09-17 晚）</b>：按"关联物料订单 + 订单是否完成"分三种情况收尾 ——
 * ①订单未完成(RECEIVING)：审核扣减该订单收料数（净收料=收料总数−送修数），修好登记返回时回补，订单台账自动闭环；
 * ②订单已完成(FINISHED)：不动订单，靠本单「送修/已返回」+ 结案跟踪；③未关联订单：同②。</p>
 */
public interface OutsourceMaterialReturnService {

    /**
     * 分页查询（returnType：REFUND 退货退款 / REPAIR 维修返还，空=全部）。
     * @param progress 进度筛选（维修返还用）：PENDING_RETURN 还有未返回 / CLOSED 已结案 / 空=全部
     */
    Page<Map<String, Object>> page(int pageNum, int pageSize, String code, Long supplierId, String status, String returnType, String progress);

    /** 详情 */
    Map<String, Object> detail(Long id);

    /** 创建草稿 */
    void create(OutsourceMaterialReturn order, List<Map<String, Object>> itemsRaw);

    /** 编辑草稿 */
    void update(Long id, OutsourceMaterialReturn order, List<Map<String, Object>> itemsRaw);

    /** 审核：物料出源仓；退货退款另生成负向应付，维修返还不动应付 */
    void audit(Long id);

    /** 取消审核：物料回源仓；退货退款另冲销应付（维修返还若已有返回记录则拦下） */
    void unAudit(Long id);

    /**
     * 登记维修返回（维修返还单：审核送修后，供应商修好把物料送回来 → 入库）。
     * <p>登记即生效：物料入指定仓（`MATERIAL_REPAIR_IN`），**不产生应付**；按物料核销不超过送修量。</p>
     */
    void repairReturn(Long id, Map<String, Object> body);

    /** 撤销维修返回（按记录ID：扣回已入库物料并删除该记录；关联订单已扣过的收料数同步回退） */
    void cancelRepairReturn(Long repairRecordId);

    /**
     * 结案（仅维修返还）：全部送修数量都已返回（未返回=0）后人工确认收尾。
     * <p>结案后禁止再登记维修返回、禁止撤销返回、禁止反审核（需先「撤销结案」）。</p>
     */
    void close(Long id);

    /** 撤销结案（回到"送修中"跟踪状态，可继续登记维修返回） */
    void reOpen(Long id);

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

    /**
     * 从「物料收货」发起退货时的预填数据（2026-09-17）：按收料单带出供应商/源仓/物料与该单「可退数量」。
     * @param deliveryId 收料单ID（outsource_delivery.id，delivery_type=RECEIVE）
     */
    Map<String, Object> returnPrefill(Long deliveryId);
}
