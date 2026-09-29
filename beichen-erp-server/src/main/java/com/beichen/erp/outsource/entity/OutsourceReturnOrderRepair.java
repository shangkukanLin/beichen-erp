package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 委外维修返回记录（2026-09-17）
 * <p>维修退货单（{@code return_type=REPAIR}）送修出库后，工厂修好分批送回我方仓库的入库记录。</p>
 *
 * <p><b>2026-09-28 用户口径（「加工和物料的登记返回都需要审核和反审核」）</b>：原为**登记即生效**
 * （登记即成品入库 + 核销在厂 + 扣用料 + 按行挂维修费应付），现改为 —— <b>登记只建草稿</b>
 * （不动库存/账务；维修费单价仍在这里填、金额按行快照落库），详情页对每条记录点<b>审核</b>才落账
 * （入库 + 核销在厂 + 按实际用料扣料/成本 + **按行生成维修费应付**），<b>反审核</b>对称逆回（含冲销应付）
 * 并留痕（记录回草稿）；草稿可删除。与「物料维修返回」（{@code outsource_material_return_repair}）
 * 及「加工返回」（{@code outsource_return_back}）同一口径 ⇒ {@code status} 只可能是 DRAFT / AUDITED。</p>
 */
@Data
@TableName("outsource_return_order_repair")
public class OutsourceReturnOrderRepair {

    /** 制单人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「制单人」，由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private String createByName;

    /** 审核人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「审核人」，审核时盖章 */
    private Long auditorId;

    private String auditorName;

    /**
     * 状态（2026-09-28 用户口径「加工和物料的登记返回都需要审核和反审核」）：
     * {@code DRAFT}=草稿（登记只建草稿，不动库存/账务）；{@code AUDITED}=已审核（已落账）。
     * <p>聚合口径：已返回量 / 未返回量 / 结案判定 / "不超送修"校验**只认 AUDITED**；草稿不参与，
     * 因而也不挡主单反审核。存量行由 DataInitializer 回填为 AUDITED（历史即生效）。</p>
     */
    private String status;

    /** 审核时间（审核时盖章；反审核清空） */
    private LocalDateTime auditTime;


    @TableId(type = IdType.AUTO)
    private Long id;

    /** 维修退货单ID（outsource_return_order.id） */
    private Long returnOrderId;

    /** 返回日期 */
    private LocalDate repairDate;

    /** 返回入库仓（我方成品仓） */
    private Long warehouseId;

    /** 产品主数据ID（product.id） */
    private Long productId;

    /** 产品名称快照 */
    private String productName;

    /** 返回品质（A/B/C/DEFECT） */
    private String qualityType;

    /** 返回数量 */
    private BigDecimal quantity;

    /**
     * 维修费单价（元/件）—— 2026-09-28 用户口径：「费用精确到产品里，在登记返回时填写」。
     * <p>整单的 {@code outsource_return_order.charge_amount} 是历史口径（新增时填、送修审核时挂账）；
     * 现在维修费**按返回产品行**在**登记维修返回**时填：本列 = 单价，{@link #repairAmount} = 单价 × 数量。
     * 留空/null = 该产品不收费（0）。</p>
     */
    private BigDecimal repairUnitPrice;

    /**
     * 维修费金额（= 单价 × 数量）—— 单价在**登记返回**时填、金额在此按行快照落库；
     * 应付在**审核**该行时生成（反审核/删除草稿即冲销）—— 2026-09-28 草稿口径。
     */
    private BigDecimal repairAmount;

    /** 备注 */
    private String remark;

    /** 公司ID */
    private Long companyId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
