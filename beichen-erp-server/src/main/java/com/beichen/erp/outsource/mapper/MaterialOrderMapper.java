package com.beichen.erp.outsource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.outsource.entity.MaterialOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * F7-122（2026-09-20）：原实现把 `@TableLogic Integer deleted = 0;` 写在**本接口的常量**上。
 * MyBatis-Plus 只识别**实体字段**上的 `@TableLogic`，接口常量（隐式 `public static final`）**完全无效**
 * ⇒ 当时的注释"物理删除自动转为 UPDATE deleted=1"与实际行为**相反**（实际是物理删除）。
 * 现删除该无效常量，并把真相写清楚：**逻辑删除未启用**；如确需启用，应把 `@TableLogic` 标注到
 * `MaterialOrder.deleted` 字段上（并同步评估"删除后明细是否需要级联"）。
 */
@Mapper
public interface MaterialOrderMapper extends BaseMapper<MaterialOrder> {
}
