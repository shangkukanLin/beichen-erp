package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.outsource.entity.CloseReport;
import com.beichen.erp.outsource.entity.CloseReportItem;

import java.util.List;
import java.util.Map;

public interface CloseReportService extends IService<CloseReport> {
    /** 生成或获取结单报表（含明细和交货记录） */
    Map<String, Object> getOrCreateReport(Long orderId);

    /** 保存草稿 */
    void saveDraft(Long orderId, List<CloseReportItem> items, String remark);

    /**
     * 确认结单（returnWarehouseId：退料退回仓库，必填）。
     * <p>2026-09-16（问题①）：委外交货领料走"允许负"口径，而结单退料原先走严格口径 →
     * 工厂委外仓账面为负（或无库存行）时无法退料，必须先补一张入库单才能结单。
     * {@code force=true} 时退料与缺失按"强制出库"口径处理（允许把委外仓扣成负数，流水留痕可查），
     * 与物料收发单/调拨单的「强制出库」同规格。</p>
     */
    void confirmClose(Long orderId, Long returnWarehouseId, boolean force);

    /** 反结单：逆向结单的库存/退料/缺失/超损应付，订单回退到生产中 */
    void reopenClose(Long orderId);
}
