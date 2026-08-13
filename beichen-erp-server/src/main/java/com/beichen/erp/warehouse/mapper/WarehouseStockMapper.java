package com.beichen.erp.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface WarehouseStockMapper extends BaseMapper<WarehouseStock> {

    /** 原子库存加减（成品库存，按 product_id） */
    @Update("""
            UPDATE warehouse_stock
            SET quantity = quantity + #{delta},
                available_quantity = available_quantity + #{delta},
                update_time = NOW()
            WHERE warehouse_id = #{warehouseId}
              AND product_id = #{productId}
              AND quality_type = #{qualityType}
              AND company_id = #{companyId}
              AND quantity + #{delta} >= 0
            """)
    int updateQuantity(@Param("warehouseId") Long warehouseId,
                       @Param("productId") Long productId,
                       @Param("qualityType") String qualityType,
                       @Param("companyId") Long companyId,
                       @Param("delta") BigDecimal delta);

    /** 原子库存加减（物料库存，按 material_id） */
    @Update("""
            UPDATE warehouse_stock
            SET quantity = quantity + #{delta},
                update_time = NOW()
            WHERE warehouse_id = #{warehouseId}
              AND material_id = #{materialId}
              AND company_id = #{companyId}
              AND quantity + #{delta} >= 0
            """)
    int updateMaterialQuantity(@Param("warehouseId") Long warehouseId,
                                @Param("materialId") Long materialId,
                                @Param("companyId") Long companyId,
                                @Param("delta") BigDecimal delta);

    /** 插入成品库存首条记录 */
    @Insert("""
            INSERT INTO warehouse_stock
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
