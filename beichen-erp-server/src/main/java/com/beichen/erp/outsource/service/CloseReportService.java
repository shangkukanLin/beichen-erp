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

    /** 确认结单（returnWarehouseId：退料退回仓库，必填） */
    void confirmClose(Long orderId, Long returnWarehouseId);

    /** 反结单：逆向结单的库存/退料/缺失/超损应付，订单回退到生产中 */
    void reopenClose(Long orderId);
}
