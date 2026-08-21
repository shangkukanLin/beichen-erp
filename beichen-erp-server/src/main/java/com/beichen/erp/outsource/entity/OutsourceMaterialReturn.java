package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 委外物料退货单（主表）
 * <p>物料从源仓退回物料商，冲减应付。return_type 预留成品商退货扩展。</p>
 */
@Data
@TableName("outsource_material_return")
public class OutsourceMaterialReturn {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 退货单号（MR-YYYYMMDD-NNN） */
    private String code;

    /** 退货类型：MATERIAL(物料商)/PRODUCT(成品商，预留) */
    private String returnType;

    /** 退回对象供应商ID（物料商） */
    private Long supplierId;

    /** 物料出库源仓（用户自选，委外仓/自有物料仓均可） */
    private Long fromWarehouseId;

    /** 退货日期 */
    private LocalDate returnDate;

    /** 单据状态：DRAFT/AUDITED/CANCELLED */
    private String status;

    /** 审核人ID */
    private Long auditorId;

    /** 审核人姓名 */
    private String auditorName;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 备注 */
    private String remark;

    /** 公司ID */
    private Long companyId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
