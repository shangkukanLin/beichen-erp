package com.beichen.erp.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.entity.Company;
import com.beichen.erp.system.entity.Menu;
import com.beichen.erp.system.entity.Role;
import com.beichen.erp.system.mapper.RoleMapper;
import com.beichen.erp.system.service.CompanyService;
import com.beichen.erp.system.service.MenuService;
import com.beichen.erp.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final RoleService roleService;
    private final MenuService menuService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * F8-04（2026-09-30 设置模块审核批 A）：`/admin/verify` 的**失败计数与锁定** —— 该接口是"用超管口令换取
     * `companyId=0` 超管会话"的入口（未登录可达），原实现无任何频率限制 ⇒ 可无限暴力尝试口令。
     * 口径：同一用户名连续失败 {@link #MAX_VERIFY_FAILURES} 次 ⇒ 锁定 {@link #LOCK_MILLIS}；成功即清零。
     * （内存级实现：单实例有效；多实例部署时应换 Redis/DB 计数。）
     */
    private static final int MAX_VERIFY_FAILURES = 5;
    private static final long LOCK_MILLIS = 10 * 60 * 1000L;
    private static final Map<String, long[]> VERIFY_STATE = new java.util.concurrent.ConcurrentHashMap<>();

    /** 记录一次失败（[0]=累计失败次数，[1]=锁定截止时间戳） */
    private void recordVerifyFailure(String username) {
        if (username == null) return;
        VERIFY_STATE.compute(username, (k, v) -> {
            long[] s = v == null ? new long[]{0, 0} : v;
            s[0]++;
            if (s[0] >= MAX_VERIFY_FAILURES) {
                s[1] = System.currentTimeMillis() + LOCK_MILLIS;
                s[0] = 0;   // 进入锁定后重置计数，锁满释放再重新累计
            }
            return s;
        });
    }

    /**
     * 验证超级管理员凭证，返回 token。
     *
     * <p><b>F8-04</b>：补失败锁定（见 {@link #recordVerifyFailure}）；并把"超管角色缺失"改为**失败关闭**
     * （原 `getSuperAdminRoleId()` 缺行时兜底 `1L`，会把"角色 ID=1"误判为超管）。</p>
     */
    @PostMapping("/admin/verify")
    public R<Map<String, Object>> verifyAdmin(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        long[] state = VERIFY_STATE.get(username == null ? "" : username);
        if (state != null && state[1] > System.currentTimeMillis()) {
            long left = (state[1] - System.currentTimeMillis()) / 1000 + 1;
            throw new BusinessException("失败次数过多，该账号已临时锁定，请 " + left + " 秒后重试");
        }
        User user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (user == null) { recordVerifyFailure(username); throw new BusinessException("账号不存在"); }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            recordVerifyFailure(username);
            throw new BusinessException("密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) throw new BusinessException("账号已被禁用");
        List<Long> roleIds = roleService.getRoleIdsByUserId(user.getId());
        if (!roleIds.contains(getSuperAdminRoleId())) {
            recordVerifyFailure(username);
            throw new BusinessException(403, "无超级管理员权限");
        }
        VERIFY_STATE.remove(username);   // 成功 ⇒ 清零失败计数
        // 登录超管
        StpUtil.login(user.getId());
        // 超管公司ID设为0，不受租户限制（仅用于公司管理页）
        StpUtil.getSession().set("companyId", 0L);
        Map<String, Object> result = new HashMap<>();
        result.put("token", StpUtil.getTokenInfo().tokenValue);
        return R.ok(result);
    }

    /**
     * 超管选择公司进入系统：切换 session companyId 并加载菜单。
     * 调用后前端跳转到 /dashboard，后续请求均以所选公司身份执行（多租户过滤生效）。
     */
    @PostMapping("/switch")
    public R<Map<String, Object>> switchCompany(@RequestBody Map<String, String> body) {
        checkSuperAdmin();
        String companyIdStr = body.get("companyId");
        if (companyIdStr == null || companyIdStr.isBlank()) {
            throw new BusinessException("公司ID不能为空");
        }
        Long companyId = Long.valueOf(companyIdStr);
        // 切换公司上下文
        StpUtil.getSession().set("companyId", companyId);

        long userId = StpUtil.getLoginIdAsLong();
        // 重新加载角色与菜单
        List<String> roleCodes = roleService.getRoleCodesByUserId(userId);
        StpUtil.getSession().set("roles", roleCodes);
        List<Long> roleIds = roleService.getRoleIdsByUserId(userId);
        List<Menu> menus = menuService.getMenuTreeByRoleIds(roleIds, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("companyId", companyId);
        result.put("roles", roleCodes);
        result.put("menus", menus);
        return R.ok(result);
    }

    /**
     * 获取公司列表 —— **按调用者身份分层返回**。
     *
     * <p><b>F8-01（2026-09-30 设置模块审核批 A）</b>：本路径必须允许**登录前**访问（登录页「选择公司」下拉，
     * 见 `SaTokenConfig.EXEMPT_PATHS` 与 `views/login/index.vue:42-45`），因此不能简单要求登录；但原实现
     * **无差别返回整行实体** ⇒ 匿名用户即可拿到全部租户的**税号/电话/地址/联系人**（实测无 token 返回 2 家公司）。</p>
     *
     * <p><b>现口径</b>：非超管（含未登录）只返回 `id + companyName` **最小投影**；仅超管返回完整档案
     * （公司管理页 `views/system/company.vue` 需要 status 等字段）。</p>
     */
    @GetMapping("/list")
    public R<List<Company>> list() {
        List<Company> all = companyService.listAll();
        if (isSuperAdmin()) return R.ok(all);
        List<Company> minimal = new java.util.ArrayList<>(all.size());
        for (Company c : all) {
            Company m = new Company();
            m.setId(c.getId());
            m.setCompanyName(c.getCompanyName());
            minimal.add(m);
        }
        return R.ok(minimal);
    }

    /** 获取单条（F8-01：收口到超管 —— 原先任意登录用户可按 id 读取任意公司全字段） */
    @GetMapping("/{id}")
    public R<Company> getById(@PathVariable Long id) {
        checkSuperAdmin();
        return R.ok(companyService.getById(id));
    }

    /** 当前会话是否超管（未登录 / 会话异常 / 超管角色缺失 ⇒ false，失败关闭） */
    private boolean isSuperAdmin() {
        try {
            if (!StpUtil.isLogin()) return false;
            long userId = StpUtil.getLoginIdAsLong();
            return roleService.getRoleIdsByUserId(userId).contains(getSuperAdminRoleId());
        } catch (Exception e) {
            return false;
        }
    }

    /** 创建公司（仅超管） */
    @PostMapping
    public R<Void> create(@RequestBody Company company) {
        checkSuperAdmin();
        companyService.create(company);
        return R.ok();
    }

    /** 更新公司（仅超管） */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Company company) {
        checkSuperAdmin();
        company.setId(id);
        companyService.update(company);
        return R.ok();
    }

    /** 删除公司（仅超管） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        checkSuperAdmin();
        companyService.delete(id);
        return R.ok();
    }

    private void checkSuperAdmin() {
        long userId = StpUtil.getLoginIdAsLong();
        List<Long> roleIds = roleService.getRoleIdsByUserId(userId);
        if (!roleIds.contains(getSuperAdminRoleId())) throw new BusinessException(403, "仅超级管理员可操作");
    }

    /**
     * 超管角色 ID。
     *
     * <p><b>F8-04（2026-09-30 批 A）</b>：角色行缺失时**不再兜底 `1L`** —— 那会把"角色 ID=1"（可能是普通角色）
     * 误判为超级管理员、从而放大权限。改为**失败关闭**并给出可定位的报错。</p>
     */
    private Long getSuperAdminRoleId() {
        Role superAdmin = roleMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, "super_admin"));
        if (superAdmin == null)
            throw new BusinessException(403, "超级管理员角色未配置（sys_role.role_code = super_admin 缺失），请先修复角色数据");
        return superAdmin.getId();
    }
}
