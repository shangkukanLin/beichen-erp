package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 成品报损单主表
 * <p>报损是只出不入的单据：审核时按明细（产品×品质）扣减对应仓库库存，反审核加回。</p>
 */
@Data
@TableName("inventory_stock_loss")
public class InventoryStockLoss {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报损单号：BS-yyyyMMdd-NNN */
    private String code;

    private Long warehouseId;

    /** 仓库名称（冗余，列表直接展示，免联查） */
    private String warehouseName;

    /** 报损日期 */
    private LocalDate lossDate;

    /** 报损原因（LossReason 枚举 code：DAMAGE/EXPIRED/LOST/QUALITY/OTHER） */
    private String lossReason;

    /** 报损总金额（明细金额合计，冗余便于列表展示与统计） */
    private BigDecimal totalAmount;

    /**
     * 损失承担方（2026-09-29 用户口径「报损需要走财务流程」）：
     * {@code INTERNAL}=内部损失（审核生成「报损损失」费用单，**无账户=非资金**）；
     * {@code SUPPLIER}=供应商/加工厂承担（审核生成**对供应商的应收**索赔，反审核冲销）。
     */
    private String liableParty;

    /** 承担方供应商ID（liableParty=SUPPLIER 时必填） */
    private Long liableSupplierId;

    /** 承担方供应商名称（冗余留痕） */
    private String liableSupplierName;

    /** 财务影响（非表字段，详情页展示：如「损失费用单 FY-… (已审核)」/「应收索赔 AR-… 未收」） */
    @TableField(exist = false)
    private String financeInfo;

    /** 状态（DocStatus：DRAFT 草稿 / AUDITED 已审核 / CANCELLED 已作废） */
    private String status;

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

    /** 明细概况："产品名×数量"顿号分隔（非表字段，列表直接展示，免前端逐条拉明细） */
    @TableField(exist = false)
    private String itemSummary;
}
