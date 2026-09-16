package com.beichen.erp.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.entity.WarehouseStockLog;
import com.beichen.erp.warehouse.mapper.WarehouseStockLogMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 统一库存变更 Service（合并 inventory_warehouse_stock + outsource_warehouse_stock）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseStockService {

    private final WarehouseStockMapper warehouseStockMapper;
    private final WarehouseStockLogMapper warehouseStockLogMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final com.beichen.erp.material.mapper.ProductMapper productMapper;
    private final com.beichen.erp.warehouse.mapper.WarehouseMapper warehouseMapper;

    /** 入库：增加库存，不存在则新建 */
    @Transactional
    public void stockIn(Long warehouseId, Long productId, BigDecimal quantity) {
        changeStock(warehouseId, productId, quantity, StockChangeType.PURCHASE_IN,
                null, (RelatedBillType) null, null, null, ProductQualityType.A.getCode());
    }

    /** 旧签名兼容；⚠️ `spec` 为历史遗留参数（方法体内从未使用），产品规格已于 2026-09-15 全站下线，调用方传 `""` */
    @Transactional
    public void changeStock(Long warehouseId, String productName, BigDecimal quantity,
                            StockChangeType type, String relatedBillNo, RelatedBillType relatedBillType,
                            Long productId, String spec, Long relatedBillId, String qualityType) {
        changeStock(warehouseId, productId, quantity, type, relatedBillNo, relatedBillType, spec, relatedBillId, qualityType);
    }

    /**
     * 通用库存变更：按 (warehouseId, productId, qualityType) 定位唯一库存行。
     * <p>⚠️ `spec`（产品规格）为**历史遗留参数，方法体内从未使用**；产品规格字段已于 2026-09-15 全站下线
     * （DB 列与实体字段均已删除），调用方一律传 `""`。保留形参仅为避免大范围改动调用点。</p>
     */
    @Transactional
    public void changeStock(Long warehouseId, Long productId, BigDecimal quantity,
                            StockChangeType type, String relatedBillNo, RelatedBillType relatedBillType,
                            String spec, Long relatedBillId, String qualityType) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) return;
        quantity = intQty(quantity, "成品库存"); // 数量一律为整数（2026-09-16）
        assertRefsExist(warehouseId, productId, null); // P2-33：拒绝往不存在的仓库/产品写库存
        if (qualityType == null) qualityType = ProductQualityType.A.getCode();
        Long companyId = CompanyContext.get();
        if (companyId != null && companyId <= 0) companyId = null;

        int rows = warehouseStockMapper.updateQuantity(warehouseId, productId, qualityType, companyId, quantity);
        if (rows == 0) {
            WarehouseStock exist = selectExist(warehouseId, productId, qualityType, companyId);
            if (exist != null || quantity.compareTo(BigDecimal.ZERO) < 0) {
                BigDecimal available = exist != null && exist.getQuantity() != null ? exist.getQuantity() : BigDecimal.ZERO;
                throw productShortage(warehouseId, productId, qualityType, available, quantity);
            }
            try {
                insertStock(warehouseId, productId, qualityType, companyId, quantity);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                int retry = warehouseStockMapper.updateQuantity(warehouseId, productId, qualityType, companyId, quantity);
                if (retry == 0) throw new BusinessException("库存不足，无法出库：产品ID=" + productId);
            }
        }

        // 写库存流水
        WarehouseStock latest = selectExist(warehouseId, productId, qualityType, companyId);
        BigDecimal after = latest != null && latest.getQuantity() != null ? latest.getQuantity() : quantity;
        BigDecimal before = after.subtract(quantity);

        WarehouseStockLog log = new WarehouseStockLog();
        log.setWarehouseId(warehouseId);
        log.setProductId(productId);
        log.setQualityType(qualityType);
        log.setChangeType(type.getCode());
        log.setChangeQuantity(quantity);
        log.setBeforeQuantity(before);
        log.setAfterQuantity(after);
        log.setRelatedBillNo(relatedBillNo);
        log.setRelatedBillType(relatedBillType != null ? relatedBillType.getCode() : null);
        log.setRelatedBillId(relatedBillId);
        if (companyId != null) log.setCompanyId(companyId);
        warehouseStockLogMapper.insert(log);
    }

    /**
     * 物料库存变更（委外仓物料）
     */
    @Transactional
    public void changeMaterialStock(Long warehouseId, Long materialId, BigDecimal quantity,
                                     String changeType, String relatedCode, RelatedBillType relatedBillType,
                                     Long relatedDeliveryId, Long relatedOrderId) {
        changeMaterialStock(warehouseId, materialId, quantity, changeType, relatedCode, relatedBillType,
                relatedDeliveryId, relatedOrderId, null);
    }

    /**
     * 物料库存变更（委外仓物料），多传一个 relatedBillId。
     * <p>物料类流水原先只有 relatedDeliveryId / relatedOrderId，物料报损这类独立单据
     * 需要按「单据类型 + 单据ID」跳回详情页，故加此重载（原 8 参方法行为不变）。</p>
     */
    @Transactional
    public void changeMaterialStock(Long warehouseId, Long materialId, BigDecimal quantity,
                                     String changeType, String relatedCode, RelatedBillType relatedBillType,
                                     Long relatedDeliveryId, Long relatedOrderId, Long relatedBillId) {
        changeMaterialStockInternal(warehouseId, materialId, quantity, changeType, relatedCode, relatedBillType,
                relatedDeliveryId, relatedOrderId, relatedBillId, false);
    }

    /**
     * 物料库存变更（**允许负库存**）—— 委外"强制出库"口径。
     *
     * <p>委外交货领料（可 {@code forceDelivery} 忽略缺料提示）、委外其他出入库等场景**历史上就允许把委外仓扣成负数**，
     * 收敛到本服务时不能顺手改语义：本方法只统一"库存写入 + 流水字段"，**不做库存充足校验**
     * （即不校验 {@code quantity + delta >= 0}，故不能复用 {@link WarehouseStockMapper#updateMaterialQuantity} 那条带护栏的 SQL）。</p>
     */
    @Transactional
    public void changeMaterialStockAllowNegative(Long warehouseId, Long materialId, BigDecimal quantity,
                                                 String changeType, String relatedCode, RelatedBillType relatedBillType,
                                                 Long relatedDeliveryId, Long relatedOrderId, Long relatedBillId) {
        changeMaterialStockInternal(warehouseId, materialId, quantity, changeType, relatedCode, relatedBillType,
                relatedDeliveryId, relatedOrderId, relatedBillId, true);
    }

    /**
     * P2-33：库存写入前校验引用维度存在。
     * <p>边界矩阵实测：`productId/materialId/warehouseId = 99999999` 的单据都能审核通过并把库存与流水
     * 写到不存在的维度上（"幽灵库存行"）。收敛在库存写入层做一次校验，可覆盖所有模块（含未来新增路径）。</p>
     */
    private void assertRefsExist(Long warehouseId, Long productId, Long materialId) {
        if (warehouseId == null || warehouseMapper.selectById(warehouseId) == null)
            throw new BusinessException("仓库不存在：ID=" + warehouseId);
        if (productId != null && productMapper.selectById(productId) == null)
            throw new BusinessException("产品不存在：ID=" + productId);
        if (materialId != null && outsourceMaterialMapper.selectById(materialId) == null)
            throw new BusinessException("委外物料不存在：ID=" + materialId);
    }

    /**
     * 物料库存变更统一实现（物料侧唯一写入口）。
     *
     * @param allowNegative false=严格口径（库存不足抛错，供报损/退货等单据用）；true=委外强制出库口径（可扣成负数）
     */
    private void changeMaterialStockInternal(Long warehouseId, Long materialId, BigDecimal quantity,
                                             String changeType, String relatedCode, RelatedBillType relatedBillType,
                                             Long relatedDeliveryId, Long relatedOrderId, Long relatedBillId,
                                             boolean allowNegative) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) return;
        quantity = intQty(quantity, "物料库存"); // 数量一律为整数（2026-09-16）
        assertRefsExist(warehouseId, null, materialId); // P2-33：拒绝往不存在的仓库/物料写库存
        Long companyId = CompanyContext.get();

        // 物料库存用 material_id，qualityType 统一为 GOOD
        String qt = QualityType.GOOD.getCode();
        if (allowNegative) {
            WarehouseStock exist = selectMaterialExist(warehouseId, materialId, companyId);
            if (exist == null) {
                // 首条记录可以直接是负数（与历史"强制出库"行为一致）
                insertMaterialStock(warehouseId, materialId, companyId, quantity);
            } else {
                // 允许负数的原子加减：updateMaterialQuantity 的 SQL 带 quantity+delta>=0 护栏，这里不能复用
                warehouseStockMapper.update(null, new LambdaUpdateWrapper<WarehouseStock>()
                        .eq(WarehouseStock::getId, exist.getId())
                        .setSql("quantity = IFNULL(quantity, 0) + (" + quantity.toPlainString() + ")"));
            }
        } else {
            int rows = warehouseStockMapper.updateMaterialQuantity(warehouseId, materialId, companyId, quantity);
            if (rows == 0) {
                if (selectMaterialExist(warehouseId, materialId, companyId) != null || quantity.compareTo(BigDecimal.ZERO) < 0) {
                    throw materialShortage(warehouseId, materialId, quantity);
                }
                try {
                    insertMaterialStock(warehouseId, materialId, companyId, quantity);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    int retry = warehouseStockMapper.updateMaterialQuantity(warehouseId, materialId, companyId, quantity);
                    if (retry == 0) throw materialShortage(warehouseId, materialId, quantity);
                }
            }
        }

        WarehouseStock latest = selectMaterialExist(warehouseId, materialId, companyId);
        BigDecimal after = latest != null && latest.getQuantity() != null ? latest.getQuantity() : quantity;
        BigDecimal before = after.subtract(quantity);

        // A3 负库存告警（2026-09-12）：委外"强制出库"口径**允许**扣成负数（不拦），但必须留痕可查 ——
        // 本方法是物料侧唯一写入口，在此集中告警即可覆盖领料/还料/退货/其他出入库等全部强制出库调用点。
        if (allowNegative && after.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("委外仓强制出库导致负库存：warehouseId={}, materialId={}, before={}, after={}, changeType={}, relatedBillType={}, relatedCode={}",
                    warehouseId, materialId, before, after, changeType,
                    relatedBillType != null ? relatedBillType.getCode() : null, relatedCode);
        }

        WarehouseStockLog logEntry = new WarehouseStockLog();
        logEntry.setWarehouseId(warehouseId);
        logEntry.setMaterialId(materialId);
        // 固化物料名称，避免流水展示时物料名为空
        String matName = materialNameOf(materialId);
        if (matName != null) logEntry.setMaterialName(matName);
        logEntry.setQualityType(qt);
        logEntry.setChangeType(changeType);
        logEntry.setChangeQuantity(quantity);
        logEntry.setBeforeQuantity(before);
        logEntry.setAfterQuantity(after);
        logEntry.setRelatedOrderCode(relatedCode);
        logEntry.setRelatedBillType(relatedBillType != null ? relatedBillType.getCode() : null);
        logEntry.setRelatedDeliveryId(relatedDeliveryId);
        // relatedBillId：物料报损等独立单据用它跳回详情页；其余物料变动为 null
        if (relatedBillId != null) logEntry.setRelatedBillId(relatedBillId);
        if (companyId != null) logEntry.setCompanyId(companyId);
        warehouseStockLogMapper.insert(logEntry);
    }

    /** 查询指定仓库+产品+品质等级的当前库存量（用于反审核前校验库存是否被后续单据消耗），无记录返回 0 */
    public BigDecimal getQuantity(Long warehouseId, Long productId, String qualityType) {
        if (qualityType == null) qualityType = ProductQualityType.A.getCode();
        Long companyId = CompanyContext.get();
        if (companyId != null && companyId <= 0) companyId = null;
        WarehouseStock exist = selectExist(warehouseId, productId, qualityType, companyId);
        return exist != null && exist.getQuantity() != null ? exist.getQuantity() : BigDecimal.ZERO;
    }

    /**
     * 查询指定仓库+物料的当前库存量（物料库存不区分品质，唯一键是仓库+物料），无记录返回 0。
     * 供物料报损等单据在扣减前做「库存是否够」的前置校验。
     */
    public BigDecimal getMaterialQuantity(Long warehouseId, Long materialId) {
        Long companyId = CompanyContext.get();
        if (companyId != null && companyId <= 0) companyId = null;
        WarehouseStock exist = selectMaterialExist(warehouseId, materialId, companyId);
        return exist != null && exist.getQuantity() != null ? exist.getQuantity() : BigDecimal.ZERO;
    }

    /** 成品出库库存不足异常（带品质、可用、需求与缺口数量，便于直接定位缺多少） */
    private BusinessException productShortage(Long warehouseId, Long productId, String qualityType,
                                             BigDecimal available, BigDecimal need) {
        BigDecimal avail = available != null ? available : BigDecimal.ZERO;
        BigDecimal needAbs = need != null ? need.abs() : BigDecimal.ZERO;
        BigDecimal gap = needAbs.subtract(avail).max(BigDecimal.ZERO);
        return new BusinessException("库存不足，无法出库：产品ID=" + productId
                + "（规格 " + (qualityType != null ? qualityType : "A") + "，仓库ID=" + warehouseId
                + "）可用 " + avail.stripTrailingZeros().toPlainString()
                + "，需求 " + needAbs.stripTrailingZeros().toPlainString()
                + "，缺口 " + gap.stripTrailingZeros().toPlainString());
    }

    /** 物料库存不足异常（带物料名 + 可用/需求/缺口数量，便于定位是哪一行、缺多少） */
    private BusinessException materialShortage(Long warehouseId, Long materialId, BigDecimal quantity) {
        String name = materialNameOf(materialId);
        BigDecimal need = quantity != null ? quantity.abs() : BigDecimal.ZERO;
        BigDecimal avail = getMaterialQuantity(warehouseId, materialId);
        BigDecimal gap = need.subtract(avail).max(BigDecimal.ZERO);
        return new BusinessException("物料[" + (name != null ? name : "ID=" + materialId) + "]库存不足，无法出库：仓库ID=" + warehouseId
                + "，可用 " + avail.stripTrailingZeros().toPlainString()
                + "，需求 " + need.stripTrailingZeros().toPlainString()
                + "，缺口 " + gap.stripTrailingZeros().toPlainString());
    }

    /**
     * 数量统一取整（四舍五入）。2026-09-16 用户要求：**物料与产品数量都是整数**。
     * <p>数量列已统一为 DECIMAL(18,0)；此处再兜底一次，避免调用方漏取整把小数写进库存/流水
     * （一旦出现小数会打 WARN，便于回溯是哪个调用方漏了取整）。</p>
     */
    private BigDecimal intQty(BigDecimal qty, String scene) {
        if (qty == null) return null;
        BigDecimal rounded = qty.setScale(0, RoundingMode.HALF_UP);
        if (rounded.compareTo(qty) != 0) {
            log.warn("{}写入小数数量 {} → 取整为 {}（数量一律为整数，请检查调用方）",
                    scene, qty.stripTrailingZeros().toPlainString(), rounded.toPlainString());
        }
        return rounded;
    }

    private String materialNameOf(Long materialId) {
        if (materialId == null) return null;
        OutsourceMaterial mat = outsourceMaterialMapper.selectById(materialId);
        return mat != null ? mat.getMaterialName() : null;
    }

    private WarehouseStock selectExist(Long warehouseId, Long productId, String qualityType, Long companyId) {
        return warehouseStockMapper.selectOne(new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId)
                .eq(WarehouseStock::getProductId, productId)
                .eq(WarehouseStock::getQualityType, qualityType)
                .eq(companyId != null, WarehouseStock::getCompanyId, companyId));
    }

    private WarehouseStock selectMaterialExist(Long warehouseId, Long materialId, Long companyId) {
        return warehouseStockMapper.selectOne(new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId)
                .eq(WarehouseStock::getMaterialId, materialId)
                .eq(companyId != null, WarehouseStock::getCompanyId, companyId));
    }

    private void insertStock(Long warehouseId, Long productId, String qualityType, Long companyId, BigDecimal quantity) {
        WarehouseStock s = new WarehouseStock();
        s.setWarehouseId(warehouseId);
        s.setProductId(productId);
        s.setQualityType(qualityType);
        s.setQuantity(quantity);
        s.setAvailableQuantity(quantity);
        if (companyId != null) s.setCompanyId(companyId);
        warehouseStockMapper.insert(s);
    }

    private void insertMaterialStock(Long warehouseId, Long materialId, Long companyId, BigDecimal quantity) {
        WarehouseStock s = new WarehouseStock();
        s.setWarehouseId(warehouseId);
        s.setMaterialId(materialId);
        s.setQualityType(QualityType.GOOD.getCode());
        s.setQuantity(quantity);
        if (companyId != null) s.setCompanyId(companyId);
        warehouseStockMapper.insert(s);
    }
}
