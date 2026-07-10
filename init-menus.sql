-- 北辰ERP 菜单和角色权限初始化 SQL
-- 一级菜单
INSERT IGNORE INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,route_name,icon,sort_order,visible,status) VALUES
(1,0,'首页','menu','/dashboard','Dashboard','HomeFilled',1,1,1),
(2,0,'研发管理','catalog','','','Cpu',2,1,1),
(4,0,'委外加工','catalog','','','Setting',4,1,1),
(5,0,'进销存','catalog','','','Goods',5,1,1),
(6,0,'财务管理','catalog','','','Money',6,1,1),
(7,0,'设置','catalog','','','Tools',7,1,1);

-- 研发管理子菜单
INSERT IGNORE INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,route_name,icon,sort_order,visible,status) VALUES
(11,2,'方案商','menu','/supplier/solution','SupplierSolution','Connection',1,1,1),
(33,2,'BOM类型','menu','/dev/bom-type','DevBomType','Tickets',2,1,1),
(8,2,'研发项目','menu','/dev/project','DevProject','Notebook',3,1,1),
(10,2,'图纸文档','menu','/dev/drawing','DevDrawing','Files',4,1,1),
(9,2,'BOM管理','menu','/dev/bom','DevBom','Tickets',5,1,1);

-- 委外加工子菜单
INSERT IGNORE INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,route_name,icon,sort_order,visible,status) VALUES
(15,4,'委外加工单','menu','/outsource/order','OutsourceOrder','Document',1,1,1),
(16,4,'物料信息','menu','/outsource/material-info','OutsourceMaterialInfo','Switch',2,1,1),
(12,4,'委外加工厂','menu','/supplier/factory','SupplierFactory','OfficeBuilding',3,1,1),
(14,4,'辅料商','menu','/supplier/material-supplier','SupplierMaterialSupplier','Box',4,1,1),
(35,4,'委外仓库','menu','/outsource/warehouse','OutsourceWarehouse','Odometer',5,1,1),
(37,4,'加工合同模板','menu','/outsource/contract-template','OutsourceContractTemplate','Document',6,1,1);

-- 进销存子菜单
INSERT IGNORE INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,route_name,icon,sort_order,visible,status) VALUES
(39,5,'品牌管理','menu','/inventory/brand','InventoryBrand','CollectionTag',1,1,1),
(18,5,'客户管理','menu','/inventory/customer','InventoryCustomer','User',2,1,1),
(19,5,'产品管理','menu','/material','MaterialManage','TakeawayBox',3,1,1),
(20,5,'采购单','menu','/inventory/purchase','InventoryPurchase','ShoppingCart',4,1,1),
(13,5,'成品供应商','menu','/supplier/product','SupplierProduct','GoodsFilled',5,1,1),
(22,5,'成品库存','menu','/inventory/stock','InventoryStock','Odometer',6,1,1),
(23,5,'销售单','menu','/inventory/sale','InventorySale','Sell',7,1,1),
(36,5,'仓库管理','menu','/inventory/warehouse','InventoryWarehouse','Odometer',1,1,1);

-- 财务管理子菜单
INSERT IGNORE INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,route_name,icon,sort_order,visible,status) VALUES
(25,6,'应收管理','menu','/finance/receivable','FinanceReceivable','Wallet',1,1,1),
(26,6,'应付管理','menu','/finance/payable','FinancePayable','CreditCard',2,1,1),
(27,6,'账单生成','menu','/finance/bill','FinanceBill','Postcard',3,1,1),
(28,6,'资金流水','menu','/finance/cashflow','FinanceCashflow','TrendCharts',4,1,1);

-- 设置子菜单
INSERT IGNORE INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,route_name,icon,sort_order,visible,status) VALUES
(40,7,'智能管理','menu','/system/smart','SystemSmart','Cpu',1,1,1),
(41,7,'系统设置','catalog','','','Tools',2,1,1),
(42,7,'清空数据','menu','/system/clear-data','SystemClearData','Delete',3,1,1),
(43,41,'系统信息','menu','/system/settings','SystemSettings','Setting',1,1,1),
(29,41,'用户管理','menu','/system/user','SystemUser','UserFilled',2,1,1),
(30,41,'角色管理','menu','/system/role','SystemRole','Avatar',3,1,1),
(31,41,'菜单管理','menu','/system/menu','SystemMenu','Menu',4,1,1);

-- 角色-菜单权限 (super_admin: 全部菜单)
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu;
-- admin (排除菜单管理31)
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) SELECT 2, id FROM sys_menu WHERE id != 31;

SELECT 'Menus initialized: ' AS result, COUNT(*) AS count FROM sys_menu;
SELECT 'Role menus: ' AS result, COUNT(*) AS count FROM sys_role_menu;
