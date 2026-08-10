package com.beichen.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.inventory.entity.InventoryWarehouseStock;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface InventoryWarehouseStockMapper extends BaseMapper<InventoryWarehouseStock> {

    /**
     * 原子库存加减：在数据库内直接 quantity = quantity + delta，并防止扣成负数。
     * 返回影响行数：0 表示（行不存在）或（库存不足导致 quantity+delta<0）。
     */
    @Update("""
            UPDATE inventory_warehouse_stock
            SET quantity = quantity + #{delta},
                available_quantity = available_quantity + #{delta},
                update_time = NOW()
            WHERE warehouse_id = #{warehouseId}
              AND product_id = #{productId}
              AND quality_type = #{qualityType}
              AND company_id = #{companyId}
              AND quantity + #{delta} >= 0
            """)
    int deltaUpdate(@Param("warehouseId") Long warehouseId,
                    @Param("productId") Long productId,
                    @Param("qualityType") String qualityType,
                    @Param("companyId") Long companyId,
                    @Param("delta") BigDecimal delta);

    /**
     * 库存行不存在时插入首条记录（并发冲突由唯一索引兜底）。
     */
    @Insert("""
            INSERT INTO inventory_warehouse_stock
                (warehouse_id, product_id, quality_type, quantity, available_quantity, company_id, create_time, update_time)
            VALUES
                (#{warehouseId}, #{productId}, #{qualityType}, #{delta}, #{delta},
                 #{companyId}, NOW(), NOW())
            """)
    int insertStock(@Param("warehouseId") Long warehouseId,
                    @Param("productId") Long productId,
                    @Param("qualityType") String qualityType,
                    @Param("companyId") Long companyId,
                    @Param("delta") BigDecimal delta);
}
