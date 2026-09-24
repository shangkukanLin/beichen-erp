-- Archive before stock_form migration (2026-09-25 P0-2)
-- warehouse_stock rows|qty = 78|14572
-- warehouse_stock_log rows  = 2128
-- OUTSOURCE stock rows|qty  = 29|9129
-- INVENTORY stock rows|qty  = 49|5443

*************************** 1. row ***************************
warehouse_stock
CREATE TABLE `warehouse_stock` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `warehouse_id` bigint NOT NULL COMMENT '仓库ID',
  `product_id` bigint DEFAULT NULL COMMENT '产品ID(关联product表，自有仓成品库存)',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID(关联outsource_material表，委外仓物料库存)',
  `quality_type` varchar(20) DEFAULT 'A' COMMENT '品质等级：成品(product_id非空)用 A/B/C/DEFECT/PENDING；委外物料(material_id非空)用 GOOD/DEFECT。两体系互斥，GOOD 仅用于物料',
  `quantity` decimal(18,0) DEFAULT '0',
  `available_quantity` decimal(18,0) DEFAULT '0',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wh_prod_quality_company` (`warehouse_id`,`product_id`,`quality_type`,`company_id`),
  UNIQUE KEY `uk_wh_material_company` (`warehouse_id`,`material_id`,`company_id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_material_id` (`material_id`)
) ENGINE=InnoDB AUTO_INCREMENT=238 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一库存表'

*************************** 1. row ***************************
warehouse_stock_log
CREATE TABLE `warehouse_stock_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `warehouse_id` bigint NOT NULL COMMENT '仓库ID',
  `product_id` bigint DEFAULT NULL COMMENT '产品ID(自有仓成品流水)',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID(委外仓物料流水)',
  `material_name` varchar(100) DEFAULT NULL COMMENT '物料名称',
  `quality_type` varchar(20) DEFAULT NULL COMMENT '品质等级：成品(product_id非空)用 A/B/C/DEFECT/PENDING；委外物料(material_id非空)用 GOOD/DEFECT。两体系互斥，GOOD 仅用于物料',
  `change_type` varchar(50) NOT NULL COMMENT '变动类型',
  `change_quantity` decimal(18,0) DEFAULT '0',
  `before_quantity` decimal(18,0) DEFAULT '0',
  `after_quantity` decimal(18,0) DEFAULT '0',
  `related_bill_no` varchar(100) DEFAULT NULL COMMENT '关联单据号',
  `related_bill_type` varchar(50) DEFAULT NULL COMMENT '关联单据类型',
  `related_bill_id` bigint DEFAULT NULL COMMENT '关联单据ID',
  `related_delivery_id` bigint DEFAULT NULL COMMENT '关联发货单ID(委外物料流水使用)',
  `related_order_code` varchar(30) DEFAULT NULL COMMENT '关联加工单号(委外物料流水使用)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_related_bill_no` (`related_bill_no`)
) ENGINE=InnoDB AUTO_INCREMENT=3908 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一库存流水表'
