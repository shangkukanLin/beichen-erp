package com.beichen.erp.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;

@Component
public class SeedData implements ApplicationRunner {
    @Autowired private DataSource ds;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = ds.getConnection()) { c.setAutoCommit(false); Statement s = c.createStatement();
            s.execute("INSERT IGNORE INTO sys_company (id, company_name, status) VALUES (1, '北辰科技', 1)");
            s.execute("INSERT IGNORE INTO sys_role (id, role_name, role_code, status, remark) VALUES (1, '超级管理员','super_admin',1,''), (2,'管理员','admin',1,''), (3,'普通用户','user',1,'')");
            s.execute("INSERT IGNORE INTO sys_user (id, username, password, status) VALUES (1, 'lin', '" + new BCryptPasswordEncoder().encode("123") + "', 1)");
            s.execute("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1)");
            c.commit(); s.close();
            System.out.println("Seed: super admin created.");
        } catch (Exception e) { System.out.println("Seed error: " + e.getMessage()); }
    }
}
