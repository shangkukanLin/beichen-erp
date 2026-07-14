package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.entity.ContractTemplate;
import com.beichen.erp.outsource.entity.MaterialOrder;
import com.beichen.erp.outsource.entity.MaterialOrderItem;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceMaterialComponent;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderMaterial;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.mapper.MaterialOrderItemMapper;
import com.beichen.erp.outsource.mapper.MaterialOrderMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialComponentMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.service.ContractTemplateService;
import com.beichen.erp.outsource.service.OutsourceOrderService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.system.entity.Company;
import com.beichen.erp.system.mapper.CompanyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/outsource/contract-template")
@RequiredArgsConstructor
public class ContractTemplateController {

    private final ContractTemplateService templateService;
    private final OutsourceOrderService orderService;
    private final SupplierMapper supplierMapper;
    private final CompanyMapper companyMapper;
    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;
    private final OutsourceMaterialComponentMapper componentMapper;
    private final OutsourceMaterialMapper materialMapper;

    private static final int A4_W = 595;
    private static final int A4_H = 842;
    private static final int MARGIN = 40;

    @GetMapping("/list") public R<List<ContractTemplate>> list(@RequestParam(required = false) String templateType) { return R.ok(templateService.list(templateType)); }
    @GetMapping("/{id}") public R<ContractTemplate> getById(@PathVariable Long id) { return R.ok(templateService.getById(id)); }
    @PostMapping public R<Void> create(@RequestBody ContractTemplate t) { templateService.save(t); return R.ok(); }
    @PutMapping("/{id}") public R<Void> update(@PathVariable Long id, @RequestBody ContractTemplate t) { t.setId(id); templateService.update(t); return R.ok(); }
    @DeleteMapping("/{id}") public R<Void> delete(@PathVariable Long id) { templateService.delete(id); return R.ok(); }
    @PutMapping("/{id}/default") public R<Void> setDefault(@PathVariable Long id) { templateService.setDefault(id); return R.ok(); }

