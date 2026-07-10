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

    private String assemblyName;

    private String displaySupplierName;

    private String touchSupplierName;

    private String adaptModel;

    private String originalSize;

    private String originalResolution;

    private Long projectLeaderId;

    private Long sampleFactoryId;

    private Long outsourceFactoryId;

    private LocalDate startDate;

    private LocalDate expectedEndDate;

    private LocalDate actualEndDate;

    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
