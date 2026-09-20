-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: beichen_erp
-- ------------------------------------------------------
-- Server version	8.0.46

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

--
-- Table structure for table `outsource_contract_template`
--

DROP TABLE IF EXISTS `outsource_contract_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `outsource_contract_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  `template_name` varchar(100) NOT NULL COMMENT '模板名称',
  `content` text COMMENT '合同模板内容',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0禁用',
  `is_default` tinyint DEFAULT '0' COMMENT '0非默认 1默认模板',
  `template_type` varchar(20) DEFAULT 'PROCESSING' COMMENT '模板类型：加工合同/采购合同',
  `party_a_address` varchar(255) DEFAULT NULL COMMENT '甲方地址',
  `party_a_contact` varchar(50) DEFAULT NULL COMMENT '甲方联系人',
  `party_a_phone` varchar(20) DEFAULT NULL COMMENT '甲方联系电话',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_company_id` (`company_id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外协合同模板表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `outsource_contract_template`
--

/*!40000 ALTER TABLE `outsource_contract_template` DISABLE KEYS */;
INSERT INTO `outsource_contract_template` (`id`, `template_name`, `content`, `status`, `is_default`, `template_type`, `party_a_address`, `party_a_contact`, `party_a_phone`, `company_id`, `create_time`, `update_time`) VALUES (11,'委外加工合同','<h1 style=\"text-align: center;\">委外加工合同</h1><p><br></p><p>就甲方委托乙方加工生产本协议所列产品事宜，经双方友好协商，达成如下条款：</p><p><br></p><h3>订单备注</h3><p>1、订单一经确认回传即刻具备法律效力。</p><p>2、乙方需按照甲方订单要求的产品型号，规格及数量加工质量合格的产品。</p><p>3、乙方收到甲方物料后需两天内确认好实际到货数量与订单数量是否相符，如有偏差应立刻向甲方反馈，超过两天未提出异议则默认到货数量无误。</p><p>4、乙方应妥善保管相关物料，如有损坏，丢失，则由乙方照价赔偿。</p><p>5、乙方收到物料之日起，7个工作日内交货，交货后3个工作日内结单。</p><p>6、乙方不得随意改变生产工艺及配套辅料。如需调整工艺或配套辅料，应先打样由甲方确认，样品通过甲方验证后方可调整，否则产生的一切损失由乙方承担。</p><p>7、全新物料加工良率保98%以上（含贴片，绑定，贴合总成等全段工序）；旧物料加工良率原则上保96%以上，如发生良率超标时，由乙方照价赔偿。如遇特殊项目则以双方协商良率为准。</p><p>8、双方合作的新项目及新批次物料，乙方须先做小批量由甲方验证以后方可量产。</p><p>9、双方遵守保密原则，双方的所有资料（含商业资料和技术资料）均做好保密措施，未经允许不得外泄。</p>',1,1,'PROCESSING',NULL,NULL,NULL,1,'2026-09-18 01:58:31','2026-09-18 01:58:31'),(12,'物料采购合同','<h1 style=\"text-align: center;\">物料采购合同</h1><p><br></p><p>就甲方向乙方采购本协议所列物料事宜，经双方友好协商，达成如下条款：</p><p><br></p><p>1、订单一经确认回传即刻具备法律效力。</p><p>2、订单确认后，应如期交货，如有延迟必须告知甲方，获取甲方同意。</p><p>3、乙方需按照甲方订单需求的产品型号，规格及数量提供质量合格的产品。</p><p>4、如因乙方私自调整产品的材料或工艺等原因导致的产品质量问题，所产生的一切损失由乙方承担。</p><p>5、双方遵守保密原则，双方合作各项细节需做好保密措施，未经允许不得外泄。</p>',1,1,'PURCHASE',NULL,NULL,NULL,1,'2026-09-18 01:58:31','2026-09-18 01:58:31');
/*!40000 ALTER TABLE `outsource_contract_template` ENABLE KEYS */;

--
-- Table structure for table `outsource_material`
--

