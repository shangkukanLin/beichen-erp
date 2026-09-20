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
    /** 物料类型ID（关联 material_type.id），物料按此ID归类 */
    private Long materialTypeId;
    // F7-125（2026-09-20）：删除历史遗留 `spec`（产品规格已于 2026-09-15 全站下线；本列现网 30 行全 NULL，
    // 写入侧 `OutsourceMaterialServiceImpl` 不再接收、读取侧两处拷贝已一并移除）⇒ 列由 DDL 同步 DROP。
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
