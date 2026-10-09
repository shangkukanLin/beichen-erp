package com.beichen.erp.material.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.material.common.ProductStatus;
import com.beichen.erp.material.common.ProductSpec;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.material.service.ProductService;
import com.beichen.erp.dev.service.ProjectProductSyncService;
import com.beichen.erp.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * 成品管理（product 表）
 */
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService service;
    private final ProjectProductSyncService projectProductSyncService;

    /** 分页查询（支持关键字(名称/SKU)/SKU精确/品牌/规格/状态筛选） */
    @GetMapping("/page")
    public R<Page<Product>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String specType,
            @RequestParam(required = false) String status) {
        ProductStatus ps = status != null ? ProductStatus.fromValue(status) : null;
        return R.ok(service.page(keyword, specType, brandId, ps, sku, pageNum, pageSize));
    }

    /**
     * 预览下一个可用 SKU（只读），供「新增产品」预填；允许用户修改后再提交。
     * <p>2026-09-21 用户要求：SKU 先默认生成、可以修改。</p>
     * <p>2026-09-21（供货SKU）：带 {@code supplierId} 时按该供货商的「供货SKU」作前缀取号
     * （{@code ABC ⇒ ABC-000001}）；不带/该供货商未配 ⇒ 默认 {@code SKU-000001}。
     * 前端在产品页切换「供货商」时会重新调用本接口刷新预填值。</p>
     */
    @GetMapping("/next-sku")
    public R<String> nextSku(@RequestParam(required = false) Long supplierId,
                             @RequestParam(required = false) String prefix) {
        // 2026-09-21（立项「产品SKU」）：显式前缀优先 —— 立项页用它预填 NS- 打头的 SKU。
        // 前缀非法时回落到默认 SKU-（只影响预览值；真正落库仍由 save 的唯一校验 + 唯一索引兜底）。
        if (prefix != null && !prefix.isBlank()) {
            return R.ok(service.peekNextSkuByPrefix(prefix));
        }
        return R.ok(service.peekNextSku(supplierId));
    }

    /** 单条查询 */
    @GetMapping("/{id}")
    public R<Product> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    /** 新增 */
    @PostMapping
    public R<Void> add(@Valid @RequestBody Product product) {
        // 规格必填（2026-09-21 用户要求）。⚠️ 放在 Controller 而非 Service：
        // 研发立项会自动建产品（ProjectProductSyncServiceImpl 走 ProductService.save），
        // 那是内部路径、规格尚未确定，不能因必填校验把立项流程打断。
        product.setSpecType(ProductSpec.requireValid(product.getSpecType()));
        service.save(product);
        return R.ok();
    }

    /** 修改 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        // 规格必填（理由同上）
        product.setSpecType(ProductSpec.requireValid(product.getSpecType()));
        // 若产品名称变更，同步更新关联项目的总成名称
        if (product.getName() != null) {
            Product old = service.getById(id);
            if (old != null && (old.getName() == null || !old.getName().equals(product.getName()))) {
                // 2026-09-21：方法随「总成名称 → 产品名称」改名（原 syncAssemblyNameFromProduct）
                projectProductSyncService.syncProductNameToProject(id, product.getName());
            }
        }
        service.updateById(product);
        // 2026-09-21（供货商字段）：MyBatis-Plus 的 updateById 会跳过 null 字段 ⇒ 把供货商改回「未选」时
        // 落不了库，这里显式补一次置空。⚠️ 只影响 supplier_id 一列；本接口的前端调用方只有产品管理页
        // （material/detail.vue），其提交体恒带 supplierId 键，故 null 即"用户清空"的语义。
        if (product.getSupplierId() == null) {
            service.clearSupplier(id);
        }
        return R.ok();
    }

    /**
     * 只改「安全库存」（2026-10-09 用户需求：成品库存详情列表里点安全库存直接弹框改）。
     *
     * <p><b>为什么单开端点</b>：{@link #update} 是**整实体**更新、且带**规格必填**校验
     * （{@code ProductSpec.requireValid}）⇒ 只发 {@code {safetyStock}} 会被它拦下 ✗。
     * 本端点 **只写 {@code product.safety_stock} 一列**（MyBatis-Plus 的 updateById 会跳过 null 字段 ⇒
     * 传部分实体是安全的 ✓）。</p>
     *
     * <p><b>粒度提醒</b>：安全库存是**产品级**（一个产品一份，所有仓库共用）—— 这是表结构决定的
     * （`warehouse_stock` 无该列），不是本端点引入的；页面弹框里已写明这一点 ✓。</p>
     *
     * <p>校验：非空、非负、**必须是整数**（列是 DECIMAL(18,0)，产品管理表单也是 :precision=0 ⇒ 口径一致）。
     * 清空语义 = 填 0（0 与 null 在低库存判定里等价：都表示"未设置"）。</p>
     */
    @PutMapping("/{id}/safety-stock")
    public R<Void> updateSafetyStock(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Object raw = body == null ? null : body.get("safetyStock");
        if (raw == null || String.valueOf(raw).isBlank())
            throw new BusinessException("安全库存不能为空（如需清空请填 0）");
        BigDecimal val;
        try {
            val = new BigDecimal(String.valueOf(raw).trim());
        } catch (Exception e) {
            throw new BusinessException("安全库存格式不正确：" + raw);
        }
        if (val.compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessException("安全库存不能为负数：当前 " + val);
        if (val.stripTrailingZeros().scale() > 0)
            throw new BusinessException("安全库存必须是整数：当前 " + val);
        if (service.getById(id) == null)
            throw new BusinessException("产品不存在");
        Product p = new Product();
        p.setId(id);
        p.setSafetyStock(val);
        service.updateById(p);
        return R.ok();
    }

    /** 删除（物理删除改为停用，走 status 生命周期，避免关联库存/单据成孤儿数据） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.discontinue(id);
        return R.ok();
    }

    /** 获取品质等级 **code 列表**（2026-09-14：「接口只回 code」，前端已改为本地 `ProductQualityTypeLabel` 生成下拉） */
    @GetMapping("/quality-types")
    public R<List<String>> getQualityTypes() {
        List<String> list = new ArrayList<>();
        for (ProductQualityType t : ProductQualityType.values()) {
            list.add(t.name());
        }
        return R.ok(list);
    }
}
