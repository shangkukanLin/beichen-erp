package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 委外物料维修返回记录（2026-09-17）。
 * <p>物料退货（维修退货）单审核送修后，供应商修好分批把物料送回来 —— 本表记录每一批"回来"的物料。
 * 与主单一起构成「送修出库 → 维修返回入库」闭环；<b>登记即生效</b>（物料库存 +），支持逐行撤销（库存回滚）。</p>
 * <p>与「委外加工退货」的维修返回（{@code outsource_return_order_repair}）同范式，只是对象是物料而非产品。</p>
 */
@Data
@TableName("outsource_material_return_repair")
public class OutsourceMaterialReturnRepair {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 物料退货单ID(outsource_material_return.id) */
    private Long returnOrderId;

    /** 返回日期 */
    private LocalDate repairDate;

    /** 返回入库仓（默认=该单出库源仓，可改） */
    private Long warehouseId;

    /** 本次返回回补到哪一行物料订单明细（outsource_material_order_item.id）；未关联订单/订单已完成时为 null */
    private Long materialOrderItemId;

    /** 委外物料ID(outsource_material.id) */
    private Long materialId;

    /** 物料名称快照（展示用，避免历史改名后对不上） */
    private String materialName;

    /** 单位 */
    private String unit;

    /** 本次返回数量 */
    private BigDecimal quantity;

    private String remark;

    /** 公司ID */
    private Long companyId;

    private LocalDateTime createTime;
}
