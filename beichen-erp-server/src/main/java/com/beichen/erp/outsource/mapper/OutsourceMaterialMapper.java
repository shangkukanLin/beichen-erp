package com.beichen.erp.outsource.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OutsourceMaterialMapper extends BaseMapper<OutsourceMaterial> {

    /** 按名称查找物料ID（绕过租户过滤，因为物料名来自dev_bom可能跨公司） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id FROM outsource_material WHERE material_name = #{name} LIMIT 1")
    Long findIdByName(String name);
}
