package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("dev_project_timeline")
public class ProjectTimeline {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String statusName;

    private Integer sortOrder;

    private LocalDate plannedEnd;

    private LocalDate actualEnd;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
}
