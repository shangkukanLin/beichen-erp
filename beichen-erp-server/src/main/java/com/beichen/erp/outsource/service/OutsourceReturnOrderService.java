package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.outsource.entity.ReturnOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 委外加工退货单业务层
 * <p>退货物料入工厂委外仓、成品出库、负向应付 + 收费应付。草稿-审核-取消审核状态机。</p>
 * <p><b>维修退货的收尾（2026-09-17）</b>：货在工厂手上、修好陆续送回，故按「送修 / 已返回」跟踪，
 * 全部送回（未返回 = 0）后「结案」收尾；结案后禁登记/撤销维修返回、禁反审核。
 * 加工退货审核即终结（无"回来"腿），不使用结案。</p>
 */
public interface OutsourceReturnOrderService {

    /**
     * 分页查询（returnType 为空 = 全部；DEFECT 加工退货 / REPAIR 维修退货）。
     * @param progress 维修退货进度筛选：PENDING_RETURN 还有未返回 / RETURNED 已返回完 / CLOSED 已结案 / 空=全部
     * @param statuses 状态多值（逗号分隔，2026-09-27 三级菜单）：DRAFT,AUDITED=有效单据 / CANCELLED=已作废；空=全部
     */
    Page<Map<String, Object>> page(int pageNum, int pageSize, String code, Long factoryId, String returnType,
                                   String progress, String statuses);

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

    /**
     * BOM 快照的物料明细（**单套用量**口径）。2026-09-17 起按**快照ID**取，
     * 不再绕「加工单 → 订单产品行 → 需求数量 ÷ 订单数量」反算（快照已多单共享）。
     */
    List<Map<String, Object>> bomSnapshot(Long snapshotId);

    /**
     * 登记维修返回的**实际用料候选集**（2026-09-27 用户口径）。
     * <p>成品维修 ⇒ 用料**只能**从本单各产品行的 **BOM 快照**里选（用行上的 {@code bom_snapshot_id}，
     * 与"加工单当时按哪版 BOM 生产"同源，不用产品最新 BOM）；同一物料被多处引用时合并单套用量。</p>
     * <p>同一份逻辑供提交时的**范围校验**复用（前端限制 + 后端拦截成对）。</p>
     */
    List<Map<String, Object>> repairMaterialCandidates(Long id);

    /**
     * 从「成品收货」发起退货时的预填数据（2026-09-17）。
     * @param deliveryId 交货记录ID（按记录退货，带出各规格「可退数量」）
     * @param orderId    加工单ID（列表行入口，仅带出工厂/出库仓/产品）
     */
    Map<String, Object> returnPrefill(Long deliveryId, Long orderId);

    /**
     * 登记维修返回（2026-09-17 立；**2026-09-28 改草稿口径**）：维修退货单**已审核**（货已送工厂）后，
     * 工厂修好分批送回我方仓库。
     * <p>用户口径「加工和物料的登记返回都需要审核和反审核」⇒ 登记只建**草稿**：只校验（本单已审核、未结案、
     * 数量不超「送修 − 已审核返回」、实际用料限本单 BOM）+ 落库（返回行含维修费单价/金额快照 + 用料明细），
     * <b>不动库存/在厂行/成本/应付</b>；审核（{@link #auditRepairReturn}）才落账，反审核
     * （{@link #unAuditRepairReturn}）对称逆回（含冲销按行生成的维修费应付），草稿可直接删除。</p>
     */
    void repairReturn(Long id, Map<String, Object> body);

    /**
     * 删除维修返回**草稿**（2026-09-28 草稿口径）：草稿未落账 ⇒ 直接删记录（用料明细改挂到本单仍在的草稿）；
     * 已审核的必须先 {@link #unAuditRepairReturn}（对称逆回 + 留痕）再删。
     */
    void cancelRepairReturn(Long repairRecordId);

    /**
     * **审核**维修返回（2026-09-28 草稿口径）：审核才落账 —— 成品入我方仓 + 核销在厂 + 实际用料扣料/FIFO/成本，
     * 并按行生成**维修费应付**（金额 = 该行 repair_amount，0 = 不收费），最后盖审核人章。
     */
    void auditRepairReturn(Long repairRecordId);

    /**
     * **反审核**维修返回（2026-09-28 草稿口径）：逐腿对称逆回（先冲应付再回滚库存），
     * 记录回到**草稿**（留痕可查）并清空审核人。
     */
    void unAuditRepairReturn(Long repairRecordId);

    /**
     * 结案（仅维修退货，2026-09-17）：工厂把修好的货**全部送回**（未返回 = 0）后人工确认收尾。
     * <p>结案后禁止再登记/撤销维修返回、禁止反审核（需先「撤销结案」）。</p>
     */
    void close(Long id);

    /** 撤销结案：回到「送修中」跟踪状态，可继续登记维修返回 */
    void reOpen(Long id);
}
