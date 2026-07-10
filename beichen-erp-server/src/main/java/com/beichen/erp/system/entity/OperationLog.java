package com.beichen.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String operation;
    private String target;
    private String detail;
    private String ip;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
}
