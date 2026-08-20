package com.beichen.erp.memo.common;

import lombok.Getter;

/**
 * 备忘录状态枚举
 * <p>OPEN=未处理 CLOSED=已关闭，管理 memo.status 字段。</p>
 */
@Getter
public enum MemoStatus {

    /** 未处理 */
    OPEN("OPEN", "未处理"),
    /** 已关闭 */
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String label;

    MemoStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
