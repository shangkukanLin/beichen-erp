package com.beichen.erp.material.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("material_bom")
public class MaterialBom {

    private Long id;

    /** 父物料ID(成品/半成品) */
    private Long parentMaterialId;

    /** 子物料ID */
    private Long childMaterialId;

    /** 单台/单套用量 */
    private BigDecimal quantity;

    /** 损耗率 */
    private BigDecimal lossRate;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
