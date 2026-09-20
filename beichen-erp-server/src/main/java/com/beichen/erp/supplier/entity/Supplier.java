package com.beichen.erp.supplier.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("supplier")
public class Supplier {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    /**
     * 供货SKU（2026-09-21 新增）：该供货商所供产品 SKU 的**前缀**。
     * <p>新增产品时若选了本供货商，SKU 取「供货SKU + '-' + 6位流水」（如 ABC ⇒ ABC-000001，
     * 且各前缀各自独立取号）；为空则该供货商的产品仍走默认前缀 {@code SKU-}。
     * 保存时统一 trim + 转大写，公司内非空值唯一（DB 唯一键 {@code uk_company_supply_sku} 兜底）。</p>
     */
    private String supplySku;

    private String contact;
    /** 类型编码列表（不持久化，用于返回前端） */
    @TableField(exist = false)
    private java.util.List<String> typeCodes;

    private String phone;

    private String address;

    private Integer status;

    private Integer hasDisplay;

    private Integer hasTouch;

    private Long relatedSupplierId;

    /** 应付余额（实时汇总，不落库，用于列表展示） */
    @TableField(exist = false)
    private BigDecimal payableBalance;

    /** 账期（月） */
    private Integer creditPeriodMonths;

    /** 账期（天） */
    private Integer creditPeriod;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
