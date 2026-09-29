package com.beichen.erp.common;

import com.beichen.erp.exception.BusinessException;

/**
 * 单号序号解析 / 生成（F7-109 / F7-116 · 2026-09-20）。
 *
 * <p><b>背景</b>：本工程有 5 处"前缀 + yyyyMMdd + 三位序号"的单号生成器（销售单 / 销售出库单 /
 * 销售退货单 / 销售换货单 / 退货整理单），原先都是同一段手写逻辑：</p>
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

    /**
     * F7-261（2026-09-30 审核批 H 收口）：**生成唯一单号** —— 在 {@link #format} 之上补"占用检测 + 递增重试"，
     * 与财务侧的 F7-39#3 惯用法（`for (i&lt;999) { if (selectCount(...) == 0) return code; seq++; }`）同口径。
     *
     * <p><b>为什么需要</b>：本类原先只做"尾段数字解析 + 格式化"，**不含占用检测**（类注释里写的是"交由唯一索引
     * 兜底报错"）；调用方一律 `last + 1 → format` 直接返回 ⇒ 两个并发请求读到同一条 `last` 就会生成同一单号，
     * 第二条 INSERT 撞 `uk_code` 抛**数据库原始异常**（数据本身安全：31 张含单号列的表都有唯一键 ✓，
     * 但用户看到的是不可读的 SQL 报错、且与财务侧"自动递增重试"的口径不一致）。</p>
     *
     * <p>用法（每个调用点只改一行）：</p>
     * <pre>
     * return BillNoSeq.formatUnique(prefix, seq,
     *         cand -&gt; mapper.selectCount(new LambdaQueryWrapper&lt;Xxx&gt;().eq(Xxx::getCode, cand)) &gt; 0);
     * </pre>
     *
     * @param prefix   单号前缀（含日期段）
     * @param startSeq 起始序号（通常 = 已存在最大尾号 + 1；小于 1 时按 1 处理）
     * @param exists   占用判定：给定候选单号返回 true 表示已被占用
     * @return 一个未被占用的单号
     * @throws BusinessException 连续 999 个候选都被占用（当日单号用尽，需人工介入）
     */
    public static String formatUnique(String prefix, int startSeq, java.util.function.Predicate<String> exists) {
        int seq = Math.max(startSeq, 1);
        for (int i = 0; i < 999; i++) {
            String code = format(prefix, seq);
            if (exists == null || !exists.test(code)) {
                return code;
            }
            seq++;
        }
        throw new BusinessException("单号已用尽（前缀 " + prefix + "），请检查当日单据量或联系管理员");
    }
}
