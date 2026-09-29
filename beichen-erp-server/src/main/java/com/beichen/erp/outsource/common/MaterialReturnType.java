package com.beichen.erp.outsource.common;

/**
 * 委外物料退货类型（2026-09-17 建立两态；<b>2026-09-28 按用户口径扩为三态</b>）
 * <ul>
 *   <li><b>ORDER 订单退料</b>（新）：**仅限关联订单且订单未结单(RECEIVING)** ——
 *       审核 = 源仓扣减（{@code MATERIAL_RETURN_OUT}）+ 扣减该订单的出货/收料数量（记入
 *       {@code outsource_material_order_item.order_returned_qty}）；<b>不动任何账务、不跟踪返回</b>；</li>
 *   <li><b>REFUND 退货退款</b>：物料退还供应商，供应商把货款退给我们 →
 *       审核 = 源仓扣减（{@code MATERIAL_RETURN_OUT}）+ 生成<b>对供应商的应收</b>（P2 起；当前仍为负向应付）；
 *       适用：无单，或关联订单**已结单**；</li>
 *   <li><b>REPAIR 维修返回</b>（原文案"维修退货"，2026-09-28 统一为"维修返回"）：物料送供应商维修，
 *       修好后<b>把物料还给我们</b> → 审核 = 源仓扣减（{@code MATERIAL_REPAIR_OUT}）+ 转入供应商委外仓；
 *       修好回来时在详情页「登记维修返回」入库（{@code MATERIAL_REPAIR_IN}），可逐行撤销。
 *       适用：无单，或关联订单**已结单**。</li>
 * </ul>
 * <p>历史值 {@code MATERIAL}（旧枚举"物料商退货"）与空值都在 {@link #normalize(String)} 归一到 REFUND，
 * 保证存量单据可正常显示与反审核。</p>
 */
public enum MaterialReturnType {

    /** 订单退料：关联订单未结单时把货退回供应商，扣源仓 + 扣该订单出货数量（不生成应收/应付） */
    ORDER("订单退料"),

    /** 退货退款：物料退回供应商并退款（生成对供应商的应收） */
    REFUND("退货退款"),

    /** 维修返回：送供应商维修，修好后返还（可收费；列表按未返回完/已返回完分流） */
    REPAIR("维修返回");

    private final String label;

    MaterialReturnType(String label) {
        this.label = label;
    }

    /** 前端显示的中文名称 */
    public String getLabel() {
        return label;
    }

    /** 存入数据库的枚举常量名（如 REFUND） */
    public String getCode() {
        return name();
    }

    public static MaterialReturnType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (MaterialReturnType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }

    /** 归一化：空值/历史值（MATERIAL）/非法值一律按 REFUND 处理 */
    public static MaterialReturnType normalize(String code) {
        MaterialReturnType t = fromCode(code);
        return t != null ? t : REFUND;
    }

    /** 是否维修返回 */
    public static boolean isRepair(String code) {
        return normalize(code) == REPAIR;
    }

    /** 是否订单退料（2026-09-28 新增类型：仅关联订单未结单时可用） */
    public static boolean isOrderReturn(String code) {
        return normalize(code) == ORDER;
    }

    /**
     * **类型 ↔ 关联订单状态**的合法性校验（2026-09-28 用户口径），返回 null = 通过，否则返回给用户的提示语。
     * <ul>
     *   <li>{@code ORDER} 订单退料 ⇒ 必须关联订单，且订单**未结单(RECEIVING)**；</li>
     *   <li>{@code REFUND}/{@code REPAIR} ⇒ 未关联订单（无单）放行；关联了订单则订单必须**已结单(FINISHED)**。</li>
     * </ul>
     *
     * @param code        本单类型（REFUND/REPAIR/ORDER）
     * @param hasOrder    是否关联了物料订单
     * @param orderStatus 关联订单的状态（未关联时传 null）
     */
    public static String checkOrderStatus(String code, boolean hasOrder, String orderStatus) {
        boolean receiving = MaterialOrderStatus.RECEIVING.getCode().equalsIgnoreCase(orderStatus);
        boolean finished = MaterialOrderStatus.FINISHED.getCode().equalsIgnoreCase(orderStatus);
        if (isOrderReturn(code)) {
            if (!hasOrder) return "订单退料必须关联物料订单（无单退料请选「退货退款」或「维修返回」）";
            if (!receiving) return "该物料订单未处于「生产中」：订单退料仅适用于未结单订单，请改用「退货退款」或「维修返回」";
            return null;
        }
        if (hasOrder && !finished)
            return "该物料订单未结单：请改用「订单退料」（退货退款 / 维修返回 仅适用于已结单订单）";
        return null;
    }
}
