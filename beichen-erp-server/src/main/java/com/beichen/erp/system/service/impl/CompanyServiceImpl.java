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
        // 原先"优先 super_admin、回退 admin"，在 super_admin 角色尚未创建时恰好一直走回退，问题被掩盖；
        // 自 DataInitializer 建出 super_admin 后，该写法会让**每个新建公司**的管理员拿到**平台级**权限
        // （可整库导出/导入/清空）→ 越租户边界，故改为显式授予 admin。
        // super_admin 为平台能力角色，只由 DataInitializer 授予唯一平台账号 lin，且用户管理禁止分配。
        // S-9②（2026-09-30 批 C · 角色按公司隔离）：新建公司要拿**本公司**的 admin 角色 ——
        // ① 先找本公司 admin（幂等）；
        // ② 没有就从"平台模板角色"（company_id=0 的 admin，保存着完整菜单授权）**克隆一份**到本公司
        //    （角色行 + sys_role_menu 全量复制）；
        // ③ 原实现是全局 `selectOne(role_code='admin')` ⇒ 一旦角色按公司复制出多行，**selectOne 会抛
        //    TooManyResultsException**（新建公司直接 500），故此处必须按公司查询。
        Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, SystemConstants.ADMIN_ROLE_CODE)
                .eq(Role::getCompanyId, company.getId()));
        if (adminRole == null) {
            Role template = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                    .eq(Role::getRoleCode, SystemConstants.ADMIN_ROLE_CODE)
                    .eq(Role::getCompanyId, SystemConstants.PLATFORM_COMPANY_ID));
            if (template != null) {
                adminRole = new Role();
                adminRole.setRoleName(template.getRoleName());
                adminRole.setRoleCode(template.getRoleCode());
                adminRole.setStatus(template.getStatus());
                adminRole.setRemark(template.getRemark());
                adminRole.setCompanyId(company.getId());
                roleMapper.insert(adminRole);
                // 复制平台模板的菜单授权（克隆后新公司管理员立即拥有与平台一致的页面权限）
                jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id) "
                        + "SELECT ?, menu_id FROM sys_role_menu WHERE role_id = ?", adminRole.getId(), template.getId());
            }
        }
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
        // 删除公司关联的用户和角色
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getCompanyId, id));
        for (User u : users) {
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", u.getId());
            userMapper.deleteById(u.getId());
        }
        companyMapper.deleteById(id);
    }
}
