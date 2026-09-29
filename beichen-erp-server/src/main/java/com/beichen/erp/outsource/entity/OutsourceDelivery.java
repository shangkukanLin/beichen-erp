package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 委外收发单（发料/收料/退料/调拨）
 * <p>
 * 审核状态机（存入 status 字段，复用通用 DocStatus）：
 * DRAFT=草稿（仅存盘，不落库存流水）、AUDITED=已审核（审核后扣/增库存、写流水）、CANCELLED=已作废。
 * 注意：草稿态可编辑/删除且不碰库存；仅审核通过后库存与流水才生效；审核后发现填错可反审核回滚。
 * </p>
 */
@Data
@TableName("outsource_delivery")
public class OutsourceDelivery {

    /** 审核人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「审核人」，审核时盖章 */
    private Long auditorId;

    private String auditorName;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 单据编码，规则 DEL-YYYYMMDDNNN */
    private String code;
    /** 收发类型：DELIVERY发料 / RECEIVE收料 / RETURN退料 / TRANSFER调拨（枚举 DeliveryType） */
    private String deliveryType;
    /** 关联项目ID（选填） */
    private Long projectId;
    /** 加工厂ID（必填，决定目标委外仓） */
    private Long factoryId;
    /** 来源仓库ID（我方仓或委外仓，收料/退料/调拨/非直发发料必填） */
    private Long fromWarehouseId;
    /** 目标仓库ID（委外仓或我方仓，发料/调拨必填） */
    private Long toWarehouseId;
    /** 是否供应商直发：0=否(从我方仓发) / 1=是(供应商直发工厂)，仅发料使用 */
    private Integer supplierDirect;
    /**
     * 是否允许强制出库（把来源仓扣成负库存）：0/null=否（默认严格校验，库存不足直接报错）。
     * 2026-09-16 流程重构新增，仅「调拨」使用 —— 工厂实际有货但账面未记时的显式旁路。
     */
    private Integer allowNegative;
    /** 供应商ID（供应商直发时记录） */
    private Long supplierId;
    /** 物流公司（选填） */
    private String logisticsCompany;
    /** 物流单号（选填） */
    private String logisticsNo;
    /** 收发日期 */
    private LocalDate deliveryDate;
    /** 联系人 */
    private String contact;
    /** 联系电话 */
    private String phone;
    /** 审核状态：DRAFT草稿 / AUDITED已审核 / CANCELLED已作废（复用 DocStatus） */
    private String status;
    /** 备注 */
    private String remark;
    /** 附件地址（如送货单图片） */
    private String attachUrl;
    /** 来源订单ID（关联 outsource_material_order.id，强关联回查，替代 remark LIKE 弱关联） */
    private Long sourceOrderId;
    /**
     * 是否**已确认超收**（2026-09-29 用户口径「加工订单和物料订单都可以超量收货」，仅收货单 RECEIVE 用）：
     * 1=建单时用户已二次确认"收货数量超过该物料下单数量"⇒ 审核期不再复核「已收 + 本次 ≤ 下单数」；
     * 0/NULL=未确认（未确认的草稿审核仍会被拦）。纯粹留痕，不参与库存/应付计算。
     */
    private Integer overReceipt;
    /** 企业ID（多租户隔离，自动填充） */
    /** 制单人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「制单人」，由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private String createByName;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
