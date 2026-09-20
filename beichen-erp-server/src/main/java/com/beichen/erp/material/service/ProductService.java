package com.beichen.erp.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.material.common.ProductStatus;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductService extends ServiceImpl<ProductMapper, Product> {

    /** SKU 流水号位数：SKU- 前缀后补 6 位，如 SKU-000001 */
    private static final String SKU_NUM_FORMAT = "%06d";

    /** 生成 SKU 时的最大顺延次数（数据库唯一索引之外的兜底，正常不会触发） */
    private static final int SKU_MAX_RETRY = 100;

    /** SKU 最大长度（与 product.sku VARCHAR(64) 对齐，超出直接拒绝而不是靠 DB 报错） */
    private static final int SKU_MAX_LEN = 64;

    public Page<Product> page(String keyword, String specType, Long brandId, ProductStatus status,
                              String sku, int pageNum, int pageSize) {
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 关键字同时匹配产品名称与 SKU，便于直接粘贴 SKU 检索
            w.and(x -> x.like(Product::getName, kw).or().like(Product::getSku, kw));
        }
        if (StringUtils.hasText(sku)) {
            w.eq(Product::getSku, sku.trim());
        }
        if (StringUtils.hasText(specType)) {
            w.eq(Product::getSpecType, specType);
        }
        if (brandId != null) {
            w.eq(Product::getBrandId, brandId);
        }
        if (status != null) {
            w.eq(Product::getStatus, status);
        } else {
            // 列表默认隐藏已停用品，避免停用品仍出现在查询列表
            w.ne(Product::getStatus, ProductStatus.DISCONTINUED);
        }
        w.orderByDesc(Product::getId);
        return this.page(new Page<>(pageNum, pageSize), w);
    }

    /**
     * 批量给单据明细补齐 SKU（明细实体上标注为 {@code @TableField(exist = false)} 的展示字段）。
     * <p>按 productId 一次性查产品再回填，避免循环内 selectById（N+1）。</p>
     *
     * @param items          明细列表
     * @param productIdGetter 取产品ID的方法引用，如 {@code SaleOrderItem::getProductId}
     * @param skuSetter       写回 SKU 的方法引用，如 {@code SaleOrderItem::setSku}
     */
    public <T> void fillSku(List<T> items, Function<T, Long> productIdGetter, BiConsumer<T, String> skuSetter) {
        if (items == null || items.isEmpty()) return;
        Set<Long> pids = items.stream().map(productIdGetter).filter(Objects::nonNull).collect(Collectors.toSet());
        if (pids.isEmpty()) return;
        Map<Long, String> skuMap = this.list(new LambdaQueryWrapper<Product>().in(Product::getId, pids))
                .stream()
                .collect(Collectors.toMap(Product::getId, p -> p.getSku() != null ? p.getSku() : "", (a, b) -> a));
        for (T it : items) {
            Long pid = productIdGetter.apply(it);
            if (pid != null) skuSetter.accept(it, skuMap.getOrDefault(pid, ""));
        }
    }

    /** 停用成品（逻辑删除替代物理删除，避免库存/销售/采购等关联表变成孤儿数据） */
    public void discontinue(Long id) {
        LambdaUpdateWrapper<Product> u = new LambdaUpdateWrapper<>();
        u.eq(Product::getId, id).set(Product::getStatus, ProductStatus.DISCONTINUED);
        this.update(u);
    }

    // ==================== SKU（产品级唯一编码） ====================

    /**
     * 新增产品：**传入 SKU 则采用**（2026-09-21 用户要求：前端预填自动生成的编码、允许用户改），
     * 传空则按公司内最大流水自动生成（SKU-000001）。
     */
    @Override
    public boolean save(Product entity) {
        String incoming = entity.getSku() == null ? "" : entity.getSku().trim();
        if (!StringUtils.hasText(incoming)) {
            entity.setSku(nextSku());
        } else {
            assertSkuAvailable(incoming, null);
            entity.setSku(incoming);
        }
        return super.save(entity);
    }

    /**
     * 修改产品：**允许修改 SKU**（2026-09-21 用户要求；前端在值发生变化时会二次确认）。
     * <p>传空 ⇒ 保持库中现值（历史脏数据才补生成），避免"编辑时把 SKU 清空"；
     * 传了非空值 ⇒ 校验公司内唯一（排除自身）后采用。</p>
     * <p>⚠️ 历史单据明细里的 SKU 是**冗余快照**（{@code inventory_stock_take_item.sku} /
     * {@code inventory_stock_loss_item.sku}），本方法**不回改**这些快照。</p>
     */
    @Override
    public boolean updateById(Product entity) {
        Product old = entity.getId() != null ? this.getById(entity.getId()) : null;
        String incoming = entity.getSku() == null ? "" : entity.getSku().trim();
        if (!StringUtils.hasText(incoming)) {
            entity.setSku(old != null && StringUtils.hasText(old.getSku()) ? old.getSku() : nextSku());
        } else if (old != null && incoming.equals(old.getSku())) {
            // 未变化：直接放行，跳过唯一校验
            entity.setSku(incoming);
        } else {
            assertSkuAvailable(incoming, entity.getId());
            entity.setSku(incoming);
        }
        return super.updateById(entity);
    }

    /**
     * 预览下一个可用 SKU（**只读**，供前端「新增产品」预填；复用与自动生成完全相同的取号逻辑）。
     * <p>仅作建议值：并发下两个请求可能拿到同一个，最终由唯一键
     * {@code uk_company_sku(company_id, sku)} 拦截并由 {@link #assertSkuAvailable} 明确报错。</p>
     */
    public String peekNextSku() {
        return nextSku();
    }

    /** 校验 SKU 可用：非空、长度合法、公司内唯一（{@code excludeId} 用于编辑时排除自身） */
    private void assertSkuAvailable(String sku, Long excludeId) {
        if (sku.length() > SKU_MAX_LEN) {
            throw new BusinessException("SKU 长度不能超过 " + SKU_MAX_LEN + " 个字符");
        }
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<Product>().eq(Product::getSku, sku);
        if (excludeId != null) w.ne(Product::getId, excludeId);
        if (count(w) > 0) {
            throw new BusinessException("SKU 已存在：" + sku + "，请更换");
        }
    }

    /** 取下一个可用 SKU：按公司内已有最大流水 +1，若被占用则顺延（并发下由唯一索引兜底） */
    private String nextSku() {
        int seq = nextSeq();
        for (int i = 0; i < SKU_MAX_RETRY; i++) {
            String candidate = BillPrefix.PRODUCT_SKU + String.format(SKU_NUM_FORMAT, seq + i);
            if (count(new LambdaQueryWrapper<Product>().eq(Product::getSku, candidate)) == 0) {
                return candidate;
            }
        }
        throw new BusinessException("SKU 生成失败，请联系管理员");
    }

    /** 公司内已使用的最大 SKU 流水号 +1（无记录则返回 1） */
    private int nextSeq() {
        Long cid = CompanyContext.get();
        List<Product> max = list(new LambdaQueryWrapper<Product>()
                .eq(cid != null, Product::getCompanyId, cid)
                .likeRight(Product::getSku, BillPrefix.PRODUCT_SKU)
                .orderByDesc(Product::getSku)
                .last("LIMIT 1"));
        if (max.isEmpty() || !StringUtils.hasText(max.get(0).getSku())) return 1;
        String num = max.get(0).getSku().substring(BillPrefix.PRODUCT_SKU.length());
        try {
            return Integer.parseInt(num) + 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
