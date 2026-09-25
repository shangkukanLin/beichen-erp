-- =====================================================================================
-- 迁移：加工返回单两表（2026-09-25，P1-2）
-- 目的：无单加工退货的"回来"腿独立单据化——核销在厂成品（PRODUCT_DEFECT）+ 修好成品回我方仓
--       + 实际用料多行（可超 BOM）从委外仓扣减 + 料款按 FIFO 生成对加工厂的应收（工厂赔料）。
-- 安全性：纯新增两张表（CREATE TABLE IF NOT EXISTS，幂等），不触碰任何既有表与数据。
-- 执行：mysql -uroot -proot beichen_erp -e "source 本脚本路径"
-- =====================================================================================

CREATE TABLE IF NOT EXISTS outsource_return_back (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code VARCHAR(50) NOT NULL COMMENT '返回单号(ORB-)',
    factory_id BIGINT NOT NULL COMMENT '加工厂ID(supplier.id)：赔料应收对象',
    factory_name VARCHAR(100) COMMENT '加工厂名称快照',
    product_id BIGINT NOT NULL COMMENT '产品主数据ID(product.id)',
    product_name VARCHAR(100) COMMENT '产品名称快照',
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
    INDEX idx_status (status),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外加工返回单';

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