    @GetMapping("/export/{orderId}")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long orderId,
            @RequestParam(required = false) Long templateId) {
        OutsourceOrder order = orderService.getById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");
        ContractTemplate template = templateId != null ? templateService.getById(templateId) : templateService.getDefault("加工合同");
        if (template == null || template.getContent() == null) throw new BusinessException("未找到合同模板");
        byte[] pdfBytes = doExport(order, template);
        String fileName = URLEncoder.encode("委外加工合同-" + order.getCode() + ".pdf", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_PDF).body(pdfBytes);
    }

    // ======================== 物料订单合同导出 ========================
    @GetMapping("/export-material-order/{orderId}")
    public ResponseEntity<byte[]> exportMaterialOrderPdf(@PathVariable Long orderId,
            @RequestParam(required = false) Long templateId) {
        MaterialOrder order = materialOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("物料订单不存在");
        ContractTemplate template = templateId != null ? templateService.getById(templateId) : templateService.getDefault("采购合同");
        if (template == null || template.getContent() == null) throw new BusinessException("未找到采购合同模板");
        byte[] pdfBytes = doExportMaterialOrder(order, template);
        String fileName = URLEncoder.encode("物料采购合同-" + order.getCode() + ".pdf", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_PDF).body(pdfBytes);
    }

    private byte[] doExportMaterialOrder(MaterialOrder order, ContractTemplate template) {
        try {
            String html = buildMaterialOrderHtml(order, template);
            BufferedImage full = render(html);
            return toPdf(full);
        } catch (Exception e) {
            log.error("物料订单PDF生成失败: {}", e.getMessage(), e);
            throw new BusinessException("PDF生成失败：" + e.getMessage());
        }
    }

    private String buildMaterialOrderHtml(MaterialOrder order, ContractTemplate tplObj) {
        Company co = companyMapper.selectById(1L);
        String cn = co != null ? co.getCompanyName() : "甲方";
        Supplier supp = order.getSupplierId() != null ? supplierMapper.selectById(order.getSupplierId()) : null;
        String sn = supp != null ? supp.getName() : "";
        String ds = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
        List<MaterialOrderItem> items = materialOrderItemMapper.selectList(
            new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, order.getId()));

        String body = tplObj.getContent();
        body = body.replace("{甲方}", cn).replace("{乙方}", sn).replace("{订单号}", order.getCode()).replace("{日期}", ds).replace("{备注}", n(order.getRemark()))
            .replace("{甲方地址}", n(tplObj.getPartyAAddress())).replace("{甲方联系人}", n(tplObj.getPartyAContact())).replace("{甲方电话}", n(tplObj.getPartyAPhone()))
            .replace("{乙方地址}", supp != null ? n(supp.getAddress()) : "").replace("{乙方联系人}", supp != null ? n(supp.getContact()) : "").replace("{乙方电话}", supp != null ? n(supp.getPhone()) : "");
        body = body.replace("{合同信息}", infoBlockMo(cn, sn, supp, tplObj));
        body = body.replace("{物料明细表格}", itemTable(items));
        body = body.replace("{组件表格}", compTable(items));
        body = body.replace("{产品表格}", "").replace("{物料表格}", "");
        body = body.replace("{签名区}", signBlock(cn, sn, ds));

        body = body.replaceAll("<h1([^>]*)>", "<center><h1$1 style='font-size:16px;text-align:center'>")
                   .replaceAll("</h1>", "</h1></center>");
        body = body.replaceAll("<h2([^>]*)>", "<center><h2$1 style='font-size:16px;text-align:center'>")
                   .replaceAll("</h2>", "</h2></center>");
        body = body.replaceAll("<h3([^>]*)>", "<center><h3$1 style='font-size:14px;color:#666;text-align:center'>")
                   .replaceAll("</h3>", "</h3></center>");

        return "<html><head><meta charset='utf-8'><style>"
            + "body{font-family:'Microsoft YaHei',SimHei,sans-serif;font-size:8px;line-height:1.8;margin:0;padding:25px 35px;color:#333}"
            + "h1{font-size:16px;margin:0 0 6px}h2{font-size:14px;margin:10px 0}h3{font-size:12px;margin:6px 0}"
            + "p{margin:3px 0}"
            + ".info-table{width:100%;margin:8px 0 14px;border:none}.info-table td{border:none;padding:3px 8px;vertical-align:top;width:50%}"
            + ".data-table{width:100%;margin:6px 0 12px;border-collapse:collapse}"
            + ".data-table th,.data-table td{border:1px solid #777;padding:4px 6px;text-align:center;vertical-align:middle;font-size:7px}"
            + ".data-table th{background:#ffffff;font-weight:bold}.total-row td{font-weight:bold;background:#f5f5f5}"
            + ".sign-table{width:100%;margin-top:20px;border:none}.sign-table td{border:none;padding:5px 10px;vertical-align:top;width:50%;font-size:8px}"
            + "</style></head><body>" + body + "</body></html>";
    }

    private String infoBlockMo(String cn, String sn, Supplier supp, ContractTemplate t) {
        return "<table class='info-table'><tr><td><p><b>甲方：" + cn + "</b></p><p>地址：" + n(t.getPartyAAddress()) + "</p><p>联系人：" + n(t.getPartyAContact()) + "</p><p>电话：" + n(t.getPartyAPhone()) + "</p></td>"
            + "<td><p><b>乙方：" + sn + "</b></p><p>地址：" + (supp != null ? n(supp.getAddress()) : "") + "</p><p>联系人：" + (supp != null ? n(supp.getContact()) : "") + "</p><p>电话：" + (supp != null ? n(supp.getPhone()) : "") + "</p></td></tr></table>";
    }

    private String itemTable(List<MaterialOrderItem> items) {
        StringBuilder sb = new StringBuilder("<table class='data-table'>");
        sb.append("<tr><th align='center' valign='middle'>序号</th><th align='center' valign='middle'>物料名称</th><th align='center' valign='middle'>单位</th><th align='center' valign='middle'>数量</th><th align='center' valign='middle'>单价(元)</th><th align='center' valign='middle'>金额(元)</th><th align='center' valign='middle'>备注</th></tr>");
        BigDecimal total = BigDecimal.ZERO; int i = 1;
        if (items != null) for (MaterialOrderItem it : items) {
            BigDecimal a = it.getAmount() != null ? it.getAmount() : BigDecimal.ZERO; total = total.add(a);
            sb.append("<tr><td align='center' valign='middle'>").append(i++).append("</td><td align='center' valign='middle'>").append(n(it.getMaterialName())).append("</td><td align='center' valign='middle'>").append(n(it.getUnit())).append("</td><td align='center' valign='middle'>").append(fmt(it.getOrderQuantity())).append("</td><td align='center' valign='middle'>").append(fmt(it.getUnitPrice())).append("</td><td align='center' valign='middle'>").append(fmt(a)).append("</td><td align='center' valign='middle'>").append(n(it.getRemark())).append("</td></tr>");
        }
        sb.append("<tr class='total-row'><td align='center' valign='middle' colspan='4'>合计</td><td align='center' valign='middle' colspan='3'>").append(fmt(total)).append(" 元</td></tr></table>");
        return sb.toString();
    }

    private String compTable(List<MaterialOrderItem> items) {
        // 收集所有带组件的物料
        StringBuilder sb = new StringBuilder();
        boolean hasAny = false;
        if (items != null) {
            for (MaterialOrderItem it : items) {
                if (it.getMaterialId() == null) continue;
                List<OutsourceMaterialComponent> comps = componentMapper.selectList(
                    new LambdaQueryWrapper<OutsourceMaterialComponent>().eq(OutsourceMaterialComponent::getParentMaterialId, it.getMaterialId()));
                if (comps != null && !comps.isEmpty()) {
                    if (!hasAny) {
                        hasAny = true;
                        sb.append("<h3>三、物料所含子物料明细</h3>");
                    }
                    sb.append("<p><b>").append(n(it.getMaterialName())).append("（下单数：").append(fmt(it.getOrderQuantity())).append(" ").append(n(it.getUnit())).append("）</b></p>");
                    sb.append("<table class='data-table'><tr><th align='center' valign='middle'>序号</th><th align='center' valign='middle'>子物料名称</th><th align='center' valign='middle'>单位</th><th align='center' valign='middle'>每套用量</th><th align='center' valign='middle'>需求总数</th><th align='center' valign='middle'>损耗率(%)</th></tr>");
                    int j = 1;
                    for (OutsourceMaterialComponent c : comps) {
                        BigDecimal perQty = c.getQuantity() != null ? c.getQuantity() : BigDecimal.ONE;
                        BigDecimal totalQty = perQty.multiply(it.getOrderQuantity() != null ? it.getOrderQuantity() : BigDecimal.ONE);
                        String childName = "";
                        String childUnit = "";
                        OutsourceMaterial child = materialMapper.selectById(c.getChildMaterialId());
                        if (child != null) { childName = child.getMaterialName() != null ? child.getMaterialName() : ""; childUnit = child.getUnit() != null ? child.getUnit() : ""; }
                        sb.append("<tr><td align='center' valign='middle'>").append(j++).append("</td><td align='center' valign='middle'>").append(childName).append("</td><td align='center' valign='middle'>").append(childUnit).append("</td><td align='center' valign='middle'>").append(fmt(perQty)).append("</td><td align='center' valign='middle'>").append(fmt(totalQty)).append("</td><td align='center' valign='middle'>").append(fmt(c.getLossRate())).append("</td></tr>");
                    }
                    sb.append("</table>");
                }
            }
        }
        return sb.toString();
    }

    private byte[] doExport(OutsourceOrder order, ContractTemplate template) {
        try {
            String html = buildHtml(order, template);
            // 渲染完整 HTML 为一张长图
            BufferedImage full = render(html);
            // 分割长图为 A4 页面并输出 PDF
            return toPdf(full);
        } catch (Exception e) {
            log.error("PDF生成失败: {}", e.getMessage(), e);
            throw new BusinessException("PDF生成失败：" + e.getMessage());
        }
    }

    // ----------------- 渲染 HTML → 长图 -----------------
    private BufferedImage render(String html) throws Exception {
        JEditorPane pane = new JEditorPane("text/html", html);
        // 先让 Swing 以无限高度布局以获取实际内容尺寸
        pane.setSize(A4_W, Short.MAX_VALUE);
        Thread.sleep(400);
        int h = pane.getPreferredSize().height + 60;
        log.info("内容高度: {} px, A4={} px", h, A4_H);

        BufferedImage img = new BufferedImage(A4_W, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE); g.fillRect(0, 0, A4_W, h);
        pane.setSize(A4_W, h); pane.validate(); Thread.sleep(100);
        pane.paint(g); g.dispose();
        return img;
    }

    // ----------------- 长图 → 多页 PDF（避免行被截断） -----------------
    private byte[] toPdf(BufferedImage full) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            int y = 0, pageNum = 0;
            // 文本行高约 18px，对齐到行边界避免字符断裂
            int lineH = 18;
            int fullH = full.getHeight();
            // 取实际宽高比，决定绘制时是否需要适配
            int imgW = full.getWidth();

            while (y < fullH) {
                int sh = Math.min(A4_H, fullH - y);
                if (y + sh < fullH) { sh = (sh / lineH) * lineH; }
                if (sh <= 0) sh = lineH;

                BufferedImage slice = full.getSubimage(0, y, imgW, sh);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(slice, "PNG", png);

                PDPage page = new PDPage(PDRectangle.A4); doc.addPage(page);
                PDImageXObject img = PDImageXObject.createFromByteArray(doc, png.toByteArray(), "p" + pageNum);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    // 1:1 绘制（slice 宽度已等于 A4_W），垂直方向贴齐顶部
                    cs.drawImage(img, 0, PDRectangle.A4.getHeight() - sh, slice.getWidth(), sh);
                }
                y += sh; pageNum++;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            log.info("PDF: {} pages, {} bytes", pageNum, bos.size());
            return bos.toByteArray();
        }
    }

    // ======================== HTML ========================
    private String buildHtml(OutsourceOrder order, ContractTemplate tplObj) {
        Company co = companyMapper.selectById(1L);
        String cn = co != null ? co.getCompanyName() : "甲方";
        Supplier fac = order.getFactoryId() != null ? supplierMapper.selectById(order.getFactoryId()) : null;
        String fn = fac != null ? fac.getName() : "";
        String ds = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
        List<OutsourceOrderProduct> pros = orderService.getProducts(order.getId());
        List<OutsourceOrderMaterial> mats = new ArrayList<>();
        if (pros != null) for (OutsourceOrderProduct p : pros) { List<OutsourceOrderMaterial> m = orderService.getMaterials(p.getId()); if (m != null) mats.addAll(m); }

        String body = tplObj.getContent();
        body = body.replace("{甲方}", cn).replace("{乙方}", fn).replace("{加工单号}", order.getCode()).replace("{日期}", ds).replace("{备注}", n(order.getRemark()))
            .replace("{甲方地址}", n(tplObj.getPartyAAddress())).replace("{甲方联系人}", n(tplObj.getPartyAContact())).replace("{甲方电话}", n(tplObj.getPartyAPhone()))
            .replace("{乙方地址}", fac != null ? n(fac.getAddress()) : "").replace("{乙方联系人}", fac != null ? n(fac.getContact()) : "").replace("{乙方电话}", fac != null ? n(fac.getPhone()) : "");
        body = body.replace("{合同信息}", infoBlock(cn, fn, fac, tplObj));
        body = body.replace("{产品表格}", prodTable(pros));
        body = body.replace("{物料表格}", matTable(mats));
        body = body.replace("{签名区}", signBlock(cn, fn, ds));

        // HTMLEditorKit 对 text-align:center 支持不稳定，用 <center> 标签包裹标题
        body = body.replaceAll("<h1([^>]*)>", "<center><h1$1 style='font-size:16px;text-align:center'>")
                   .replaceAll("</h1>", "</h1></center>");
        body = body.replaceAll("<h2([^>]*)>", "<center><h2$1 style='font-size:16px;text-align:center'>")
                   .replaceAll("</h2>", "</h2></center>");
        body = body.replaceAll("<h3([^>]*)>", "<center><h3$1 style='font-size:14px;color:#666;text-align:center'>")
                   .replaceAll("</h3>", "</h3></center>");

        return "<html><head><meta charset='utf-8'><style>"
            + "body{font-family:'Microsoft YaHei',SimHei,sans-serif;font-size:8px;line-height:1.8;margin:0;padding:25px 35px;color:#333}"
            + "h1{font-size:16px;margin:0 0 6px}h2{font-size:14px;margin:10px 0}h3{font-size:12px;margin:6px 0}"
            + "p{margin:3px 0}"
            + ".info-table{width:100%;margin:8px 0 14px;border:none}.info-table td{border:none;padding:3px 8px;vertical-align:top;width:50%}"
            + ".data-table{width:100%;margin:6px 0 12px;border-collapse:collapse}"
            + ".data-table th,.data-table td{border:1px solid #777;padding:4px 6px;text-align:center;vertical-align:middle;font-size:7px}"
            + ".data-table th{background:#ffffff;font-weight:bold}.total-row td{font-weight:bold;background:#f5f5f5}"
            + ".sign-table{width:100%;margin-top:20px;border:none}.sign-table td{border:none;padding:5px 10px;vertical-align:top;width:50%;font-size:8px}"
            + "</style></head><body>" + body + "</body></html>";
    }

    private String infoBlock(String cn, String fn, Supplier fac, ContractTemplate t) {
        return "<table class='info-table'><tr><td><p><b>甲方：" + cn + "</b></p><p>地址：" + n(t.getPartyAAddress()) + "</p><p>联系人：" + n(t.getPartyAContact()) + "</p><p>电话：" + n(t.getPartyAPhone()) + "</p></td>"
            + "<td><p><b>乙方：" + fn + "</b></p><p>地址：" + (fac != null ? n(fac.getAddress()) : "") + "</p><p>联系人：" + (fac != null ? n(fac.getContact()) : "") + "</p><p>电话：" + (fac != null ? n(fac.getPhone()) : "") + "</p></td></tr></table>";
    }

    private String prodTable(List<OutsourceOrderProduct> pros) {
        StringBuilder sb = new StringBuilder("<table class='data-table'>");
        sb.append("<tr><th align='center' valign='middle'>序号</th><th align='center' valign='middle'>名称</th><th align='center' valign='middle'>单价(元)</th><th align='center' valign='middle'>数量</th><th align='center' valign='middle'>合计(元)</th><th align='center' valign='middle'>备注</th></tr>");
        BigDecimal total = BigDecimal.ZERO; int i = 1;
        if (pros != null) for (OutsourceOrderProduct p : pros) {
            BigDecimal a = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO; total = total.add(a);
            sb.append("<tr><td align='center' valign='middle'>").append(i++).append("</td><td align='center' valign='middle'>").append(n(p.getProductName())).append("</td><td align='center' valign='middle'>").append(fmt(p.getUnitPrice())).append("</td><td align='center' valign='middle'>").append(fmt(p.getQuantity())).append("</td><td align='center' valign='middle'>").append(fmt(a)).append("</td><td align='center' valign='middle'>").append(n(p.getRemark())).append("</td></tr>");
        }
        sb.append("<tr class='total-row'><td align='center' valign='middle' colspan='4'>合计</td><td align='center' valign='middle' colspan='2'>").append(fmt(total)).append(" 元</td></tr></table>");
        return sb.toString();
    }

    private String matTable(List<OutsourceOrderMaterial> mats) {
        StringBuilder sb = new StringBuilder("<table class='data-table'>");
        sb.append("<tr><th align='center' valign='middle'>序号</th><th align='center' valign='middle'>物料名称</th><th align='center' valign='middle'>数量</th><th align='center' valign='middle'>损耗率(%)</th><th align='center' valign='middle'>备注</th></tr>");
        int j = 1;
        for (OutsourceOrderMaterial m : mats) {
            sb.append("<tr><td align='center' valign='middle'>").append(j++).append("</td><td align='center' valign='middle'>").append(n(m.getMaterialName())).append("</td><td align='center' valign='middle'>").append(fmt(m.getDemandQuantity())).append("</td><td align='center' valign='middle'>").append(fmt(m.getLossRate())).append("</td><td align='center' valign='middle'>").append(n(m.getRemark())).append("</td></tr>");
        }
        sb.append("</table>"); return sb.toString();
    }

    private String signBlock(String cn, String fn, String ds) {
        return "<table class='sign-table'><tr><td><p><b>甲方（盖章）：" + cn + "</b></p><p><br></p><p>法定代表人/授权代表：</p><p><br></p><p>日期：" + ds + "</p></td>"
            + "<td><p><b>乙方（盖章）：" + fn + "</b></p><p><br></p><p>法定代表人/授权代表：</p><p><br></p><p>日期：</p></td></tr></table>";
    }

    private String n(String v) { return v != null ? v : ""; }
    /** 格式化数字：整数不显示小数部分，有小数则保留有效小数 */
    private String fmt(BigDecimal v) {
        if (v == null) return "";
        return v.stripTrailingZeros().toPlainString();
    }
}
