package com.beichen.erp.outsource.common;

/**
 * 委外库存变动类型枚举
 * <p>
 * 管理 outsource_stock_log.change_type 字段。
 * 委外库存独立于成品库存(inventory_stock_log)，使用独立的枚举体系。
 * </p>
 */
public enum OutsourceStockChangeType {

    /** 发料出：加工单发料时从我方仓库扣减物料 */
    DELIVERY_OUT("发料出"),
    /** 发料入：发料逆向操作，恢复我方仓库物料 */
    DELIVERY_IN("发料入"),
    /** 调拨出：加工单调拨时从调出仓库扣减物料 */
    TRANSFER_OUT("调拨出"),
    /** 调拨入：调拨操作时向调入仓库增加物料 */
    TRANSFER_IN("调拨入"),
    /** 退料入：退料时将物料退回仓库 */
    RETURN_IN("退料入"),
    /** 取消发料：收发单取消时恢复已发物料 */
    CANCEL_DELIVERY("取消发料"),
    /** 取消调拨出：取消调拨出库时恢复库存 */
    CANCEL_TRANSFER_OUT("取消调拨出"),
    /** 取消调拨入：取消调拨入库时扣回库存 */
    CANCEL_TRANSFER_IN("取消调拨入"),
    /** 编辑回滚-发料：编辑收发单后回滚已发的物料 */
    EDIT_ROLLBACK("编辑回滚-发料"),
    /** 编辑回滚-调拨出：编辑收发单后回滚调拨出的物料 */
    EDIT_ROLLBACK_TRANSFER_OUT("编辑回滚-调拨出"),
    /** 编辑回滚-调拨入：编辑收发单后回滚调拨入的物料 */
    EDIT_ROLLBACK_TRANSFER_IN("编辑回滚-调拨入"),
    /** 编辑-发料：编辑后重新发料 */
    EDIT_DELIVERY("编辑-发料");

    private final String label;

    OutsourceStockChangeType(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }
}
