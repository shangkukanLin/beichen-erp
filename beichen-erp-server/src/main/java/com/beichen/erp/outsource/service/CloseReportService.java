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

    /** 确认结单 */
    void confirmClose(Long orderId);
}
