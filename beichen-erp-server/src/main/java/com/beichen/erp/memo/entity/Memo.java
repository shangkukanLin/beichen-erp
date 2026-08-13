package com.beichen.erp.memo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备忘录主表（个人私有，按 user_id 隔离）
 */
@Data
@TableName("memo")
public class Memo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标题 */
    private String title;

    /** 状态: OPEN 进行中 / CLOSED 关闭 */
    private String status;

    /** 所属用户 */
    private Long userId;

    /** 公司ID（多租户隔离，自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
