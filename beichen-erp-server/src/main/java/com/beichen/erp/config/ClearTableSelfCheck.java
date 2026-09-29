package com.beichen.erp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 启动自检：**「清空本公司数据」的表清单是否覆盖了全部含 {@code company_id} 的业务表**（F8-16）。
 *
 * <p>背景：审核批 D（2026-09-30）实测发现 —— 库中 {含 company_id 的 BASE TABLE} = **100 张**，而
 * {@link ClearController} 的手写清单只覆盖 **86 张** ⇒ 采购换货（113/114 行）、收款账户分摊（156 行）、
 * 加工返回单（5/6 行）、物料移库（11/11 行）等**清空后原样残留**，而其主表已被删除 ⇒ 孤儿数据，
 * 用户却以为"已经清干净了"。</p>
 *
 * <p>该类把"清单必须 ⊇ 待清表"变成**启动期断言**：新增业务表却忘了补清单，日志里就会出现
 * {@code [clear-selfcheck] 漏清单}，不必等到下一次人工审核。</p>
 *
 * <p>口径（与 {@link ClearController#PRESERVE_TABLES} 对齐）：</p>
 * <ul>
 *   <li>范围 = {@code information_schema} 里 {@code table_type='BASE TABLE'} 且含 {@code company_id} 列的表；</li>
 *   <li>排除 {@code sys_%}（系统表：公司/用户/角色/菜单/参数/操作日志 —— 清空业务数据时**按设计保留**）；</li>
 *   <li>排除 {@link ClearController#PRESERVE_TABLES}（显式豁免，如屏幕资料知识库 {@code screen_model}）；</li>
 *   <li>清单侧取 {@link ClearController#deletedTables()}（含"按父表归属删除"的表，如
 *       {@code outsource_order_close_report_item} —— 它自身没有 company_id，按父表 company 删）。</li>
 * </ul>
 */
@Slf4j
@Component
public class ClearTableSelfCheck implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public ClearTableSelfCheck(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            check();
        } catch (Exception e) {
            // 自检本身不得影响启动（如 schema 尚未就绪、只读库等）
            log.warn("[clear-selfcheck] 跳过（检查异常）：{}", e.getMessage());
        }
    }

    private void check() {
        List<String> candidates = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND column_name = 'company_id' "
                        + "AND table_name NOT LIKE 'sys\\_%' ORDER BY table_name",
                String.class);
        List<String> baseTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'",
                String.class);
        if (candidates.isEmpty() && baseTables.isEmpty()) {
            log.warn("[clear-selfcheck] 未读到任何表（schema 未就绪？），跳过检查");
            return;
        }
        Set<String> base = new LinkedHashSet<>(baseTables);
        Set<String> covered = ClearController.deletedTables();

        Set<String> missing = new TreeSet<>();
        for (String t : candidates) {
            if (!base.contains(t)) {
                continue;                              // 视图（如 outsource_order_material）不参与
            }
            if (ClearController.PRESERVE_TABLES.contains(t)) {
                continue;                              // 显式保留
            }
            if (!covered.contains(t)) {
                missing.add(t);
            }
        }

        // D-26（2026-09-30 用户口径）：sys_param / sys_operation_log 这两个"带 company_id 的系统表"
        // 也属于本公司数据 ⇒ 必须在清单里。它们被上面的通用规则排除（sys_% 默认保留），故单独**正向断言**，
        // 防止日后有人"顺手清理"清单时把它们去掉。
        Set<String> requiredMissing = new TreeSet<>();
        for (String required : List.of("sys_param", "sys_operation_log")) {
            if (base.contains(required) && !covered.contains(required)) {
                requiredMissing.add(required);
            }
        }
        if (!requiredMissing.isEmpty()) {
            log.error("[clear-selfcheck] 以下表按 2026-09-30 口径（D-26）**必须**纳入「清空本公司数据」清单，"
                    + "但当前缺失：{}（在 ClearController.COMPANY_DELETES 补 \"DELETE FROM <t> WHERE company_id = ?\"）",
                    requiredMissing);
        }

        if (missing.isEmpty() && requiredMissing.isEmpty()) {
            log.info("[clear-selfcheck] 清空清单一致性 OK：含 company_id 的业务表 {} 张，清单覆盖 {} 张（含 sys_param/sys_operation_log）",
                    candidates.size(), covered.size());
        } else {
            log.error("[clear-selfcheck] 有 {} 张含 company_id 的表**不在**「清空本公司数据」清单里（F8-16 同族问题，"
                    + "清空后会残留孤儿数据）：", missing.size());
            for (String t : missing) {
                log.error("[clear-selfcheck]   漏清单 -> {}", t);
            }
            log.error("[clear-selfcheck] 处理方式：在 ClearController.COMPANY_DELETES 补一条 "
                    + "\"DELETE FROM {} WHERE company_id = ?\"（按子表→主表顺序）；"
                    + "若该表确属\"清空时应保留\"（行业基础资料等），则登记进 ClearController.PRESERVE_TABLES 并写明理由。", missing);
        }
    }
}
