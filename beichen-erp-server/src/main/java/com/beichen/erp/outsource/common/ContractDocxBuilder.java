package com.beichen.erp.outsource.common;

import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * 合同 DOCX 生成工具
 * <p>
 * 按固定结构拼装合同：标题 → 甲乙双方信息（左右两栏、无边框） → 明细表格（固定列宽、表头底纹）
 * → 条款（富文本） → 签名区（左右两栏、无边框），输出可编辑的 .docx 文档（Apache POI XWPF）。
 * </p>
 */
public final class ContractDocxBuilder {

    private ContractDocxBuilder() {}

    // ======================== 排版常量（单位：twips，1/20 磅） ========================

    /** A4 页面宽 11906、左右页边距各 1440 → 内容区宽 9026 */
    private static final int PAGE_WIDTH = 11906;
    /** A4 页面高 */
    private static final int PAGE_HEIGHT = 16838;
    /** 四边页边距（2.54cm） */
    private static final int PAGE_MARGIN = 1440;
    /** A4 内容区总宽度 */
    private static final int PAGE_CONTENT_WIDTH = 9026;
    /** 标题字号 */
    private static final int FONT_TITLE = 16;
    /** 小节标题字号 */
    private static final int FONT_SECTION = 12;
    /** 正文字号 */
    private static final int FONT_BODY = 10;
    /** 表头底纹颜色（浅灰） */
    private static final String HEADER_SHADING = "D9D9D9";
    /** 细单线边框粗细（sz 单位 1/8 磅，4 = 0.5 磅） */
    private static final int BORDER_THIN_SZ = 4;
    /** 边框颜色（黑色，ARGB 由 POI 自动补全） */
    private static final String BORDER_COLOR = "000000";

    /** 甲乙双方 / 签名区两栏列宽（各占 50%） */
    private static final int[] PARTY_COL_WIDTHS = {4513, 4513};
    /** 加工产品明细列宽：序号/名称/单价/数量/合计/备注 */
    private static final int[] PRODUCT_COL_WIDTHS = {700, 2800, 1300, 1100, 1400, 1726};
    /** 物料明细列宽：序号/物料名称/数量/损耗率/备注 */
    private static final int[] MATERIAL_COL_WIDTHS = {700, 3300, 1500, 1500, 2026};
    /** 采购物料明细列宽：序号/物料名称/单位/数量/单价/金额/备注 */
    private static final int[] ORDER_ITEM_COL_WIDTHS = {600, 2400, 900, 1100, 1300, 1400, 1326};
    /** 子物料明细列宽：序号/子物料名称/单位/每套用量/需求总数/损耗率 */
    private static final int[] COMPONENT_COL_WIDTHS = {600, 2600, 1000, 1500, 1700, 1626};
    /** 小节标题中文序号 */
    private static final String[] SECTION_NUMS = {"一", "二", "三", "四"};

    /** 甲方信息载体 */
    public record PartyA(String name, String contact, String phone, String address) {}
    /** 乙方信息载体 */
    public record PartyB(String name, String contact, String phone, String address) {}

    /** 加工合同产品明细行 */
    public record ProductRow(String name, String unitPrice, String quantity, String amount, String remark) {}
    /** 加工合同物料明细行 */
    public record MaterialRow(String name, String quantity, String lossRate, String remark) {}
    /** 采购合同物料明细行 */
    public record OrderItemRow(String name, String unit, String quantity, String unitPrice, String amount, String remark) {}
    /** 采购合同组件明细行 */
    public record ComponentRow(String parentName, String orderQty, String unit, List<String[]> children) {}

