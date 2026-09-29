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

    /**
     * 损失承担方（2026-09-29 用户口径「报损需要走财务流程」，与成品报损同口径）：
     * {@code INTERNAL}=内部损失（审核生成「报损损失」费用单，**无账户=非资金**）；
     * {@code SUPPLIER}=供应商/加工厂承担（审核生成**对供应商的应收**索赔，反审核冲销）。
     */
    private String liableParty;

    /** 承担方供应商/加工厂ID（liableParty=SUPPLIER 时必填） */
    private Long liableSupplierId;

    /** 承担方供应商/加工厂名称（冗余留痕） */
    private String liableSupplierName;

    /** 财务影响（非表字段，详情页展示） */
    @TableField(exist = false)
    private String financeInfo;

    /** 状态（DocStatus：DRAFT / AUDITED / CANCELLED） */
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

    /** 明细概况："物料名×数量"顿号分隔（非表字段） */
    @TableField(exist = false)
    private String itemSummary;
}
