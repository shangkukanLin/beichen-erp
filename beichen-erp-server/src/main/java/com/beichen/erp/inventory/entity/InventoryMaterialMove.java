package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物料移仓单主表（2026-09-24 新增）。
 *
 * <p>业务背景：用户口径「物料收发单不要了，改用物料移仓代替」⇒ 本单据按**成品移仓单
 * （{@link InventoryWarehouseMove}）**的结构复刻，只是明细对象从「产品」换成「委外物料」，
 * 仓库范围从「自有成品仓」放开为「我方物料仓 + 委外仓」。</p>
 *
 * <p>它承接的正是原「物料收发单」里**手工**的两种能力：发料（我方物料仓 → 委外仓）与调拨
 * （物料相关仓之间互转）；收料/退不良仍走物料收货流程，与本单据无关。</p>
 *
 * <p>库存写入口径与成品移仓一致：审核时才落库存，同一单据内 from 减 / to 加；移仓不改变加权价
 * （总量守恒），仅对目标仓新出现的库存做成本兜底。</p>
 */
@Data
@TableName("inventory_material_move")
public class InventoryMaterialMove {

    /** 审核人（ID + 姓名快照）—— 全站单据口径：详情页显示「审核人」，审核时盖章 */
    private Long auditorId;

    private String auditorName;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long fromWarehouseId;

    private Long toWarehouseId;

    private LocalDate moveDate;

    private String status;

    private String remark;

    /** 制单人（ID + 姓名快照）—— 由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private String createByName;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
