package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 阶段模板实体
 * <p>productStatusSync标记该阶段完成/跳过时是否需要同步产品状态（研发中→正常）</p>
 */
@Data
@TableName("dev_phase_template")
public class PhaseTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer defaultDays;
    private Integer sortOrder;
    /**
     * 是否触发产品状态同步（研发中→正常）
     * 存储 0/1：0=不同步，1=同步
     */
    private Integer productStatusSync;
    /** productStatusSync 取值为 1 时表示该阶段完成/跳过需同步产品状态 */
    public static final int SYNC_PRODUCT_STATUS = 1;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
