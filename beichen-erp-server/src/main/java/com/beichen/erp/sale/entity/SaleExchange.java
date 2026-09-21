package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.beichen.erp.common.DocStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售换货单（只支持同品换货）
 * <p>
 * 强关联销售单：主表 {@code sale_order_id} 指向销售单，明细关联 {@code sale_order_item_id}（销售单明细行）。
 * 可换数量受「已售 − 已退 − 已换」约束（只约束<b>退回数量</b>），支持同一明细多次部分换货。
 * </p>
 * <p>明细拆为「退回侧 + 换出侧」：只支持同品换货（换出产品固定为退回产品），
 * 换出品质与换出数量可不同（如退 2 换 1，换出 A 规变 B 规）。</p>
 * <p>审核时双向联动：退回货品入售后仓（品质 PENDING 待整理，走退货整理），
 * 换出货品从成品仓按退回产品({@code productId})与 {@code outQualityType} 扣减库存。</p>
 * <p>是否收费由 {@code chargeFlag} 控制，金额手工填写，审核后生成一条正向应收（单号 -FEE 后缀）。
 * 只支持正向收费：换出的产品比退回的便宜需要退款时，另开销售退货单处理。</p>
 */
@Data
@TableName("sale_exchange")
public class SaleExchange {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 换货单号 */
    private String code;

    /** 来源销售单ID（强关联，必填） */
    private Long saleOrderId;

    /** 来源销售单号（冗余，便于列表展示与检索） */
    private String saleOrderCode;

    private Long customerId;

    /** 换入仓：退回货品入此仓，必须为售后仓 */
    private Long warehouseInId;

    /** 换出仓：发出新货从此仓扣减，必须为成品仓 */
    private Long warehouseOutId;

    private LocalDate exchangeDate;

    /** 状态：DRAFT / AUDITED / CANCELLED */
    private String status;

    /** 是否收费：0否 1是（收费则审核后生成一条正向应收，单号后缀 -FEE） */
    private Integer chargeFlag;

    /** 收费类型：SERVICE服务费 / DIFF品质差价 / FULL全额货值 / OTHER其他 */
    private String chargeType;

    /** 收费金额（手工填写），审核后生成正向应收 */
    private BigDecimal chargeAmount;

    /** 收费说明（原因备注） */
    private String chargeReason;

    private String remark;

    private Long auditorId;

    private String auditorName;

    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    public SaleExchange() {
        this.status = DocStatus.DRAFT.getCode();
    }
}
