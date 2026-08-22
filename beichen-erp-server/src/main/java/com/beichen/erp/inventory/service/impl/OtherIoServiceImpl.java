package com.beichen.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.entity.InventoryOtherIo;
import com.beichen.erp.inventory.entity.InventoryOtherIoItem;
import com.beichen.erp.inventory.mapper.InventoryOtherIoMapper;
import com.beichen.erp.inventory.mapper.InventoryOtherIoItemMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.inventory.common.IoType;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.inventory.service.OtherIoService;
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
public class OtherIoServiceImpl implements OtherIoService {

    private final InventoryOtherIoMapper ioMapper;
    private final InventoryOtherIoItemMapper itemMapper;
    private final WarehouseStockService stockService;
    private final ProductMapper productMapper;

    @Override
    public Page<Map<String, Object>> page(String status, Long warehouseId, String ioType, int pageNum, int pageSize) {
        LambdaQueryWrapper<InventoryOtherIo> w = new LambdaQueryWrapper<InventoryOtherIo>()
                .eq(status != null && !status.isBlank(), InventoryOtherIo::getStatus, status)
                .eq(warehouseId != null, InventoryOtherIo::getWarehouseId, warehouseId)
                .eq(ioType != null && !ioType.isBlank(), InventoryOtherIo::getIoType, ioType)
                .orderByDesc(InventoryOtherIo::getId);
        Page<InventoryOtherIo> raw = ioMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            m.put("warehouseId", o.getWarehouseId()); m.put("ioType", o.getIoType());
            m.put("ioDate", o.getIoDate()); m.put("status", o.getStatus()); m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            return m;
        }).toList());
        return res;
    }

    @Override
    public InventoryOtherIo getById(Long id) { return ioMapper.selectById(id); }

    @Override
    public List<InventoryOtherIoItem> getItems(Long otherIoId) {
        return itemMapper.selectList(new LambdaQueryWrapper<InventoryOtherIoItem>().eq(InventoryOtherIoItem::getOtherIoId, otherIoId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(InventoryOtherIo otherIo, List<InventoryOtherIoItem> items) {
        if (otherIo.getWarehouseId() == null) throw new BusinessException("仓库不能为空");
        if (otherIo.getIoType() == null || otherIo.getIoType().isBlank()) throw new BusinessException("出入库类型不能为空");
        otherIo.setCode(gen(BillPrefix.INVENTORY_OTHER_IO));
        // 统一流程：创建为草稿，审核时才应用库存
        otherIo.setStatus(DocStatus.DRAFT.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) otherIo.setCompanyId(cid);
        ioMapper.insert(otherIo);
        for (InventoryOtherIoItem it : items) {
            it.setId(null);
            it.setOtherIoId(otherIo.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(InventoryOtherIo otherIo, List<InventoryOtherIoItem> items) {
        InventoryOtherIo old = ioMapper.selectById(otherIo.getId());
        if (old == null) throw new BusinessException("其他出入库单不存在");
        // 统一流程：仅草稿可编辑（草稿未应用库存，直接更新主表与明细）
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("仅草稿状态可编辑");

        otherIo.setCode(old.getCode()); otherIo.setStatus(DocStatus.DRAFT.getCode());
        ioMapper.updateById(otherIo);

        // 删旧明细 + 插新明细
        itemMapper.delete(new LambdaQueryWrapper<InventoryOtherIoItem>().eq(InventoryOtherIoItem::getOtherIoId, otherIo.getId()));
        Long cid = CompanyContext.get();
        for (InventoryOtherIoItem it : items) {
            it.setId(null); it.setOtherIoId(otherIo.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        InventoryOtherIo old = ioMapper.selectById(id);
        if (old == null) throw new BusinessException("其他出入库单不存在");
        // 统一流程：仅草稿可作废（草稿未应用库存，无需逆向）；已审核单据请先反审核
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("仅草稿状态可作废，已审核单据请先反审核");
        InventoryOtherIo u = new InventoryOtherIo(); u.setId(id); u.setStatus(DocStatus.CANCELLED.getCode());
        ioMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InventoryOtherIo io = ioMapper.selectById(id);
        if (io == null) throw new BusinessException("其他出入库单不存在");
        if (!DocStatus.DRAFT.getCode().equals(io.getStatus())) throw new BusinessException("仅草稿状态可审核");
        // 审核时应用库存
        List<InventoryOtherIoItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<InventoryOtherIoItem>().eq(InventoryOtherIoItem::getOtherIoId, id));
        applyStock(io, items);
        InventoryOtherIo u = new InventoryOtherIo(); u.setId(id); u.setStatus(DocStatus.AUDITED.getCode());
        ioMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        InventoryOtherIo io = ioMapper.selectById(id);
        if (io == null) throw new BusinessException("其他出入库单不存在");
        if (!DocStatus.AUDITED.getCode().equals(io.getStatus())) throw new BusinessException("仅已审核状态可反审核");
        // 反审核时逆向库存，回到草稿
        List<InventoryOtherIoItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<InventoryOtherIoItem>().eq(InventoryOtherIoItem::getOtherIoId, id));
        revertStock(io, items);
        InventoryOtherIo u = new InventoryOtherIo(); u.setId(id); u.setStatus(DocStatus.DRAFT.getCode());
        ioMapper.updateById(u);
    }

    /** 应用库存变更 */
    private void applyStock(InventoryOtherIo io, List<InventoryOtherIoItem> items) {
            StockChangeType type = IoType.IN.getCode().equals(io.getIoType()) ? StockChangeType.OTHER_IN : StockChangeType.OTHER_OUT;
        for (InventoryOtherIoItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal delta = IoType.IN.getCode().equals(io.getIoType()) ? q : q.negate();
            Product prod = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(io.getWarehouseId(), prod != null ? prod.getName() : "",
                    delta, type, io.getCode(), RelatedBillType.OTHER_IO, it.getProductId(),
                    prod != null ? prod.getSpec() : "", io.getId(), it.getQualityType());
        }
    }

    /** 逆向库存（编辑回滚 / 取消） */
    private void revertStock(InventoryOtherIo io, List<InventoryOtherIoItem> items) {
        StockChangeType type = IoType.IN.getCode().equals(io.getIoType()) ? StockChangeType.CANCEL_IN : StockChangeType.CANCEL_OUT;
        for (InventoryOtherIoItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            // 逆向：入库变成扣回，出库变成加回
            BigDecimal delta = IoType.IN.getCode().equals(io.getIoType()) ? q.negate() : q;
            Product prod = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(io.getWarehouseId(), prod != null ? prod.getName() : "",
                    delta, type, io.getCode(), RelatedBillType.OTHER_IO, it.getProductId(),
                    prod != null ? prod.getSpec() : "", io.getId(), it.getQualityType());
        }
    }

    private String gen(String prefix) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        LambdaQueryWrapper<InventoryOtherIo> w = new LambdaQueryWrapper<InventoryOtherIo>()
                .likeRight(InventoryOtherIo::getCode, pat).orderByDesc(InventoryOtherIo::getCode).last("LIMIT 1");
        InventoryOtherIo last = ioMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return prefix + d + String.format("%03d", seq);
    }
}
