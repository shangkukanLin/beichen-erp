package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("dev_project_phase")
public class ProjectPhase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String phaseName;

    /**
     * 来源阶段模板ID（2026-09-21 新增）：生成项目阶段时从模板写入。
     * <p>**治本修法** —— 原先"是否需要同步产品状态"靠**阶段名回查模板**（F7-94：用可变展示名当关联键），
     * 模板一改名就静默失效；改为直查 templateId 后，模板改名/改默认天数都不再影响已生成的阶段。</p>
     * <p>可空：历史行按 (项目规格, 阶段名) 回填，匹配不到的（如"改规格前建的旧项目"里只存在于
     * 另一套模板的阶段）保持为空，此时回落按 (项目规格, 阶段名) 匹配。</p>
     */
    private Long templateId;

    private Integer sortOrder;
    private Integer defaultDays;
    private LocalDate plannedEnd;

    private LocalDate actualEnd;

    /** 备注 */
    private String remark;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
}
