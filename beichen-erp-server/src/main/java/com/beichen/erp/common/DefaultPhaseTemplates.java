package com.beichen.erp.common;

import com.beichen.erp.material.common.ProductSpec;

/**
 * 研发阶段模板默认数据（2026-09-21 抽出）——「原配 / 改配」各一套。
 *
 * <p><b>为什么要有这个类</b>：原先"那 14 个阶段"在 {@code DataInitializer.initPhaseTemplates()}
 * 与 {@code ClearController} 里**各硬编码了一份**，而 {@code ClearController} 的 INSERT **漏写了
 * {@code product_status_sync}** ⇒ 用户点一次"清空数据"后，"小批量/结项"就不再触发产品状态同步
 * （现网 14 条全 0 就是这么来的）。这里统一为**唯一来源**，两处都改为引用它。</p>
 *
 * <p>2026-09-21 用户需求：原配项目的阶段只有 7 个（立项 / 排线打样 / 背贴盖板打样 / 总成样品 /
 * 测试 / 小批量 / 结项），改配用原来那 14 个 —— 故模板分两套，立项时按项目规格自动套用。</p>
 */
public final class DefaultPhaseTemplates {

    private DefaultPhaseTemplates() {}

    /**
     * 一行默认阶段。
     *
     * @param name             阶段名称
     * @param defaultDays      默认天数
     * @param sortOrder        排序序号
     * @param productStatusSync 1=完成/跳过该阶段需同步产品状态（研发中→正常）
     * @param remark           备注（作业说明）
     */
    public record Row(String name, int defaultDays, int sortOrder, int productStatusSync, String remark) {}

    /** 改配（14 个）—— 与 2026-09-21 之前逐字一致，保证存量行为不变 */
    public static final Row[] MODIFIED = {
        new Row("立项", 0, 1, 0, ""),
        new Row("结构评估", 2, 2, 0, "根据玻璃尺寸和摄像头孔位与R角来综合评估结构是否支持立项。"),
        new Row("立项准备", 5, 3, 0, "根据项目型号收手机，拆分成机板和屏幕分体状态，交给触摸方案公司抓取触摸协议，明确是否可以破解协议以及用哪颗物料可以满足技术标准。"),
        new Row("显示评估", 2, 4, 0, "提供机板和原屏给到显示方案公司，并告知触摸方案商建议使用的触摸IC料号及规格书与触摸原理图，让显示方案公司抓取显示协议，根据手机的分辨率与刷新率和玻璃的分辨率综合评估用哪颗码片物料，以及驱动IC。"),
        new Row("排线图纸", 3, 5, 0, "根据触摸方案公司建议的触摸IC和显示方案公司建议的码片，开始画图纸，一般都可以画，后期一般是谁画的图纸就和谁买码片。"),
        new Row("排线打样", 4, 6, 0, "出图纸后，把图纸给到排线工厂打样，一般打10PCS，码片和触摸IC需要找方案公司提供，哪个公司画的排线图纸就找哪个公司寄码片，触摸公司寄触摸IC。"),
        new Row("FOG打样", 2, 7, 0, "排线打样好之后直接让工厂寄给打样加工厂，同时需要寄驱动IC过去和玻璃过去，一般先打样5PCS。"),
        new Row("显示调试", 5, 8, 0, "FOG打样直接寄到显示方案公司，并且提供机板，开始调试显示功能。其他兼容的基板，等没什么大问题再去购买给方案公司做兼容。"),
        new Row("触摸调试", 5, 9, 0, "初版显示做好以后，移交机板和FOG去触摸方案公司调试触摸。同时保留一个机板和FOG去盖板厂根据屏幕的实际显示效果开模做盖板样品，然后去背贴厂开背贴样品。"),
        new Row("背贴盖板打样", 2, 10, 0, "使用保留的一个机板和FOG去盖板厂根据屏幕的实际显示效果开模做盖板样品，然后去背贴厂开背贴样品。"),
        new Row("总成样品", 2, 11, 0, "将盖板和背贴样品寄到加工厂做成总成，需要寄2PCS总成和机板过去方案公司优化触摸。"),
        new Row("测试", 5, 12, 0, "开始测试，需要测试结构/显示/触摸，详见测试文档。"),
        new Row("小批量", 3, 13, 1, "测试没问题之后，下物料寄到工厂，先进行100PCS的小批量，到货后过一遍，没有批次问题，就可以结项了。"),
        new Row("结项", 0, 14, 1, "结项，通知工厂开始量产。")
    };

    /**
     * 原配要走的 7 个阶段名称（顺序即执行顺序）——与用户 2026-09-21 明确列出的完全一致。
     * <p>它就是"改配去掉 7 个显示/触摸相关阶段"（结构评估 / 立项准备 / 显示评估 / 排线图纸 /
     * FOG打样 / 显示调试 / 触摸调试）的结果。</p>
     */
    public static final String[] MATCHED_NAMES = {
        "立项", "排线打样", "背贴盖板打样", "总成样品", "测试", "小批量", "结项"
    };

    /**
     * 原配（7 个）：**从改配那套按名称取行**（共享 默认天数/备注/同步标记），只把 sort_order 重排为 1..7。
     * <p>刻意"派生"而不是再抄一份：两套里的这 7 个阶段是同一个作业步骤，手抄一份备注必然日后漂移。
     * （与建库时那条 {@code INSERT ... SELECT ... FIELD(name, ...)} 迁移 SQL 完全同构。）</p>
     */
    public static final Row[] MATCHED = buildMatched();

    private static Row[] buildMatched() {
        Row[] out = new Row[MATCHED_NAMES.length];
        for (int i = 0; i < MATCHED_NAMES.length; i++) {
            Row src = findByName(MATCHED_NAMES[i]);
            if (src == null) {
                throw new IllegalStateException("原配阶段[" + MATCHED_NAMES[i] + "]不在改配模板中，默认数据自相矛盾");
            }
            out[i] = new Row(src.name(), src.defaultDays(), i + 1, src.productStatusSync(), src.remark());
        }
        return out;
    }

    private static Row findByName(String name) {
        for (Row r : MODIFIED) {
            if (r.name().equals(name)) return r;
        }
        return null;
    }

    /** 规格 code（与 {@code PhaseTemplate.specType} 一致）：改配 */
    public static final String SPEC_MODIFIED = ProductSpec.MODIFIED.getCode();
    /** 规格 code：原配 */
    public static final String SPEC_MATCHED = ProductSpec.MATCHED.getCode();
}
