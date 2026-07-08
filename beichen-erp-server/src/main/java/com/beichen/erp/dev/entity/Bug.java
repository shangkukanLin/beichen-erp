package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_bug")
public class Bug {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String code;

    private String title;

    private String severity;

    private String bugType;

    private String status;

    private String description;

    private Long foundBy;

    private Long assignedTo;

    private LocalDateTime foundTime;

    private LocalDateTime resolvedTime;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
}
