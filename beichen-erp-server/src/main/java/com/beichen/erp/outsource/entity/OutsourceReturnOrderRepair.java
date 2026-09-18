package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 委外维修返回记录（2026-09-17）
 * <p>维修退货单（{@code return_type=REPAIR}）送修出库后，工厂修好分批送回我方仓库的入库记录。
 * 登记即生效（库存 +），可逐行撤销（库存回滚）；不产生任何应付（维修费在送修审核时已挂）。</p>
 */
@Data
@TableName("outsource_return_order_repair")
public class OutsourceReturnOrderRepair {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 维修退货单ID（outsource_return_order.id） */
    private Long returnOrderId;

    /** 返回日期 */
    private LocalDate repairDate;

    /** 返回入库仓（我方成品仓） */
    private Long warehouseId;

    /** 产品主数据ID（product.id） */
    private Long productId;

    /** 产品名称快照 */
    private String productName;

    /** 返回品质（A/B/C/DEFECT） */
    private String qualityType;

    /** 返回数量 */
    private BigDecimal quantity;

    /** 备注 */
    private String remark;

    /** 公司ID */
    private Long companyId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
