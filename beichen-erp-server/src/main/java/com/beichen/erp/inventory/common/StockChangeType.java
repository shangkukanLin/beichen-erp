package com.beichen.erp.inventory.common;

/**
 * 库存变动类型枚举
 * <p>统一管理 inventory_stock_log.change_type 字段，禁止散落硬编码中文字符串。</p>
 */
public enum StockChangeType {

    // ===== 采购相关 =====
    /** 采购入库：采购单/采购入库单审核后，成品入库增加库存 */
    PURCHASE_IN("采购入库"),
    /** 采购反审核：采购单反审核后，冲回已入库的库存 */
    PURCHASE_UN_AUDIT("采购反审核"),
    /** 退货出库：采购退货单审核后，退货出库扣减库存 */
    RETURN_OUT("退货出库"),
    /** 退货反审核：采购退货单反审核后，恢复已扣减的库存 */
    RETURN_UN_AUDIT("退货反审核"),
    /** 采购换货退回出库：采购换货单审核后，退回货品从我方仓按品质扣减（退给供货商，2026-09-18） */
    PURCHASE_EXCHANGE_OUT("采购换货退回出库"),
    /** 采购换货入库：采购换货单审核后，供货商换回的良品入我方仓 */
    PURCHASE_EXCHANGE_IN("采购换货入库"),
    /** 采购换货反审核：采购换货单反审核后，恢复退回库存并扣回换入库存 */
    PURCHASE_EXCHANGE_UN_AUDIT("采购换货反审核"),

    // ===== 销售相关 =====
    /** 销售出库：销售单/销售出库单审核后，成品出库扣减库存 */
    SALE_OUT("销售出库"),
    /** 销售反审核：销售单反审核后，原路加回已出库库存 */
    SALE_OUT_UN_AUDIT("销售反审核"),
    /** 销售退货入库：销售退货单审核后，客户退回不良品入库增加库存 */
    SALE_RETURN_IN("销售退货入库"),
    /** 销售退货反审核：销售退货单反审核后，扣减已入库的不良品库存 */
    SALE_RETURN_UN_AUDIT("销售退货反审核"),
    /** 换货退回入库：销售换货单审核后，客户退回货品入售后仓（品质 PENDING 待整理） */
    EXCHANGE_IN("换货退回入库"),
    /** 换货出库：销售换货单审核后，换出货品从成品仓按指定品质扣减 */
    EXCHANGE_OUT("换货出库"),
    /** 换货反审核：销售换货单反审核后，扣回换出库存并扣减已退回库存 */
    EXCHANGE_UN_AUDIT("换货反审核"),

    // ===== 移仓相关 =====
    /** 移仓出：移仓单审核后，从移出仓库扣减库存 */
    MOVE_OUT("移仓出库"),
    /** 移仓入：移仓单审核后，向移入仓库增加库存 */
    MOVE_IN("移仓入库"),
    /** 物料移仓出：物料移仓单审核后，从移出仓库扣减物料库存（2026-09-24） */
    MATERIAL_MOVE_OUT("物料移仓出"),
    /** 物料移仓入：物料移仓单审核后，向移入仓库增加物料库存（2026-09-24） */
    MATERIAL_MOVE_IN("物料移仓入"),

    // ===== 其他出入库 =====
    /** 其他入库：其他出入库-入库，手动调整增加库存 */
    OTHER_IN("其他入库"),
    /** 其他出库：其他出入库-出库，手动调整扣减库存 */
    OTHER_OUT("其他出库"),
    /** 取消入库：其他出入库取消入库，撤销入库操作 */
    CANCEL_IN("取消入库"),
    /** 取消出库：其他出入库取消出库，撤销出库操作 */
    CANCEL_OUT("取消出库"),

