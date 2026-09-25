package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 委外加工返回单（P1-2，2026-09-25）
 * <p>无单加工退货的"回来"腿：修好送回时核销在厂成品（委外仓 PRODUCT_DEFECT 行）+ 修好成品回我方仓
 * + 按<b>实际用料多行（可超 BOM）</b>从委外仓扣物料 + 料款按 FIFO 生成<b>对加工厂</b>的应收（工厂赔料）。</p>
 */
@Data
@TableName("outsource_return_back")
public class OutsourceReturnBack {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 返回单号（ORB-yyyyMMdd###） */
    private String code;

    /** 加工厂ID：赔料应收对象（subjectType=SUPPLIER） */
    private Long factoryId;

    /** 加工厂名称快照 */
    private String factoryName;

    /** 产品主数据ID（product.id） */
    private Long productId;

    /** 产品名称快照 */
    private String productName;

    /** 返回数量（核销在厂成品与回仓同量） */
    private BigDecimal quantity;

    /** 在厂成品规格（A/B/C/DEFECT）：定位委外仓 PRODUCT_DEFECT 行 */
    private String defectQualityType;

    /** 修好回仓品质（A/B/C/DEFECT） */
    private String returnQualityType;

    /** 回仓仓库ID（我方成品仓） */
    private Long inWarehouseId;

    /** 加工厂委外仓ID（创建时解析快照） */
    private Long outsourceWarehouseId;

    /** 料款合计快照（Σ行 FIFO，审核生成对工厂应收） */
    private BigDecimal materialAmount;

    private LocalDate returnDate;

    /** 状态：DRAFT/AUDITED/CANCELLED（{@link com.beichen.erp.common.DocStatus}） */
    private String status;

    private Long auditorId;
    private String auditorName;
    private LocalDateTime auditTime;
    private String remark;

    private Long companyId;

    /** 制单人（ID + 姓名快照）—— 全站单据口径：详情页显示「制单人」，由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private String createByName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
