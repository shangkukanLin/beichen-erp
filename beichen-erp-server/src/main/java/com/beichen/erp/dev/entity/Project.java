package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 研发项目实体
 * <p>status由时间线自动推导，cancelledAt标记取消时间</p>
 */
@Data
@TableName("dev_project")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String assemblyName;
    private String originalSize;
    private String originalResolution;
    private String displaySupplierName;
    private String touchSupplierName;
    private String adaptModel;
    private Long projectLeaderId;
    private Long sampleFactoryId;
    private Long outsourceFactoryId;
    private LocalDate startDate;
    private LocalDate expectedEndDate;
    private LocalDate actualEndDate;
    private String status;
    private LocalDateTime cancelledAt;
    private String remark;
    private Long companyId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
