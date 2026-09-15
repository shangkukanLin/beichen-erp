package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 委外物料报损单主表
 * <p>与成品报损（inventory_stock_loss）结构一致但独立成表：两者主体分别是
 * outsource_material 与 product，品质枚举也不同（GOOD/DEFECT 与 A/B/C/DEFECT/PENDING）。</p>
 */
@Data
@TableName("outsource_stock_loss")
public class OutsourceStockLoss {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报损单号：WBS-yyyyMMdd-NNN */
    private String code;

    private Long warehouseId;

    /** 仓库名称（冗余，列表直接展示） */
    private String warehouseName;

    /** 报损日期 */
    private LocalDate lossDate;

    /** 报损原因（LossReason 枚举 code） */
    private String lossReason;

    /** 报损总金额（明细金额合计） */
    private BigDecimal totalAmount;

    /** 状态（DocStatus：DRAFT / AUDITED / CANCELLED） */
    private String status;

    private String remark;

    private Long auditorId;

    private String auditorName;

    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 明细概况："物料名×数量"顿号分隔（非表字段） */
    @TableField(exist = false)
    private String itemSummary;
}
