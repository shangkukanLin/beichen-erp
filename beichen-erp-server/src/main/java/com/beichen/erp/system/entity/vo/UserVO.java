package com.beichen.erp.system.entity.vo;

import com.beichen.erp.auth.entity.User;
import com.beichen.erp.system.entity.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 用户视图对象（不暴露 password）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserVO extends User {

    private List<Role> roles;

    /** 首页业务 TAB 勾选（null/空=全部可见） */
    private List<String> dashboardTabs;

    @Override
    @JsonIgnore
    public String getPassword() {
        return super.getPassword();
    }
}
