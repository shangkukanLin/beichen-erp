package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.beichen.erp.sale.common.SaleReturnStatus;
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

    /** 客户名称（冗余存储） */
    private String customerName;

    /** 退货入库仓库ID */
    private Long warehouseId;

    /** 退货日期 */
    private LocalDate returnDate;

    /** 状态：0=草稿 1=已审核 2=已作废 */
    private Integer status;

    /** 退货总金额 */
    private BigDecimal totalAmount;

    /** 备注 */
    private String remark;

    /** 审核人ID */
    private Long auditorId;

    /** 审核人姓名 */
    private String auditorName;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 公司ID */
    private Long companyId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    public SaleReturn() {
        this.status = SaleReturnStatus.DRAFT.getCode();
        this.totalAmount = BigDecimal.ZERO;
    }
}
