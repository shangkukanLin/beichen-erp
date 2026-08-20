package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.common.ContractDocxBuilder;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    @GetMapping("/list") public R<List<ContractTemplate>> list(@RequestParam(required = false) String templateType) { return R.ok(templateService.list(templateType)); }
    @GetMapping("/{id}") public R<ContractTemplate> getById(@PathVariable Long id) { return R.ok(templateService.getById(id)); }
    @PostMapping public R<Void> create(@RequestBody ContractTemplate t) { templateService.save(t); return R.ok(); }
    @PutMapping("/{id}") public R<Void> update(@PathVariable Long id, @RequestBody ContractTemplate t) { t.setId(id); templateService.update(t); return R.ok(); }
    @DeleteMapping("/{id}") public R<Void> delete(@PathVariable Long id) { templateService.delete(id); return R.ok(); }
    @PutMapping("/{id}/default") public R<Void> setDefault(@PathVariable Long id) { templateService.setDefault(id); return R.ok(); }

    @GetMapping("/export/{orderId}")
    public ResponseEntity<byte[]> exportDocx(@PathVariable Long orderId,
            @RequestParam(required = false) Long templateId) {
        OutsourceOrder order = orderService.getById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");
        ContractTemplate template = templateId != null ? templateService.getById(templateId) : templateService.getDefault("加工合同");
        if (template == null || template.getContent() == null) throw new BusinessException("未找到合同模板");
        byte[] docxBytes = buildProcessingDocx(order, template);
        return docxResponse("委外加工合同-" + order.getCode() + ".docx", docxBytes);
    }

    // ======================== 物料订单合同导出 ========================
    @GetMapping("/export-material-order/{orderId}")
    public ResponseEntity<byte[]> exportMaterialOrderDocx(@PathVariable Long orderId,
            @RequestParam(required = false) Long templateId) {
        MaterialOrder order = materialOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("物料订单不存在");
        ContractTemplate template = templateId != null ? templateService.getById(templateId) : templateService.getDefault("采购合同");
        if (template == null || template.getContent() == null) throw new BusinessException("未找到采购合同模板");
        byte[] docxBytes = buildPurchaseDocx(order, template);
        return docxResponse("物料采购合同-" + order.getCode() + ".docx", docxBytes);
    }

    private ResponseEntity<byte[]> docxResponse(String fileName, byte[] bytes) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(bytes);
    }

    /** 当前登录公司（甲方），回退 1L 兜底兼容超管 */
    private Company currentCompany() {
        Long cid = CompanyContext.get();
        Company co = companyMapper.selectById(cid != null && cid > 0 ? cid : 1L);
        return co;
    }

    // ======================== 加工合同 DOCX ========================
    private byte[] buildProcessingDocx(OutsourceOrder order, ContractTemplate template) {
        try {
            Company co = currentCompany();
            String cn = co != null ? co.getCompanyName() : "甲方";
            ContractDocxBuilder.PartyA partyA = new ContractDocxBuilder.PartyA(
                    cn, co != null ? co.getContactPerson() : "", co != null ? co.getPhone() : "", co != null ? co.getAddress() : "");

            Supplier fac = order.getFactoryId() != null ? supplierMapper.selectById(order.getFactoryId()) : null;
            String fn = fac != null ? fac.getName() : "";
            ContractDocxBuilder.PartyB partyB = new ContractDocxBuilder.PartyB(
                    fn, fac != null ? fac.getContact() : "", fac != null ? fac.getPhone() : "", fac != null ? fac.getAddress() : "");

            List<OutsourceOrderProduct> pros = orderService.getProducts(order.getId());
            List<ContractDocxBuilder.ProductRow> productRows = new ArrayList<>();
            if (pros != null) for (OutsourceOrderProduct p : pros) {
                productRows.add(new ContractDocxBuilder.ProductRow(
                        p.getProductName(), fmt(p.getUnitPrice()), fmt(p.getQuantity()), fmt(p.getAmount()), p.getRemark()));
            }
            List<OutsourceOrderMaterial> mats = new ArrayList<>();
            if (pros != null) for (OutsourceOrderProduct p : pros) {
                List<OutsourceOrderMaterial> m = orderService.getMaterials(p.getId());
                if (m != null) mats.addAll(m);
            }
            List<ContractDocxBuilder.MaterialRow> materialRows = new ArrayList<>();
            for (OutsourceOrderMaterial m : mats) {
                materialRows.add(new ContractDocxBuilder.MaterialRow(
                        getMaterialNameById(m.getMaterialId()), fmt(m.getDemandQuantity()), fmt(m.getLossRate()), m.getRemark()));
            }

            return ContractDocxBuilder.build("委外加工合同", partyA, partyB,
                    template.getContent(), productRows, materialRows, null, null);
        } catch (Exception e) {
            log.error("加工合同DOC生成失败: {}", e.getMessage(), e);
            throw new BusinessException("合同生成失败：" + e.getMessage());
        }
    }

    // ======================== 采购合同 DOCX ========================
    private byte[] buildPurchaseDocx(MaterialOrder order, ContractTemplate template) {
        try {
            Company co = currentCompany();
            String cn = co != null ? co.getCompanyName() : "甲方";
            ContractDocxBuilder.PartyA partyA = new ContractDocxBuilder.PartyA(
                    cn, co != null ? co.getContactPerson() : "", co != null ? co.getPhone() : "", co != null ? co.getAddress() : "");

            Supplier supp = order.getSupplierId() != null ? supplierMapper.selectById(order.getSupplierId()) : null;
            String sn = supp != null ? supp.getName() : "";
            ContractDocxBuilder.PartyB partyB = new ContractDocxBuilder.PartyB(
                    sn, supp != null ? supp.getContact() : "", supp != null ? supp.getPhone() : "", supp != null ? supp.getAddress() : "");

            List<MaterialOrderItem> items = materialOrderItemMapper.selectList(
                    new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, order.getId()));

            List<ContractDocxBuilder.OrderItemRow> orderItemRows = new ArrayList<>();
            if (items != null) for (MaterialOrderItem it : items) {
                orderItemRows.add(new ContractDocxBuilder.OrderItemRow(
                        getMaterialNameById(it.getMaterialId()), it.getUnit(), fmt(it.getOrderQuantity()),
                        fmt(it.getUnitPrice()), fmt(it.getAmount()), it.getRemark()));
            }

            List<ContractDocxBuilder.ComponentRow> componentRows = new ArrayList<>();
            if (items != null) for (MaterialOrderItem it : items) {
                if (it.getMaterialId() == null) continue;
                List<OutsourceMaterialComponent> comps = componentMapper.selectList(
                        new LambdaQueryWrapper<OutsourceMaterialComponent>().eq(OutsourceMaterialComponent::getParentMaterialId, it.getMaterialId()));
                if (comps == null || comps.isEmpty()) continue;
                List<String[]> children = new ArrayList<>();
                for (OutsourceMaterialComponent c : comps) {
                    BigDecimal perQty = c.getQuantity() != null ? c.getQuantity() : BigDecimal.ONE;
                    BigDecimal totalQty = perQty.multiply(it.getOrderQuantity() != null ? it.getOrderQuantity() : BigDecimal.ONE);
                    String childName = "", childUnit = "";
                    OutsourceMaterial child = materialMapper.selectById(c.getChildMaterialId());
                    if (child != null) { childName = child.getMaterialName() != null ? child.getMaterialName() : ""; childUnit = child.getUnit() != null ? child.getUnit() : ""; }
                    children.add(new String[]{childName, childUnit, fmt(perQty), fmt(totalQty), fmt(c.getLossRate())});
                }
                componentRows.add(new ContractDocxBuilder.ComponentRow(
                        getMaterialNameById(it.getMaterialId()), fmt(it.getOrderQuantity()), it.getUnit(), children));
            }

            return ContractDocxBuilder.build("物料采购合同", partyA, partyB,
                    template.getContent(), null, null, orderItemRows, componentRows);
        } catch (Exception e) {
            log.error("采购合同DOC生成失败: {}", e.getMessage(), e);
            throw new BusinessException("合同生成失败：" + e.getMessage());
        }
    }

    /** 根据委外物料ID查询名称，用于展示回填（ID关联查询替代冗余name字段） */
    private String getMaterialNameById(Long materialId) {
        if (materialId == null) return "";
        OutsourceMaterial m = materialMapper.selectById(materialId);
        return m != null ? m.getMaterialName() : "";
    }

    private String fmt(BigDecimal v) {
        if (v == null) return "";
        return v.stripTrailingZeros().toPlainString();
    }
}
