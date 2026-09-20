package com.beichen.erp.common;

/**
 * 单号序号解析 / 生成（F7-109 / F7-116 · 2026-09-20）。
 *
 * <p><b>背景</b>：本工程有 5 处"前缀 + yyyyMMdd + 三位序号"的单号生成器（销售单 / 销售出库单 /
 * 销售退单 / 销售换货单 / 退货整理单），原先都是同一段手写逻辑：</p>
 * <pre>
 *   try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; }
 *   catch (Exception e) { seq = 1; }
 * </pre>
 * <p>两个隐患：</p>
 * <ol>
 *   <li><b>序号 ≥ 1000 时截错</b>：{@code "…001000"} 取后三位得 {@code "000"} ⇒ {@code seq = 1}
 *       ⇒ 与当日已有单号撞车（靠 {@code uk_code} 唯一索引才没写脏数据，但接口会报重复键错误）；</li>
 *   <li><b>{@code catch → seq = 1} 静默回退</b>：任何解析异常都变成"重新从 001 开始"，
 *       把"格式异常"伪装成"当天第一单"，排查时看不出真因。</li>
 * </ol>
 *
 * <p><b>现口径</b>：统一走本类 —— 按<b>尾段连续数字</b>解析（不再假设固定 3 位）；
 * 解析失败返回 {@code 0}（调用方 {@code +1} 后从 001 起），且<b>不再吞异常</b>：
 * 真撞车时由 {@code uk_code} 抛可见错误、由全局异常处理器给出可读提示。</p>
 */
public final class BillNoSeq {

    private BillNoSeq() {
    }

    /**
     * 解析单号尾部的连续数字（不含前缀），如 {@code lastSeq("XS260919003", "XS260919") = 3}。
     *
     * <p>兼容带分隔符的写法（{@code "XS-260919-003"} ⇒ 尾段 {@code "003"} ⇒ 3）。
     * 前缀不匹配、尾段非数字、或数字超出 int 范围时返回 {@code 0}（调用方按"下一号为 001"处理）。</p>
     */
    public static int lastSeq(String code, String prefix) {
        if (code == null || prefix == null || !code.startsWith(prefix)) {
            return 0;
        }
        String tail = code.substring(prefix.length());
        int i = tail.length();
        while (i > 0 && Character.isDigit(tail.charAt(i - 1))) {
            i--;
        }
        if (i == tail.length()) {
            return 0;   // 尾段一个数字都没有
        }
        try {
            return Integer.parseInt(tail.substring(i));
        } catch (NumberFormatException e) {
            return 0;   // 超长数字：按"无法解析"处理，交由唯一索引兜底报错
        }
    }

    /**
     * 生成单号：{@code prefix + 三位序号}；序号超过 999 时**自动扩位**（{@code 1000} 就写 1000），
     * 而不是像旧实现那样取后三位（会把 1000 变成 000）。
     */
    public static String format(String prefix, int seq) {
        if (seq < 1000) {
            return prefix + String.format("%03d", seq);
        }
        return prefix + seq;
    }
}
