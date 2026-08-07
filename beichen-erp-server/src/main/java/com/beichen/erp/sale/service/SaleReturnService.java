package com.beichen.erp.sale.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.beichen.erp.sale.entity.SaleReturn;
import com.beichen.erp.sale.entity.SaleReturnItem;

import java.util.List;
import java.util.Map;

public interface SaleReturnService {

    IPage<Map<String, Object>> page(Integer status, Long customerId, String code, int pageNum, int pageSize);

    SaleReturn getById(Long id);

    List<SaleReturnItem> getItems(Long returnId);

    SaleReturn create(SaleReturn order, List<Map<String, Object>> itemMaps);

    SaleReturn update(Long id, SaleReturn order, List<Map<String, Object>> itemMaps);

    void audit(Long id);

    void unAudit(Long id);

    void cancel(Long id);

    void delete(Long id);
}
