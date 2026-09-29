package com.beichen.erp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * F7-106（2026-09-20）：<b>{@link ApiPermGuard} 收口一致性自检</b>（启动期，<b>只告警不中断</b>）。
 *
 * <p><b>为什么需要它</b>：{@code ApiPermGuard} 是**白名单式收口** —— {@code EXEMPT / READ_SHARED /
 * RULES / WRITE_RULES} 四张表都没命中时 {@code check()} 直接返回（放行），**没有默认拒绝分支**。
 * 于是"新增一个控制器却忘了登记"就等于**任何登录用户可读写**（F7-105 的
 * {@code /api/inventory/outbound} 与 {@code /api/sale/analysis} 就是这样来的，前者还顺带叠了
 * "出库单重复扣库存"，见报告 §37/§40）。</p>
 *
 * <p><b>做法</b>：启动后遍历 Spring MVC 实际注册的端点，取所有 {@code /api/**} 的路径，
 * 逐个用 {@link ApiPermGuard#isRegistered(String)}（与运行时同一套"路径段最长匹配"语义）判断；
 * 未登记者打印 <b>ERROR</b> 日志并汇总。**不中断启动** —— 因为"默认拒绝"需要先让清单归零
 * （见报告 §40.3 的两步走：先告警、后默认拒绝）。</p>
 *
 * <p><b>注意</b>：这里只校验 <b>控制器前缀</b>（{@code @RequestMapping} 的类级/方法级路径拼接结果）。
 * 动态路径段（如 {@code /{id}}）不影响判断 —— 前缀匹配只看路径段边界。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiPermGuardSelfCheck implements ApplicationRunner {

    /**
     * F7-257（2026-09-30 审核批 G）：**EXEMPT 中确实需要写操作的**前缀白名单（其余前缀出现非 GET 端点即报 ERROR）。
     * <p>逐条理由：`/api/auth`（登录/改密，未登录也要能 POST）· `/api/company`（超管公司 CRUD，控制器内自校验角色）·
     * `/api/memo`（个人备忘录，按当前用户隔离）· `/api/dev/file`（各页附件上传/下载）·
     * `/api/system`（已由 `@SaCheckRole(admin/super_admin)` 保护，见 EXEMPT 注释）。</p>
     */
    private static final Set<String> EXEMPT_WRITE_ALLOWED = Set.of(
            "/api/auth", "/api/company", "/api/memo", "/api/dev/file", "/api/system");

    /**
     * F8-02（2026-09-30 设置模块审核批 A）：**允许出现在 {@code SaTokenConfig.EXEMPT_PATHS}（拦截器排除清单）里的
     * `/api` 路径** —— 排除即"同时绕开登录与权限"，所以每条都必须对应一处"**未登录也必须能调**"的场景，白名单化。
     *
     * <p>新加一条排除却忘了在此登记 ⇒ 启动即 ERROR，避免像 F8-01（`/api/company/list` 匿名可读租户档案）那样静默放开。</p>
     */
    private static final Set<String> EXEMPT_PATH_ALLOWED = Set.of(
            "/api/auth/login",            // 登录
            "/api/auth/captcha",          // 验证码
            "/api/auth/company-name",     // 登录页仅展示公司名
            "/api/company/admin/verify",  // 登录前的超管口令校验
            "/api/company/list",          // 登录页公司下拉（F8-01 修复后已收为 id+companyName 最小投影）
            "/api/system/menu/tree/user"  // 侧栏菜单树（控制器内按当前用户过滤）
    );

    private final RequestMappingHandlerMapping handlerMapping;

    @Override
    public void run(ApplicationArguments args) {
        Map<RequestMappingInfo, ?> handlers = handlerMapping.getHandlerMethods();

        // 去重后按字典序输出，便于人工核对与日志比对
        Set<String> unguarded = new TreeSet<>();
        Set<String> scanned = new LinkedHashSet<>();
        // F7-257（2026-09-30 审核批 G）：**EXEMPT 前缀不得含写端点** —— 新增的第二类断言。
        Set<String> exemptWrites = new TreeSet<>();

        for (RequestMappingInfo info : handlers.keySet()) {
            Set<RequestMethod> methods = methodsOf(info);
            for (String pattern : patternsOf(info)) {
                if (pattern == null || !pattern.startsWith("/api/")) {
                    continue;
                }
                // 去掉路径变量的花括号内容不影响前缀判断（/api/x/{id}/y 的前缀仍是 /api/x）
                scanned.add(pattern);
                if (!ApiPermGuard.isRegistered(pattern)) {
                    unguarded.add(pattern);
                }
                // 白名单里的**写**端点：F7-225（supplier-settlement 两处 POST 裸奔）/ F7-255（分析聚合越权可读）
                // 都是这一类的先例。空 methods（= 任意方法）视为风险写法，同样登记。
                if (ApiPermGuard.isExempt(pattern) && !EXEMPT_WRITE_ALLOWED.contains(exemptPrefixOf(pattern))) {
                    if (methods.isEmpty() || methods.stream().anyMatch(m -> m != RequestMethod.GET)) {
                        exemptWrites.add(pattern + "  [" + (methods.isEmpty() ? "ALL" : methods.toString()) + "]");
                    }
                }
            }
        }

        if (!exemptWrites.isEmpty()) {
            log.error("[perm-selfcheck] EXEMPT 白名单里出现 {} 个**写**端点（白名单判定在 RULES/WRITE_RULES 之前 "
                    + "⇒ 这些写操作对任何登录用户开放，参见 F7-225 / F7-255）：", exemptWrites.size());
            for (String p : exemptWrites) {
                log.error("[perm-selfcheck]   EXEMPT 含写 -> {}", p);
            }
            log.error("[perm-selfcheck] 处理方式：把该前缀移出 EXEMPT、按码登记到 RULES/WRITE_RULES；"
                    + "若确属「登录即可」（如登录/改密/个人备忘录），加入 ApiPermGuardSelfCheck.EXEMPT_WRITE_ALLOWED 白名单并写明理由。");
        }

        // F8-02（2026-09-30 批 A）：拦截器排除清单的**白名单断言** —— 不是"必须落在 EXEMPT 内"，
        // 而是"必须在 EXEMPT_PATH_ALLOWED 显式登记并写明理由"（EXEMPT 前缀写得太宽，宽到 E 了 F8-01）。
        Set<String> strayExempt = new TreeSet<>();
        for (String p : SaTokenConfig.EXEMPT_PATHS) {
            if (p != null && p.startsWith("/api/") && !EXEMPT_PATH_ALLOWED.contains(p)) {
                strayExempt.add(p);
            }
        }
        if (strayExempt.isEmpty()) {
            log.info("[perm-selfcheck] 拦截器排除清单 OK：{} 条 /api 排除项全部在显式白名单内（F8-02）",
                    EXEMPT_PATH_ALLOWED.size());
        } else {
            log.error("[perm-selfcheck] SaTokenConfig.EXEMPT_PATHS 含 {} 个**未登记**的 /api 排除项"
                    + "（排除 = 同时绕开登录与权限，等同匿名开放，参见 F8-01）：", strayExempt.size());
            for (String p : strayExempt) {
                log.error("[perm-selfcheck]   未登记排除 -> {}", p);
            }
            log.error("[perm-selfcheck] 处理方式：确认该路径确需「未登录可调」，再登记到 "
                    + "ApiPermGuardSelfCheck.EXEMPT_PATH_ALLOWED 并写明理由；否则从 SaTokenConfig.EXEMPT_PATHS 移除。");
        }

        if (unguarded.isEmpty()) {
            log.info("[perm-selfcheck] 收口一致性 OK：扫描 {} 个 /api 端点，全部落在已登记前缀内", scanned.size());
            return;
        }

        log.error("[perm-selfcheck] 发现 {} 个**未登记收口**的接口路径（当前实现下任何登录用户均可访问，"
                + "写接口 = 任何登录用户可写）：", unguarded.size());
        for (String p : unguarded) {
            log.error("[perm-selfcheck]   未收口 -> {}", p);
        }
        log.error("[perm-selfcheck] 处理方式：在 ApiPermGuard 的 EXEMPT / RULES / WRITE_RULES 中补登记"
                + "（只读聚合可进 EXEMPT，模块接口进 RULES，基础数据进 WRITE_RULES）；"
                + "补完重启应看到「收口一致性 OK」提示。");
    }

    /** 该端点绑定的 HTTP 方法（空集 = 未限定，即任意方法）。 */
    private Set<RequestMethod> methodsOf(RequestMappingInfo info) {
        try {
            return info.getMethodsCondition().getMethods();
        } catch (Exception ex) {
            return Set.of();
        }
    }

    /** 命中的 EXEMPT 前缀（用于查白名单）。 */
    private String exemptPrefixOf(String pattern) {
        for (String ex : ApiPermGuard.registeredPrefixes()) {
            if (pattern.equals(ex) || pattern.startsWith(ex + "/")) {
                return ex;
            }
        }
        return pattern;
    }

    /** 兼容 Spring 的两套路径条件实现（PathPattern 优先，回退 Ant 风格）。 */
    private Set<String> patternsOf(RequestMappingInfo info) {
        Set<String> out = new LinkedHashSet<>();
        try {
            PathPatternsRequestCondition pp = info.getPathPatternsCondition();
            if (pp != null) {
                out.addAll(pp.getPatternValues());
                return out;
            }
            PatternsRequestCondition pc = info.getPatternsCondition();
            if (pc != null) {
                out.addAll(pc.getPatterns());
            }
        } catch (Exception ex) {
            // 自检本身绝不能影响启动
            log.warn("[perm-selfcheck] 解析端点路径失败：{}", ex.toString());
        }
        return out;
    }
}
