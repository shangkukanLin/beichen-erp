package com.beichen.erp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
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

    private final RequestMappingHandlerMapping handlerMapping;

    @Override
    public void run(ApplicationArguments args) {
        Map<RequestMappingInfo, ?> handlers = handlerMapping.getHandlerMethods();

        // 去重后按字典序输出，便于人工核对与日志比对
        Set<String> unguarded = new TreeSet<>();
        Set<String> scanned = new LinkedHashSet<>();

        for (RequestMappingInfo info : handlers.keySet()) {
            for (String pattern : patternsOf(info)) {
                if (pattern == null || !pattern.startsWith("/api/")) {
                    continue;
                }
                // 去掉路径变量的花括号内容不影响前缀判断（/api/x/{id}/y 的前缀仍是 /api/x）
                scanned.add(pattern);
                if (!ApiPermGuard.isRegistered(pattern)) {
                    unguarded.add(pattern);
                }
            }
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
