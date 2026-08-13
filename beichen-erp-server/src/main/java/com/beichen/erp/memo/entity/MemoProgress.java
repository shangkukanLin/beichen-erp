package com.beichen.erp.memo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备忘录进度明细表（个人私有，按 user_id 隔离）
 */
@Data
@TableName("memo_progress")
public class MemoProgress {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联备忘录ID */
    private Long memoId;

    /** 进度内容 */
    private String content;

    /** 所属用户 */
    private Long userId;

    /** 公司ID（多租户隔离，自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
