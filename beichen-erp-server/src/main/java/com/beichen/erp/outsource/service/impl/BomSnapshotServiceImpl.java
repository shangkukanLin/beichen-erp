package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.outsource.entity.BomSnapshot;
import org.springframework.dao.DuplicateKeyException;
import com.beichen.erp.outsource.entity.BomSnapshotItem;
import com.beichen.erp.outsource.entity.OutsourceOrderMaterial;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.mapper.BomSnapshotItemMapper;
import com.beichen.erp.outsource.mapper.BomSnapshotMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderProductMapper;
import com.beichen.erp.outsource.service.BomSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * BOM 快照服务实现（2026-09-17 重构）。
 *
 * <p><b>复用规则</b>（用户口径：下单时"研发 BOM 版本与上一次下单的不一样"才算变化）：</p>
 * <ol>
 *   <li>取该产品上一次快照（bom_snapshot 中 product_key 相同、id 最大的一条）；</li>
 *   <li>若「研发 BOM 版本(dev_bom.version) 相同」且「明细内容指纹相同」→ 直接复用该快照（多单共享，不再生成）；</li>
 *   <li>否则新建一份快照（含指纹）。历史迁移快照指纹为空时，按版本号一致即复用。</li>
 * </ol>
 *
 * <p><b>为什么能共享</b>：快照明细只存<b>单套用量</b>（与订单数量无关），
 * 需求数量由视图 {@code outsource_order_material} 按 ⟨单套用量 × 该产品行数量⟩ 实时换算，
 * 因此既有消费方（交货扣料/缺料判定/结单超损/退货快照/供应商需求/合同导出）无需改动。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BomSnapshotServiceImpl implements BomSnapshotService {

    private final BomSnapshotMapper snapshotMapper;
    private final BomSnapshotItemMapper itemMapper;
    private final OutsourceOrderProductMapper orderProductMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long resolveSnapshot(Long productMasterId, String productName, Long projectId,
                                BigDecimal productQuantity, List<OutsourceOrderMaterial> materials, Long companyId) {
        List<BomSnapshotItem> items = buildItems(materials, productQuantity);
        if (items.isEmpty()) {
            // 无 BOM 明细（如未选项目的空白产品行）：不建快照，与重构前"无物料则不写明细"一致
            return null;
        }
        String key = productKey(productMasterId, productName);
        Integer version = currentBomVersion(projectId);
        String fingerprint = fingerprint(items);

        BomSnapshot latest = snapshotMapper.selectOne(new LambdaQueryWrapper<BomSnapshot>()
                .eq(BomSnapshot::getProductKey, key)
                .orderByDesc(BomSnapshot::getId)
                .last("LIMIT 1"));
        // 复用判定：研发BOM版本一致 + 明细内容一致。
        // 用「内容直接比对」而不是比指纹列：历史迁移快照没有指纹，比指纹会误判为"有变化"（或反之）。
        if (latest != null && Objects.equals(latest.getBomVersion(), version)) {
            List<BomSnapshotItem> latestItems = itemMapper.selectList(
                    new LambdaQueryWrapper<BomSnapshotItem>().eq(BomSnapshotItem::getSnapshotId, latest.getId()));
            sortItems(latestItems);
            if (canonical(latestItems, true).equals(canonical(items, true))) {
                log.info("BOM 快照复用：productKey={} bomVersion={} snapshotId={}（研发BOM未变化，不重复生成）",
                        key, version, latest.getId());
                return latest.getId();
            }
        }

        BomSnapshot snapshot = new BomSnapshot();
        snapshot.setProductKey(key);
        snapshot.setProductMasterId(productMasterId);
        snapshot.setProjectId(projectId);
        snapshot.setBomVersion(version);
        snapshot.setFingerprint(fingerprint);
        snapshot.setItemCount(items.size());
        snapshot.setKind(resolveKind(projectId, items));
        String prevDesc;
        if (latest == null) {
            prevDesc = "｜首次快照";
        } else if (Objects.equals(latest.getBomVersion(), version)) {
            prevDesc = "｜研发BOM版本未变，但明细有调整";
        } else {
            prevDesc = "｜研发BOM版本由 " + (latest.getBomVersion() == null ? "无" : "v" + latest.getBomVersion())
                    + " 变为 " + (version == null ? "无" : "v" + version);
        }
        snapshot.setRemark("下单生成｜BOM版本 " + (version == null ? "无" : "v" + version) + prevDesc);
        snapshot.setCompanyId(companyId);
        try {
            snapshotMapper.insert(snapshot);
        } catch (DuplicateKeyException dup) {
            // F2-4（2026-09-18 审核修复）：并发下单时两张单可能同时判定"需要新建"，
            // 由唯一键 uk_snapshot(product_key, bom_version, fingerprint) 兜住；
            // 抢输的一方改为复用赢家已建好的快照，避免"整单报错"或"生成两份内容相同的快照"。
            BomSnapshot winner = snapshotMapper.selectOne(new LambdaQueryWrapper<BomSnapshot>()
                    .eq(BomSnapshot::getProductKey, key)
                    .eq(BomSnapshot::getFingerprint, fingerprint)
                    .orderByDesc(BomSnapshot::getId)
                    .last("LIMIT 1"));
            if (winner != null) {
                log.info("BOM 快照并发复用：productKey={} bomVersion={} snapshotId={}", key, version, winner.getId());
                return winner.getId();
            }
            throw dup;
        }

        for (BomSnapshotItem item : items) {
            item.setId(null);
            item.setSnapshotId(snapshot.getId());
            item.setCompanyId(companyId);
            itemMapper.insert(item);
        }
        log.info("BOM 快照新建：productKey={} bomVersion={} items={} snapshotId={}",
                key, version, items.size(), snapshot.getId());
        return snapshot.getId();
    }

    @Override
    public BomSnapshot snapshotOfOrderProduct(Long orderProductId) {
        if (orderProductId == null) return null;
        OutsourceOrderProduct p = orderProductMapper.selectById(orderProductId);
        if (p == null || p.getBomSnapshotId() == null) return null;
        return snapshotMapper.selectById(p.getBomSnapshotId());
    }

    @Override
    public List<Map<String, Object>> historyByProject(Long projectId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (projectId == null) return result;
        List<BomSnapshot> snapshots = snapshotMapper.selectList(new LambdaQueryWrapper<BomSnapshot>()
                .eq(BomSnapshot::getProjectId, projectId)
                .orderByDesc(BomSnapshot::getId));
        for (BomSnapshot s : snapshots) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("bomVersion", s.getBomVersion());
            m.put("kind", s.getKind());
            m.put("itemCount", s.getItemCount());
            m.put("remark", s.getRemark());
            m.put("createTime", s.getCreateTime());
            m.put("items", itemsOf(s.getId()));
            m.put("orders", ordersOf(s.getId()));
            result.add(m);
        }
        return result;
    }

    // ==================== 内部实现 ====================

    /** 明细（含物料名/类型名，供立项详细页展示） */
    private List<Map<String, Object>> itemsOf(Long snapshotId) {
        return jdbcTemplate.queryForList(
                "SELECT si.id, si.outsource_material_id AS materialId, si.material_type_id AS materialTypeId, "
                        + "si.unit, si.quantity_per_set AS quantityPerSet, si.loss_rate AS lossRate, "
                        + "si.supply_type AS supplyType, si.remark, "
                        + "m.material_name AS materialName, mt.type_name AS materialTypeName "
                        + "FROM bom_snapshot_item si "
                        + "LEFT JOIN outsource_material m ON m.id = si.outsource_material_id "
                        + "LEFT JOIN material_type mt ON mt.id = si.material_type_id "
                        + "WHERE si.snapshot_id = ? ORDER BY si.id", snapshotId);
    }

    /** 引用该快照的加工单（单号 + 状态），说明"这份快照被哪些单用着" */
    private List<Map<String, Object>> ordersOf(Long snapshotId) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT o.code AS code, o.status AS status "
                        + "FROM outsource_order o "
                        + "JOIN outsource_order_product op ON op.order_id = o.id "
                        + "WHERE op.bom_snapshot_id = ? ORDER BY o.code", snapshotId);
    }

    /**
     * 组装快照明细：统一折算为「单套用量」。
     * 优先用前端带来的单套用量(bomQuantityPerSet→quantityPerSet)，缺省时按 ⟨需求数量 ÷ 产品数量⟩ 折算。
     */
    private List<BomSnapshotItem> buildItems(List<OutsourceOrderMaterial> materials, BigDecimal productQuantity) {
        List<BomSnapshotItem> items = new ArrayList<>();
        if (materials == null || materials.isEmpty()) return items;
        BigDecimal qty = (productQuantity != null && productQuantity.signum() != 0) ? productQuantity : null;
        for (OutsourceOrderMaterial m : materials) {
            if (m == null || m.getMaterialId() == null) continue; // 无物料ID的行不落快照
            BomSnapshotItem item = new BomSnapshotItem();
            item.setMaterialId(m.getMaterialId());
            item.setMaterialTypeId(m.getMaterialTypeId());
            item.setUnit(m.getUnit());
            BigDecimal perSet = m.getQuantityPerSet();
            if (perSet == null && m.getDemandQuantity() != null && qty != null) {
                perSet = m.getDemandQuantity().divide(qty, 4, RoundingMode.HALF_UP);
            }
            item.setQuantityPerSet(perSet == null ? BigDecimal.ZERO : perSet.setScale(4, RoundingMode.HALF_UP));
            item.setLossRate(nz(m.getLossRate()));
            item.setSupplyType(m.getSupplyType() == null || m.getSupplyType().isBlank() ? "OURS" : m.getSupplyType());
            item.setRemark(m.getRemark());
            items.add(item);
        }
        // 规范化排序：使"同样的明细、不同录入顺序"得到同一指纹
        sortItems(items);
        return items;
    }

    /** 明细规范化排序（内容比对/指纹计算前必须统一顺序） */
    private void sortItems(List<BomSnapshotItem> items) {
        if (items == null || items.isEmpty()) return;
        items.sort(Comparator.comparing(BomSnapshotItem::getMaterialId)
                .thenComparing(i -> nz(i.getQuantityPerSet()))
                .thenComparing(i -> nz(i.getLossRate()))
                .thenComparing(i -> String.valueOf(i.getSupplyType()))
                .thenComparing(i -> String.valueOf(i.getUnit()))
                .thenComparing(i -> String.valueOf(i.getMaterialTypeId())));
    }

    /** 明细内容指纹：物料+类型+单位+单套用量+损耗+供料方（排序后 MD5） */
    private String fingerprint(List<BomSnapshotItem> items) {
        return md5(canonical(items, true));
    }

    /** 核心口径指纹（不含供料方）：用于判断"快照是否与研发 BOM 一致" */
    private String coreFingerprintOfItems(List<BomSnapshotItem> items) {
        return md5(canonical(items, false));
    }

    private String canonical(List<BomSnapshotItem> items, boolean withSupplyType) {
        StringBuilder sb = new StringBuilder();
        for (BomSnapshotItem i : items) {
            sb.append(i.getMaterialId()).append('|')
                    .append(i.getMaterialTypeId() == null ? "" : i.getMaterialTypeId()).append('|')
                    .append(i.getUnit() == null ? "" : i.getUnit().trim()).append('|')
                    .append(nz(i.getQuantityPerSet()).setScale(4, RoundingMode.HALF_UP).toPlainString()).append('|')
                    .append(nz(i.getLossRate()).setScale(4, RoundingMode.HALF_UP).toPlainString());
            if (withSupplyType) sb.append('|').append(i.getSupplyType());
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 快照来源：与当前研发 BOM 明细一致 = BOM；否则（订单内调整过损耗/供料方、或无项目）= ORDER */
    private String resolveKind(Long projectId, List<BomSnapshotItem> items) {
        if (projectId == null) return "ORDER";
        List<Map<String, Object>> bomRows = jdbcTemplate.queryForList(
                "SELECT outsource_material_id, material_type_id, unit, quantity, loss_rate "
                        + "FROM dev_bom WHERE project_id = ? "
                        + "AND version = (SELECT MAX(version) FROM dev_bom WHERE project_id = ?) "
                        + "ORDER BY outsource_material_id, id", projectId, projectId);
        if (bomRows.isEmpty()) return "ORDER";
        List<BomSnapshotItem> bomItems = new ArrayList<>();
        for (Map<String, Object> row : bomRows) {
            BomSnapshotItem it = new BomSnapshotItem();
            it.setMaterialId(toLong(row.get("outsource_material_id")));
            it.setMaterialTypeId(toLong(row.get("material_type_id")));
            it.setUnit(row.get("unit") == null ? null : row.get("unit").toString());
            it.setQuantityPerSet(row.get("quantity") == null ? BigDecimal.ZERO
                    : new BigDecimal(row.get("quantity").toString()).setScale(4, RoundingMode.HALF_UP));
            it.setLossRate(row.get("loss_rate") == null ? BigDecimal.ZERO
                    : new BigDecimal(row.get("loss_rate").toString()));
            if (it.getMaterialId() != null) bomItems.add(it);
        }
        bomItems.sort(Comparator.comparing(BomSnapshotItem::getMaterialId)
                .thenComparing(i -> nz(i.getQuantityPerSet()))
                .thenComparing(i -> nz(i.getLossRate())));
        return coreFingerprintOfItems(bomItems).equals(coreFingerprintOfItems(items)) ? "BOM" : "ORDER";
    }

    /** 研发 BOM 当前版本号（dev_bom.version 的最大值）；无项目或无 BOM 时为空 */
    private Integer currentBomVersion(Long projectId) {
        if (projectId == null) return null;
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT MAX(version) FROM dev_bom WHERE project_id = ?", Integer.class, projectId);
        } catch (Exception e) {
            log.warn("取研发BOM版本失败 projectId={}: {}", projectId, e.getMessage());
            return null;
        }
    }

    /** 快照归属键：有产品主数据用 P:{id}（跨订单共享），否则退化为 N:{产品名称} */
    private String productKey(Long productMasterId, String productName) {
        if (productMasterId != null) return "P:" + productMasterId;
        return "N:" + (productName == null ? "" : productName.trim());
    }

    private String md5(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 算法不可用", e);
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        String s = v.toString().trim();
        return s.isEmpty() ? null : Long.valueOf(s);
    }
}
