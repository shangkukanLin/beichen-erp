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
