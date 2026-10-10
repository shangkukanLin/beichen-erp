package com.beichen.erp.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
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
@Slf4j
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    /** F3-3（2026-09-18 接口级权限专项）：页面级接口权限守卫（前缀映射，见 ApiPermGuard） */
    private final ApiPermGuard apiPermGuard;

    /** 2026-09-23：仅为兜底解析"操作人姓名"（会话里没有 username 时查一次并回写会话） */
    private final UserMapper userMapper;

    /**
     * 拦截器**排除清单**（F8-01/F8-02，2026-09-30 设置模块审核批 A）。
     *
     * <p><b>为什么必须显式列出并写明理由</b>：本类 `addInterceptors()` 用**同一条**拦截器做两层校验 ——
     * `StpUtil.checkLogin()`（登录）与 `apiPermGuard.check(...)`（页面级权限）。因此**排除了路径 = 同时绕开登录与权限**
     * （等价于对匿名用户开放）。这正是 F8-01 的成因：`/api/company/list` 曾被列在此处，导致**无 token 即可拉取全部公司**。</p>
     *
     * <p><b>纪律</b>：① 每条必须是"**未登录也必须能调**"的场景；② 能只放"最小信息"的接口就不要再放"整表列表"；
     * ③ 新增/删除本条清单时，必须同步 {@code ApiPermGuardSelfCheck} 的 {@code EXEMPT_PATH_ALLOWED}
     * （否则启动自检会报 ERROR —— 该断言就是为了让"新加一条排除"不再静默）。</p>
     */
    public static final List<String> EXEMPT_PATHS = List.of(
            "/api/auth/login",            // 登录（未登录必须可达）
            "/api/auth/captcha",          // 验证码
            "/api/auth/company-name",     // 登录页展示公司名（仅名称，无敏感字段）
            "/api/company/admin/verify",  // 超管口令校验（用于换取超管会话，本身在登录前）
            // 登录页「选择公司」下拉（登录前必须可达）。**F8-01 修复**：该接口已改为**按调用者身份分层返回** ——
            // 未登录/非超管只拿到 `id + companyName`（最小投影，不再暴露税号/电话/地址/联系人），
            // 仅超管（公司管理页）取完整档案；控制器见 CompanyController.list()。
            "/api/company/list",
            "/api/system/menu/tree/user", // 侧栏菜单树（控制器内按**当前用户**过滤角色菜单）
            // ---- 文档 / 静态资源（与业务无关）----
            "/doc.html",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/webjars/**"
    );

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
                    // 登录后从会话读取 companyId 设置到 ThreadLocal。
                    // ⚠️ 2026-10-10：必须是 **getTokenSession()**（按 token 区分），不能用 getSession()
                    // —— 后者是 **Account-Session**（按账号ID分配，同一账号在 PC / APP / 不同浏览器登录**共用同一个**），
                    // 于是"这一次登录选的是哪家公司"会被广播给该账号的其它会话（实测：公司1 的 token 突然只看得到 GD 的数据）。
                    // 读取点与写入点必须同作用域：写入见 AuthServiceImpl.login / CompanyController.verifyAdmin·switchCompany。
                    Object cid = StpUtil.getTokenSession().get("companyId");
                    if (cid != null) {
                        CompanyContext.set(Long.valueOf(cid.toString()));
                    } else {
                        // 兜底可观测性：已登录却读不到 companyId ⇒ 本次请求**不做公司过滤**（能看到所有公司的数据）。
                        // 正常不会出现（所有登录路径都会写入）；一旦出现即"某登录路径漏写"，让它立刻可见而不是静默越权。
                        log.warn("Token-Session 缺少 companyId ⇒ 本次请求不做租户过滤（读取点见 SaTokenConfig，"
                                + "写入点见 AuthServiceImpl.login / CompanyController.verifyAdmin·switchCompany）");
                    }
                    // 2026-09-23（用户口径：单据详情要显示「制单人 / 审核人」）：把"当前操作人"写入 ThreadLocal，
                    // 供 MetaObjectHandler 自动填充 create_by/create_by_name，以及各审核点盖章 auditor。
                    // 姓名优先取会话快照（登录时写入）；老会话没有则查一次 sys_user 并**回写会话**（一个会话只查一次）。
                    try {
                        long uid = StpUtil.getLoginIdAsLong();
                        Object cachedName = StpUtil.getSession().get("username");
                        String uname = cachedName != null ? cachedName.toString() : null;
                        if (uname == null || uname.isBlank()) {
                            User u = userMapper.selectById(uid);
                            uname = u != null ? u.getUsername() : null;
                            if (uname != null && !uname.isBlank()) {
                                StpUtil.getSession().set("username", uname);
                            }
                        }
                        UserContext.set(uid, uname);
                    } catch (Exception ignore) {
                        // 未登录/会话异常：不阻断主流程（后续 CompanyContext/权限守卫会各司其职）
                    }
                    // F3-3（2026-09-18 接口级权限专项）：页面级接口权限校验（方法感知：写必收口，读按共享白名单）
                    // 口径：用户被收掉某页面（菜单）后，直调该模块**写接口**即 403，不再只是"前端不显示入口"
                    apiPermGuard.check(currentRequestPath(), currentRequestMethod());
                }))
                .addPathPatterns("/api/**")
                .excludePathPatterns(EXEMPT_PATHS.toArray(new String[0]));

        // 请求完成后清除 CompanyContext / UserContext（线程池复用必须清理，否则串号）
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                    Object handler, Exception ex) {
                CompanyContext.clear();
                UserContext.clear();
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
