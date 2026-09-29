package com.beichen.erp.sale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.BillNoSeq;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.material.service.ProductService;
import com.beichen.erp.sale.entity.SaleOrder;
import com.beichen.erp.sale.entity.SaleOutbound;
import com.beichen.erp.sale.entity.SaleOutboundItem;
import com.beichen.erp.sale.mapper.SaleOutboundMapper;
import com.beichen.erp.sale.mapper.SaleOutboundItemMapper;
import com.beichen.erp.sale.mapper.SaleOrderMapper;
import com.beichen.erp.sale.service.SaleOutboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleOutboundServiceImpl implements SaleOutboundService {

    private final SaleOutboundMapper outboundMapper;
    private final SaleOutboundItemMapper itemMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;
    // 2026-09-20（F7-105）：原注入的 WarehouseStockService 已移除 —— 本单不再变动库存（见 audit 的说明）。

    @Override
    public Page<Map<String, Object>> page(String status, Long customerId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<SaleOutbound> w = new LambdaQueryWrapper<SaleOutbound>()
                .eq(status != null && !status.isBlank(), SaleOutbound::getStatus, status)
                .eq(customerId != null, SaleOutbound::getCustomerId, customerId)
                .like(code != null && !code.isBlank(), SaleOutbound::getCode, code)
                .orderByDesc(SaleOutbound::getId);
        Page<SaleOutbound> raw = outboundMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 批量查询客户名称，消除 N+1
        List<Long> customerIds = raw.getRecords().stream().map(SaleOutbound::getCustomerId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Customer> customerMap = customerIds.isEmpty() ? Collections.emptyMap()
                : customerMapper.selectBatchIds(customerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, c -> c, (a, b) -> a));
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("orderId", o.getOrderId());
            m.put("customerId", o.getCustomerId());
            m.put("warehouseId", o.getWarehouseId());
            m.put("outboundDate", o.getOutboundDate());
            m.put("status", o.getStatus());
            m.put("totalAmount", o.getTotalAmount());
            m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            Customer c = o.getCustomerId() != null ? customerMap.get(o.getCustomerId()) : null;
            m.put("customerName", c != null ? c.getName() : "");
            return m;
        }).toList());
        return res;
    }

    @Override
    public SaleOutbound getById(Long id) { return outboundMapper.selectById(id); }

    @Override
    public List<SaleOutboundItem> getItems(Long outboundId) {
        List<SaleOutboundItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleOutboundItem>().eq(SaleOutboundItem::getOutboundId, outboundId));
        // 回填产品名称与 SKU（两者都是非表字段），前端免查库
        Set<Long> pids = items.stream().map(SaleOutboundItem::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!pids.isEmpty()) {
            Map<Long, Product> pm = productMapper.selectBatchIds(pids).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
            for (SaleOutboundItem it : items) {
                Product p = it.getProductId() != null ? pm.get(it.getProductId()) : null;
                it.setProductName(p != null && p.getName() != null ? p.getName() : "");
                it.setSku(p != null && p.getSku() != null ? p.getSku() : "");
            }
        }
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(SaleOutbound outbound, List<SaleOutboundItem> items) {
        if (outbound.getCustomerId() == null) throw new BusinessException("客户不能为空");
        if (outbound.getWarehouseId() == null) throw new BusinessException("出库仓库不能为空");
        outbound.setCode(generateCode());
        outbound.setStatus(DocStatus.DRAFT.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) outbound.setCompanyId(cid);
        BigDecimal total = BigDecimal.ZERO;
        outboundMapper.insert(outbound);
        for (SaleOutboundItem it : items) {
            it.setId(null);
            it.setOutboundId(outbound.getId());
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal p = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(q.multiply(p));
            total = total.add(it.getAmount());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        SaleOutbound u = new SaleOutbound();
        u.setId(outbound.getId());
        u.setTotalAmount(total);
        outboundMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SaleOutbound outbound, List<SaleOutboundItem> items) {
        SaleOutbound old = outboundMapper.selectById(outbound.getId());
        if (old == null) throw new BusinessException("销售出库单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        outbound.setCode(old.getCode());
        outboundMapper.updateById(outbound);
        itemMapper.delete(new LambdaQueryWrapper<SaleOutboundItem>().eq(SaleOutboundItem::getOutboundId, outbound.getId()));
        BigDecimal total = BigDecimal.ZERO;
        Long cid = CompanyContext.get();
        for (SaleOutboundItem it : items) {
            it.setId(null);
            it.setOutboundId(outbound.getId());
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal p = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(q.multiply(p));
            total = total.add(it.getAmount());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        SaleOutbound u = new SaleOutbound();
        u.setId(outbound.getId());
        u.setTotalAmount(total);
        outboundMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        SaleOutbound old = outboundMapper.selectById(id);
        if (old == null) throw new BusinessException("销售出库单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败
        if (!DocStatusGuard.claim(outboundMapper, SaleOutbound::getId, id, SaleOutbound::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("已审核的出库单不可作废");
        SaleOutbound u = new SaleOutbound();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        outboundMapper.updateById(u);
    }

    /**
     * 审核：**纯出库凭证，不参与库存变动**（F7-105 · 2026-09-20 移除重复扣减）。
     *
     * <p><b>财务口径（F7-249 · 2026-09-29 批 E）</b>：本单**不写任何财务腿**（audit/unAudit/cancel 三处都
     * 刻意不触碰台账与资金）—— 应收在**销售单审核**时一次性挂账（{@code finance_receivable}，来源
     * {@code SALE_ORDER}），出库只是"发货执行凭证"，不重复挂账也不冲账。若将来要让出库单独立挂账，
     * 必须同时改销售单侧（否则双记）并补"来源销售单"约束。</p>
     *
     * <p>历史：2026-09-17（D2）起「销售单审核」已统一扣减库存（{@code SaleOrderServiceImpl.audit} →
     * {@code StockChangeType.SALE_OUT / RelatedBillType.SALE_ORDER}），而本方法**也**扣一次，
     * 两者同时启用即**双重扣减**。原实现以"该页面未注册路由、无菜单（点不到）"为由保留代码、仅在注释里警示；
     * 但 {@code /api/inventory/outbound} 当时**未在 ApiPermGuard 登记** ⇒ 接口层本就可达
     * （任意登录用户一条 curl 即可触发，实测见报告 §37）⇒ "点不到"的假设不成立。</p>
     *
     * <p>现口径：**库存的唯一入口是销售单审核**。出库单只保留
     * ① CAS 状态机；② "必须挂一张已审核销售单"的校验；③ 出库凭证字段（仓库/日期/明细/备注）。
     * 将来给它挂路由（作为物流/批次凭证）时，**无需再动库存逻辑**。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        SaleOutbound outbound = outboundMapper.selectById(id);
        if (outbound == null) throw new BusinessException("销售出库单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免库存重复扣减
        if (!DocStatusGuard.claim(outboundMapper, SaleOutbound::getId, id, SaleOutbound::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        // C7：出库前必须挂一张**已审核**的销售单，防"无销售单直接出库"或挂未审核单出库
        if (outbound.getOrderId() == null)
            throw new BusinessException("出库单未关联销售单，无法审核（请在出库单上选择销售单）");
        SaleOrder saleOrder = saleOrderMapper.selectById(outbound.getOrderId());
        if (saleOrder == null) throw new BusinessException("关联的销售单不存在（ID=" + outbound.getOrderId() + "）");
        if (!DocStatus.AUDITED.getCode().equals(saleOrder.getStatus()))
            throw new BusinessException("关联的销售单尚未审核（单号 " + saleOrder.getCode() + "，当前状态 "
                    + saleOrder.getStatus() + "），请先审核销售单再出库");
        List<SaleOutboundItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleOutboundItem>().eq(SaleOutboundItem::getOutboundId, id));
        if (items.isEmpty()) throw new BusinessException("出库单明细不能为空");
        // 2026-09-20（F7-105）：**此处原先会扣减库存**（SALE_OUT / SALE_OUTBOUND），与销售单审核重复。
        // 已移除 —— 库存只由 SaleOrderServiceImpl.audit 变动（单入口）。
        // 更新出库单状态（本单是出库凭证：不动库存、不生成应收、不改订单状态，避免跨单污染）
        SaleOutbound u = new SaleOutbound();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        outboundMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        SaleOutbound outbound = outboundMapper.selectById(id);
        if (outbound == null) throw new BusinessException("销售出库单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免库存重复回补
        if (!DocStatusGuard.claim(outboundMapper, SaleOutbound::getId, id, SaleOutbound::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已审核的出库单可反审核");
        // 2026-09-20（F7-105）：**此处原先会回补库存**（SALE_OUT_UN_AUDIT，与 audit 的扣减对称）。
        // 已随扣减一并移除 —— 本单不再参与库存变动，反审核只回退状态。
        // 出库单状态回退为草稿（实体无审核人字段，仅回退状态）
        SaleOutbound u = new SaleOutbound();
        u.setId(id);
        u.setStatus(DocStatus.DRAFT.getCode());
        outboundMapper.updateById(u);
    }

    private String generateCode() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.SALE_OUTBOUND + d;
        LambdaQueryWrapper<SaleOutbound> w = new LambdaQueryWrapper<SaleOutbound>()
                .likeRight(SaleOutbound::getCode, pat).orderByDesc(SaleOutbound::getCode).last("LIMIT 1");
        SaleOutbound last = outboundMapper.selectOne(w);
        // F7-116（2026-09-20）：统一走 BillNoSeq（详见该类 javadoc）。
        int seq = BillNoSeq.lastSeq(last == null ? null : last.getCode(), pat) + 1;
        return BillNoSeq.formatUnique(pat, seq, cand -> outboundMapper.selectCount(new LambdaQueryWrapper<SaleOutbound>().eq(SaleOutbound::getCode, cand)) > 0) /* F7-261 冲突检测+重试 */;
    }
}
