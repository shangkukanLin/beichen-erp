package com.beichen.erp.common;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Excel 导出公共件（2026-09-29）：POI 样式工厂 + 单元格写值 + 打印设置 + 附件响应头。
 *
 * <p><b>为什么后端出 Excel</b>：前端 SheetJS 是**社区版**（`xlsx@0.18.5`），社区版**不支持单元格样式**
 * —— 字体/字号/边框/底色/对齐全都写不出来，也没有打印设置 ⇒ 只能导出"排得整齐的裸数据"。
 * 对账单要发给往来单位盖章核对，必须是**可打印的专业件**，所以走后端 POI。</p>
 *
 * <p><b>先例</b>：`outsource/controller/CloseReportController`（结单报表导出）早已这么做（同样的
 * `createStyle(...)` / 合并 / `setColumnWidth(w*256)` / `formulaCell` 公式写法）。本类把这套写法
 * 抽成可复用的公共件，新导出直接用；结单报表暂未迁移（避免动既有导出，属 P2）。</p>
 *
 * <p>⚠️ 只适用于 **XSSFWorkbook**（.xlsx）：底色/字体色走 XSSF 专用 API。</p>
 */
public class ExcelExportHelper {

    private final Workbook wb;

    public ExcelExportHelper(Workbook wb) { this.wb = wb; }

    /**
     * 样式工厂（四边同一边框，最常用——表头/合计行等）。
     *
     * @param bold    加粗
     * @param fontSize 字号（磅）
     * @param align   水平对齐
     * @param fmt     数字格式（如 {@code #,##0.00}）；null = 常规
     * @param border  四边边框；null = 不画边框（正文行最干净）
     */
    public CellStyle style(boolean bold, double fontSize, HorizontalAlignment align, String fmt, BorderStyle border) {
        return style(bold, fontSize, align, fmt, border, border, border, border, null, null);
    }

    /** 样式工厂（无边框版：大标题、抬头、说明文字用） */
    public CellStyle plain(boolean bold, double fontSize, HorizontalAlignment align, String fmt) {
        return style(bold, fontSize, align, fmt, null, null, null, null, null, null);
    }

