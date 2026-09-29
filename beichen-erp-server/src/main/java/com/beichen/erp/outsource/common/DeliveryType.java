package com.beichen.erp.outsource.common;

/**
 * 委外交货类型枚举
 * <p>
 * 管理 outsource_delivery.delivery_type 字段。
 * 用于区分委外交货单的发货方式。
 * </p>
 */
public enum DeliveryType {

    /** 发料：从我方仓库发料到委外工厂 */
    DELIVERY("发料"),
    /** 收料：从委外工厂收回物料 */
    RECEIVE("收料"),
    /** 调拨：仓库间调拨物料 */
    TRANSFER("调拨"),
    /**
     * 退料：退回物料到供应商或仓库。
     * <p><b>2026-09-16 流程重构后已下线（仅历史数据/历史单据反审核使用）</b>：
     * 手工新建入口全部关闭；供应商清算"一键退料"、加工单结单"自动退料"改为生成
     * {@link #TRANSFER} 调拨单。保留 code 是为了历史单据仍能正确展示与回滚，
     * 且本枚举被成品交货表 outsource_order_delivery 共用，不能删值。</p>
     */
    RETURN("退料"),
    /** 退不良：退回不良品 */
    DEFECT_RETURN("退不良"),
    /**
     * 退货：把**已收**物料退回物料商（2026-09-29 用户口径「收货记录里的退货」）。
     *
     * <p>与 {@link #DEFECT_RETURN} 的分工（两者都扣同一个退货仓的库存，此前被用户判为"功能重复"）：</p>
     * <ul>
     *   <li>退不良：数量记在 {@code outsource_material_order_item.defect_returned_qty}（净已收口径），
     *       只有「折现退款」才动账（负应付）；</li>
     *   <li>退货：**直接冲减该订单的已收数量**（{@code received_quantity}），并按退货金额生成**负应付**
     *       （不再欠物料商这批货的钱）—— 冲减已收 = 这些货回到"未收"，订单可以重新收/重新下单数量口径一致。</li>
     * </ul>
     *
     * <p>⚠️ 刻意**不复用**历史值 {@link #RETURN}（那是 2026-09-16 已下线的"退料"，且被成品交货表
     * {@code outsource_order_delivery} 共用，语义不同）。</p>
     */
    RECEIVE_RETURN("退货");

    private final String label;

    DeliveryType(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /**
     * 根据数据库存储的 code（枚举名）反向查找。
     */
    public static DeliveryType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (DeliveryType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
