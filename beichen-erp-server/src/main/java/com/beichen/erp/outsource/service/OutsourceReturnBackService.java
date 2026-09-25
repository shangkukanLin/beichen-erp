package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.outsource.entity.OutsourceReturnBack;
import com.beichen.erp.outsource.entity.OutsourceReturnBackItem;

import java.util.List;
import java.util.Map;

/**
 * 委外加工返回单（P1-2，2026-09-25）
 * <p>核销在厂成品（PRODUCT_DEFECT）+ 修好成品回我方仓 + 实际用料多行（可超 BOM）从委外仓扣减
 * + 料款按 FIFO 生成对加工厂的应收（工厂赔料）+ 成本结转。草稿-审核-反审核-作废状态机。</p>
 */
public interface OutsourceReturnBackService {

    /** 分页列表（含在厂/回仓仓库名、加工厂名等展示字段） */
    Page<Map<String, Object>> page(int pageNum, int pageSize, String code, Long factoryId, String status);

    /** 详情 */
    OutsourceReturnBack getById(Long id);

    /** 用料明细 */
    List<OutsourceReturnBackItem> getItems(Long id);

    /** 新建草稿（body: factoryId/productId/quantity/defectQualityType/returnQualityType/inWarehouseId/returnDate/remark/items[]） */
    OutsourceReturnBack create(Map<String, Object> body);

    /** 修改草稿（整单替换明细） */
    void update(Long id, Map<String, Object> body);

    /** 审核：三腿落账 + 应收 + 成本结转 */
    void audit(Long id);

    /** 反审核：三腿对称回滚 + 冲应收 + 反结转成本 */
    void unAudit(Long id);

    /** 作废（仅草稿） */
    void cancel(Long id);

    /** 删除草稿 */
    void deleteDraft(Long id);
}
