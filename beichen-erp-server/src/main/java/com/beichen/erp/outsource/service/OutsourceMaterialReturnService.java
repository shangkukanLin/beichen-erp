package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.outsource.entity.OutsourceMaterialReturn;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 委外物料退货单业务层
 * <p><b>三种类型（2026-09-28 用户口径定稿，见 {@link com.beichen.erp.outsource.common.MaterialReturnType}）</b>：
 * <ul>
 *   <li><b>订单退料 ORDER</b>：仅**关联订单且订单未结单**；审核 = 扣源仓 + 扣该订单出货/收料数量
 *       （`order_returned_qty`，永久），不动账务、不跟踪返回；</li>
 *   <li><b>退货退款 REFUND</b>：无单或关联**已结单**订单；审核 = 扣源仓（账务 P2 起为「对供应商的应收」）；</li>
 *   <li><b>维修返回 REPAIR</b>：无单或关联**已结单**订单；审核 = 扣源仓 + 转入供应商委外仓，
 *       修好后「登记维修返回」**先存草稿**（2026-09-28 用户口径「加工和物料的登记返回都需要审核和反审核」），
 *       对草稿条点「审核」才入库（草稿可删、已审核可反审核），全部返回后可结案。</li>
 * </ul>
 * 草稿-审核-取消审核状态机。</p>
 * <p>❗**类型与订单状态的强绑定**由服务层强制（{@code MaterialReturnType.checkOrderStatus}），前端只是体验层：<br>
 * 订单退料 ⇒ 订单必须 RECEIVING；退款/维修 关联订单时 ⇒ 订单必须 FINISHED（否则报错引导改类型）。</p>
 */
public interface OutsourceMaterialReturnService {

    /**
     * 分页查询（returnType：ORDER 订单退料 / REFUND 退货退款 / REPAIR 维修返回，空=全部）。
     * @param progress 进度筛选（维修返回用）：PENDING_RETURN 还有未返回 / RETURNED 已返回完 / CLOSED 已结案 / 空=全部
     * @param statuses 状态多值（逗号分隔，2026-09-27 三级菜单）：DRAFT,AUDITED=有效单据 / CANCELLED=已作废；空=全部
     * @param linked 是否关联物料订单（2026-09-27 三级菜单，与加工侧同口径）：
     *               WITH_ORDER=关联退料（MRH-）/ WITHOUT_ORDER=无单退料（MRW-）/ 空=不筛选
     */
    Page<Map<String, Object>> page(int pageNum, int pageSize, String code, Long supplierId, String status,
                                   String returnType, String progress, String statuses, String linked);

    /** 详情 */
    Map<String, Object> detail(Long id);

    /** 创建草稿 */
    void create(OutsourceMaterialReturn order, List<Map<String, Object>> itemsRaw);

    /** 编辑草稿 */
    void update(Long id, OutsourceMaterialReturn order, List<Map<String, Object>> itemsRaw);

    /** 审核：物料出源仓；订单退料另扣订单出货/收料数；退货退款另生成负向应付（P2 改应收）；维修返回不动账务 */
    void audit(Long id);

    /** 取消审核：物料回源仓；订单退料回加订单收料数；退货退款另冲销应付（维修返回若已有返回记录则拦下） */
    void unAudit(Long id);

    /**
     * 登记维修返回（维修返回单：审核送修后，供应商修好把物料送回来）—— **只建草稿**。
     * <p>2026-09-28 用户口径「加工和物料的登记返回都需要审核和反审核」：登记时只校验（本单已审核送修、未结案、
     * 数量不超「送修 − 已审核返回」、实际用料只能选送修物料的子物料）并落库（返回行 + 用料行），
     * **不动库存/订单收料数/在厂行/成本**；审核（{@link #auditRepairReturn}）才落账，反审核
     * （{@link #unAuditRepairReturn}）对称逆回，草稿可直接删除。</p>
     * <p>（收费/应付：P3 口径 —— 维修费在送修审核时挂应付，登记返回不产生应付。）</p>
     */
    void repairReturn(Long id, Map<String, Object> body);

    /**
     * 登记维修返回的**实际用料（补料）候选集**（2026-09-27 用户口径）：
     * 物料维修 ⇒ 用料只能从**送修物料自己的子物料**（{@code outsource_material_component}）里选。
     * <p>同一份逻辑供提交时的**范围校验**复用（前端限制 + 后端拦截成对）；不筛库存（数量仍可超）。</p>
     */
    List<Map<String, Object>> repairMaterialCandidates(Long id);

    /**
     * 撤销（删除）维修返回**草稿**（2026-09-28 用户口径）：草稿未落账 ⇒ 直接删记录；
     * 本批实际用料行会**改挂**到本单仍在的首条草稿上（本单无草稿才随之删除）。
     * <p>已审核的记录不允许删除 —— 必须先 {@link #unAuditRepairReturn}（对称逆回 + 留痕）。</p>
     */
    void cancelRepairReturn(Long repairRecordId);

    /**
     * **审核**维修返回（按记录ID，2026-09-28 用户口径）：审核才落账 ——
     * 物料入指定仓 + 核销供应商委外仓在厂行 + 关联订单回补收料数 + 实际用料扣料/FIFO/成本结转，并盖审核人章。
     */
    void auditRepairReturn(Long repairRecordId);

    /**
     * **反审核**维修返回（按记录ID，2026-09-28 用户口径）：逐腿对称逆回该条记录已落的账，
     * 记录回到**草稿**（留痕可查）并清空审核人。
     */
    void unAuditRepairReturn(Long repairRecordId);

    /**
     * 结案（仅维修退货）：全部送修数量都已返回（未返回=0）后人工确认收尾。
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
