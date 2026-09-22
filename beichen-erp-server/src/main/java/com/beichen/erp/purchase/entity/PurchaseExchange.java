package com.beichen.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购换货单（进货业务，2026-09-18 新增；**同品换货**）
 * <p>
 * 业务：把向供货商采购的成品退回供货商，同时换回（同款）良品，一张单管住"出一进"。
 * </p>
 * <p>
 * <b>来源采购单可选</b>（2026-09-21 用户口径「可以不强关联采购单」）：
 * <ul>
 *   <li>选了采购单：明细由采购单带出，可换量 = 采购明细数量 − 已退(TH-) − 已换(CH-)，只约束退回数量；</li>
 *   <li>不选采购单（无单换货）：供货商手工选，明细手工逐行录（产品/品质/数量/单价），
 *       不再有可换量上限 —— 退回能否出库由**审核时的库存校验**把关。</li>
 * </ul>
 * </p>
 * <p>
 * 审核双向联动库存：
 * <ol>
 *   <li>退回侧：从 {@code warehouseOutId} 按明细 {@code qualityType}（默认不良品 DEFECT）**出库**扣减；</li>
 *   <li>换入侧：向 {@code warehouseInId} 按 {@code inQualityType}（默认 A 规）**入库**增加。</li>
 * </ol>
 * 财务联动：退回侧生成**负向**应付（冲减我们欠供货商的钱），换入侧生成**正向**应付，
 * 两行净额即差价（等价换货净额 0；加价换新则净额为正）。
 * </p>
 * <p>
 * <b>是否付费</b>（2026-09-21 用户口径）：{@code chargeFlag}=1 时审核**额外**生成一条正向应付
 * （source_bill_type=PURCHASE_EXCHANGE_CHARGE），金额手工填写。
 * ⚠️ <b>方向：我们向供货商付费</b>（如换货服务费、补差价），与销售换货「向客户收费」恰好相反。
 * </p>
 */
@Data
@TableName("purchase_exchange")
public class PurchaseExchange {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 换货单号（CH-yyyyMMddNNN） */
    private String code;

    /** 供货商ID */
    private Long supplierId;

    /** 供货商名称（非表字段，查询时按 supplierId 回填） */
    @TableField(exist = false)
    private String supplierName;

    /** 关联采购单ID（**可选**：2026-09-21 起允许"无单换货"，为空则明细手工录入、无可换量上限） */
    private Long purchaseOrderId;

    /** 关联采购单号（冗余，便于列表展示与检索；无单换货时为空） */
    private String purchaseOrderCode;

    /** 退回出库仓ID：退回货品从此仓扣减（我方成品仓，货退给供货商） */
    private Long warehouseOutId;

    /** 换入入库仓ID：供货商换回的良品入此仓（我方成品仓，可与退回仓相同） */
    private Long warehouseInId;

    private LocalDate exchangeDate;

    /** 状态：DRAFT / AUDITED / CANCELLED */
    private String status;

    /** 退回侧总金额（明细 quantity × unitPrice 汇总，生成负向应付） */
    private BigDecimal totalReturnAmount;

    /** 换入侧总金额（明细 inQuantity × inUnitPrice 汇总，生成正向应付） */
    private BigDecimal totalInAmount;

    /** 是否付费：0否 1是（2026-09-21 新增）。⚠️ 方向：**我们向供货商付费** */
    private Integer chargeFlag;

    /** 付费类型：SERVICE服务费 / DIFF品质差价 / FULL全额货值 / OTHER其他（见 {@code PurchaseChargeType}；2026-09-21 改名） */
    private String chargeType;

    /** 付费金额（手工填写）：我方付给供货商的金额，审核后生成一条正向应付 */
    private BigDecimal chargeAmount;

    /** 付费说明（原因备注） */
    private String chargeReason;

    private String remark;

    private Long auditorId;

    private String auditorName;

    private LocalDateTime auditTime;

    /** 制单人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「制单人」，由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private String createByName;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
