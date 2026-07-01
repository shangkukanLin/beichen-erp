package com.beichen.erp.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 权限/角色实现
 * 让 @SaCheckRole 从 session 读取登录时存入的角色 codes
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 暂不做按钮权限
        return new ArrayList<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getRoleList(Object loginId, String loginType) {
        try {
            Object roles = StpUtil.getSession().get("roles");
            if (roles instanceof List) {
                return (List<String>) roles;
            }
        } catch (Exception e) {
            // 会话未建立等情况返回空列表
        }
        return new ArrayList<>();
    }
}
