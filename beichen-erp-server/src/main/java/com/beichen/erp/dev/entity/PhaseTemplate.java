package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dev_phase_template")
public class PhaseTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer defaultDays;
    private Integer sortOrder;
    private String remark;
    private Long companyId;
}
