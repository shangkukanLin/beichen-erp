package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.beichen.erp.common.DocStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售退货单主表
 * <p>客户将已售不良品退回公司，实物入库（品质等级 DEFECT）增加库存；财务退款走独立登记，本单不自动生成应收。</p>
 */
@Data
@TableName("sale_return")
public class SaleReturn {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 退货单号 */
    private String code;

    /** 客户ID */
    private Long customerId;

    /** 客户名称（实时查名，不落库） */
    @TableField(exist = false)
    private String customerName;

    /** 退货入库仓库ID */
    private Long warehouseId;

    /** 关联销售单ID（可选，用于追溯） */
    private Long saleOrderId;

    /** 关联销售单号（冗余展示） */
    private String saleOrderCode;

    /** 退货日期 */
    private LocalDate returnDate;

    /** 状态：DRAFT=草稿 AUDITED=已审核 CANCELLED=已作废 */
    private String status;

    /** 退货总金额（冲减应收的负向金额） */
    private BigDecimal totalAmount;

    /** 折损收款金额：整理后 B/C/不良品的折损，由用户填写，审核时生成一条正向应收向客户收取（不计入退货总额） */
    private BigDecimal lossAmount;

    /** 是否收费：0否 1是（收费则审核后生成一条正向应收，单号后缀 -FEE，与退货冲抵应收区分） */
    private Integer chargeFlag;

    /** 收费类型：SERVICE服务费 / DIFF品质差价 / FULL全额货值 / OTHER其他 */
    private String chargeType;

    /** 收费金额（手工填写），审核后生成正向应收 */
    private BigDecimal chargeAmount;

    /** 收费说明（原因备注） */
    private String chargeReason;

    /** 备注 */
    private String remark;

    /** 审核人ID */
    private Long auditorId;

    /** 审核人姓名 */
    private String auditorName;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 制单人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「制单人」，由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private String createByName;

    /** 公司ID */
    private Long companyId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 注意：构造器只初始化状态，不要给业务字段（如 totalAmount / lossAmount）赋默认值。
     * 因为审核/反审核/作废都是用 `new SaleReturn()` 做局部更新（updateById），
     * 若构造器把业务字段设成非 null 的默认值，会被一并写库，把真实值覆盖成 0。
     * 总额由 Service 按明细汇总后显式设置。
     */
    public SaleReturn() {
        this.status = DocStatus.DRAFT.getCode();
    }
}
