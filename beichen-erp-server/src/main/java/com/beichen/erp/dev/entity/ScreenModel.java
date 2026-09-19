package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 屏幕资料知识库：行业机型的屏幕参数资料（折叠屏 / 直板 AMOLED）。
 * <p>
 * 属于行业基础资料、不是业务数据：清空数据时一律保留（见 ClearController 的排除说明）。
 * <p>
 * F7-99（2026-09-19）：本表为**各公司共享的单一知识库**，不参与租户隔离
 * （已加入 {@code CompanyTenantHandler.IGNORE_TABLES}），{@code company_id} 恒为 NULL。
 */

@Data
@TableName("screen_model")
public class ScreenModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 机型类别：FOLD=折叠屏 / AMOLED=直板 */
    private String category;

    private String brand;

    private String model;

    private String screenSize;

    private String resolution;

    private String screenType;

    private String refreshRate;

    /** 以下四项为折叠屏副屏参数，直板机型为空 */
    private String subSize;

    private String subResolution;

    private String subScreenType;

    private String subRefreshRate;

    private String fingerprint;

    private String panelSupplier;

    private String releaseDate;

    private String remark;

    /**
     * 公司ID：F7-99（2026-09-19）起本表为**行业共享**（不参与租户隔离），该列恒为 NULL；
     * 去掉 {@code FieldFill.INSERT} 避免写入时按登录公司回填。
     */
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