    /**
     * 样式工厂（逐边控制，总计行/表头装饰用）。
     *
     * @param bgHex   底色 RGB（6 位十六进制，如 {@code D9E1F2}）；null = 无底色
     * @param fontHex 字体色 RGB；null = 默认黑
     */
    public CellStyle style(boolean bold, double fontSize, HorizontalAlignment align, String fmt,
                           BorderStyle top, BorderStyle bottom, BorderStyle left, BorderStyle right,
                           String bgHex, String fontHex) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = (XSSFFont) wb.createFont();
        f.setBold(bold);
        f.setFontHeightInPoints((short) fontSize);
        if (fontHex != null) f.setColor(new XSSFColor(rgb(fontHex), null));
        s.setFont(f);
        s.setAlignment(align);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(false);
        if (top != null) s.setBorderTop(top);
        if (bottom != null) s.setBorderBottom(bottom);
        if (left != null) s.setBorderLeft(left);
        if (right != null) s.setBorderRight(right);
        if (fmt != null) s.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat(fmt));
        if (bgHex != null) {
            ((XSSFCellStyle) s).setFillForegroundColor(new XSSFColor(rgb(bgHex), null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return s;
    }

    /** 写文本单元格（含 null 安全） */
    public static void text(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }

    /** 写数值单元格（**真数值**，不是文本 —— 这样才能在 Excel 里继续求和/排序） */
    public static void num(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? 0d : value.doubleValue());
        c.setCellStyle(style);
    }

    /** 写公式单元格（如合计行 {@code SUM(D9:D65)}） */
    public static void formula(Row row, int col, String formula, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellFormula(formula);
        c.setCellStyle(style);
    }

    /** 只上样式的空格（合计行整行铺底色/上边框时要显式建格） */
    public static void blank(Row row, int col, CellStyle style) {
        row.createCell(col).setCellStyle(style);
    }

    /**
     * 合并单元格。
     *
     * <p>⚠️ 参数顺序是 <b>(行, 列, 行, 列)</b> —— 即 {@code merge(sheet, 首行, 首列, 末行, 末列)}，
     * 与 POI 的 {@link CellRangeAddress}（{@code firstRow, lastRow, firstCol, lastCol}）**不同**！
     * 调用点几乎都是"同一行的若干列"，用本顺序读起来是 {@code (行, 从列, 行, 到列)}，不易写反；
     * 内部再换成 POI 的顺序（曾把两者搞混 ⇒ 运行时 POI 报
     * {@code Invalid cell range, having lastRow < firstRow}，见 2026-09-29 首次实证）。</p>
     */
    public static void merge(Sheet sheet, int firstRow, int firstCol, int lastRow, int lastCol) {
        sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, firstCol, lastCol));
    }

    /** 批量列宽（单位：字符宽，内部 ×256 换成 POI 的 1/256 字符） */
    public static void widths(Sheet sheet, int... wch) {
        for (int i = 0; i < wch.length; i++) sheet.setColumnWidth(i, wch[i] * 256);
    }

    /**
     * 打印设置：A4 + 横向 + **1 页宽**（列多也不会被切断）+ **重复表头行**（跨页看得出来）+ 页脚页码。
     * <p>对账单是要打出来给对方的单据，"能打印"本身就是专业度的一部分（电子表格截图发微信并不算）。</p>
     *
     * @param repeatHeaderRow 跨页重复的表头行号（0 基）；null = 不重复
     */
    public static void printSetup(Sheet sheet, boolean landscape, int fitWidth, Integer repeatHeaderRow, String footerCenter) {
        sheet.setFitToPage(true);
        PrintSetup ps = sheet.getPrintSetup();
        ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
        ps.setLandscape(landscape);
        ps.setFitWidth((short) fitWidth);
        ps.setFitHeight((short) 0);              // 0 = 高度自动（按宽度铺满后不限页数）
        sheet.setMargin(Sheet.LeftMargin, 0.5);
        sheet.setMargin(Sheet.RightMargin, 0.5);
        sheet.setMargin(Sheet.TopMargin, 0.6);
        sheet.setMargin(Sheet.BottomMargin, 0.6);
        sheet.setHorizontallyCenter(true);
        if (repeatHeaderRow != null) {
            sheet.setRepeatingRows(new CellRangeAddress(repeatHeaderRow, repeatHeaderRow, -1, -1));
        }
        if (footerCenter != null) sheet.getFooter().setCenter(footerCenter);
    }

    /**
     * 附件响应头：中文文件名用 RFC 5987 的 {@code filename*}（否则浏览器拿到乱码）+ ASCII 兜底。
     *
     * <p>ASCII 兜底刻意写成**不带引号**的 token（{@code filename=export.xlsx}）—— 带引号会让头里出现
     * 转义引号，解析/断言时极易踩坑；RFC 6266 允许无引号 token，浏览器对 {@code filename*} 亦优先。</p>
     */
    public static String contentDisposition(String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=export.xlsx; filename*=UTF-8''" + encoded;
    }

    /** 写响应流并关闭工作簿（**不**吞异常：让上层统一处理，避免用户拿到半截文件） */
    public static void write(HttpServletResponse resp, Workbook wb, String fileName) throws IOException {
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setHeader("Content-Disposition", contentDisposition(fileName));
        try {
            OutputStream os = resp.getOutputStream();
            wb.write(os);
            os.flush();
        } finally {
            wb.close();
        }
    }

    /** 6 位十六进制 → RGB 三字节（POI 的 XSSFColor 要 byte[]） */
    private static byte[] rgb(String hex) {
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6) throw new IllegalArgumentException("颜色必须是 6 位十六进制，如 D9E1F2：" + hex);
        return new byte[]{
                (byte) Integer.parseInt(h.substring(0, 2), 16),
                (byte) Integer.parseInt(h.substring(2, 4), 16),
                (byte) Integer.parseInt(h.substring(4, 6), 16),
        };
    }
}
