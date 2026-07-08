package com.beichen.erp.outsource.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.entity.ContractTemplate;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderMaterial;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.service.ContractTemplateService;
import com.beichen.erp.outsource.service.OutsourceOrderService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.system.entity.Company;
import com.beichen.erp.system.mapper.CompanyMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/outsource/contract-template")
@RequiredArgsConstructor
public class ContractTemplateController {

    private final ContractTemplateService templateService;
    private final OutsourceOrderService orderService;
    private final SupplierMapper supplierMapper;
    private final CompanyMapper companyMapper;

    @GetMapping("/list")
    public R<List<ContractTemplate>> list() {
        return R.ok(templateService.list());
    }

    @GetMapping("/{id}")
    public R<ContractTemplate> getById(@PathVariable Long id) {
        return R.ok(templateService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody ContractTemplate template) {
        templateService.save(template);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody ContractTemplate template) {
        template.setId(id);
        templateService.update(template);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/default")
    public R<Void> setDefault(@PathVariable Long id) {
        templateService.setDefault(id);
        return R.ok();
    }

    /** 导出加工合同 PDF */
    @GetMapping("/export/{orderId}")
    public ResponseEntity<Resource> exportPdf(@PathVariable Long orderId,
            @RequestParam(required = false) Long templateId) throws IOException {
        OutsourceOrder order = orderService.getById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");

        ContractTemplate template = templateId != null ? templateService.getById(templateId) : templateService.getDefault();
        if (template == null || template.getContent() == null) throw new BusinessException("未找到合同模板");

        String content = template.getContent();
        // 替换占位变量
        content = replacePlaceholders(content, order, template);

        // 生成 PDF
        byte[] pdfBytes = generatePdf(content);

        String fileName = URLEncoder.encode("委外加工合同-" + order.getCode() + ".pdf", StandardCharsets.UTF_8).replace("+", "%20");
        ByteArrayResource resource = new ByteArrayResource(pdfBytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    private String replacePlaceholders(String content, OutsourceOrder order, ContractTemplate template) {
        // 公司名（甲方）
        Company company = companyMapper.selectById(1L);
        String companyName = company != null ? company.getCompanyName() : "甲方";
        content = content.replace("{甲方}", companyName);

        // 加工厂（乙方）
        Supplier factory = order.getFactoryId() != null ? supplierMapper.selectById(order.getFactoryId()) : null;
        String factoryName = factory != null ? factory.getName() : "";
        content = content.replace("{乙方}", factoryName);

        content = content.replace("{加工单号}", order.getCode());
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
        content = content.replace("{日期}", dateStr);
        content = content.replace("{备注}", order.getRemark() != null ? order.getRemark() : "");

        // 甲方信息（从模板配置读取）
        content = content.replace("{甲方地址}", template.getPartyAAddress() != null ? template.getPartyAAddress() : "");
        content = content.replace("{甲方联系人}", template.getPartyAContact() != null ? template.getPartyAContact() : "");
        content = content.replace("{甲方电话}", template.getPartyAPhone() != null ? template.getPartyAPhone() : "");

        // 乙方信息（从加工厂读取）
        content = content.replace("{乙方地址}", factory != null && factory.getAddress() != null ? factory.getAddress() : "");
        content = content.replace("{乙方联系人}", factory != null && factory.getContact() != null ? factory.getContact() : "");
        content = content.replace("{乙方电话}", factory != null && factory.getPhone() != null ? factory.getPhone() : "");

        // 合同顶部信息表（甲乙双方左中并排）
        String topInfoHtml = "<table style=\"width:100%; margin-bottom:20px; border:none; border-collapse:collapse;\"><tbody><tr>"
                + "<td style=\"width:50%; border:none; padding:4px 8px; text-align:left; vertical-align:top;\">"
                + "<p>甲方：" + escapeHtml(companyName) + "</p>"
                + "<p>联系地址：" + escapeHtml(template.getPartyAAddress()) + "</p>"
                + "<p>联系人：" + escapeHtml(template.getPartyAContact()) + "</p>"
                + "<p>联系电话：" + escapeHtml(template.getPartyAPhone()) + "</p>"
                + "</td>"
                + "<td style=\"width:50%; border:none; padding:4px 8px; text-align:left; vertical-align:top;\">"
                + "<p>乙方：" + escapeHtml(factoryName) + "</p>"
                + "<p>联系地址：" + escapeHtml(factory != null ? factory.getAddress() : "") + "</p>"
                + "<p>联系人：" + escapeHtml(factory != null ? factory.getContact() : "") + "</p>"
                + "<p>联系电话：" + escapeHtml(factory != null ? factory.getPhone() : "") + "</p>"
                + "</td>"
                + "</tr></tbody></table>";
        content = content.replace("{合同信息}", topInfoHtml);

        // 签名区占位替换为表格
        String signatureHtml = "<table style=\"width:100%; margin-top:40px; border:none; border-collapse:collapse;\"><tbody><tr>"
                + "<td style=\"width:50%; border:none; padding:4px 8px; text-align:left; vertical-align:top;\"><p>甲方：" + escapeHtml(companyName) + "</p><p>法人代表或授权代表：</p><p>&nbsp;</p><p>日期：" + dateStr + "</p></td>"
                + "<td style=\"width:50%; border:none; padding:4px 8px; text-align:left; vertical-align:top;\"><p>乙方：" + escapeHtml(factoryName) + "</p><p>法人代表或授权代表：</p><p>&nbsp;</p><p>日期：</p></td>"
                + "</tr></tbody></table>";
        content = content.replace("{签名区}", signatureHtml);

        // 产品表格
        List<OutsourceOrderProduct> products = orderService.getProducts(order.getId());
        StringBuilder productTable = new StringBuilder();
        productTable.append("<table border='1' cellpadding='8' cellspacing='0' style='width:100%;border-collapse:collapse;text-align:center;'>");
        productTable.append("<tr style='background:#f2f2f2'><th>序号</th><th>名称</th><th>加工单价(元)</th><th>加工数量(件)</th><th>合计(元)</th><th>备注</th></tr>");
        BigDecimal total = BigDecimal.ZERO;
        int i = 1;
        for (OutsourceOrderProduct p : products) {
            BigDecimal amount = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
            total = total.add(amount);
            productTable.append("<tr>")
                    .append("<td>").append(i++).append("</td>")
                    .append("<td>").append(escapeHtml(p.getProductName())).append("</td>")
                    .append("<td>").append(p.getUnitPrice()).append("</td>")
                    .append("<td>").append(p.getQuantity()).append("</td>")
                    .append("<td>").append(amount).append("</td>")
                    .append("<td>").append(escapeHtml(p.getRemark())).append("</td>")
                    .append("</tr>");
        }
        productTable.append("<tr><td colspan='4' style='text-align:right;font-weight:bold'>合计：</td><td style='font-weight:bold'>").append(total).append("</td><td></td></tr>");
        productTable.append("</table>");
        content = content.replace("{产品表格}", productTable.toString());

        // 物料表格
        StringBuilder materialTable = new StringBuilder();
        materialTable.append("<table border='1' cellpadding='8' cellspacing='0' style='width:100%;border-collapse:collapse;text-align:center;'>");
        materialTable.append("<tr style='background:#f2f2f2'><th>序号</th><th>名称</th><th>数量</th><th>损耗率(%)</th></tr>");
        int j = 1;
        for (OutsourceOrderProduct p : products) {
            List<OutsourceOrderMaterial> materials = orderService.getMaterials(p.getId());
            for (OutsourceOrderMaterial m : materials) {
                materialTable.append("<tr>")
                        .append("<td>").append(j++).append("</td>")
                        .append("<td>").append(escapeHtml(m.getMaterialName())).append("</td>")
                        .append("<td>").append(m.getDemandQuantity()).append("</td>")
                        .append("<td>").append(m.getLossRate() != null ? m.getLossRate() : "").append("</td>")
                        .append("</tr>");
            }
        }
        materialTable.append("</table>");
        content = content.replace("{物料表格}", materialTable.toString());

        return content;
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private byte[] generatePdf(String html) throws IOException {
        Path dir = Files.createTempDirectory("contract-");
        String id = UUID.randomUUID().toString();
        Path htmlFile = dir.resolve(id + ".html");
        Path pdfFile = dir.resolve(id + ".pdf");

        // 包裹完整 HTML
        String fullHtml = "<!DOCTYPE html><html><head><meta charset='utf-8'><style>body{font-family:SimSun,serif;font-size:14px;line-height:1.6;padding:40px}h1{text-align:center}table th,table td{border:1px solid #333}</style></head><body>"
                + html + "</body></html>";
        Files.writeString(htmlFile, fullHtml, StandardCharsets.UTF_8);

        ProcessBuilder pb = new ProcessBuilder(
                "python3", "-m", "weasyprint",
                htmlFile.toAbsolutePath().toString(),
                pdfFile.toAbsolutePath().toString()
        );
        pb.inheritIO();
        Process process = pb.start();
        try {
            int exit = process.waitFor();
            if (exit != 0) throw new BusinessException("PDF生成失败，WeasyPrint返回" + exit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("PDF生成被中断");
        }
        byte[] bytes = Files.readAllBytes(pdfFile);
        // 清理
        try { Files.deleteIfExists(htmlFile); Files.deleteIfExists(pdfFile); Files.deleteIfExists(dir); } catch (Exception ignore) {}
        return bytes;
    }
}
