package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 加工返回单用料明细（P1-2，2026-09-25）
 * <p>数量允许<b>超过 BOM 标准用量</b>（按实际耗用记账）。</p>
 * <p><b>单价（2026-09-29 用户口径「登记返回时可以填写具体价格，默认 FIFO 可修改」）</b>：
 * 登记时即把单价**快照**到本表 —— 人工填了就用人工价（{@code price_manual=1}），没填则用**登记时点**的
 * 默认价（FIFO：成本价 → 交期 FIFO → 物料参考价 → 0）。审核时直接用本快照算**行料款 = 对加工厂的赔料应收**，
 * 登记到审核之间的价格漂移不再改变本单金额；而**成品成本结转仍取审核时点的 FIFO**（人工定价不污染库存成本）。</p>
 */
@Data
@TableName("outsource_return_back_item")
public class OutsourceReturnBackItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 返回单ID（outsource_return_back.id） */
    private Long returnBackId;

    /** 委外物料ID（outsource_material.id） */
    private Long materialId;

    /** 物料名称快照 */
    private String materialName;

    /** 单位 */
    private String unit;

    /** 实际用料数量（可超 BOM 标准用量） */
    private BigDecimal quantity;

    /**
     * 单价快照（2026-09-29 起**登记时**写入：人工定价 或 登记时点的默认 FIFO 价；审核时不再覆盖）。
     * <p>行料款 = 本单价 × 数量 = 对加工厂的赔料应收；成品成本仍按审核时点的 FIFO 结转。</p>
     */
    private BigDecimal unitPrice;

    /** 行料款 = 单价 × 数量（审核时落账；口径 = 赔料应收） */
    private BigDecimal amount;

    /**
     * 单价是否**人工填写**（2026-09-29 用户口径）：1=登记时人工填价（可与 FIFO 不同，含 0 元）/
     * 0=默认（登记时点的 FIFO 快照）。仅留痕与提示用，不参与金额计算。
     */
    private Integer priceManual;

    private Long companyId;
}
