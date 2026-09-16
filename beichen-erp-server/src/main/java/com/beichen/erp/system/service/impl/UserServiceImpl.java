package com.beichen.erp.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.common.SystemConstants;
import com.beichen.erp.system.entity.Menu;
import com.beichen.erp.system.entity.Role;
import com.beichen.erp.system.entity.UserDashboardTab;
import com.beichen.erp.system.entity.UserRole;
import com.beichen.erp.system.entity.dto.ResetPasswordDTO;
import com.beichen.erp.system.entity.dto.UserDTO;
import com.beichen.erp.system.entity.dto.UserQueryDTO;
import com.beichen.erp.system.entity.vo.UserVO;
import com.beichen.erp.system.mapper.MenuMapper;
import com.beichen.erp.system.mapper.RoleMapper;
import com.beichen.erp.system.mapper.UserDashboardTabMapper;
import com.beichen.erp.system.mapper.UserRoleMapper;
import com.beichen.erp.system.service.RoleService;
import com.beichen.erp.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserRoleMapper userRoleMapper;
    private final UserDashboardTabMapper dashboardTabMapper;
    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final RoleService roleService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 当前租户ID（用于"本公司"过滤）。
     * <p>超管上下文的 companyId 是哨兵值 0（见 {@code CompanyContext} / 超管 verify 时写入 0），
     * 而 {@code sys_user} 不参与 MyBatis-Plus 租户插件的自动过滤，只能在此手工过滤。
     * 若直接判 {@code != null}（0 也非 null），超管会被当成"公司0"过滤，导致用户管理页**看不到任何用户**、
     * 也无法给新公司管理员分配角色。故此处把 null 与 0 统一视为"不过滤"。
     */
    private Long tenantCompany() {
        Long cid = CompanyContext.get();
        return (cid == null || cid == 0L) ? null : cid;
    }

    @Override
    public Page<UserVO> page(UserQueryDTO query) {
        // 若指定了 roleId，先查关联表得到 userIds
        List<Long> userIds = null;
        if (query.getRoleId() != null) {
            List<UserRole> urs = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getRoleId, query.getRoleId()));
            userIds = urs.stream().map(UserRole::getUserId).toList();
            if (userIds.isEmpty()) {
                Page<UserVO> emptyPage = new Page<>(query.getPageNum(), query.getPageSize(), 0);
                emptyPage.setRecords(Collections.emptyList());
                return emptyPage;
            }
        }

        Page<User> page = new Page<>(query.getPageNum(), query.getPageSize());
        // 普通公司租户仅可见本公司用户，超管（CompanyContext 为 null）可见全部
        Long currentCompany = tenantCompany();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(currentCompany != null, User::getCompanyId, currentCompany)
                .like(query.getUsername() != null && !query.getUsername().isBlank(),
                        User::getUsername, query.getUsername())
                .like(query.getPhone() != null && !query.getPhone().isBlank(),
                        User::getPhone, query.getPhone())
                .eq(query.getStatus() != null, User::getStatus, query.getStatus())
                .in(userIds != null, User::getId, userIds)
                .orderByDesc(User::getId);
        Page<User> userPage = baseMapper.selectPage(page, wrapper);

        Page<UserVO> result = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        List<UserVO> records = new ArrayList<>();
        for (User u : userPage.getRecords()) {
            UserVO vo = new UserVO();
            BeanUtils.copyProperties(u, vo);
            vo.setRoles(getRolesByUserId(u.getId()));
            records.add(vo);
        }
        result.setRecords(records);
        return result;
    }

    @Override
    public UserVO getUserById(Long id) {
        Long currentCompany = tenantCompany();
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<User>().eq(User::getId, id);
        if (currentCompany != null) {
            w.eq(User::getCompanyId, currentCompany);
        }
        User user = baseMapper.selectOne(w);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        vo.setRoles(getRolesByUserId(id));
        vo.setDashboardTabs(getDashboardTabsByUserId(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createUser(UserDTO dto) {
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessException("密码不能为空");
        }
        // 用户名查重限定本公司内，避免跨公司用户名冲突误判
        Long currentCompany = tenantCompany();
        LambdaQueryWrapper<User> dupWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername());
        if (currentCompany != null) {
            dupWrapper.eq(User::getCompanyId, currentCompany);
        }
        Long count = baseMapper.selectCount(dupWrapper);
        if (count != null && count > 0) {
            throw new BusinessException("用户名已存在");
        }
        // P2-31：username 是全库唯一索引且**不区分逻辑删除** —— 已删除的同名用户仍占位。
        // 口径：同名"重建"= 复活原行（保留 id，历史操作日志/审计关联不丢），并重置为本次提交的密码/资料/角色。
        User removed = baseMapper.selectByUsernameIncludeDeleted(dto.getUsername());
        if (removed != null) {
            int rows = baseMapper.reviveUser(removed.getId(), passwordEncoder.encode(dto.getPassword()),
                    dto.getPhone(), dto.getDept(), dto.getStatus(), currentCompany);
            if (rows == 0) throw new BusinessException("重建用户失败，请重试");
            // 角色与看板页签一律重建（deleteUser 已清空，这里再兜一层，避免异常路径残留）
            userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, removed.getId()));
            dashboardTabMapper.delete(new LambdaQueryWrapper<UserDashboardTab>().eq(UserDashboardTab::getUserId, removed.getId()));
            saveUserRoles(removed.getId(), dto.getRoleIds());
            saveDashboardTabs(removed.getId(), dto.getDashboardTabs());
            log.info("已复活同名删除用户 {}（id={}）", dto.getUsername(), removed.getId());
            return;
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setPhone(dto.getPhone());
        user.setDept(dto.getDept());
        user.setStatus(dto.getStatus());
        // 归属当前租户；超管上下文为 null 时不设置 companyId（留待后续明确）
        user.setCompanyId(currentCompany);
        try {
            baseMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 查重限定本公司、唯一索引却是全库的：同名被其它公司占用时查重放行 → 插入撞键。
            // 不能让它冒泡成 500「系统异常」（P2-31），给出可读提示。
            throw new BusinessException("用户名「" + dto.getUsername() + "」已被占用（可能属于其它公司或历史删除记录），请更换");
        }

        saveUserRoles(user.getId(), dto.getRoleIds());
        saveDashboardTabs(user.getId(), dto.getDashboardTabs());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("用户ID不能为空");
        }
        User exist = baseMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException("用户不存在");
        }
        // 校验归属本公司，防止越权改其他公司用户
        if (tenantCompany() != null && !tenantCompany().equals(exist.getCompanyId())) {
            // 跨公司越权：语义上是"禁止"，返回 403（与 SaToken NotRoleException 一致），而非默认 500
            throw new BusinessException(403, "无权限操作该用户");
        }
        // 不改密码不改 username
        User update = new User();
        update.setId(dto.getId());
        update.setPhone(dto.getPhone());
        update.setDept(dto.getDept());
        update.setStatus(dto.getStatus());
        baseMapper.updateById(update);

        // 替换角色关联
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, dto.getId()));
        saveUserRoles(dto.getId(), dto.getRoleIds());
        saveDashboardTabs(dto.getId(), dto.getDashboardTabs());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        User user = baseMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 校验归属本公司，防止越权删其他公司用户
        if (tenantCompany() != null && !tenantCompany().equals(user.getCompanyId())) {
            // 跨公司越权：语义上是"禁止"，返回 403（与 SaToken NotRoleException 一致），而非默认 500
            throw new BusinessException(403, "无权限操作该用户");
        }
        if (SystemConstants.SUPER_ADMIN_USERNAME.equals(user.getUsername())) {
            throw new BusinessException("超级管理员不可删除");
        }
        baseMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, id));
        dashboardTabMapper.delete(new LambdaQueryWrapper<UserDashboardTab>()
                .eq(UserDashboardTab::getUserId, id));
    }

    @Override
    public void resetPassword(ResetPasswordDTO dto) {
        User user = baseMapper.selectById(dto.getId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 校验归属本公司，防止越权重置其他公司用户密码
        if (tenantCompany() != null && !tenantCompany().equals(user.getCompanyId())) {
            // 跨公司越权：语义上是"禁止"，返回 403（与 SaToken NotRoleException 一致），而非默认 500
            throw new BusinessException(403, "无权限操作该用户");
        }
        User update = new User();
        update.setId(dto.getId());
        update.setPassword(passwordEncoder.encode(dto.getPassword()));
        baseMapper.updateById(update);
    }

    @Override
    public void toggleStatus(Long id) {
        User user = baseMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 校验归属本公司，防止越权禁用其他公司用户
        if (tenantCompany() != null && !tenantCompany().equals(user.getCompanyId())) {
            // 跨公司越权：语义上是"禁止"，返回 403（与 SaToken NotRoleException 一致），而非默认 500
            throw new BusinessException(403, "无权限操作该用户");
        }
        if (SystemConstants.SUPER_ADMIN_USERNAME.equals(user.getUsername())) {
            throw new BusinessException("超级管理员不可禁用");
        }
        User update = new User();
        update.setId(id);
        update.setStatus(user.getStatus() != null && user.getStatus() == 1 ? 0 : 1);
        baseMapper.updateById(update);
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        // 查询超级管理员角色ID，禁止通过用户管理分配
        Role superAdminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, SystemConstants.SUPER_ADMIN_ROLE_CODE));
        Long superAdminId = superAdminRole != null ? superAdminRole.getId() : null;
        for (Long roleId : roleIds) {
            if (superAdminId != null && roleId.equals(superAdminId)) {
                continue; // 跳过 super_admin
            }
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    /**
     * 保存用户首页业务 TAB 勾选（全删全插）。
     * null/空 = 全部可见（直接清空记录，兼容「未配置视为全部」语义）。
     */
    private void saveDashboardTabs(Long userId, List<String> tabs) {
        dashboardTabMapper.delete(new LambdaQueryWrapper<UserDashboardTab>()
                .eq(UserDashboardTab::getUserId, userId));
        if (tabs == null || tabs.isEmpty()) {
            return;
        }
        for (String tab : tabs) {
            if (tab == null || tab.isBlank()) continue;
            UserDashboardTab t = new UserDashboardTab();
            t.setUserId(userId);
            t.setTabKey(tab.trim());
            t.setCompanyId(tenantCompany());
            dashboardTabMapper.insert(t);
        }
    }

    @Override
    public List<String> getDashboardTabsByUserId(Long userId) {
        return dashboardTabMapper.selectList(new LambdaQueryWrapper<UserDashboardTab>()
                .eq(UserDashboardTab::getUserId, userId))
                .stream().map(UserDashboardTab::getTabKey).distinct().toList();
    }

    /**
     * 按角色集合推导首页业务 TAB 默认勾选。
     * 取角色菜单的 route_name 集合，套用与前端 dashboard 相同的「菜单 → TAB」映射规则；多角色取并集。
     */
    @Override
    public List<String> deriveDefaultDashboardTabs(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return Collections.emptyList();
        Set<Long> menuIds = new java.util.LinkedHashSet<>();
        for (Long roleId : roleIds) {
            menuIds.addAll(roleService.getMenuIdsByRoleId(roleId));
        }
        if (menuIds.isEmpty()) return Collections.emptyList();
        Set<String> routeNames = new java.util.HashSet<>();
        for (Menu m : menuMapper.selectBatchIds(menuIds)) {
            if (m.getRouteName() != null && !m.getRouteName().isBlank()) routeNames.add(m.getRouteName());
        }
        // 与 dashboard/index.vue checkUserMenus() 的映射保持一致
        List<String> tabs = new ArrayList<>();
        if (routeNames.contains("DevProject") || routeNames.contains("DevBom")) tabs.add("dev");
        if (routeNames.contains("OutsourceOrder") || routeNames.contains("OutsourceMaterialOrder")) tabs.add("outsource");
        if (routeNames.contains("InventoryPurchase") || routeNames.contains("SupplierManage") || routeNames.contains("OutsourceSupplierManage")) tabs.add("purchase");
        if (routeNames.contains("InventorySale") || routeNames.contains("InventoryCustomer")) tabs.add("sale");
        // 物料仓库（2026-09-16 新增 TAB）：含任一物料收发/其他出入库/报损/自有物料仓 菜单即视为该模块可见
        // 注：委外仓库与成品仓库管理共用 route_name「Warehouse」，故这里不按它判断，避免串号
        if (routeNames.contains("OutsourceDelivery") || routeNames.contains("OutsourceOtherIo")
                || routeNames.contains("OutsourceStockLoss") || routeNames.contains("OutsourceMaterialWarehouse")) {
            tabs.add("materialWarehouse");
        }
        if (routeNames.contains("InventoryStock") || routeNames.contains("Warehouse") || routeNames.contains("ProductManage")) tabs.add("stock");
        if (routeNames.contains("FinanceReceivable") || routeNames.contains("FinancePayable")) tabs.add("finance");
        return tabs;
    }

    private List<Role> getRolesByUserId(Long userId) {
        List<Long> roleIds = roleService.getRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleService.listByIds(roleIds);
    }
}
