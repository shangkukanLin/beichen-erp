package com.beichen.erp.outsource.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.inventory.entity.InventoryWarehouse;
import com.beichen.erp.inventory.mapper.InventoryWarehouseMapper;
import com.beichen.erp.outsource.entity.CloseReportItem;
import com.beichen.erp.outsource.service.CloseReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outsource/order/{orderId}/close-report")
@RequiredArgsConstructor
public class CloseReportController {

    private final CloseReportService reportService;
    private final InventoryWarehouseMapper inventoryWarehouseMapper;

    @GetMapping
    public R<Map<String, Object>> getReport(@PathVariable Long orderId) {
        return R.ok(reportService.getOrCreateReport(orderId));
    }

    @PutMapping
    public R<Void> saveDraft(@PathVariable Long orderId, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsRaw = (List<Map<String, Object>>) body.get("items");
        List<CloseReportItem> items = itemsRaw != null ? itemsRaw.stream().map(m -> {
            CloseReportItem i = new CloseReportItem();
            i.setMaterialName((String) m.get("materialName"));
            i.setMaterialType((String) m.get("materialType"));
            i.setUnit((String) m.get("unit"));
            if (m.get("deliveredQuantity") != null) i.setDeliveredQuantity(new BigDecimal(m.get("deliveredQuantity").toString()));
            if (m.get("returnedQuantity") != null) i.setReturnedQuantity(new BigDecimal(m.get("returnedQuantity").toString()));
            if (m.get("goodReturnQty") != null) i.setGoodReturnQty(new BigDecimal(m.get("goodReturnQty").toString()));
            if (m.get("defectReturnQty") != null) i.setDefectReturnQty(new BigDecimal(m.get("defectReturnQty").toString()));
            if (m.get("shippedQuantity") != null) i.setShippedQuantity(new BigDecimal(m.get("shippedQuantity").toString()));
            if (m.get("targetYieldRate") != null) i.setTargetYieldRate(new BigDecimal(m.get("targetYieldRate").toString()));
            if (m.get("actualYieldRate") != null) i.setActualYieldRate(new BigDecimal(m.get("actualYieldRate").toString()));
            if (m.get("yieldLoss") != null) i.setYieldLoss(new BigDecimal(m.get("yieldLoss").toString()));
            if (m.get("excessLossQty") != null) i.setExcessLossQty(new BigDecimal(m.get("excessLossQty").toString()));
            if (m.get("unitPrice") != null) i.setMaterialPrice(new BigDecimal(m.get("unitPrice").toString()));
            i.setRemark((String) m.get("remark"));
            return i;
        }).toList() : List.of();
        String remark = (String) body.get("remark");
        reportService.saveDraft(orderId, items, remark);
        return R.ok();
    }

    @PostMapping("/confirm")
    public R<Void> confirm(@PathVariable Long orderId) {
        reportService.confirmClose(orderId);
        return R.ok();
    }

