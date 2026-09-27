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
    menu_mode VARCHAR(10) DEFAULT 'ROLE' COMMENT '页面权限模式: ROLE=跟随角色(默认) CUSTOM=以用户级菜单为准',
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
    -- F3-3（2026-09-18 接口级权限专项）：页面级接口权限码（如 purchase:exchange），
    -- 仅 menu_type='menu' 的页面菜单有值；用户的有效权限 = 其可见菜单的 perms 集合，
    -- 由 StpInterfaceImpl.getPermissionList 提供给 @SaCheckPermission。目录(catalog)恒为 NULL。
    -- 存量库（表已存在，CREATE TABLE IF NOT EXISTS 不会改）请手工执行：
    --   ALTER TABLE sys_menu ADD COLUMN perms VARCHAR(100) DEFAULT NULL COMMENT '接口权限码';
    perms VARCHAR(100) DEFAULT NULL COMMENT '接口权限码',
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

-- 用户级页面权限（2026-09-18 新增）：仅当 sys_user.menu_mode = CUSTOM 时生效，
-- 此时该用户的可见菜单**完全以本表为准**（可加可减，不再叠加角色菜单）；
-- menu_mode = ROLE（默认）时本表应为空（保存"跟随角色"会清空），菜单取自角色。
-- 语义与 sys_role_menu 完全对称；构建菜单树时统一走 MenuServiceImpl。
CREATE TABLE IF NOT EXISTS sys_user_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    UNIQUE KEY uk_user_menu (user_id, menu_id),
    INDEX idx_user_id (user_id),
    INDEX idx_menu_id (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户菜单关联表（自定义页面权限）';

CREATE TABLE IF NOT EXISTS sys_user_dashboard_tab (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    tab_key VARCHAR(30) NOT NULL COMMENT '首页业务TAB标识: dev/outsource/purchase/sale/stock/finance',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    UNIQUE KEY uk_user_tab (user_id, tab_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户首页业务TAB可见性配置';

CREATE TABLE IF NOT EXISTS sys_company (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '公司ID',
    company_name VARCHAR(100) NOT NULL COMMENT '公司名称',
    phone VARCHAR(20) COMMENT '电话',
    address VARCHAR(200) COMMENT '地址',
    contact_person VARCHAR(50) COMMENT '联系人',
    tax_no VARCHAR(50) COMMENT '税号',
    email VARCHAR(100) COMMENT '邮箱',
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
    supply_sku VARCHAR(32) DEFAULT NULL COMMENT '供货SKU(该供货商所供产品 SKU 的前缀，如 ABC ⇒ 产品 SKU 形如 ABC-000001；可空，空则产品走默认前缀 SKU-)',
    -- supplier_type 已拆分为 supplier_type_ref 中间表
    contact VARCHAR(50) COMMENT '联系人',
    phone VARCHAR(20) COMMENT '手机号',
    address VARCHAR(200) COMMENT '地址',
    status TINYINT DEFAULT 1 COMMENT '1合作中 0已停用',
    has_display TINYINT DEFAULT 0 COMMENT '支持显示方案',
    has_touch TINYINT DEFAULT 0 COMMENT '支持触摸方案',
    credit_period_months INT DEFAULT NULL COMMENT '账期(月)',
    credit_period INT DEFAULT NULL COMMENT '账期(天)',
    related_supplier_id BIGINT DEFAULT NULL COMMENT '关联供应商ID',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    UNIQUE KEY uk_company_supply_sku (company_id, supply_sku),
    INDEX idx_company_id (company_id),
    INDEX idx_related_supplier_id (related_supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表';

CREATE TABLE IF NOT EXISTS supplier_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    product_id BIGINT NOT NULL COMMENT '关联产品ID（关联 product 表）',
    unit_price DECIMAL(18,4) COMMENT '参考单价',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商产品居间表';

CREATE TABLE IF NOT EXISTS supplier_material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    material_id BIGINT NOT NULL COMMENT '关联外协物料ID（关联 outsource_material 表）',
    unit_price DECIMAL(18,4) COMMENT '参考单价',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商物料居间表';

-- ==================== 外协模块 ====================

CREATE TABLE IF NOT EXISTS outsource_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订单ID',
    code VARCHAR(50) NOT NULL COMMENT '订单编号',
    factory_id BIGINT NOT NULL COMMENT '外协工厂ID',
    plan_start_date DATE COMMENT '计划开始日期',
    plan_end_date DATE COMMENT '计划结束日期',
    actual_start_date DATE COMMENT '实际开始日期',
    actual_end_date DATE COMMENT '实际结束日期',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态',
    supply_mode VARCHAR(20) DEFAULT 'OURS' COMMENT '供料模式:OURS来料加工 FACTORY包工包料',
    tax_included TINYINT DEFAULT 0 COMMENT '0未含税 1含税',
    tax_rate DECIMAL(18,4) DEFAULT 0 COMMENT '税率',
    tax_amount DECIMAL(18,4) DEFAULT 0 COMMENT '税额(含税总额按税率拆分)',
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
    product_id BIGINT COMMENT '关联产品主数据ID(product.id)',
    bom_snapshot_id BIGINT DEFAULT NULL COMMENT '所用BOM快照ID(bom_snapshot.id)：同一产品同BOM版本同内容的多张加工单共享一份快照',
    product_name VARCHAR(100) NOT NULL COMMENT '产品名称',
    quantity DECIMAL(18,0) DEFAULT 0 COMMENT '数量',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order_id (order_id),
    INDEX idx_project_id (project_id),
    INDEX idx_bom_snapshot_id (bom_snapshot_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协订单产品表';

-- BOM 快照（2026-09-17 重构，替代原 outsource_order_material 表）
-- 设计：快照按「产品 + 研发BOM版本 + 明细内容」共享；下单时若研发BOM版本/内容与上一次快照一致，
--       则新加工单直接复用既有快照（不再每次下单都生成一份），只有"有变化"时才新建。
-- 读取：outsource_order_material 已改为**视图**（见文件末尾），字段与旧表完全兼容，
--       既有消费方（交货扣料/缺料判定/结单超损/退货快照/供应商需求/合同导出）无需改动。
CREATE TABLE IF NOT EXISTS bom_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '快照ID',
    product_key VARCHAR(160) NOT NULL COMMENT '快照归属键：有产品主数据=P:{product.id}，无则=N:{产品名称}（物料直挂模式）',
    product_master_id BIGINT DEFAULT NULL COMMENT '产品主数据ID(product.id)',
    project_id BIGINT DEFAULT NULL COMMENT '研发项目ID(dev_project.id)，用于取研发BOM版本',
    bom_version INT DEFAULT NULL COMMENT '研发BOM版本号(dev_bom.version)；无项目时为空',
    fingerprint VARCHAR(64) DEFAULT NULL COMMENT '明细内容指纹(MD5)：物料+单套用量+损耗率+供料方 规范化后计算，用于判断"是否有变化"',
    item_count INT DEFAULT 0 COMMENT '明细行数(冗余便于展示)',
    kind VARCHAR(20) DEFAULT 'BOM' COMMENT '来源：BOM=按研发BOM版本生成 ORDER=订单内手工调整后生成 MIGRATED=历史订单迁移',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    -- F2-4（2026-09-18 审核修复）：并发下单时同一 (product_key, bom_version, fingerprint) 只允许一份，
    -- 抢输的一方由 BomSnapshotServiceImpl 捕获 DuplicateKeyException 后复用赢家快照（不再产生重复快照）。
    -- 存量库（本表已存在，CREATE TABLE IF NOT EXISTS 不会改）请手工执行，SQL 见《代码审核报告_20260918》§9.6：
    --   ALTER TABLE bom_snapshot ADD UNIQUE KEY uk_snapshot (product_key, bom_version, fingerprint);
    UNIQUE KEY uk_snapshot (product_key, bom_version, fingerprint),
    INDEX idx_product_key (product_key),
    INDEX idx_product_master_id (product_master_id),
    INDEX idx_project_id (project_id),
    INDEX idx_bom_version (bom_version),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BOM快照版本表(多加工单共享)';

CREATE TABLE IF NOT EXISTS bom_snapshot_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细ID',
    snapshot_id BIGINT NOT NULL COMMENT '快照ID(bom_snapshot.id)',
    outsource_material_id BIGINT DEFAULT NULL COMMENT '委外物料ID',
    material_type_id BIGINT DEFAULT NULL COMMENT '物料类型ID(关联material_type.id)',
    unit VARCHAR(20) COMMENT '单位',
    quantity_per_set DECIMAL(18,4) DEFAULT 0 COMMENT '单套用量（需求数量 = 单套用量 × 加工数量，读取时由视图换算）',
    loss_rate DECIMAL(18,4) DEFAULT 0 COMMENT '损耗率',
    supply_type VARCHAR(20) DEFAULT 'OURS' COMMENT '供料方:OURS我方供 FACTORY工厂包',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_snapshot_id (snapshot_id),
    INDEX idx_outsource_material_id (outsource_material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BOM快照明细表(单套用量口径)';

CREATE TABLE IF NOT EXISTS outsource_order_delivery (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    -- 收费售后(source_type=AFTER_SALE)与「不关联加工单的加工退货」(source_type=RETURN_DEFECT) 均不关联加工单，
    -- order_id 可为空；仅普通交货必填
    order_id BIGINT DEFAULT NULL COMMENT '订单ID',
    -- 2026-09-21 新增：不关联加工单的加工退货（source_type=RETURN_DEFECT）用 —— 无单时靠它定位
    -- 「工厂委外仓」（BOM 料还回目标）与「应付对象」；其余行一律为空
    factory_id BIGINT DEFAULT NULL COMMENT '工厂ID（仅不关联加工单的加工退货使用）',
    warehouse_id BIGINT DEFAULT NULL COMMENT '收货仓库ID',
    delivery_date DATE COMMENT '发货日期',
    product_id BIGINT COMMENT '产品ID(关联outsource_order_product)',
    product_master_id BIGINT COMMENT '关联产品主数据ID(product.id)',
    quality_type VARCHAR(20) COMMENT '退不良规格(A/B/C/DEFECT)',
    -- 2026-09-27 新增（用户口径）：仅「不关联加工单的加工退货」(source_type=RETURN_DEFECT) 使用 ——
    -- 建单时解析/人工选定的 BOM 快照；返回时（加工返回单）据此限定「实际用料」的可选范围。
    -- 存量库本表已存在（CREATE TABLE IF NOT EXISTS 不会改）⇒ 需手工执行：
    --   ALTER TABLE outsource_order_delivery ADD COLUMN bom_snapshot_id BIGINT DEFAULT NULL
    --     COMMENT 'BOM快照ID（仅无单加工退货使用：返回时限定实际用料范围）' AFTER quality_type;
    bom_snapshot_id BIGINT DEFAULT NULL COMMENT 'BOM快照ID（无单加工退货用；返回时限定实际用料范围）',
    quantity DECIMAL(18,0) DEFAULT 0 COMMENT '数量',
    delivery_type VARCHAR(20) DEFAULT 'DELIVERY' COMMENT 'DELIVERY正常交货/DEFECT_RETURN退不良',
    a_qty DECIMAL(18,0) DEFAULT 0 COMMENT 'A规数量',
    b_qty DECIMAL(18,0) DEFAULT 0 COMMENT 'B规数量',
    c_qty DECIMAL(18,0) DEFAULT 0 COMMENT 'C规数量',
    defect_qty DECIMAL(18,0) DEFAULT 0 COMMENT '不良数量',
    source_type VARCHAR(20) DEFAULT 'DELIVERY' COMMENT '来源类型: DELIVERY普通交货/RETURN_DEFECT委外退货/AFTER_SALE收费售后',
    code VARCHAR(50) DEFAULT NULL COMMENT '加工退货红冲单号（仅 DEFECT_RETURN 记录填写：关联加工单 GTH- / 无单 GTW-；普通交货为 NULL）',
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
    supplier_direct TINYINT DEFAULT 0 COMMENT '0否 1供应商直发（2026-09-16 起已下线，固定 0）',
    supplier_id BIGINT COMMENT '供应商ID',
    allow_negative TINYINT DEFAULT 0 COMMENT '是否允许强制出库（扣成负库存）：0否(默认严格) 1是；仅调拨使用（2026-09-16）',
    logistics_company VARCHAR(100) COMMENT '物流公司',
    logistics_no VARCHAR(100) COMMENT '物流单号',
    delivery_date DATE COMMENT '发货日期',
    contact VARCHAR(50) COMMENT '联系人',
    phone VARCHAR(20) COMMENT '联系电话',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '审核状态: DRAFT草稿/AUDITED已审核/CANCELLED已作废',
    remark VARCHAR(500) COMMENT '备注',
    attach_url VARCHAR(500) COMMENT '附件URL',
    source_order_id BIGINT DEFAULT NULL COMMENT '来源订单ID(关联outsource_material_order.id，强关联回查)',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_project_id (project_id),
    INDEX idx_factory_id (factory_id),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协发货单表';

CREATE TABLE IF NOT EXISTS outsource_delivery_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    delivery_id BIGINT NOT NULL COMMENT '发货单ID',
    outsource_material_id BIGINT COMMENT '委外物料ID',
    material_type_id BIGINT DEFAULT NULL COMMENT '物料类型ID(关联material_type.id)',
    item_id BIGINT DEFAULT NULL COMMENT '来源订单明细行ID',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,0) DEFAULT 0 COMMENT '数量',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '行金额',
    quality_type VARCHAR(20) DEFAULT 'GOOD' COMMENT '良品/不良品',
    handle_type VARCHAR(20) DEFAULT NULL COMMENT '处理方式：维修返还/折现退款',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_delivery_id (delivery_id),
    INDEX idx_outsource_material_id (outsource_material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协发货单明细表';

CREATE TABLE IF NOT EXISTS outsource_material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    cost_price DECIMAL(18,4) DEFAULT NULL COMMENT '移动加权平均成本价(委外仓入库自动更新)',
    cost_manual TINYINT DEFAULT 0 COMMENT '成本价是否手工锁定 0否 1是',
    last_in_price DECIMAL(18,4) DEFAULT NULL COMMENT '最近入库单价',
    -- 2026-09-21：删除两处历史漂移 —— warehouse_id 与 spec 两列（+ idx_warehouse_id 索引）在**现库与实体中都不存在**
    -- （spec 已于 F7-125 随"规格全站下线"移除；warehouse_id 全仓无任何 SQL 引用，grep 命中的都是别的表），
    -- 但本文件一直保留着 ⇒ 用本文件建**新库**会多出 2 根死列 + 1 个死索引，与老库不一致。
    material_name VARCHAR(100) NOT NULL COMMENT '物料名称',
    material_type_id BIGINT DEFAULT NULL COMMENT '物料类型ID(关联material_type.id)',
    unit VARCHAR(20) COMMENT '单位',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    price DECIMAL(18,2) DEFAULT 0 COMMENT '单价',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_company_id (company_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协物料表';

-- ==================== 委外物料订单 ====================

CREATE TABLE IF NOT EXISTS outsource_material_order (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code              VARCHAR(50) NOT NULL               COMMENT '订单号',
    supplier_id       BIGINT                            COMMENT '供应商ID',
    order_type        VARCHAR(10) DEFAULT 'PURCHASE'         COMMENT '订单类型: 采购/委外',
    target_warehouse_id BIGINT DEFAULT NULL             COMMENT '收货目标仓库',
    delivery_date     DATE                              COMMENT '交货日期',
    status            VARCHAR(20) DEFAULT 'PENDING'       COMMENT '状态: PENDING/RECEIVING/FINISHED/CANCELLED',
    remark            VARCHAR(500)                      COMMENT '备注',
    attach_url        VARCHAR(500) DEFAULT NULL          COMMENT '合同附件URL',
    company_id        BIGINT DEFAULT NULL               COMMENT '公司ID',
    finish_time       DATETIME DEFAULT NULL              COMMENT '订单完成时间',
    deleted           TINYINT DEFAULT 0 COMMENT '逻辑删除标记：0未删除/1已删除',
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
    material_type_id           BIGINT DEFAULT NULL           COMMENT '物料类型ID(关联material_type.id)',
    unit                  VARCHAR(20)                   COMMENT '单位',
    order_quantity        DECIMAL(18,0) DEFAULT 0       COMMENT '订购数量',
    received_quantity     DECIMAL(18,0) DEFAULT 0       COMMENT '已收数量',
    defect_returned_qty   DECIMAL(18,0) DEFAULT 0       COMMENT '退不良已退数量',
    -- 2026-09-17：送修中数量（维修返还单已送修、供应商尚未修好送回的部分）。
    -- 订单未完成(RECEIVING)时，送修审核会同时扣减 received_quantity（净收料=已收−送修），
    -- 修好「登记维修返回」时回补；本列只用于在订单上区分"未收 / 送修中"。
    repair_returned_qty   DECIMAL(18,0) DEFAULT 0       COMMENT '送修中数量(维修返还已送修未返回)',
    unit_price            DECIMAL(18,4) DEFAULT 0       COMMENT '单价',
    amount                DECIMAL(18,4) DEFAULT 0       COMMENT '金额',
    remark                VARCHAR(255)                  COMMENT '备注',
    company_id            BIGINT DEFAULT NULL            COMMENT '公司ID',
    deleted               TINYINT DEFAULT 0 COMMENT '逻辑删除标记：0未删除/1已删除',
    INDEX idx_order_id (order_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外物料订单明细表';

-- ==================== 委外仓库（已废弃，统一迁移至 warehouse 表） ====================
-- 以下两表保留注释以备参考，实际使用 warehouse + warehouse_stock + warehouse_stock_log

-- ==================== 统一仓库模块 ====================

CREATE TABLE IF NOT EXISTS warehouse (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '仓库ID',
    code VARCHAR(50) NOT NULL COMMENT '仓库编码',
    warehouse_name VARCHAR(100) NOT NULL COMMENT '仓库名称',
    warehouse_category VARCHAR(20) NOT NULL COMMENT '仓库类别: INVENTORY(自有)/OUTSOURCE(委外)',
    warehouse_type VARCHAR(50) COMMENT '仓库类型(仅INVENTORY，2026-09-16 收敛为 2 种): AUXILIARY辅料仓/FINISHED成品仓；委外仓为 NULL',
    factory_id BIGINT COMMENT '关联加工厂ID(仅OUTSOURCE，关联supplier.id)',
    address VARCHAR(200) COMMENT '仓库地址',
    contact VARCHAR(50) COMMENT '联系人',
    phone VARCHAR(20) COMMENT '联系电话',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_factory_id (factory_id),
    INDEX idx_company_id (company_id),
    INDEX idx_category (warehouse_category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一仓库表';

CREATE TABLE IF NOT EXISTS warehouse_stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    product_id BIGINT DEFAULT NULL COMMENT '产品ID(关联product表，自有仓成品库存)',
    material_id BIGINT DEFAULT NULL COMMENT '物料ID(关联outsource_material表，委外仓物料库存)',
    stock_form VARCHAR(20) NOT NULL DEFAULT 'MATERIAL' COMMENT '库存形态：MATERIAL=物料；PRODUCT_DEFECT=成品(加工退货)；PRODUCT_REPAIR=成品(维修退货)。2026-09-25 新增：委外仓也要能放退回的成品',
    quality_type VARCHAR(20) DEFAULT 'A' COMMENT '品质等级：成品(product_id非空)用 A/B/C/DEFECT/PENDING；委外物料(material_id非空)用 GOOD/DEFECT。两体系互斥，GOOD 仅用于物料',
    quantity DECIMAL(18,0) DEFAULT 0 COMMENT '库存数量',
    available_quantity DECIMAL(18,0) DEFAULT 0 COMMENT '可用数量(预留,目前等于quantity)',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_product_id (product_id),
    INDEX idx_material_id (material_id),
    UNIQUE KEY uk_wh_prod_quality_company (warehouse_id, product_id, quality_type, stock_form, company_id),
    UNIQUE KEY uk_wh_material_company (warehouse_id, material_id, stock_form, company_id),
    INDEX idx_stock_form (stock_form)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一库存表';

CREATE TABLE IF NOT EXISTS warehouse_stock_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    product_id BIGINT DEFAULT NULL COMMENT '产品ID(自有仓成品流水)',
    material_id BIGINT DEFAULT NULL COMMENT '物料ID(委外仓物料流水)',
    stock_form VARCHAR(20) NOT NULL DEFAULT 'MATERIAL' COMMENT '库存形态：同 warehouse_stock.stock_form（2026-09-25 新增，保证流水可还原形态）',
    material_name VARCHAR(100) COMMENT '物料名称',
    quality_type VARCHAR(20) COMMENT '品质等级：成品(product_id非空)用 A/B/C/DEFECT/PENDING；委外物料(material_id非空)用 GOOD/DEFECT。两体系互斥，GOOD 仅用于物料',
    change_type VARCHAR(50) NOT NULL COMMENT '变动类型',
    change_quantity DECIMAL(18,0) DEFAULT 0 COMMENT '变更数量',
    before_quantity DECIMAL(18,0) DEFAULT 0 COMMENT '变更前库存',
    after_quantity DECIMAL(18,0) DEFAULT 0 COMMENT '变更后库存',
    related_bill_no VARCHAR(100) COMMENT '关联单据号',
    related_bill_type VARCHAR(50) COMMENT '关联单据类型',
    related_bill_id BIGINT COMMENT '关联单据ID',
    related_delivery_id BIGINT COMMENT '关联发货单ID(委外物料流水使用)',
    related_order_code VARCHAR(30) COMMENT '关联加工单号(委外物料流水使用)',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_product_id (product_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id),
    INDEX idx_related_bill_no (related_bill_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一库存流水表';

CREATE TABLE IF NOT EXISTS outsource_order_close_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    order_id BIGINT NOT NULL COMMENT '加工单ID',
    close_date DATE COMMENT '结单日期',
    remark VARCHAR(500) COMMENT '备注',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '草稿/已结单',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结单报表主表';

CREATE TABLE IF NOT EXISTS outsource_order_close_report_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    report_id BIGINT NOT NULL COMMENT '报表ID',
    outsource_material_id BIGINT COMMENT '委外物料ID',
    material_type_id BIGINT DEFAULT NULL COMMENT '物料类型ID(关联material_type.id)',
    unit VARCHAR(20) COMMENT '单位',
    returned_quantity DECIMAL(18,0) DEFAULT 0 COMMENT '退料总数',
    good_return_qty DECIMAL(18,0) DEFAULT 0 COMMENT '良品退料',
    defect_return_qty DECIMAL(18,0) DEFAULT 0 COMMENT '不良退料',
    shipped_quantity DECIMAL(18,0) DEFAULT 0 COMMENT '出货消耗',
    target_yield_rate DECIMAL(18,4) DEFAULT 0 COMMENT '加工良率%',
    actual_yield_rate DECIMAL(18,4) DEFAULT 0 COMMENT '生产良率%',
    yield_loss DECIMAL(18,4) DEFAULT 0 COMMENT '良率超损%',
    excess_loss_qty DECIMAL(18,0) DEFAULT 0 COMMENT '超损数量',
    material_price DECIMAL(18,4) DEFAULT 0 COMMENT '物料单价',
    factory_retain_qty DECIMAL(18,0) DEFAULT NULL COMMENT '留存工厂',
    missing_qty DECIMAL(18,0) DEFAULT NULL COMMENT '缺失(手动填写)',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    INDEX idx_report_id (report_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结单报表物料明细';

CREATE TABLE IF NOT EXISTS outsource_return_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code VARCHAR(50) NOT NULL COMMENT '退货单号',
    factory_id BIGINT COMMENT '加工厂ID',
    -- 2026-09-17：委外加工退货分两类（同一张单、类型区分）
    --   DEFECT = 不良退货：工厂交货后我方发现不良退回工厂；可关联加工单(也可不关联)；**禁止向工厂收费**
    --   REPAIR = 维修退货：客户使用后退回我方的售后品推给工厂维修；**不关联加工单**；**必须由工厂向我方收费**
    return_type VARCHAR(20) NOT NULL DEFAULT 'DEFECT' COMMENT '退货类型: DEFECT不良退货/REPAIR维修退货',
    order_id BIGINT COMMENT '关联加工单ID（维修退货必须为空）',
    source_delivery_id BIGINT DEFAULT NULL COMMENT '来源交货记录ID(outsource_order_delivery.id)：成品收货页发起退货时落库，用于按记录算可退数量',
    warehouse_id BIGINT DEFAULT NULL COMMENT '成品出库仓',
    return_date DATE COMMENT '退货日期',
    status VARCHAR(20) DEFAULT 'AUDITED' COMMENT '状态(草稿-审核-取消审核)',
    auditor_id BIGINT DEFAULT NULL COMMENT '审核人ID',
    auditor_name VARCHAR(50) DEFAULT NULL COMMENT '审核人姓名',
    audit_time DATETIME DEFAULT NULL COMMENT '审核时间',
    remark VARCHAR(500) COMMENT '备注',
    charge_flag TINYINT DEFAULT 0 COMMENT '工厂收费(加工厂向我方收取): 0否 1是(审核生成一条我方付给加工厂的正向应付)。不良退货必须 0；维修退货必须 1',
    charge_type VARCHAR(20) DEFAULT NULL COMMENT '收费类型: REWORK返工费/FREIGHT运费/INSPECTION检测费/EXCESS_LOSS超损赔偿/OTHER其他',
    charge_amount DECIMAL(18,2) DEFAULT 0 COMMENT '收费金额(手工填写，审核后生成正向应付，与退料负向冲减分开记账)',
    charge_reason VARCHAR(200) COMMENT '收费说明(原因备注)',
    -- 2026-09-17 维修退货结案：工厂把修好的货全部送回（未返回=0）后人工确认收尾。
    -- 结案后禁止再登记/撤销维修返回、禁止反审核（需先「撤销结案」）；不良退货审核即终结，不使用本列。
    closed_flag TINYINT DEFAULT 0 COMMENT '结案：0未结案 1已结案（仅维修退货用）',
    closed_time DATETIME DEFAULT NULL COMMENT '结案时间',
    closed_by VARCHAR(50) DEFAULT NULL COMMENT '结案人',
    company_id BIGINT COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_factory_id (factory_id),
    INDEX idx_order_id (order_id),
    INDEX idx_source_delivery_id (source_delivery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外退货单';

CREATE TABLE IF NOT EXISTS outsource_return_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    return_order_id BIGINT NOT NULL COMMENT '退货单ID',
    outsource_material_id BIGINT COMMENT '委外物料ID(关联outsource_material.id)',
    material_type_id BIGINT DEFAULT NULL COMMENT '物料类型ID(关联material_type.id)',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,0) COMMENT '退回数量',
    unit_price DECIMAL(18,4) COMMENT '加工单价',
    amount DECIMAL(18,4) COMMENT '小计金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT COMMENT '公司ID',
    INDEX idx_return_order_id (return_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外退货明细';

CREATE TABLE IF NOT EXISTS outsource_return_order_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    return_order_id BIGINT NOT NULL COMMENT '退货单ID',
    product_id BIGINT COMMENT '产品ID(关联product.id，产品主数据ID)',
    product_name VARCHAR(100) COMMENT '产品名称快照',
    -- 2026-09-17：该产品退货时所用的 BOM 快照（bom_snapshot.id）——追溯"按哪份 BOM 用量退的料"；
    -- 关联加工单时由订单产品行决定（不允许手改），未关联时由用户选择
    bom_snapshot_id BIGINT DEFAULT NULL COMMENT '所用BOM快照ID(bom_snapshot.id)',
    quantity DECIMAL(18,0) COMMENT '退货数量',
    quality_type VARCHAR(20) DEFAULT 'A' COMMENT '退回成品规格(A/B/C/DEFECT)，出库按该规格扣减',
    company_id BIGINT COMMENT '公司ID',
    INDEX idx_return_order_id (return_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外加工退货成品明细';

-- 2026-09-17：委外维修返回记录（维修退货单送修后，工厂修好分批送回我方仓库）
-- 与主单一起构成"送修出库 → 维修返回入库"闭环；登记即生效（库存 +），支持逐行撤销（库存回滚）。
CREATE TABLE IF NOT EXISTS outsource_return_order_repair (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    return_order_id BIGINT NOT NULL COMMENT '维修退货单ID(outsource_return_order.id)',
    repair_date DATE COMMENT '返回日期',
    warehouse_id BIGINT COMMENT '返回入库仓(我方成品仓)',
    product_id BIGINT COMMENT '产品主数据ID(product.id)',
    product_name VARCHAR(100) COMMENT '产品名称快照',
    quality_type VARCHAR(20) DEFAULT 'A' COMMENT '返回品质(A/B/C/DEFECT)',
    quantity DECIMAL(18,0) COMMENT '返回数量',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_return_order_id (return_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外维修返回记录';

-- 2026-09-25 P2-1：维修返回明细（在厂核销 + 实际用料）。
-- 送修 = 成品（维修退货）转移进加工厂委外仓（stock_form=PRODUCT_REPAIR）；
-- 返回登记 = 核销在厂行（ALLOC 行）+ 成品回我方仓 + 实际用料多行（可超 BOM，MATERIAL 行，允许扣负）
-- + 按 FIFO 结转成本；**无赔料应收**（我方责任，charge* 维修费应付保持）。
CREATE TABLE IF NOT EXISTS outsource_return_order_repair_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    repair_record_id BIGINT NOT NULL COMMENT '维修返回记录ID(outsource_return_order_repair.id)',
    item_type VARCHAR(20) NOT NULL COMMENT '行类型: ALLOC=在厂核销(产品) / MATERIAL=实际用料(物料)',
    product_id BIGINT COMMENT '核销行-产品主数据ID(item_type=ALLOC)',
    product_name VARCHAR(100) COMMENT '核销行-产品名称快照',
    quality_type VARCHAR(20) COMMENT '核销行-在厂规格(核销时的 PRODUCT_REPAIR 行规格)',
    material_id BIGINT COMMENT '用料行-委外物料ID(item_type=MATERIAL)',
    material_name VARCHAR(100) COMMENT '用料行-物料名称快照',
    unit VARCHAR(20) COMMENT '用料行-单位',
    quantity DECIMAL(18,0) NOT NULL COMMENT '数量(核销量/用料量，可超BOM)',
    unit_price DECIMAL(18,4) COMMENT '用料行-FIFO单价快照(登记时)',
    amount DECIMAL(18,2) COMMENT '用料行-金额快照(登记时)',
    company_id BIGINT COMMENT '公司ID',
    INDEX idx_repair_record_id (repair_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修返回明细(在厂核销+实际用料)';

-- 2026-09-25 P1-2：加工返回单（无单加工退货的"回来"腿，与 P1-1 转移进厂构成闭环）。
-- 业务口径（用户 2026-09-25）：修好送回时——核销在厂成品（委外仓 stock_form=PRODUCT_DEFECT 行）
-- + 修好成品回我方仓 + 按【实际用料多行（数量可超 BOM）】从委外仓扣物料
-- + 料款按 FIFO 生成【对加工厂】的应收（工厂赔料）；成本只在返回单结转。
CREATE TABLE IF NOT EXISTS outsource_return_back (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code VARCHAR(50) NOT NULL COMMENT '返回单号(ORB-)',
    factory_id BIGINT NOT NULL COMMENT '加工厂ID(supplier.id)：赔料应收对象',
    factory_name VARCHAR(100) COMMENT '加工厂名称快照',
    product_id BIGINT NOT NULL COMMENT '产品主数据ID(product.id)',
    product_name VARCHAR(100) COMMENT '产品名称快照',
    source_delivery_id BIGINT DEFAULT NULL COMMENT '来源无单加工退货记录ID(outsource_order_delivery.id)：已返回/未返回与防超返按它聚合（存量单为NULL）',
    quantity DECIMAL(18,0) NOT NULL COMMENT '返回数量(核销在厂成品与回仓同量)',
    defect_quality_type VARCHAR(20) DEFAULT 'A' COMMENT '在厂成品规格(A/B/C/DEFECT)：定位委外仓 PRODUCT_DEFECT 行',
    return_quality_type VARCHAR(20) DEFAULT 'A' COMMENT '修好回仓品质(A/B/C/DEFECT)',
    in_warehouse_id BIGINT NOT NULL COMMENT '回仓仓库ID(我方成品仓)',
    outsource_warehouse_id BIGINT COMMENT '加工厂委外仓ID(创建时解析快照)',
    material_amount DECIMAL(18,2) DEFAULT 0 COMMENT '料款合计快照(Σ行FIFO，审核生成对工厂应收)',
    return_date DATE COMMENT '返回日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT=草稿 AUDITED=已审核 CANCELLED=已作废',
    auditor_id BIGINT COMMENT '审核人ID',
    auditor_name VARCHAR(50) COMMENT '审核人姓名',
    audit_time DATETIME COMMENT '审核时间',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT COMMENT '公司ID',
    create_by BIGINT COMMENT '制单人ID',
    create_by_name VARCHAR(50) COMMENT '制单人姓名',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_factory_id (factory_id),
    INDEX idx_source_delivery (source_delivery_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外加工返回单';

-- 用料明细：数量允许**超过 BOM 标准用量**（按实际耗用记账），审核时逐行按 FIFO 计料款
CREATE TABLE IF NOT EXISTS outsource_return_back_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    return_back_id BIGINT NOT NULL COMMENT '返回单ID(outsource_return_back.id)',
    material_id BIGINT NOT NULL COMMENT '委外物料ID(outsource_material.id)',
    material_name VARCHAR(100) COMMENT '物料名称快照',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,0) NOT NULL COMMENT '实际用料数量(可超BOM标准用量)',
    unit_price DECIMAL(18,4) COMMENT 'FIFO结转单价快照(审核时)',
    amount DECIMAL(18,2) COMMENT '行料款=单价×数量(审核时)',
    company_id BIGINT COMMENT '公司ID',
    INDEX idx_return_back_id (return_back_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加工返回单用料明细';

-- 2026-09-25 物料形态化：维修返回的【实际用料（子物料）】明细（可超 BOM、允许扣负）。
-- 送修 = 物料以 stock_form=MATERIAL_REPAIR 进供应商委外仓；返回登记 = 核销在厂行 + 回我方仓 + 按实际用料扣子物料
-- + FIFO 结转成本到回仓主物料；**无赔料应收**（与成品维修同口径）。
CREATE TABLE IF NOT EXISTS outsource_material_return_repair_material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    repair_record_id BIGINT NOT NULL COMMENT '维修返回记录ID(outsource_material_return_repair.id)',
    material_id BIGINT NOT NULL COMMENT '子物料ID(outsource_material.id，实际耗用的补料)',
    material_name VARCHAR(100) COMMENT '子物料名称快照',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,0) NOT NULL COMMENT '实际耗用数量(可超BOM标准用量)',
    unit_price DECIMAL(18,4) COMMENT 'FIFO单价快照(登记时)',
    amount DECIMAL(18,2) COMMENT '金额=单价×数量快照(登记时)',
    company_id BIGINT COMMENT '公司ID',
    INDEX idx_repair_record_id (repair_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料维修返回实际用料明细';

CREATE TABLE IF NOT EXISTS outsource_material_return (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code VARCHAR(50) NOT NULL COMMENT '退货单号',
    return_type VARCHAR(20) DEFAULT 'REFUND' COMMENT '退货类型：REFUND退货退款(冲减应付)/REPAIR维修返还(修好返还，登记维修返回入库)',
    supplier_id BIGINT COMMENT '退回对象供应商ID',
    from_warehouse_id BIGINT COMMENT '物料出库源仓(用户自选)',
    source_delivery_id BIGINT DEFAULT NULL COMMENT '来源收料单ID(outsource_delivery.id)：物料收货页发起退货时落库，用于按记录算可退数量',
    -- 2026-09-17 维修返还闭环：关联物料订单（订单未完成时审核扣减其收料数，修好返回时回补）
    material_order_id BIGINT DEFAULT NULL COMMENT '关联物料订单ID(outsource_material_order.id)：未关联=null，返回情况靠本单「送修/已返回」跟踪',
    deducted_flag TINYINT DEFAULT 0 COMMENT '是否已在关联物料订单上扣减收料数：0否 1是（审核瞬间按订单状态冻结，反审核按此回滚）',
    closed_flag TINYINT DEFAULT 0 COMMENT '结案：0未结案 1已结案（未返回=0才能结案，结案后禁再登记返回/反审核）',
    closed_time DATETIME DEFAULT NULL COMMENT '结案时间',
    closed_by VARCHAR(50) DEFAULT NULL COMMENT '结案人',
    return_date DATE COMMENT '退货日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT/AUDITED/CANCELLED',
    auditor_id BIGINT COMMENT '审核人ID',
    auditor_name VARCHAR(50) COMMENT '审核人姓名',
    audit_time DATETIME COMMENT '审核时间',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_from_warehouse_id (from_warehouse_id),
    INDEX idx_source_delivery_id (source_delivery_id),
    INDEX idx_material_order_id (material_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外物料退货单';

CREATE TABLE IF NOT EXISTS outsource_material_return_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    return_order_id BIGINT NOT NULL COMMENT '退货单ID',
    outsource_material_id BIGINT COMMENT '委外物料ID(关联outsource_material.id)',
    material_type_id BIGINT DEFAULT NULL COMMENT '物料类型ID(关联material_type.id)',
    -- 2026-09-17：本行送修落在哪一行物料订单明细（审核时按关联订单解析并冻结；未关联订单为 null）：
    -- 送修审核按它扣减该行收料数，修好返回/反审核按它回补或回滚
    material_order_item_id BIGINT DEFAULT NULL COMMENT '送修落到的物料订单明细行(outsource_material_order_item.id)',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,0) COMMENT '退货数量',
    unit_price DECIMAL(18,4) COMMENT '单价(FIFO默认可手填)',
    amount DECIMAL(18,4) COMMENT '小计金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT COMMENT '公司ID',
    INDEX idx_return_order_id (return_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外物料退货明细';

-- 2026-09-17：委外物料维修返回记录（维修返还单送修后，供应商修好分批把物料送回来）
-- 与主单一起构成"送修出库 → 维修返回入库"闭环；登记即生效（物料库存 +），支持逐行撤销（库存回滚）。
-- 与「委外加工退货」的 outsource_return_order_repair 同范式，只是对象是物料而非成品。
CREATE TABLE IF NOT EXISTS outsource_material_return_repair (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    return_order_id BIGINT NOT NULL COMMENT '物料退货单ID(outsource_material_return.id)',
    -- 2026-09-17：本次返回回补到哪一行物料订单明细（关联订单未完成时，返回会回补该行收料数/冲减送修中）
    material_order_item_id BIGINT DEFAULT NULL COMMENT '回补的物料订单明细行(outsource_material_order_item.id)',
    repair_date DATE COMMENT '返回日期',
    warehouse_id BIGINT COMMENT '返回入库仓(默认=该单出库源仓，可改)',
    material_id BIGINT COMMENT '委外物料ID(outsource_material.id)',
    material_name VARCHAR(100) COMMENT '物料名称快照',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,0) DEFAULT 0 COMMENT '本次返回数量',
    -- 2026-09-27（对称性二修）：本条登记时是否真的核销了在厂行（0=旧单无在厂行被跳过）。
    -- 撤销腿按本标记决定是否恢复在厂，避免旧单撤销凭空增加在厂。
    onsite_leg TINYINT DEFAULT 1 COMMENT '登记时是否核销在厂行：1=是(撤销需恢复) 0=旧单跳过(撤销不恢复)',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_return_order_id (return_order_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外物料维修返回记录';

CREATE TABLE IF NOT EXISTS outsource_contract_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '模板ID',
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    content TEXT COMMENT '合同模板内容',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    is_default TINYINT DEFAULT 0 COMMENT '0非默认 1默认模板',
    template_type VARCHAR(20) DEFAULT 'PROCESSING' COMMENT '模板类型：加工合同/采购合同',
    -- 2026-09-21：删除 F7-124 已批准并在现库执行过的 3 根死列（party_a_address/contact/phone）。
    -- 甲方信息实际从 Company 读取（ContractTemplateController），实体无对应字段，现网 2 行全 NULL。
    -- 当时只在现库 DROP、本文件漏同步 ⇒ 建新库会多出 3 根死列。备份见
    -- tools/db-archive/before-f7-124-125-126-drop-columns.sql。
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协合同模板表';

-- ==================== 库存模块（已废弃，统一迁移至 warehouse 表） ====================

-- ==================== 研发模块 ====================

CREATE TABLE IF NOT EXISTS dev_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '项目ID',
    code VARCHAR(50) NOT NULL COMMENT '项目编号',
    name VARCHAR(100) NOT NULL COMMENT '项目名称',
    -- 2026-09-21：列 assembly_name 已改名为 product_name（用户：「总成名称修改成产品名称，更贴切业务」+「连 DB 列名一起改」）
    product_name VARCHAR(100) COMMENT '产品名称(原「总成名称」；研发立项时用它自动建产品并回写 product_id)',
    -- 2026-09-21 新增：立项「规格」——与 product.spec_type 联动；原配时前端隐藏方案与改配信息
    spec_type VARCHAR(20) COMMENT '规格: MATCHED原配/MODIFIED改配（立项必填；原配时隐藏显示方案/触摸方案/改配信息）',
    product_id BIGINT DEFAULT NULL COMMENT '关联产品ID(product.id)，与产品表双向关联',
    brand_id BIGINT DEFAULT NULL COMMENT '品牌ID(brand.id)',
    display_supplier_name VARCHAR(100) COMMENT '显示方案供应商',
    touch_supplier_name VARCHAR(100) COMMENT '触摸方案供应商',
    adapt_model VARCHAR(100) COMMENT '适配机型',
    original_size VARCHAR(50) COMMENT '原始尺寸',
    original_resolution VARCHAR(50) COMMENT '原始分辨率',
    original_drive_ic VARCHAR(100) COMMENT '原机驱动IC型号',
    original_touch_ic VARCHAR(100) COMMENT '原机触摸IC型号',
    glass_size VARCHAR(50) COMMENT '玻璃尺寸',
    glass_resolution VARCHAR(50) COMMENT '玻璃分辨率',
    config_drive_ic_id BIGINT DEFAULT NULL COMMENT '改配驱动IC物料ID(outsource_material.id)',
    config_touch_ic_id BIGINT DEFAULT NULL COMMENT '改配触摸IC物料ID(outsource_material.id)',
    config_code_ic_id BIGINT DEFAULT NULL COMMENT '改配码片IC物料ID(outsource_material.id)',
    project_leader_id BIGINT COMMENT '项目负责人ID',
    sample_factory_id BIGINT COMMENT '样品工厂ID',
    outsource_factory_id BIGINT COMMENT '外协工厂ID',
    start_date DATE COMMENT '开始日期',
    expected_end_date DATE COMMENT '预计结束日期',
    actual_end_date DATE COMMENT '实际结束日期',
    status VARCHAR(20) DEFAULT 'IN_PROGRESS' COMMENT '项目状态(项目阶段自动推导)',
    cancelled_at DATETIME COMMENT '取消时间',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_status (status),
    INDEX idx_project_leader_id (project_leader_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='研发项目表';

CREATE TABLE IF NOT EXISTS dev_project_phase (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    phase_name VARCHAR(50) NOT NULL COMMENT '节点名称',
    -- 2026-09-21 新增：记来源模板ID —— 消除"用可变阶段名回查模板"（F7-94 根因），
    -- 模板改名/改默认天数不再影响已生成的阶段。历史行按 (项目规格, 阶段名) 回填。
    template_id BIGINT DEFAULT NULL COMMENT '来源阶段模板ID(dev_phase_template.id)',
    sort_order INT DEFAULT 0 COMMENT '排序',
    default_days INT DEFAULT 0 COMMENT '默认天数',
    planned_end DATE COMMENT '计划完成日期',
    actual_end DATE COMMENT '实际完成日期',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    status VARCHAR(20) COMMENT '状态',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_project_id (project_id),
    INDEX idx_template_id (template_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目阶段表';

CREATE TABLE IF NOT EXISTS dev_phase_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name VARCHAR(50) NOT NULL COMMENT '阶段名称',
    -- 2026-09-21 新增：阶段模板分「原配 / 改配」两套，立项时按项目规格自动套用对应的一套。
    -- 默认 MODIFIED ⇒ 存量 14 条自动归入「改配」，与既有行为一致。
    spec_type VARCHAR(20) NOT NULL DEFAULT 'MODIFIED' COMMENT '适用规格: MATCHED原配/MODIFIED改配',
    default_days INT DEFAULT 0 COMMENT '默认天数',
    sort_order INT DEFAULT 0 COMMENT '排序',
    product_status_sync TINYINT(1) DEFAULT 0 COMMENT '是否触发产品状态同步(研发中→正常)',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    -- 唯一键必须含 spec_type：两套模板有 7 个同名阶段（立项/排线打样/背贴盖板打样/总成样品/测试/小批量/结项）
    UNIQUE KEY uk_company_spec_name (company_id, spec_type, name),
    INDEX idx_sort (sort_order),
    INDEX idx_spec_sort (spec_type, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='研发项目阶段模板表（原配/改配各一套）';

-- dev_material 已重命名为 dev_purchase_item

CREATE TABLE IF NOT EXISTS dev_purchase_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    project_id BIGINT DEFAULT NULL COMMENT '项目ID（可空，表示不关联研发项目）',
    name VARCHAR(100) NOT NULL COMMENT '名称',
    type VARCHAR(30) COMMENT '类型',
    quantity DECIMAL(18,0) DEFAULT 1 COMMENT '数量',
    location_detail VARCHAR(200) COMMENT '位置详情（具体库位/货架号）',
    warehouse_id BIGINT DEFAULT NULL COMMENT '存放仓库ID（配合 warehouse_type 定位，自有仓与委外仓 ID 独立）',
    warehouse_type VARCHAR(20) DEFAULT NULL COMMENT '仓库归属类型：INVENTORY 自有仓库 / OUTSOURCE 委外仓库',
    purchase_date DATE COMMENT '采购日期',
    amount DECIMAL(18,4) COMMENT '采购金额',
    status VARCHAR(20) DEFAULT 'GOOD' COMMENT '状态: 完好/已损坏/已使用',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_project_id (project_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='研发项目物料表';

-- ==================== 屏幕资料知识库 ====================
-- 行业机型屏幕参数资料（研发管理 → 屏幕资料知识库）：折叠屏 + 直板 AMOLED 机型。
-- 注意：这是行业基础资料、不是业务数据，**清空数据时一律保留**，
--       已在 ClearController 的两处清空逻辑中排除（详见该类注释）。
CREATE TABLE IF NOT EXISTS screen_model (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    category            VARCHAR(20) NOT NULL                COMMENT '机型类别: FOLD折叠屏 / AMOLED直板',
    brand               VARCHAR(32)                         COMMENT '品牌',
    model               VARCHAR(64)                         COMMENT '型号',
    screen_size         VARCHAR(16)                         COMMENT '主屏尺寸',
    resolution          VARCHAR(64)                         COMMENT '主屏分辨率',
    screen_type         VARCHAR(32)                         COMMENT '主屏类型(直屏/折叠/水滴/挖孔等)',
    refresh_rate        VARCHAR(32)                         COMMENT '主屏刷新率',
    sub_size            VARCHAR(16)                         COMMENT '副屏尺寸(折叠屏专用)',
    sub_resolution      VARCHAR(64)                         COMMENT '副屏分辨率(折叠屏专用)',
    sub_screen_type     VARCHAR(32)                         COMMENT '副屏类型(折叠屏专用)',
    sub_refresh_rate    VARCHAR(32)                         COMMENT '副屏刷新率(折叠屏专用)',
    fingerprint         VARCHAR(16)                         COMMENT '指纹识别(侧装/屏下/后置等)',
    panel_supplier      VARCHAR(128)                        COMMENT '屏幕供应商',
    release_date        VARCHAR(32)                         COMMENT '发布时间(原文文本)',
    remark              VARCHAR(255)                        COMMENT '备注',
    company_id          BIGINT DEFAULT NULL                 COMMENT '公司ID',
    create_time         DATETIME DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_category (category),
    INDEX idx_brand (brand),
    INDEX idx_model (model),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='屏幕资料知识库(行业机型屏幕参数)';

-- ==================== 研发物料流转 ====================
CREATE TABLE IF NOT EXISTS dev_material_flow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    material_id BIGINT NOT NULL COMMENT '物料ID(dev_purchase_item.id)',
    place_type VARCHAR(30) NOT NULL COMMENT '位置类型：INVENTORY/OUTSOURCE/SUPPLIER/CUSTOMER/TEXT',
    place_id BIGINT DEFAULT NULL COMMENT '关联对象ID(仓库/供应商/客户主键，TEXT时为null)',
    place_name VARCHAR(200) COMMENT '位置名称(快照，用于列表展示)',
    place_detail VARCHAR(200) COMMENT '自定义文本位置(place_type=TEXT时使用)',
    handler VARCHAR(50) COMMENT '经办人',
    flow_time DATETIME COMMENT '流转时间',
    images VARCHAR(1000) COMMENT '图片URL列表(逗号分隔)',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='研发物料位置流转记录表';

CREATE TABLE IF NOT EXISTS dev_bom (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'BOM ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    material_type_id BIGINT COMMENT '物料类型ID',
    outsource_material_id BIGINT COMMENT '关联外协物料ID(outsource_material.id)',
    supplier_id BIGINT COMMENT '供应商ID',
    quantity DECIMAL(18,0) DEFAULT 0 COMMENT '单套用量',
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

CREATE TABLE IF NOT EXISTS material_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    type_name VARCHAR(50) NOT NULL COMMENT '类型名称',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    is_default TINYINT DEFAULT 0 COMMENT '1默认类型(不可删除) 0自定义',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_company_id (company_id),
    UNIQUE KEY uk_company_name (company_id, type_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料类型表';

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
    INDEX idx_status (status),
    UNIQUE KEY uk_code (code)
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
    INDEX idx_project_id (project_id),
    UNIQUE KEY uk_proj_doc_ver (project_id, doc_name, doc_type, version_code)
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
    warehouse_id BIGINT COMMENT '入库仓库ID',
    order_date DATE COMMENT '订单日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT=草稿 AUDITED=已审核 CANCELLED=已作废',
    tax_included TINYINT DEFAULT 0 COMMENT '0未含税 1含税',
    tax_rate DECIMAL(18,4) DEFAULT 0 COMMENT '税率',
    tax_amount DECIMAL(18,4) DEFAULT 0 COMMENT '税额(含税总额按税率拆分)',
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
    product_id BIGINT COMMENT '产品ID',
    quality_type VARCHAR(10) DEFAULT 'A' COMMENT '品质等级: A/B/C/DEFECT',
    quantity DECIMAL(18,0) DEFAULT 0 COMMENT '数量',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单明细表';

-- ==================== 采购退货单 ====================

CREATE TABLE IF NOT EXISTS purchase_return (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code            VARCHAR(50) NOT NULL               COMMENT '退货单号',
    supplier_id     BIGINT                            COMMENT '供应商ID',
    warehouse_id    BIGINT                            COMMENT '退货仓库ID',
    purchase_order_id   BIGINT                        COMMENT '关联采购单ID',
    purchase_order_code VARCHAR(30)                   COMMENT '关联采购单号',
    return_date     DATE                              COMMENT '退货日期',
    status          VARCHAR(20) DEFAULT 'DRAFT'       COMMENT '状态: DRAFT=草稿 AUDITED=已审核 CANCELLED=已作废',
    total_amount    DECIMAL(18,2) DEFAULT 0            COMMENT '退货总金额',
    -- 是否付费（2026-09-21 用户口径：采购退货单/采购换货单都要有，⚠️ 方向 = 我们付给供货商 ⇒ 正向应付）
    -- 单据级是**派生值**：金额 = Σ 明细行付费，类型仅当各付费行类型一致时回填，否则 NULL（详情显示"多类型"）
    charge_flag     TINYINT DEFAULT 0                 COMMENT '是否付费: 0否 1是（我们向供货商付费；派生自明细）',
    charge_type     VARCHAR(30) DEFAULT NULL          COMMENT '付费类型: SERVICE服务费/DIFF品质差价/FULL全额货值/OTHER其他（各明细一致才回填）',
    charge_amount   DECIMAL(18,2) DEFAULT 0           COMMENT '付费金额 = Σ 明细行付费（审核生成一条正向应付）',
    charge_reason   VARCHAR(255) DEFAULT NULL         COMMENT '付费说明（整单共用一句话）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购退货单主表';

CREATE TABLE IF NOT EXISTS purchase_return_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键ID',
    return_id    BIGINT NOT NULL                   COMMENT '退货单ID(关联主表)',
    purchase_order_item_id BIGINT                  COMMENT '关联采购单明细ID',
    product_id   BIGINT                            COMMENT '产品ID(联查product表)',
    quality_type VARCHAR(10) DEFAULT 'A'           COMMENT '品质等级: A/B/C/DEFECT',
    quantity     DECIMAL(18,0) DEFAULT 0           COMMENT '退货数量',
    unit_price  DECIMAL(18,4) DEFAULT 0           COMMENT '单价',
    amount      DECIMAL(18,4) DEFAULT 0           COMMENT '金额',
    -- 逐产品付费（2026-09-21）：一行 = 一个产品 ⇒ 金额/类型/说明挂在本行；方向 = **我们付给供货商**
    charge_flag   TINYINT DEFAULT 0               COMMENT '是否付费: 0否 1是(逐产品，我们向供货商付费)',
    charge_type   VARCHAR(20) DEFAULT NULL        COMMENT '付费类型: SERVICE/DIFF/FULL/OTHER',
    charge_amount DECIMAL(18,2) DEFAULT 0         COMMENT '该产品付费金额(我方付给供货商)',
    charge_reason VARCHAR(200) DEFAULT NULL       COMMENT '该产品付费说明',
    remark      VARCHAR(255)                      COMMENT '备注',
    company_id  BIGINT DEFAULT NULL               COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_return_id (return_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购退货单明细表';

-- ==================== 采购换货单（进货业务：向供货商退回换新，2026-09-18 新增） ====================
-- 业务：把向供货商采购的成品退回供货商，同时换回（同品）良品，一张单管住"出一进"。
--       2026-09-21：来源采购单改为**可选**（无单换货 = 明细手工录，无可换量上限，退回靠审核时库存校验把关）。
-- 库存：退回侧从我方仓按品质出库（默认不良品 DEFECT）；换入侧入我方仓按品质入库（默认 A 规）。
-- 财务：退回侧生成负向应付（冲减，source_bill_type=PURCHASE_EXCHANGE_RETURN，-RET）
--       换入侧生成正向应付（source_bill_type=PURCHASE_EXCHANGE_IN，-IN）⇒ 两行净额即差价。
--       2026-09-21：「是否付费=是」再额外生成一条正向应付（source_bill_type=PURCHASE_EXCHANGE_CHARGE）
--       ⚠️ 方向：**我们向供货商付费**（服务费/补差价）。
-- 存量库（本表已存在，CREATE TABLE IF NOT EXISTS 不会改）由 DataInitializer#migratePurchaseExchangeCharge 幂等补列：
--   ALTER TABLE purchase_exchange ADD COLUMN charge_flag TINYINT DEFAULT 0;  -- + charge_type / charge_amount / charge_reason

CREATE TABLE IF NOT EXISTS purchase_exchange (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code            VARCHAR(50) NOT NULL               COMMENT '换货单号（CH-yyyyMMddNNN）',
    supplier_id     BIGINT                            COMMENT '供货商ID',
    purchase_order_id   BIGINT                        COMMENT '关联采购单ID（可选：为空=无单换货）',
    purchase_order_code VARCHAR(30)                   COMMENT '关联采购单号（冗余；无单换货为空）',
    warehouse_out_id BIGINT                           COMMENT '退回出库仓ID（我方成品仓，退给供货商）',
    warehouse_in_id  BIGINT                           COMMENT '换入入库仓ID（我方成品仓，可同仓）',
    exchange_date   DATE                              COMMENT '换货日期',
    status          VARCHAR(20) DEFAULT 'DRAFT'       COMMENT '状态: DRAFT=草稿 AUDITED=已审核 CANCELLED=已作废',
    total_return_amount DECIMAL(18,2) DEFAULT 0        COMMENT '退回侧总金额（负向应付冲减）',
    total_in_amount DECIMAL(18,2) DEFAULT 0            COMMENT '换入侧总金额（正向应付）',
    charge_flag     TINYINT DEFAULT 0                 COMMENT '是否付费: 0否 1是（我们向供货商付费）',
    charge_type     VARCHAR(30) DEFAULT NULL          COMMENT '付费类型: SERVICE服务费/DIFF品质差价/FULL全额货值/OTHER其他',
    charge_amount   DECIMAL(18,2) DEFAULT 0           COMMENT '付费金额（我方付给供货商，审核生成正向应付）',
    charge_reason   VARCHAR(255) DEFAULT NULL         COMMENT '付费说明',
    remark          VARCHAR(500)                      COMMENT '备注',
    auditor_id      BIGINT                            COMMENT '审核人ID',
    auditor_name    VARCHAR(50)                       COMMENT '审核人姓名',
    audit_time      DATETIME                          COMMENT '审核时间',
    company_id      BIGINT DEFAULT NULL               COMMENT '公司ID',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购换货单主表';

CREATE TABLE IF NOT EXISTS purchase_exchange_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键ID',
    exchange_id  BIGINT NOT NULL                   COMMENT '换货单ID(关联主表)',
    purchase_order_item_id BIGINT                  COMMENT '关联采购单明细ID（可换量校验锚点）',
    -- 退回侧（退给供货商，出库）
    product_id   BIGINT                            COMMENT '退回产品ID(联查product表)',
    product_name VARCHAR(100)                      COMMENT '退回产品名称（冗余，便于展示）',
    quality_type VARCHAR(10) DEFAULT 'DEFECT'      COMMENT '退回品质等级: A/B/C/DEFECT（默认不良品）',
    quantity     DECIMAL(18,0) DEFAULT 0           COMMENT '退回数量',
    unit_price  DECIMAL(18,4) DEFAULT 0            COMMENT '退回单价（默认取采购单原价）',
    amount      DECIMAL(18,4) DEFAULT 0            COMMENT '退回金额',
    -- 换入侧（供货商换回的良品，入库；同品换货默认 in_product_id=product_id）
    in_product_id BIGINT                           COMMENT '换入产品ID（同品换货默认=退回产品；预留换不同型号）',
    in_quality_type VARCHAR(10) DEFAULT 'A'        COMMENT '换入品质等级: A/B/C/DEFECT（默认A规）',
    in_quantity  DECIMAL(18,0) DEFAULT 0           COMMENT '换入数量（默认=退回数量）',
    in_unit_price DECIMAL(18,4) DEFAULT 0          COMMENT '换入单价（默认=退回单价；改高表示加价换新）',
    in_amount   DECIMAL(18,4) DEFAULT 0            COMMENT '换入金额',
    -- 逐产品付费（2026-09-21）：一行 = 一个产品 ⇒ 金额/类型/说明挂在本行；方向 = **我们付给供货商**
    charge_flag   TINYINT DEFAULT 0               COMMENT '是否付费: 0否 1是(逐产品，我们向供货商付费)',
    charge_type   VARCHAR(20) DEFAULT NULL        COMMENT '付费类型: SERVICE/DIFF/FULL/OTHER',
    charge_amount DECIMAL(18,2) DEFAULT 0         COMMENT '该产品付费金额(我方付给供货商)',
    charge_reason VARCHAR(200) DEFAULT NULL       COMMENT '该产品付费说明',
    remark      VARCHAR(255)                      COMMENT '备注',
    company_id  BIGINT DEFAULT NULL               COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_exchange_id (exchange_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购换货单明细表（退回侧 + 换入侧）';

-- ==================== 销售模块 ====================

CREATE TABLE IF NOT EXISTS sale_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '销售单ID',
    code VARCHAR(50) NOT NULL COMMENT '销售单号',
    customer_id BIGINT COMMENT '客户ID',
    warehouse_id BIGINT COMMENT '出库仓库ID',
    order_date DATE COMMENT '订单日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: 草稿/已审核/已出库/已作废',
    audit_time DATETIME DEFAULT NULL COMMENT '审核时间(财务分析利润表按审核时间归月)',
    tax_included TINYINT DEFAULT 0 COMMENT '0未含税 1含税',
    tax_rate DECIMAL(18,4) DEFAULT 0 COMMENT '税率',
    tax_amount DECIMAL(18,4) DEFAULT 0 COMMENT '税额(含税总额按税率拆分)',
    total_amount DECIMAL(18,4) DEFAULT 0 COMMENT '总金额',
    -- 2026-09-18：结算方式（**按单记**：同一客户有时现金、有时账期）
    settle_type VARCHAR(20) NOT NULL DEFAULT 'CREDIT' COMMENT '结算方式: CREDIT=账期(只挂应收) CASH=现金(审核后自动生成草稿收款单)',
    settle_account_id BIGINT DEFAULT NULL COMMENT '结算账户ID(finance_account.id)：settle_type=CASH 时必填，默认取现金账户',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_customer_id (customer_id),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_settle_account_id (settle_account_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售单表';

CREATE TABLE IF NOT EXISTS sale_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '销售单明细ID',
    order_id BIGINT NOT NULL COMMENT '销售单ID',
    product_id BIGINT COMMENT '产品ID',
    quality_type VARCHAR(10) DEFAULT 'A' COMMENT '品质等级: A/B/C/DEFECT',
    quantity DECIMAL(18,0) DEFAULT 0 COMMENT '数量',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售单明细表';

CREATE TABLE IF NOT EXISTS sale_outbound (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '销售出库单ID',
    code VARCHAR(50) NOT NULL COMMENT '销售出库单号',
    order_id BIGINT COMMENT '关联销售单ID',
    customer_id BIGINT COMMENT '客户ID',
    warehouse_id BIGINT COMMENT '出库仓库ID',
    outbound_date DATE COMMENT '出库日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: 草稿/已审核/已作废',
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
    product_id BIGINT COMMENT '产品ID',
    quality_type VARCHAR(10) DEFAULT 'A' COMMENT '品质等级: A/B/C/DEFECT',
    quantity DECIMAL(18,0) DEFAULT 0 COMMENT '数量',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '金额',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_outbound_id (outbound_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售出库明细表';

-- ==================== 销售退货单（客户退回不良品，入库增库存） ====================

CREATE TABLE IF NOT EXISTS sale_return (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '销售退货单ID',
    code VARCHAR(30) COMMENT '退货单号',
    customer_id BIGINT COMMENT '客户ID',
    warehouse_id BIGINT COMMENT '退货入库仓库ID',
    sale_order_id BIGINT COMMENT '关联销售单ID',
    sale_order_code VARCHAR(30) COMMENT '关联销售单号',
    return_date DATE COMMENT '退货日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT=草稿 AUDITED=已审核 CANCELLED=已作废',
    total_amount DECIMAL(18,2) DEFAULT 0 COMMENT '退货总金额',
    loss_amount DECIMAL(18,2) DEFAULT 0 COMMENT '折损收款金额(整理后B/C/不良的折损，向客户收取，审核生成正向应收)',
    charge_flag TINYINT DEFAULT 0 COMMENT '是否收费: 0否 1是(收费则审核生成一条正向应收，单号后缀 -FEE)',
    charge_type VARCHAR(20) DEFAULT NULL COMMENT '收费类型: SERVICE服务费/DIFF品质差价/FULL全额货值/OTHER其他',
    charge_amount DECIMAL(18,2) DEFAULT 0 COMMENT '收费金额(手工填写，审核后生成正向应收，单号后缀 -FEE)',
    charge_reason VARCHAR(200) COMMENT '收费说明(原因备注)',
    remark VARCHAR(500) COMMENT '备注',
    auditor_id BIGINT COMMENT '审核人ID',
    auditor_name VARCHAR(50) COMMENT '审核人姓名',
    audit_time DATETIME COMMENT '审核时间',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_customer_id (customer_id),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售退货单主表';

CREATE TABLE IF NOT EXISTS sale_return_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '退货单明细ID',
    return_id BIGINT COMMENT '退货单ID',
    sale_order_item_id BIGINT COMMENT '关联销售单明细ID',
    product_id BIGINT COMMENT '产品ID',
    quality_type VARCHAR(10) DEFAULT 'PENDING' COMMENT '品质等级: A/B/C/DEFECT/PENDING，销售退货默认PENDING(待整理)',
    quantity DECIMAL(18,0) DEFAULT 0 COMMENT '退货数量',
    sorted_quantity DECIMAL(18,0) DEFAULT 0 COMMENT '已整理数量(退货整理单审核后累加、反审核扣回)',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,2) DEFAULT 0 COMMENT '金额',
    -- 逐产品收费（2026-09-21 用户口径：收费要精确到产品）——单据级 charge_* 变为 Σ(本表)
    charge_flag TINYINT DEFAULT 0 COMMENT '是否收费: 0否 1是(逐产品)',
    charge_type VARCHAR(20) DEFAULT NULL COMMENT '收费类型: SERVICE服务费/DIFF品质差价/FULL全额货值/OTHER其他',
    charge_amount DECIMAL(18,2) DEFAULT 0 COMMENT '该产品收费金额(向客户收取)',
    charge_reason VARCHAR(200) COMMENT '该产品收费说明',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_return_id (return_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售退货单明细表';

-- ==================== 库存扩展（盘点/调拨/其他出入库） ====================
-- 库存流水表已统一迁移至 warehouse_stock_log

-- ==================== 成品移仓单主表 ====================
-- ==================== 库存盘点单（每月每仓一次） ====================

CREATE TABLE IF NOT EXISTS inventory_stock_take (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '盘点单ID',
    take_no VARCHAR(50) NOT NULL COMMENT '盘点单号(PD-yyyyMMddNNN)',
    warehouse_id BIGINT NOT NULL COMMENT '盘点仓库ID',
    warehouse_name VARCHAR(100) COMMENT '盘点仓库名称(冗余)',
    period VARCHAR(7) NOT NULL COMMENT '盘点月份(yyyy-MM)',
    take_date DATE COMMENT '盘点日期',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT=草稿 AUDITED=已审核 CANCELLED=已作废',
    remark VARCHAR(500) COMMENT '备注',
    auditor_id BIGINT COMMENT '审核人ID',
    auditor_name VARCHAR(50) COMMENT '审核人姓名',
    audit_time DATETIME COMMENT '审核时间',
    company_id BIGINT COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_take_no (take_no),
    INDEX idx_warehouse (warehouse_id),
    INDEX idx_period (period),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存盘点单主表';

CREATE TABLE IF NOT EXISTS inventory_stock_take_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细ID',
    take_id BIGINT NOT NULL COMMENT '盘点单ID',
    product_id BIGINT COMMENT '产品ID(成品仓盘点)',
    product_name VARCHAR(100) COMMENT '产品名称(冗余)',
    sku VARCHAR(64) COMMENT 'SKU(冗余)',
    material_id BIGINT COMMENT '委外物料ID(委外仓盘点)',
    material_name VARCHAR(100) COMMENT '物料名称(冗余)',
    quality_type VARCHAR(20) COMMENT '品质/等级(成品 A/B/C/DEFECT/PENDING；物料 GOOD/DEFECT)',
    stock_form VARCHAR(20) NOT NULL DEFAULT 'MATERIAL' COMMENT '库存形态：MATERIAL=物料；PRODUCT_DEFECT=成品(加工退货)；PRODUCT_REPAIR=成品(维修退货)',
    unit VARCHAR(20) COMMENT '单位(冗余)',
    book_quantity DECIMAL(18,0) DEFAULT 0 COMMENT '账面数量(建单时快照)',
    actual_quantity DECIMAL(18,0) DEFAULT 0 COMMENT '实盘数量',
    diff_quantity DECIMAL(18,0) DEFAULT 0 COMMENT '差异数量(实盘-账面)',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT COMMENT '公司ID',
    INDEX idx_take_id (take_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存盘点单明细';

CREATE TABLE IF NOT EXISTS inventory_warehouse_move (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键ID',
    code              VARCHAR(50) NOT NULL               COMMENT '移仓单号(YC-yyyyMMdd-NNN)',
    from_warehouse_id BIGINT                            COMMENT '移出仓库ID',
    to_warehouse_id   BIGINT                            COMMENT '移入仓库ID',
    move_date         DATE                              COMMENT '移仓日期',
    status            VARCHAR(20) DEFAULT 'DRAFT'         COMMENT '状态: 草稿/已审核/已作废',
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
    quantity     DECIMAL(18,0) DEFAULT 0           COMMENT '移仓数量',
    remark      VARCHAR(255)                      COMMENT '备注',
    company_id  BIGINT DEFAULT NULL               COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_move_id (move_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成品移仓单明细表';

-- ==================== 物料移仓单主表（2026-09-24 新增）====================
-- 复刻成品移仓单结构，明细对象改为委外物料、仓库范围为我方物料仓 + 委外仓。
-- 用途：替代已下线的手工「物料收发单」（发料 / 调拨）；收料/退不良仍走物料收货流程，与本表无关。
CREATE TABLE IF NOT EXISTS inventory_material_move (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键ID',
    code              VARCHAR(50) NOT NULL               COMMENT '物料移仓单号(MYC-yyyyMMddNNN)',
    from_warehouse_id BIGINT                            COMMENT '移出仓库ID',
    to_warehouse_id   BIGINT                            COMMENT '移入仓库ID',
    move_date         DATE                              COMMENT '移仓日期',
    status            VARCHAR(20) DEFAULT 'DRAFT'         COMMENT '状态: 草稿/已审核/已作废',
    remark            VARCHAR(500)                      COMMENT '备注',
    auditor_id        BIGINT                            COMMENT '审核人ID',
    auditor_name      VARCHAR(50)                       COMMENT '审核人姓名(快照)',
    create_by         BIGINT                            COMMENT '制单人ID',
    create_by_name    VARCHAR(50)                       COMMENT '制单人姓名(快照)',
    company_id        BIGINT DEFAULT NULL               COMMENT '公司ID',
    create_time       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_from_warehouse_id (from_warehouse_id),
    INDEX idx_to_warehouse_id (to_warehouse_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料移仓单主表';

-- ==================== 物料移仓单明细表 ====================
CREATE TABLE IF NOT EXISTS inventory_material_move_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键ID',
    move_id     BIGINT NOT NULL                    COMMENT '移仓单ID(关联主表)',
    material_id BIGINT                             COMMENT '委外物料ID(关联 outsource_material)',
    quality_type VARCHAR(10) DEFAULT 'A'           COMMENT '品质等级: A/B/C/DEFECT(单据留痕，不参与库存)',
    quantity    DECIMAL(18,0) DEFAULT 0            COMMENT '移仓数量',
    remark      VARCHAR(255)                       COMMENT '备注',
    company_id  BIGINT DEFAULT NULL                COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_move_id (move_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料移仓单明细表';

-- ==================== 品质重分类单主表 ====================
CREATE TABLE IF NOT EXISTS inventory_stock_reclass (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '主键ID',
    code              VARCHAR(50) NOT NULL               COMMENT '重分类单号(FL-yyyyMMdd-NNN)',
    warehouse_id      BIGINT NOT NULL                   COMMENT '仓库ID(关联仓库表)',
    reclass_date      DATE NOT NULL                     COMMENT '业务日期',
    status            VARCHAR(20) DEFAULT 'DRAFT'         COMMENT '状态: 草稿/已审核/已作废',
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
    from_quality VARCHAR(10) NOT NULL              COMMENT '源等级: A/B/C/DEFECT',
    to_quality   VARCHAR(10) NOT NULL              COMMENT '目标等级: A/B/C/DEFECT',
    quantity     DECIMAL(18,0) DEFAULT 0           COMMENT '重分类数量',
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
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: 草稿/已审核/已作废',
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
    quantity      DECIMAL(18,0) DEFAULT 0 COMMENT '数量',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_other_io_id (other_io_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='其他出入库明细表';

-- ==================== 成品报损 ====================

CREATE TABLE IF NOT EXISTS inventory_stock_loss (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '报损单ID',
    code           VARCHAR(50) NOT NULL  COMMENT '报损单号(BS-yyyyMMdd-NNN)',
    warehouse_id   BIGINT NOT NULL       COMMENT '报损仓库ID',
    warehouse_name VARCHAR(100)          COMMENT '仓库名称(冗余)',
    loss_date      DATE                  COMMENT '报损日期',
    loss_reason    VARCHAR(30)           COMMENT '报损原因: DAMAGE=破损 EXPIRED=变质过期 LOST=丢失 QUALITY=质量不合格 OTHER=其他',
    total_amount   DECIMAL(18,2) DEFAULT 0 COMMENT '报损总金额(明细金额合计,冗余便于列表展示)',
    status         VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT=草稿 AUDITED=已审核 CANCELLED=已作废',
    remark         VARCHAR(500)          COMMENT '备注',
    auditor_id     BIGINT                COMMENT '审核人ID',
    auditor_name   VARCHAR(50)           COMMENT '审核人姓名',
    audit_time     DATETIME              COMMENT '审核时间',
    company_id     BIGINT DEFAULT NULL   COMMENT '公司ID',
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_status (status),
    INDEX idx_loss_reason (loss_reason),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成品报损单主表';

CREATE TABLE IF NOT EXISTS inventory_stock_loss_item (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细ID',
    loss_id      BIGINT NOT NULL        COMMENT '报损单ID',
    product_id   BIGINT NOT NULL        COMMENT '产品ID',
    product_name VARCHAR(100)           COMMENT '产品名称(冗余)',
    sku          VARCHAR(64)            COMMENT 'SKU(冗余)',
    quality_type VARCHAR(10) DEFAULT 'A' COMMENT '品质等级: A/B/C/DEFECT/PENDING',
    unit         VARCHAR(20)            COMMENT '单位(冗余)',
    quantity     DECIMAL(18,0) DEFAULT 0 COMMENT '报损数量',
    unit_price   DECIMAL(18,4) DEFAULT 0 COMMENT '报损单价(带出产品成本价,可改)',
    amount       DECIMAL(18,2) DEFAULT 0 COMMENT '报损金额(数量×单价)',
    remark       VARCHAR(255)           COMMENT '备注',
    company_id   BIGINT DEFAULT NULL    COMMENT '公司ID',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_loss_id (loss_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成品报损单明细';

-- ==================== 品质重分类 ====================

CREATE TABLE IF NOT EXISTS product_reclassify (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code            VARCHAR(50) NOT NULL               COMMENT '单号(PC-yyyyMMdd-001)',
    warehouse_id    BIGINT NOT NULL                    COMMENT '仓库ID',
    reclassify_date DATE                               COMMENT '调整日期',
    status          VARCHAR(20) DEFAULT 'DRAFT'         COMMENT '状态: 草稿/已审核/已取消',
    remark          VARCHAR(500)                       COMMENT '备注',
    create_by       BIGINT DEFAULT NULL                COMMENT '整理人ID(建单账户)',
    create_by_name  VARCHAR(50) DEFAULT NULL           COMMENT '整理人名称(冗余展示)',
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
    quantity        DECIMAL(18,0) DEFAULT 0            COMMENT '调整数量',
    remark          VARCHAR(255)                       COMMENT '备注',
    company_id      BIGINT DEFAULT NULL                COMMENT '公司ID',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_reclassify_id (reclassify_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品质重分类明细表';

CREATE TABLE IF NOT EXISTS return_sort (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code                  VARCHAR(50) NOT NULL               COMMENT '单号(TS-yyyyMMdd-001)',
    warehouse_id          BIGINT NOT NULL                    COMMENT '源仓库(售后仓)ID',
    sort_date             DATE                               COMMENT '整理日期',
    target_warehouse_a    BIGINT DEFAULT NULL                COMMENT 'A规入库仓库（2026-09-22 起前端不再选择：缺省由服务端回填=源仓库，分选后按品质回源仓）',
    target_warehouse_b    BIGINT DEFAULT NULL                COMMENT 'B规入库仓库（同上；历史单的显式值保留，保证反审核/再审核落同一个仓）',
    target_warehouse_c    BIGINT DEFAULT NULL                COMMENT 'C规入库仓库（同上）',
    target_warehouse_defect BIGINT DEFAULT NULL              COMMENT '不良入库仓库（同上；不良靠品质 DEFECT 区分，不需要独立不良仓）',
    status                VARCHAR(20) DEFAULT 'DRAFT'        COMMENT '状态: DRAFT/AUDITED/CANCELLED',
    loss_amount           DECIMAL(18,2) DEFAULT 0            COMMENT '折损收款金额(整理后B/C/不良品的折损，向客户收取，审核后生成正向应收，单号后缀 -LOSS)',
    loss_remark           VARCHAR(200)                       COMMENT '折损收款说明',
    remark                VARCHAR(500)                       COMMENT '备注',
    sort_user_id          BIGINT DEFAULT NULL                COMMENT '整理人用户ID（2026-09-22：谁操作的就是谁整理的，服务端按当前登录用户写入，前端不可传）',
    sort_user_name        VARCHAR(50) DEFAULT NULL           COMMENT '整理人登录名（冗余，便于详情直接展示）',
    company_id            BIGINT DEFAULT NULL                COMMENT '公司ID',
    create_time           DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货整理主表';

CREATE TABLE IF NOT EXISTS return_sort_item (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    sort_id               BIGINT NOT NULL                    COMMENT '退货整理单ID',
    product_id            BIGINT NOT NULL                    COMMENT '产品ID',
    pending_id            BIGINT DEFAULT NULL                COMMENT '来源售后待整理批次ID(after_sale_pending.id)，整理数量回写的唯一追溯锚点',
    sale_return_item_id   BIGINT DEFAULT NULL                COMMENT '[已废弃，保留兼容]来源销售退货明细ID，追溯统一走 pending_id',
    product_name          VARCHAR(200)                       COMMENT '产品名称(冗余)',
    unit                  VARCHAR(20)                        COMMENT '单位(冗余)',
    total_quantity        DECIMAL(18,0) DEFAULT 0            COMMENT '待整理数量',
    qty_a                 DECIMAL(18,0) DEFAULT 0            COMMENT 'A规数量',
    qty_b                 DECIMAL(18,0) DEFAULT 0            COMMENT 'B规数量',
    qty_c                 DECIMAL(18,0) DEFAULT 0            COMMENT 'C规数量',
    qty_defect            DECIMAL(18,0) DEFAULT 0            COMMENT '不良数量',
    remark                VARCHAR(255)                       COMMENT '备注',
    company_id            BIGINT DEFAULT NULL                COMMENT '公司ID',
    create_time           DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_sort_id (sort_id),
    INDEX idx_product_id (product_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货整理明细表';

-- 售后待整理批次（销售退货单/销售换货单统一入口）
-- 设计说明：售后仓的待整理(PENDING)库存按 (仓库,产品,品质) 聚合，本身不记录来源，无法追溯。
-- 退货单与换货单审核时各写入一条待整理批次，退货整理单消费本表并回写 sorted_quantity，
-- 从而统一追溯「这批待整理品来自哪张单据、是否已整理完」。新增售后单据类型只需扩展 source_type。
CREATE TABLE IF NOT EXISTS after_sale_pending (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '待整理批次ID',
    source_type           VARCHAR(20) NOT NULL              COMMENT '来源单据类型: SALE_RETURN(销售退货单)/SALE_EXCHANGE(销售换货单)',
    source_id             BIGINT NOT NULL                   COMMENT '来源单据ID(sale_return.id / sale_exchange.id)',
    source_item_id        BIGINT NOT NULL                   COMMENT '来源单据明细ID(sale_return_item.id / sale_exchange_item.id)',
    source_code           VARCHAR(64)                       COMMENT '来源单号(冗余，便于列表展示与检索)',
    source_date           DATE                              COMMENT '来源单据业务日期(退货单的退货日期/换货的换货日期，冗余展示)',
    warehouse_id          BIGINT NOT NULL                   COMMENT '售后仓ID(待整理品所在仓)',
    customer_id           BIGINT DEFAULT NULL               COMMENT '客户ID(冗余，整理后生成折损应收用)',
    product_id            BIGINT NOT NULL                   COMMENT '产品ID',
    product_name          VARCHAR(200)                      COMMENT '产品名称(冗余)',
    unit                  VARCHAR(20)                       COMMENT '单位(冗余)',
    quantity              DECIMAL(18,0) DEFAULT 0           COMMENT '待整理数量(来源单据审核时的入库数量)',
    sorted_quantity       DECIMAL(18,0) DEFAULT 0           COMMENT '已整理数量(整理单审核累加、反审核扣回)',
    unit_price            DECIMAL(18,4) DEFAULT 0           COMMENT '来源单价(冗余，折损计算与展示用)',
    company_id            BIGINT DEFAULT NULL               COMMENT '公司ID',
    create_time           DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_source_item (source_type, source_item_id),
    INDEX idx_warehouse_product (warehouse_id, product_id),
    INDEX idx_source (source_type, source_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后待整理批次表(退货单/换货统一入口)';

-- 销售换货单（只支持同品换货，强关联销售单：审核时退回入售后仓 + 换出从成品仓扣减）
CREATE TABLE IF NOT EXISTS sale_exchange (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code                  VARCHAR(64) UNIQUE                 COMMENT '换货单号(HH-yyyyMMdd-NNN)',
    sale_order_id         BIGINT DEFAULT NULL                COMMENT '来源销售单ID(sale_order.id)，强关联',
    sale_order_code       VARCHAR(64)                        COMMENT '来源销售单号(冗余，便于检索)',
    customer_id           BIGINT DEFAULT NULL                COMMENT '客户ID',
    warehouse_in_id       BIGINT DEFAULT NULL                COMMENT '换入仓：退回货品入此仓(须为售后仓)',
    warehouse_out_id      BIGINT DEFAULT NULL                COMMENT '换出仓：发出新货从此仓扣减(须为成品仓)',
    exchange_date         DATE                               COMMENT '换货日期',
    status                VARCHAR(20) DEFAULT 'DRAFT'        COMMENT '状态: DRAFT/AUDITED/CANCELLED',
    -- total_amount（换出货值合计）：F7-118（2026-09-20）已 **DROP COLUMN** —— 该列后端从不回写
    -- （实体 SaleExchange 里根本没有该字段，保存/审核都不计算）⇒ 恒为 0；分析页曾误用它算换货率，
    -- 导致"换货率永远 0、饼图为空"。换货金额口径统一取**明细** sale_exchange_item.out_amount。
    charge_flag           TINYINT DEFAULT 0                  COMMENT '是否收费: 0否 1是',
    charge_type           VARCHAR(20) DEFAULT NULL           COMMENT '收费类型: SERVICE服务费/DIFF品质差价/FULL全额货值/OTHER其他',
    charge_amount         DECIMAL(18,2) DEFAULT 0            COMMENT '收费金额(手工填写，审核后生成正向应收，单号后缀 -FEE)',
    charge_reason         VARCHAR(200)                       COMMENT '收费说明(原因备注)',
    remark                VARCHAR(500)                       COMMENT '备注',
    auditor_id            BIGINT DEFAULT NULL                COMMENT '审核人ID',
    auditor_name          VARCHAR(50)                        COMMENT '审核人姓名',
    audit_time            DATETIME                           COMMENT '审核时间',
    company_id            BIGINT DEFAULT NULL                COMMENT '公司ID',
    create_time           DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_sale_order_id (sale_order_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售换货单';

-- 销售换货明细（退回侧 + 换出侧：只支持同品换货，换出产品固定为退回产品）
CREATE TABLE IF NOT EXISTS sale_exchange_item (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    exchange_id           BIGINT NOT NULL                   COMMENT '换货单ID',
    sale_order_item_id    BIGINT DEFAULT NULL               COMMENT '来源销售单明细ID(sale_order_item.id)，可换量校验与追溯锚点',
    -- ===== 退回侧（客户退回的货品，入售后仓待整理）=====
    product_id            BIGINT NOT NULL                   COMMENT '退回产品ID(取自销售单明细行)',
    product_name          VARCHAR(200)                      COMMENT '退回产品名称(冗余)',
    quantity              DECIMAL(18,0) DEFAULT 0           COMMENT '退回数量(可换量校验以此为准)',
    unit_price            DECIMAL(18,4) DEFAULT 0           COMMENT '原销售单价(冗余，仅展示)',
    amount                DECIMAL(18,2) DEFAULT 0           COMMENT '退回金额(退回数量×原销售单价，仅展示)',
    -- ===== 换出侧（发给客户的新货，从成品仓扣减；只支持同品，产品固定为退回产品）=====
    out_quantity          DECIMAL(18,0) DEFAULT 0           COMMENT '换出数量(可与退回数量不等，如退2换1)',
    out_unit_price        DECIMAL(18,4) DEFAULT 0           COMMENT '换出单价(默认取原销售单价，可手工改，仅用于展示与差价参考)',
    out_amount            DECIMAL(18,2) DEFAULT 0           COMMENT '换出金额(换出数量×换出单价，仅展示)',
    out_quality_type      VARCHAR(10) DEFAULT 'A'           COMMENT '换出品质: A/B/C/DEFECT(退回统一记 PENDING 待整理)',
    -- 逐产品收费（2026-09-21 用户口径：收费要精确到产品）——单据级 charge_* 变为 Σ(本表)
    charge_flag           TINYINT DEFAULT 0                 COMMENT '是否收费: 0否 1是(逐产品)',
    charge_type           VARCHAR(20) DEFAULT NULL          COMMENT '收费类型: SERVICE服务费/DIFF品质差价/FULL全额货值/OTHER其他',
    charge_amount         DECIMAL(18,2) DEFAULT 0           COMMENT '该产品收费金额(向客户收取)',
    charge_reason         VARCHAR(200)                      COMMENT '该产品收费说明',
    remark                VARCHAR(500)                      COMMENT '备注',
    company_id            BIGINT DEFAULT NULL               COMMENT '公司ID',
    create_time           DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_exchange_id (exchange_id),
    INDEX idx_sale_order_item_id (sale_order_item_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售换货明细表(退回侧+换出侧，只支持同品换货)';

-- ==================== 财务模块 ====================

CREATE TABLE IF NOT EXISTS finance_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '账户ID',
    account_name VARCHAR(100) NOT NULL COMMENT '账户名称',
    account_type VARCHAR(20) COMMENT '类型: 现金/银行',
    bank_name VARCHAR(100) COMMENT '开户行',
    account_no VARCHAR(50) COMMENT '账号',
    opening_balance DECIMAL(18,4) DEFAULT 0 COMMENT '期初余额(开户时初始资金，之后不可变)',
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
    -- subject_type: CUSTOMER=客户应收(默认) SUPPLIER=供应商应收(由应付转应收单生成，挂 supplier_id)
    subject_type VARCHAR(20) DEFAULT 'CUSTOMER' COMMENT '往来主体类型: CUSTOMER=客户 SUPPLIER=供应商',
    supplier_id BIGINT DEFAULT NULL COMMENT '供应商ID(subject_type=SUPPLIER 时有值，客户应收为空)',
    supplier_name VARCHAR(100) DEFAULT NULL COMMENT '供应商名称(冗余，subject_type=SUPPLIER 时留痕)',
    source_bill_type VARCHAR(30) COMMENT '来源单据类型: 销售出库/其他应收',
    source_bill_no VARCHAR(50) COMMENT '来源单据号',
    source_id BIGINT DEFAULT NULL COMMENT '来源记录ID',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '应收金额',
    paid_amount DECIMAL(18,4) DEFAULT 0 COMMENT '已收金额',
    unpaid_amount DECIMAL(18,4) DEFAULT 0 COMMENT '未收金额',
    due_date DATE COMMENT '到期日',
    status VARCHAR(20) DEFAULT 'UNSETTLED' COMMENT '状态: 未结清/部分结清/已结清',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_bill_no (bill_no),
    INDEX idx_customer_id (customer_id),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_subject_type (subject_type),
    INDEX idx_status (status),
    INDEX idx_source_bill_no (source_bill_no),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应收台账表';

CREATE TABLE IF NOT EXISTS finance_payable (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '应付台账ID',
    bill_no VARCHAR(50) NOT NULL COMMENT '单据号',
    supplier_id BIGINT COMMENT '供应商ID',
    supplier_name VARCHAR(100) COMMENT '供应商名称',
    -- supplier_type 固化开单时的主体类型（供货商/加工厂/辅料商/方案商）：
    -- 不实时取 supplier_type_ref，避免供应商类型变更后历史账务被篡改
    supplier_type VARCHAR(30) DEFAULT NULL COMMENT '往来主体类型: product=供货商 factory=加工厂 material=辅料商 solution=方案商',
    source_bill_type VARCHAR(30) COMMENT '来源单据类型: 采购入库/其他应付',
    source_bill_no VARCHAR(50) COMMENT '来源单据号',
    source_id BIGINT DEFAULT NULL COMMENT '来源记录ID',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '应付金额(负数=退货/扣款等冲减项)',
    paid_amount DECIMAL(18,4) DEFAULT 0 COMMENT '已付金额',
    unpaid_amount DECIMAL(18,4) DEFAULT 0 COMMENT '未付金额',
    due_date DATE COMMENT '到期日',
    status VARCHAR(20) DEFAULT 'UNSETTLED' COMMENT '状态: 未结清/部分结清/已结清',
    -- transferred_to_receivable=1 表示该笔(负数)冲减项已转成应收向对方收款，付款抵扣时须跳过，避免重复抵扣
    transferred_to_receivable TINYINT DEFAULT 0 COMMENT '是否已转应收: 0否 1是',
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_bill_no (bill_no),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_supplier_type (supplier_type),
    INDEX idx_status (status),
    INDEX idx_source_bill_no (source_bill_no),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应付台账表';

-- ==================== 应付转应收单 ====================

-- 场景：退货/超损扣款产生的是负向应付，正常在下次付款时净额抵扣；
-- 当月无货款可抵时，用它把该笔冲减项转为「向供应商收款」的应收，走收款单核销。
CREATE TABLE IF NOT EXISTS finance_payable_transfer (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    code            VARCHAR(50) NOT NULL   COMMENT '转应收单号(PZ-yyyyMMdd-NNN)',
    payable_id      BIGINT NOT NULL        COMMENT '来源应付台账ID(负数冲减项)',
    payable_bill_no VARCHAR(50)            COMMENT '来源应付台账单号(冗余，便于列表展示)',
    supplier_id     BIGINT NOT NULL        COMMENT '供应商/加工厂ID',
    supplier_name   VARCHAR(100)           COMMENT '供应商名称(冗余)',
    supplier_type   VARCHAR(30)            COMMENT '往来主体类型: product/factory/material/solution',
    amount          DECIMAL(18,4) DEFAULT 0 COMMENT '转出金额(正数,取来源应付的绝对值)',
    transfer_date   DATE                   COMMENT '转应收日期',
    status          VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT=草稿 AUDITED=已审核 CANCELLED=已作废',
    remark          VARCHAR(500)           COMMENT '备注',
    auditor_id      BIGINT                 COMMENT '审核人ID',
    auditor_name    VARCHAR(50)            COMMENT '审核人姓名',
    audit_time      DATETIME               COMMENT '审核时间',
    company_id      BIGINT DEFAULT NULL    COMMENT '公司ID',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_payable_id (payable_id),
    INDEX idx_supplier_id (supplier_id),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应付转应收单';

CREATE TABLE IF NOT EXISTS finance_receipt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '收款单ID',
    code VARCHAR(50) NOT NULL COMMENT '收款单号',
    customer_id BIGINT COMMENT '客户ID',
    customer_name VARCHAR(100) COMMENT '客户名称',
    -- 供应商收款：应付转应收产生的供应商应收（subject_type=SUPPLIER），向对方收回退货/扣款
    subject_type VARCHAR(20) DEFAULT 'CUSTOMER' COMMENT '往来主体类型: CUSTOMER=客户 SUPPLIER=供应商',
    supplier_id BIGINT DEFAULT NULL COMMENT '供应商ID(subject_type=SUPPLIER 时有值)',
    supplier_name VARCHAR(100) DEFAULT NULL COMMENT '供应商名称(冗余留痕)',
    account_id BIGINT COMMENT '收款账户ID',
    account_name VARCHAR(100) COMMENT '收款账户名称',
    receipt_date DATE COMMENT '收款日期',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '收款金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: 草稿/已审核/已作废',
    -- 2026-09-18：来源单据（销售单现金结算时由「审核销售单」自动生成草稿收款单；
    -- 销售单反审核需按此精确定位并联动：草稿→自动作废、已审核→拦住提示先撤收款）
    source_bill_type VARCHAR(40) DEFAULT NULL COMMENT '来源单据类型(SourceBillType code) 如 SALE_ORDER',
    source_bill_no VARCHAR(50) DEFAULT NULL COMMENT '来源单据号',
    source_id BIGINT DEFAULT NULL COMMENT '来源单据ID',
    remark VARCHAR(500) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_customer_id (customer_id),
    INDEX idx_account_id (account_id),
    INDEX idx_source_bill (source_bill_type, source_id),
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
    -- 主体类型在创建时按供应商标签/核销应付固化，列表可按类型筛选
    supplier_type VARCHAR(30) DEFAULT NULL COMMENT '往来主体类型: product/factory/material/solution',
    supplier_name VARCHAR(100) COMMENT '供应商名称',
    account_id BIGINT COMMENT '付款账户ID',
    account_name VARCHAR(100) COMMENT '付款账户名称',
    payment_date DATE COMMENT '付款日期',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '付款金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: 草稿/已审核/已作废',
    remark VARCHAR(500) COMMENT '备注',
    attach_url VARCHAR(500) DEFAULT NULL COMMENT '付款凭证',
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
    remark VARCHAR(255) COMMENT '备注',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    -- F7-39（2026-09-19）：流水号补唯一约束 —— 原先 flow_no **无任何索引**，
    -- 单号生成的 catch 兜底（seq=1）会静默重号，且无 DB 兜底。
    UNIQUE KEY uk_flow_no (flow_no),
    INDEX idx_account_id (account_id),
    INDEX idx_flow_type (flow_type),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金流水表';

CREATE TABLE IF NOT EXISTS finance_expense (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '费用单ID',
    expense_no VARCHAR(50) NOT NULL COMMENT '费用单号',
    expense_type VARCHAR(50) COMMENT '费用类型(存code): OFFICE/RENT/SALARY/TRANSPORT/TRAVEL/ENTERTAIN/RND=研发支出/OTHER',
    amount DECIMAL(18,4) NOT NULL COMMENT '费用金额',
    expense_date DATE COMMENT '费用日期（利润表按此归月）',
    account_id BIGINT COMMENT '支出账户ID',
    account_name VARCHAR(100) COMMENT '支出账户名称',
    remark VARCHAR(500) COMMENT '备注',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT/AUDITED/CANCELLED',
    company_id BIGINT COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    -- 2026-09-27：来源引用三列（命名与 finance_receivable 一致）—— 只给"由别的业务对象带出来的"费用单用，
    -- 目前唯一来源：物料信息管理「新增物料 → 同时登记研发支出」(RD_MATERIAL + source_id=outsource_material.id)。
    -- 作用：幂等（同一物料不重复建） + 可追溯。手工登记的费用单这三列为 NULL。
    source_bill_type VARCHAR(30) DEFAULT NULL COMMENT '来源类型(存code): RD_MATERIAL=物料研发支出',
    source_id BIGINT DEFAULT NULL COMMENT '来源对象ID(如 outsource_material.id)',
    source_bill_no VARCHAR(50) DEFAULT NULL COMMENT '来源单号(费用单目前为空，保留与应收同构)',
    -- F7-39（2026-09-19）：费用单号补唯一约束（原先 expense_no 无任何索引，catch 兜底 seq=1 可静默重号）
    UNIQUE KEY uk_expense_no (expense_no),
    INDEX idx_expense_type (expense_type),
    INDEX idx_expense_date (expense_date),
    INDEX idx_status (status),
    INDEX idx_expense_source (source_bill_type, source_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用登记表';

-- ==================== 发票登记表（税务口径：销项/进项） ====================

CREATE TABLE IF NOT EXISTS finance_invoice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '发票ID',
    invoice_no VARCHAR(50) NOT NULL COMMENT '发票号码',
    direction VARCHAR(20) NOT NULL COMMENT '方向: SALE=销项 PURCHASE=进项',
    invoice_kind VARCHAR(30) COMMENT '发票类型: 增值税专用发票/增值税普通发票/电子专票/电子普票',
    invoice_date DATE COMMENT '开票日期',
    partner_name VARCHAR(100) COMMENT '对方单位(销项=购买方, 进项=销售方)',
    amount DECIMAL(18,4) DEFAULT 0 COMMENT '不含税金额',
    tax_rate DECIMAL(18,4) DEFAULT 0 COMMENT '税率(%)',
    tax_amount DECIMAL(18,4) DEFAULT 0 COMMENT '税额',
    total_amount DECIMAL(18,4) DEFAULT 0 COMMENT '价税合计',
    source_bill_code VARCHAR(50) COMMENT '关联业务单号(销售单/采购单号, 可选)',
    remark VARCHAR(500) COMMENT '备注',
    status VARCHAR(20) DEFAULT 'REGISTERED' COMMENT '状态: REGISTERED=已登记 CANCELLED=已作废',
    company_id BIGINT COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_invoice_no (invoice_no),
    INDEX idx_direction (direction),
    INDEX idx_invoice_date (invoice_date),
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票登记表';

-- ==================== 入库批次成本记录（移动加权平均成本回滚依据） ====================

CREATE TABLE IF NOT EXISTS cost_inbound_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    target_type VARCHAR(20) NOT NULL COMMENT '成本对象类型: PRODUCT/MATERIAL',
    target_id BIGINT NOT NULL COMMENT '成本对象ID',
    change_type VARCHAR(50) COMMENT '库存变动类型',
    related_bill_id BIGINT COMMENT '关联单据ID(反审核冲销依据)',
    related_bill_no VARCHAR(50) COMMENT '关联单号',
    quantity DECIMAL(18,0) DEFAULT 0 COMMENT '入库数量',
    unit_cost DECIMAL(18,4) DEFAULT 0 COMMENT '入库单价',
    total_cost DECIMAL(18,4) DEFAULT 0 COMMENT '入库总成本',
    cost_after DECIMAL(18,4) COMMENT '入库后加权成本快照',
    company_id BIGINT COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_target (target_type, target_id),
    INDEX idx_bill (change_type, related_bill_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库批次成本记录表';

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
    status VARCHAR(20) DEFAULT 'UNSETTLED' COMMENT '状态: 未结清/已结清',
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
    source_id BIGINT DEFAULT NULL COMMENT '来源台账ID(应付/应收台账主键，核销联动用)',
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

CREATE TABLE IF NOT EXISTS finance_settlement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '核销流水ID',
    receipt_payment_id BIGINT NOT NULL COMMENT '收付款单ID(付款单/收款单)',
    payable_receivable_id BIGINT NOT NULL COMMENT '应付/应收台账ID',
    amount DECIMAL(18,4) NOT NULL COMMENT '本次核销金额',
    direction VARCHAR(20) NOT NULL COMMENT '核销方向: PAY(付款)/RECEIVE(收款)',
    source_type VARCHAR(30) COMMENT '来源单据类型: PAYMENT/RECEIPT(预留BILL)',
    source_id BIGINT COMMENT '来源单据ID',
    status VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '核销状态: NORMAL=有效/CANCELLED=已冲销(反审核留痕，不物理删除)',
    company_id BIGINT DEFAULT NULL COMMENT '公司ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_receipt_payment_id (receipt_payment_id),
    INDEX idx_payable_receivable_id (payable_receivable_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核销流水表';

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
    status      VARCHAR(20) DEFAULT 'AUDITED'         COMMENT '状态',
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
    material_type_id             BIGINT DEFAULT NULL     COMMENT '物料类型ID(关联material_type.id)',
    unit                    VARCHAR(20)              COMMENT '单位',
    quantity                DECIMAL(18,0) DEFAULT 0 COMMENT '数量',
    unit_price              DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    remark                  VARCHAR(255)            COMMENT '备注',
    company_id              BIGINT DEFAULT NULL      COMMENT '公司ID',
    INDEX idx_other_io_id (other_io_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外协其他出入库明细表';

-- ==================== 委外物料报损 ====================

CREATE TABLE IF NOT EXISTS outsource_stock_loss (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '报损单ID',
    code           VARCHAR(50) NOT NULL  COMMENT '报损单号(WBS-yyyyMMdd-NNN)',
    warehouse_id   BIGINT NOT NULL       COMMENT '报损仓库ID(委外仓/自有物料仓)',
    warehouse_name VARCHAR(100)          COMMENT '仓库名称(冗余)',
    loss_date      DATE                  COMMENT '报损日期',
    loss_reason    VARCHAR(30)           COMMENT '报损原因: DAMAGE=破损 EXPIRED=变质过期 LOST=丢失 QUALITY=质量不合格 OTHER=其他',
    total_amount   DECIMAL(18,2) DEFAULT 0 COMMENT '报损总金额(明细金额合计,冗余便于列表展示)',
    status         VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT=草稿 AUDITED=已审核 CANCELLED=已作废',
    remark         VARCHAR(500)          COMMENT '备注',
    auditor_id     BIGINT                COMMENT '审核人ID',
    auditor_name   VARCHAR(50)           COMMENT '审核人姓名',
    audit_time     DATETIME              COMMENT '审核时间',
    company_id     BIGINT DEFAULT NULL   COMMENT '公司ID',
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_warehouse_id (warehouse_id),
    INDEX idx_status (status),
    INDEX idx_loss_reason (loss_reason),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外物料报损单主表';

CREATE TABLE IF NOT EXISTS outsource_stock_loss_item (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细ID',
    loss_id        BIGINT NOT NULL        COMMENT '报损单ID',
    material_id    BIGINT NOT NULL        COMMENT '委外物料ID(关联outsource_material.id)',
    material_name  VARCHAR(100)           COMMENT '物料名称(冗余)',
    material_type_id    BIGINT                 COMMENT '物料类型ID(关联material_type.id)',
    material_type_name  VARCHAR(50)            COMMENT '物料类型名称(冗余)',
    quality_type   VARCHAR(10) DEFAULT 'GOOD' COMMENT '品质: GOOD=良品 DEFECT=不良品',
    -- 2026-09-21：删除 F7-125 已批准并在现库执行过的死列 spec（产品规格 2026-09-15 全站下线；
    -- 现网全 NULL、实体无字段）。当时只在现库 DROP、本文件漏同步。
    unit           VARCHAR(20)            COMMENT '单位(冗余)',
    quantity       DECIMAL(18,0) DEFAULT 0 COMMENT '报损数量',
    unit_price     DECIMAL(18,4) DEFAULT 0 COMMENT '报损单价(带出物料最近进价,可改)',
    amount         DECIMAL(18,2) DEFAULT 0 COMMENT '报损金额(数量×单价)',
    remark         VARCHAR(255)           COMMENT '备注',
    company_id     BIGINT DEFAULT NULL    COMMENT '公司ID',
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_loss_id (loss_id),
    INDEX idx_material_id (material_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外物料报损单明细';

-- ==================== 外协物料组件 ====================

CREATE TABLE IF NOT EXISTS outsource_material_component (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    parent_outsource_material_id    BIGINT NOT NULL   COMMENT '父物料ID',
    child_outsource_material_id     BIGINT NOT NULL   COMMENT '子物料ID',
    quantity                        DECIMAL(18,0) DEFAULT 1 COMMENT '数量',
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
    sku             VARCHAR(64) DEFAULT NULL           COMMENT 'SKU编码(产品级唯一；新增留空由后端自动生成 SKU-000001)',
    brand_id        BIGINT DEFAULT NULL               COMMENT '品牌ID',
    supplier_id     BIGINT DEFAULT NULL               COMMENT '供货商ID(关联 supplier.id，类型=product 的供货商；新增该产品时 SKU 前缀取该供货商的 supply_sku)',
    spec_type       VARCHAR(20)                       COMMENT '规格: ORIGINAL原装/MATCHED原配/MODIFIED改配（产品管理必填；研发立项自动建产品可为空，之后补填）',
    general_model   VARCHAR(100) DEFAULT NULL          COMMENT '通用型号(适用多款机型)',
    unit            VARCHAR(20) DEFAULT 'pcs'          COMMENT '单位',
    safety_stock    DECIMAL(18,0) DEFAULT 0           COMMENT '安全库存',
    cost_price      DECIMAL(18,4) DEFAULT NULL         COMMENT '移动加权平均成本价(入库自动更新)',
    cost_manual     TINYINT DEFAULT 0                  COMMENT '成本价是否手工锁定 0否 1是',
    last_in_price   DECIMAL(18,4) DEFAULT NULL         COMMENT '最近入库单价',
    status          VARCHAR(20) DEFAULT 'NORMAL'         COMMENT '状态: NORMAL/DISCONTINUED/DEVELOPING',
    project_id      BIGINT DEFAULT NULL               COMMENT '关联项目ID',
    remark          VARCHAR(255)                      COMMENT '备注',
    company_id      BIGINT DEFAULT NULL               COMMENT '公司ID',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_company_id (company_id),
    INDEX idx_supplier_id (supplier_id),
    UNIQUE KEY uk_company_sku (company_id, sku)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成品主数据表';

-- ==================== 备忘录模块 ====================

CREATE TABLE IF NOT EXISTS memo (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '备忘录ID',
    title           VARCHAR(200) NOT NULL              COMMENT '标题',
    status          VARCHAR(20) DEFAULT 'OPEN'          COMMENT '状态: OPEN进行中/CLOSED关闭',
    user_id         BIGINT NOT NULL                    COMMENT '所属用户ID',
    company_id      BIGINT DEFAULT NULL                COMMENT '公司ID',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备忘录主表';

CREATE TABLE IF NOT EXISTS memo_progress (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '进度ID',
    memo_id         BIGINT NOT NULL                    COMMENT '关联备忘录ID',
    content         VARCHAR(1000) NOT NULL             COMMENT '进度内容',
    user_id         BIGINT NOT NULL                    COMMENT '所属用户ID',
    company_id      BIGINT DEFAULT NULL                COMMENT '公司ID',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_memo_id (memo_id),
    INDEX idx_user_id (user_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备忘录进度明细表';

-- ==================== 兼容视图：外协订单物料（BOM快照） ====================
-- 2026-09-17 BOM 快照重构：原 outsource_order_material 表由「每单一份明细」改为
-- 「bom_snapshot + bom_snapshot_item 共享快照」，本视图把共享快照按加工单产品行展开，
-- 对外字段与旧表完全一致（id/product_id/outsource_material_id/material_type_id/unit/
-- quantity_per_set/demand_quantity/loss_rate/supply_type/remark/company_id/create_time），
-- 因此所有既有查询（orderService.getMaterials、缺料判定、交货扣料、结单超损、退货快照、
-- 供应商物料需求、合同导出）无需改动。
--   · demand_quantity = 单套用量 × 该产品行数量（取整）—— 与重构前前端算法一致；
--   · 只读视图：写入请改 bom_snapshot(_item)，如 CREATE/UPDATE 加工单时写快照并回填
--     outsource_order_product.bom_snapshot_id。
-- 依赖顺序：必须位于 bom_snapshot_item / outsource_order_product 之后（本文件末尾）。
CREATE OR REPLACE VIEW outsource_order_material AS
SELECT si.id AS id,
       op.id AS product_id,
       si.outsource_material_id AS outsource_material_id,
       si.material_type_id AS material_type_id,
       si.unit AS unit,
       si.quantity_per_set AS quantity_per_set,
       ROUND(si.quantity_per_set * IFNULL(op.quantity, 0)) AS demand_quantity,
       si.loss_rate AS loss_rate,
       si.supply_type AS supply_type,
       si.remark AS remark,
       si.company_id AS company_id,
       si.create_time AS create_time
FROM bom_snapshot_item si
INNER JOIN outsource_order_product op ON op.bom_snapshot_id = si.snapshot_id;
