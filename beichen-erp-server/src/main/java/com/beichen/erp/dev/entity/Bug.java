package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Bug实体
 * <p>severity存储SeverityType枚举code，bugType存储BugTypeEnum枚举code，status存储BugStatus枚举code</p>
 */
@Data
@TableName("dev_bug")
public class Bug {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String title;
    private String bugType;
    private String severity;
    private String status;
    private String description;
    private String foundBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