    /** 导出 Excel（含公式） */
    @GetMapping("/export")
    public void export(@PathVariable Long orderId, HttpServletResponse resp) throws Exception {
        Map<String, Object> report = reportService.getOrCreateReport(orderId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) report.get("items");
        @SuppressWarnings("unchecked")
        List<?> deliveriesRaw = (List<?>) report.get("deliveries");

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("结单报表");

        // ---------- 样式 ----------
        CellStyle titleStyle = createStyle(wb, true, HorizontalAlignment.CENTER, (short)16, null);
        CellStyle labelStyle = createStyle(wb, true, HorizontalAlignment.LEFT, (short)11, null);
        CellStyle valueStyle = createStyle(wb, false, HorizontalAlignment.LEFT, (short)11, null);
        CellStyle headerStyle = createStyle(wb, true, HorizontalAlignment.CENTER, (short)10, null);
        CellStyle numStyle = createStyle(wb, false, HorizontalAlignment.RIGHT, (short)10, "#,##0.00");
        CellStyle pctStyle = createStyle(wb, false, HorizontalAlignment.RIGHT, (short)10, "0.00");
        CellStyle textStyle = createStyle(wb, false, HorizontalAlignment.LEFT, (short)10, null);
        CellStyle sectionStyle = createStyle(wb, true, HorizontalAlignment.LEFT, (short)12, null);

        // ---------- Section 1: 基本信息 ----------
        int rowIdx = 0;
        // ===== 标题 =====
        Row tRow = sheet.createRow(rowIdx++); tRow.setHeightInPoints(24);
        Cell tc = tRow.createCell(0); tc.setCellValue("委外加工单结单报表 - " + report.get("orderCode")); tc.setCellStyle(titleStyle);
        merge(tRow, 0, 0, 15);

        // ===== 一、订单信息 =====
        Row s1 = sheet.createRow(rowIdx++); s1.setHeightInPoints(18);
        Cell sc1 = s1.createCell(0); sc1.setCellValue("一、订单信息"); sc1.setCellStyle(sectionStyle);
        merge(s1, 0, 0, 15);

        // 左：基本信息 (col 0-1) ，右：加工产品 (col 2-3)
        List<?> productsRaw = (List<?>) report.get("products");
        int firstProductRow = rowIdx;
        // 加工单号行
        Row r1a = sheet.createRow(rowIdx++);
        textBold(r1a, 0, "加工单号", labelStyle); textCell(r1a, 1, str(report.get("orderCode")), valueStyle);
        if (productsRaw != null && !productsRaw.isEmpty()) {
            Map<String, Object> p0 = toMap(productsRaw.get(0));
            textBold(r1a, 2, "加工产品", labelStyle); textCell(r1a, 3, str(p0.get("productName")), valueStyle);
        }
        // 加工厂行
        Row r1b = sheet.createRow(rowIdx++);
        textBold(r1b, 0, "加工厂", labelStyle); textCell(r1b, 1, str(report.get("factoryName")), valueStyle);
        if (productsRaw != null && !productsRaw.isEmpty()) {
            Map<String, Object> p0 = toMap(productsRaw.get(0));
            textBold(r1b, 2, "订单数量", labelStyle); numCellRaw(r1b, 3, p0.get("quantity"), valueStyle);
        }
        rowIdx++;

        // ===== 二、物料明细 =====
        Row s2 = sheet.createRow(rowIdx++); s2.setHeightInPoints(18);
        Cell sc2 = s2.createCell(0); sc2.setCellValue("二、物料明细"); sc2.setCellStyle(sectionStyle);
        merge(s2, 0, 0, 15);

        String[] headers = {"类目","物料名称","发料数量","退料总计","出货消耗","良品退料","不良退料","缺失","加工良率%","生产良率%","良率超损%","超损数量","最大超损","物料单价","超损总价","备注"};
        Row hRow = sheet.createRow(rowIdx++);
        for (int c = 0; c < headers.length; c++) hRow.createCell(c).setCellValue(headers[c]);
        for (int c = 0; c < headers.length; c++) hRow.getCell(c).setCellStyle(headerStyle);

        int[] widths = {10, 22, 12, 12, 12, 12, 12, 12, 13, 13, 13, 13, 13, 13, 13, 18};
        for (int c = 0; c < widths.length; c++) sheet.setColumnWidth(c, widths[c] * 256);

        for (int r = 0; r < items.size(); r++) {
            Map<String, Object> it = items.get(r);
            Row dRow = sheet.createRow(rowIdx++);
            int curRow = dRow.getRowNum() + 1;

            textCell(dRow, 0, (String) it.get("materialType"), textStyle);
            textCell(dRow, 1, (String) it.get("materialName"), textStyle);
            numCell(dRow, 2, it, "deliveredQuantity", numStyle);
            formulaCell(dRow, 3, "F" + curRow + "+G" + curRow, numStyle);
            numCell(dRow, 4, it, "shippedQuantity", numStyle);
            numCell(dRow, 5, it, "goodReturnQty", numStyle);
            numCell(dRow, 6, it, "defectReturnQty", numStyle);
            formulaCell(dRow, 7, "C" + curRow + "-D" + curRow + "-E" + curRow, numStyle);
            numCell(dRow, 8, it, "targetYieldRate", pctStyle);
            formulaCell(dRow, 9, "IF(C" + curRow + ">0,(E" + curRow + "+F" + curRow + ")/C" + curRow + "*100,0)", pctStyle);
            formulaCell(dRow, 10, "I" + curRow + "-J" + curRow, pctStyle);
            formulaCell(dRow, 11, "MAX(0,C" + curRow + "*I" + curRow + "/100-E" + curRow + "-F" + curRow + ")", numStyle);
            // 最大超损 = MAX(0, (C-F)*(1-I/100))
            formulaCell(dRow, 12, "MAX(0,(C" + curRow + "-F" + curRow + ")*(1-I" + curRow + "/100))", numStyle);
            // 物料单价
            numCell(dRow, 13, it, "unitPrice", numStyle);
            // 超损总价 = L * M
            formulaCell(dRow, 14, "L" + curRow + "*M" + curRow, numStyle);
            // 备注
            textCell(dRow, 15, (String) it.get("remark"), textStyle);
        }

        // ===== 三、交货记录 =====
        if (deliveriesRaw != null && !deliveriesRaw.isEmpty()) {
            rowIdx++;
            Row s3 = sheet.createRow(rowIdx++); s3.setHeightInPoints(18);
            Cell sc3 = s3.createCell(0); sc3.setCellValue("三、交货记录"); sc3.setCellStyle(sectionStyle);
            merge(s3, 0, 0, 5);

            String[] dHeaders = {"日期","产品名称","数量","类型","收货仓库","备注"};
            Row dH = sheet.createRow(rowIdx++);
            for (int c = 0; c < dHeaders.length; c++) { dH.createCell(c).setCellValue(dHeaders[c]); dH.getCell(c).setCellStyle(headerStyle); }

            BigDecimal normalTotal = BigDecimal.ZERO, defectTotal = BigDecimal.ZERO;
            for (Object obj : deliveriesRaw) {
                Map<String, Object> d;
                if (obj instanceof Map) { @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) obj; d = m; }
                else { var dlv = (com.beichen.erp.outsource.entity.OutsourceOrderDelivery) obj;
                    d = new java.util.LinkedHashMap<>();
                    d.put("deliveryDate", dlv.getDeliveryDate()); d.put("productName", dlv.getProductName());
                    d.put("quantity", dlv.getQuantity()); d.put("deliveryType", dlv.getDeliveryType());
                    d.put("warehouseId", dlv.getWarehouseId()); d.put("remark", dlv.getRemark()); }
                Row dr = sheet.createRow(rowIdx++);
                textCell(dr, 0, str(d.get("deliveryDate")), textStyle);
                textCell(dr, 1, str(d.get("productName")), textStyle);
                numCellRaw(dr, 2, d.get("quantity"), numStyle);
                String dtype = str(d.get("deliveryType"));
                textCell(dr, 3, "退不良".equals(dtype) ? "退不良" : "交货", textStyle);
                String whName = "";
                Object whIdObj = d.get("warehouseId");
                if (whIdObj != null) { InventoryWarehouse iw = inventoryWarehouseMapper.selectById(Long.valueOf(whIdObj.toString())); if (iw != null) whName = iw.getWarehouseName(); }
                textCell(dr, 4, whName, textStyle);
                textCell(dr, 5, str(d.get("remark")), textStyle);
                BigDecimal q = toBigDecimal(d.get("quantity"));
                if ("退不良".equals(dtype)) defectTotal = defectTotal.add(q.abs()); else normalTotal = normalTotal.add(q);
            }
            // 合计行
            rowIdx++;
            Row totalRow = sheet.createRow(rowIdx++);
            Cell t0 = totalRow.createCell(0); t0.setCellValue("合计"); t0.setCellStyle(headerStyle);
            Cell t1 = totalRow.createCell(1); t1.setCellValue("实际已交：" + normalTotal.subtract(defectTotal).stripTrailingZeros().toPlainString()); t1.setCellStyle(textStyle);
            Cell t3 = totalRow.createCell(3); t3.setCellValue("退不良：" + defectTotal.stripTrailingZeros().toPlainString()); t3.setCellStyle(textStyle);
        }

