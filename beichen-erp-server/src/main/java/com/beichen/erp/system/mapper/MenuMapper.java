package com.beichen.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.system.entity.Menu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MenuMapper extends BaseMapper<Menu> {

    @Select("SELECT * FROM sys_menu WHERE status = 1 AND visible = 1 ORDER BY sort_order")
    List<Menu> selectAllEnabled();
}
