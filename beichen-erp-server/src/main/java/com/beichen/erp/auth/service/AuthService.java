package com.beichen.erp.auth.service;

import com.beichen.erp.auth.entity.LoginDTO;
import com.beichen.erp.auth.entity.User;

import java.util.Map;

public interface AuthService {

    /**
     * 登录，返回 token + 用户信息
     */
    Map<String, Object> login(LoginDTO loginDTO);

    /**
     * 登出
     */
    void logout();

    /**
     * 获取当前登录用户
     */
    User getCurrentUser();
}
