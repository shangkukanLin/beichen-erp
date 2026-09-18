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

    /** 来源收料单ID（outsource_delivery.id）：物料收货页按记录发起退货时落库 */
    private Long sourceDeliveryId;

    /**
     * 关联物料订单ID(outsource_material_order.id)，2026-09-17 维修返还闭环：
     * <ul>
     *   <li>订单<b>未完成</b>(RECEIVING)：审核时扣减该订单明细的收料数（净收料 = 收料总数 − 送修数），
     *       修好「登记维修返回」时回补 → 订单台账自动闭环，无需另行跟踪；</li>
     *   <li>订单<b>已完成</b>(FINISHED) 或为空：不动订单，返回情况靠本单「送修/已返回」+ 结案跟踪。</li>
     * </ul>
     */
    private Long materialOrderId;

    /** 是否已在关联物料订单上扣减收料数：0否 1是（审核瞬间按订单状态冻结，反审核按此精确回滚） */
    private Integer deductedFlag;

    /** 结案：0未结案 1已结案（未返回=0 才能结案；结案后禁再登记返回、禁反审核） */
    private Integer closedFlag;

    /** 结案时间 */
    private LocalDateTime closedTime;

    /** 结案人 */
    private String closedBy;

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
