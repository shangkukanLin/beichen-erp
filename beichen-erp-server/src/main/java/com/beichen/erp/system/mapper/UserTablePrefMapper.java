package com.beichen.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.system.entity.UserTablePref;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表格列宽偏好（sys_user_table_pref，2026-10-09）。
 * 注意：该表无 company_id，须保持在 CompanyTenantHandler.IGNORE_TABLES 中。
 */
@Mapper
public interface UserTablePrefMapper extends BaseMapper<UserTablePref> {
}
