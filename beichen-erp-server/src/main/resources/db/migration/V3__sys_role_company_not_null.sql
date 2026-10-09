-- =====================================================================================
-- V3 (2026-10-08) sys_role.company_id 收紧为 NOT NULL DEFAULT 0
--
-- 【背景】角色自 S-9②（2026-09-30 批 C）起按「company_id = 当前公司」隔离查询
--   （见 RoleServiceImpl.listEnabled / RoleController.page），而 company_id 原先**可空** ⇒
--   一旦写入 NULL，该角色在**任何公司视角下都查不到**，表现为用户投诉的
--   「角色管理里角色不见了」（现网曾出现 7 行 NULL 角色，已人工清理）。
--
-- 【为什么 NULL 归到 0 不是语义变更】代码里 NULL 与平台哨兵 0 **本来就是同一语义**：
--   RoleServiceImpl.assertOwned 的判据是 `roleCompany == null || roleCompany.equals(PLATFORM_COMPANY_ID)`
--   —— 两者都按"平台级共享角色"处理。故本脚本把 NULL 归到 0，是让**数据与代码口径一致**。
--
-- 【为什么用增量 V3 而不是改 V1__base.sql】按 docs/数据库演化约定.md §三，未上线期应改 V1 并重建库；
--   但本项目**已上线**，且 V1 已被生产库执行过 —— 改动 V1 会使校验和不匹配 ⇒ Flyway validate 失败、
--   应用起不来。故沿用 V2 的增量口径：不动 V1、不重建库，重启时由 Flyway 自动应用本脚本。
--
-- 【执行时机】重启时由 Flyway 自动应用（先于 DataInitializer 的 @PostConstruct 初始化）。
--   2026-10-08 已人工清理存量 NULL（7 行）⇒ ①② 在本库预期**均为 0 行受影响**。
--
-- 【幂等】可重复执行：① 无匹配行则不删；② 无 NULL 则不更新；③ MODIFY 到同一目标态无副作用。
-- =====================================================================================

-- ① 清掉"游离的重复角色"：company_id 为 NULL、**无人引用**、且与其它角色**同码**的行。
--    为什么必须先删：唯一键是 uk_role_code(company_id, role_code) —— 若公司 0 已存在同码角色，
--    下面 ② 的 UPDATE 会撞唯一键而失败。这类行没有任何引用方，删除无损（同码角色仍然存在）。
DELETE r FROM sys_role r
 WHERE r.company_id IS NULL
   AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.role_id = r.id)
   AND EXISTS (SELECT 1 FROM (SELECT id, role_code FROM sys_role) x
                WHERE x.role_code = r.role_code AND x.id <> r.id);

-- ② 仍残留的 NULL 角色（说明其 role_code 全库唯一）→ 归入平台哨兵 0（与代码语义一致，见文件头）。
UPDATE sys_role SET company_id = 0 WHERE company_id IS NULL;

-- ③ 焊死：此后任何"漏设 company_id"的写入都会**直接报错**，而不是静默生成一条看不见的角色。
--    语义：0 = 平台模板角色（SystemConstants.PLATFORM_COMPANY_ID，仅超管可操作）；
--          真实公司 -> 该公司的自有角色（由 CompanyRoleProvisioner 克隆）。
--
--    ⚠️ 若本语句报错 1138 (Invalid use of NULL value)，说明 ② 之后仍有 NULL ——
--       唯一可能是"被某用户引用、且与公司 0 同码"的 NULL 角色（①② 均处理不到）。处理步骤：
--         SELECT r.* FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id
--          WHERE r.company_id IS NULL;                    -- 先看是谁在用
--         -- 把该用户的授权改指到公司 0 的同码角色 -> 删除这条 NULL 角色 -> 重启应用
ALTER TABLE sys_role
    MODIFY COLUMN company_id BIGINT NOT NULL DEFAULT 0 COMMENT '公司ID（0=平台模板）';
