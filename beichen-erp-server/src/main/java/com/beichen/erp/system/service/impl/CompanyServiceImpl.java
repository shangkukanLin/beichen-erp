package com.beichen.erp.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.entity.Company;
import com.beichen.erp.system.mapper.CompanyMapper;
import com.beichen.erp.system.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyMapper companyMapper;
    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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

        // 自动创建超级管理员：lin / 123
        User user = new User();
        user.setUsername("lin");
        user.setPassword(passwordEncoder.encode("123"));
        user.setCompanyId(company.getId());
        user.setStatus(1);
        userMapper.insert(user);

        // 分配超级管理员角色 (role_id=4)
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, company_id) VALUES (?, 4, ?)",
                user.getId(), company.getId());
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
