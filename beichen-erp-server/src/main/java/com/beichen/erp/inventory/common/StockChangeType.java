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
    /** 退货出库：成品退货单审核后，退货出库扣减库存 */
    RETURN_OUT("退货出库"),
    /** 退货反审核：成品退货单反审核后，恢复已扣减的库存 */
    RETURN_UN_AUDIT("退货反审核"),

    // ===== 销售相关 =====
    /** 销售出库：销售单/销售出库单审核后，成品出库扣减库存 */
    SALE_OUT("销售出库"),
    /** 销售反审核：销售单反审核后，原路加回已出库库存 */
    SALE_OUT_UN_AUDIT("销售反审核"),
    /** 销售退货入库：销售退货单审核后，客户退回不良品入库增加库存 */
    SALE_RETURN_IN("销售退货入库"),
    /** 销售退货反审核：销售退货单反审核后，扣减已入库的不良品库存 */
    SALE_RETURN_UN_AUDIT("销售退货反审核"),

    // ===== 移仓相关 =====
    /** 移仓出：移仓单审核后，从移出仓库扣减库存 */
    MOVE_OUT("移仓出库"),
    /** 移仓入：移仓单审核后，向移入仓库增加库存 */
    MOVE_IN("移仓入库"),

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
    OUTSOURCE_FINISH_IN("委外交货入库"),
    /** 委外退料出：委外退货退回供应商，从委外仓扣减 */
    OUTSOURCE_RETURN_OUT("委外退料出"),
    /** 委外退不良：加工单退回不良品 */
    OUTSOURCE_DEFECT_RETURN("委外退不良"),
    /** 交货回滚：委外交货回滚，撤销入库操作 */
    OUTSOURCE_ROLLBACK("交货回滚"),
    /** 取消发料：收发单取消，恢复已发物料库存 */
    OUTSOURCE_CANCEL_DELIVERY("取消发料"),
    /** 编辑回滚-发料：编辑收发单后回滚已发物料 */
    OUTSOURCE_EDIT_ROLLBACK("编辑回滚-发料"),

    // ===== 委外交货细分 =====
    /** 发料出：委外交货-发料出库 */
    DELIVERY_OUT("发料出"),
    /** 调拨出：委外交货-调拨出库 */
    TRANSFER_OUT("调拨出"),
    /** 调拨入：委外交货-调拨入库 */
    TRANSFER_IN("调拨入"),

    // ===== 供应商清算 =====
    /** 清算退料入：供应商清算后退料入库 */
    SETTLEMENT_RETURN_IN("清算退料入"),

    // ===== 初始化 =====
    /** 期初导入：系统初始化时导入期初库存 */
    INIT("期初导入"),

    // ===== 品质重分类 =====
    RECLASSIFY_OUT("重分类出"),
    RECLASSIFY_IN("重分类入"),
    CANCEL_RECLASSIFY_OUT("取消重分类出"),
    CANCEL_RECLASSIFY_IN("取消重分类入");

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

    /**
     * 根据数据库存储的 code（枚举名）反向查找，兼容存量数据迁移。
     * 如果找不到匹配的 code，则尝试用 label 匹配（兼容尚未迁移的中文旧数据）。
     */
    public static StockChangeType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        // 优先按枚举名匹配
        for (StockChangeType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        // 回退按中文 label 匹配（兼容未迁移的存量数据）
        for (StockChangeType t : values()) {
            if (t.label.equals(code)) return t;
        }
        return null;
    }
}
