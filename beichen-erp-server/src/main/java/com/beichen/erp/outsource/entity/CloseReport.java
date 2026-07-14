package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("outsource_order_close_report")
public class CloseReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private LocalDate closeDate;
    private String remark;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