    /** 生成合同 DOCX 字节数组 */
    public static byte[] build(String title, PartyA partyA, PartyB partyB, String clausesHtml,
                               List<ProductRow> products, List<MaterialRow> materials,
                               List<OrderItemRow> orderItems, List<ComponentRow> components) {
        try (XWPFDocument doc = new XWPFDocument()) {
            // 0. 固定 A4 页面与页边距，保证表格排版宽度一致
            setupPage(doc);

            // 1. 标题（居中、加粗）
            XWPFParagraph titleP = doc.createParagraph();
            titleP.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleP.createRun();
            titleRun.setText(title);
            titleRun.setBold(true);
            titleRun.setFontSize(FONT_TITLE);
            titleRun.setFontFamily("宋体");

            addBlank(doc);

            // 2. 甲乙双方信息（甲方靠左半区、乙方靠右半区，无边框）
            addParties(doc, partyA, partyB);

            // 3. 明细表格（加工合同：产品+物料；采购合同：物料+组件），按出现顺序自动编号小节标题
            int section = 0;
            if (products != null && !products.isEmpty()) {
                addSectionTitle(doc, sectionTitle(section++, "加工产品明细"));
                addProductTable(doc, products);
                addBlank(doc);
            }
            if (materials != null && !materials.isEmpty()) {
                addSectionTitle(doc, sectionTitle(section++, "物料明细"));
                addMaterialTable(doc, materials);
                addBlank(doc);
            }
            if (orderItems != null && !orderItems.isEmpty()) {
                addSectionTitle(doc, sectionTitle(section++, "采购物料明细"));
                addOrderItemTable(doc, orderItems);
                addBlank(doc);
            }
            if (components != null && !components.isEmpty()) {
                addSectionTitle(doc, sectionTitle(section++, "物料所含子物料明细"));
                addComponentTables(doc, components);
                addBlank(doc);
            }

            // 4. 条款（解析富文本 HTML 为段落）
            addClauses(doc, clausesHtml);

            // 5. 签名区（甲方靠左、乙方靠右，无边框）
            addBlank(doc);
            addBlank(doc);
            addSignature(doc, partyA, partyB);

            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            doc.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("合同DOC生成失败：" + e.getMessage(), e);
        }
    }

    // ======================== 页面与通用排版 ========================

    /** 固定 A4 纵向页面与四边页边距，确保内容宽度与列宽常量匹配 */
    private static void setupPage(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr() : doc.getDocument().getBody().addNewSectPr();
        CTPageSz pgSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pgSz.setW(BigInteger.valueOf(PAGE_WIDTH));
        pgSz.setH(BigInteger.valueOf(PAGE_HEIGHT));
        CTPageMar pgMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pgMar.setTop(BigInteger.valueOf(PAGE_MARGIN));
        pgMar.setBottom(BigInteger.valueOf(PAGE_MARGIN));
        pgMar.setLeft(BigInteger.valueOf(PAGE_MARGIN));
        pgMar.setRight(BigInteger.valueOf(PAGE_MARGIN));
    }

    private static void addBlank(XWPFDocument doc) {
        doc.createParagraph().createRun().setText("");
    }

    /** 明细小节标题（加粗） */
    private static void addSectionTitle(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontFamily("宋体");
        r.setFontSize(FONT_SECTION);
    }

    /** 拼接小节标题：中文序号 + 名称 */
    private static String sectionTitle(int idx, String name) {
        return SECTION_NUMS[idx] + "、" + name;
    }

    /** 去除表格全部边框（甲乙双方区、签名区使用） */
    private static void setTableBorderNone(XWPFTable table) {
        setTableBorders(table, XWPFTable.XWPFBorderType.NIL, 0);
    }

    /** 明细表格统一细单线边框 */
    private static void setTableBordersThin(XWPFTable table) {
        setTableBorders(table, XWPFTable.XWPFBorderType.SINGLE, BORDER_THIN_SZ);
    }

    /** 统一设置表格六条边框（类型/粗细/间距/颜色），使用 POI 内置 API 避免 lite 包缺少 CT 枚举类 */
    private static void setTableBorders(XWPFTable table, XWPFTable.XWPFBorderType type, int sz) {
        table.setTopBorder(type, sz, 0, BORDER_COLOR);
        table.setBottomBorder(type, sz, 0, BORDER_COLOR);
        table.setLeftBorder(type, sz, 0, BORDER_COLOR);
        table.setRightBorder(type, sz, 0, BORDER_COLOR);
        table.setInsideHBorder(type, sz, 0, BORDER_COLOR);
        table.setInsideVBorder(type, sz, 0, BORDER_COLOR);
    }

