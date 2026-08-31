package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 研发项目实体
 * <p>status由项目阶段自动推导，cancelledAt标记取消时间</p>
 */
@Data
@TableName("dev_project")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String assemblyName;
    /** 关联产品ID(product.id)，与产品表双向关联；产品管理里新建的产品此字段为空 */
    private Long productId;
    /** 品牌ID(brand.id) */
    private Long brandId;
    private String originalSize;
    private String originalResolution;
    /** 原机驱动IC（手动填写） */
    private String originalDriveIc;
    /** 原机触摸IC（手动填写） */
    private String originalTouchIc;
    /** 玻璃尺寸（改配，手动填写） */
    private String glassSize;
    /** 玻璃分辨率（改配，手动填写） */
    private String glassResolution;
    /** 改配驱动IC物料ID（联动项目BOM） */
    private Long configDriveIcId;
    /** 改配触摸IC物料ID（联动项目BOM） */
    private Long configTouchIcId;
    /** 改配码片IC物料ID（联动项目BOM） */
    private Long configCodeIcId;
    private String displaySupplierName;
    private String touchSupplierName;
    private String adaptModel;
    private Long projectLeaderId;
    private Long sampleFactoryId;
    private Long outsourceFactoryId;
    /** 品牌名称（关联 brand，不落库，随详情/列表一起返回） */
    @TableField(exist = false)
    private String brandName;
    /** 打样工厂名称（关联 supplier，不落库，随详情/列表一起返回） */
    @TableField(exist = false)
    private String sampleFactoryName;
    /** 委外工厂名称（关联 supplier，不落库，随详情/列表一起返回） */
    @TableField(exist = false)
    private String outsourceFactoryName;
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
