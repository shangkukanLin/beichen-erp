-- ============================================================
-- 北辰ERP 自动建表脚本
-- 启动时由 spring.sql.init 自动执行
-- 所有表使用 InnoDB + utf8mb4
-- ============================================================

-- ==================== 认证模块 ====================

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) UNIQUE NOT NULL COMMENT '登录账号',
    password VARCHAR(100) NOT NULL COMMENT 'BCrypt加密密码',
    phone VARCHAR(20) COMMENT '手机号',
    dept VARCHAR(50) COMMENT '所属部门',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    deleted TINYINT DEFAULT 0 COMMENT '0正常 1已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_company_id (company_id),
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ==================== 系统模块 ====================

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_role_code (role_code),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '菜单ID',
    parent_id BIGINT DEFAULT 0 COMMENT '父菜单ID，0=一级',
    menu_name VARCHAR(50) NOT NULL COMMENT '菜单名称',
    menu_type VARCHAR(20) NOT NULL COMMENT '类型: catalog目录/menu菜单',
    route_path VARCHAR(100) DEFAULT '' COMMENT '路由路径',
    route_name VARCHAR(100) DEFAULT '' COMMENT '路由名称',
    icon VARCHAR(50) DEFAULT '' COMMENT '图标',
    sort_order INT DEFAULT 0 COMMENT '排序',
    visible TINYINT DEFAULT 1 COMMENT '0隐藏 1显示',
    status TINYINT DEFAULT 1 COMMENT '0禁用 1启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_parent_id (parent_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    UNIQUE KEY uk_role_menu (role_id, menu_id),
    INDEX idx_role_id (role_id),
    INDEX idx_menu_id (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

CREATE TABLE IF NOT EXISTS sys_company (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '公司ID',
    company_name VARCHAR(100) NOT NULL COMMENT '公司名称',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公司表';

-- ==================== 品牌模块 ====================

CREATE TABLE IF NOT EXISTS brand (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '品牌ID',
    brand_name VARCHAR(100) NOT NULL COMMENT '品牌名称',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_brand_name_company (brand_name, company_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌表';

-- ==================== 物料模块 ====================
-- material 表已废弃，物料主表统一使用 outsource_material 和 product

-- ==================== 供应商模块 ====================

CREATE TABLE IF NOT EXISTS supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '供应商ID',
    code VARCHAR(50) NOT NULL COMMENT '供应商编码',
    name VARCHAR(100) NOT NULL COMMENT '供应商名称',
    -- supplier_type 已拆分为 supplier_type_ref 中间表
    contact VARCHAR(50) COMMENT '联系人',
    phone VARCHAR(20) COMMENT '手机号',
    address VARCHAR(200) COMMENT '地址',
    status TINYINT DEFAULT 1 COMMENT '1合作中 0已停用',
    has_display TINYINT DEFAULT 0 COMMENT '支持显示方案',
    has_touch TINYINT DEFAULT 0 COMMENT '支持触摸方案',
    related_supplier_id BIGINT DEFAULT NULL COMMENT '关联供应商ID',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_company_id (company_id),
    INDEX idx_related_supplier_id (related_supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表';

CREATE TABLE IF NOT EXISTS supplier_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    product_name VARCHAR(100) NOT NULL COMMENT '产品名称',
    spec VARCHAR(100) COMMENT '规格型号',
    unit VARCHAR(20) COMMENT '单位',
    unit_price DECIMAL(18,4) COMMENT '参考单价',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商产品表';

-- ==================== 外协模块 ====================

CREATE TABLE IF NOT EXISTS outsource_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订单ID',
    code VARCHAR(50) NOT NULL COMMENT '订单编号',
    factory_id BIGINT NOT NULL COMMENT '外协工厂ID',
    plan_start_date DATE COMMENT '计划开始日期',
    plan_end_date DATE COMMENT '计划结束日期',
    actual_start_date DATE COMMENT '实际开始日期',
    actual_end_date DATE COMMENT '实际结束日期',
    status VARCHAR(20) DEFAULT '待处理' COMMENT '状态',
    tax_included TINYINT DEFAULT 0 COMMENT '0未含税 1含税',
    tax_rate DECIMAL(18,4) DEFAULT 0 COMMENT '税率',
    total_amount DECIMAL(18,4) DEFAULT 0 COMMENT '总金额',
    remark VARCHAR(500) COMMENT '备注',
    attach_url VARCHAR(500) COMMENT '附件URL',
    logistics_company VARCHAR(100) COMMENT '物流公司',
    logistics_no VARCHAR(100) COMMENT '物流单号',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_factory_id (factory_id),
    INDEX idx_company_id (company_id),
    INDEX idx_status (status),
    INDEX idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协订单表';

CREATE TABLE IF NOT EXISTS outsource_order_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    project_id BIGINT COMMENT '项目ID',
    product_name VARCHAR(100) NOT NULL COMMENT '产品名称',
    product_spec VARCHAR(100) COMMENT '产品规格',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order_id (order_id),
    INDEX idx_project_id (project_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协订单产品表';

CREATE TABLE IF NOT EXISTS outsource_order_material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    product_id BIGINT NOT NULL COMMENT '产品ID(关联outsource_order_product)',
    material_id BIGINT COMMENT '物料ID',
    bom_type_id BIGINT DEFAULT NULL COMMENT 'BOM类型ID(关联dev_bom_type.id)',
    unit VARCHAR(20) COMMENT '单位',
    demand_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '需求数量',
    loss_rate DECIMAL(18,4) DEFAULT 0 COMMENT '损耗率',
    delivered_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '已发货数量',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_product_id (product_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协订单物料表';

CREATE TABLE IF NOT EXISTS outsource_order_delivery (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    warehouse_id BIGINT DEFAULT NULL COMMENT '收货仓库ID',
    delivery_date DATE COMMENT '发货日期',
    product_id BIGINT COMMENT '产品ID',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    a_qty DECIMAL(18,4) DEFAULT 0 COMMENT 'A规数量',
    b_qty DECIMAL(18,4) DEFAULT 0 COMMENT 'B规数量',
    c_qty DECIMAL(18,4) DEFAULT 0 COMMENT 'C规数量',
    defect_qty DECIMAL(18,4) DEFAULT 0 COMMENT '不良数量',
    source_type VARCHAR(20) DEFAULT 'DELIVERY' COMMENT '来源类型: DELIVERY普通交货/RETURN_DEFECT委外退货/AFTER_SALE收费售后',
    tracking_no VARCHAR(100) COMMENT '物流单号',
    remark VARCHAR(255) COMMENT '备注',
    attach_url VARCHAR(500) COMMENT '附件URL',
    status VARCHAR(20) DEFAULT 'NORMAL' COMMENT '状态: NORMAL/REVERSED',
    is_reverse TINYINT DEFAULT 0 COMMENT '是否退不良红冲记录：1=退不良(数量为负) / 0=普通交货',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order_id (order_id),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协订单发货记录表';

CREATE TABLE IF NOT EXISTS outsource_delivery (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '发货单ID',
    code VARCHAR(50) NOT NULL COMMENT '发货单号',
    delivery_type VARCHAR(20) COMMENT '发货类型',
    project_id BIGINT COMMENT '项目ID',
    factory_id BIGINT COMMENT '工厂ID',
    from_warehouse_id BIGINT COMMENT '来源仓库ID',
    to_warehouse_id BIGINT COMMENT '目标仓库ID',
    supplier_direct TINYINT DEFAULT 0 COMMENT '0否 1供应商直发',
    supplier_id BIGINT COMMENT '供应商ID',
    logistics_company VARCHAR(100) COMMENT '物流公司',
    logistics_no VARCHAR(100) COMMENT '物流单号',
    delivery_date DATE COMMENT '发货日期',
    contact VARCHAR(50) COMMENT '联系人',
    phone VARCHAR(20) COMMENT '联系电话',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '审核状态: DRAFT草稿/AUDITED已审核/CANCELLED已作废',
    remark VARCHAR(500) COMMENT '备注',
    attach_url VARCHAR(500) COMMENT '附件URL',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_code (code),
    INDEX idx_project_id (project_id),
    INDEX idx_factory_id (factory_id),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协发货单表';

CREATE TABLE IF NOT EXISTS outsource_delivery_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    delivery_id BIGINT NOT NULL COMMENT '发货单ID',
    material_id BIGINT COMMENT '物料ID',
    bom_type_id BIGINT DEFAULT NULL COMMENT 'BOM类型ID(关联dev_bom_type.id)',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    quality_type VARCHAR(20) DEFAULT '良品' COMMENT '良品/不良品',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_delivery_id (delivery_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协发货单明细表';

CREATE TABLE IF NOT EXISTS outsource_material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    project_ids VARCHAR(500) COMMENT '关联项目ID列表(逗号分隔)',
    warehouse_id BIGINT COMMENT '仓库ID',
    material_name VARCHAR(100) NOT NULL COMMENT '物料名称',
    bom_type_id BIGINT DEFAULT NULL COMMENT 'BOM类型ID(关联dev_bom_type.id)',
    spec VARCHAR(100) COMMENT '规格型号',
    supplier_ids VARCHAR(500) COMMENT '关联供应商ID列表(逗号分隔)',
    unit VARCHAR(20) COMMENT '单位',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_company_id (company_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协物料表';

-- ==================== 委外物料订单 ====================

CREATE TABLE IF NOT EXISTS outsource_material_order (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code              VARCHAR(50) NOT NULL               COMMENT '订单号',
    supplier_id       BIGINT                            COMMENT '供应商ID',
    order_type        VARCHAR(10) DEFAULT '采购'         COMMENT '订单类型: 采购/委外',
    target_warehouse_id BIGINT DEFAULT NULL             COMMENT '收货目标仓库',
    delivery_date     DATE                              COMMENT '交货日期',
    status            VARCHAR(20) DEFAULT '待确认'       COMMENT '状态: 待确认/收货中/已完成/已取消',
    remark            VARCHAR(500)                      COMMENT '备注',
    attach_url        VARCHAR(500) DEFAULT NULL          COMMENT '合同附件URL',
    company_id        BIGINT DEFAULT NULL               COMMENT '公司ID',
    finish_time       DATETIME DEFAULT NULL              COMMENT '订单完成时间',
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外物料订单主表';

CREATE TABLE IF NOT EXISTS outsource_material_order_item (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    order_id              BIGINT NOT NULL                COMMENT '订单ID',
    outsource_material_id BIGINT                        COMMENT '外协物料ID',
    bom_type_id           BIGINT DEFAULT NULL           COMMENT 'BOM类型ID(关联dev_bom_type.id)',
    unit                  VARCHAR(20)                   COMMENT '单位',
    order_quantity        DECIMAL(18,4) DEFAULT 0       COMMENT '订购数量',
    received_quantity     DECIMAL(18,4) DEFAULT 0       COMMENT '已收数量',
    defect_returned_qty   DECIMAL(18,4) DEFAULT 0       COMMENT '退不良已退数量',
    unit_price            DECIMAL(18,4) DEFAULT 0       COMMENT '单价',
    amount                DECIMAL(18,4) DEFAULT 0       COMMENT '金额',
    remark                VARCHAR(255)                  COMMENT '备注',
    company_id            BIGINT DEFAULT NULL            COMMENT '公司ID',
    INDEX idx_order_id (order_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外物料订单明细表';

-- ==================== 委外仓库 ====================

CREATE TABLE IF NOT EXISTS outsource_warehouse (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '仓库ID',
    factory_id BIGINT COMMENT '工厂ID（关联 supplier.id）',
    warehouse_name VARCHAR(100) NOT NULL COMMENT '仓库名称',
    address VARCHAR(200) COMMENT '仓库地址',
    contact VARCHAR(50) COMMENT '联系人',
    phone VARCHAR(20) COMMENT '联系电话',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_factory_id (factory_id),
    INDEX idx_company_id (company_id),
    CONSTRAINT fk_warehouse_supplier FOREIGN KEY (factory_id) REFERENCES supplier (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协仓库表';

CREATE TABLE IF NOT EXISTS outsource_warehouse_stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    material_id BIGINT NOT NULL COMMENT '物料ID',
    quality_type VARCHAR(20) DEFAULT '良品' COMMENT '良品/不良品',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '库存数量',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_material_id (material_id),
    UNIQUE KEY uk_warehouse_material_quality (warehouse_id, material_id, quality_type),
    CONSTRAINT fk_stock_warehouse FOREIGN KEY (warehouse_id) REFERENCES outsource_warehouse (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协仓库库存表';

CREATE TABLE IF NOT EXISTS outsource_stock_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    material_id BIGINT NOT NULL COMMENT '物料ID',
    material_name VARCHAR(100) COMMENT '物料名称',
    change_type VARCHAR(20) NOT NULL COMMENT '出库/回滚',
    change_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '变更数量(负数出库,正数回滚)',
    before_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '变更前库存',
    after_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '变更后库存',
    related_order_code VARCHAR(30) COMMENT '关联加工单号',
    related_delivery_id BIGINT COMMENT '关联交货记录ID',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_material_id (material_id),
    INDEX idx_warehouse_material (warehouse_id, material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协仓库库存流水表';

CREATE TABLE IF NOT EXISTS outsource_order_close_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    order_id BIGINT NOT NULL COMMENT '加工单ID',
    close_date DATE COMMENT '结单日期',
    remark VARCHAR(500) COMMENT '备注',
    status VARCHAR(20) DEFAULT '草稿' COMMENT '草稿/已结单',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结单报表主表';

CREATE TABLE IF NOT EXISTS outsource_order_close_report_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    report_id BIGINT NOT NULL COMMENT '报表ID',
    material_id BIGINT COMMENT '物料ID',
    bom_type_id BIGINT DEFAULT NULL COMMENT 'BOM类型ID(关联dev_bom_type.id)',
    unit VARCHAR(20) COMMENT '单位',
    delivered_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '发料数量',
    returned_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '退料总数',
    good_return_qty DECIMAL(18,4) DEFAULT 0 COMMENT '良品退料',
    defect_return_qty DECIMAL(18,4) DEFAULT 0 COMMENT '不良退料',
    shipped_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '出货消耗',
    target_yield_rate DECIMAL(18,4) DEFAULT 0 COMMENT '加工良率%',
    actual_yield_rate DECIMAL(18,4) DEFAULT 0 COMMENT '生产良率%',
    yield_loss DECIMAL(18,4) DEFAULT 0 COMMENT '良率超损%',
    excess_loss_qty DECIMAL(18,4) DEFAULT 0 COMMENT '超损数量',
    remark VARCHAR(255) COMMENT '备注',
    INDEX idx_report_id (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结单报表物料明细';

CREATE TABLE IF NOT EXISTS outsource_return_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code VARCHAR(50) NOT NULL COMMENT '退货单号',
    factory_id BIGINT COMMENT '加工厂ID',
    order_id BIGINT COMMENT '关联加工单ID',
    return_date DATE COMMENT '退货日期',
    status VARCHAR(20) DEFAULT '已确认' COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_factory_id (factory_id),
    INDEX idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外退货单';

CREATE TABLE IF NOT EXISTS outsource_return_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    return_order_id BIGINT NOT NULL COMMENT '退货单ID',
    outsource_material_id BIGINT COMMENT '委外物料ID(关联outsource_material.id)',
    bom_type_id BIGINT DEFAULT NULL COMMENT 'BOM类型ID(关联dev_bom_type.id)',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,4) COMMENT '退回数量',
    unit_price DECIMAL(18,4) COMMENT '加工单价',
    amount DECIMAL(18,4) COMMENT '小计金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT COMMENT '公司ID',
    INDEX idx_return_order_id (return_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外退货明细';

CREATE TABLE IF NOT EXISTS outsource_contract_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '模板ID',
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    content TEXT COMMENT '合同模板内容',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    is_default TINYINT DEFAULT 0 COMMENT '0非默认 1默认模板',
    party_a_address VARCHAR(255) COMMENT '甲方地址',
    party_a_contact VARCHAR(50) COMMENT '甲方联系人',
    party_a_phone VARCHAR(20) COMMENT '甲方联系电话',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协合同模板表';

-- ==================== 库存模块 ====================

CREATE TABLE IF NOT EXISTS inventory_warehouse (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '仓库ID',
    code VARCHAR(50) NOT NULL COMMENT '仓库编码',
    warehouse_name VARCHAR(100) NOT NULL COMMENT '仓库名称',
    warehouse_type VARCHAR(50) COMMENT '仓库类型',
    address VARCHAR(200) COMMENT '仓库地址',
    manager VARCHAR(50) COMMENT '管理员',
    phone VARCHAR(20) COMMENT '联系电话',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存仓库表';

CREATE TABLE IF NOT EXISTS inventory_warehouse_stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    product_id BIGINT DEFAULT NULL COMMENT '产品ID(关联product表)',
    material_id BIGINT DEFAULT NULL COMMENT '物料ID(关联material表)',
    quality_type VARCHAR(20) DEFAULT 'A' COMMENT '品质等级(A/B/C/DEFECT)，对应成品等级 ProductQualityType',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '库存数量',
    available_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '可用数量(预留,目前等于quantity)',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_product_id (product_id),
    INDEX idx_material_id (material_id),
    UNIQUE KEY uk_warehouse_product_quality_company (warehouse_id, product_id, quality_type, company_id),
    UNIQUE KEY uk_warehouse_material_company (warehouse_id, material_id, company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='进销存仓库库存表';

-- ==================== 研发模块 ====================

CREATE TABLE IF NOT EXISTS dev_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '项目ID',
    code VARCHAR(50) NOT NULL COMMENT '项目编号',
    name VARCHAR(100) NOT NULL COMMENT '项目名称',
    assembly_name VARCHAR(100) COMMENT '总成名称',
    display_supplier_name VARCHAR(100) COMMENT '显示方案供应商',
    touch_supplier_name VARCHAR(100) COMMENT '触摸方案供应商',
    adapt_model VARCHAR(100) COMMENT '适配机型',
    original_size VARCHAR(50) COMMENT '原始尺寸',
    original_resolution VARCHAR(50) COMMENT '原始分辨率',
    project_leader_id BIGINT COMMENT '项目负责人ID',
    sample_factory_id BIGINT COMMENT '样品工厂ID',
    outsource_factory_id BIGINT COMMENT '外协工厂ID',
    start_date DATE COMMENT '开始日期',
    expected_end_date DATE COMMENT '预计结束日期',
    actual_end_date DATE COMMENT '实际结束日期',
    status VARCHAR(20) DEFAULT 'IN_PROGRESS' COMMENT '项目状态(时间线自动推导)',
    cancelled_at DATETIME COMMENT '取消时间',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_code (code),
    INDEX idx_status (status),
    INDEX idx_project_leader_id (project_leader_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='研发项目表';

CREATE TABLE IF NOT EXISTS dev_project_timeline (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    status_name VARCHAR(50) NOT NULL COMMENT '节点名称',
    sort_order INT DEFAULT 0 COMMENT '排序',
    planned_end DATE COMMENT '计划完成日期',
    actual_end DATE COMMENT '实际完成日期',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    status VARCHAR(20) COMMENT '状态',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_project_id (project_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目时间线表';

CREATE TABLE IF NOT EXISTS dev_phase_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name VARCHAR(50) NOT NULL COMMENT '阶段名称',
    default_days INT DEFAULT 0 COMMENT '默认天数',
    sort_order INT DEFAULT 0 COMMENT '排序',
    product_status_sync TINYINT(1) DEFAULT 0 COMMENT '是否触发产品状态同步(研发中→正常)',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_name_company (name, company_id),
    INDEX idx_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='研发项目阶段模板表';

-- dev_material 已重命名为 dev_purchase_item

CREATE TABLE IF NOT EXISTS dev_purchase_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    name VARCHAR(100) NOT NULL COMMENT '名称',
    type VARCHAR(30) COMMENT '类型',
    quantity DECIMAL(18,4) DEFAULT 1 COMMENT '数量',
    location VARCHAR(30) COMMENT '存放位置',
    location_detail VARCHAR(200) COMMENT '位置详情',
    purchase_date DATE COMMENT '采购日期',
    amount DECIMAL(18,4) COMMENT '采购金额',
    status VARCHAR(20) DEFAULT '完好' COMMENT '状态: 完好/已损坏/已使用',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_project_id (project_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='研发项目物料表';

CREATE TABLE IF NOT EXISTS dev_bom (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'BOM ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    bom_type_id BIGINT COMMENT 'BOM类型ID',
    outsource_material_id BIGINT COMMENT '关联外协物料ID(outsource_material.id)',
    supplier_id BIGINT COMMENT '供应商ID',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '单套用量',
    loss_rate DECIMAL(18,4) DEFAULT 0 COMMENT '损耗率',
    specification VARCHAR(100) COMMENT '规格型号',
    unit VARCHAR(20) COMMENT '单位',
    version INT DEFAULT 1 COMMENT 'BOM版本号',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_project_id (project_id),
    INDEX idx_supplier_id (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BOM表';

CREATE TABLE IF NOT EXISTS dev_bom_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    type_name VARCHAR(50) NOT NULL COMMENT '类型名称',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BOM类型表';

CREATE TABLE IF NOT EXISTS dev_bug (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Bug ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    code VARCHAR(50) COMMENT 'Bug编号',
    title VARCHAR(200) NOT NULL COMMENT 'Bug标题',
    severity VARCHAR(20) COMMENT '严重程度(SeverityType枚举code)',
    bug_type VARCHAR(50) COMMENT 'Bug类型(BugTypeEnum枚举code)',
    status VARCHAR(20) DEFAULT 'OPEN' COMMENT '状态(BugStatus枚举code)',
    description TEXT COMMENT '描述',
    found_by VARCHAR(100) COMMENT '发现人',
    found_time DATETIME COMMENT '发现时间',
    resolved_time DATETIME COMMENT '解决时间',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_project_id (project_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bug表';

CREATE TABLE IF NOT EXISTS dev_drawing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '图纸ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    doc_name VARCHAR(200) NOT NULL COMMENT '文档名称',
    doc_type VARCHAR(50) COMMENT '文档类型(与doc_name组合作为版本分组)',
    file_url VARCHAR(500) COMMENT '文件URL',
    version_code INT DEFAULT 1 COMMENT '版本号(自动递增)',
    version VARCHAR(50) COMMENT '版本标注(手动填写)',
    remark VARCHAR(255) COMMENT '备注',
    upload_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    INDEX idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图纸/文档表';

-- ==================== 客户模块（进销存） ====================

CREATE TABLE IF NOT EXISTS customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '客户ID',
    code VARCHAR(50) NOT NULL COMMENT '客户编码',
    name VARCHAR(100) NOT NULL COMMENT '客户名称',
    contact VARCHAR(50) COMMENT '联系人',
    phone VARCHAR(20) COMMENT '联系电话',
    address VARCHAR(200) COMMENT '地址',
    credit_period INT DEFAULT 0 COMMENT '账期(天)',
    credit_period_months INT DEFAULT 0 COMMENT '账期(月)',
    credit_limit DECIMAL(18,4) DEFAULT 0 COMMENT '信用额度',
    receivable_balance DECIMAL(18,4) DEFAULT 0 COMMENT '应收余额(冗余汇总)',
    prepaid_balance DECIMAL(18,4) DEFAULT 0 COMMENT '预收余额',
    status TINYINT DEFAULT 1 COMMENT '1合作中 0已停用',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_company_id (company_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户档案表';

-- ==================== 采购模块 ====================

CREATE TABLE IF NOT EXISTS purchase_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '采购订单ID',
    code VARCHAR(50) NOT NULL COMMENT '采购订单号',
    supplier_id BIGINT COMMENT '供应商ID',
    supplier_name VARCHAR(100) COMMENT '供应商名称',
    warehouse_id BIGINT COMMENT '入库仓库ID',
    order_date DATE COMMENT '订单日期',
    status TINYINT DEFAULT 0 COMMENT '状态: 0=草稿 1=已完成 2=已作废',
    tax_included TINYINT DEFAULT 0 COMMENT '0未含税 1含税',
    tax_rate DECIMAL(18,4) DEFAULT 0 COMMENT '税率',
    total_amount DECIMAL(18,4) DEFAULT 0 COMMENT '总金额',
    remark VARCHAR(500) COMMENT '备注',
    auditor_id BIGINT DEFAULT NULL COMMENT '审核人ID',
    auditor_name VARCHAR(50) DEFAULT NULL COMMENT '审核人姓名',
    audit_time DATETIME DEFAULT NULL COMMENT '审核时间',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单表';

CREATE TABLE IF NOT EXISTS purchase_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '采购订单明细ID',
    order_id BIGINT NOT NULL COMMENT '采购订单ID',
    material_id BIGINT COMMENT '物料ID',
    material_code VARCHAR(50) COMMENT '物料编码',
    quality_type VARCHAR(10) DEFAULT 'A' COMMENT '品质等级: A/B/C/DEFECT',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order_id (order_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单明细表';

CREATE TABLE IF NOT EXISTS purchase_inbound (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '采购入库单ID',
    code VARCHAR(50) NOT NULL COMMENT '采购入库单号',
    order_id BIGINT COMMENT '关联采购订单ID',
    supplier_id BIGINT COMMENT '供应商ID',
    supplier_name VARCHAR(100) COMMENT '供应商名称',
    warehouse_id BIGINT COMMENT '入库仓库ID',
    inbound_date DATE COMMENT '入库日期',
    status VARCHAR(20) DEFAULT '草稿' COMMENT '状态: 草稿/已审核/已作废',
    total_amount DECIMAL(18,4) DEFAULT 0 COMMENT '总金额',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_order_id (order_id),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购入库单表';

CREATE TABLE IF NOT EXISTS purchase_inbound_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '采购入库明细ID',
    inbound_id BIGINT NOT NULL COMMENT '采购入库单ID',
    order_item_id BIGINT COMMENT '关联采购订单明细ID',
    material_id BIGINT COMMENT '物料ID',
    material_code VARCHAR(50) COMMENT '物料编码',
    quality_type VARCHAR(10) DEFAULT 'A' COMMENT '品质等级: A/B/C/DEFECT',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_inbound_id (inbound_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购入库明细表';

-- ==================== 成品退货单 ====================

CREATE TABLE IF NOT EXISTS purchase_return (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code            VARCHAR(50) NOT NULL               COMMENT '退货单号',
    supplier_id     BIGINT                            COMMENT '供应商ID',
    warehouse_id    BIGINT                            COMMENT '退货仓库ID',
    return_date     DATE                              COMMENT '退货日期',
    status          INTEGER DEFAULT 1                  COMMENT '状态: 1=草稿 2=已审核 3=已作废',
    total_amount    DECIMAL(18,2) DEFAULT 0            COMMENT '退货总金额',
    remark          VARCHAR(500)                      COMMENT '备注',
    auditor_id      BIGINT                            COMMENT '审核人ID',
    auditor_name    VARCHAR(50)                       COMMENT '审核人姓名',
    audit_time      DATETIME                          COMMENT '审核时间',
    company_id      BIGINT DEFAULT NULL               COMMENT '公司ID',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成品退货单主表';

CREATE TABLE IF NOT EXISTS purchase_return_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键ID',
    return_id    BIGINT NOT NULL                   COMMENT '退货单ID(关联主表)',
    product_id   BIGINT                            COMMENT '产品ID(联查product表)',
    quality_type VARCHAR(10) DEFAULT 'A'           COMMENT '品质等级: A/B/C/DEFECT',
    quantity     DECIMAL(18,4) DEFAULT 0           COMMENT '退货数量',
    unit_price  DECIMAL(18,4) DEFAULT 0           COMMENT '单价',
    amount      DECIMAL(18,4) DEFAULT 0           COMMENT '金额',
    remark      VARCHAR(255)                      COMMENT '备注',
    company_id  BIGINT DEFAULT NULL               COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_return_id (return_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成品退货单明细表';

-- ==================== 销售模块 ====================

CREATE TABLE IF NOT EXISTS sale_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '销售单ID',
    code VARCHAR(50) NOT NULL COMMENT '销售单号',
    customer_id BIGINT COMMENT '客户ID',
    customer_name VARCHAR(100) COMMENT '客户名称',
    warehouse_id BIGINT COMMENT '出库仓库ID',
    order_date DATE COMMENT '订单日期',
    status VARCHAR(20) DEFAULT '草稿' COMMENT '状态: 草稿/已审核/已出库/已作废',
    tax_included TINYINT DEFAULT 0 COMMENT '0未含税 1含税',
    tax_rate DECIMAL(18,4) DEFAULT 0 COMMENT '税率',
    total_amount DECIMAL(18,4) DEFAULT 0 COMMENT '总金额',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_customer_id (customer_id),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售单表';

CREATE TABLE IF NOT EXISTS sale_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '销售单明细ID',
    order_id BIGINT NOT NULL COMMENT '销售单ID',
    material_id BIGINT COMMENT '物料ID',
    material_code VARCHAR(50) COMMENT '物料编码',
    quality_type VARCHAR(10) DEFAULT 'A' COMMENT '品质等级: A/B/C/DEFECT',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order_id (order_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售单明细表';

CREATE TABLE IF NOT EXISTS sale_outbound (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '销售出库单ID',
    code VARCHAR(50) NOT NULL COMMENT '销售出库单号',
    order_id BIGINT COMMENT '关联销售单ID',
    customer_id BIGINT COMMENT '客户ID',
    customer_name VARCHAR(100) COMMENT '客户名称',
    warehouse_id BIGINT COMMENT '出库仓库ID',
    outbound_date DATE COMMENT '出库日期',
    status VARCHAR(20) DEFAULT '草稿' COMMENT '状态: 草稿/已审核/已作废',
    total_amount DECIMAL(18,4) DEFAULT 0 COMMENT '总金额',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_order_id (order_id),
    INDEX idx_customer_id (customer_id),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售出库单表';

CREATE TABLE IF NOT EXISTS sale_outbound_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '销售出库明细ID',
    outbound_id BIGINT NOT NULL COMMENT '销售出库单ID',
    order_item_id BIGINT COMMENT '关联销售单明细ID',
    material_id BIGINT COMMENT '物料ID',
    material_code VARCHAR(50) COMMENT '物料编码',
    quality_type VARCHAR(10) DEFAULT 'A' COMMENT '品质等级: A/B/C/DEFECT',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_outbound_id (outbound_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售出库明细表';

-- ==================== 销售退货单（客户退回不良品，入库增库存） ====================

CREATE TABLE IF NOT EXISTS sale_return (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '销售退货单ID',
    code VARCHAR(30) COMMENT '退货单号',
    customer_id BIGINT COMMENT '客户ID',
    customer_name VARCHAR(100) COMMENT '客户名称',
    warehouse_id BIGINT COMMENT '退货入库仓库ID',
    return_date DATE COMMENT '退货日期',
    status INT DEFAULT 0 COMMENT '状态: 0=草稿 1=已审核 2=已作废',
    total_amount DECIMAL(18,2) DEFAULT 0 COMMENT '退货总金额',
    remark VARCHAR(500) COMMENT '备注',
    auditor_id BIGINT COMMENT '审核人ID',
    auditor_name VARCHAR(50) COMMENT '审核人姓名',
    audit_time DATETIME COMMENT '审核时间',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_customer_id (customer_id),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售退货单主表';

CREATE TABLE IF NOT EXISTS sale_return_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '退货单明细ID',
    return_id BIGINT COMMENT '退货单ID',
    product_id BIGINT COMMENT '产品ID',
    product_name VARCHAR(100) COMMENT '产品名称',
    quality_type VARCHAR(10) DEFAULT 'DEFECT' COMMENT '品质等级: 固定DEFECT(不良品)',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '退货数量',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,2) DEFAULT 0 COMMENT '金额',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_return_id (return_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售退货单明细表';

-- ==================== 库存扩展（流水/盘点/调拨/其他出入库） ====================

CREATE TABLE IF NOT EXISTS inventory_stock_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '库存流水ID',
    warehouse_id BIGINT COMMENT '仓库ID',
    product_id BIGINT COMMENT '产品ID',
    quality_type VARCHAR(20) COMMENT '品质等级(A/B/C/DEFECT)',
    change_type VARCHAR(30) COMMENT '变动类型(StockChangeType枚举code: PURCHASE_IN/SALE_OUT/MOVE_OUT等)',
    change_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '变动数量(正入库/负出库)',
    before_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '变动前数量',
    after_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '变动后数量',
    related_bill_no VARCHAR(50) COMMENT '关联单据号',
    related_bill_type VARCHAR(30) COMMENT '关联单据类型',
    related_bill_id BIGINT COMMENT '关联单据ID(用于跳转详情)',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_product_id (product_id),
    INDEX idx_related_bill_no (related_bill_no),
    INDEX idx_company_id (company_id),
    INDEX idx_change_type (change_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存变动流水表';

-- ==================== 成品移仓单主表 ====================
CREATE TABLE IF NOT EXISTS inventory_warehouse_move (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键ID',
    code              VARCHAR(50) NOT NULL               COMMENT '移仓单号(YC-yyyyMMdd-NNN)',
    from_warehouse_id BIGINT                            COMMENT '移出仓库ID',
    to_warehouse_id   BIGINT                            COMMENT '移入仓库ID',
    move_date         DATE                              COMMENT '移仓日期',
    status            VARCHAR(20) DEFAULT '草稿'         COMMENT '状态: 草稿/已审核/已作废',
    remark            VARCHAR(500)                      COMMENT '备注',
    company_id        BIGINT DEFAULT NULL               COMMENT '公司ID',
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_from_warehouse_id (from_warehouse_id),
    INDEX idx_to_warehouse_id (to_warehouse_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成品移仓单主表';

-- ==================== 成品移仓单明细表 ====================
CREATE TABLE IF NOT EXISTS inventory_warehouse_move_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键ID',
    move_id      BIGINT NOT NULL                   COMMENT '移仓单ID(关联主表)',
    product_id   BIGINT                            COMMENT '产品ID(关联产品表)',
    quality_type VARCHAR(10) DEFAULT 'A'           COMMENT '品质等级: A/B/C/DEFECT',
    quantity     DECIMAL(18,4) DEFAULT 0           COMMENT '移仓数量',
    remark      VARCHAR(255)                      COMMENT '备注',
    company_id  BIGINT DEFAULT NULL               COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_move_id (move_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成品移仓单明细表';

-- ==================== 品质重分类单主表 ====================
CREATE TABLE IF NOT EXISTS inventory_stock_reclass (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键ID',
    code              VARCHAR(50) NOT NULL               COMMENT '重分类单号(FL-yyyyMMdd-NNN)',
    warehouse_id      BIGINT NOT NULL                   COMMENT '仓库ID(关联仓库表)',
    reclass_date      DATE NOT NULL                     COMMENT '业务日期',
    status            VARCHAR(20) DEFAULT '草稿'         COMMENT '状态: 草稿/已审核/已作废',
    remark            VARCHAR(500)                      COMMENT '备注',
    company_id        BIGINT DEFAULT NULL               COMMENT '公司ID',
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品质重分类单主表';

-- ==================== 品质重分类单明细表 ====================
CREATE TABLE IF NOT EXISTS inventory_stock_reclass_item (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键ID',
    reclass_id   BIGINT NOT NULL                   COMMENT '重分类单ID(关联主表)',
    product_id   BIGINT                            COMMENT '产品ID(关联产品表)',
    product_name VARCHAR(200) NOT NULL             COMMENT '产品名称',
    from_quality VARCHAR(10) NOT NULL              COMMENT '源等级: A/B/C/DEFECT',
    to_quality   VARCHAR(10) NOT NULL              COMMENT '目标等级: A/B/C/DEFECT',
    quantity     DECIMAL(18,4) DEFAULT 0           COMMENT '重分类数量',
    remark       VARCHAR(255)                     COMMENT '备注',
    company_id   BIGINT DEFAULT NULL              COMMENT '公司ID',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_reclass_id (reclass_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品质重分类单明细表';

CREATE TABLE IF NOT EXISTS inventory_other_io (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '其他出入库单ID',
    code VARCHAR(50) NOT NULL COMMENT '单据号',
    warehouse_id BIGINT COMMENT '仓库ID',
    io_type VARCHAR(20) COMMENT '类型: 其他入库/其他出库',
    io_date DATE COMMENT '业务日期',
    status VARCHAR(20) DEFAULT '草稿' COMMENT '状态: 草稿/已审核/已作废',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_io_type (io_type),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='其他出入库单表';

CREATE TABLE IF NOT EXISTS inventory_other_io_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '其他出入库明细ID',
    other_io_id   BIGINT NOT NULL COMMENT '其他出入库单ID',
    product_id    BIGINT COMMENT '产品ID(名称/规格/单位联查product表)',
    quality_type  VARCHAR(10) DEFAULT 'A' COMMENT '品质等级: A/B/C/DEFECT',
    quantity      DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_other_io_id (other_io_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='其他出入库明细表';

-- ==================== 品质重分类 ====================

CREATE TABLE IF NOT EXISTS product_reclassify (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code            VARCHAR(50) NOT NULL               COMMENT '单号(PC-yyyyMMdd-001)',
    warehouse_id    BIGINT NOT NULL                    COMMENT '仓库ID',
    reclassify_date DATE                               COMMENT '调整日期',
    status          VARCHAR(20) DEFAULT '草稿'         COMMENT '状态: 草稿/已审核/已取消',
    remark          VARCHAR(500)                       COMMENT '备注',
    company_id      BIGINT DEFAULT NULL                COMMENT '公司ID',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品质重分类主表';

CREATE TABLE IF NOT EXISTS product_reclassify_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    reclassify_id   BIGINT NOT NULL                    COMMENT '重分类单ID',
    product_id      BIGINT NOT NULL                    COMMENT '产品ID',
    from_quality    VARCHAR(10) NOT NULL               COMMENT '原品质: A/B/C/DEFECT',
    to_quality      VARCHAR(10) NOT NULL               COMMENT '目标品质: A/B/C/DEFECT',
    quantity        DECIMAL(18,4) DEFAULT 0            COMMENT '调整数量',
    remark          VARCHAR(255)                       COMMENT '备注',
    company_id      BIGINT DEFAULT NULL                COMMENT '公司ID',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_reclassify_id (reclassify_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品质重分类明细表';

-- ==================== 财务模块 ====================

CREATE TABLE IF NOT EXISTS finance_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '账户ID',
    account_name VARCHAR(100) NOT NULL COMMENT '账户名称',
    account_type VARCHAR(20) COMMENT '类型: 现金/银行',
    bank_name VARCHAR(100) COMMENT '开户行',
    account_no VARCHAR(50) COMMENT '账号',
    balance DECIMAL(18,4) DEFAULT 0 COMMENT '账户余额',
    status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_company_id (company_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金账户表';

CREATE TABLE IF NOT EXISTS finance_receivable (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '应收台账ID',
    bill_no VARCHAR(50) NOT NULL COMMENT '单据号',
    customer_id BIGINT COMMENT '客户ID',
    customer_name VARCHAR(100) COMMENT '客户名称',
    source_bill_type VARCHAR(30) COMMENT '来源单据类型: 销售出库/其他应收',
    source_bill_no VARCHAR(50) COMMENT '来源单据号',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '应收金额',
    paid_amount DECIMAL(18,4) DEFAULT 0 COMMENT '已收金额',
    unpaid_amount DECIMAL(18,4) DEFAULT 0 COMMENT '未收金额',
    due_date DATE COMMENT '到期日',
    status VARCHAR(20) DEFAULT '未结清' COMMENT '状态: 未结清/部分结清/已结清',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_bill_no (bill_no),
    INDEX idx_customer_id (customer_id),
    INDEX idx_status (status),
    INDEX idx_source_bill_no (source_bill_no),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应收台账表';

CREATE TABLE IF NOT EXISTS finance_payable (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '应付台账ID',
    bill_no VARCHAR(50) NOT NULL COMMENT '单据号',
    supplier_id BIGINT COMMENT '供应商ID',
    supplier_name VARCHAR(100) COMMENT '供应商名称',
    source_bill_type VARCHAR(30) COMMENT '来源单据类型: 采购入库/其他应付',
    source_bill_no VARCHAR(50) COMMENT '来源单据号',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '应付金额',
    paid_amount DECIMAL(18,4) DEFAULT 0 COMMENT '已付金额',
    unpaid_amount DECIMAL(18,4) DEFAULT 0 COMMENT '未付金额',
    due_date DATE COMMENT '到期日',
    status VARCHAR(20) DEFAULT '未结清' COMMENT '状态: 未结清/部分结清/已结清',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_bill_no (bill_no),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_status (status),
    INDEX idx_source_bill_no (source_bill_no),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应付台账表';

CREATE TABLE IF NOT EXISTS finance_receipt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '收款单ID',
    code VARCHAR(50) NOT NULL COMMENT '收款单号',
    customer_id BIGINT COMMENT '客户ID',
    customer_name VARCHAR(100) COMMENT '客户名称',
    account_id BIGINT COMMENT '收款账户ID',
    account_name VARCHAR(100) COMMENT '收款账户名称',
    receipt_date DATE COMMENT '收款日期',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '收款金额',
    status VARCHAR(20) DEFAULT '草稿' COMMENT '状态: 草稿/已审核/已作废',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_customer_id (customer_id),
    INDEX idx_account_id (account_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收款单表';

CREATE TABLE IF NOT EXISTS finance_receipt_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '收款核销明细ID',
    receipt_id BIGINT NOT NULL COMMENT '收款单ID',
    receivable_id BIGINT COMMENT '应收台账ID',
    receivable_bill_no VARCHAR(50) COMMENT '应收单据号',
    this_amount DECIMAL(18,4) DEFAULT 0 COMMENT '本次核销金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_receipt_id (receipt_id),
    INDEX idx_receivable_id (receivable_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收款核销明细表';

CREATE TABLE IF NOT EXISTS finance_payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '付款单ID',
    code VARCHAR(50) NOT NULL COMMENT '付款单号',
    supplier_id BIGINT COMMENT '供应商ID',
    supplier_name VARCHAR(100) COMMENT '供应商名称',
    account_id BIGINT COMMENT '付款账户ID',
    account_name VARCHAR(100) COMMENT '付款账户名称',
    payment_date DATE COMMENT '付款日期',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '付款金额',
    status VARCHAR(20) DEFAULT '草稿' COMMENT '状态: 草稿/已审核/已作废',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_account_id (account_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款单表';

CREATE TABLE IF NOT EXISTS finance_payment_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '付款核销明细ID',
    payment_id BIGINT NOT NULL COMMENT '付款单ID',
    payable_id BIGINT COMMENT '应付台账ID',
    payable_bill_no VARCHAR(50) COMMENT '应付单据号',
    this_amount DECIMAL(18,4) DEFAULT 0 COMMENT '本次核销金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_payment_id (payment_id),
    INDEX idx_payable_id (payable_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款核销明细表';

CREATE TABLE IF NOT EXISTS finance_cashflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '资金流水ID',
    flow_no VARCHAR(50) NOT NULL COMMENT '流水号',
    account_id BIGINT COMMENT '账户ID',
    account_name VARCHAR(100) COMMENT '账户名称',
    flow_type VARCHAR(20) COMMENT '类型: 收款/付款/其他收入/费用支出',
    related_bill_no VARCHAR(50) COMMENT '关联单据号',
    related_bill_type VARCHAR(30) COMMENT '关联单据类型',
    income DECIMAL(18,4) DEFAULT 0 COMMENT '收入金额',
    expense DECIMAL(18,4) DEFAULT 0 COMMENT '支出金额',
    balance DECIMAL(18,4) DEFAULT 0 COMMENT '账户余额(变动后)',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_account_id (account_id),
    INDEX idx_flow_type (flow_type),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金流水表';

CREATE TABLE IF NOT EXISTS finance_bill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '账单ID',
    bill_no VARCHAR(50) NOT NULL COMMENT '账单号',
    bill_type VARCHAR(20) COMMENT '类型: 应收/应付',
    partner_id BIGINT COMMENT '往来单位ID',
    partner_name VARCHAR(100) COMMENT '往来单位名称',
    period_start DATE COMMENT '账期起',
    period_end DATE COMMENT '账期止',
    total_amount DECIMAL(18,4) DEFAULT 0 COMMENT '应收/应付总额',
    paid_amount DECIMAL(18,4) DEFAULT 0 COMMENT '已收/已付总额',
    unpaid_amount DECIMAL(18,4) DEFAULT 0 COMMENT '未收/未付总额',
    status VARCHAR(20) DEFAULT '未结清' COMMENT '状态: 未结清/已结清',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_bill_no (bill_no),
    INDEX idx_bill_type (bill_type),
    INDEX idx_partner_id (partner_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账单表';

CREATE TABLE IF NOT EXISTS finance_bill_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '账单明细ID',
    bill_id BIGINT NOT NULL COMMENT '账单ID',
    source_bill_type VARCHAR(30) COMMENT '来源单据类型',
    source_bill_no VARCHAR(50) COMMENT '来源单据号',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '金额',
    paid_amount DECIMAL(18,4) DEFAULT 0 COMMENT '已收/已付金额',
    unpaid_amount DECIMAL(18,4) DEFAULT 0 COMMENT '未收/未付金额',
    due_date DATE COMMENT '到期日',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_bill_id (bill_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账单明细表';

-- ==================== 系统参数 ====================

CREATE TABLE IF NOT EXISTS sys_param (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    param_key   VARCHAR(100) NOT NULL               COMMENT '参数键',
    param_value VARCHAR(500)                        COMMENT '参数值',
    remark      VARCHAR(255)                        COMMENT '备注',
    company_id  BIGINT DEFAULT NULL                 COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_key_company (param_key, company_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数表';

CREATE TABLE IF NOT EXISTS sys_operation_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id     BIGINT                              COMMENT '用户ID',
    username    VARCHAR(50)                         COMMENT '用户名',
    module      VARCHAR(50)                         COMMENT '操作模块',
    operation   VARCHAR(50)                         COMMENT '操作类型',
    target      VARCHAR(200)                        COMMENT '操作目标',
    detail      VARCHAR(1000)                       COMMENT '操作详情',
    ip          VARCHAR(50)                         COMMENT 'IP地址',
    company_id  BIGINT DEFAULT NULL                 COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ==================== 供应商类型关联 ====================

CREATE TABLE IF NOT EXISTS supplier_type_ref (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    supplier_id BIGINT NOT NULL                     COMMENT '供应商ID',
    type_code   VARCHAR(50) NOT NULL                COMMENT '类型编码',
    company_id  BIGINT DEFAULT NULL                 COMMENT '公司ID',
    UNIQUE KEY uk_supplier_type (supplier_id, type_code),
    INDEX idx_supplier_id (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商类型关联表';

-- ==================== 外协其他出入库 ====================

CREATE TABLE IF NOT EXISTS outsource_other_io (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code        VARCHAR(50)                         COMMENT '单据号',
    warehouse_id BIGINT                             COMMENT '仓库ID',
    io_type     VARCHAR(20)                         COMMENT '类型: 入库/出库',
    io_date     DATE                                COMMENT '业务日期',
    status      VARCHAR(20) DEFAULT '已确认'         COMMENT '状态',
    remark      VARCHAR(500)                        COMMENT '备注',
    company_id  BIGINT DEFAULT NULL                 COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协其他出入库主表';

CREATE TABLE IF NOT EXISTS outsource_other_io_item (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    other_io_id             BIGINT NOT NULL          COMMENT '关联单据ID',
    outsource_material_id   BIGINT                  COMMENT '外协物料ID',
    bom_type_id             BIGINT DEFAULT NULL     COMMENT 'BOM类型ID(关联dev_bom_type.id)',
    unit                    VARCHAR(20)              COMMENT '单位',
    quantity                DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    unit_price              DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    remark                  VARCHAR(255)            COMMENT '备注',
    company_id              BIGINT DEFAULT NULL      COMMENT '公司ID',
    INDEX idx_other_io_id (other_io_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协其他出入库明细表';

-- ==================== 外协物料组件 ====================

CREATE TABLE IF NOT EXISTS outsource_material_component (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    parent_outsource_material_id    BIGINT NOT NULL   COMMENT '父物料ID',
    child_outsource_material_id     BIGINT NOT NULL   COMMENT '子物料ID',
    quantity                        DECIMAL(18,4) DEFAULT 1 COMMENT '数量',
    loss_rate                       DECIMAL(18,4) DEFAULT 0 COMMENT '损耗率',
    remark                          VARCHAR(255)    COMMENT '备注',
    company_id                      BIGINT DEFAULT NULL COMMENT '公司ID',
    UNIQUE KEY uk_parent_child (parent_outsource_material_id, child_outsource_material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协物料组件表';

-- ==================== 成品表 ====================

CREATE TABLE IF NOT EXISTS product (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '产品ID',
    name            VARCHAR(100) NOT NULL              COMMENT '产品名称',
    brand_id        BIGINT DEFAULT NULL               COMMENT '品牌ID',
    category        VARCHAR(30)                       COMMENT '分类',
    spec            VARCHAR(100)                      COMMENT '规格型号',
    general_model   VARCHAR(100) DEFAULT NULL          COMMENT '通用型号(适用多款机型)',
    unit            VARCHAR(20) DEFAULT 'pcs'          COMMENT '单位',
    safety_stock    DECIMAL(18,4) DEFAULT 0           COMMENT '安全库存',
    current_stock   DECIMAL(18,4) DEFAULT 0           COMMENT '当前库存',
    status          VARCHAR(20) DEFAULT 'NORMAL'         COMMENT '状态: NORMAL/DISCONTINUED/DEVELOPING',
    project_id      BIGINT DEFAULT NULL               COMMENT '关联项目ID',
    remark          VARCHAR(255)                      COMMENT '备注',
    company_id      BIGINT DEFAULT NULL               COMMENT '公司ID',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成品主数据表';
