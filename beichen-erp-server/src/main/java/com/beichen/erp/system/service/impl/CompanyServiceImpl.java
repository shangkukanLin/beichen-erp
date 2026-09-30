package com.beichen.erp.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.dev.entity.MaterialType;
import com.beichen.erp.dev.mapper.MaterialTypeMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.entity.Company;
import com.beichen.erp.system.entity.Role;
import com.beichen.erp.system.mapper.CompanyMapper;
import com.beichen.erp.system.common.SystemConstants;
import com.beichen.erp.system.mapper.RoleMapper;
import com.beichen.erp.system.service.CompanyRoleProvisioner;
import com.beichen.erp.system.service.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyMapper companyMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final MaterialTypeMapper materialTypeMapper;
    private final JdbcTemplate jdbcTemplate;
    /** S-9②：角色按公司补齐 —— 与启动期兜底共用同一处克隆口径 */
    private final CompanyRoleProvisioner companyRoleProvisioner;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 默认口令哨兵：出现即告警（生产忘了注入 INIT_ADMIN_PASSWORD） */
    private static final String DEFAULT_PASSWORD = "123";

    /** 新公司管理员的初始口令（P0 配置外置）：生产必须注入 INIT_ADMIN_PASSWORD */
    @Value("${app.init.admin-password:123}")
    private String initAdminPassword;

    @Override
    public List<Company> listAll() {
        return companyMapper.selectList(new LambdaQueryWrapper<Company>().orderByAsc(Company::getId));
    }

    @Override
    public Company getById(Long id) {
        return companyMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(Company company) {
        if (company.getCompanyName() == null || company.getCompanyName().isBlank()) {
            throw new BusinessException("公司名称不能为空");
        }
        companyMapper.insert(company);

        // 自动创建公司管理员：admin{公司ID}
        User user = new User();
        user.setUsername("admin" + company.getId());
        user.setPassword(passwordEncoder.encode(initAdminPassword));
        user.setCompanyId(company.getId());
        user.setStatus(1);
        userMapper.insert(user);

        // 分配**租户级** admin 角色（P2-34 口径修正 · 2026-09-14）：
        // 不能"优先 super_admin、回退 admin" —— super_admin 尚未创建时问题被掩盖；一旦存在，
        // 每个新建公司的管理员都会拿到**平台级**权限（可整库导出/导入/清空）⇒ 越租户边界。
        // super_admin 是平台能力角色，只由 DataInitializer 授予唯一平台账号 lin，用户管理侧禁止分配。
        //
        // S-9② 收口（2026-09-30）：角色克隆统一交给 CompanyRoleProvisioner（与启动期兜底**共用同一处口径**），
        // 一次性补齐 admin + 5 个业务角色（各带菜单授权；不克隆平台级 super_admin）。
        // 原先此处**只克隆 admin**，其余 5 个要等下次启动才补 ⇒ 新公司开箱只有 1 个角色；
        // 且原实现用全局 `selectOne(role_code='admin')` 找模板，角色按公司复制出多行后会抛
        // TooManyResultsException（新建公司直接 500）—— 现在模板由组件内部按"授权最完整的行"解析，无此问题。
        companyRoleProvisioner.provision(company.getId());
        // 取回本公司 admin 角色，用于给新建的「admin{公司ID}」账号授权
        Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, SystemConstants.ADMIN_ROLE_CODE)
                .eq(Role::getCompanyId, company.getId()));
        if (adminRole != null) {
            jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)",
                    user.getId(), adminRole.getId());
        }
        if (DEFAULT_PASSWORD.equals(initAdminPassword)) {
            log.warn("安全提示：新公司[{}]管理员 {} 的初始口令为默认值 {}，请提醒其首次登录后修改"
                    + "（生产请注入 INIT_ADMIN_PASSWORD）", company.getCompanyName(), user.getUsername(), DEFAULT_PASSWORD);
        }

        // 初始化默认物料类型
        String[] defaultTypes = com.beichen.erp.common.DefaultMaterialTypes.TYPES;
        for (int i = 0; i < defaultTypes.length; i++) {
            MaterialType bt = new MaterialType();
            bt.setTypeName(defaultTypes[i]);
            bt.setSortOrder(i + 1);
            bt.setStatus(1);
            bt.setIsDefault(1);
            bt.setCompanyId(company.getId());
            materialTypeMapper.insert(bt);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Company company) {
        if (company.getId() == null) throw new BusinessException("公司ID不能为空");
        companyMapper.updateById(company);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // ① 删除公司关联的用户：口径与 UserServiceImpl.delete 对齐 —— 连用户级配置一起清，
        //    否则留下孤儿行（角色关联 / 自定义页面权限 / 首页 TAB）
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getCompanyId, id));
        for (User u : users) {
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", u.getId());
            jdbcTemplate.update("DELETE FROM sys_user_menu WHERE user_id = ?", u.getId());
            jdbcTemplate.update("DELETE FROM sys_user_dashboard_tab WHERE user_id = ?", u.getId());
            userMapper.deleteById(u.getId());
        }
        // ② 删除公司关联的角色（S-9② 补充 · 2026-09-30）：角色自批 C 起按 company_id 隔离，
        //    每家新公司都会自带一套角色（见 CompanyRoleProvisioner）⇒ 删公司必须连**角色与授权**一起清理，
        //    否则 sys_role / sys_role_menu 里会留下孤儿行（实测删除公司后残留 6 个角色 + 其全部授权）。
        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>().eq(Role::getCompanyId, id));
        for (Role r : roles) {
            jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", r.getId());
            roleMapper.deleteById(r.getId());
        }
        companyMapper.deleteById(id);
    }
}
