package com.beichen.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("purchase_order")
public class PurchaseOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long supplierId;

    /** 供应商名称（实时查名，不落库） */
    @TableField(exist = false)
    private String supplierName;

    private Long warehouseId;

    private LocalDate orderDate;

    private String status;

    private Integer taxIncluded;

    private BigDecimal taxRate;

    /** 税额（taxIncluded=1 时按税率从含税总额中拆出：total × rate/(100+rate)） */
    private BigDecimal taxAmount;

    private BigDecimal totalAmount;

    /**
     * 结算方式（2026-10-09，口径参考销售单）：{@code CREDIT}=账期（只挂应付）；
     * {@code CASH}=现金（审核后自动生成并**立即审核**付款单，核销本单应付）。
     * <p><b>未指定 = 现金</b>（用户 2026-10-09 口径"老单据默认现金"）—— 与销售侧"未指定 = 账期"刻意不同。</p>
     */
    private String settleType;

    /** 现金结算的付款账户（账期时由服务层清空） */
    private Long settleAccountId;

    /** 本次付款总额（NULL = 按应付全额付款；账期时由服务层清空） */
    private BigDecimal settleAmount;

    private String remark;

    /** 制单人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「制单人」，由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private String createByName;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    private Long auditorId;

    private String auditorName;

    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