DROP TABLE IF EXISTS `outsource_material`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `outsource_material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `cost_price` decimal(18,4) DEFAULT NULL COMMENT '移动加权平均成本价(委外仓入库自动更新)',
  `cost_manual` tinyint DEFAULT '0' COMMENT '成本价是否手工锁定 0否 1是',
  `last_in_price` decimal(18,4) DEFAULT NULL COMMENT '最近入库单价',
  `project_ids` varchar(500) DEFAULT NULL COMMENT '关联项目ID列表(逗号分隔)',
  `warehouse_id` bigint DEFAULT NULL COMMENT '仓库ID',
  `material_name` varchar(100) NOT NULL COMMENT '物料名称',
  `material_type_id` bigint DEFAULT NULL COMMENT '物料类型ID(关联material_type.id)',
  `spec` varchar(100) DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(20) DEFAULT NULL COMMENT '单位',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0禁用',
  `price` decimal(18,2) DEFAULT '0.00' COMMENT '单价',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_company_id` (`company_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=96 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外协物料表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `outsource_material`
--

/*!40000 ALTER TABLE `outsource_material` DISABLE KEYS */;
INSERT INTO `outsource_material` (`id`, `cost_price`, `cost_manual`, `last_in_price`, `project_ids`, `warehouse_id`, `material_name`, `material_type_id`, `spec`, `unit`, `status`, `price`, `remark`, `company_id`, `create_time`, `update_time`) VALUES (33,12.2774,0,0.0000,'',NULL,'测试物料A1',64,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:06:53','2026-09-19 21:45:59'),(34,16.0000,0,0.0000,'',NULL,'测试物料A2',68,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:07:03','2026-09-19 21:52:42'),(35,15.1373,0,0.0000,'',NULL,'测试物料A3',65,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:07:14','2026-09-19 21:45:59'),(36,NULL,0,NULL,'',NULL,'测试物料A4',64,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:07:25','2026-09-18 02:07:25'),(37,NULL,0,NULL,'',NULL,'测试物料A5',68,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:07:36','2026-09-18 02:07:36'),(38,NULL,0,NULL,'',NULL,'测试物料A6',65,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:07:46','2026-09-18 02:07:47'),(39,NULL,0,NULL,'',NULL,'测试物料A7',64,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:07:57','2026-09-18 02:07:58'),(40,NULL,0,NULL,'',NULL,'测试物料A8',68,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:08:08','2026-09-18 02:08:08'),(41,NULL,0,NULL,'',NULL,'测试物料A9',65,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:08:19','2026-09-18 02:08:19'),(42,NULL,0,NULL,'',NULL,'测试物料A10',64,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:08:29','2026-09-18 02:08:30'),(43,NULL,0,NULL,'',NULL,'测试物料A11',68,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:08:40','2026-09-18 02:08:41'),(44,NULL,0,NULL,'',NULL,'测试物料A12',65,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:08:51','2026-09-18 02:08:51'),(45,NULL,0,NULL,'',NULL,'测试物料A13',64,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:09:01','2026-09-18 02:09:02'),(46,NULL,0,NULL,'',NULL,'测试物料A14',68,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:09:12','2026-09-18 02:09:13'),(47,NULL,0,NULL,'',NULL,'测试物料A15',65,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:09:23','2026-09-18 02:09:23'),(48,NULL,0,NULL,'',NULL,'测试物料A16',64,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:09:34','2026-09-18 02:09:34'),(49,NULL,0,NULL,'',NULL,'测试物料A17',68,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:09:44','2026-09-18 02:09:45'),(50,NULL,0,NULL,'',NULL,'测试物料A18',65,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:09:55','2026-09-18 02:09:56'),(51,NULL,0,NULL,'',NULL,'测试物料A19',64,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:10:06','2026-09-18 02:10:06'),(52,NULL,0,NULL,'',NULL,'测试物料A20',68,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:10:17','2026-09-18 02:10:17'),(53,NULL,0,NULL,'',NULL,'测试物料A21',66,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:20:49','2026-09-18 02:20:50'),(54,NULL,0,NULL,'',NULL,'测试物料A22',67,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:21:00','2026-09-18 02:21:01'),(55,NULL,0,NULL,'',NULL,'测试物料A23',66,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:21:11','2026-09-18 02:21:12'),(56,NULL,0,NULL,'',NULL,'测试物料A24',67,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:21:22','2026-09-18 02:21:22'),(57,NULL,0,NULL,'',NULL,'测试物料A25',66,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:21:33','2026-09-18 02:21:33'),(58,NULL,0,NULL,'',NULL,'测试物料A26',67,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:21:44','2026-09-18 02:21:44'),(59,NULL,0,NULL,'',NULL,'测试物料A27',66,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:21:55','2026-09-18 02:21:55'),(60,NULL,0,NULL,'',NULL,'测试物料A28',67,NULL,'PCS',1,0.00,'',1,'2026-09-18 02:22:06','2026-09-18 02:22:06'),(61,5.0000,0,5.0000,NULL,NULL,'排线-MFTESTE2E1',68,NULL,'PCS',1,0.00,NULL,1,'2026-09-18 02:22:35','2026-09-18 09:48:57'),(62,5.0000,0,0.0000,NULL,NULL,'排线-MFTESTE2E2',68,NULL,'PCS',1,0.00,NULL,1,'2026-09-18 02:23:00','2026-09-19 20:18:42');
/*!40000 ALTER TABLE `outsource_material` ENABLE KEYS */;

--
-- Table structure for table `outsource_stock_loss_item`
--

DROP TABLE IF EXISTS `outsource_stock_loss_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `outsource_stock_loss_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `loss_id` bigint NOT NULL COMMENT '报损单ID',
  `material_id` bigint NOT NULL COMMENT '委外物料ID(关联outsource_material.id)',
  `material_name` varchar(100) DEFAULT NULL COMMENT '物料名称(冗余)',
  `material_type_id` bigint DEFAULT NULL COMMENT '物料类型ID(关联material_type.id)',
  `material_type_name` varchar(50) DEFAULT NULL COMMENT '物料类型名称(冗余)',
  `quality_type` varchar(10) DEFAULT 'GOOD' COMMENT '品质: GOOD=良品 DEFECT=不良品',
  `spec` varchar(100) DEFAULT NULL COMMENT '规格(冗余)',
  `unit` varchar(20) DEFAULT NULL COMMENT '单位(冗余)',
  `quantity` decimal(18,0) DEFAULT '0',
  `unit_price` decimal(18,4) DEFAULT '0.0000' COMMENT '报损单价(带出物料最近进价,可改)',
  `amount` decimal(18,2) DEFAULT '0.00' COMMENT '报损金额(数量×单价)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_loss_id` (`loss_id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_company_id` (`company_id`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='委外物料报损单明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `outsource_stock_loss_item`
--

/*!40000 ALTER TABLE `outsource_stock_loss_item` DISABLE KEYS */;
INSERT INTO `outsource_stock_loss_item` (`id`, `loss_id`, `material_id`, `material_name`, `material_type_id`, `material_type_name`, `quality_type`, `spec`, `unit`, `quantity`, `unit_price`, `amount`, `remark`, `company_id`, `create_time`) VALUES (12,12,33,'测试物料A1',64,'玻璃','GOOD',NULL,'PCS',20,10.0000,200.00,'',1,'2026-09-18 09:55:29'),(13,13,33,'测试物料A1',64,'玻璃','GOOD',NULL,'PCS',20,10.0000,200.00,'',1,'2026-09-18 09:55:51'),(14,14,33,'测试物料A1',64,'玻璃','GOOD',NULL,'PCS',20,10.0000,200.00,'',1,'2026-09-18 09:56:12'),(15,15,33,'测试物料A1',64,'玻璃','GOOD',NULL,'PCS',2,10.0000,20.00,NULL,1,'2026-09-18 12:24:40'),(16,16,33,'测试物料A1',64,'玻璃','GOOD',NULL,'PCS',2,10.0000,20.00,NULL,1,'2026-09-18 12:25:14'),(17,17,33,'测试物料A1',64,'玻璃','GOOD',NULL,'PCS',2,10.0000,20.00,NULL,1,'2026-09-18 12:25:38'),(18,18,33,'测试物料A1',64,'玻璃','GOOD',NULL,'PCS',2,10.0000,20.00,NULL,1,'2026-09-18 12:26:04'),(19,19,33,'测试物料A1',64,'玻璃','GOOD',NULL,'PCS',2,10.0000,20.00,NULL,1,'2026-09-18 12:26:20'),(20,20,33,'测试物料A1',64,'玻璃','GOOD',NULL,'PCS',2,10.0000,20.00,NULL,1,'2026-09-19 18:21:23');
/*!40000 ALTER TABLE `outsource_stock_loss_item` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-20 16:01:54
