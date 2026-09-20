package com.beichen.erp.outsource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterialReturn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OutsourceMaterialReturnMapper extends BaseMapper<OutsourceMaterialReturn> {

    /**
     * F7-138（2026-09-20）：**行锁**读取维修返还单（`FOR UPDATE`），与物料侧的
     * {@code repairReturn / cancelRepairReturn / close / reOpen} 四个"先查后写"动作配套，
     * 防止并发双击重复入库 / 重复扣减。**带租户条件**，理由同 {@code ReturnOrderMapper.selectForUpdate}。
     */
    @Select("<script>SELECT * FROM outsource_material_return WHERE id = #{id}"
            + "<if test='companyId != null'> AND company_id = #{companyId}</if> FOR UPDATE</script>")
    OutsourceMaterialReturn selectForUpdate(@Param("id") Long id, @Param("companyId") Long companyId);
}
