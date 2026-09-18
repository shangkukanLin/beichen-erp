package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("outsource_return_order")
public class ReturnOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long factoryId;
    /** 退货类型：见 {@link com.beichen.erp.outsource.common.OutsourceReturnType}（DEFECT不良退货 / REPAIR维修退货） */
    private String returnType;
    private Long orderId;
    /** 来源交货记录ID（outsource_order_delivery.id）：成品收货页按记录发起退货时落库 */
    private Long sourceDeliveryId;
    private Long warehouseId;
    private LocalDate returnDate;
    private String status;
    private Long auditorId;
    private String auditorName;
    private LocalDateTime auditTime;
    private String remark;
    /** 工厂收费：0否 1是（**加工厂向我方收取** → 审核生成一条我方付给加工厂的正向应付） */
    private Integer chargeFlag;
    /** 收费类型：见 OutsourceChargeType */
    private String chargeType;
    /** 收费金额（手工填写，审核后生成正向应付） */
    private BigDecimal chargeAmount;
    /** 收费说明 */
    private String chargeReason;
    /**
     * 结案：0未结案 1已结案（仅维修退货用，2026-09-17）。
     * <p>维修退货是"货在工厂手上、修好陆续送回"的单据，全部送回（未返回=0）后人工确认收尾；
     * 结案后禁止再登记/撤销维修返回、禁止反审核（需先撤销结案）。不良退货审核即终结，不使用本字段。</p>
     */
    private Integer closedFlag;
    /** 结案时间 */
    private LocalDateTime closedTime;
    /** 结案人 */
    private String closedBy;
    private Long companyId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
