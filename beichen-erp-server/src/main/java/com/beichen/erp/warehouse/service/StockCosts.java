package com.beichen.erp.warehouse.service;

import com.beichen.erp.material.entity.Product;
import com.beichen.erp.outsource.entity.OutsourceMaterial;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 库存金额口径（2026-10-09 · 用户需求「物料仓和成品仓库需要库存金额」）。
 *
 * <p><b>公式</b>：库存金额 = 库存数量 × 单价。</p>
 *
 * <p><b>单价取值（用户拍板方案 C：现行成本 + 兜底 + 明示）</b>：
 * <ol>
 *   <li>现行移动加权成本价 {@code cost_price}（入库自动更新，见 {@link CostService}；{@code cost_manual=1} 时为手填价）；</li>
 *   <li>为空则回落「最近进价」{@code last_in_price}；</li>
 *   <li>物料再回落手填「单价」{@code price}（<b>成品没有这一档</b>）；</li>
 *   <li>全空 ⇒ 单价视为<b>未维护</b>：金额记 0，并由调用方回传 {@code costMissing=true} <b>明示</b>。
 *       <b>绝不静默按 0 参与合计</b> —— 否则用户会以为系统算错了（本仓测试库实测：物料成本 100% 未维护）。</li>
 * </ol>
 * </p>
 *
 * <p><b>数量口径</b>：与列表页「总库存」严格一致 —— 成品取 A/B/C/不良/待整理五种品质；
 * 物料取 {@code stock_form=MATERIAL} 下的良品/不良。<b>「送修在厂」不计入</b>（它本就不在列表的总库存里，
 * 若计入会造成"金额 ÷ 数量"对不上）。</p>
 *
 * <p><b>含税口径</b>：单价跟随来源单据的「含税」开关（{@code tax_included=1} 时即含税价），
 * 本项目不做价税分离 ⇒ 此处不再二次换算（见 {@code CostInboundLog.unitCost} 的说明）。</p>
 *
 * <p><b>已知边界（刻意为之）</b>：成本是<b>现值</b>、无历史快照 ⇒ 金额是"按当前成本重算"的口径，
 * 不是历史成本；这与滞销面板「参考金额」、利润表成本口径同源（见 {@code kpiFormula}）。</p>
 */
public final class StockCosts {

    /** 金额小数位（与成本价 DECIMAL(18,4) 一致；展示层再格式化到 2 位） */
    public static final int SCALE = 4;

    /** 口径说明文案（接口回传，前端直接展示；避免各页面自己写口径而说法不一） */
    public static final String CALIBER =
            "库存金额 = 库存数量 × 单价。单价取现行移动加权成本价，缺失时依次回落「最近进价」→ 物料手填单价，"
          + "仍缺失则记 0 并标记「成本未维护」（该部分不计入有效合计）。数量口径与本页「总库存」一致"
          + "（物料：良品+不良；成品：A/B/C/不良/待整理；均不含「送修在厂」）。"
          + "单价跟随单据含税开关，未做价税分离；成本为现值、无历史快照。";

    private StockCosts() {
    }

    /** 产品单价：移动加权成本价 → 最近进价 → 未维护(null) */
    public static BigDecimal unitCost(Product p) {
        if (p == null) return null;
        return firstPositive(p.getCostPrice(), p.getLastInPrice());
    }

    /** 物料单价：移动加权成本价 → 最近进价 → 手填单价 → 未维护(null) */
    public static BigDecimal unitCost(OutsourceMaterial m) {
        if (m == null) return null;
        return firstPositive(m.getCostPrice(), m.getLastInPrice(), m.getPrice());
    }

    /** 取第一个「有值且 > 0」的价格；全空/全 0 返回 null（= 成本未维护） */
    private static BigDecimal firstPositive(BigDecimal... candidates) {
        for (BigDecimal c : candidates) {
            if (c != null && c.compareTo(BigDecimal.ZERO) > 0) return c;
        }
        return null;
    }

    /** 行金额 = 数量 × 单价（单价未维护时返回 0；调用方须同时回传 costMissing=true） */
    public static BigDecimal amount(BigDecimal quantity, BigDecimal unitCost) {
        BigDecimal q = quantity != null ? quantity : BigDecimal.ZERO;
        if (unitCost == null) return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        return q.multiply(unitCost).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** 金额求和（null 安全） */
    public static BigDecimal sum(BigDecimal a, BigDecimal b) {
        return (a != null ? a : BigDecimal.ZERO).add(b != null ? b : BigDecimal.ZERO);
    }

    /** 统一到 4 位小数（合计用；避免各处 scale 不一致导致"合计 ≠ 各项之和"） */
    public static BigDecimal scale(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
