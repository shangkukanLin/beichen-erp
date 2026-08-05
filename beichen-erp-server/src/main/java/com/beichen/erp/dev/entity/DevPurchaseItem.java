package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
    private String name;
    private String type;
    private Integer quantity;
    private String location;
    private String locationDetail;
    private LocalDate purchaseDate;
    private BigDecimal amount;
    /** 状态：完好/已损坏/已使用 */
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
