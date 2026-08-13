package com.beichen.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.inventory.entity.InventoryProductReclassify;
import com.beichen.erp.inventory.entity.InventoryProductReclassifyItem;
import com.beichen.erp.inventory.mapper.InventoryProductReclassifyMapper;
import com.beichen.erp.inventory.mapper.InventoryProductReclassifyItemMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.inventory.service.ReclassifyService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReclassifyServiceImpl implements ReclassifyService {

    private final InventoryProductReclassifyMapper rcMapper;
    private final InventoryProductReclassifyItemMapper itemMapper;
    private final WarehouseStockService stockService;
    private final ProductMapper productMapper;

    @Override
    public Page<Map<String, Object>> page(String status, Long warehouseId, int pageNum, int pageSize) {
        LambdaQueryWrapper<InventoryProductReclassify> w = new LambdaQueryWrapper<InventoryProductReclassify>()
                .eq(status != null && !status.isBlank(), InventoryProductReclassify::getStatus, status)
                .eq(warehouseId != null, InventoryProductReclassify::getWarehouseId, warehouseId)
                .orderByDesc(InventoryProductReclassify::getId);
        Page<InventoryProductReclassify> raw = rcMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            m.put("warehouseId", o.getWarehouseId());
            m.put("reclassifyDate", o.getReclassifyDate()); m.put("status", o.getStatus());
            m.put("remark", o.getRemark()); m.put("createTime", o.getCreateTime());
            return m;
        }).toList());
        return res;
    }

    @Override
    public InventoryProductReclassify getById(Long id) { return rcMapper.selectById(id); }

    @Override
    public List<InventoryProductReclassifyItem> getItems(Long reclassifyId) {
        return itemMapper.selectList(new LambdaQueryWrapper<InventoryProductReclassifyItem>()
                .eq(InventoryProductReclassifyItem::getReclassifyId, reclassifyId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(InventoryProductReclassify rc, List<InventoryProductReclassifyItem> items) {
        if (rc.getWarehouseId() == null) throw new BusinessException("仓库不能为空");
        rc.setCode(gen("PC-"));
        rc.setStatus(DocStatus.DRAFT.name());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) rc.setCompanyId(cid);
        rcMapper.insert(rc);
        for (InventoryProductReclassifyItem it : items) {
            if (it.getFromQuality() == null || it.getToQuality() == null)
                throw new BusinessException("原品质和目标品质不能为空");
            if (it.getFromQuality().equals(it.getToQuality()))
                throw new BusinessException("原品质和目标品质不能相同");
            it.setId(null);
            it.setReclassifyId(rc.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(InventoryProductReclassify rc, List<InventoryProductReclassifyItem> items) {
        InventoryProductReclassify old = rcMapper.selectById(rc.getId());
        if (old == null) throw new BusinessException("品质重分类单不存在");
        if (DocStatus.AUDITED.name().equals(old.getStatus())) throw new BusinessException("已审核的单据不可编辑");

        // 草稿状态更新：直接删旧明细 + 插新
        rc.setCode(old.getCode()); rc.setStatus(DocStatus.DRAFT.name());
        rcMapper.updateById(rc);

        itemMapper.delete(new LambdaQueryWrapper<InventoryProductReclassifyItem>()
                .eq(InventoryProductReclassifyItem::getReclassifyId, rc.getId()));
        Long cid = CompanyContext.get();
        for (InventoryProductReclassifyItem it : items) {
            it.setId(null); it.setReclassifyId(rc.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            if (it.getFromQuality().equals(it.getToQuality()))
                throw new BusinessException("原品质和目标品质不能相同");
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InventoryProductReclassify rc = rcMapper.selectById(id);
        if (rc == null) throw new BusinessException("品质重分类单不存在");
        if (!DocStatus.DRAFT.name().equals(rc.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<InventoryProductReclassifyItem> items = getItems(id);
        if (items.isEmpty()) throw new BusinessException("重分类明细不能为空");

        // 执行库存变更：from_quality 扣减，to_quality 增加
        for (InventoryProductReclassifyItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product prod = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            // 扣减原品质
            stockService.changeStock(rc.getWarehouseId(), prod != null ? prod.getName() : "",
                    it.getQuantity().negate(), StockChangeType.RECLASSIFY_OUT, rc.getCode(),
                    RelatedBillType.PRODUCT_RECLASSIFY, it.getProductId(),
                    prod != null ? prod.getSpec() : "", rc.getId(), it.getFromQuality());
            // 增加目标品质
            stockService.changeStock(rc.getWarehouseId(), prod != null ? prod.getName() : "",
                    it.getQuantity(), StockChangeType.RECLASSIFY_IN, rc.getCode(),
                    RelatedBillType.PRODUCT_RECLASSIFY, it.getProductId(),
                    prod != null ? prod.getSpec() : "", rc.getId(), it.getToQuality());
        }
        InventoryProductReclassify u = new InventoryProductReclassify(); u.setId(id); u.setStatus(DocStatus.AUDITED.name());
        rcMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        // 注意：品质重分类单无独立 unAudit，cancel 在此承担"反审核"职责——
        // 校验已审核后执行逆向库存回滚（恢复原品质、冲回目标品质），再将状态置 CANCELLED。
        // 与 WarehouseMove.unAudit 语义一致，仅方法命名不同（前端已按 cancel 调用，故不改名）。
        InventoryProductReclassify rc = rcMapper.selectById(id);
        if (rc == null) throw new BusinessException("品质重分类单不存在");
        if (!DocStatus.AUDITED.name().equals(rc.getStatus())) throw new BusinessException("只有已审核状态可取消");
        List<InventoryProductReclassifyItem> items = getItems(id);

        // 逆向操作：恢复 from_quality，冲回 to_quality
        for (InventoryProductReclassifyItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product prod = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            // 恢复原品质
            stockService.changeStock(rc.getWarehouseId(), prod != null ? prod.getName() : "",
                    it.getQuantity(), StockChangeType.CANCEL_RECLASSIFY_OUT, rc.getCode(),
                    RelatedBillType.PRODUCT_RECLASSIFY, it.getProductId(),
                    prod != null ? prod.getSpec() : "", rc.getId(), it.getFromQuality());
            // 冲回目标品质
            stockService.changeStock(rc.getWarehouseId(), prod != null ? prod.getName() : "",
                    it.getQuantity().negate(), StockChangeType.CANCEL_RECLASSIFY_IN, rc.getCode(),
                    RelatedBillType.PRODUCT_RECLASSIFY, it.getProductId(),
                    prod != null ? prod.getSpec() : "", rc.getId(), it.getToQuality());
        }
        InventoryProductReclassify u = new InventoryProductReclassify(); u.setId(id); u.setStatus(DocStatus.CANCELLED.name());
        rcMapper.updateById(u);
    }

    private String gen(String prefix) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        LambdaQueryWrapper<InventoryProductReclassify> w = new LambdaQueryWrapper<InventoryProductReclassify>()
                .likeRight(InventoryProductReclassify::getCode, pat).orderByDesc(InventoryProductReclassify::getCode).last("LIMIT 1");
        InventoryProductReclassify last = rcMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return prefix + d + String.format("%03d", seq);
    }
}
