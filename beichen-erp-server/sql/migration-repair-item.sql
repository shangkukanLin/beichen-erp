-- =====================================================================================
-- 迁移：维修返回明细子表（2026-09-25，P2-1）
-- 目的：维修退货送修 = 成品（维修退货）转移进委外仓（PRODUCT_REPAIR）；维修返回登记 =
--       核销在厂行 + 成品回我方仓 + 实际用料多行（可超 BOM）从委外仓扣 + FIFO 成本结转；无赔料应收。
-- 安全性：纯新增一张子表（CREATE TABLE IF NOT EXISTS，幂等），不触碰任何既有表与数据。
-- 执行：mysql -uroot -proot beichen_erp -e "source 本脚本路径"
-- =====================================================================================

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
