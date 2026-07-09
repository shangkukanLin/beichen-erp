package com.beichen.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.inventory.entity.InventoryWarehouseStock;
import com.beichen.erp.inventory.mapper.InventoryWarehouseStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InventoryWarehouseStockService {

    private final InventoryWarehouseStockMapper mapper;

    /** 入库：增加库存，不存在则新建 */
    @Transactional
    public void stockIn(Long warehouseId, String productName, BigDecimal quantity) {
        InventoryWarehouseStock stock = mapper.selectOne(new LambdaQueryWrapper<InventoryWarehouseStock>()
                .eq(InventoryWarehouseStock::getWarehouseId, warehouseId)
                .eq(InventoryWarehouseStock::getProductName, productName));
        if (stock != null) {
            stock.setQuantity(stock.getQuantity().add(quantity));
            mapper.updateById(stock);
        } else {
            stock = new InventoryWarehouseStock();
            stock.setWarehouseId(warehouseId);
            stock.setProductName(productName);
            stock.setQuantity(quantity);
            mapper.insert(stock);
        }
    }
}
