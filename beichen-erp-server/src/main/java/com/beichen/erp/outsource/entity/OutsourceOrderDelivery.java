package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
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
public class OutsourceOrderDelivery {
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联加工单ID */
    private Long orderId;
    /** 关联加工单产品ID */
    private Long productId;
    /** 成品入库仓库ID（选了仓库才做成品入库） */
    private Long warehouseId;
    /** 交货日期 */
    private LocalDate deliveryDate;
    /** 产品名称（快照展示用） */
    private String productName;
    /** 交货数量：正数=普通交货，负数=退不良 */
    private BigDecimal quantity;
    /** 交货类型：空=普通交货，DEFECT_RETURN=退不良（配合 isReverse 使用） */
    private String deliveryType;
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
