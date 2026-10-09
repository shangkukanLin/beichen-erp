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
import java.util.HashMap;
import java.util.Map;
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

    /**
     * P4（2026-09-30 未上线清理，用户口径「旧数据不需要兼容」）：此处原有一个
     * {@code @Deprecated} 的**旧签名兼容重载** {@code changeStock(Long, String productName, ...)} ——
     * 它把第二个形参当产品名接收、内部却**只透传 `spec`**（产品名整个丢弃），语义容易误用。
     * 16 处调用点已全部改为直接调下面的主重载（productId 作第 2 参）⇒ 该重载删除。
     *
     * <p>遗留项：主重载里的 {@code spec} 形参自"产品规格下线"后**从未被使用**（调用方一律传 {@code ""}），
     * 保留只是为了不动 50+ 处调用点；如需彻底瘦身可单独排期（纯机械改动）。</p>
     */

    /**
     * 通用库存变更：按 (warehouseId, productId, qualityType) 定位唯一库存行。
     * <p>⚠️ `spec`（产品规格）为**历史遗留参数，方法体内从未使用**；产品规格字段已于 2026-09-15 全站下线
     * （DB 列与实体字段均已删除），**调用方一律传 `""`（不得传 `null`）**。保留形参仅为避免大范围改动调用点
     * （全项目 50+ 处调用）。F7-65①（2026-09-20）：本模块（outsource）已把 4 处 `null` 改为 `""` 以符合该约定。</p>
     */
    @Transactional
    public void changeStock(Long warehouseId, Long productId, BigDecimal quantity,
                            StockChangeType type, String relatedBillNo, RelatedBillType relatedBillType,
                            String spec, Long relatedBillId, String qualityType) {
        // 2026-09-25 P0-3 收尾：保留原签名（40+ 调用点不动），委托带形态的重载并默认 MATERIAL（行为等价）
        changeStock(warehouseId, productId, quantity, type, relatedBillNo, relatedBillType,
                spec, relatedBillId, qualityType, WarehouseStock.FORM_MATERIAL);
    }

    /**
     * 带库存形态的通用库存变更（P1/P2 写「成品（加工退货/维修退货）」时调用本重载）。
     *
     * @param stockForm 库存形态（{@link WarehouseStock#FORM_MATERIAL} / FORM_PRODUCT_DEFECT / FORM_PRODUCT_REPAIR），
     *                  定位键/建行/流水全部按该形态走，不同形态互不串行
     */
    @Transactional
    public void changeStock(Long warehouseId, Long productId, BigDecimal quantity,
                            StockChangeType type, String relatedBillNo, RelatedBillType relatedBillType,
                            String spec, Long relatedBillId, String qualityType, String stockForm) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) return;
        quantity = intQty(quantity, "成品库存"); // 数量一律为整数（2026-09-16）
        assertRefsExist(warehouseId, productId, null); // P2-33：拒绝往不存在的仓库/产品写库存
        if (qualityType == null) qualityType = ProductQualityType.A.getCode();
        if (stockForm == null || stockForm.isBlank()) stockForm = WarehouseStock.FORM_MATERIAL;
        Long companyId = CompanyContext.get();
        if (companyId != null && companyId <= 0) companyId = null;

        int rows = warehouseStockMapper.updateQuantity(warehouseId, productId, qualityType, stockForm, companyId, quantity);
        if (rows == 0) {
            WarehouseStock exist = selectExist(warehouseId, productId, qualityType, stockForm, companyId);
            if (exist != null || quantity.compareTo(BigDecimal.ZERO) < 0) {
                BigDecimal available = exist != null && exist.getQuantity() != null ? exist.getQuantity() : BigDecimal.ZERO;
                throw productShortage(warehouseId, productId, qualityType, available, quantity);
            }
            try {
                insertStock(warehouseId, productId, qualityType, stockForm, companyId, quantity);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                int retry = warehouseStockMapper.updateQuantity(warehouseId, productId, qualityType, stockForm, companyId, quantity);
                if (retry == 0) throw new BusinessException("库存不足，无法出库：产品ID=" + productId);
            }
        }

        // 写库存流水
        WarehouseStock latest = selectExist(warehouseId, productId, qualityType, stockForm, companyId);
        BigDecimal after = latest != null && latest.getQuantity() != null ? latest.getQuantity() : quantity;
        BigDecimal before = after.subtract(quantity);

        WarehouseStockLog log = new WarehouseStockLog();
        log.setWarehouseId(warehouseId);
        log.setProductId(productId);
        log.setQualityType(qualityType);
        log.setStockForm(stockForm);   // 2026-09-25 P0-2：流水与库存同行形态
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
        // 2026-09-25 P0-3 收尾：保留原签名（40+ 调用点不动），委托带形态的重载并默认 MATERIAL（行为等价）
        changeMaterialStock(warehouseId, materialId, quantity, changeType, relatedCode, relatedBillType,
                relatedDeliveryId, relatedOrderId, relatedBillId, WarehouseStock.FORM_MATERIAL);
    }

    /**
     * 带库存形态的物料库存变更（严格口径：库存不足抛错）。
     *
     * @param stockForm 库存形态（物料侧唯一区分维度，quality_type 恒为 GOOD，两者正交勿混）
     */
    @Transactional
    public void changeMaterialStock(Long warehouseId, Long materialId, BigDecimal quantity,
                                     String changeType, String relatedCode, RelatedBillType relatedBillType,
                                     Long relatedDeliveryId, Long relatedOrderId, Long relatedBillId,
                                     String stockForm) {
        changeMaterialStockInternal(warehouseId, materialId, quantity, changeType, relatedCode, relatedBillType,
                relatedDeliveryId, relatedOrderId, relatedBillId, false, stockForm);
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
        // 2026-09-25 P0-3 收尾：保留原签名，委托带形态的重载并默认 MATERIAL（行为等价）
        changeMaterialStockAllowNegative(warehouseId, materialId, quantity, changeType, relatedCode, relatedBillType,
                relatedDeliveryId, relatedOrderId, relatedBillId, WarehouseStock.FORM_MATERIAL);
    }

    /** 带库存形态的物料库存变更（允许负库存口径：委外强制出库）。 */
    @Transactional
    public void changeMaterialStockAllowNegative(Long warehouseId, Long materialId, BigDecimal quantity,
                                                 String changeType, String relatedCode, RelatedBillType relatedBillType,
                                                 Long relatedDeliveryId, Long relatedOrderId, Long relatedBillId,
                                                 String stockForm) {
        changeMaterialStockInternal(warehouseId, materialId, quantity, changeType, relatedCode, relatedBillType,
                relatedDeliveryId, relatedOrderId, relatedBillId, true, stockForm);
    }

    /**
     * P2-33：库存写入前校验引用维度存在。
     * <p>边界矩阵实测：`productId/materialId/warehouseId = 99999999` 的单据都能审核通过并把库存与流水
     * 写到不存在的维度上（"幽灵库存行"）。收敛在库存写入层做一次校验，可覆盖所有模块（含未来新增路径）。</p>
     */
    private void assertRefsExist(Long warehouseId, Long productId, Long materialId) {
        // T7（2026-09-18 修复）：库存行必须能归属到「产品」或「物料」，**两者都空**会写出无法归属的
        // "幽灵库存行"（实测：其他出入库明细未选产品也能保存并审核，写出 product_id=material_id=NULL、
        // 数量 5 的库存行，产品库存统计出现脏数据）。此处是**库存写入层兜底**，覆盖所有模块与未来新增路径。
        if (productId == null && materialId == null)
            throw new BusinessException("库存变更必须指定产品ID或物料ID（不能都为空，否则会产生无法归属的库存行）");
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
        changeMaterialStockInternal(warehouseId, materialId, quantity, changeType, relatedCode, relatedBillType,
                relatedDeliveryId, relatedOrderId, relatedBillId, allowNegative, WarehouseStock.FORM_MATERIAL);
    }

    /**
     * 物料库存变更统一实现（物料侧唯一写入口）。
     *
     * @param allowNegative false=严格口径（库存不足抛错，供报损/退货等单据用）；true=委外强制出库口径（可扣成负数）
     * @param stockForm     库存形态（P0-3 收尾：由带形态的 public 重载传入；缺省 MATERIAL，行为与改造前等价）
     */
    private void changeMaterialStockInternal(Long warehouseId, Long materialId, BigDecimal quantity,
                                             String changeType, String relatedCode, RelatedBillType relatedBillType,
                                             Long relatedDeliveryId, Long relatedOrderId, Long relatedBillId,
                                             boolean allowNegative, String stockForm) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) return;
        quantity = intQty(quantity, "物料库存"); // 数量一律为整数（2026-09-16）
        assertRefsExist(warehouseId, null, materialId); // P2-33：拒绝往不存在的仓库/物料写库存
        Long companyId = CompanyContext.get();

        // 物料库存用 material_id，qualityType 统一为 GOOD
        String qt = QualityType.GOOD.getCode();
        // 2026-09-25 P0-3 收尾：形态改为形参（public 重载缺省 MATERIAL）；退回成品是"成品"形态，走成品侧 changeStock，不进本方法
        if (stockForm == null || stockForm.isBlank()) stockForm = WarehouseStock.FORM_MATERIAL;
        if (allowNegative) {
            WarehouseStock exist = selectMaterialExist(warehouseId, materialId, stockForm, companyId);
            if (exist == null) {
                // 首条记录可以直接是负数（与历史"强制出库"行为一致）
                insertMaterialStock(warehouseId, materialId, stockForm, companyId, quantity);
            } else {
                // 允许负数的原子加减：updateMaterialQuantity 的 SQL 带 quantity+delta>=0 护栏，这里不能复用。
                // ⚠️ 这里按**行 ID** 更新（不是按 仓+物料 定位）⇒ 天然不会跨形态累加，无需补形态条件。
                // 2026-10-08：available_quantity 与 quantity 同步（口径见 insertMaterialStock 的注释）。
                warehouseStockMapper.update(null, new LambdaUpdateWrapper<WarehouseStock>()
                        .eq(WarehouseStock::getId, exist.getId())
                        .setSql("quantity = IFNULL(quantity, 0) + (" + quantity.toPlainString() + "), "
                                + "available_quantity = IFNULL(available_quantity, 0) + (" + quantity.toPlainString() + ")"));
            }
        } else {
            int rows = warehouseStockMapper.updateMaterialQuantity(warehouseId, materialId, stockForm, companyId, quantity);
            if (rows == 0) {
                if (selectMaterialExist(warehouseId, materialId, stockForm, companyId) != null || quantity.compareTo(BigDecimal.ZERO) < 0) {
                    throw materialShortage(warehouseId, materialId, quantity);
                }
                try {
                    insertMaterialStock(warehouseId, materialId, stockForm, companyId, quantity);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    int retry = warehouseStockMapper.updateMaterialQuantity(warehouseId, materialId, stockForm, companyId, quantity);
                    if (retry == 0) throw materialShortage(warehouseId, materialId, quantity);
                }
            }
        }

        WarehouseStock latest = selectMaterialExist(warehouseId, materialId, stockForm, companyId);
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
        logEntry.setStockForm(stockForm);   // 2026-09-25 P0-2：流水与库存同行形态
        logEntry.setChangeType(changeType);
        logEntry.setChangeQuantity(quantity);
        logEntry.setBeforeQuantity(before);
        logEntry.setAfterQuantity(after);
        logEntry.setRelatedOrderCode(relatedCode);
        logEntry.setRelatedBillType(relatedBillType != null ? relatedBillType.getCode() : null);
        // F1（2026-09-17 修复）：物料流水原先**只写 related_order_code、从不写 related_bill_no**，
        // 导致物料侧流水 100% 无法回溯单据（实测 264/440 行为空，退货/收料/领料/维修返回整类为空）。
        // 各调用点传入的 relatedCode 就是单据号（报损 WBS- / 物料退货 MR- / 加工单 WO- / 盘点 PD- 等），
        // 与成品侧 changeStock 的口径保持一致地写入 related_bill_no。
        if (relatedCode != null && !relatedCode.isBlank()) logEntry.setRelatedBillNo(relatedCode);
        logEntry.setRelatedDeliveryId(relatedDeliveryId);
        // relatedBillId：物料报损/退货/维修返回等独立单据用它跳回详情页
        if (relatedBillId != null) logEntry.setRelatedBillId(relatedBillId);
        if (companyId != null) logEntry.setCompanyId(companyId);
        warehouseStockLogMapper.insert(logEntry);
    }

    /** 查询指定仓库+产品+品质等级的当前库存量（用于反审核前校验库存是否被后续单据消耗），无记录返回 0 */
    public BigDecimal getQuantity(Long warehouseId, Long productId, String qualityType) {
        return getQuantity(warehouseId, productId, qualityType, WarehouseStock.FORM_MATERIAL);
    }

    /** 带库存形态的读侧重载（P0-3 收尾：盘点按形态对账取账面用），无记录返回 0 */
    public BigDecimal getQuantity(Long warehouseId, Long productId, String qualityType, String stockForm) {
        if (qualityType == null) qualityType = ProductQualityType.A.getCode();
        if (stockForm == null || stockForm.isBlank()) stockForm = WarehouseStock.FORM_MATERIAL;
        Long companyId = CompanyContext.get();
        if (companyId != null && companyId <= 0) companyId = null;
        WarehouseStock exist = selectExist(warehouseId, productId, qualityType, stockForm, companyId);
        return exist != null && exist.getQuantity() != null ? exist.getQuantity() : BigDecimal.ZERO;
    }

    /**
     * 查询指定仓库+物料的当前库存量（物料库存不区分品质，唯一键是仓库+物料），无记录返回 0。
     * 供物料报损等单据在扣减前做「库存是否够」的前置校验。
     */
    public BigDecimal getMaterialQuantity(Long warehouseId, Long materialId) {
        return getMaterialQuantity(warehouseId, materialId, WarehouseStock.FORM_MATERIAL);
    }

    /** 带库存形态的读侧重载（P0-3 收尾：盘点按形态对账取账面用），无记录返回 0 */
    public BigDecimal getMaterialQuantity(Long warehouseId, Long materialId, String stockForm) {
        if (stockForm == null || stockForm.isBlank()) stockForm = WarehouseStock.FORM_MATERIAL;
        Long companyId = CompanyContext.get();
        if (companyId != null && companyId <= 0) companyId = null;
        WarehouseStock exist = selectMaterialExist(warehouseId, materialId, stockForm, companyId);
        return exist != null && exist.getQuantity() != null ? exist.getQuantity() : BigDecimal.ZERO;
    }

    /**
     * F7-82（2026-09-20 性能专项）：**批量**查询同一仓库下若干物料的当前库存量（不区分品质，唯一键=仓库+物料）。
     *
     * <p>语义与 {@link #getMaterialQuantity(Long, Long)} **完全一致**（同样的租户条件、同样的"无记录按 0"），
     * 只是把 N 次单查收敛为 1 次：供"逐明细校验库存"的场景（物料报损等）使用。
     * 返回的 Map **只包含有库存记录**的物料，调用方用 {@code getOrDefault(id, ZERO)} 兜底。</p>
     */
    public Map<Long, BigDecimal> getMaterialQuantities(Long warehouseId, java.util.Collection<Long> materialIds) {
        Map<Long, BigDecimal> map = new HashMap<>();
        if (warehouseId == null || materialIds == null || materialIds.isEmpty()) return map;
        Long companyId = CompanyContext.get();
        if (companyId != null && companyId <= 0) companyId = null;
        for (WarehouseStock s : warehouseStockMapper.selectList(new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId)
                .in(WarehouseStock::getMaterialId, materialIds)
                .eq(companyId != null, WarehouseStock::getCompanyId, companyId))) {
            if (s.getMaterialId() == null) continue;
            map.put(s.getMaterialId(), s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO);
        }
        return map;
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

    private WarehouseStock selectExist(Long warehouseId, Long productId, String qualityType, String stockForm, Long companyId) {
        return warehouseStockMapper.selectOne(new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId)
                .eq(WarehouseStock::getProductId, productId)
                .eq(WarehouseStock::getQualityType, qualityType)
                .eq(WarehouseStock::getStockForm, stockForm)   // 2026-09-25 P0-2：形态是定位键的一部分
                .eq(companyId != null, WarehouseStock::getCompanyId, companyId));
    }

    private WarehouseStock selectMaterialExist(Long warehouseId, Long materialId, String stockForm, Long companyId) {
        return warehouseStockMapper.selectOne(new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId)
                .eq(WarehouseStock::getMaterialId, materialId)
                .eq(WarehouseStock::getStockForm, stockForm)   // 2026-09-25 P0-2：形态是定位键的一部分
                .eq(companyId != null, WarehouseStock::getCompanyId, companyId));
    }

    private void insertStock(Long warehouseId, Long productId, String qualityType, String stockForm, Long companyId, BigDecimal quantity) {
        WarehouseStock s = new WarehouseStock();
        s.setWarehouseId(warehouseId);
        s.setProductId(productId);
        s.setQualityType(qualityType);
        s.setStockForm(stockForm);   // 2026-09-25 P0-2：显式落形态（默认值仅为兼容存量，不能依赖）
        s.setQuantity(quantity);
        s.setAvailableQuantity(quantity);
        if (companyId != null) s.setCompanyId(companyId);
        warehouseStockMapper.insert(s);
    }

    private void insertMaterialStock(Long warehouseId, Long materialId, String stockForm, Long companyId, BigDecimal quantity) {
        WarehouseStock s = new WarehouseStock();
        s.setWarehouseId(warehouseId);
        s.setMaterialId(materialId);
        s.setQualityType(QualityType.GOOD.getCode());
        s.setStockForm(stockForm);   // 2026-09-25 P0-2：显式落形态（默认值仅为兼容存量，不能依赖）
        s.setQuantity(quantity);
        // 2026-10-08：**可用数量必须与 quantity 同步**。schema 列注释写明「可用数量(预留,目前等于quantity)」、
        // WarehouseStockController 也按"恒等于 quantity"实现；物料侧原先不设该列（落库默认 0）⇒ 物料库存会
        // 显示成「数量 2400 / 可用 0」。成品侧 insertStock 一直是两者同写，此处对齐口径。
        s.setAvailableQuantity(quantity);
        if (companyId != null) s.setCompanyId(companyId);
        warehouseStockMapper.insert(s);
    }
}
