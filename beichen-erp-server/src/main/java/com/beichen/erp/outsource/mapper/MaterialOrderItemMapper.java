package com.beichen.erp.outsource.mapper;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.outsource.entity.MaterialOrderItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MaterialOrderItemMapper extends BaseMapper<MaterialOrderItem> {
    /** 逻辑删除字段，物理删除自动转为 UPDATE deleted=1 */
    @TableLogic
    Integer deleted = 0;
}