    // ===== 委外相关 =====
    /** 委外发料出库：加工单发料，从我方仓库扣除物料 */
    OUTSOURCE_DELIVERY_OUT("委外发料出库"),
    /** 委外交货入库：加工单完工交货，成品入库 */
    OUTSOURCE_FINISH_IN("委外收货入库"),
    /** 委外退料出：委外退货退回供应商，从委外仓扣减 */
    OUTSOURCE_RETURN_OUT("委外退料出"),
    /** 委外退料出反审核：委外加工退货取消审核，恢复已出库成品 */
    OUTSOURCE_RETURN_OUT_UN_AUDIT("委外退料出反审核"),
    /** 委外退不良：加工单退回不良品 */
    OUTSOURCE_DEFECT_RETURN("委外退不良"),
    /** 交货回滚：委外交货回滚，撤销入库操作 */
    OUTSOURCE_ROLLBACK("收货回滚"),
    /** 交货扣料：委外交货收货时按 BOM 扣减子物料 */
    OUTSOURCE_CONSUME("收货扣料"),
    /** 取消交货扣料：委外交货反审核时恢复已扣减的子物料 */
    CANCEL_OUTSOURCE_CONSUME("取消收货扣料"),
    /** 委外退不良反审核：退不良反审核时扣回已还的物料 */
    OUTSOURCE_DEFECT_RETURN_UN_AUDIT("委外退不良反审核"),
    /** 成品（加工退货）入委外仓：无单加工退货审核，退回成品以 PRODUCT_DEFECT 形态进加工厂委外仓（2026-09-25 P1-1） */
    OUTSOURCE_DEFECT_IN("成品加工退货入委外仓"),
    /** 核销成品（加工退货）：无单加工退货反审核，把已入委外仓的成品（加工退货）核销扣回（2026-09-25 P1-1） */
    CANCEL_OUTSOURCE_DEFECT_IN("核销成品加工退货"),
    /** 加工返回单-核销在厂成品：返回单审核，扣减委外仓 PRODUCT_DEFECT 行（2026-09-25 P1-2） */
    OUTSOURCE_BACK_CONSUME("返回单核销在厂成品"),
    /** 加工返回单-恢复在厂成品：返回单反审核，回补委外仓 PRODUCT_DEFECT 行（2026-09-25 P1-2） */
    CANCEL_OUTSOURCE_BACK_CONSUME("返回单恢复在厂成品"),
    /** 加工返回单-修好成品回仓：返回单审核，修好成品按回仓品质入我方仓（2026-09-25 P1-2） */
    OUTSOURCE_BACK_IN("修好成品回仓"),
    /** 加工返回单-修好成品扣回：返回单反审核，从我方仓扣回已入库成品（2026-09-25 P1-2） */
    CANCEL_OUTSOURCE_BACK_IN("修好成品扣回"),
    /** 加工返回单-实际用料出仓：返回单审核，按实际用料（可超 BOM）从委外仓扣物料（2026-09-25 P1-2） */
    OUTSOURCE_BACK_MATERIAL("返回单用料出仓"),
    /** 加工返回单-用料恢复：返回单反审核，等量回补委外仓物料（2026-09-25 P1-2） */
    CANCEL_OUTSOURCE_BACK_MATERIAL("返回单用料恢复"),
    /** 委外维修出库：维修退货单送修审核，从我方成品仓扣除待修品（2026-09-17） */
    OUTSOURCE_REPAIR_OUT("委外维修出库"),
    /** 委外维修出库反审核：维修退货单反审核，把送修品加回我方成品仓 */
    OUTSOURCE_REPAIR_OUT_UN_AUDIT("委外维修出库反审核"),
    /** 委外维修入库：维修返回登记，工厂修好的成品入我方仓库 */
    OUTSOURCE_REPAIR_IN("委外维修入库"),
    /** 取消委外维修入库：撤销维修返回登记，把已入库成品扣回 */
    CANCEL_OUTSOURCE_REPAIR_IN("取消委外维修入库"),
    /** 成品（维修退货）入委外仓：维修退货送修审核，以 PRODUCT_REPAIR 形态进加工厂委外仓（2026-09-25 P2-1） */
    OUTSOURCE_REPAIR_STOCK_IN("成品维修退货入委外仓"),
    /** 核销成品（维修退货）：维修返回登记扣减在厂行（−）/ 送修反审核恢复（+）时扣减（2026-09-25 P2-1）。方向由 change_quantity 符号区分 */
    CANCEL_OUTSOURCE_REPAIR_STOCK_IN("核销成品维修退货"),
    /** 维修用料出仓：维修返回登记按实际用料（可超 BOM）从委外仓扣物料（2026-09-25 P2-1） */
    OUTSOURCE_REPAIR_MATERIAL("维修用料出仓"),
    /** 维修用料恢复：撤销维修返回，等量回补委外仓物料（2026-09-25 P2-1） */
    CANCEL_OUTSOURCE_REPAIR_MATERIAL("维修用料恢复"),
    /** 取消发料：收发单取消，恢复已发物料库存 */
    OUTSOURCE_CANCEL_DELIVERY("取消发料"),
    /** 编辑回滚-发料：编辑收发单后回滚已发物料 */
    OUTSOURCE_EDIT_ROLLBACK("编辑回滚-发料"),

