package com.beichen.erp.common;

/**
 * 默认 物料类型（玻璃/驱动IC/触摸IC/码片IC/排线/盖板/背贴/钢板/COP）
 * <p>
 * 统一管理默认 物料类型，供 DataInitializer（启动初始化）与 ClearController（清空数据后重置）共用，避免两处不一致。
 * </p>
 */
public class DefaultMaterialTypes {

    public static final String[] TYPES = {"玻璃", "驱动IC", "触摸IC", "码片IC", "排线", "盖板", "背贴", "钢板", "COP"};

    private DefaultMaterialTypes() {}
}
