package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 售后待整理批次
 * <p>
 * 售后仓的待整理(PENDING)库存按 (仓库, 产品, 品质) 聚合，本身不记录来源、无法追溯。
 * 销售退货单 / 销售换货单审核时各写入一条本批次，退货整理单消费本表并回写 {@code sortedQuantity}，
 * 从而统一回答「这批待整理品来自哪张单据、还剩多少没整理」。
 * </p>
 * <p>剩余可整理数量 = {@code quantity − sortedQuantity}。</p>
 *
 * @see com.beichen.erp.sale.common.AfterSaleSourceType
 */
@Data
@TableName("after_sale_pending")
public class AfterSalePending {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源单据类型：SALE_RETURN / SALE_EXCHANGE */
    private String sourceType;

    /** 来源单据ID（sale_return.id / sale_exchange.id） */
    private Long sourceId;

    /** 来源单据明细ID（sale_return_item.id / sale_exchange_item.id） */
    private Long sourceItemId;

    /** 来源单号（冗余，便于列表展示与检索） */
    private String sourceCode;

    /** 来源单据业务日期（退货单的退货日期 / 换货的换货日期，冗余展示） */
    private LocalDate sourceDate;

    /** 售后仓ID：待整理品所在仓 */
    private Long warehouseId;

    /** 客户ID（冗余，整理后生成折损应收时免查来源单据） */
    private Long customerId;

    private Long productId;

    /** SKU（展示用，非表字段；明细接口按 productId 批量回填） */
    @TableField(exist = false)
    private String sku;

    /** 产品名称（冗余） */
    private String productName;

    /** 单位（冗余） */
    private String unit;

    /** 待整理数量（来源单据审核时的入库数量） */
    private BigDecimal quantity;

    /** 已整理数量（整理单审核累加、反审核扣回） */
    private BigDecimal sortedQuantity;

    /** 来源单价（冗余，折损计算与展示用） */
    private BigDecimal unitPrice;

    private Long companyId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
