package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 研发物料位置流转记录实体
 * <p>记录物料每次位置变更（借给委外工厂、借给方案商等），最新一条即为物料当前位置。</p>
 * <p>对应数据表 dev_material_flow</p>
 */
@Data
@TableName("dev_material_flow")
public class DevMaterialFlow {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 物料ID（dev_purchase_item.id） */
    private Long materialId;
    /** 位置类型：INVENTORY/OUTSOURCE/SUPPLIER/CUSTOMER/TEXT（见 DevMaterialPlaceTypeEnum） */
    private String placeType;
    /** 关联对象ID（仓库/供应商/客户的主键，placeType=TEXT 时为 null） */
    private Long placeId;
    /** 位置名称（快照，用于列表展示；关联对象变更名称时历史记录仍保留原名称） */
    private String placeName;
    /** 自定义文本位置（placeType=TEXT 时使用） */
    private String placeDetail;
    /** 经办人 */
    private String handler;
    /** 流转时间 */
    private LocalDateTime flowTime;
    /** 图片URL列表（逗号分隔） */
    private String images;
    /** 备注 */
    private String remark;
    /** 公司ID */
    @TableField("company_id")
    private Long companyId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
