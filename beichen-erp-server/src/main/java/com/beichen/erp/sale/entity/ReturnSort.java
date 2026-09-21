package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 退货整理单主表
 * <p>销售退货单/销售换货单退回的待整理品先入售后仓，再按 A/B/C/不良品分选后分别入库（A/B/C 入成品仓，不良入不良仓）。
 * 待整理来源统一取自 {@code after_sale_pending}，因此退货单与换货退回的货品共用同一条整理链路。</p>
 */
@Data
@TableName("return_sort")
public class ReturnSort {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 单号(TS-yyyyMMdd-001) */
    private String code;

    /** 源仓库(售后仓)ID */
    private Long warehouseId;

    /** 整理日期 */
    private LocalDate sortDate;

    /** A规入库仓库 */
    private Long targetWarehouseA;

    /** B规入库仓库 */
    private Long targetWarehouseB;

    /** C规入库仓库 */
    private Long targetWarehouseC;

    /** 不良入库仓库 */
    private Long targetWarehouseDefect;

    /** 状态: DRAFT/AUDITED/CANCELLED */
    private String status;

    /**
     * 折损收款金额：整理后 B/C/不良品的折损，向客户收取，审核后生成一条正向应收（单号 -LOSS 后缀）。
     * <p>折损金额取决于整理结果（各品质数量），因此挂在整理单而非销售退货单上——退货单审核时还不知道分选结果。</p>
     */
    private BigDecimal lossAmount;

    /** 折损收款说明 */
    private String lossRemark;

    private String remark;

    private Long companyId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