        // 输出
        String filename = "close_report_" + report.get("orderCode") + ".xlsx";
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        OutputStream os = resp.getOutputStream();
        wb.write(os); os.flush(); wb.close();
    }

    // ========== 工具方法 ==========
    private CellStyle createStyle(Workbook wb, boolean bold, HorizontalAlignment align, short fontSize, String fmtStr) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont(); f.setBold(bold); f.setFontHeightInPoints(fontSize);
        s.setFont(f); s.setAlignment(align); s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.THIN); s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN); s.setBorderRight(BorderStyle.THIN); s.setWrapText(false);
        if (fmtStr != null) s.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat(fmtStr));
        return s;
    }
    private void merge(Row row, int col, int c1, int c2) {
        sheet(row).addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), c1, c2));
    }
    private Sheet sheet(Row row) { return row.getSheet(); }
    private String str(Object o) { return o != null ? o.toString() : ""; }
    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }
    private void addInfo(Sheet sheet, int row, String label, Map<String, Object> data, String key, CellStyle labelStyle, CellStyle valueStyle) {
        Row r = sheet.createRow(row);
        Cell lc = r.createCell(0); lc.setCellValue(label); lc.setCellStyle(labelStyle);
        Cell vc = r.createCell(1); vc.setCellValue(str(data.get(key))); vc.setCellStyle(valueStyle);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 1, 3));
    }
    private void textCell(Row row, int col, String val, CellStyle style) { Cell c = row.createCell(col); c.setCellValue(val); c.setCellStyle(style); }
    private void textBold(Row row, int col, String val, CellStyle style) { Cell c = row.createCell(col); c.setCellValue(val); c.setCellStyle(style); }
    private java.util.Map<String, Object> toMap(Object obj) {
        if (obj instanceof java.util.Map) { @SuppressWarnings("unchecked") java.util.Map<String, Object> m = (java.util.Map<String, Object>) obj; return m; }
        var pr = (com.beichen.erp.outsource.entity.OutsourceOrderProduct) obj;
        java.util.Map<String, Object> p = new java.util.LinkedHashMap<>();
        p.put("productName", pr.getProductName()); p.put("productSpec", pr.getProductSpec()); p.put("quantity", pr.getQuantity());
        return p;
    }
    private void numCell(Row row, int col, Map<String, Object> it, String key, CellStyle style) { numCellRaw(row, col, it.get(key), style); }
    private void numCellRaw(Row row, int col, Object v, CellStyle style) {
        Cell c = row.createCell(col);
        if (v != null) { try { c.setCellValue(Double.parseDouble(v.toString())); } catch (Exception ignored) {} }
        c.setCellStyle(style);
    }
    private void formulaCell(Row row, int col, String formula, CellStyle style) {
        Cell c = row.createCell(col); c.setCellFormula(formula); c.setCellStyle(style);
    }
}
