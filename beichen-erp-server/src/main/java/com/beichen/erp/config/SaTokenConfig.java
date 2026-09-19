package com.beichen.erp.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置：拦截器 + 多租户 + 跨域
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    /** F3-3（2026-09-18 接口级权限专项）：页面级接口权限守卫（前缀映射，见 ApiPermGuard） */
    private final ApiPermGuard apiPermGuard;

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
                    // F3-3（2026-09-18 接口级权限专项）：页面级接口权限校验（方法感知：写必收口，读按共享白名单）
                    // 口径：用户被收掉某页面（菜单）后，直调该模块**写接口**即 403，不再只是"前端不显示入口"
                    apiPermGuard.check(currentRequestPath(), currentRequestMethod());
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

    /** 当前请求路径（不含 context-path）；无请求上下文时返回 null ⇒ 守卫放行（如内部调用/定时任务） */
    private static String currentRequestPath() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest().getRequestURI() : null;
    }

    /** 当前请求方法；无请求上下文时返回 null（守卫按"读取"处理，只校验写入） */
    private static String currentRequestMethod() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest().getMethod() : null;
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
