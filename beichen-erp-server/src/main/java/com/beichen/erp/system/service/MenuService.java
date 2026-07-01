package com.beichen.erp.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.system.entity.Menu;

import java.util.List;

public interface MenuService extends IService<Menu> {

    /**
     * 构建完整菜单树（两级）
     */
    List<Menu> getMenuTree();

    /**
     * 按角色查菜单树，用于登录返回
     */
    List<Menu> getMenuTreeByRoleIds(List<Long> roleIds);

    /**
     * 所有启用可见的菜单平列表
     */
    List<Menu> getAllEnabledMenus();
}
