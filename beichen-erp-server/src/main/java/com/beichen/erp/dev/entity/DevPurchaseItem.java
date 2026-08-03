package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 研发采购记录实体
 * <p>记录研发项目中购买的物料（如手机等），用于抓数据等研发活动</p>
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
