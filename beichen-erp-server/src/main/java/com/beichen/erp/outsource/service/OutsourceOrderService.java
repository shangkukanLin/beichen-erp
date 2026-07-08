package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderMaterial;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;

import java.util.List;
import java.util.Map;

public interface OutsourceOrderService {

    Page<Map<String, Object>> page(String status, Long factoryId, String code, int pageNum, int pageSize);

    OutsourceOrder getById(Long id);

    List<OutsourceOrderProduct> getProducts(Long orderId);

    List<OutsourceOrderMaterial> getMaterials(Long productId);

    void create(OutsourceOrder order, List<OutsourceOrderProduct> products);

    void update(OutsourceOrder order, List<OutsourceOrderProduct> products);

    void confirm(Long id);

    void complete(Long id);

    void cancel(Long id);
}
