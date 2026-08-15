package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 研发项目物料实体
 * <p>记录研发项目中自购的用料（如机板、原屏幕等），用于研发活动，与 BOM 表、委外物料无关</p>
 * <p>对应数据表 dev_purchase_item</p>
 */
@Data
@TableName("dev_purchase_item")
public class DevPurchaseItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    /** 公司ID（研发物料弱引用，不强制过滤） */
    @TableField("company_id")
    private Long companyId;
    private String name;
    private String type;
    private Integer quantity;
    private String locationDetail;
    private LocalDate purchaseDate;
    private BigDecimal amount;
    /** 状态：完好/已损坏/已使用 */
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 当前位置名称（非表字段，取最新流转记录 place_name 实时回填） */
    @TableField(exist = false)
    private String warehouseName;
    /** 当前位置详情（非表字段，取最新流转记录 place_detail 实时回填） */
    @TableField(exist = false)
    private String warehouseAddress;
}
