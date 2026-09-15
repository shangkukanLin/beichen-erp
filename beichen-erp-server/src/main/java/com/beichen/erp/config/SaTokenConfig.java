package com.beichen.erp.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置：拦截器 + 多租户 + 跨域
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 跨域白名单（P0 配置外置 · 2026-09-14）：逗号分隔，默认 {@code *} 仅限开发联调。
     * <p>生产必须显式配置（如 {@code https://erp.example.com}）—— 此前硬编码 {@code *} + {@code allowCredentials=true}
     * 等于允许任意站点携带凭证调用接口。</p>
     */
    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
                    StpUtil.checkLogin();
                    // 登录后从 session 读取 companyId 设置到 ThreadLocal
                    Object cid = StpUtil.getSession().get("companyId");
                    if (cid != null) {
                        CompanyContext.set(Long.valueOf(cid.toString()));
                    }
                }))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/captcha",
                        "/api/auth/company-name",
                        "/api/company/admin/verify",
                        "/api/company/list",
                        "/api/system/menu/tree/user",
                        "/doc.html",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/webjars/**"
                );

        // 请求完成后清除 CompanyContext
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                    Object handler, Exception ex) {
                CompanyContext.clear();
            }
        }).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.split("\\s*,\\s*"))
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
