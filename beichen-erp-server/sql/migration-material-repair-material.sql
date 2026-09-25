-- =====================================================================================
-- 迁移：物料维修返回实际用料明细子表（2026-09-25，物料形态化）
-- 背景：物料送修以 stock_form=MATERIAL_REPAIR 进供应商委外仓（对齐成品 P2-1）；
--       返回登记按实际用料（子物料，可超 BOM、允许扣负）扣供应商委外仓 + FIFO 结转成本；无赔料应收。
-- 安全性：纯新增一张子表（CREATE TABLE IF NOT EXISTS，幂等），不触碰既有表与数据。
-- 执行：mysql -uroot -proot beichen_erp -e "source 本脚本路径"
-- =====================================================================================

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
