package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 加工单交货记录
 * <p>
 * 记录某加工单、某产品交了多少数量、交到哪个成品仓库，以及是否为退不良。
 * 审核状态机（存入 status 字段，复用通用 DocStatus）：
 * DRAFT=草稿（仅存盘，不扣料/不入库存/不生成应付）、
 * AUDITED=已审核（审核后扣减委外仓物料、成品入库、生成应付）、
 * CANCELLED=已作废。
 * 退不良通过 isReverse=true + 负数数量表达（不再使用 status 区分），审核后才冲减账实。
 * </p>
 */
@Data
@TableName("outsource_order_delivery")
// 只按字段序列化：否则 Lombok getAQty() 会被 Jackson 推断为 "aqty"，与字段上的
// @JsonProperty("aQty") 形成两个属性，JSON 里同时输出 aqty/aQty 重复键
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class OutsourceOrderDelivery {
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联加工单ID */
    private Long orderId;
    /**
     * 工厂ID：**仅「不关联加工单的加工退货」使用**（`source_type=RETURN_DEFECT` 且 `order_id` 为空）——
     * 无单时靠它定位「工厂委外仓」（BOM 料还回目标）与「应付对象」；其余行一律为空。
     * <p>2026-09-21 用户口径：该入口的**本意就是"可以不关联加工单"**，其余业务与成品收货页的
     * 「加工退货」（红冲收货）**完全一致**，故仍复用本表、仍走同一套审核（不建新表）。</p>
     */
    private Long factoryId;
    /** 关联加工单产品ID(outsource_order_product.id)。⚠️ 无单加工退货**必须留空**（财务分析按它 join 取加工单价） */
    private Long productId;

    /** SKU（展示用，非表字段；明细接口按 productId 批量回填） */
    @TableField(exist = false)
    private String sku;

    /** 产品名称（展示用，非表字段；原 product_name 列已删除，改为关联 product 表回填） */
    @TableField(exist = false)
    private String productName;

    /** 关联产品主数据ID(product.id)，成品库存/流水落账用 */
    private Long productMasterId;
    /** 成品入库仓库ID（选了仓库才做成品入库） */
    private Long warehouseId;
    /** 交货日期 */
    private LocalDate deliveryDate;
    /** 交货数量：正数=普通交货，负数=退不良（普通交货时等于四等级之和） */
    private BigDecimal quantity;
    /** A规数量 */
    @JsonProperty("aQty")
    private BigDecimal aQty;
    /** B规数量 */
    @JsonProperty("bQty")
    private BigDecimal bQty;
    /** C规数量 */
    @JsonProperty("cQty")
    private BigDecimal cQty;
    /** 不良数量 */
    @JsonProperty("defectQty")
    private BigDecimal defectQty;
    /** 来源类型：DELIVERY=普通交货 / RETURN_DEFECT=委外退货(不知订单) / AFTER_SALE=收费售后(不知订单) */
    private String sourceType;
    /** 交货类型：空=普通交货，DEFECT_RETURN=退不良（配合 isReverse 使用） */
    private String deliveryType;
    /** 退不良规格(A/B/C/DEFECT)，普通交货为空 */
    private String qualityType;
    /** 物流单号（选填） */
    private String trackingNo;
    /** 备注 */
    private String remark;
    /** 附件地址（选填） */
    private String attachUrl;
    /** 审核状态：DRAFT草稿 / AUDITED已审核 / CANCELLED已作废（复用 DocStatus） */
    private String status;
    /** 是否退不良红冲记录：true=退不良(数量为负) / false=普通交货，不再占用 status 字段 */
    private Boolean isReverse;
    /** 企业ID（多租户隔离，自动填充） */
    private Long companyId;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
