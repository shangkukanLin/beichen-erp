package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.Bom;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.dto.ProjectDTO;
import com.beichen.erp.dev.entity.dto.ProjectQueryDTO;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.dev.mapper.BomMapper;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.dev.service.ProjectService;
import com.beichen.erp.dev.service.ProjectTimelineService;
import com.beichen.erp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectTimelineService timelineService;
    private final BomMapper bomMapper;
    private final BomTypeMapper bomTypeMapper;

    @Override
    public Page<Project> page(ProjectQueryDTO query) {
        Page<Project> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .like(query.getName() != null && !query.getName().isBlank(),
                        Project::getName, query.getName())
                .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                        Project::getStatus, query.getStatus())
                .orderByDesc(Project::getId);
        return projectMapper.selectPage(page, wrapper);
    }

    @Override
    public String generateCode() {
        String prefix = "DEV";
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String likePattern = prefix + "-" + dateStr;

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .likeRight(Project::getCode, likePattern)
                .orderByDesc(Project::getCode)
                .last("LIMIT 1");
        Project last = projectMapper.selectOne(wrapper);

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
    public void create(ProjectDTO dto) {
        Project project = new Project();
        BeanUtils.copyProperties(dto, project);
        project.setId(null); // 强制自增
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) project.setCompanyId(cid);
        String code = generateCode();
        for (int i = 0; i < 3; i++) {
            try {
                project.setCode(code);
                projectMapper.insert(project);
                timelineService.initTimeline(project.getId());
                initBom(dto, project.getId());
                return;
            } catch (DuplicateKeyException e) {
                if (i >= 2) throw e;
                code = generateCode();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ProjectDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        Project exist = projectMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException("项目不存在");
        }
        Project project = new Project();
        BeanUtils.copyProperties(dto, project);
        project.setCode(exist.getCode());
        projectMapper.updateById(project);

        // 如果带了 bomData，upsert 更新 BOM 物料（只更新物料名，保留用量等字段）
        if (dto.getBomData() != null && !dto.getBomData().isEmpty()) {
            List<Bom> existingBoms = bomMapper.selectList(
                    new LambdaQueryWrapper<Bom>().eq(Bom::getProjectId, dto.getId()));
            Map<String, Bom> existingMap = existingBoms.stream()
                    .collect(Collectors.toMap(Bom::getMaterialType, b -> b, (a, b) -> a));
            Long cid = CompanyContext.get();
            for (Map.Entry<String, String> entry : dto.getBomData().entrySet()) {
                String typeName = entry.getKey();
                String materialName = entry.getValue();
                if (materialName == null || materialName.isBlank()) continue;
                Bom existBom = existingMap.get(typeName);
                if (existBom != null) {
                    // 已有记录：只更新物料名，保留 quantityPerSet/spec/unit/lossRate 等
                    Bom upd = new Bom();
                    upd.setId(existBom.getId());
                    upd.setMaterialName(materialName);
                    bomMapper.updateById(upd);
                } else {
                    // 新增记录：默认用量=1，损耗率=2%
                    Bom bom = new Bom();
                    bom.setProjectId(dto.getId());
                    bom.setMaterialType(typeName);
                    bom.setMaterialName(materialName);
                    bom.setQuantityPerSet(BigDecimal.ONE);
                    bom.setLossRate(new BigDecimal("2"));
                    if (cid != null && cid > 0) bom.setCompanyId(cid);
                    bomMapper.insert(bom);
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        projectMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        Project update = new Project();
        update.setId(id);
        update.setStatus(status);
        projectMapper.updateById(update);
    }

    private void initBom(ProjectDTO dto, Long projectId) {
        List<BomType> types = bomTypeMapper.selectList(
                new LambdaQueryWrapper<BomType>()
                        .eq(BomType::getStatus, 1)
                        .orderByAsc(BomType::getSortOrder));
        if (types.isEmpty()) {
            log.warn("未找到启用的BOM类型（status=1），BOM未自动生成，projectId={}", projectId);
            return;
        }
        for (BomType bt : types) {
            String value = getMaterialValue(dto, bt.getTypeName());
            addBomIfNotNull(projectId, value, bt.getTypeName());
        }
    }

    private String getMaterialValue(ProjectDTO dto, String typeName) {
        // 从 bomData Map 取值（前端新增/编辑页用此方式传BOM物料）
        if (dto.getBomData() != null && dto.getBomData().containsKey(typeName)) {
            return dto.getBomData().get(typeName);
        }
        return null;
    }

    private void addBomIfNotNull(Long projectId, String value, String materialType) {
        if (value != null && !value.isBlank()) {
            Bom bom = new Bom();
            bom.setProjectId(projectId);
            bom.setMaterialType(materialType);
            bom.setMaterialName(value);
            Long cid = CompanyContext.get();
            if (cid != null && cid > 0) bom.setCompanyId(cid);
            bomMapper.insert(bom);
        }
    }
}
