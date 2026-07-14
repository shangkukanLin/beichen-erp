package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("outsource_contract_template")
public class ContractTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateName;
    private String content;
    private Integer status;
    private Integer isDefault;
    @TableField("template_type")
    private String templateType;
    @TableField("party_a_address")
    private String partyAAddress;
    @TableField("party_a_contact")
    private String partyAContact;
    @TableField("party_a_phone")
    private String partyAPhone;
    private Long companyId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
