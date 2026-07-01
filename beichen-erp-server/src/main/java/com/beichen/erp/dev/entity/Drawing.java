package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dev_drawing")
public class Drawing {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String docName;

    private String docType;

    private String fileUrl;

    private Long fileSize;

    private String version;

    private Long uploadUserId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