    // ===== 委外交货细分 =====
    /** 发料出：委外交货-发料出库 */
    DELIVERY_OUT("发料出"),
    /** 发料入：发料逆向操作，恢复我方仓库物料 */
    DELIVERY_IN("发料入"),
    /** 调拨出：委外交货-调拨出库 */
    TRANSFER_OUT("调拨出"),
    /** 调拨入：委外交货-调拨入库 */
    TRANSFER_IN("调拨入"),
    /**
     * 取消发料：收发单取消，恢复已发物料库存。
     * <p>【C5 · 2026-09-12 起停用】发料反审核两个仓已统一用 {@link #OUTSOURCE_CANCEL_DELIVERY}；
     * 本常量保留仅为兼容历史流水的展示（库中无该 code 的流水）。</p>
     */
    @Deprecated
    CANCEL_DELIVERY("取消发料"),
    /** 取消调拨出：取消调拨出库时恢复库存 */
    CANCEL_TRANSFER_OUT("取消调拨出"),
    /** 取消调拨入：取消调拨入库时扣回库存 */
    CANCEL_TRANSFER_IN("取消调拨入"),
    /** 退料入：退料时将物料退回仓库 */
    RETURN_IN("退料入"),
    /** 取消退料入：取消退料单时从仓库扣回物料 */
    CANCEL_RETURN_IN("取消退料入"),
    /** 物料收货入：委外物料订单收货时入目标仓库 */
    RECEIVE_IN("物料收货入"),
    /** 物料退不良出：物料订单退不良时从目标仓库扣减 */
    DEFECT_OUT("物料退不良出"),
    /** 取消物料收货入：反审核时扣回目标仓库 */
    CANCEL_RECEIVE_IN("取消物料收货入"),
    /** 取消物料退不良出：反审核时恢复目标仓库 */
    CANCEL_DEFECT_OUT("取消物料退不良出"),
    /** 委外收货扣子物料：委外物料订单收货时按 BOM 子物料从供应商仓扣减（可扣成负数） */
    OUTSOURCE_COMPONENT_CONSUME("委外收货扣子物料"),
    /** 退审恢复子物料：委外物料订单收货反审核时把已扣的子物料加回 */
    CANCEL_OUTSOURCE_COMPONENT_CONSUME("退审恢复子物料"),
    /** 委外物料退货出：委外物料退货单审核，物料从源仓扣减退回物料商 */
    MATERIAL_RETURN_OUT("委外物料退货出"),
    /** 取消委外物料退货出：委外物料退货取消审核，物料恢复源仓 */
    CANCEL_MATERIAL_RETURN_OUT("取消委外物料退货出"),
    /** 委外物料维修出：物料退货（维修返还）审核，把待修物料送出源仓，交供应商维修（2026-09-17） */
    MATERIAL_REPAIR_OUT("委外物料维修出"),
    /** 委外物料维修出反审核：维修返还单反审核，把送修物料恢复源仓 */
    MATERIAL_REPAIR_OUT_UN_AUDIT("委外物料维修出反审核"),
    /** 委外物料维修入：登记维修返回，供应商修好的物料入我方仓库 */
    MATERIAL_REPAIR_IN("委外物料维修入"),
    /** 取消委外物料维修入：撤销维修返回登记，把已入库物料扣回 */
    CANCEL_MATERIAL_REPAIR_IN("取消委外物料维修入"),

    // ===== 供应商清算 =====
    /** 清算退料入：供应商清算后退料入库 */
    SETTLEMENT_RETURN_IN("清算退料入"),
    /** 清算退料出：供应商清算时从委外仓扣减物料 */
    SETTLEMENT_RETURN_OUT("清算退料出"),

    // ===== 初始化 =====
    /** 期初导入：系统初始化时导入期初库存 */
    INIT("期初导入"),

    // ===== 品质重分类 =====
    RECLASSIFY_OUT("重分类出"),
    RECLASSIFY_IN("重分类入"),
    CANCEL_RECLASSIFY_OUT("取消重分类出"),
    CANCEL_RECLASSIFY_IN("取消重分类入"),

    // ===== 库存盘点 =====
    /** 盘点盘盈：实盘大于账面，补记入库 */
    STOCK_TAKE_IN("盘点盘盈"),
    /** 盘点盘亏：实盘小于账面，冲减库存 */
    STOCK_TAKE_OUT("盘点盘亏"),

    // ===== 退货整理 =====
    RETURN_SORT_OUT("退货整理出"),
    RETURN_SORT_IN("退货整理入"),
    CANCEL_RETURN_SORT_OUT("取消退货整理出"),
    CANCEL_RETURN_SORT_IN("取消退货整理入"),

    // ===== 报损 =====
    /** 报损出库：报损单审核，库存报损扣减（成品与委外物料共用同一变动类型） */
    LOSS_OUT("报损出库"),
    /** 取消报损出：报损单反审核，把报损扣减的数量加回 */
    CANCEL_LOSS_OUT("取消报损出");

    private final String label;

    StockChangeType(String label) {
        this.label = label;
    }

    /** 前端显示的中文名称 */
    public String getLabel() {
        return label;
    }

    /** 枚举常量名，即存入数据库的值（如 PURCHASE_IN） */
    public String getCode() {
        return name();
    }

    /** 根据 code 获取中文标签，找不到返回 code 本身 */
    public static String labelOf(String code) {
        StockChangeType t = fromCode(code);
        return t != null ? t.label : (code != null ? code : "");
    }

    /**
     * 根据数据库存储的 code（枚举名）反向查找。
     */
    public static StockChangeType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (StockChangeType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
