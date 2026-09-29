package com.beichen.erp.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.entity.*;
import com.beichen.erp.system.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SysSettingsController {

    private final CompanyMapper companyMapper;
    private final SysParamMapper paramMapper;
    private final OperationLogMapper logMapper;

    // ==================== 公司信息 ====================
    @GetMapping("/company")
    public R<Company> getCompany() {
        Long cid = getCompanyId();
        Company c = companyMapper.selectById(cid);
        if (c == null) throw new BusinessException("公司不存在");
        return R.ok(c);
    }

    @PutMapping("/company")
    public R<Void> updateCompany(@RequestBody Company company) {
        Long cid = getCompanyId();
        company.setId(cid);
        companyMapper.updateById(company);
        return R.ok();
    }

    // ==================== 系统参数 ====================
    @GetMapping("/params")
    public R<List<SysParam>> getParams() {
        // F8-03（2026-09-30 设置模块审核批 A）：**按公司过滤** —— `sys_*` 被 CompanyTenantHandler 显式忽略
        // ⇒ 无插件兜底，必须显式带 company_id，否则多公司下会读到别家公司的参数（此前只有 orderByAsc）。
        return R.ok(paramMapper.selectList(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getCompanyId, getCompanyId())
                .orderByAsc(SysParam::getId)));
    }

    @PutMapping("/params")
    public R<Void> saveParams(@RequestBody List<SysParam> params) {
        Long cid = getCompanyId();
        for (SysParam p : params) {
            if (p.getId() == null) {
                p.setCompanyId(cid);
                paramMapper.insert(p);
            } else {
                // F8-03：改既有行前**校验归属**（原实现按客户端给的 id 直接 updateById ⇒ 可改他公司参数）
                SysParam exist = paramMapper.selectById(p.getId());
                if (exist == null) throw new BusinessException("系统参数不存在（ID=" + p.getId() + "）");
                if (exist.getCompanyId() != null && !exist.getCompanyId().equals(cid))
                    throw new BusinessException("不能修改其他公司的系统参数（ID=" + p.getId() + "）");
                p.setCompanyId(cid);
                paramMapper.updateById(p);
            }
        }
        return R.ok();
    }

    // ==================== 操作日志 ====================
    @GetMapping("/logs")
    public R<Page<OperationLog>> getLogs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long cid = CompanyContext.get();   // F8-03：见下方查询条件
        LambdaQueryWrapper<OperationLog> w = new LambdaQueryWrapper<OperationLog>()
                // F8-03：按公司过滤（`sys_*` 无租户插件兜底）；超管会话 companyId=0 ⇒ 不加条件（可看全部）
                .eq(cid != null && cid > 0, OperationLog::getCompanyId, cid)
                .eq(module != null && !module.isBlank(), OperationLog::getModule, module)
                .like(username != null && !username.isBlank(), OperationLog::getUsername, username)
                .ge(startDate != null && !startDate.isBlank(), OperationLog::getCreateTime, startDate)
                .le(endDate != null && !endDate.isBlank(), OperationLog::getCreateTime, endDate + " 23:59:59")
                .orderByDesc(OperationLog::getId);
        return R.ok(logMapper.selectPage(new Page<>(pageNum, pageSize), w));
    }

    private Long getCompanyId() {
        Long cid = CompanyContext.get();
        if (cid == null || cid == 0) throw new BusinessException("请先选择公司");
        return cid;
    }
}
