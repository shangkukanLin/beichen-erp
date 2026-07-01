package com.beichen.erp.common;

import lombok.Data;

/**
 * 分页参数
 */
@Data
public class PageParam {

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
