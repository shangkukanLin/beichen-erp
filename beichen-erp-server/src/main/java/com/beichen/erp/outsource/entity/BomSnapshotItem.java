package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BOM 快照明细（单套用量口径）。2026-09-17 起替代原 <code>outsource_order_material</code> 的写入。
 * <p>需求数量 = 单套用量 × 该加工单产品行数量，由视图 <code>outsource_order_material</code> 实时换算，
 * 因此快照本身与订单数量无关，可被多张加工单共享。</p>
 */
@Data
@TableName("bom_snapshot_item")
public class BomSnapshotItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 快照ID(bom_snapshot.id) */
    private Long snapshotId;

    @TableField("outsource_material_id")
    private Long materialId;

    /** 物料类型ID（关联 material_type.id） */
    private Long materialTypeId;

    private String unit;

    /** 单套用量 */
    private BigDecimal quantityPerSet;

    private BigDecimal lossRate;

    /** 供料方：OURS我方供 FACTORY工厂包 */
    private String supplyType;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
