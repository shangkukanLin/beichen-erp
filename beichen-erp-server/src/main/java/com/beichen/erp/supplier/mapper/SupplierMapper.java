package com.beichen.erp.supplier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.supplier.entity.Supplier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SupplierMapper extends BaseMapper<Supplier> {

    /**
     * 行锁查询供应商（用于清算等需加锁场景），配合 FOR UPDATE 使用
     */
    @Select("SELECT * FROM supplier WHERE id = #{id} FOR UPDATE")
    Supplier selectForUpdate(@Param("id") Long id);

    /**
     * 批量汇总供应商应付余额：按供应商ID分组，SUM 未结清应付台账的未付金额
     * 采用 LEFT JOIN + GROUP BY 一次性算完，配合 idx_supplier_id 索引，避免逐供应商 N+1 查询
     * @param supplierIds 供应商ID集合（非空）
     * @return supplierId -> 应付余额
     */
    @Select("<script>" +
            "SELECT s.id AS supplier_id, IFNULL(SUM(p.unpaid_amount), 0) AS balance " +
            "FROM supplier s " +
            "LEFT JOIN finance_payable p ON p.supplier_id = s.id AND p.status != '已结清' " +
            "WHERE s.id IN " +
            "<foreach collection='supplierIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY s.id" +
            "</script>")
    @MapKey("supplier_id")
    Map<Long, Map<String, Object>> sumPayableBalance(@Param("supplierIds") List<Long> supplierIds);
}
