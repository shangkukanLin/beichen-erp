package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.Bug;
import com.beichen.erp.dev.entity.dto.BugDTO;
import com.beichen.erp.dev.mapper.BugMapper;
import com.beichen.erp.dev.service.BugService;
import com.beichen.erp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BugServiceImpl extends ServiceImpl<BugMapper, Bug> implements BugService {

    private final BugMapper bugMapper;

    @Override
    public List<Bug> listByProject(Long projectId) {
        LambdaQueryWrapper<Bug> wrapper = new LambdaQueryWrapper<Bug>()
                .eq(Bug::getProjectId, projectId)
                .orderByDesc(Bug::getId);
        return bugMapper.selectList(wrapper);
    }

    private String generateCode() {
        String prefix = "BUG";
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String likePattern = prefix + "-" + dateStr;

        LambdaQueryWrapper<Bug> wrapper = new LambdaQueryWrapper<Bug>()
                .likeRight(Bug::getCode, likePattern)
                .orderByDesc(Bug::getCode)
                .last("LIMIT 1");
        Bug last = bugMapper.selectOne(wrapper);

        int seq = 1;
        if (last != null && last.getCode() != null) {
            String code = last.getCode();
            try {
                String numPart = code.substring(code.length() - 3);
                seq = Integer.parseInt(numPart) + 1;
            } catch (Exception e) {
                seq = 1;
            }
        }

        return prefix + "-" + dateStr + String.format("%03d", seq);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(Long projectId, BugDTO dto) {
        Bug bug = new Bug();
        bug.setProjectId(projectId);
        bug.setTitle(dto.getTitle());
        bug.setSeverity(dto.getSeverity());
        bug.setBugType(dto.getBugType());
        bug.setStatus(dto.getStatus());
        bug.setDescription(dto.getDescription());
        bug.setAssignedTo(dto.getAssignedTo());
        bug.setFoundTime(LocalDateTime.now());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) bug.setCompanyId(cid);

        String code = generateCode();
        for (int i = 0; i < 3; i++) {
            try {
                bug.setCode(code);
                bugMapper.insert(bug);
                return;
            } catch (DuplicateKeyException e) {
                if (i >= 2) throw e;
                code = generateCode();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, BugDTO dto) {
        Bug exist = bugMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("Bug不存在");
        }

        Bug bug = new Bug();
        bug.setId(id);
        bug.setTitle(dto.getTitle());
        bug.setSeverity(dto.getSeverity());
        bug.setBugType(dto.getBugType());
        bug.setStatus(dto.getStatus());
        bug.setDescription(dto.getDescription());
        bug.setAssignedTo(dto.getAssignedTo());

        if (dto.getStatus() != null
                && ("已修复".equals(dto.getStatus()) || "已关闭".equals(dto.getStatus()))
                && exist.getResolvedTime() == null) {
            bug.setResolvedTime(LocalDateTime.now());
        } else {
            bug.setResolvedTime(exist.getResolvedTime());
        }

        bugMapper.updateById(bug);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Bug bug = bugMapper.selectById(id);
        if (bug == null) {
            throw new BusinessException("Bug不存在");
        }
        bugMapper.deleteById(id);
    }
}
