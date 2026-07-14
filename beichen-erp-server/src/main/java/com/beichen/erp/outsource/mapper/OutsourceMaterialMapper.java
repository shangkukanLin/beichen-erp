package com.beichen.erp.outsource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OutsourceMaterialMapper extends BaseMapper<OutsourceMaterial> {

    /** 按名称查找物料ID（遵循登录公司租户过滤） */
    @Select("SELECT id FROM outsource_material WHERE material_name = #{name} LIMIT 1")
    Long findIdByName(String name);
}
