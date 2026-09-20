package com.beichen.erp.material.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.beichen.erp.material.common.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    @NotBlank(message = "产品名称不能为空")
    private String name;
    /**
     * SKU 编码（产品级唯一，公司内不重复）。
     * 新增时留空则由后端按最大流水自动生成（SKU-000001），也可手工填写（需唯一）。
     */
    private String sku;
    private Long brandId;
    /**
     * 供货商ID（2026-09-21 新增）：关联 {@code supplier.id} 且类型为 {@code product} 的供货商。
     * <p>新增产品时若选了供货商，SKU 前缀取该供货商的「供货SKU」（{@code ABC ⇒ ABC-000001}）；
     * 未选、或该供货商未配前缀 ⇒ 走默认 {@code SKU-000001}。</p>
     * <p>可空；**编辑时改/清空供货商不会重算已生成的 SKU**（SKU 是既有编码，历史单据里还存着快照）。</p>
     */
    private Long supplierId;
    /**
     * 规格：ORIGINAL原装 / MATCHED原配 / MODIFIED改配（枚举 {@link com.beichen.erp.material.common.ProductSpec}）。
     * 2026-09-21：原自由文本「分类 category」替换为规格枚举。
     * 产品管理的新增/编辑**必填**（校验在 ProductController）；研发立项自动建产品时规格尚未确定，可为空、之后补填。
     */
    private String specType;
    /** 通用型号（适用多款机型） */
    private String generalModel;
    private String unit;
    private BigDecimal safetyStock;

    /** 移动加权平均成本价（入库自动更新；cost_manual=1 时以手填为准） */
    private BigDecimal costPrice;

    /** 成本价是否手工锁定（1=手改，自动加权跳过） */
    private Integer costManual;

    /** 最近一次入库单价（参考价） */
    private BigDecimal lastInPrice;

    private ProductStatus status;
    private Long projectId;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
