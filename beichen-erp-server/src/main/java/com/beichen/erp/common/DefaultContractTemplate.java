package com.beichen.erp.common;

/**
 * 默认合同模板常量
 * <p>
 * 统一管理「加工合同」「采购合同」默认模板的 HTML 内容与占位符，
 * 供 DataInitializer（启动初始化）与 ClearController（清空数据后重置）共用，避免内容不一致。
 * </p>
 */
public final class DefaultContractTemplate {

    private DefaultContractTemplate() {}

    /** 委外加工合同模板类型 */
    public static final String TYPE_PROCESSING = "加工合同";
    /** 物料采购合同模板类型 */
    public static final String TYPE_PURCHASE = "采购合同";

    /** 委外加工合同默认模板名称 */
    public static final String NAME_PROCESSING = "委外加工合同";
    /** 物料采购合同默认模板名称 */
    public static final String NAME_PURCHASE = "物料采购合同";

    /** 委外加工合同默认模板内容（占位符由导出时动态替换） */
    public static final String PROCESSING_CONTRACT_HTML =
        "<h1 style=\"text-align: center;\">委外加工合同</h1><p><br></p>"
        + "<p><span style=\"color: #eb2f96; background-color: #fff0f6;\"><strong>{合同信息}</strong></span></p><p><br></p>"
        + "<p>就甲方委托乙方为其生产加工本协议中所列明的产品事宜，经双方友好协商共同达成并签署以下条款：</p><p><br></p>"
        + "<h3>一、委托加工产品数量及价格</h3>"
        + "<p><span style=\"color: #eb2f96; background-color: #fff0f6;\"><strong>{产品表格}</strong></span></p><p><br></p>"
        + "<h3>二、甲方提供物料明细</h3>"
        + "<p><span style=\"color: #13c2c2; background-color: #e6fffb;\"><strong>{物料表格}</strong></span></p><p><br></p>"
        + "<h3>三、订单备注</h3>"
        + "<p><span style=\"color: #f5222d; background-color: #fff1f0;\"><strong>{备注}</strong></span></p><p><br></p>"
        + "<p><span style=\"color: #eb2f96; background-color: #fff0f6;\"><strong>{签名区}</strong></span></p>";

    /** 物料采购合同默认模板内容（占位符由导出时动态替换） */
    public static final String PURCHASE_CONTRACT_HTML =
        "<h1 style=\"text-align: center;\">物料采购合同</h1><p><br></p>"
        + "<p><span style=\"color: #eb2f96; background-color: #fff0f6;\"><strong>{合同信息}</strong></span></p><p><br></p>"
        + "<p>就甲方向乙方采购本协议中所列明的物料事宜，经双方友好协商共同达成并签署以下条款：</p><p><br></p>"
        + "<h3>一、采购物料明细</h3>"
        + "<p><span style=\"color: #ff4d4f; background-color: #fff1f0;\"><strong>{物料明细表格}</strong></span></p><p><br></p>"
        + "<h3>二、订单备注</h3>"
        + "<p><span style=\"color: #f5222d; background-color: #fff1f0;\"><strong>{备注}</strong></span></p><p><br></p>"
        + "<p><span style=\"color: #eb2f96; background-color: #fff0f6;\"><strong>{签名区}</strong></span></p>";
}
