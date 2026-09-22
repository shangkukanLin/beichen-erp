package com.beichen.erp.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置：分页插件 + 多租户 + 自动填充字段
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 多租户插件
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new CompanyTenantHandler()));
        // 分页插件（必须在租户插件之后）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                Long cid = CompanyContext.get();
                if (cid != null && cid > 0) {
                    this.strictInsertFill(metaObject, "companyId", Long.class, cid);
                }
                // 2026-09-23（用户口径：单据详情要显示「制单人」）：统一在此落**创建人 ID + 姓名快照**。
                // 各实体只要把 createBy/createByName 标上 @TableField(fill = FieldFill.INSERT) 即自动生效，
                // 业务代码零改动；未加字段的老实体 strictInsertFill 自动跳过 ⇒ 可按批补字段。
                // 姓名落快照（与 auditor_id/auditor_name 同范式）：用户改名/离职后历史单据仍显示当时的操作人。
                Long uid = UserContext.getId();
                if (uid != null && uid > 0) {
                    this.strictInsertFill(metaObject, "createBy", Long.class, uid);
                }
                String uname = UserContext.getName();
                if (uname != null && !uname.isBlank()) {
                    this.strictInsertFill(metaObject, "createByName", String.class, uname);
                }
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
