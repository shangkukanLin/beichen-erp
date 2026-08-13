package com.beichen.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.inventory.entity.InventoryStockReclass;
import com.beichen.erp.inventory.entity.InventoryStockReclassItem;
import com.beichen.erp.inventory.mapper.InventoryStockReclassItemMapper;
import com.beichen.erp.inventory.mapper.InventoryStockReclassMapper;
import com.beichen.erp.inventory.service.InventoryStockReclassService;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 品质重分类单业务实现 */
@Service
@RequiredArgsConstructor
public class InventoryStockReclassServiceImpl extends ServiceImpl<InventoryStockReclassMapper, InventoryStockReclass>
        implements InventoryStockReclassService {

    private final InventoryStockReclassItemMapper itemMapper;
    private final WarehouseStockService stockService;

    @Override
    public IPage<InventoryStockReclass> pageList(Map<String, Object> params) {
        Long companyId = (Long) params.get("companyId");
        Long warehouseId = (Long) params.get("warehouseId");
        String status = (String) params.get("status");
        String code = (String) params.get("code");
        int pageNum = params.get("pageNum") == null ? (params.get("page") == null ? 1 : Integer.parseInt(params.get("page").toString())) : Integer.parseInt(params.get("pageNum").toString());
        int pageSize = params.get("pageSize") == null ? (params.get("size") == null ? 10 : Integer.parseInt(params.get("size").toString())) : Integer.parseInt(params.get("pageSize").toString());

        var wrapper = Wrappers.<InventoryStockReclass>lambdaQuery()
                .eq(companyId != null, InventoryStockReclass::getCompanyId, companyId)
                .eq(warehouseId != null, InventoryStockReclass::getWarehouseId, warehouseId)
                .eq(status != null && !status.isEmpty(), InventoryStockReclass::getStatus, status)
                .like(code != null && !code.isEmpty(), InventoryStockReclass::getCode, code)
                .orderByDesc(InventoryStockReclass::getId);
        return baseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(InventoryStockReclass header, List<InventoryStockReclassItem> items) {
        Long companyId = header.getCompanyId();
        if (header.getReclassifyDate() == null) header.setReclassifyDate(LocalDate.now());
        if (header.getStatus() == null || header.getStatus().isEmpty()) header.setStatus("DRAFT");
        if (header.getId() == null) {
            if (header.getCode() == null || header.getCode().isEmpty())
                header.setCode("FL-" + LocalDate.now() + "-" + System.currentTimeMillis() % 100000);
            baseMapper.insert(header);
        } else {
            baseMapper.updateById(header);
            itemMapper.delete(Wrappers.<InventoryStockReclassItem>lambdaQuery()
                    .eq(InventoryStockReclassItem::getReclassId, header.getId()));
        }
        if (items != null) {
            for (InventoryStockReclassItem it : items) {
                it.setReclassId(header.getId());
                it.setCompanyId(companyId);
                itemMapper.insert(it);
            }
        }
        return header.getId();
    }

    @Override
    public List<InventoryStockReclassItem> loadItems(Long id) {
        return itemMapper.selectList(Wrappers.<InventoryStockReclassItem>lambdaQuery()
                .eq(InventoryStockReclassItem::getReclassId, id)
                .orderByAsc(InventoryStockReclassItem::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(Long id) {
        InventoryStockReclass header = baseMapper.selectById(id);
        if (header == null) throw new BusinessException("重分类单不存在");
        if (!"DRAFT".equals(header.getStatus())) throw new BusinessException("仅草稿可审核");

        List<InventoryStockReclassItem> items = loadItems(id);
        if (items.isEmpty()) throw new BusinessException("重分类单无明细");

        for (InventoryStockReclassItem it : items) {
            // 入参完整性校验，避免 NPE
            if (it.getFromQuality() == null || it.getToQuality() == null)
                throw new BusinessException("源等级与目标等级不能为空：" + it.getProductName());
            if (it.getQuantity() == null)
                throw new BusinessException("重分类数量不能为空：" + it.getProductName());
            if (it.getFromQuality().equals(it.getToQuality()))
                throw new BusinessException("源等级与目标等级不能相同：" + it.getProductName());
            // 源等级扣减
            stockService.changeStock(header.getWarehouseId(), it.getProductName(), it.getQuantity().negate(),
                    StockChangeType.RECLASSIFY_OUT, header.getCode(), RelatedBillType.PRODUCT_RECLASSIFY,
                    it.getProductId(), null, it.getId(), it.getFromQuality());
            // 目标等级增加
            stockService.changeStock(header.getWarehouseId(), it.getProductName(), it.getQuantity(),
                    StockChangeType.RECLASSIFY_IN, header.getCode(), RelatedBillType.PRODUCT_RECLASSIFY,
                    it.getProductId(), null, it.getId(), it.getToQuality());
        }
        header.setStatus("AUDITED");
        header.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(header);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unreview(Long id) {
        InventoryStockReclass header = baseMapper.selectById(id);
        if (header == null) throw new BusinessException("重分类单不存在");
        if (!"AUDITED".equals(header.getStatus())) throw new BusinessException("仅已审核可反审核");

        List<InventoryStockReclassItem> items = loadItems(id);
        for (InventoryStockReclassItem it : items) {
            // 入参完整性校验，避免 NPE
            if (it.getFromQuality() == null || it.getToQuality() == null)
                throw new BusinessException("源等级与目标等级不能为空：" + it.getProductName());
            if (it.getQuantity() == null)
                throw new BusinessException("重分类数量不能为空：" + it.getProductName());
            // 反向回滚：目标等级扣减（CANCEL_RECLASSIFY_IN）、源等级增加（CANCEL_RECLASSIFY_OUT）
            // 与 ReclassifyServiceImpl.cancel 的常量语义保持一致，确保流水报表能区分正逆向
            stockService.changeStock(header.getWarehouseId(), it.getProductName(), it.getQuantity().negate(),
                    StockChangeType.CANCEL_RECLASSIFY_IN, header.getCode(), RelatedBillType.PRODUCT_RECLASSIFY,
                    it.getProductId(), null, it.getId(), it.getToQuality());
            stockService.changeStock(header.getWarehouseId(), it.getProductName(), it.getQuantity(),
                    StockChangeType.CANCEL_RECLASSIFY_OUT, header.getCode(), RelatedBillType.PRODUCT_RECLASSIFY,
                    it.getProductId(), null, it.getId(), it.getFromQuality());
        }
        header.setStatus("DRAFT");
        header.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(header);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void discard(Long id) {
        InventoryStockReclass header = baseMapper.selectById(id);
        if (header == null) throw new BusinessException("重分类单不存在");
        if ("AUDITED".equals(header.getStatus())) throw new BusinessException("已审核单据不可作废，请先反审核");
        header.setStatus("CANCELLED");
        header.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(header);
    }
}
