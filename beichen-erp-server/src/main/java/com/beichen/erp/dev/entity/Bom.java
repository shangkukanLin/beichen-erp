package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BOM物料清单实体
 * <p>outsourceMaterialId关联outsource_material.id</p>
 * <p>version用于BOM多版本管理</p>
 */
@Data
@TableName("dev_bom")
public class Bom {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long bomTypeId;
    /** 关联外协物料ID（outsource_material.id） */
    private Long outsourceMaterialId;
    private Long supplierId;
    private BigDecimal quantity;
    private BigDecimal lossRate;
    private String specification;
    private String unit;
    /** BOM版本号，默认1 */
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
