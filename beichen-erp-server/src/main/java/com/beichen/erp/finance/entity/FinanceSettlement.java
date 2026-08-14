package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 核销流水表：记录收付款单与应付/应收台账的双向核销关系
 * <p>
 * 付款/收款审核时写入，反审核时据此精确冲销，消除单向核销的脆弱性。
 * 后续负数应付（预付）与账单核销均复用此表记录。
 * </p>
 */
@Data
@TableName("finance_settlement")
public class FinanceSettlement {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收付款单 ID（付款单/收款单） */
    private Long receiptPaymentId;

    /** 应付/应收台账 ID */
    private Long payableReceivableId;

    /** 本次核销金额 */
    private BigDecimal amount;

    /** 核销方向：PAY(付款核销应付) / RECEIVE(收款核销应收) */
    private String direction;

    /** 来源单据类型：PAYMENT/RECEIPT（为账单核销预留 BILL） */
    private String sourceType;

    /** 来源单据 ID */
    private Long sourceId;

    /** 公司ID（多租户隔离） */
    private Long companyId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
