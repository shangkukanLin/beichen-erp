package com.beichen.erp.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.auth.entity.LoginDTO;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.auth.service.AuthService;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.common.SystemConstants;
import com.beichen.erp.system.entity.Company;
import com.beichen.erp.system.entity.Menu;
import com.beichen.erp.system.mapper.CompanyMapper;
import com.beichen.erp.system.service.MenuService;
import com.beichen.erp.system.service.RoleService;
import com.beichen.erp.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RoleService roleService;
    private final MenuService menuService;
    private final UserService userService;
    private final CompanyMapper companyMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Map<String, Object> login(LoginDTO loginDTO) {
        // 公司ID为必填入参（LoginDTO已校验非空），按"公司+用户名"唯一定位，避免跨公司越权登录
        Long companyId = loginDTO.getCompanyId();
        String username = loginDTO.getUsername();
        String rawPassword = loginDTO.getPassword();

        // ① 常规路径：按「公司 + 用户名」唯一定位（多租户隔离核心口径，保持原样）
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getCompanyId, companyId));

        // ② 2026-10-10（乙）：**超管跨公司直登**。
        //    用户报障背景："lin 是超级管理员、拥有所有公司的所有权限，新建公司 GD 后却无法用 lin 登录"——
        //    根因是上面那条"公司+用户名"的定位：lin 的用户行归属公司 1，(lin, GD) 查不到 ⇒ 统一提示"用户名或密码错误"
        //    （统一提示是防用户名枚举的刻意设计，会把"账号不属于此公司"伪装成密码错，很难自查）。
        //    现口径：该组合查不到时，若**同名账号持有 super_admin 角色**且**口令正确**，则放行，
        //    并把会话公司设为**所选公司**（等价于登录即完成一次 switchCompany）⇒ 超管可在任何公司像本司账号一样登录。
        //    安全边界（三条，缺一不可）：
        //      - 只对 super_admin 生效：普通账号跨公司登录依旧失败 ⇒ 租户隔离不变；
        //      - **先验口令、再查角色**，且口令错时仍抛与常规路径**完全相同**的提示 ⇒ 不泄露"该用户名存在/是超管"；
        //      - 目标公司必须真实存在且启用 ⇒ 不会登进"空壳公司"会话。
        boolean passwordVerifiedBySuperAdminBranch = false;
        if (user == null) {
            User superAdmin = findSuperAdminByUsername(username);
            if (superAdmin != null && passwordEncoder.matches(rawPassword, superAdmin.getPassword())) {
                // 口令已正确 ⇒ 此刻给出"公司不存在/已停用"的明确提示是安全的（调用者已用凭证自证身份）
                Company target = companyMapper.selectById(companyId);
                if (target == null) {
                    throw new BusinessException("所选公司不存在，请重新选择公司");
                }
                if (target.getStatus() != null && target.getStatus() == 0) {
                    throw new BusinessException("所选公司已停用，无法登录");
                }
                user = superAdmin;
                passwordVerifiedBySuperAdminBranch = true;
                log.info("超管跨公司登录：用户[{}]（归属公司 {}）以公司 {}({}) 为会话上下文登录",
                        username, superAdmin.getCompanyId(), companyId, target.getCompanyName());
            }
        }

        // 统一提示，防止用户名枚举（跨公司分支已在上方比对过口令，此处不重复比对）
        if (user == null || (!passwordVerifiedBySuperAdminBranch
                && !passwordEncoder.matches(rawPassword, user.getPassword()))) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        StpUtil.login(user.getId());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        // 公司上下文写入 **Token-Session（每个 token 独立）** —— ⚠️ 不是 getSession()！
        // 2026-10-10 修复（根因）：`StpUtil.getSession()` 是 **Account-Session —— 按"账号ID"分配的会话，
        //   同一个账号在 PC / APP / 不同浏览器登录时**共用同一个 Session 对象**（Sa-Token 官方口径）；
        //   而 companyId 的语义是"**这一次登录**所选的公司"，属于 token 级状态。
        //   写进 Account-Session 的后果（实测）：超管先以公司1 登录、再以 GD 登录 ⇒ 第二次把**同一个会话对象**
        //   的 companyId 改成 4 ⇒ 公司1 那个 token 也随之只看得到 GD 的数据（供应商 14 条 → 0 条）。
        //   `StpUtil.getTokenSession()` 按 token 区分 ⇒ 多端/多公司上下文互不串扰
        //   （配合 sa-token.is-share=false 才有多个独立 token，两处一起才成立，见 application.yml）。
        //   roles / perms / username 仍留在 Account-Session —— 它们是**账号级**的，各 token 本就应一致。
        StpUtil.getTokenSession().set("companyId", companyId);

        // 查公司名称，存入 session 和 userInfo
        Company company = companyMapper.selectById(companyId);
        String companyName = company != null ? company.getCompanyName() : "";
        StpUtil.getTokenSession().set("companyName", companyName);

        // 查询角色 codes，存入 session 供 @SaCheckRole 使用
        List<String> roleCodes = roleService.getRoleCodesByUserId(user.getId());
        StpUtil.getSession().set("roles", roleCodes);

        // 查询用户有权限的菜单树（角色授权 / 用户级自定义权限，见 MenuService）
        List<Long> roleIds = roleService.getRoleIdsByUserId(user.getId());
        List<Menu> menus = menuService.getMenuTreeByRoleIds(roleIds, user.getId());

        // F3-3（2026-09-18 接口级权限专项）：把"页面级接口权限码"存入 session，供 @SaCheckPermission 使用。
        // 权限口径与菜单同源（看得见的页面 = 调得通的接口）；super_admin 自身不挂菜单 ⇒ 兜底给全量码。
        // 与 roles 一样在登录时快照：改了授权需重新登录才生效（与既有 @SaCheckRole 行为一致）。
        // 方案 A：动作码（:audit/:unaudit/:cancel/:delete）跟随页面自动带出 ⇒ 前端可用它控制按钮显示
        List<String> perms = roleCodes.contains(SystemConstants.SUPER_ADMIN_ROLE_CODE)
                ? menuService.listAllPerms()
                : menuService.collectPermsWithButtons(menus);
        StpUtil.getSession().set("perms", perms);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("phone", user.getPhone());
        userInfo.put("dept", user.getDept());
        userInfo.put("status", user.getStatus());
        userInfo.put("roles", roleCodes);
        userInfo.put("companyId", companyId);
        userInfo.put("companyName", companyName);
        userInfo.put("perms", perms);

        Map<String, Object> result = new HashMap<>();
        result.put("token", tokenInfo.tokenValue);
        result.put("userInfo", userInfo);
        result.put("menus", menus);
        // 首页业务 TAB 勾选（无记录=全部可见，前端按此语义处理）
        result.put("dashboardTabs", userService.getDashboardTabsByUserId(user.getId()));
        return result;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public User getCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    /**
     * 按用户名找出「持有 {@code super_admin} 角色的用户行」—— 仅供登录时的超管跨公司分支使用（见
     * {@link #login}）。返回 null 表示"用户名不存在 / 该用户不是超管"（两者对外**不作区分**，由调用方统一提示）。
     *
     * <p>⚠️ 这里**必须用 {@code selectList} 而不是 {@code selectOne}**：新增公司的能力上线后，同一个用户名
     * （例如 {@code lin}）可以合法地存在于多个公司（公司 4 完全可以自建一个叫 lin 的用户），
     * {@code selectOne} 遇到多行会抛 {@code TooManyResultsException} ⇒ 登录直接 500。
     * 多行时取其中**持有超管角色**的那一行 —— 跨公司直登只对超管开放。</p>
     */
    private User findSuperAdminByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        List<User> candidates = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        for (User candidate : candidates) {
            if (roleService.getRoleCodesByUserId(candidate.getId())
                    .contains(SystemConstants.SUPER_ADMIN_ROLE_CODE)) {
                return candidate;
            }
        }
        return null;
    }
}
