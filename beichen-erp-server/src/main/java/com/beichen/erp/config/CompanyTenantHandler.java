package com.beichen.erp.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

/**
 * 多租户处理器：自动给 SQL 注入 company_id 过滤条件
 */
public class CompanyTenantHandler implements TenantLineHandler {

    /**
     * 不参与租户隔离的表（无 company_id 列的共享表）。
     * <p>⚠️ 新增"无 company_id 列的关联表"时必须加进来，否则租户插件会给它注入 company_id 条件 →
     * {@code Unknown column 'company_id' in 'where clause'}（2026-09-18 加 sys_user_menu 时踩过）。</p>
     */
    private static final java.util.Set<String> IGNORE_TABLES = java.util.Set.of(
        "sys_company", "sys_user", "sys_role", "sys_menu", "sys_role_menu", "sys_user_role",
        "sys_user_menu",
        // 2026-10-09（V5）：用户表格列宽偏好 —— 按**用户**维度存储（用户口径：列宽不跟公司走），
        // 表内没有 company_id 列 ⇒ 必须在这里登记，否则会被拼上 company_id 条件而报 Unknown column。
        "sys_user_table_pref",
        // F7-99（2026-09-19）：屏幕资料知识库 = 行业基础资料（1268 条机型种子数据），
        // 不是业务数据（清空数据时一律保留）⇒ **各公司共享同一份**，不参与租户隔离；
        // 该表 company_id 已统一置 NULL（DataInitializer 导入时也会归拢）。
        "screen_model"
    );

    @Override
    public Expression getTenantId() {
        Long companyId = CompanyContext.get();
        if (companyId == null || companyId == 0) return null;
        return new LongValue(companyId);
    }

    @Override
    public String getTenantIdColumn() {
        return "company_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // 超管模式(companyId=0/null) 或特定表不参与租户隔离
        Long companyId = CompanyContext.get();
        if (companyId == null || companyId == 0) return true;
        return IGNORE_TABLES.contains(tableName);
    }
}
