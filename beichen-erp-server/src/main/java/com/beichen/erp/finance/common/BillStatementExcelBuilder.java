package com.beichen.erp.finance.common;

import com.beichen.erp.common.AmountInWords;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.ExcelExportHelper;
import com.beichen.erp.finance.entity.FinanceBill;
import com.beichen.erp.finance.entity.FinanceBillItem;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.purchase.common.PurchaseChargeType;
import com.beichen.erp.sale.common.ExchangeChargeType;
import com.beichen.erp.sale.common.SaleReturnChargeType;
import com.beichen.erp.system.entity.Company;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 「应收/应付对账单」Excel 构建器（2026-09-29 用户口径「**账单详情要添加导出功能**」，且要**专业**）。
 *
 * <p>成品形态（单 sheet，A4 横向可直印）：公司抬头 → 单据标题（带（草稿）/（已作废）后缀）→
 * 副标题（账单号/账期/状态）→ 状态警示行 → 单头信息（往来单位/联系人/电话/制单日期/对账截止）→
 * 备注 → 明细表（含**逾期 N 天**与红字未收付）→ **合计行（真 SUM 公式）** → 人民币**大写** → 口径说明 →
 * 签字区（制单人/审核人/对方确认盖章）。</p>
 *
 * <p><b>为什么这么做</b>：对账单不是"数据导出"，是**发给往来单位核对并盖章的对外单据** ⇒ 必须一眼可读、
 * 可打印、金额可复核（小写 + 大写 + 合计公式三件套）。前端 SheetJS 社区版写不出样式与打印设置，
 * 故走后端 POI（公共件见 {@link ExcelExportHelper}；先例见 outsource 结单报表）。</p>
 *
 * <p><b>数据口径（与系统内一致，刻意不另立）</b>：</p>
 * <ul>
 *   <li>金额列一律取账单明细行自身字段（`amount / paid_amount / unpaid_amount`），合计行用 SUM 公式
 *       复算 —— 若与库内不符，Excel 里会立刻显形（这本身就是对账工具该有的性质）；</li>
 *   <li>逾期 = <b>未收付 &gt; 0 且到期日 &lt; 对账截止日</b>（当天到期不算），无到期日显示「未约定」且不计入逾期
 *       —— 与应付/应收汇总、供应商工作台**同一口径**；</li>
 *   <li>来源类型取 {@link SourceBillType#getLabel()} 全称（与详情页表格一致，不用列表窄列短名）。</li>
 * </ul>
 */
public final class BillStatementExcelBuilder {

    /**
     * 表头行（0 基）：跨页重复打印、冻结窗格、SUM 区间都锚在这一行。
     * <p>⚠️ 上面 0..6 共 7 行是「抬头/标题/副标题/警示/单头×2/备注」，故表头落在 **7**；
     * 首行数据 = Excel 第 9 行（{@code HEADER_ROW + 2} 的 1 基换算）。</p>
     */
    public static final int HEADER_ROW = 7;
    private static final int LAST_COL = 8;                        // I 列 = 备注
    private static final int[] WIDTHS = {6, 16, 22, 14, 14, 14, 13, 10, 24};
    private static final String MONEY = "#,##0.00";
    private static final String BG_HEAD = "D9E1F2";               // 表头浅蓝
    private static final String BG_TOTAL = "FFF2CC";              // 合计浅黄
    private static final String RED = "C00000";
    private static final String GRAY = "808080";

    private BillStatementExcelBuilder() {}

    /**
     * 构建对账单工作簿。
     *
     * @param bill           账单（{@code service.getById}）
     * @param items          明细（{@code service.getItems}，可为 null/空）
     * @param groups         **逐产品明细**（{@code BillProductItemService#groups}，可为 null/空）——
     *                       2026-10-02 用户要求「导出里也带明细」，落在**第二个 sheet**（见
     *                       {@link #buildDetailSheet}：主表一个字没动，故行数与 SUM 契约原样成立）
     * @param company        我方公司（取公司名做抬头；null = 抬头留空，不报错）
     * @param partnerContact 往来单位联系人（取不到传 null）
     * @param partnerPhone   往来单位电话（取不到传 null）
     * @param asOf           对账截止日（= 逾期判定的"今天"，由调用方传入以便可测）
     */
    public static Workbook build(FinanceBill bill, List<FinanceBillItem> items, List<Map<String, Object>> groups,
                                 Company company, String partnerContact, String partnerPhone, LocalDate asOf) {
        List<FinanceBillItem> rows = items == null ? List.of() : items;
        Workbook wb = new XSSFWorkbook();
        ExcelExportHelper h = new ExcelExportHelper(wb);

        String typeLabel = billTypeLabel(bill.getBillType());
        boolean cancelled = DocStatus.CANCELLED.name().equalsIgnoreCase(bill.getStatus());
        boolean draft = DocStatus.DRAFT.name().equalsIgnoreCase(bill.getStatus());
        String sheetName = typeLabel + "对账单";
        Sheet sheet = wb.createSheet(sheetName.length() > 31 ? sheetName.substring(0, 31) : sheetName);

        // ---------- 样式 ----------
        CellStyle titleStyle = h.plain(true, 18, HorizontalAlignment.CENTER, null);
        CellStyle subTitleStyle = h.plain(true, 16, HorizontalAlignment.CENTER, null);
        CellStyle subStyle = h.style(false, 9, HorizontalAlignment.CENTER, null, null, null, null, null, null, GRAY);
        CellStyle warnStyle = h.style(true, 10, HorizontalAlignment.CENTER, null, null, null, null, null, null, RED);
        CellStyle labelStyle = h.plain(true, 10, HorizontalAlignment.LEFT, null);
        CellStyle valueStyle = h.plain(false, 10, HorizontalAlignment.LEFT, null);
        CellStyle headerStyle = h.style(true, 10, HorizontalAlignment.CENTER, null,
                BorderStyle.THIN, BorderStyle.THIN, BorderStyle.THIN, BorderStyle.THIN, BG_HEAD, null);
        CellStyle textStyle = h.plain(false, 10, HorizontalAlignment.LEFT, null);
        CellStyle centerStyle = h.plain(false, 10, HorizontalAlignment.CENTER, null);
        CellStyle moneyStyle = h.plain(false, 10, HorizontalAlignment.RIGHT, MONEY);
        CellStyle moneyRedStyle = h.style(true, 10, HorizontalAlignment.RIGHT, MONEY, null, null, null, null, null, RED);
        CellStyle grayCenterStyle = h.style(false, 10, HorizontalAlignment.CENTER, null, null, null, null, null, null, GRAY);
        CellStyle redCenterStyle = h.style(true, 10, HorizontalAlignment.CENTER, null, null, null, null, null, null, RED);
        CellStyle totalLabelStyle = h.style(true, 11, HorizontalAlignment.RIGHT, null,
                null, BorderStyle.DOUBLE, null, null, BG_TOTAL, null);
        CellStyle totalMoneyStyle = h.style(true, 11, HorizontalAlignment.RIGHT, MONEY,
                null, BorderStyle.DOUBLE, null, null, BG_TOTAL, null);
        CellStyle totalBlankStyle = h.style(false, 10, HorizontalAlignment.CENTER, null,
                null, BorderStyle.DOUBLE, null, null, BG_TOTAL, null);
        CellStyle wordsStyle = h.plain(true, 12, HorizontalAlignment.LEFT, null);
        CellStyle noteStyle = h.style(false, 9, HorizontalAlignment.LEFT, null, null, null, null, null, null, GRAY);
        // 说明行跨满整行且文字较长（口径 + 行数 + 小计）⇒ **必须自动换行**，否则尾部信息被裁（同类"显示不全"缺陷）
        noteStyle.setWrapText(true);
        CellStyle signStyle = h.plain(false, 10, HorizontalAlignment.LEFT, null);

        int r = 0;
        // ---------- 抬头 + 标题 ----------
        Row row0 = sheet.createRow(r++);
        row0.setHeightInPoints(30);
        ExcelExportHelper.text(row0, 0, company != null ? nvl(company.getCompanyName(), "") : "", titleStyle);
        ExcelExportHelper.merge(sheet, 0, 0, 0, LAST_COL);

        String suffix = cancelled ? "（已作废）" : (draft ? "（草稿）" : "");
        Row row1 = sheet.createRow(r++);
        row1.setHeightInPoints(26);
        ExcelExportHelper.text(row1, 0, typeLabel + "对账单" + suffix, subTitleStyle);
        ExcelExportHelper.merge(sheet, 1, 0, 1, LAST_COL);

        // 副标题：账单号 / 账期 / 状态（一行排开，不竖排 key-value）
        String period = (bill.getPeriodStart() != null ? bill.getPeriodStart().toString() : "—")
                + " ~ " + (bill.getPeriodEnd() != null ? bill.getPeriodEnd().toString() : "—");
        String subText = "账单号：" + nvl(bill.getBillNo(), "—")
                + "　｜　账期：" + period
                + "　｜　状态：" + docStatusLabel(bill.getStatus())
                + "　｜　对账截止：" + (asOf != null ? asOf.toString() : "—");
        Row row2 = sheet.createRow(r++);
        row2.setHeightInPoints(18);
        ExcelExportHelper.text(row2, 0, subText, subStyle);
        ExcelExportHelper.merge(sheet, 2, 0, 2, LAST_COL);

        // 状态警示行（**始终占行**，保证表头行号固定 ⇒ 冻结/重复打印/断言都稳定）：非草稿非作废时留空
        String warn = cancelled ? "⚠ 本账单已作废，仅供存档，不可作为收付依据"
                : draft ? "⚠ 本账单为草稿，尚未审核生效，仅供内部核对" : "";
        Row row3 = sheet.createRow(r++);
        row3.setHeightInPoints(warn.isEmpty() ? 6 : 18);
        if (!warn.isEmpty()) {
            ExcelExportHelper.text(row3, 0, warn, warnStyle);
            ExcelExportHelper.merge(sheet, 3, 0, 3, LAST_COL);
        }

        // ---------- 单头信息 ----------
        // ⚠️ 2026-09-29 修（用户实测「往来单位：」「制单日期：」显示不全）：**标签必须跨列**！
        // A 列只有 6 宽（那是给「序号」的），标签放 A、值紧邻放 B ⇒ Excel 没有溢出空间（右侧有内容就不会
        // 溢出到邻格）⇒ 5 个汉字（≈10 宽）显示不下。现统一：标签跨 A:B（22 宽），值从 C 起、按需跨列。
        Row info1 = sheet.createRow(r++);
        info1.setHeightInPoints(20);
        ExcelExportHelper.text(info1, 0, "往来单位：", labelStyle);
        ExcelExportHelper.merge(sheet, info1.getRowNum(), 0, info1.getRowNum(), 1);
        ExcelExportHelper.text(info1, 2, nvl(bill.getPartnerName(), ""), valueStyle);
        ExcelExportHelper.merge(sheet, info1.getRowNum(), 2, info1.getRowNum(), 4);
        ExcelExportHelper.text(info1, 5, "联系人：", labelStyle);
        ExcelExportHelper.text(info1, 6, nvl(partnerContact, ""), valueStyle);
        ExcelExportHelper.merge(sheet, info1.getRowNum(), 6, info1.getRowNum(), LAST_COL);

        Row info2 = sheet.createRow(r++);
        info2.setHeightInPoints(20);
        ExcelExportHelper.text(info2, 0, "制单日期：", labelStyle);
        ExcelExportHelper.merge(sheet, info2.getRowNum(), 0, info2.getRowNum(), 1);
        ExcelExportHelper.text(info2, 2,
                bill.getCreateTime() != null ? bill.getCreateTime().toLocalDate().toString() : "—", valueStyle);
        ExcelExportHelper.merge(sheet, info2.getRowNum(), 2, info2.getRowNum(), 3);
        ExcelExportHelper.text(info2, 4, "制单人：", labelStyle);
        ExcelExportHelper.text(info2, 5, nvl(bill.getCreateByName(), "—"), valueStyle);
        ExcelExportHelper.merge(sheet, info2.getRowNum(), 5, info2.getRowNum(), 6);
        ExcelExportHelper.text(info2, 7, "电话：", labelStyle);
        ExcelExportHelper.text(info2, 8, nvl(partnerPhone, ""), valueStyle);

        Row remarkRow = sheet.createRow(r++);
        remarkRow.setHeightInPoints(20);
        ExcelExportHelper.text(remarkRow, 0, "备注：", labelStyle);
        ExcelExportHelper.merge(sheet, remarkRow.getRowNum(), 0, remarkRow.getRowNum(), 1);
        ExcelExportHelper.text(remarkRow, 2, nvl(bill.getRemark(), ""), valueStyle);
        ExcelExportHelper.merge(sheet, remarkRow.getRowNum(), 2, remarkRow.getRowNum(), LAST_COL);

        // ---------- 明细表头（行号固定 = HEADER_ROW） ----------
        // 2026-09-29 用户口径：列名跟账单类型走（应付=已付/未付，应收=已收/未收）—— 「已收付/未收付」
        // 这种合写词在对外单据上会让人以为"还有第三种状态"（用户原话：已收就是已收、已付就是已付）
        String paidCol = paidWord(bill);
        String unpaidCol = unpaidWord(bill);
        String[] headers = {"序号", "来源类型", "来源单号", "金额", paidCol, unpaidCol, "到期日", "逾期", "备注"};
        Row head = sheet.createRow(r++);
        head.setHeightInPoints(22);
        for (int c = 0; c < headers.length; c++) ExcelExportHelper.text(head, c, headers[c], headerStyle);

        // ---------- 明细行 ----------
        BigDecimal amountTotal = BigDecimal.ZERO;
        BigDecimal paidTotal = BigDecimal.ZERO;
        BigDecimal unpaidTotal = BigDecimal.ZERO;
        int no = 0;
        for (FinanceBillItem it : rows) {
            no++;
            Row dr = sheet.createRow(r++);
            dr.setHeightInPoints(18);
            BigDecimal amount = nz(it.getAmount());
            BigDecimal paid = nz(it.getPaidAmount());
            BigDecimal unpaid = nz(it.getUnpaidAmount());
            amountTotal = amountTotal.add(amount);
            paidTotal = paidTotal.add(paid);
            unpaidTotal = unpaidTotal.add(unpaid);

            // 逾期口径与系统内一致：未收付 > 0 且 到期日 < 对账截止日（当天到期不算；无到期日不计入）
            boolean hasDue = it.getDueDate() != null;
            boolean overdue = unpaid.signum() > 0 && hasDue && it.getDueDate().isBefore(asOf);

            ExcelExportHelper.text(dr, 0, String.valueOf(no), centerStyle);
            ExcelExportHelper.text(dr, 1, sourceTypeLabel(it.getSourceBillType()), textStyle);
            ExcelExportHelper.text(dr, 2, nvl(it.getSourceBillNo(), ""), textStyle);
            ExcelExportHelper.num(dr, 3, amount, moneyStyle);
            ExcelExportHelper.num(dr, 4, paid, moneyStyle);
            ExcelExportHelper.num(dr, 5, unpaid, overdue ? moneyRedStyle : moneyStyle);
            ExcelExportHelper.text(dr, 6, hasDue ? it.getDueDate().toString() : "未约定",
                    hasDue ? centerStyle : grayCenterStyle);
            ExcelExportHelper.text(dr, 7,
                    overdue ? "逾期 " + ChronoUnit.DAYS.between(it.getDueDate(), asOf) + " 天"
                            : (hasDue ? "—" : "未约定"),
                    overdue ? redCenterStyle : centerStyle);
            ExcelExportHelper.text(dr, 8, nvl(it.getRemark(), ""), textStyle);
        }

        // ---------- 合计行（真公式；库内数若不等于 SUM，Excel 会立刻显形） ----------
        Row total = sheet.createRow(r++);
        total.setHeightInPoints(22);
        ExcelExportHelper.text(total, 0, "合  计", totalLabelStyle);
        ExcelExportHelper.merge(sheet, total.getRowNum(), 0, total.getRowNum(), 2);
        String firstData = String.valueOf(HEADER_ROW + 2);                    // 1 基行号
        String lastData = String.valueOf(HEADER_ROW + 1 + Math.max(no, 1));
        for (int c = 3; c <= 5; c++) {
            String col = String.valueOf((char) ('A' + c));
            if (no > 0) {
                ExcelExportHelper.formula(total, c, "SUM(" + col + firstData + ":" + col + lastData + ")", totalMoneyStyle);
            } else {
                ExcelExportHelper.num(total, c, BigDecimal.ZERO, totalMoneyStyle);
            }
        }
        for (int c = 6; c <= LAST_COL; c++) ExcelExportHelper.blank(total, c, totalBlankStyle);

        // ---------- 大写 + 口径说明 ----------
        Row words = sheet.createRow(r++);
        words.setHeightInPoints(24);
        ExcelExportHelper.text(words, 0, unpaidCol + "合计（大写）：" + AmountInWords.of(unpaidTotal), wordsStyle);
        ExcelExportHelper.merge(sheet, words.getRowNum(), 0, words.getRowNum(), LAST_COL);

        Row note = sheet.createRow(r++);
        note.setHeightInPoints(30);   // 与 noteStyle 的 wrapText 配套：留出 2 行高度，长说明不裁
        ExcelExportHelper.text(note, 0, "说明：" + unpaidCol + " = 金额 − " + paidCol + "；逾期口径 = 到期日早于 " + asOf
                + "（当天到期不算，未约定到期日不计入逾期）；金额单位：元。（共 " + no
                + " 行，金额小计 " + plain(amountTotal) + "，" + paidCol + " " + plain(paidTotal) + "）", noteStyle);
        ExcelExportHelper.merge(sheet, note.getRowNum(), 0, note.getRowNum(), LAST_COL);

        // ---------- 签字区 ----------
        r++;   // 留白一行
        Row sign = sheet.createRow(r);
        sign.setHeightInPoints(26);
        ExcelExportHelper.text(sign, 0, "制单人：" + nvl(bill.getCreateByName(), "＿＿＿＿"), signStyle);
        ExcelExportHelper.merge(sheet, sign.getRowNum(), 0, sign.getRowNum(), 2);
        ExcelExportHelper.text(sign, 3, "审核人：" + nvl(bill.getAuditorName(), "＿＿＿＿"), signStyle);
        ExcelExportHelper.merge(sheet, sign.getRowNum(), 3, sign.getRowNum(), 5);
        ExcelExportHelper.text(sign, 6, "对方确认（签字/盖章）：＿＿＿＿＿＿", signStyle);
        ExcelExportHelper.merge(sheet, sign.getRowNum(), 6, sign.getRowNum(), LAST_COL);

        // ---------- 列宽 / 打印 / 冻结 ----------
        ExcelExportHelper.widths(sheet, WIDTHS);
        ExcelExportHelper.printSetup(sheet, true, 1, HEADER_ROW, "第 &P 页 / 共 &N 页");
        sheet.createFreezePane(0, HEADER_ROW + 1);

        // ---------- 第二个 sheet：产品明细（每张来源单卖了什么 / 收了什么费） ----------
        buildDetailSheet(wb, groups, h, bill);
        return wb;
    }

    /** 明细 sheet 列（11 列）：来源类型/来源单号/明细类型/产品名称/SKU/品质/数量/单价/金额/说明/核对 */
    private static final String[] DETAIL_HEADERS =
            {"来源类型", "来源单号", "明细类型", "产品名称", "SKU", "品质", "数量", "单价", "金额", "说明", "核对"};
    private static final int[] DETAIL_WIDTHS = {16, 20, 10, 24, 16, 8, 10, 12, 14, 22, 12};
    /** 明细 sheet 的表头行（0 基）：上面 2 行是标题 + 口径说明 */
    private static final int DETAIL_HEADER_ROW = 2;

    /**
     * 第二个 sheet「产品明细」：把每张来源单的**产品行**（或收费行）摊开，让对账单能回答"每一张单卖了什么"。
     *
     * <p><b>为什么单开一页而不是插进主表</b>：主表的表头行号（{@link #HEADER_ROW}）是冻结窗格、跨页重复打印、
     * 合计 SUM 区间三者的共同锚点，插入明细子行这三样全要重算；而且主表合计已经有金额列，明细里再出现金额
     * 会造成"合计 = 明细之和"的**双计**。单开一页后主表一个字没动（对外单据版式稳定，
     * `verify-bill-export.ps1` 对主表的行数/SUM 断言也原样成立），明细另页供内部与对方核对。</p>
     *
     * <p><b>金额方向</b>：本页金额取**带符号**值（{@code signedAmount}：销售/采购单取正、退货取负），
     * 与台账方向一致 ⇒ 页脚的 SUM 恰好等于主表「金额」列合计，可互相印证（若不等，Excel 里立刻显形）。</p>
     *
     * <p>没有产品明细的来源类型（收费之外的各种折损/报损/委外/预收台账…）**仍然占一行**：产品名称列写
     * 该类型的专属原因（灰字），避免对方以为"漏了行"。</p>
     */
    private static void buildDetailSheet(Workbook wb, List<Map<String, Object>> groups,
                                         ExcelExportHelper h, FinanceBill bill) {
        List<Map<String, Object>> gs = groups == null ? List.of() : groups;
        if (gs.isEmpty()) return;   // 没有明细可展开 ⇒ 不加空页

        Sheet sheet = wb.createSheet("产品明细");
        CellStyle titleStyle = h.plain(true, 14, HorizontalAlignment.CENTER, null);
        CellStyle noteStyle = h.style(false, 9, HorizontalAlignment.LEFT, null, null, null, null, null, null, GRAY);
        noteStyle.setWrapText(true);
        CellStyle headerStyle = h.style(true, 10, HorizontalAlignment.CENTER, null,
                BorderStyle.THIN, BorderStyle.THIN, BorderStyle.THIN, BorderStyle.THIN, BG_HEAD, null);
        CellStyle textStyle = h.plain(false, 10, HorizontalAlignment.LEFT, null);
        CellStyle centerStyle = h.plain(false, 10, HorizontalAlignment.CENTER, null);
        CellStyle moneyStyle = h.plain(false, 10, HorizontalAlignment.RIGHT, MONEY);
        CellStyle grayStyle = h.style(false, 10, HorizontalAlignment.LEFT, null, null, null, null, null, null, GRAY);
        CellStyle okStyle = h.style(false, 10, HorizontalAlignment.CENTER, null, null, null, null, null, null, GRAY);
        CellStyle badStyle = h.style(true, 10, HorizontalAlignment.CENTER, null, null, null, null, null, null, RED);
        CellStyle totalLabelStyle = h.style(true, 11, HorizontalAlignment.RIGHT, null,
                null, BorderStyle.DOUBLE, null, null, BG_TOTAL, null);
        CellStyle totalMoneyStyle = h.style(true, 11, HorizontalAlignment.RIGHT, MONEY,
                null, BorderStyle.DOUBLE, null, null, BG_TOTAL, null);
        CellStyle totalBlankStyle = h.style(false, 10, HorizontalAlignment.CENTER, null,
                null, BorderStyle.DOUBLE, null, null, BG_TOTAL, null);

        int r = 0;
        Row t = sheet.createRow(r++);
        t.setHeightInPoints(24);
        ExcelExportHelper.text(t, 0, "产品明细（每张来源单卖了什么）　账单号：" + nvl(bill.getBillNo(), "—"), titleStyle);
        ExcelExportHelper.merge(sheet, 0, 0, 0, DETAIL_HEADERS.length - 1);

        Row n = sheet.createRow(r++);
        n.setHeightInPoints(28);
        ExcelExportHelper.text(n, 0, "说明：金额为**带符号**值（销售/采购单取正、退货按负数冲减），与对账单主表方向一致；"
                + "「明细类型」= 货值 / 收费（收费行的金额是逐产品收费额，数量与单价是该单据行的业务量，仅供对照）；"
                + "「核对」列 = 该来源单的明细合计与其台账金额是否一致。本页仅供核对，不单独作为收付依据。", noteStyle);
        ExcelExportHelper.merge(sheet, 1, 0, 1, DETAIL_HEADERS.length - 1);

        Row head = sheet.createRow(r++);
        head.setHeightInPoints(22);
        for (int c = 0; c < DETAIL_HEADERS.length; c++) ExcelExportHelper.text(head, c, DETAIL_HEADERS[c], headerStyle);

        int lineRows = 0;
        for (Map<String, Object> g : gs) {
            String typeLabel = sourceTypeLabel(String.valueOf(g.get("sourceBillType")));
            String billNo = nvl((String) g.get("sourceBillNo"), "");
            String kind = "CHARGE".equals(g.get("lineKind")) ? "收费" : "货值";
            Object matched = g.get("matched");
            String checkText = Boolean.TRUE.equals(matched) ? "一致"
                    : Boolean.FALSE.equals(matched) ? "不符" : "—";
            List<Map<String, Object>> lines = asList(g.get("lines"));
            if (lines.isEmpty()) {
                Row dr = sheet.createRow(r++);
                dr.setHeightInPoints(18);
                ExcelExportHelper.text(dr, 0, typeLabel, textStyle);
                ExcelExportHelper.text(dr, 1, billNo, textStyle);
                ExcelExportHelper.text(dr, 2, "—", centerStyle);
                ExcelExportHelper.text(dr, 3, nvl((String) g.get("noDetailReason"), "无产品明细"), grayStyle);
                ExcelExportHelper.merge(sheet, dr.getRowNum(), 3, dr.getRowNum(), DETAIL_HEADERS.length - 1);
                continue;
            }
            boolean chargeRow = "CHARGE".equals(g.get("lineKind"));
            boolean first = true;
            for (Map<String, Object> l : lines) {
                Row dr = sheet.createRow(r++);
                dr.setHeightInPoints(18);
                lineRows++;
                ExcelExportHelper.text(dr, 0, typeLabel, textStyle);
                ExcelExportHelper.text(dr, 1, billNo, textStyle);
                ExcelExportHelper.text(dr, 2, kind, centerStyle);
                ExcelExportHelper.text(dr, 3, nvl((String) l.get("productName"), ""), textStyle);
                ExcelExportHelper.text(dr, 4, nvl((String) l.get("sku"), ""), textStyle);
                ExcelExportHelper.text(dr, 5, qualityLabel((String) l.get("qualityType")), centerStyle);
                ExcelExportHelper.num(dr, 6, nz((BigDecimal) l.get("quantity")), moneyStyle);
                if (chargeRow) {
                    // 收费行没有「本次收费单价」（库里只有 chargeAmount），摆单据原单价会让人算不对
                    // （2 件 × 100 却收 50）⇒ 写「—」，为什么收这笔钱看「说明」列
                    ExcelExportHelper.text(dr, 7, "—", centerStyle);
                    ExcelExportHelper.text(dr, 9, chargeNote(String.valueOf(g.get("sourceBillType")), l), textStyle);
                } else {
                    ExcelExportHelper.num(dr, 7, nz((BigDecimal) l.get("unitPrice")), moneyStyle);
                    ExcelExportHelper.blank(dr, 9, textStyle);
                }
                ExcelExportHelper.num(dr, 8, nz((BigDecimal) l.get("signedAmount")), moneyStyle);
                if (first) {   // 核对结果只在该来源单的第一行标一次
                    ExcelExportHelper.text(dr, 10, checkText, Boolean.FALSE.equals(matched) ? badStyle : okStyle);
                    first = false;
                } else {
                    ExcelExportHelper.blank(dr, 10, centerStyle);
                }
            }
        }

        Row total = sheet.createRow(r++);
        total.setHeightInPoints(22);
        ExcelExportHelper.text(total, 0, "明细金额合计", totalLabelStyle);
        ExcelExportHelper.merge(sheet, total.getRowNum(), 0, total.getRowNum(), 7);
        if (lineRows > 0) {
            String firstData = String.valueOf(DETAIL_HEADER_ROW + 2);
            String lastData = String.valueOf(DETAIL_HEADER_ROW + 1 + lineRows);
            ExcelExportHelper.formula(total, 8, "SUM(I" + firstData + ":I" + lastData + ")", totalMoneyStyle);
        } else {
            ExcelExportHelper.num(total, 8, BigDecimal.ZERO, totalMoneyStyle);
        }
        ExcelExportHelper.blank(total, 9, totalBlankStyle);
        ExcelExportHelper.blank(total, 10, totalBlankStyle);

        ExcelExportHelper.widths(sheet, DETAIL_WIDTHS);
        ExcelExportHelper.printSetup(sheet, true, 1, DETAIL_HEADER_ROW, "第 &P 页 / 共 &N 页");
        sheet.createFreezePane(0, DETAIL_HEADER_ROW + 1);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object v) {
        return v instanceof List ? (List<Map<String, Object>>) v : List.of();
    }

    /** 品质 code（A/B/C/DEFECT/PENDING）→ 中文（A规…）；未知 code 原样回显，不吞掉。 */
    private static String qualityLabel(String code) {
        ProductQualityType qt = ProductQualityType.of(code);
        return qt != null ? qt.getLabel() : nvl(code, "");
    }

    /**
     * 收费行的「说明」列：收费类型中文（+ 收费说明）。
     *
     * <p>⚠️ 收费类型**分侧**：销售退货用 {@link SaleReturnChargeType}（盖板划伤/其他），
     * 销售换货用 {@link ExchangeChargeType}，采购侧（退货付费/换货付费）用 {@link PurchaseChargeType}
     * —— 用错枚举会把"盖板划伤"印成"服务费"（前端 {@code enums.billChargeTypeLabel} 是同一份口径）。</p>
     */
    private static String chargeNote(String sourceBillType, Map<String, Object> line) {
        String code = (String) line.get("chargeType");
        String label = "";
        if (code != null && !code.isEmpty()) {
            if (SourceBillType.SALE_RETURN_CHARGE.getCode().equals(sourceBillType)) {
                label = labelOfSaleReturn(code);
            } else if (SourceBillType.SALE_EXCHANGE_CHARGE.getCode().equals(sourceBillType)) {
                label = labelOfExchange(code);
            } else {
                label = labelOfPurchase(code);
            }
            if (label.isEmpty()) label = code;      // 未知 code 原样回显，不留空白
        }
        String reason = nvl((String) line.get("chargeReason"), "");
        if (label.isEmpty()) return reason;
        return reason.isEmpty() ? label : label + "：" + reason;
    }

    private static String labelOfSaleReturn(String code) {
        for (SaleReturnChargeType t : SaleReturnChargeType.values()) {
            if (t.name().equals(code)) return nvl(t.getLabel(), "");
        }
        return "";
    }

    private static String labelOfExchange(String code) {
        for (ExchangeChargeType t : ExchangeChargeType.values()) {
            if (t.name().equals(code)) return nvl(t.getLabel(), "");
        }
        return "";
    }

    private static String labelOfPurchase(String code) {
        for (PurchaseChargeType t : PurchaseChargeType.values()) {
            if (t.name().equals(code)) return nvl(t.getLabel(), "");
        }
        return "";
    }

    /** 下载文件名：`应付对账单_往来单位_账单号.xlsx`（剔非法字符，过长截断） */
    public static String fileName(FinanceBill bill) {
        String type = billTypeLabel(bill == null ? null : bill.getBillType());
        String partner = nvl(bill == null ? null : bill.getPartnerName(), "");
        String no = nvl(bill == null ? null : bill.getBillNo(), "");
        String name = type + "对账单"
                + (partner.isEmpty() ? "" : "_" + partner)
                + (no.isEmpty() ? "" : "_" + no) + ".xlsx";
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.length() > 120 ? safe.substring(0, 115) + ".xlsx" : safe;
    }

    /**
     * 「已结算」侧的方向词（2026-09-29 用户口径：**不用合写词「已收付」**）。
     * <p>应付账单 = 已付（我方付出去），应收账单 = 已收（我方收进来）—— 与页面
     * （bill.vue / bill/detail.vue）以及台账页（payable.vue 用「已付」、receivable.vue 用「已收」）同一措辞。</p>
     */
    private static String paidWord(FinanceBill bill) {
        return BillType.PAYABLE.name().equalsIgnoreCase(bill.getBillType()) ? "已付" : "已收";
    }

    /** 「未结算」侧的方向词：应付 = 未付，应收 = 未收（见 {@link #paidWord}） */
    private static String unpaidWord(FinanceBill bill) {
        return BillType.PAYABLE.name().equalsIgnoreCase(bill.getBillType()) ? "未付" : "未收";
    }

    /** 账单类型中文（应付/应收）——枚举是唯一口径来源，避免硬编码 */
    public static String billTypeLabel(String code) {
        if (code == null) return "";
        for (BillType t : BillType.values()) if (t.name().equalsIgnoreCase(code)) return t.getLabel();
        return code;
    }

    /** 来源单据类型全称（与详情页表格一致；未知 code 原样输出，不吞信息） */
    private static String sourceTypeLabel(String code) {
        SourceBillType t = SourceBillType.fromCode(code);
        return t != null ? t.getLabel() : nvl(code, "");
    }

    /** 单据状态中文 */
    private static String docStatusLabel(String code) {
        if (code == null) return "—";
        for (DocStatus s : DocStatus.values()) if (s.name().equalsIgnoreCase(code)) return s.getLabel();
        return code;
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static String nvl(String v, String dft) { return v == null || v.isBlank() ? dft : v; }

    private static String plain(BigDecimal v) { return nz(v).stripTrailingZeros().toPlainString(); }
}
