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

-- ==================== 物料模块 ====================

CREATE TABLE IF NOT EXISTS material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '物料ID',
    code VARCHAR(50) NOT NULL COMMENT '物料编码',
    name VARCHAR(100) NOT NULL COMMENT '物料名称',
    category VARCHAR(30) COMMENT '分类(原料/辅料/半成品/成品)',
    spec VARCHAR(100) COMMENT '规格型号',
    unit VARCHAR(20) COMMENT '单位',
    safety_stock DECIMAL(18,4) DEFAULT 0 COMMENT '安全库存',
    current_stock DECIMAL(18,4) DEFAULT 0 COMMENT '当前库存',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    remark VARCHAR(255) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料主数据表';

-- ==================== 物料BOM(多级组成) ====================
-- 一个物料可由多个子物料组成；通过 parent_material_id -> child_material_id 的边表示层级关系
-- 仅存储子物料ID，查询时联表取最新名称/规格/单位，子物料修改后BOM自动同步

CREATE TABLE IF NOT EXISTS material_bom (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'BOM组成ID',
    parent_material_id BIGINT NOT NULL COMMENT '父物料ID(成品/半成品)',
    child_material_id BIGINT NOT NULL COMMENT '子物料ID',
    quantity DECIMAL(18,4) DEFAULT 1 COMMENT '单台/单套用量',
    loss_rate DECIMAL(18,4) DEFAULT 0 COMMENT '损耗率',
    remark VARCHAR(255) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_parent_child (parent_material_id, child_material_id),
    INDEX idx_parent (parent_material_id),
    INDEX idx_child (child_material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料BOM组成表(多级)';

-- ==================== 供应商模块 ====================

CREATE TABLE IF NOT EXISTS supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '供应商ID',
    code VARCHAR(50) NOT NULL COMMENT '供应商编码',
    name VARCHAR(100) NOT NULL COMMENT '供应商名称',
    supplier_type VARCHAR(20) NOT NULL COMMENT '类型: solution/factory/product/material',
    contact VARCHAR(50) COMMENT '联系人',
    phone VARCHAR(20) COMMENT '手机号',
    address VARCHAR(200) COMMENT '地址',
    status TINYINT DEFAULT 1 COMMENT '1合作中 0已停用',
    has_display TINYINT DEFAULT 0 COMMENT '支持显示方案',
    has_touch TINYINT DEFAULT 0 COMMENT '支持触摸方案',
    related_supplier_id BIGINT DEFAULT NULL COMMENT '关联供应商ID',
    brand VARCHAR(100) COMMENT '供应品牌',
    material_type VARCHAR(100) COMMENT '供应类型',
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
    material_name VARCHAR(100) COMMENT '物料名称',
    material_type VARCHAR(50) COMMENT '物料类型',
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
    product_name VARCHAR(100) COMMENT '产品名称',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    tracking_no VARCHAR(100) COMMENT '物流单号',
    remark VARCHAR(255) COMMENT '备注',
    attach_url VARCHAR(500) COMMENT '附件URL',
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
    status VARCHAR(20) DEFAULT '待发货' COMMENT '状态',
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
    material_name VARCHAR(100) COMMENT '物料名称',
    material_type VARCHAR(50) COMMENT '物料类型',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
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
    material_type VARCHAR(50) COMMENT '物料类型',
    spec VARCHAR(100) COMMENT '规格型号',
    supplier_name VARCHAR(100) COMMENT '供应商名称',
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

CREATE TABLE IF NOT EXISTS outsource_material_bom (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'BOM组成ID',
    parent_material_id BIGINT NOT NULL COMMENT '父物料ID',
    child_material_id BIGINT NOT NULL COMMENT '子物料ID',
    quantity DECIMAL(18,4) DEFAULT 1 COMMENT '单台/单套用量',
    loss_rate DECIMAL(18,4) DEFAULT 0 COMMENT '损耗率',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_parent_child (parent_material_id, child_material_id),
    INDEX idx_parent (parent_material_id),
    INDEX idx_child (child_material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协物料BOM组成表(多级)';

CREATE TABLE IF NOT EXISTS outsource_warehouse (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '仓库ID',
    factory_id BIGINT COMMENT '工厂ID',
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
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协仓库表';

CREATE TABLE IF NOT EXISTS outsource_warehouse_stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    material_id BIGINT NOT NULL COMMENT '物料ID',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '库存数量',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_material_id (material_id),
    UNIQUE KEY uk_warehouse_material (warehouse_id, material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协仓库库存表';

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
    product_name VARCHAR(100) NOT NULL COMMENT '产品名称',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '库存数量',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_product_name (product_name),
    UNIQUE KEY uk_warehouse_product (warehouse_id, product_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='进销存仓库库存表';

-- ==================== 研发模块 ====================

CREATE TABLE IF NOT EXISTS dev_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '项目ID',
    code VARCHAR(50) NOT NULL COMMENT '项目编号',
    name VARCHAR(100) NOT NULL COMMENT '项目名称',
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
    status VARCHAR(20) DEFAULT '未开始' COMMENT '项目状态',
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
    status VARCHAR(20) COMMENT '状态',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_project_id (project_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目时间线表';

CREATE TABLE IF NOT EXISTS dev_bom (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'BOM ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    supplier_id BIGINT COMMENT '供应商ID',
    material_name VARCHAR(100) NOT NULL COMMENT '物料名称',
    spec VARCHAR(100) COMMENT '规格型号',
    unit VARCHAR(20) COMMENT '单位',
    quantity_per_set DECIMAL(18,4) DEFAULT 0 COMMENT '单套用量',
    loss_rate DECIMAL(18,4) DEFAULT 0 COMMENT '损耗率',
    material_type VARCHAR(50) COMMENT '物料类型',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_project_id (project_id),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_company_id (company_id)
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
    severity VARCHAR(20) COMMENT '严重程度',
    bug_type VARCHAR(50) COMMENT 'Bug类型',
    status VARCHAR(20) DEFAULT '未处理' COMMENT '状态',
    description TEXT COMMENT '描述',
    found_by BIGINT COMMENT '发现人ID',
    assigned_to BIGINT COMMENT '指派人ID',
    found_time DATETIME COMMENT '发现时间',
    resolved_time DATETIME COMMENT '解决时间',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_project_id (project_id),
    INDEX idx_status (status),
    INDEX idx_found_by (found_by),
    INDEX idx_assigned_to (assigned_to),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bug表';

CREATE TABLE IF NOT EXISTS dev_drawing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '图纸ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    doc_name VARCHAR(200) NOT NULL COMMENT '文档名称',
    doc_type VARCHAR(50) COMMENT '文档类型',
    file_url VARCHAR(500) COMMENT '文件URL',
    file_size BIGINT COMMENT '文件大小(字节)',
    version VARCHAR(50) COMMENT '版本号',
    upload_user_id BIGINT COMMENT '上传人ID',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_project_id (project_id),
    INDEX idx_upload_user_id (upload_user_id),
    INDEX idx_company_id (company_id)
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
    status VARCHAR(20) DEFAULT '草稿' COMMENT '状态: 草稿/已审核/已入库/已作废',
    tax_included TINYINT DEFAULT 0 COMMENT '0未含税 1含税',
    tax_rate DECIMAL(18,4) DEFAULT 0 COMMENT '税率',
    total_amount DECIMAL(18,4) DEFAULT 0 COMMENT '总金额',
    remark VARCHAR(500) COMMENT '备注',
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
    material_name VARCHAR(100) COMMENT '物料名称',
    spec VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
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
    material_name VARCHAR(100) COMMENT '物料名称',
    spec VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
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

-- ==================== 销售模块 ====================

CREATE TABLE IF NOT EXISTS sale_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '销售订单ID',
    code VARCHAR(50) NOT NULL COMMENT '销售订单号',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单表';

CREATE TABLE IF NOT EXISTS sale_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '销售订单明细ID',
    order_id BIGINT NOT NULL COMMENT '销售订单ID',
    material_id BIGINT COMMENT '物料ID',
    material_code VARCHAR(50) COMMENT '物料编码',
    material_name VARCHAR(100) COMMENT '物料名称',
    spec VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order_id (order_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单明细表';

CREATE TABLE IF NOT EXISTS sale_outbound (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '销售出库单ID',
    code VARCHAR(50) NOT NULL COMMENT '销售出库单号',
    order_id BIGINT COMMENT '关联销售订单ID',
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
    order_item_id BIGINT COMMENT '关联销售订单明细ID',
    material_id BIGINT COMMENT '物料ID',
    material_code VARCHAR(50) COMMENT '物料编码',
    material_name VARCHAR(100) COMMENT '物料名称',
    spec VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
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

-- ==================== 库存扩展（流水/盘点/调拨/其他出入库） ====================

CREATE TABLE IF NOT EXISTS inventory_stock_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '库存流水ID',
    warehouse_id BIGINT COMMENT '仓库ID',
    material_id BIGINT COMMENT '物料ID',
    material_name VARCHAR(100) COMMENT '物料名称',
    spec VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    change_type VARCHAR(30) COMMENT '变动类型: 采购入库/销售出库/其他入库/其他出库/调拨入/调拨出/盘点溢/盘点损',
    change_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '变动数量(正入库/负出库)',
    before_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '变动前数量',
    after_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '变动后数量',
    related_bill_no VARCHAR(50) COMMENT '关联单据号',
    related_bill_type VARCHAR(30) COMMENT '关联单据类型',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_material_id (material_id),
    INDEX idx_related_bill_no (related_bill_no),
    INDEX idx_company_id (company_id),
    INDEX idx_change_type (change_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存变动流水表';

CREATE TABLE IF NOT EXISTS inventory_stock_take (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '盘点单ID',
    code VARCHAR(50) NOT NULL COMMENT '盘点单号',
    warehouse_id BIGINT COMMENT '仓库ID',
    take_date DATE COMMENT '盘点日期',
    status VARCHAR(20) DEFAULT '草稿' COMMENT '状态: 草稿/已审核',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存盘点单表';

CREATE TABLE IF NOT EXISTS inventory_stock_take_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '盘点明细ID',
    take_id BIGINT NOT NULL COMMENT '盘点单ID',
    material_id BIGINT COMMENT '物料ID',
    material_name VARCHAR(100) COMMENT '物料名称',
    spec VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    book_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '账面数量',
    actual_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '实盘数量',
    profit_loss_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '盘盈(+)/盘亏(-)数量',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_take_id (take_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存盘点明细表';

CREATE TABLE IF NOT EXISTS inventory_transfer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '调拨单ID',
    code VARCHAR(50) NOT NULL COMMENT '调拨单号',
    from_warehouse_id BIGINT COMMENT '调出仓库ID',
    to_warehouse_id BIGINT COMMENT '调入仓库ID',
    transfer_date DATE COMMENT '调拨日期',
    status VARCHAR(20) DEFAULT '草稿' COMMENT '状态: 草稿/已审核/已作废',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_from_warehouse_id (from_warehouse_id),
    INDEX idx_to_warehouse_id (to_warehouse_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存调拨单表';

CREATE TABLE IF NOT EXISTS inventory_transfer_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '调拨明细ID',
    transfer_id BIGINT NOT NULL COMMENT '调拨单ID',
    material_id BIGINT COMMENT '物料ID',
    material_name VARCHAR(100) COMMENT '物料名称',
    spec VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '调拨数量',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_transfer_id (transfer_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存调拨明细表';

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
    other_io_id BIGINT NOT NULL COMMENT '其他出入库单ID',
    material_id BIGINT COMMENT '物料ID',
    material_name VARCHAR(100) COMMENT '物料名称',
    spec VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_other_io_id (other_io_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='其他出入库明细表';

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
