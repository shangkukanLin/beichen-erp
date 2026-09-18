package com.beichen.erp.outsource.service;

import com.beichen.erp.outsource.entity.BomSnapshot;
import com.beichen.erp.outsource.entity.OutsourceOrderMaterial;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * BOM 快照服务（2026-09-17 重构）。
 * <p>把「每张加工单各存一份 BOM 明细」改为「按 产品 + 研发BOM版本 + 明细内容 共享一份快照」：
 * 下单时若研发 BOM 版本与明细内容与上一次快照一致，直接复用既有快照，只有有变化时才新建。</p>
 */
public interface BomSnapshotService {

    /**
     * 解析（复用或新建）BOM 快照，返回快照ID（无有效物料明细时返回 null）。
     *
     * @param productMasterId 产品主数据ID(product.id)，可为空（物料直挂模式）
     * @param productName     产品名称（无主数据ID时作快照归属键）
     * @param projectId       研发项目ID，用于取研发BOM版本(dev_bom.version)
     * @param productQuantity 该产品行数量（用于把"需求数量"折算成"单套用量"）
     * @param materials       本次提交的 BOM 明细（来自研发BOM或物料直挂）
     * @param companyId       公司ID
     */
    Long resolveSnapshot(Long productMasterId, String productName, Long projectId,
                         BigDecimal productQuantity, List<OutsourceOrderMaterial> materials, Long companyId);

    /** 加工单产品行所用快照（版本/来源/生成时间），供加工单详情展示 */
    BomSnapshot snapshotOfOrderProduct(Long orderProductId);

    /** 立项详细页：该项目的 BOM 历史快照（版本 + 明细 + 引用它的加工单） */
    List<Map<String, Object>> historyByProject(Long projectId);
}
