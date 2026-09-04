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
    private Long orderId;
    private Long warehouseId;
    private LocalDate returnDate;
    private String status;
    private Long auditorId;
    private String auditorName;
    private LocalDateTime auditTime;
    private String remark;
    /** 是否收费：0否 1是（我方支付给加工厂的加工退货费用） */
    private Integer chargeFlag;
    /** 收费类型：见 OutsourceChargeType */
    private String chargeType;
    /** 收费金额（手工填写，审核后生成正向应付） */
    private BigDecimal chargeAmount;
    /** 收费说明 */
    private String chargeReason;
    private Long companyId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
