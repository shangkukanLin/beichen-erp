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
@TableName("dev_project")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String displaySupplierName;

    private String touchSupplierName;

    private String glass;

    private String touchIc;

    private String displayDriverIc;

    private String chip;

    private String backPaste;

    private String coverPlate;

    private String flexCable;

    private String adaptModel;

    private String originalSize;

    private String originalResolution;

    private Long projectLeaderId;

    private LocalDate startDate;

    private LocalDate expectedEndDate;

    private LocalDate actualEndDate;

    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
