package com.beichen.erp.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface WarehouseStockMapper extends BaseMapper<WarehouseStock> {

    /** 原子库存加减（成品库存，按 product_id）—— 2026-09-25 P0-2：WHERE 增 stock_form（形态），避免跨形态累加 */
    @Update("""
            UPDATE warehouse_stock
            SET quantity = quantity + #{delta},
                available_quantity = available_quantity + #{delta},
                update_time = NOW()
            WHERE warehouse_id = #{warehouseId}
              AND product_id = #{productId}
              AND quality_type = #{qualityType}
              AND stock_form = #{stockForm}
              AND company_id = #{companyId}
              AND quantity + #{delta} >= 0
            """)
    int updateQuantity(@Param("warehouseId") Long warehouseId,
                       @Param("productId") Long productId,
                       @Param("qualityType") String qualityType,
                       @Param("stockForm") String stockForm,
                       @Param("companyId") Long companyId,
                       @Param("delta") BigDecimal delta);

    /** 原子库存加减（物料库存，按 material_id）—— 2026-09-25 P0-2：WHERE 增 stock_form（形态） */
    @Update("""
            UPDATE warehouse_stock
            SET quantity = quantity + #{delta},
                available_quantity = available_quantity + #{delta},
                update_time = NOW()
            WHERE warehouse_id = #{warehouseId}
              AND material_id = #{materialId}
              AND stock_form = #{stockForm}
              AND company_id = #{companyId}
              AND quantity + #{delta} >= 0
            """)
    int updateMaterialQuantity(@Param("warehouseId") Long warehouseId,
                                @Param("materialId") Long materialId,
                                @Param("stockForm") String stockForm,
                                @Param("companyId") Long companyId,
                                @Param("delta") BigDecimal delta);

}
