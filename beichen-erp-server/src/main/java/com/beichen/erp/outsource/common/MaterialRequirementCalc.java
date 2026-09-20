package com.beichen.erp.outsource.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 委外「物料需求」的统一口径（F7-60，2026-09-20）。
 *
 * <p>背景：同一物料需求量原先在 5 处各写一份，差异有三类——
 * ① 单套用量来源：有的直取 {@code quantity_per_set}，有的只用「整单需求 ÷ 产品数量」反算；
 * ② 反算精度：4 / 6 / 10 位不一；③ 取整时机：有的不取整。⇒ 列表页与详情页对同一物料
 * 可能给出**相反**的缺料结论。</p>
 *
 * <p>本类固化两条口径，供全模块统一调用：</p>
 * <ol>
 *   <li><b>单套用量</b>：优先直取 {@code quantity_per_set}（快照里的精确值），
 *       缺失时才按「整单需求 ÷ 产品数量」反算，统一保留 <b>6 位</b>（F2-3 已定的口径）；</li>
 *   <li><b>需求量</b>：= 单套用量 × 数量，一律四舍五入到<b>整数</b>（数量类字段全模块均为整数）。</li>
 * </ol>
 *
 * <p>注意：{@code BomSnapshotServiceImpl} 生成快照时仍按 4 位落库 {@code quantity_per_set} ——
 * 那是**数据源**（快照值本身），不属于消费侧口径，故不在此统一范围内。</p>
 */
public final class MaterialRequirementCalc {

    private MaterialRequirementCalc() {}

    /**
     * 单套用量：优先 {@code quantityPerSet}；否则 {@code demandQuantity ÷ productQty}（6 位，四舍五入）。
     *
     * @param quantityPerSet  快照中的单套用量（精确值，优先）
     * @param demandQuantity  该产品的整单需求（兜底反算用）
     * @param productQty      该产品行的计划数量（除数；为 null 或 0 时按 1 处理，避免除零）
     * @return 单套用量（永不为 null；两个入参都为空时返回 0）
     */
    public static BigDecimal perUnit(BigDecimal quantityPerSet, BigDecimal demandQuantity, BigDecimal productQty) {
        if (quantityPerSet != null) return quantityPerSet;
        if (demandQuantity == null) return BigDecimal.ZERO;
        BigDecimal denom = (productQty == null || productQty.compareTo(BigDecimal.ZERO) == 0)
                ? BigDecimal.ONE : productQty;
        return demandQuantity.divide(denom, 6, RoundingMode.HALF_UP);
    }

    /**
     * 需求量 = 单套用量 × 数量，四舍五入到整数。
     *
     * @param perUnit 单套用量（比率）
     * @param qty     数量（计划量 / 剩余待交量 / 交货量 / 已交量…）
     * @return 整数需求量（任一入参为 null 时返回 0）
     */
    public static BigDecimal need(BigDecimal perUnit, BigDecimal qty) {
        if (perUnit == null || qty == null) return BigDecimal.ZERO;
        return perUnit.multiply(qty).setScale(0, RoundingMode.HALF_UP);
    }
}
