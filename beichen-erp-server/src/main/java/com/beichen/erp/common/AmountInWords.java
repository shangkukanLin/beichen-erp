package com.beichen.erp.common;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 人民币金额大写（2026-09-29 新增，供账单/对账单等财务导出使用）。
 *
 * <p><b>为什么要自己写</b>：对账单是发给往来单位核对/盖章的，金额必须同时给「小写 + 大写」，
 * 大写写错比不写更容易出对账纠纷（收付两个方向都会被拿来主张权利）⇒ 规则必须落成可测的代码，
 * 不能用"看起来差不多"的字符串拼接。</p>
 *
 * <p>规则（国标财务写法）：</p>
 * <ul>
 *   <li>数字：零壹贰叁肆伍陆柒捌玖；节内单位：仟佰拾（无「个」）；节单位：万、亿、兆；</li>
 *   <li><b>零的合并</b>：整节为 0 压缩成一个「零」，且不与前一个「零」连写（100000001 ⇒ 壹亿零壹元整）；</li>
 *   <li><b>节内补零</b>：低位节的值小于 1000（千位为 0）时前面要补「零」（10001 ⇒ 壹万零壹元整）；</li>
 *   <li>角分：角为 0 且分非 0 ⇒ 补「零」（1.05 ⇒ 壹元零伍分）；分为 0 ⇒ 收尾「整」（1.50 ⇒ 壹元伍角整）；</li>
 *   <li>金额为 0 ⇒ 「零元整」；负数 ⇒ 前缀「负」（退款/冲减方向，语义由调用方保证）；</li>
 *   <li>入参按 **HALF_UP 四舍五入到分** 后计算（不静默截断，避免与大写不一致）。</li>
 * </ul>
 *
 * <p>上限：整数部分支持到「兆」级（16 位）；超出范围的极端值退化为不给节单位（不抛异常、不阻塞导出）。</p>
 *
 * <p>⚠️ 仓库守卫（`be-build.ps1`）：**禁止单引号里的中文**（防"用中文当条件/SQL 值"，枚举一律按 code 比对）
 * ⇒ 本类的单字一律用<b>双引号字符串</b>追加（中文在此是**输出数据**、不是判断条件）。</p>
 */
public final class AmountInWords {

    private static final char[] DIGITS = "零壹贰叁肆伍陆柒捌玖".toCharArray();
    private static final String[] UNITS = {"仟", "佰", "拾", ""};
    private static final String[] SECTIONS = {"", "万", "亿", "兆"};
    private static final String ZERO = "零";
    private static final String YUAN = "元";
    private static final String JIAO = "角";
    private static final String FEN = "分";
    private static final String WHOLE = "整";
    private static final String MINUS = "负";

    private AmountInWords() {}

    /** 金额转人民币大写；null 视为 0 ⇒「零元整」 */
    public static String of(BigDecimal amount) {
        if (amount == null) return ZERO + YUAN + WHOLE;
        boolean negative = amount.signum() < 0;
        BigDecimal v = amount.abs().setScale(2, RoundingMode.HALF_UP);
        BigInteger yuan = v.toBigInteger();
        int jiao = v.movePointRight(1).toBigInteger().mod(BigInteger.TEN).intValue();
        int fen = v.movePointRight(2).toBigInteger().mod(BigInteger.TEN).intValue();

        StringBuilder sb = new StringBuilder();
        if (negative) sb.append(MINUS);
        sb.append(yuan.signum() == 0 ? ZERO : yuanPart(yuan)).append(YUAN);
        if (jiao == 0 && fen == 0) {
            sb.append(WHOLE);
        } else {
            if (jiao == 0) sb.append(ZERO);
            else sb.append(DIGITS[jiao]).append(JIAO);
            if (fen != 0) sb.append(DIGITS[fen]).append(FEN);
            else sb.append(WHOLE);
        }
        return sb.toString();
    }

    /** 整数部分（元）转大写：从低位每 4 位一节，逐节拼「节内 + 节单位」 */
    private static String yuanPart(BigInteger yuan) {
        String s = yuan.toString();
        int len = s.length();
        List<Integer> secs = new ArrayList<>();
        for (int end = len; end > 0; end -= 4) {
            secs.add(Integer.parseInt(s.substring(Math.max(0, end - 4), end)));
        }
        StringBuilder sb = new StringBuilder();
        boolean pendingZero = false;                     // 中间遇到过整节为 0 ⇒ 下一个非零节前补一个「零」
        for (int i = secs.size() - 1; i >= 0; i--) {
            int v = secs.get(i);
            if (v == 0) { pendingZero = true; continue; }
            boolean needZero = pendingZero || (i < secs.size() - 1 && v < 1000);
            if (needZero && sb.length() > 0) sb.append(ZERO);
            sb.append(section(v));
            if (i < SECTIONS.length) sb.append(SECTIONS[i]);
            pendingZero = false;
        }
        return sb.toString();
    }

    /** 一节（1..9999）转大写：节内中间为 0 补一个「零」（1005 ⇒ 壹仟零伍），末尾 0 不补 */
    private static String section(int v) {
        StringBuilder sb = new StringBuilder();
        int[] d = {(v / 1000) % 10, (v / 100) % 10, (v / 10) % 10, v % 10};
        boolean zero = false;
        for (int i = 0; i < 4; i++) {
            if (d[i] == 0) { zero = sb.length() > 0; continue; }
            if (zero) { sb.append(ZERO); zero = false; }
            sb.append(DIGITS[d[i]]).append(UNITS[i]);
        }
        return sb.toString();
    }
}
