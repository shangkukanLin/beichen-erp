
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `dev_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dev_project` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  `code` varchar(50) NOT NULL COMMENT '项目编号',
  `name` varchar(100) NOT NULL COMMENT '项目名称',
  `assembly_name` varchar(100) DEFAULT NULL COMMENT '总成名称',
  `product_id` bigint DEFAULT NULL COMMENT '关联产品ID(product.id)，与产品表双向关联',
  `brand_id` bigint DEFAULT NULL COMMENT '品牌ID(brand.id)',
  `display_supplier_name` varchar(100) DEFAULT NULL COMMENT '显示方案供应商',
  `touch_supplier_name` varchar(100) DEFAULT NULL COMMENT '触摸方案供应商',
  `adapt_model` varchar(100) DEFAULT NULL COMMENT '适配机型',
  `original_size` varchar(50) DEFAULT NULL COMMENT '原始尺寸',
  `original_resolution` varchar(50) DEFAULT NULL COMMENT '原始分辨率',
  `original_drive_ic` varchar(100) DEFAULT NULL COMMENT '原机驱动IC型号',
  `original_touch_ic` varchar(100) DEFAULT NULL COMMENT '原机触摸IC型号',
  `glass_size` varchar(50) DEFAULT NULL COMMENT '玻璃尺寸',
  `glass_resolution` varchar(50) DEFAULT NULL COMMENT '玻璃分辨率',
  `config_drive_ic_id` bigint DEFAULT NULL COMMENT '改配驱动IC物料ID(outsource_material.id)',
  `config_touch_ic_id` bigint DEFAULT NULL COMMENT '改配触摸IC物料ID(outsource_material.id)',
  `config_code_ic_id` bigint DEFAULT NULL COMMENT '改配码片IC物料ID(outsource_material.id)',
  `project_leader_id` bigint DEFAULT NULL COMMENT '项目负责人ID',
  `sample_factory_id` bigint DEFAULT NULL COMMENT '样品工厂ID',
  `outsource_factory_id` bigint DEFAULT NULL COMMENT '外协工厂ID',
  `start_date` date DEFAULT NULL COMMENT '开始日期',
  `expected_end_date` date DEFAULT NULL COMMENT '预计结束日期',
  `actual_end_date` date DEFAULT NULL COMMENT '实际结束日期',
  `status` varchar(20) DEFAULT 'IN_PROGRESS' COMMENT '项目状态(项目阶段自动推导)',
  `cancelled_at` datetime DEFAULT NULL COMMENT '取消时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_status` (`status`),
  KEY `idx_project_leader_id` (`project_leader_id`),
  KEY `idx_company_id` (`company_id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='研发项目表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `dev_project` WRITE;
/*!40000 ALTER TABLE `dev_project` DISABLE KEYS */;
INSERT INTO `dev_project` VALUES (10,'DEV-260918-001','MFTESTE2E1','MFTESTE2E1',62,13,'','','','6.1','1080x2340','RM692E5','FT8006','6.1','1080x2340',35,53,54,NULL,NULL,NULL,'2026-09-18',NULL,NULL,'IN_PROGRESS',NULL,'',1,'2026-09-18 02:22:35','2026-09-18 02:22:35'),(11,'DEV-260918-002','MFTESTE2E2','MFTESTE2E2',63,13,'','','','6.1','1080x2340','RM692E5','FT8006','6.1','1080x2340',35,53,54,NULL,NULL,NULL,'2026-09-18',NULL,'2026-09-20','CLOSED',NULL,'',1,'2026-09-18 02:23:01','2026-09-18 02:23:00'),(12,'DEV-260921-001','PROBE-PHASE-082916',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-09-21','CLOSED',NULL,'probe for the complete-all verification; safe to cancel',1,'2026-09-21 08:29:16','2026-09-21 08:29:16'),(13,'DEV-260921-002','PROBE-PHASE-C-082916',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'CANCELLED','2026-09-21 08:29:17','probe for the complete-all verification; safe to cancel',1,'2026-09-21 08:29:17','2026-09-21 08:29:16'),(14,'DEV-260921-003','PROBE-PHASE-UI-083023',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-09-21','CLOSED',NULL,'probe for the complete-all verification; safe to cancel',1,'2026-09-21 08:30:24','2026-09-21 08:30:23'),(15,'DEV-260921-004','PROBE-PHASE-UI-083112',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-09-21','CLOSED',NULL,'probe for the complete-all verification; safe to cancel',1,'2026-09-21 08:31:13','2026-09-21 08:31:12'),(16,'DEV-260921-005','PROBE-PHASE-S-083148',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-09-21','CLOSED',NULL,'probe for the complete-all verification; safe to cancel',1,'2026-09-21 08:31:49','2026-09-21 08:31:48');
/*!40000 ALTER TABLE `dev_project` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

