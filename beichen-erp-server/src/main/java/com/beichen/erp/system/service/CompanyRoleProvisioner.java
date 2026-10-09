package com.beichen.erp.system.service;

import com.beichen.erp.system.common.SystemConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公司角色供给器（S-9② 收口 · 2026-09-30）：把「平台模板角色 → 本公司角色」的克隆逻辑收成**一处**，
 * 供两条路径共用，避免两边各写一份导致口径漂移：
 *
 * <ul>
 *   <li>{@code CompanyServiceImpl.create} —— 超管**新建公司**时立即补齐 ⇒ 新公司开箱即用；</li>
 *   <li>{@code DataInitializer} 启动期 —— **兜底**覆盖"默认公司"（由 {@code initCompany()} 建立，
 *       从未走过新建流程）与历史存量公司。</li>
 * </ul>
 *
 * <p><b>为什么必须有它</b>：角色自 S-9②（2026-09-30 批 C）起按「{@code company_id = 当前公司}」
 * 隔离查询，而 {@code initRoles()} 只写 {@code company_id = 0} 的**平台模板角色** ⇒
 * 任何没有自有角色的公司，其「设置 → 角色管理」列表恒为空（角色既不可见也无法授权）。</p>
 *
 * <p><b>克隆口径</b>：</p>
 * <ol>
 *   <li><b>模板只取平台行</b>（{@code company_id = 0}；唯一键保证每个 {@code role_code} 一行）——
 *       <b>2026-10-08 修正</b>：原口径是"在**全部**角色行里取菜单最完整的那行"，而各公司可以把自己角色的
 *       菜单放宽 ⇒ 新建公司会克隆到那个被放宽的行（实测公司 1 的 {@code finance} 有 64 个菜单、
 *       平台模板仅 14 个，于是新公司财务角色拿到 64 个而不是标准 14 个）。
 *       原先"不能假设 0 号行是全的"的历史顾虑（实测 {@code dev_engineer} 的 0 号行只 1 个菜单、
 *       {@code finance} 的 0 号行 0 个 —— 因为 {@code assignRoleMenus} 只在"该角色尚无任何菜单"时才写入，
 *       先存在的那行赢）**已于同日校正平台行后消除** ⇒"平台行即模板"成立；</li>
 *   <li>**不克隆 super_admin** —— 按 P2-34 它是平台级角色，仅平台账号 {@code lin} 持有，用户管理侧禁止分配；</li>
 *   <li>幂等只增不改（{@code NOT EXISTS} + {@code INSERT IGNORE} 双保险，重复调用无副作用）。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyRoleProvisioner {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 为**单个公司**补齐本公司角色（幂等）。与调用方同事务 —— 新建公司失败时角色一并回滚。
     *
     * @return 本次新克隆的角色行数（0 表示已齐备）
     */
    @Transactional(rollbackFor = Exception.class)
    public int provision(Long companyId) {
        if (companyId == null) {
            return 0;
        }
        return doProvision(companyId, resolveTemplates());
    }

    /**
     * 为**所有公司**补齐（启动期兜底）。
     *
     * <p>刻意**不加 {@code @Transactional}**：本方法在 {@code @PostConstruct} 阶段被调用，
     * 此时事务基础设施未必就绪；且逐公司独立、各自幂等，单家失败不影响其余，下次启动重试即可。</p>
     *
     * @return 实际发生补齐的公司数
     */
    public int provisionAll() {
        List<Long> companyIds;
        try {
            companyIds = jdbcTemplate.queryForList("SELECT id FROM sys_company", Long.class);
        } catch (Exception e) {
            log.warn("[角色按公司补齐] 查询公司列表失败，跳过：{}", e.getMessage());
            return 0;
        }
        Map<String, Long> templates = resolveTemplates();
        int fixed = 0;
        for (Long cid : companyIds) {
            if (cid == null) {
                continue;
            }
            try {
                if (doProvision(cid, templates) > 0) {
                    fixed++;
                }
            } catch (Exception e) {
                log.warn("[角色按公司补齐] 公司 {} 失败（跳过，下次启动重试）：{}", cid, e.getMessage());
            }
        }
        if (fixed > 0) {
            log.info("[S-9②] 已为 {} 个公司补齐本公司角色（修复「角色管理列表为空」）", fixed);
        }
        return fixed;
    }

    /** 实际克隆动作：① 按 role_code 判缺补角色行；② 复制模板行的菜单授权 */
    private int doProvision(Long companyId, Map<String, Long> templates) {
        // ① 克隆缺失的角色行（源：平台模板角色 company_id=0；super_admin 除外）
        int added = jdbcTemplate.update(
                "INSERT INTO sys_role (role_name, role_code, status, remark, company_id, customized_menu) "
                        + "SELECT t.role_name, t.role_code, t.status, t.remark, ?, 0 "
                        + "FROM sys_role t "
                        + "WHERE t.company_id = ? AND t.role_code <> ? "
                        + "AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM sys_role) x "
                        + "                WHERE x.company_id = ? AND x.role_code = t.role_code)",
                companyId, SystemConstants.PLATFORM_COMPANY_ID, SystemConstants.SUPER_ADMIN_ROLE_CODE, companyId);
        // ② 复制菜单授权（按上面算好的「最完整模板行」；uk_role_menu 唯一键 + INSERT IGNORE 兜底幂等）
        for (Map.Entry<String, Long> tpl : templates.entrySet()) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) "
                            + "SELECT nr.id, rm.menu_id FROM sys_role nr "
                            + "JOIN sys_role_menu rm ON rm.role_id = ? "
                            + "WHERE nr.company_id = ? AND nr.role_code = ?",
                    tpl.getValue(), companyId, tpl.getKey());
        }
        if (added > 0) {
            log.info("[S-9②] 公司 {} 新增本公司角色 {} 个（幂等：仅补缺失的角色编码）", companyId, added);
        }
        return added;
    }

    /**
     * 每个 {@code role_code} → **平台模板行** id（{@code company_id = 0}；唯一键保证每个 code 一行）。
     *
     * <p><b>2026-10-08 修正（模板口径唯一化）</b>：原 SQL 在**全部**角色行里取"菜单最多"的那行，
     * 于是某公司把自己角色的菜单放宽后，**新建公司会克隆到那个被放宽的行** —— 实测公司 1 的
     * {@code finance} 有 64 个菜单、平台模板 14 个 ⇒ 新公司财务角色拿到 64 个（标准应为 14）。
     * 现限定平台哨兵行，模板不再受任何公司的定制影响。</p>
     *
     * <p>保留 {@code ORDER BY menu_cnt DESC} 仅作兜底 tiebreak（按唯一键，每个 code 至多一行平台行）。</p>
     */
    private Map<String, Long> resolveTemplates() {
        Map<String, Long> byCode = new LinkedHashMap<>();
        try {
            jdbcTemplate.query(
                    "SELECT r.role_code, r.id, (SELECT COUNT(*) FROM sys_role_menu m WHERE m.role_id = r.id) AS menu_cnt "
                            + "FROM sys_role r WHERE r.company_id = ? AND r.role_code <> ? "
                            + "ORDER BY menu_cnt DESC, r.id ASC",
                    rs -> {
                        // 块体（无返回值）⇒ 只匹配 RowCallbackHandler，避免与 ResultSetExtractor 产生歧义
                        byCode.putIfAbsent(rs.getString("role_code"), rs.getLong("id"));
                    },
                    SystemConstants.PLATFORM_COMPANY_ID, SystemConstants.SUPER_ADMIN_ROLE_CODE);
        } catch (Exception e) {
            log.warn("[S-9②] 查询角色菜单模板失败（补出来的角色可能缺少菜单授权）：{}", e.getMessage());
        }
        return byCode;
    }
}
