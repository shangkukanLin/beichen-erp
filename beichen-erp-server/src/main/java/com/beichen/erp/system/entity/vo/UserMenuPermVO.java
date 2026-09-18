package com.beichen.erp.system.entity.vo;

import lombok.Data;

import java.util.List;

/**
 * 用户页面权限配置（用户管理「页面权限」弹窗读写用）。
 * <p>menuMode: ROLE=跟随角色（默认）/ CUSTOM=自定义；menuIds 仅在 CUSTOM 时有意义。</p>
 */
@Data
public class UserMenuPermVO {

    /** ROLE（默认）/ CUSTOM */
    private String menuMode;

    /** 自定义页面权限的菜单 id 集合（含勾选菜单及其祖先目录）；menuMode=ROLE 时前端不用它 */
    private List<Long> menuIds;

    /** 该用户当前角色的菜单并集（只读）：前端把开关切到「自定义」时的初始勾选 */
    private List<Long> roleMenuIds;
}
