package com.beichen.erp.outsource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.outsource.entity.MaterialOrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * F7-122（2026-09-20）：同 {@code MaterialOrderMapper} —— 原先写在**接口常量**上的 `@TableLogic` **无效**
 * （MP 只认实体字段），实际是物理删除。现删除该无效常量并写明真相；如需启用逻辑删除请标注到实体字段。
 */
@Mapper
public interface MaterialOrderItemMapper extends BaseMapper<MaterialOrderItem> {
}
