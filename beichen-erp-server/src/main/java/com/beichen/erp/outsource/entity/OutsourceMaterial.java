package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("outsource_material")
public class OutsourceMaterial {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String projectIds;
    private String materialName;
    /** BOM类型ID（关联 dev_bom_type.id），物料按此ID归类 */
    private Long bomTypeId;
    private String spec;
    private String unit;
    private Integer status;
    private String remark;
    /** 单价 */
    private BigDecimal price;

    /** 移动加权平均成本价（委外仓入库自动更新；cost_manual=1 时以手填为准） */
    private BigDecimal costPrice;

    /** 成本价是否手工锁定（1=手改，自动加权跳过） */
    private Integer costManual;

    /** 最近一次入库单价（参考价） */
    private BigDecimal lastInPrice;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
