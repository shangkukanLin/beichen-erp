package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.beichen.erp.material.common.ProductSpec;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 阶段模板实体
 * <p>productStatusSync标记该阶段完成/跳过时是否需要同步产品状态（研发中→正常）</p>
 * <p>2026-09-21（用户需求）：阶段模板分「**原配 / 改配**」两套 —— 原配只走 7 个阶段
 * （立项/排线打样/背贴盖板打样/总成样品/测试/小批量/结项），改配是原来那 14 个。
 * 立项时按项目 {@code dev_project.spec_type} 自动套用对应的一套（见 {@code ProjectPhaseServiceImpl.initPhase}）。</p>
 */
@Data
@TableName("dev_phase_template")
public class PhaseTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /**
     * 适用规格：{@code MATCHED} 原配 / {@code MODIFIED} 改配。
     * <p>复用产品规格枚举 {@link ProductSpec}（同一套 code，不另造枚举）；列上
     * {@code NOT NULL DEFAULT 'MODIFIED'} ⇒ 存量 14 条自动归入「改配」，与改动前行为一致。</p>
     * <p>唯一键为 {@code uk_company_spec_name(company_id, spec_type, name)} ⇒ **同规格内**阶段名唯一，
     * 跨规格允许同名（两套模板有 7 个同名阶段）。</p>
     */
    private String specType;
    private Integer defaultDays;
    private Integer sortOrder;
    /**
     * 是否触发产品状态同步（研发中→正常）
     * 存储 0/1：0=不同步，1=同步
     */
    private Integer productStatusSync;
    /** productStatusSync 取值为 1 时表示该阶段完成/跳过需同步产品状态 */
    public static final int SYNC_PRODUCT_STATUS = 1;

    /**
     * 项目未填规格（历史项目）时的兜底规格 —— 取「改配」，与改动前那套 14 阶段逐字一致，
     * 保证存量项目与"规格为空"的调用方行为不变。
     */
    public static final String SPEC_DEFAULT = ProductSpec.MODIFIED.getCode();

    /**
     * 归一化规格 code。**只有「原配」被原样保留，其它一切值（含 null/空、以及"原装"与历史脏值）
     * 一律回落「改配」**。
     * <p>⚠️ 之所以不抛异常：存量项目 {@code spec_type} 为空是常态，套模板时必须当作"改配"。
     * 之所以把「原装」也归到改配：阶段模板**只有原配/改配两套**，若原样返回"原装"，
     * 该规格下查不到任何模板 ⇒ 项目会被静默建出 **0 个阶段**（阶段护栏与结项推导随之失效）。</p>
     */
    public static String normalizeSpecType(String specType) {
        ProductSpec s = ProductSpec.fromCode(specType);
        return s == ProductSpec.MATCHED ? ProductSpec.MATCHED.getCode() : SPEC_DEFAULT;
    }

    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
