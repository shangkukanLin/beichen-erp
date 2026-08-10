package com.beichen.erp.supplier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.supplier.entity.Supplier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface SupplierMapper extends BaseMapper<Supplier> {

    /**
     * 原子更新应付余额：在原值基础上增减，避免并发读-改-写丢更新
     * 注意：原生 @Update 会绕过租户拦截器，但 WHERE 为主键精确更新，id 来源已通过租户校验，安全。
     * @param id    供应商ID
     * @param delta 变动额（正为增加应付，负为减少）
     * @return 受影响行数
     */
    @Update("UPDATE supplier SET payable_balance = IFNULL(payable_balance,0) + #{delta} WHERE id = #{id}")
    int addPayableBalance(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /**
     * 行锁查询供应商（用于清算等需加锁场景），配合 FOR UPDATE 使用
     */
    @Select("SELECT * FROM supplier WHERE id = #{id} FOR UPDATE")
    Supplier selectForUpdate(@Param("id") Long id);
}