    /**
     * 设置表格固定总宽与各列列宽（tblLayout=fixed + tblGrid + 逐格 tcW），
     * 从根上避免 Word 自动布局导致列宽乱跳
     */
    private static void setTableFixedWidths(XWPFTable table, int[] colWidths) {
        long total = 0;
        for (int w : colWidths) total += w;
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        if (tblPr == null) tblPr = table.getCTTbl().addNewTblPr();
        // 表格总宽
        CTTblWidth tblW = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tblW.setW(BigInteger.valueOf(total));
        tblW.setType(STTblWidth.DXA);
        // 固定布局（列宽不随内容自动伸缩）
        CTTblLayoutType layout = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);
        // tblGrid 网格列宽
        CTTblGrid grid = table.getCTTbl().getTblGrid();
        if (grid == null) grid = table.getCTTbl().addNewTblGrid();
        List<CTTblGridCol> cols = grid.getGridColList();
        for (int i = 0; i < cols.size() && i < colWidths.length; i++) {
            cols.get(i).setW(BigInteger.valueOf(colWidths[i]));
        }
        // 每行每格 tcW
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            for (int i = 0; i < cells.size() && i < colWidths.length; i++) {
                CTTcPr tcPr = cells.get(i).getCTTc().isSetTcPr()
                        ? cells.get(i).getCTTc().getTcPr() : cells.get(i).getCTTc().addNewTcPr();
                CTTblWidth tcW = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
                tcW.setW(BigInteger.valueOf(colWidths[i]));
                tcW.setType(STTblWidth.DXA);
            }
        }
    }

    /** 标记表头行：明细跨页时自动重复表头 */
    private static void markHeaderRow(XWPFTableRow row) {
        CTTrPr trPr = row.getCtRow().isSetTrPr() ? row.getCtRow().getTrPr() : row.getCtRow().addNewTrPr();
        if (trPr.sizeOfTblHeaderArray() == 0) trPr.addNewTblHeader();
    }

    // ======================== 甲乙双方 / 签名区 ========================

    private static void addParties(XWPFDocument doc, PartyA a, PartyB b) {
        XWPFTable table = doc.createTable(1, 2);
        // 无边框 + 左右两列各占 50%：甲方靠左、乙方靠右
        setTableBorderNone(table);
        setTableFixedWidths(table, PARTY_COL_WIDTHS);
        setCell(table.getRow(0).getCell(0), partyBlock("甲方（委托方）：" + n(a.name()), a));
        setCell(table.getRow(0).getCell(1), partyBlock("乙方（承揽方）：" + n(b.name()), b));
    }

    private static String partyBlock(String title, PartyA a) {
        return title + "\n地址：" + n(a.address()) + "\n联系人：" + n(a.contact()) + "\n电话：" + n(a.phone());
    }

    private static String partyBlock(String title, PartyB b) {
        return title + "\n地址：" + n(b.address()) + "\n联系人：" + n(b.contact()) + "\n电话：" + n(b.phone());
    }

    /** 无边框信息单元格：块内逐行左对齐，甲方/乙方标题行加粗 */
    private static void setCell(XWPFTableCell cell, String text) {
        String[] lines = text.split("\n");
        // 清空单元格默认段落
        while (cell.getParagraphs().size() > 0) {
            cell.removeParagraph(0);
        }
        for (String line : lines) {
            XWPFParagraph p = cell.addParagraph();
            p.setAlignment(ParagraphAlignment.LEFT);
            XWPFRun r = p.createRun();
            r.setText(line);
            r.setFontFamily("宋体");
            r.setFontSize(FONT_BODY);
            if (line.startsWith("甲方") || line.startsWith("乙方")) r.setBold(true);
        }
    }

    private static void addSignature(XWPFDocument doc, PartyA a, PartyB b) {
        XWPFTable table = doc.createTable(1, 2);
        // 签名区与甲乙双方区风格一致：无边框、甲方靠左、乙方靠右
        setTableBorderNone(table);
        setTableFixedWidths(table, PARTY_COL_WIDTHS);
        setCell(table.getRow(0).getCell(0), signBlock("甲方（盖章）：" + n(a.name())));
        setCell(table.getRow(0).getCell(1), signBlock("乙方（盖章）：" + n(b.name())));
    }

    private static String signBlock(String title) {
        return title + "\n\n法定代表人/授权代表：\n\n日期：";
    }

    // ======================== 明细表格 ========================

    private static void addProductTable(XWPFDocument doc, List<ProductRow> rows) {
        String[] heads = {"序号", "名称", "单价(元)", "数量", "合计(元)", "备注"};
        BigDecimal total = BigDecimal.ZERO;
        XWPFTable table = doc.createTable(rows.size() + 2, 6);
        setTableBordersThin(table);
        setTableFixedWidths(table, PRODUCT_COL_WIDTHS);
        for (int c = 0; c < heads.length; c++) setHeaderCell(table.getRow(0).getCell(c), heads[c]);
        markHeaderRow(table.getRow(0));
        int i = 1;
        for (ProductRow r : rows) {
            BigDecimal amt = parse(r.amount());
            total = total.add(amt);
            XWPFTableRow tr = table.getRow(i);
            setBodyCell(tr.getCell(0), String.valueOf(i));
            // 名称、备注左对齐，数字列居中
            setBodyCell(tr.getCell(1), n(r.name()), ParagraphAlignment.LEFT, false);
            setBodyCell(tr.getCell(2), n(r.unitPrice()));
            setBodyCell(tr.getCell(3), n(r.quantity()));
            setBodyCell(tr.getCell(4), n(r.amount()));
            setBodyCell(tr.getCell(5), n(r.remark()), ParagraphAlignment.LEFT, false);
            i++;
        }
        // 合计行（必须先写完各格内容、最后合并，避免删格后 XWPF 层索引错位）
        XWPFTableRow totalRow = table.getRow(rows.size() + 1);
        setBodyCell(totalRow.getCell(0), "合计");
        setBodyCell(totalRow.getCell(1), "");
        setBodyCell(totalRow.getCell(2), "");
        setBodyCell(totalRow.getCell(3), "");
        setBodyCell(totalRow.getCell(4), fmt(total) + " 元", ParagraphAlignment.CENTER, true);
        setBodyCell(totalRow.getCell(5), "");
        mergeCellsHorizontal(totalRow, 1, 3);
    }

    private static void addMaterialTable(XWPFDocument doc, List<MaterialRow> rows) {
        String[] heads = {"序号", "物料名称", "数量", "损耗率(%)", "备注"};
        XWPFTable table = doc.createTable(rows.size() + 1, 5);
        setTableBordersThin(table);
        setTableFixedWidths(table, MATERIAL_COL_WIDTHS);
        for (int c = 0; c < heads.length; c++) setHeaderCell(table.getRow(0).getCell(c), heads[c]);
        markHeaderRow(table.getRow(0));
        int i = 1;
        for (MaterialRow r : rows) {
            XWPFTableRow tr = table.getRow(i);
            setBodyCell(tr.getCell(0), String.valueOf(i));
            setBodyCell(tr.getCell(1), n(r.name()), ParagraphAlignment.LEFT, false);
            setBodyCell(tr.getCell(2), n(r.quantity()));
            setBodyCell(tr.getCell(3), n(r.lossRate()));
            setBodyCell(tr.getCell(4), n(r.remark()), ParagraphAlignment.LEFT, false);
            i++;
        }
    }

    private static void addOrderItemTable(XWPFDocument doc, List<OrderItemRow> rows) {
        String[] heads = {"序号", "物料名称", "单位", "数量", "单价(元)", "金额(元)", "备注"};
        BigDecimal total = BigDecimal.ZERO;
        XWPFTable table = doc.createTable(rows.size() + 2, 7);
        setTableBordersThin(table);
        setTableFixedWidths(table, ORDER_ITEM_COL_WIDTHS);
        for (int c = 0; c < heads.length; c++) setHeaderCell(table.getRow(0).getCell(c), heads[c]);
        markHeaderRow(table.getRow(0));
        int i = 1;
        for (OrderItemRow r : rows) {
            BigDecimal amt = parse(r.amount());
            total = total.add(amt);
            XWPFTableRow tr = table.getRow(i);
            setBodyCell(tr.getCell(0), String.valueOf(i));
            setBodyCell(tr.getCell(1), n(r.name()), ParagraphAlignment.LEFT, false);
            setBodyCell(tr.getCell(2), n(r.unit()));
            setBodyCell(tr.getCell(3), n(r.quantity()));
            setBodyCell(tr.getCell(4), n(r.unitPrice()));
            setBodyCell(tr.getCell(5), n(r.amount()));
            setBodyCell(tr.getCell(6), n(r.remark()), ParagraphAlignment.LEFT, false);
            i++;
        }
        // 合计行（先写内容再合并）
        XWPFTableRow totalRow = table.getRow(rows.size() + 1);
        setBodyCell(totalRow.getCell(0), "合计");
        for (int c = 1; c <= 4; c++) setBodyCell(totalRow.getCell(c), "");
        setBodyCell(totalRow.getCell(5), fmt(total) + " 元", ParagraphAlignment.CENTER, true);
        setBodyCell(totalRow.getCell(6), "");
        mergeCellsHorizontal(totalRow, 1, 4);
    }

    private static void addComponentTables(XWPFDocument doc, List<ComponentRow> comps) {
        String[] heads = {"序号", "子物料名称", "单位", "每套用量", "需求总数", "损耗率(%)"};
        for (ComponentRow comp : comps) {
            // 分组标题：父物料名称 + 下单数量
            XWPFParagraph title = doc.createParagraph();
            XWPFRun tr = title.createRun();
            tr.setText(n(comp.parentName()) + "（下单数：" + n(comp.orderQty()) + " " + n(comp.unit()) + "）");
            tr.setBold(true);
            tr.setFontFamily("宋体");
            tr.setFontSize(FONT_BODY);

            XWPFTable table = doc.createTable(comp.children().size() + 1, 6);
            setTableBordersThin(table);
            setTableFixedWidths(table, COMPONENT_COL_WIDTHS);
            for (int c = 0; c < heads.length; c++) setHeaderCell(table.getRow(0).getCell(c), heads[c]);
            markHeaderRow(table.getRow(0));
            int j = 1;
            for (String[] child : comp.children()) {
                XWPFTableRow row = table.getRow(j);
                setBodyCell(row.getCell(0), String.valueOf(j));
                setBodyCell(row.getCell(1), n(child[0]), ParagraphAlignment.LEFT, false);
                setBodyCell(row.getCell(2), n(child[1]));
                setBodyCell(row.getCell(3), n(child[2]));
                setBodyCell(row.getCell(4), n(child[3]));
                setBodyCell(row.getCell(5), n(child[4]));
                j++;
            }
            addBlank(doc);
        }
    }

    // ======================== 条款 ========================

    /** 解析条款富文本 HTML，逐段写入 */
    private static void addClauses(XWPFDocument doc, String html) {
        if (html == null || html.isBlank()) return;
        // 去掉 <h1> 标题（标题已在文档顶部单独输出，避免重复）
        String body = html.replaceAll("(?is)<h1[^>]*>.*?</h1>", "");
        // 按块拆分：标题、段落、列表项
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?is)<(h2|h3)[^>]*>(.*?)</\\1>|<p[^>]*>(.*?)</p>|<li[^>]*>(.*?)</li>").matcher(body);
        boolean found = false;
        while (m.find()) {
            found = true;
            String heading = m.group(1) != null ? m.group(2) : null;
            String para = m.group(3);
            String li = m.group(4);
            String text = heading != null ? heading : (para != null ? para : li);
            text = stripTags(text);
            if (text.isBlank()) continue;
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setText(text);
            r.setFontFamily("宋体");
            if (heading != null) {
                r.setBold(true);
                r.setFontSize(FONT_SECTION);
            } else {
                r.setFontSize(FONT_BODY);
            }
        }
        // 兜底：若无匹配标签（纯文本），直接写一行
        if (!found) {
            String text = stripTags(body);
            if (!text.isBlank()) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText(text);
                r.setFontFamily("宋体");
                r.setFontSize(FONT_BODY);
            }
        }
    }

    // ======================== 单元格样式 ========================

    /** 表头单元格：加粗、居中、浅灰底纹、垂直居中 */
    private static void setHeaderCell(XWPFTableCell cell, String text) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontFamily("宋体");
        r.setFontSize(FONT_BODY);
        cell.setColor(HEADER_SHADING);
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
    }

    /** 正文单元格：居中、垂直居中 */
    private static void setBodyCell(XWPFTableCell cell, String text) {
        setBodyCell(cell, text, ParagraphAlignment.CENTER, false);
    }

    /** 正文单元格：自定义水平对齐与加粗 */
    private static void setBodyCell(XWPFTableCell cell, String text, ParagraphAlignment align, boolean bold) {
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(align);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(bold);
        r.setFontFamily("宋体");
        r.setFontSize(FONT_BODY);
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
    }

    /**
     * 水平合并单元格：起始格设置 gridSpan 跨列，并移除被吞并的多余物理单元格。
     * 注意：必须在写完该行所有单元格内容之后调用，否则删格后 XWPF 层索引错位
     */
    private static void mergeCellsHorizontal(XWPFTableRow tr, int startCol, int endCol) {
        try {
            // 先累加待合并各列的 tcW 宽度，合并后写回首格，保证跨列格宽度=各列宽之和
            BigInteger mergedWidth = BigInteger.ZERO;
            for (int i = startCol; i <= endCol; i++) {
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr pr =
                        tr.getCell(i).getCTTc().isSetTcPr() ? tr.getCell(i).getCTTc().getTcPr() : tr.getCell(i).getCTTc().addNewTcPr();
                CTTblWidth w = pr.isSetTcW() ? pr.getTcW() : pr.addNewTcW();
                // poi-ooxml-lite 中 getW() 返回 Object，需显式判断 BigInteger 类型
                if (w.getW() instanceof BigInteger colW) mergedWidth = mergedWidth.add(colW);
            }
            XWPFTableCell start = tr.getCell(startCol);
            CTTcPr startPr = start.getCTTc().isSetTcPr() ? start.getCTTc().getTcPr() : start.getCTTc().addNewTcPr();
            CTDecimalNumber span = startPr.isSetGridSpan() ? startPr.getGridSpan() : startPr.addNewGridSpan();
            span.setVal(BigInteger.valueOf(endCol - startCol + 1));
            // 起始格宽度改为跨列总宽，避免 tcW 与 tblGrid 不一致导致后续金额列错位
            CTTblWidth startW = startPr.isSetTcW() ? startPr.getTcW() : startPr.addNewTcW();
            startW.setW(mergedWidth);
            startW.setType(STTblWidth.DXA);
            // 从后往前删除被合并的物理单元格，避免索引位移
            for (int i = endCol; i > startCol; i--) {
                tr.getCtRow().removeTc(i);
            }
        } catch (Exception ignore) { /* 合并失败时不影响导出 */ }
    }

    // ======================== 基础工具 ========================

    private static String stripTags(String html) {
        if (html == null) return "";
        return html.replaceAll("(?is)<[^>]+>", "")
                   .replace("&nbsp;", " ")
                   .replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"")
                   .trim();
    }

    private static String n(String v) { return v != null ? v : ""; }
    private static BigDecimal parse(String v) { return v != null && !v.isBlank() ? new BigDecimal(v) : BigDecimal.ZERO; }
    private static String fmt(BigDecimal v) { return v == null ? "" : v.stripTrailingZeros().toPlainString(); }
}
