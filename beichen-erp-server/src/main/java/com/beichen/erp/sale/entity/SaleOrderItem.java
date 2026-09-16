package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sale_order_item")
public class SaleOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long productId;

    /** SKU（展示用，非表字段；明细接口按 productId 批量回填） */
    @TableField(exist = false)
    private String sku;

    /** 产品名称（实时查名，不落库；详情/明细接口回填，供前端直接展示） */
    @TableField(exist = false)
    private String productName;

    /** 单位（实时查 product，不落库） */
    @TableField(exist = false)
    private String unit;

    /** 品质等级: A/B/C/DEFECT */
    private String qualityType;

    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
