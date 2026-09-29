package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 委外物料维修返回记录（2026-09-17）。
 * <p>物料退货（维修退货）单审核送修后，供应商修好分批把物料送回来 —— 本表记录每一批"回来"的物料。
 * 与主单一起构成「送修出库 → 维修返回入库」闭环。</p>
 *
 * <p><b>2026-09-28 用户口径（「加工和物料的登记返回都需要审核和反审核」）</b>：原为**登记即生效**
 * （登记即物料库存 +），现改为 —— <b>登记只建草稿</b>（不动库存/订单收料数/在厂行/子物料），
 * 详情页对每条记录点<b>审核</b>才落账（入库 + 回补订单收料数 + 核销在厂 MATERIAL_REPAIR + 扣子物料/成本），
 * <b>反审核</b>对称逆回并留痕（记录回草稿）；草稿可删除。与「加工返回」（{@code outsource_return_back}）
 * 同一口径 —— 所以 {@code status} 只可能是 DRAFT / AUDITED（不设作废：草稿直接删）。</p>
 *
 * <p>与「委外加工退货」的维修返回（{@code outsource_return_order_repair}）同范式，只是对象是物料而非产品；
 * ⚠️ 那侧（成品维修退货的登记返回）2026-09-28 经用户确认**仍为登记即生效**，未纳入本次改造。</p>
 */
@Data
@TableName("outsource_material_return_repair")
public class OutsourceMaterialReturnRepair {

    /** 制单人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「制单人」，由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private String createByName;

    /** 审核人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「审核人」，审核时盖章 */
    private Long auditorId;

    private String auditorName;

    /**
     * 状态（2026-09-28 用户口径「登记返回需要审核和反审核」）：
     * {@code DRAFT}=草稿（登记只建草稿，不动库存/账务）；{@code AUDITED}=已审核（已落账）。
     * <p>聚合口径：已返回量 / 未返回量 / 订单收料数回补 / 结案判定**只认 AUDITED**；草稿不参与，
     * 因而也不挡主单反审核。存量行由 DataInitializer 回填为 AUDITED（历史即生效）。</p>
     */
    private String status;

    /** 审核时间（审核时盖章；反审核清空） */
    private LocalDateTime auditTime;


    @TableId(type = IdType.AUTO)
    private Long id;

    /** 物料退货单ID(outsource_material_return.id) */
    private Long returnOrderId;

    /** 返回日期 */
    private LocalDate repairDate;

    /** 返回入库仓（默认=该单出库源仓，可改） */
    private Long warehouseId;

    /** 本次返回回补到哪一行物料订单明细（outsource_material_order_item.id）；未关联订单/订单已完成时为 null */
    private Long materialOrderItemId;

    /** 委外物料ID(outsource_material.id) */
    private Long materialId;

    /** 物料名称快照（展示用，避免历史改名后对不上） */
    private String materialName;

    /** 单位 */
    private String unit;

    /** 本次返回数量 */
    private BigDecimal quantity;

    /**
     * 2026-09-27（对称性二修）：本条**登记时是否真的核销了在厂行**。
     * <p>1=是（撤销时必须恢复在厂，见 cancelRepairReturn）；0=否 —— 只可能是「旧单」（审核于物料形态化
     * 改造之前，从未入过厂）在厂行为 0 时登记，登记腿被 {@code allocateOnSiteRepair} 跳过。
     * 撤销腿**必须按本标记**决定是否恢复：否则旧单撤销会凭空给在厂行 +qty（反向漏记）。
     * 存量库由 DataInitializer 幂等补列，默认 1（历史上绝大多数记录确实核销过）。</p>
     */
    private Integer onsiteLeg;

    private String remark;

    /** 公司ID */
    private Long companyId;

    private LocalDateTime createTime;
}
