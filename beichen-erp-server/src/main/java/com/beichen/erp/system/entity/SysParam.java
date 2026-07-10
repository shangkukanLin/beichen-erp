package com.beichen.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_param")
public class SysParam {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String paramKey;
    private String paramValue;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
