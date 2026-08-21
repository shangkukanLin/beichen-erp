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

    /** 解析加工产品关联的产品主数据ID(product.id)：明细行productId > 项目关联 > 按名称匹配 */
    Long resolveProductMasterId(OutsourceOrderProduct product);

    void create(OutsourceOrder order, List<OutsourceOrderProduct> products);

    void update(OutsourceOrder order, List<OutsourceOrderProduct> products);

    void audit(Long id);

    void unaudit(Long id);

    void cancel(Long id);
}
