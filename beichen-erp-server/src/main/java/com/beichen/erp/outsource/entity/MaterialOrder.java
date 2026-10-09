package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("outsource_material_order")
public class MaterialOrder {

    /** 审核人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「审核人」，审核时盖章 */
    private Long auditorId;

    private String auditorName;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long supplierId;
    /** 订单类型：采购 / 委外 */
    private String orderType;
    /** 收货目标仓库（委外单可指定入总成厂仓，空则默认入供应商仓） */
    private Long targetWarehouseId;
    private LocalDate deliveryDate;
    private String status;
    private String remark;
    private String attachUrl;

    /**
     * 是否含税：0未含税 1含税（2026-10-08 用户口径：与加工单/采购单/销售单对齐）。
     */
    private Integer taxIncluded;

    /** 税率（百分数，如 13 表示 13%）—— 仅当 taxIncluded=1 时参与税额拆分 */
    private BigDecimal taxRate;

    /** 税额（taxIncluded=1 时按税率从含税总额中拆出：total × rate/(100+rate)） */
    private BigDecimal taxAmount;

    /** 总金额（= 各明细金额合计；与明细一样是**含税**口径） */
    private BigDecimal totalAmount;
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
    private LocalDateTime finishTime;
    /** 结单人（ID + 姓名快照）—— 2026-09-27 用户口径：结单要留痕"谁结的"；finish 盖章、reopen 清空 */
    private Long finisherId;

    private String finisherName;
    /** 逻辑删除标记：0未删除/1已删除 */
    private Integer deleted;

    @TableField(exist = false)
    private List<MaterialOrderItem> items;
}
