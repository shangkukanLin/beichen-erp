package com.beichen.erp.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 按用户名查用户（**包含被逻辑删除的行**）。
     * <p>P2-31：逻辑删除不会释放 `sys_user.username` 的唯一索引，创建同名用户前必须先探活；
     * 否则 insert 直接撞唯一键抛 DuplicateKeyException，页面只看到 500「系统异常」。</p>
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    User selectByUsernameIncludeDeleted(@Param("username") String username);

    /**
     * 复活被逻辑删除的用户行（P2-31）。
     * <p>必须用显式 SQL：MyBatis-Plus 的逻辑删除会给 update 语句带上 `deleted = 0` 条件，改不动已删除的行。
     * companyId 传 null 时保留原值。</p>
     */
    @Update("UPDATE sys_user SET deleted = 0, password = #{password}, phone = #{phone}, dept = #{dept}, "
            + "status = #{status}, company_id = IFNULL(#{companyId}, company_id), update_time = NOW() WHERE id = #{id}")
    int reviveUser(@Param("id") Long id, @Param("password") String password, @Param("phone") String phone,
                   @Param("dept") String dept, @Param("status") Integer status, @Param("companyId") Long companyId);
}
