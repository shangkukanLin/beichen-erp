package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("dev_material")
public class DevMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联项目ID */
    private Long projectId;

    /** 物料名称 */
    private String materialName;

    /** 物料类型: 基板/屏幕/排线/IC/盖板/背贴/其他 */
    private String materialType;

    /** 数量 */
    private BigDecimal quantity;

    /** 存放位置 */
    private String location;

    /** 位置详情 */
    private String locationDetail;

    /** 采购日期 */
    private LocalDate purchaseDate;

    /** 采购金额 */
    private BigDecimal cost;

    /** 状态: 完好/已损坏/已使用 */
    private String status;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
