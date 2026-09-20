package com.beichen.erp.outsource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.outsource.entity.ReturnOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReturnOrderMapper extends BaseMapper<ReturnOrder> {

    /**
     * F7-138（2026-09-20）：**行锁**读取维修退货单（`FOR UPDATE`），供"维修返回登记 / 撤销 / 结案 / 撤销结案"
     * 串行化使用 —— 这四个动作都是"先查后写"（查已返回量 ⇒ 改库存/改结案标记），并发双击会重复入库或重复扣减。
     *
     * <p>与 `SupplierMapper.selectForUpdate` 的区别：**带租户条件**（`company_id`），这样一次调用同时完成
     * "加锁 + 租户校验"，不会因为裸 `FOR UPDATE` 绕过 mybatis-plus 的租户过滤而读到别家单据。</p>
     *
     * @param companyId 为 null 时不加租户条件（超管/无上下文场景，与项目其它 `buildWrapper` 口径一致）
     */
    @Select("<script>SELECT * FROM outsource_return_order WHERE id = #{id}"
            + "<if test='companyId != null'> AND company_id = #{companyId}</if> FOR UPDATE</script>")
    ReturnOrder selectForUpdate(@Param("id") Long id, @Param("companyId") Long companyId);
}
