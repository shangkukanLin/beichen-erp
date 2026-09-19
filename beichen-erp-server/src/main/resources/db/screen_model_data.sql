-- 屏幕资料知识库种子数据（共 1268 条：折叠屏 94 + AMOLED 1174）
-- 由 Excel「北辰型号统计」自动生成，请勿手工编辑。
-- ⚠️ F7-99（2026-09-19）：screen_model 已改为**行业共享的单一知识库**（不参与租户隔离，company_id 恒为 NULL）。
--    下列 `INSERT ... SELECT ... FROM sys_company` 是「每个公司各一份」时代的写法，保留仅为兼容；
--    DataInitializer.initScreenModels() 在导入后会统一执行 `UPDATE screen_model SET company_id = NULL`，
--    因此最终落在「共享一份」的语义上。下次重新生成种子文件时，建议直接改为插入 company_id = NULL 的单份数据。
-- DataInitializer 仅在表为空时才执行本文件。
-- 重要：一条 INSERT 占多行、以分号结尾，请按「语句」整体执行，切勿逐行执行（逐行会语法错误）。
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Fold', '7.3英寸', '1536 x 2152', '折叠右侧右置双孔', '120Hz', '4.6英寸', '720 x 1680', '直板屏', '60Hz', '侧装', '三星显示', '2/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Fold 5G', '7.3英寸', '1536 x 2152', '折叠右侧右置双孔', '120Hz', '4.6英寸', '720 x 1680', '直板屏', '60Hz', '侧装', '三星显示', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Flip', '6.7英寸', '1080 x 2636', '小折叠中置单孔', '120Hz', '1.1英寸', '112 x 300', '异形屏', '60Hz', '侧装', '三星显示', '2/11/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Flip 5G', '6.7英寸', '1080 x 2636', '小折叠中置单孔', '120Hz', '1.1英寸', '112 x 300', '异形屏', '60Hz', '侧装', '三星显示', '7/22/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Fold 2 5G', '7.6英寸', '1768 x 2208', '折叠右侧中置单孔', '120Hz', '6.23英寸', '816 x 2260', '直面中置单孔', '120Hz', '侧装', '三星显示', '8/5/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Fold 3', '7.6英寸', '1768 x 2208', '折叠右侧中置单孔', '120Hz', '6.2英寸', '832 x 2268', '直面中置单孔', '120Hz', '侧装', '三星显示', '8/11/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Flip 3', '6.7英寸', '1080 x 2640', '小折叠中置单孔', '120Hz', '1.9英寸', '260 x 512', '异形屏', '60Hz', '侧装', '三星显示', '8/11/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Fold 4', '7.6英寸', '1812 x 2176', '折叠右侧中置单孔', '120Hz', '6.2英寸', '904 x 2316', '直面中置单孔', '120Hz', '侧装', '三星显示', '8/10/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Flip 4', '6.7英寸', '1080 x 2640', '小折叠中置单孔', '120Hz', '1.9英寸', '260 x 512', '异形屏', '60Hz', '侧装', '三星显示', '8/10/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Fold 5', '7.6英寸', '1812 x 2176', '折叠右侧中置单孔', '120Hz', '6.2英寸', '904 x 2316', '直面中置单孔', '120Hz', '侧装', '三星显示', '7/26/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Flip 5', '6.7英寸', '1080 x 2640', '小折叠中置单孔', '120Hz', '3.4英寸', '720 x 748', '异形屏', '60Hz', '侧装', '三星显示', '7/26/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Fold 6', '7.6英寸', 'LTPO 1856 x 2160', '折叠右侧中置单孔', '120Hz', '6.3英寸', 'LTPO 968 x 2376', '直面中置单孔', '120Hz', '侧装', '三星显示', '7/10/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Flip 6', '6.7英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '120Hz', '3.4英寸', '720 x 748', '异形屏', '60Hz', '侧装', '三星显示', '7/10/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Fold特别版', '8.0英寸', 'LTPO 1968 x 2184', '折叠右侧中置单孔', '120Hz', '6.5英寸', 'LTPO 1080 x 2520', '直面中置单孔', '120Hz', '侧装', '三星显示', '10/21/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Flip7', '6.9英寸', 'LTPO 1080 x 2520', '小折叠中置单孔', '120Hz', '4.1英寸', '948 x 1048', '异形屏', '120Hz', '侧装', '三星显示', '7/9/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Flip7 FE', '6.7英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '120Hz', '3.4英寸', '720 x 748', '异形屏', '60Hz', '侧装', '三星显示', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '三星', 'Galaxy Z Fold7', '8.0英寸', 'LPTO 1968 x 2184', '折叠右侧中置单孔', '120Hz', '6.5英寸', '1080 x 2520', '直面中置单孔', '120Hz', '侧装', '三星显示', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Mate X', '8英寸', '2200 x 2480', '折叠外折无孔', '90Hz', '6.6英寸', '1148 x 2480', '大折叠外折叠无孔', '90Hz', '侧装', '京东方', '2/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Mate XS', '8英寸', '2200 x 2480', '折叠外折无孔', '90Hz', '6.6英寸', '1148 x 2480', '大折叠外折叠无孔', '90Hz', '侧装', '京东方', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Mate X2', '8英寸', '2200 x 2480', '折叠无孔', '90Hz', '6.45英寸', '1160 x 2700', '直屏左上双孔', '90Hz', '侧装', '京东方', '2/22/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Mate X2 4G', '8英寸', '2200 x 2480', '折叠无孔', '90Hz', '6.45英寸', '1160 x 2700', '直屏左上双孔', '90Hz', '侧装', '京东方', '6/28/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Mate XS 2', '7.8英寸', '2200 x 2480', '折叠外折右单孔', '120Hz', '6.5英寸', '1176 x 2480', '大折叠外折右单孔', '120Hz', '侧装', '三星/京东方', '4/28/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Mate X3', '7.85英寸', '2224 x 2496', '折叠右侧右置单孔', '120Hz', '6.4英寸', '1080 x 2504', '直面中置单孔', '120Hz', '侧装', '京东方/维信诺', '3/23/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Mate X5', '7.85英寸', '2224 x 2496', '折叠右侧右置单孔', '120Hz', '6.4英寸', '1080 x 2504', '直面中置单孔', '120Hz', '侧装', '京东方/维信诺', '9/8/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Mate X6', '7.93英寸', 'LTPO 2240 x 2440', '折叠右侧右置单孔', '120Hz', '6.45英寸', 'LTPO 1080 x 2440', '直面中置单孔', '120Hz', '侧装', '京东方', '11/26/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Mate XT', '10.2英寸', 'LTPO  2232 x 3184 全开，1008 x 2232 三分之一，2048 x 2232  三分之二。', '', '', '', '', '最左侧中置单孔', '120Hz', '侧装', '京东方', '9/10/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'P50 Pocket', '6.9英寸', '1188 x 2790', '小折叠中置单孔', '120Hz', '1.04英寸', '340 x 340', '异形屏', '120Hz', '侧装', '维信诺/京东方/LG/三星', '12/23/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Pocket S', '6.9英寸', '1188 x 2790', '小折叠中置单孔', '120Hz', '1.04英寸', '340 x 340', '异形屏', '120Hz', '侧装', '京东方/维信诺', '11/2/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Pocket 2', '6.94英寸', 'LPTO 1136 x 2690', '小折叠中置单孔', '120Hz', '1.15英寸', 'LTPO 340 x 340', '异形屏', '120Hz', '侧装', '京东方/维信诺', '2/22/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Flip', '6.94英寸', 'LPTO 1136 x 2690', '小折叠中置单孔', '120Hz', '2.14英寸', 'LTPO 480 x 480', '异形屏', '120Hz', '侧装', '京东方/维信诺', '8/5/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Mate XT Ultimate', '10.2 英寸', 'Tri-foldable LTPO 2232 x 3184', '左边中置单孔', '90Hz', '', '', '', '', '侧装', '', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Pura X', '6.3 英寸', 'LTPO2 1320 x 2120', '小折叠中置单孔', '120Hz', '3.5英寸', '980×980‌‌', '异形屏', '120Hz', '侧装', '', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'Mate XTs Ultimate', '10.2 英寸', 'Tri-foldable LTPO 2232 x 3184', '左边中置单孔', '90Hz', '6.4英寸', '1008 x 2232', '', '', '', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '华为', 'nova Flip S', '6.94英寸', 'LTPO 1136 x 2690', '小折叠中置单孔', '120Hz', '2.14英寸', '480 x 480', '异形屏', '120Hz', '侧装', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '荣耀', 'Magic V', '7.9英寸', '1984 x 2272', '折叠右侧中置单孔', '90Hz', '6.45英寸', '1080 x 2560', '直面中置单孔', '120Hz', '侧装', '京东方', '1/10/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '荣耀', 'Magic VS', '7.9英寸', '1984 x 2272', '折叠右侧中置单孔', '90Hz', '6.45英寸', '1080 x 2560', '直面中置单孔', '120Hz', '侧装', '京东方/维信诺', '11/23/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '荣耀', 'Magic VS Ultimate', '7.9英寸', '1984 x 2272', '折叠右侧中置单孔', '90Hz', '6.45英寸', '1080 x 2560', '直面中置单孔', '120Hz', '侧装', '京东方/维信诺', '11/23/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '荣耀', 'Magic V2', '7.92英寸', 'LTPO 2156 x 2344', '折叠右侧中置单孔', '120Hz', '6.43英寸', 'LTPO 1060 x 2376', '直面中置单孔', '120Hz', '侧装', '京东方/维信诺', '7/27/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '荣耀', 'Magic V Purse', '7.71英寸', '2016 x 2348', '折叠外折右置单孔', '90Hz', '6.45英寸', '1088 x 2348', '大折叠外折右置单孔', '90Hz', '侧装', '京东方', '9/19/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '荣耀', 'Magic VS 2', '7.92英寸', 'LTPO 2156 x 2344', '折叠右侧中置单孔', '120Hz', '6.43英寸', 'LTPO 1060 x 2376', '直面中置单孔', '120Hz', '侧装', '京东方/维信诺', '10/12/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '荣耀', 'Magic V2 RSR 保时捷', '7.92英寸', 'LTPO 2156 x 2344', '折叠右侧中置单孔', '120Hz', '6.43英寸', 'LTPO 1060 x 2376', '直面中置单孔', '120Hz', '侧装', '京东方', '1/12/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '荣耀', 'Magic V Flip', '6.8英寸', 'LTPO 1080 x 2520', '小折叠中置单孔', '120Hz', '4英寸', 'LTPO 1200 x 1092', '异形屏', '120Hz', '侧装', '京东方', '6/13/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '荣耀', 'Magic V3', '7.92英寸', 'LTPO 2156 x 2344', '折叠右侧中置单孔', '120Hz', '6.43英寸', 'LTPO 1060 x 2376', '直面中置单孔', '120Hz', '侧装', '京东方/维信诺/天马', '7/19/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '荣耀', 'Magic Vs3', '7.92英寸', 'LTPO 2156 x 2344', '折叠右侧中置单孔', '120Hz', '6.43英寸', 'LTPO 1060 x 2376', '直面中置单孔', '120Hz', '侧装', '京东方/天马', '7/19/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '荣耀', 'Magic V5', '7.95英寸', 'LTPO 2172 x 2352', '折叠右侧中置单孔', '120Hz', '6.43英寸', 'LTPO 1060 x 2376', '直面中置单孔', '120Hz', '侧装', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '荣耀', 'Magic V Flip 2', '6.82英寸', 'LTPO 1232 x 2868', '小折叠中置单孔', '120Hz', '4英寸', 'LTPO 1200 x 1092', '异形屏', '120Hz', '侧装', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '小米', 'MIX Fold', '8.01英寸', '1860 x 2480', '折叠无孔', '90Hz', '6.52英寸', '840 x 2520', '直面右置单孔', '90Hz', '侧装', '华星光电', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '小米', 'MIX Fold 2', '8.02英寸', 'LTPO 1914 x 2160', '折叠无孔', '120Hz', '6.56英寸', 'LTPO 1080 x 2520', '直面中置单孔', '120Hz', '侧装', '三星', '8/11/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '小米', 'MIX Fold 3', '8.03英寸', 'LTPO 1916 x 2160', '折叠左侧左置单孔', '120Hz', '6.56英寸', 'LPTO 1080 x 2520', '直面中置单孔', '120Hz', '侧装', '三星', '8/14/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '小米', 'MIX Fold 4', '7.98英寸', 'LTPO 2224 x 2488', '折叠左侧左置单孔', '120Hz', '6.56英寸', 'LPTO 1080 x 2520', '直面中置单孔', '120Hz', '侧装', '大屏三星/小屏华星', '7/19/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '小米', 'MIX Flip', '6.86英寸', 'LTPO 1224 x 2912', '小折叠中置单孔', '120Hz', '4.0英寸', '1392 x 1208', '直面中置单孔', '120Hz', '侧装', '华星光电', '7/19/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '小米', 'Mix Flip 2', '6.86英寸', 'LTPO 1224 x 2912', '小折叠中置单孔', '120Hz', '4英寸', '1392 x 1208', '异形屏', '120Hz', '侧装', '', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'OPPO', 'Find N', '7.1英寸', 'LTPO 1792 x 1920', '折叠左侧左置单孔', '120Hz', '5.49英寸', '988 x 1972', '直面中置单孔', '120Hz', '侧装', '大屏三星/小屏京东方', '12/15/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'OPPO', 'Find N2', '7.1英寸', 'LTPO 1792 x 1920', '折叠左侧左置单孔', '120Hz', '5.54英寸', '1080 x 2120', '直面中置单孔', '120Hz', '侧装', '三星', '12/15/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'OPPO', 'Find N2 Flip', '6.8英寸', 'LTPO 1080 x 2520', '小折叠中置单孔', '120Hz', '3.26英寸', '382 x 720', '直面屏', '120Hz', '侧装', '大屏三星/小屏京东方', '12/15/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'OPPO', 'Find N3', '7.82英寸', 'LTPO 2268 x 2440', '折叠右侧右置单孔', '120Hz', '6.31英寸', 'LTPO 1116 x 2484', '直面中置单孔', '120Hz', '侧装', '三星/京东方', '10/19/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'OPPO', 'Find N3 Flip', '6.8英寸', 'LTPO 1080 x 2520', '小折叠中置单孔', '120Hz', '3.26英寸', '382 x 720', '直面屏', '120Hz', '侧装', '京东方', '8/29/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'OPPO', 'Find N5', '8.12英寸', 'LTPO 2248 x 2480', '折叠右侧右置单孔', '120Hz', '6.62英寸', 'LTPO 1140 x 2616', '直面中置单孔', '120Hz', '侧装', '京东方', '2/19/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'oneplus', 'OnePlus Open', '7.82英寸', 'LTPO 2268 x 2440', '折叠右侧右置双孔', '120Hz', '6.31英寸', 'LTPO 1116 x 2484', '直面中置单孔', '120Hz', '侧装', '京东方', '10/19/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'VIVO', 'X Fold', '8.03英寸', 'LTPO 1916 x 2160', '折叠右侧中置单孔', '120Hz', '6.53英寸', '1080 x 2520', '直面中置单孔', '120Hz', '侧装', '三星', '4/11/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'VIVO', 'X Fold+', '8.03英寸', 'LTPO 1916 x 2160', '折叠右侧中置单孔', '120Hz', '6.53英寸', '1080 x 2520', '直面中置单孔', '120Hz', '侧装', '三星', '9/26/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'VIVO', 'X Flip', '6.74英寸', 'LTPO 1080 x 2520', '小折叠中置单孔', '120Hz', '3.0英寸', '422 x 682', '异形屏', '90Hz', '侧装', '京东方', '4/20/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'VIVO', 'X Fold 2', '8.03英寸', 'LTPO 1916 x 2160', '折叠右侧中置单孔', '120Hz', '6.53英寸', '1080 x 2520', '直面中置单孔', '120Hz', '侧装', '三星', '4/20/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'VIVO', 'X Fold 3', '8.03英寸', 'LTPO 2200 x 2480', '折叠右侧右置单孔', '120Hz', '6.53英寸', '1172 x 2748', '直面中置单孔', '120Hz', '侧装', '三星', '3/26/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'VIVO', 'X Fold 3Pro', '8.03英寸', 'LTPO 2200 x 2480', '折叠右侧右置单孔', '120Hz', '6.53英寸', '1172 x 2748', '直面中置单孔', '120Hz', '侧装', '大屏三星/小屏京东方', '3/26/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'VIVO', 'X Fold5', '8.03英寸', 'LTPO 2200 x 2480', '折叠右侧右置单孔', '120Hz', '6.53英寸', '1172 x 2748', '直面中置单孔', '120Hz', '侧装', '', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'Tecno', 'Tecno Phantom V Fold', '7.85英寸', 'LTPO 2000 x 2296', '折叠右侧右置单孔', '120Hz', '6.42英寸', 'LTPO 1080 x 2550', '直面中置单孔', '120Hz', '侧装', '华星', '2/28/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'Tecno', 'Tecno Phantom V Flip', '6.9英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '120Hz', '1.32英寸', '466 x 466', '异形屏', '90Hz', '侧装', '华星', '9/22/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'Tecno', 'Tecno Phantom V Flip2', '7.85英寸', 'LTPO 2000 x 2296', '折叠右侧右置单孔', '120Hz', '6.42英寸', 'LTPO 1080 x 2550', '直面中置单孔', '120Hz', '侧装', '华星', '9/13/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', 'Tecno', 'Tecno Phantom V Fold2', '6.9英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '120Hz', '3.64', '1056 x 1066', '异形屏', '90Hz', '侧装', '华星', '9/13/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 2019', '6.2英寸', '876 x 2142', '小折叠无孔', '60Hz', '2.7英寸', '600 x 800', '异形屏', '60Hz', '侧装', '京东方/华星', '11/14/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 5G', '6.2英寸', '876 x 2142', '小折叠无孔', '60Hz', '2.7英寸', '600 x 800', '异形屏', '60Hz', '侧装', '京东方/华星/友达', '9/15/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 2022', '6.7英寸', '1080 x 2400', '小折叠中置单孔', '144Hz', '2.7英寸', '573 x 800', '异形屏', '90Hz', '侧装', '大屏华星/小屏京东方', '8/11/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 40', '6.9英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '144Hz', '1.5英寸', '194 x 368', '异形屏', '90Hz', '侧装', '华星光电', '6/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 40 Ultra', '6.9英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '165Hz', '3.6英寸', '1056 x 1066', '异形屏', '144Hz', '侧装', '华星光电', '6/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 50', '6.9英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '120Hz', '3.6英寸', '1056 x 1066', '异形屏', '90Hz', '侧装', '华星/天马', '6/25/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 50 Ultra', '6.9英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '165Hz', '4.0英寸', 'LTPO 1272 x 1080', '异形屏', '165Hz', '侧装', '华星/天马', '6/25/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 2024', '6.9英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '120Hz', '3.6英寸', '1056 x 1066', '异形屏', '90Hz', '侧装', '华星光电', '6/25/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr+ 2024', '6.9英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '165Hz', '4.0英寸', 'LTPO 1272 x 1080', '异形屏', '165Hz', '侧装', '京东方/华星', '6/25/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr+ 2025', '6.9英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '165Hz', '4.0英寸', 'LTPO 1272 x 1080', '异形屏', '165Hz', '侧装', 'TCL CSOT、BOE/Tianma', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 2025', '6.9英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '120Hz', '3.6英寸', '1056 x 1066', '异形屏', '90Hz', '侧装', 'TCL CSOT /天马或维信诺', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 60', '6.9英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '120Hz', '3.6英寸', '1056 x 1066', '异形屏', '90Hz', '侧装', 'BOE, CSOT /Tianma', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr Ultra 2025', '7.0英寸', 'LTPO 1224 x 2912', '小折叠中置单孔', '165Hz', '4.0英寸', 'LTPO 1272 x 1080', '异形屏', '165Hz', '侧装', 'BOE, Visionox /Tianma', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 60 Ultra', '7.0英寸', 'LTPO 1224 x 2912', '小折叠中置单孔', '165Hz', '4.0英寸', 'LTPO 1272 x 1080', '异形屏', '165Hz', '侧装', 'TCL CSOT，BOE /Visionox（或 CSOT）', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr Fold', '8.1英寸', 'LTPO P-OLED 2232 x 2484', '折叠右侧右置单孔', '120Hz', '6.6英寸', 'LTPO 1080 x 2520', '直面中置单孔', '165Hz', '侧装', '华星/华星', '1/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 70', '6.9英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '120Hz', '3.6英寸', '1056 x 1066', '异形屏', '90Hz', '侧装', '华星/京东方', '4/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 2026', '6.9英寸', 'LTPO 1080 x 2640', '小折叠中置单孔', '120Hz', '3.6英寸', '1056 x 1066', '异形屏', '90Hz', '侧装', '华星/京东方', '5/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 70+', '6.9英寸', 'LTPO 1084 x 2640', '小折叠中置单孔', '165Hz', '4英寸', '1272 x 1080', '异形屏', '165Hz', '侧装', '华星/京东方', '4/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr+ 2026', '6.9英寸', 'LTPO 1084 x 2640', '小折叠中置单孔', '165Hz', '4英寸', '1272 x 1080', '异形屏', '165Hz', '侧装', '华星/京东方', '5/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr 70 Ultra', '7.0英寸', 'LTPO 1224 x 2992', '小折叠中置单孔', '165Hz', '4英寸', '1272 x 1080', '异形屏', '165Hz', '侧装', '华星/华星', '4/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '摩托', 'Razr Ultra 2026', '7.0英寸', 'LTPO 1224 x 2992', '小折叠中置单孔', '165Hz', '4英寸', '1272 x 1080', '异形屏', '165Hz', '侧装', '华星/华星', '5/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '谷歌', 'Pixel Fold', '7.6英寸', '1840 x 2208', '折叠无孔', '120Hz', '5.8英寸', '1080 x 2092', '直面中置单孔', '120Hz', '侧装', '三星', '5/10/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '谷歌', 'Pixel 9 Pro Fold', '8.0英寸', 'LTPO 2076 x 2152', '折叠右侧右置单孔', '120Hz', '6.3英寸', '1080 x 2424', '直面中置单孔', '120Hz', '侧装', '三星', '8/13/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'FOLD', '谷歌', 'Pixel 10 Pro Fold', '8.0英寸', 'LTPO 2076 x 2152', '折叠右侧右置单孔', '120Hz', '6.4英寸', '120Hz', '直面中置单孔', '120Hz', '侧装', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A20', '6.4英寸', '720 x 1560', '直屏水滴', '60Hz', '', '', '', '', '后置', '三星 AMS638TH01', '4/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A30', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '后置', '三星 AMS638WZ01', '2/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A40', '5.9英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '后置', '三星 AMS587TF01', '3/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A50', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星 AMS638WZ01', '2/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A70', '6.7英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星 AMS670TD01', '3/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A80', '6.7英寸', '1080 x 2400', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星 AMS670TA01', '4/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M30', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '后置', '三星 AMS638WZ04/三星 AMS638THXX', '3/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S10e', '5.8英寸', '1080 x 2280', '直屏右置单孔', '60Hz', '', '', '', '', '侧装', '三星 AMB575WN01', '2/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S10', '6.1英寸', '1440 x 3040', '直屏右置单孔', '60Hz', '', '', '', '', '超声波', '三星AMB666WS04', '2/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S10 5g', '6.7英寸', '1440 x 3040', '曲面屏右置双孔', '60Hz', '', '', '', '', '超声波', '三星AMB666WS04', '2/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S10+', '6.4英寸', '1440 x 3040', '曲面屏右置双孔', '60Hz', '', '', '', '', '超声波', '三星 AMB644WQ01', '2/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy Note10', '6.3英寸', '1080 x 2280', '曲面屏中置单孔', '60Hz', '', '', '', '', '超声波', '三星 AMB628TR01', '8/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy Note10 5g', '6.3英寸', '1080 x 2280', '曲面屏中置单孔', '60Hz', '', '', '', '', '超声波', '三星 AMB628TR01', '8/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy Note10+', '6.8英寸', '1440 x 3040', '曲面屏中置单孔', '60Hz', '', '', '', '', '超声波', '三星 AMB675TG01', '8/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy Note10+ 5G', '6.8英寸', '1440 x 3040', '曲面屏中置单孔', '60Hz', '', '', '', '', '超声波', '三星 AMB675TG01', '8/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A50s', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星 AMS638WZ01', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A30s', '6.4英寸', '720 x 1560', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星 AMS638TH12', '8/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A90 5g', '6.7英寸', '1080 x 2400', '曲面屏水滴', '90Hz', '', '', '', '', '光学指纹', '三星 AMS670TD06', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M10s', '6.4英寸', '720 x 1560', '直屏水滴', '90Hz', '', '', '', '', '后置', '京东方LCD（HD）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M30s', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '后置', '三星 AMS638WZ04', '10/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A70s', '6.7英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星 AMS670TD01', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A51', '6.5英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '光学指纹', '三星 AMS646UJ09/三星 AMS646UJ10/三星 AMS646UJ11', '12/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A71', '6.7英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '光学指纹', '三星 AMB667UM06', '12/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S10 Lite', '6.7英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '光学指纹', '三星 AMB667UM01', '1/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy Note10 Lite', '6.7英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '光学指纹', '三星 AMS667UK08', '1/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S20', '6.2英寸', '1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB667TY01', '2/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S20 5g', '6.2英寸', '1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB667TY01', '2/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S20 5g UW', '6.2英寸', '1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB623TS01', '5/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S20 +', '6.7英寸', '1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星AMB667TY01', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S20 + 5g', '6.7英寸', '1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星AMB667TY01', '2/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S20 Ultra 5G', '6.9英寸', '1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB687TZ01', '2/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M31', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '后置', '三星 AMS638WZ04/三星AMS638WZ19', '2/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M21', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '后置', '三星AMS638WZ04', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A41', '6.1英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMS610VM01', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A31', '6.4英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMS638VL01', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A51 5G', '6.5英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '光学指纹', '三星 AMS646UJ01/三星 AMS646UJ10', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A71 5G', '6.7英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '光学指纹', '三星 AMB667UM07', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A Quantum', '6.7英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '光学指纹', '三星 AMB667UM07', '5/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A71 5g UW', '6.7英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '光学指纹', '三星AMB667UM07/三星AMB667UM10', '7/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M31s', '6.5英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '侧装', '三星Super AMOLED（FHD+）', '7/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy Note20', '6.7英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '超声波', '三星 AMB667UM23', '8/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy Note20 5G', '6.7英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '超声波', '三星 AMB667UM23', '8/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy Note20 Ultra', '6.9英寸', '1440 x 3088', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB687VX01/三星 AMB687VX13', '8/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy Note20 Ultra 5G', '6.9英寸', '1440 x 3088', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB687VX01/三星 AMB687VX13', '8/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A51 5g UW', '6.5英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '光学指纹', '三星 AMS646UJ10/三星 AMS646UJ14', '8/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M51', '6.7英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '侧装', '三星 AMB667UM24', '8/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A42 5g', '6.6英寸', '720 x 1600', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星 AMS660XR01', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S20 FE', '6.5英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星 AMS646YB01', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S20 FE 5g', '6.5英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星 AMS646YB01', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy F41', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '后置', '三星AMS638WZ05', '10/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M31 Prime', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '后置', '三星 AMS638WZ04', '10/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M21s', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '后置', '三星 AMS638WZ05', '11/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S21 5g', '6.2英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB624XT01', '1/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S21 + 5g', '6.7英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB667XU01', '1/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S21 Ultra 5G', '6.8英寸', '1440 x 3200', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星AMB681XV01', '1/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy F62', '6.7英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '侧装', '三星AMB667UM06', '2/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M62', '6.7英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '侧装', '三星Super AMOLED（FHD+）', '2/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A32', '6.4英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星 AMS638YQ01', '2/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A52', '6.5英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '三星 AMS646YD01', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A52 5g', '6.5英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星 AMS646YD01/三星 AMS646YD04', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A72', '6.7英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMS667YM01', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy Quantum 2', '6.7英寸', '1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M42 5G', '6.6英寸', '720 x 1600', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星 AMS660XR01', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A22', '6.4英寸', '720 x 1600', '直屏水滴', '90Hz', '', '', '', '', '侧装', '三星 AMS639ZN01', '6/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M32', '6.4英寸', '1080 x 2400', '直屏水滴', '90Hz', '', '', '', '', '侧装', '三星 AMS638VL01', '6/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy F22', '6.4英寸', '720 x 1600', '直屏水滴', '90Hz', '', '', '', '', '侧装', '三星 AMS639ZN01', '7/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M21 2021', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '后置', '三星 AMS638WZ04', '7/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A52s 5g', '6.5英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星 AMS646YD01/三星 AMS646YD04', '8/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M22', '6.4英寸', '720 x 1600', '直屏水滴', '90Hz', '', '', '', '', '侧装', '三星 AMS639ZN01', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M52 5g', '6.7英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '京东方 BF067XMM-TL1-MFPC-R1.4号/三星 AMB667AN01', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S21 FE 5g', '6.4英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星 AMB641ZR01', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S22 5g', '6.1英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB606AW01', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S22 + 5g', '6.6英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB655AY01', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S22 Ultra 5G', '6.8英寸', '1440 x 3088', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB681AZ01', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A33 5G', '6.4英寸', '1080 x 2400', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '三星AMS638BL01/三星AMS638BL02', '3/17/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A53 5G', '6.5英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星 AMS646AE02', '3/17/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A73 5g', '6.7英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方 BF067XMM-TL2/华星 AMS3M667FDPSIIM-81', '3/17/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S20 FE 2022', '6.5英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星 AMS646YB01', '4/6/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M53', '6.7英寸', '1080 x 2408', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '京东方 BF067XMM-TL1-MFPC-R1.4/三星 AMB667AN01', '4/19/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S23', '6.1英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB606AW01', '2/2/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S23+', '6.6英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星AMB655CY01', '2/2/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S23 Ultra', '6.8英寸', '1440 x 3088', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB681CZ01', '2/2/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A34', '6.6英寸', '1080 x 2340', '直屏水滴', '120Hz', '', '', '', '', '光学指纹', '三星 AMS655DE01', '3/15/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A54', '6.4英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星 AMS642DF01', '3/15/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A24 4G', '6.5英寸', '1080 x 2340', '直屏水滴', '90Hz', '', '', '', '', '侧装', '三星 AMS646DS01', '4/18/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy F54', '6.7英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马OLED（120Hz）', '6/6/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M34 5g', '6.5英寸', '1080 x 2340', '直屏水滴', '120Hz', '', '', '', '', '侧装', '三星 AMS646DS01', '7/7/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy F34', '6.5英寸', '1080 x 2340', '直屏水滴', '120Hz', '', '', '', '', '侧装', '天马LCD（60Hz）', '8/7/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S23 FE', '6.4英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星 AMS642DF03', '10/4/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A15', '6.5英寸', '1080 x 2340', '直屏水滴', '90Hz', '', '', '', '', '侧装', '三星 AMS645FW01/三星 AMS645FW02', '12/13/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A15 5g', '6.5英寸', '1080 x 2340', '直屏水滴', '90Hz', '', '', '', '', '侧装', '三星 AMS645FW01/三星 AMS645FW02', '12/13/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A25', '6.5英寸', '1080 x 2340', '直屏水滴', '120Hz', '', '', '', '', '侧装', '三星 AMS646DS04', '1/17/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S24', '6.2英寸', 'LTPO 1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB616FL01', '1/17/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S24 +', '6.7英寸', 'LTPO 1440 x 3120', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB666FM01', '1/17/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S24 Ultra', '6.8英寸', 'LTPO 1440 x 3120', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 AMB679FN01', '1/17/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy F15', '6.6英寸', '1080 x 2340', '直屏水滴', '90Hz', '', '', '', '', '侧装', '天马LCD（60Hz）', '3/4/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M15', '6.5英寸', '1080 x 2340', '直屏水滴', '90Hz', '', '', '', '', '侧装', '三星AMS645FW01/三星AMS645FW02', '4/8/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A35', '6.6英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星 AMS663FS01', '3/11/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A55', '6.6英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星 AMS663FS02', '3/11/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M55', '6.7英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马 TA067FVWK24-00-MFP1-01', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy C55', '6.7英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马 TA067FVWK24-00-MFP1-01', '4/26/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy F55', '6.7英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '5/27/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M35', '6.6英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '', '5/28/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M55s', '6.7英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马 TA067FVWK24-00-MFP1-01', '9/23/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S24 FE', '6.7英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星 AMS670HD01', '10/4/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A16 5g', '6.7英寸', '1080 x 2340', '直屏中置单孔', '90Hz', '', '', '', '', '侧装', '三星 AMS666HJ01/三星 AMS666HJ02', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A16', '6.7英寸', '1080 x 2340', '直屏水滴', '90Hz', '', '', '', '', '侧装', '三星 AMS666HJ01/三星 AMS666HJ02', '12/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S25', '6.2英寸', 'LTPO 1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星AMB616FL03', '1/22/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S25 +', '6.7英寸', 'LTPO 1440 x 3120', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星AMB666FM03', '1/22/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S25 Ultra', '6.9英寸', 'LTPO1440 x 3120', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星AMB686HX01', '1/22/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M16', '6.7英寸', '1080 x 2340', '直屏水滴', '90Hz', '', '', '', '', '侧装', '', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A26', '6.7英寸', '1080 x 2340', '直屏水滴', '120Hz', '', '', '', '', '侧装', '', '3/2/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A36', '6.7英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMS670HD03', '3/2/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A56', '6.7英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMS670HD03', '3/2/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy F16', '6.7 英寸', '1080 x 2340', '直屏水滴', '90Hz', '', '', '', '', '侧装', '', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M56', '6.74 英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy F56', '6.74 英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S25 Edge', '6.7 英寸', 'LTPO 2X 1440 x 3120', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M36', '6.7 英寸', '1080 x 2340', '直屏水滴', '120Hz', '', '', '', '', '侧装', '', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy F36', '6.7 英寸', '1080 x 2340', '直屏水滴', '120Hz', '', '', '', '', '侧装', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A17', '6.7英寸', '1080 x 2340', '直屏水滴', '90Hz', '', '', '', '', '侧装', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy S25 FE', '6.7英寸', '1080 x 2340', '直屏中置单孔', 'LTPO 120HZ', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy F17', '6.7英寸', '1080 x 2340', '直屏水滴', '90Hz', '', '', '', '', '侧装', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy A17 4G', '6.7英寸', '1080 x 2340', '直屏水滴', '90Hz', '', '', '', '', '侧装', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '三星', 'Galaxy M17', '6.7英寸', '1080 x 2340', '直屏水滴', '90Hz', '', '', '', '', '侧装', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', '华为 P30', '6.1 英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMS611WG01/维信诺 G1611FP101GF-MF1-B', '3/26/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', '华为 P30 Pro', '6.47 英寸', '1080 x 2340', '曲面屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方 BF065YQM-AK0-7900/京东方 BF065YQM-TL0-BN01/LG LH647WF1-ED01 VO.3', '3/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 20 X (5G)', '7.2英寸', '1080 x 2244', '直屏水滴', '60Hz', '', '', '', '', '后置', '三星 AMS721RZ01', '5/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 5', '6.39 英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星 AMS639TE01', '6/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 5 Pro', '6.39英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED', '6/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 30', '6.62英寸', '1080 x 2340', '曲面屏中置刘海', '60Hz', '', '', '', '', '光学指纹', '三星/LG/京东方OLED混用', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 30 5g', '6.62英寸', '1080 x 2340', '曲面屏中置刘海', '60Hz', '', '', '', '', '光学指纹', '三星/LG/京东方OLED混用', '10/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 30 Pro', '6.53英寸', '1176 x 2400', '曲面屏中置刘海', '60Hz', '', '', '', '', '光学指纹', '三星/LG/京东方OLED混用', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 30 Pro 5g', '6.53英寸', '1176 x 2400', '曲面屏中置刘海', '60Hz', '', '', '', '', '光学指纹', '三星/LG/京东方OLED混用', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 30 RS保时捷', '6.53英寸', '1176 x 2400', '曲面屏中置刘海', '60Hz', '', '', '', '', '光学指纹', '三星OLED', '11/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', '畅享10s', '6.3英寸', '1080 x 2400', '曲面屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '11/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'P40', '6.1英寸', '1080 x 2340', '直屏左置双孔', '60Hz', '', '', '', '', '光学指纹', '京东方/三星OLED混用', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'P40 Pro', '6.58英寸', '1200 x 2640', '曲面屏左置双孔', '90Hz', '', '', '', '', '光学指纹', '京东方/三星/LG OLED混用', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'P40 Pro+', '6.58英寸', '1200 x 2640', '曲面屏左置双孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED', '6/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 7 5G', '6.53英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 7 Pro 5G', '6.57英寸', '1080 x 2340', '曲面屏左置双孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'P30 Pro 2020', '6.47英寸', '1080 x 2340', '曲面屏中置单孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED', '6/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Y8p', '6.3英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED', '6/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'P Smart S', '6.3英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '6/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 30E Pro 5g', '6.53英寸', '1176 x 2400', '曲面屏中置刘海', '60Hz', '', '', '', '', '光学指纹', '京东方/三星OLED混用', '11/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 40', '6.5英寸', '1080 x 2376', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方/三星/LG OLED混用', '12/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 40 Pro', '6.76英寸', '1344 x 2772', '曲面屏左置双孔', '90Hz', '', '', '', '', '光学指纹', '京东方/三星/LG OLED混用', '11/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 40 Pro +', '6.76英寸', '1344 x 2772', '曲面屏左置双孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED', '11/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 40 RS保时捷', '6.76英寸', '1344 x 2772', '曲面屏左置双孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED', '11/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 8', '6.57英寸', '1080 x 2340', '曲面屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方/维信诺OLED混用', '12/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 8 SE', '6.53英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '11/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 8 5g', '6.57英寸', '1080 x 2340', '曲面屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方/维信诺OLED混用', '1/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 8 Pro 5g', '6.72英寸', '1236 x 2676', '曲面屏左置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方/维信诺OLED混用', '1/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'P40 4g', '6.1英寸', '1080 x 2340', '直屏左置双孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED', '2/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 40E', '6.5英寸', '1080 x 2376', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', '华为nova 8 Pro 4g', '6.72英寸', '1236 x 2676', '曲面屏左置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED', '6/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 40E 4g', '6.5英寸', '1080 x 2376', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED', '6/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 40 Pro 4g', '6.76英寸', '1344 x 2772', '曲面屏左置双孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED', '6/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'P50', '6.5英寸', '1224 x 2700', '曲面屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED', '7/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'P50 Pro', '6.6英寸', '1228 x 2700', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED', '7/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 9', '6.57英寸', '1080 x 2340', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 9 Pro', '6.72英寸', '1236 x 2676', '曲面屏左置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 8 SE 4g', '6.5英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '11/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'P50E', '6.5英寸', '1224 x 2700', '直屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED', '3/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 10', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 10 Pro', '6.78英寸', '1200 x 2652', '曲面屏左置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 50', '6.7英寸', '1224 x 2700', '直屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（1-120Hz LTPO）', '9/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 50E', '6.7英寸', '1224 x 2700', '直屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED', '9/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 50 Pro', '6.74英寸', '1212 x 2616', '曲面屏中置刘海', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO 1-120Hz）', '9/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 50 RS保时捷', '6.74英寸', '1212 x 2616', '曲面屏中置刘海', '120Hz', '', '', '', '', '光学指纹', '京东方OLED', '9/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 10 SE', '6.67英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '侧装', '京东方/维信诺OLED', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'P60 Art', '6.67英寸', 'LTPO 1220 x 2700', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（4K 120Hz）', '4/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'P60', '6.67英寸', 'LTPO 1220 x 2700', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'P60 Pro', '6.67英寸', 'LTPO 1220 x 2700', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方/维信诺OLED（LTPO）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 11', '6.7英寸', '1084 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方/维信诺OLED（120Hz）', '4/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 11 Pro', '6.78英寸', '1200 x 2652', '曲面屏左置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（曲面屏）', '4/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 11 Ultra', '6.78英寸', '1200 x 2652', '曲面屏左置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO）', '4/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 60', '6.69英寸', 'LTPO 1216 x 2688', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（直屏）', '9/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 60 Pro', '6.82英寸', 'LTPO 1260 x 2720', '曲面屏中置三孔', '120Hz', '', '', '', '', '光学指纹', '京东方/维信诺OLED（LTPO）', '8/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 60 Pro +', '6.82英寸', 'LTPO 1260 x 2720', '曲面屏中置三孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO）', '9/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 60 RS', '6.82英寸', 'LTPO 1260 x 2720', '曲面屏中置三孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（曲面屏）', '9/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 11 SE', '6.67英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '侧装', '京东方OLED（90Hz）', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 12 Lite', '6.7英寸', '1084 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（60Hz）', '12/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 12', '6.7英寸', '1084 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'TCL华星/京东方OLED（直屏）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 12 Pro', '6.76英寸', 'LTPO 1224 x 2776', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', 'TCL华星/京东方OLED（四曲面）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 12 Ultra', '6.76英寸', 'LTPO 1224 x 2776', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', 'TCL华星OLED（LTPO 1-120Hz）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 12s', '6.7英寸', '1084 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 12 SE', '6.67英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '侧装', '天马LCD（60Hz）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Pura 70', '6.6英寸', 'LTPO 1256 x 2760', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（直屏）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Pura 70 Pro', '6.8英寸', 'LTPO 1260 x 2844', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方/维信诺OLED（四曲面）', '4/2/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Pura 70 Pro +', '6.8英寸', 'LTPO 1260 x 2844', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方/维信诺OLED（LTPO）', '4/3/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Pura 70 Ultra', '6.8英寸', 'LTPO 1260 x 2845', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（8T LTPO 1-144Hz）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 13', '6.7英寸', '1084 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方/维信诺OLED（120Hz）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 13 Pro', '6.76英寸', 'LTPO 1224 x 2776', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（四曲面屏）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 70', '6.7英寸', 'LTPO 1216 x 2688', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '京东方OLED（LTPO 1-144Hz）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 70 Pro', '6.9英寸', 'LTPO 1316 x 2832', '曲面屏中置双孔', '120Hz', '', '', '', '', '侧装', '京东方/维信诺OLED（LTPO）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 70 Pro +', '6.9英寸', 'LTPO 1316 x 2832', '曲面屏中置双孔', '120Hz', '', '', '', '', '侧装', '京东方OLED（8T LTPO）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Mate 70 RS', '6.9英寸', '双层LTPO 1316 x 2832', '曲面屏中置双孔', '120Hz', '', '', '', '', '侧装', '京东方OLED（保时捷定制）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', '畅享70X', '6.78英寸', '1224 x 2700', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（HD+）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', '畅享70X Energy', '6.78 英寸', '1224 x 2700', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '天马（Tianma）、京东方（BOE）', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 14', '6.7 英寸', '1084 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '京东方（BOE）、维信诺（Visionox）', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 14 Pro', '6.78 英寸', 'LTPO 1224 x 2776', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方 或 TCL CSOT', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Pura 80', '6.6英寸', 'LTPO 1256 x 2760', '曲面屏中置单孔', '120Hz', '', '', '', '', '侧装', '京东方、TCL 华星（CSOT）', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Pura 80 Pro', '6.8英寸', 'LTPO 1276 x 2848', '曲面屏中置单孔', '120Hz', '', '', '', '', '侧装', '京东方（BOE M8系列面板）', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Pura 80 Pro+', '6.8 英寸', 'LTPO 1276 x 2848', '曲面屏中置单孔', '120Hz', '', '', '', '', '侧装', '京东方 或 维信诺（Visionox）', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'Pura 80 Ultra', '6.8 英寸', 'LTPO 1276 x 2848', '曲面屏中置单孔', '120Hz', '', '', '', '', '侧装', '京东方（独家供应，Pura 高端定制屏）', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '华为', 'nova 14 Lite', '6.95 英寸', '1084 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '', '11/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 2 3D', '6.39英寸', '1080 x 2340', '曲面屏无孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（滑盖屏）', '3/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 20 lite(China)', '6.3英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '10/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Play 4T Pro', '6.3英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（60Hz）', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 30', '6.53英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '维信诺OLED（90Hz）', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 30 Pro', '6.57英寸', '1080 x 2340', '曲面屏左置双孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（曲面屏）', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 30 Pro+', '6.57英寸', '1080 x 2340', '曲面屏左置双孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 30i', '6.3英寸', '1080 x 2400', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '天马LCD', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'V40 5g', '6.72英寸', '1236 x 2676', '曲面屏左置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '1/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'V40', '6.72英寸', '1236 x 2676', '曲面屏左置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（曲面屏）', '1/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'V40 Lite', '6.57英寸', '1080 x 2340', '曲面屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '天马LCD', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Play5 5g', '6.53英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（60Hz）', '5/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 50', '6.57英寸', '1080 x 2340', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺OLED（120Hz）', '6/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 50 Pro', '6.72英寸', '1236 x 2676', '曲面屏左置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（曲面屏）', '6/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 3', '6.76英寸', '1344 x 2772', '曲面屏左置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（瀑布屏）', '8/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 3 Pro', '6.76英寸', '1344 x 2772', '曲面屏左置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO 1-120Hz）', '8/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 3 Pro +', '6.76英寸', '1344 x 2772', '曲面屏左置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO 2K）', '8/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 60', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺OLED（120Hz）', '12/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 60 Pro', '6.78英寸', '1200 x 2652', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（曲面屏）', '12/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 60 SE', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 4', '6.81英寸', 'LTPO 1224 x 2664', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO 1-120Hz）', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 4 Pro', '6.81英寸', 'LTPO 1312 x 2848', '曲面屏左置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO 2K）', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 4 Ultimate', '6.81英寸', 'LTPO 1312 x 2848', '曲面屏左置双孔', '120Hz', '', '', '', '', '超声波', '京东方OLED（定制2K LTPO）', '3/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 70', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺OLED（120Hz）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 70 Pro', '6.78英寸', '1200 x 2652', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（曲面屏）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 70 Pro +', '6.78英寸', '1200 x 2652', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO 2K）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X40', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（90Hz）', '9/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 80', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺OLED（120Hz）', '11/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 80 Pro', '6.67英寸', '1080 x 2400', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（曲面屏）', '11/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 80 GT', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（144Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 80 Pro Flat', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺OLED（直屏）', '1/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X9a', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（60Hz）', '1/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 5 Lite', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '2/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 5', '6.73英寸', '1224 x 2688', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO 1-120Hz）', '2/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 5 Pro', '6.81英寸', 'LTPO 1312 x 2848', '曲面屏左置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO 2K）', '2/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 5 Ultimate', '6.81英寸', 'LTPO 1312 x 2848', '曲面屏左置双孔', '120Hz', '', '', '', '', '超声波', '京东方OLED（定制2K LTPO）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 90', '6.7英寸', '1200 x 2664', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺OLED（120Hz）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 90 Pro', '6.78英寸', '1224 x 2700', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（曲面屏）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X9b', '6.78英寸', '1220 x 2652', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（60Hz）', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X50', '6.78英寸', '1220 x 2652', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（90Hz）', '7/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X50i+', '6.7英寸', '1080 x 2412', '直屏中置双孔', '90Hz', '', '', '', '', '侧装', '京东方OLED（60Hz）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 100', '6.7英寸', '1200 x 2664', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺OLED（120Hz）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 100 Pro', '6.78英寸', '1224 x 2700', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO 1-120Hz）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 6 Lite', '6.78英寸', '1220 x 2652', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（90Hz）', '12/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X8b', '6.7英寸', '1080 x 2412', '直屏中置双孔', '90Hz', '', '', '', '', '侧装', '天马LCD（60Hz）', '12/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 90 GT', '6.7英寸', '1200 x 2664', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（144Hz）', '12/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X50 Pro', '6.78英寸', '1220 x 2652', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺OLED（120Hz）', '12/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X50 GT', '6.78英寸', '1220 x 2652', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（144Hz）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 6', '6.78英寸', 'LTPO 1264 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO 1-144Hz）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 6 Pro', '6.8英寸', 'LTPO 1280 x 2800', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO 2K）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 6 Ultimate', '6.8英寸', 'LTPO 1280 x 2800', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（定制2K LTPO）', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic6 RSR Porsche', '6.8英寸', 'LTPO 1280 x 2800', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（保时捷定制屏）', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 200 Lite', '6.7英寸', '1080 x 2412', '直屏中置双孔', '90Hz', '', '', '', '', '侧装', '天马LCD（60Hz）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 200', '6.7英寸', '1200 x 2664', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺OLED（120Hz）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 200 Pro', '6.78英寸', '1224 x 2700', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO 1-120Hz）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X60i', '6.7英寸', '1080 x 2412', '直屏中置双孔', '90Hz', '', '', '', '', '光学指纹', '天马LCD（90Hz）', '7/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X60 Pro', '6.78英寸', '1224 x 2700', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '维信诺OLED（120Hz）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 7', '6.78英寸', 'LTPO 1264 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '京东方OLED（LTPO 1-144Hz）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 7 Pro', '6.8英寸', 'LTPO  1280 x 2800', '曲面屏中置双孔', '120Hz', '', '', '', '', '超声波', '京东方OLED（LTPO 2K）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X9c', '6.78英寸', '1224 x 2700', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（60Hz）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 300', '6.7英寸', '1200 x 2664', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺OLED（120Hz）', '12/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 300 Pro', '6.78英寸', '1224 x 2700', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（LTPO 1-120Hz）', '12/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor 300 Ultra', '6.78英寸', '1224 x 2700', '曲面屏中置双孔', '120Hz', '', '', '', '', '超声波', '京东方OLED（2K LTPO）', '12/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Honor GT', '6.7英寸', '1200 x 2664', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（144Hz）', '12/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 7 RSR Porsche', '6.8英寸', 'LTPO  1280 x 2800', '曲面屏中置双孔', '120Hz', '', '', '', '', '超声波', '京东方OLED（保时捷定制屏）', '12/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic 7 Lite', '6.78英寸', '1224 x 2700', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（90Hz）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X8c', '6.7英寸', '1080 x 2412', '直屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（60Hz）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', '400 Lite', '6.7英寸', '1080 x 2412', '直屏中置双孔', '120Hz', '', '', '', '', '光学指纹', 'BOE, Tianma', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Power', '6.78英寸', '1224 x 2700', '直屏中置双孔', '120Hz', '', '', '', '', '光学指纹', 'BOE, Visionox', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X60 GT', '6.7英寸', '1200 x 2664', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE, CSOT', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'GT Pro', '6.78英寸', 'LTPO 1224 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', 'BOE, CSOT', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X70i', '6.7 英寸', '1080 x 2412', '直屏中置双孔', '120Hz', '', '', '', '', '光学指纹', 'BOE, Visionox', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', '400', '6.55英寸', '1264 x 2736', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', '400 Pro', '6.7 英寸', '1280 x 2800', '直屏中置双孔', '120Hz', '', '', '', '', '光学指纹', 'BOE, Visionox', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', '400（中国）', '6.55英寸', '1264 x 2736', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', '400 Pro (China)', '6.55英寸', '1264 x 2736', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE, Visionox', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X70', '6.79英寸', '1200 x 2640', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'X9d', '6.79英寸', '1200 x 2640', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic8', '6.58英寸', 'LTPO 1256 x 2760', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '荣耀', 'Magic8 Pro', '6.71英寸', 'LTPO 1256 x 2808', '曲面屏中置双孔', '120Hz', '', '', '', '', '超声波', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 9', '6.39英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '2/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 9 SE', '5.97英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD）', '2/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 9 Explorer', '6.39英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（透明探索版）', '2/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi Mix 3 5g', '6.39英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '后置', '京东方AMOLED（滑盖全面屏）', '5/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K20 Pro', '6.39英寸', '1080 x 2340', '曲面屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '5/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '黑鲨2', '6.39英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '京东方AMOLED（144Hz电竞屏）', '3/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K20', '6.39英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '5/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 9t', '6.39英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（全球版K20）', '6/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 9t Pro', '6.39英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（全球版K20 Pro）', '8/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi CC9', '6.39英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（水滴屏）', '7/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi CC9e', '6.01英寸', '720 x 1560', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD（HD+）', '7/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'MI A3', '6.09英寸', '720 x 1560', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（HD+）', '7/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '黑鲨2 Pro', '6.39英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '京东方AMOLED（240Hz采样率）', '7/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 9 Lite', '6.39英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K20 Pro Premium', '6.39英寸', '1080 x 2340', '曲面屏无孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（12GB+512GB顶配）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 9 Pro', '6.39英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（升级版）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 9 Pro 5g', '6.39英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（5G基带版本）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi CC9 Pro', '6.47英寸', '1080 x 2340', '曲面屏水滴', '60Hz', '', '', '', '', '光学指纹', '维信诺AMOLED（曲面屏）', '11/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi Note 10', '6.47英寸', '1080 x 2340', '曲面屏水滴', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（全球版CC9 Pro）', '11/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi Note 10 Pro', '6.47英寸', '1080 x 2340', '曲面屏水滴', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（Pro版）', '11/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 10 5g', '6.67英寸', '1080 x 2340', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '2/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 10 Pro 5g', '6.67英寸', '1080 x 2340', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '2/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '黑鲨3', '6.67英寸', '1080 x 2400', '直屏无孔', '90Hz', '', '', '', '', '光学指纹', '京东方AMOLED（120Hz）', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '黑鲨3 Pro', '7.1英寸', '1440 x 3120', '直屏无孔', '90Hz', '', '', '', '', '光学指纹', '京东方AMOLED（2K+ 120Hz）', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K30 Pro', '6.67英寸', '1080 x 2400', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（弹出式全面屏）', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K30 Pro Zoom', '6.67英寸', '1080 x 2400', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（长焦微距版）', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 10 Lite 5g', '6.57英寸', '1080 x 2400', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '天马AMOLED（60Hz）', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 10 Youth 5G', '6.57英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（潜望长焦版）', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi Note 10 Lite', '6.47英寸', '1080 x 2340', '曲面屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco F2 Pro', '6.67英寸', '1080 x 2400', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（全球版K30 Pro）', '5/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi 10X 5G', '6.57英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马AMOLED（60Hz）', '5/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi 10X Pro 5G', '6.57英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（60Hz）', '5/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '黑鲨3s', '6.67英寸', '1080 x 2400', '曲面屏无孔', '120Hz', '', '', '', '', '光学指纹', '京东方AMOLED（165Hz）', '7/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K30 Ultra', '6.67英寸', '1080 x 2400', '直屏无孔', '120Hz', '', '', '', '', '光学指纹', '维信诺AMOLED（120Hz）', '8/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 10 Ultra', '6.67英寸', '1080 x 2340', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '华星光电AMOLED（120Hz 10bit）', '8/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 11', '6.81英寸', '1440 x 3200', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E4 AMOLED（2K 120Hz）', '12/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 11 Pro', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马AMOLED（120Hz）', '10/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 11i', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '侧装', '三星E4 AMOLED（直屏）', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Mi 11i HyperCharge 5G', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '侧装', '三星E4 AMOLED（120W快充）', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 11', '6.43英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '侧装', '天马LCD（90Hz）', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 11S', '6.43英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '侧装', '天马AMOLED（90Hz）', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 11 Pro 5G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马AMOLED（120Hz）', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K50 Gaming', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '华星光电AMOLED（1920Hz PWM）', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco M4 Pro', '6.43英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '侧装', '天马LCD（90Hz）', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco X4 Pro 5G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '三星AMOLED（120Hz）', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 11E Pro', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马LCD（120Hz）', '3/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 11 Pro + 5g (印度)', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '三星AMOLED（120Hz）', '3/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '12X', '6.28英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '华星光电AMOLED（120Hz）', '12/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '12', '6.28英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E5 AMOLED（LTPO 2.0）', '12/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '12 Pro', '6.73英寸', 'LTPO 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E5 AMOLED（2K LTPO）', '12/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K40S', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '侧装', '三星E4 AMOLED（120Hz）', '3/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K50', '6.67英寸', '1440 x 3200', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '华星光电OLED（2K 120Hz）', '3/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K50 Pro', '6.67英寸', '1440 x 3200', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '三星E5 AMOLED（2K 120Hz）', '3/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 11 Pro+ 5G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '三星AMOLED（120Hz）', '10/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '黑鲨5 RS', '6.67英寸', '1080 x 2400', '直屏中置单孔', '144Hz', '', '', '', '', '侧装', '京东方OLED（144Hz电竞屏）', '3/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '黑鲨5', '6.67英寸', '1080 x 2400', '直屏中置单孔', '144Hz', '', '', '', '', '侧装', '华星光电OLED（144Hz）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '黑鲨5 Pro', '6.67英寸', '1080 x 2400', '直屏中置单孔', '144Hz', '', '', '', '', '侧装', '京东方OLED（165Hz）', '3/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Civi 1S', '6.55英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '华星光电OLED（曲面屏）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco F4 GT', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '三星AMOLED（120Hz）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco F4', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马OLED（120Hz）', '6/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '12s', '6.28英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E5 AMOLED（2K 120Hz）', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '12S Pro', '6.73英寸', '1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E5 LTPO AMOLED（2K 120Hz）', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '12s Ultra', '6.73英寸', 'LTPO2 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E5 LTPO AMOLED（2K 120Hz）', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '12 Lite', '6.55英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '华星光电OLED（120Hz）', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 11 SE (India)', '6.43英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '侧装', '天马LCD（90Hz）', '8/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco M5s', '6.43英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '侧装', '天马AMOLED（90Hz）', '9/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Civi 2', '6.55英寸', '1080 x 2400', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（曲面屏）', '9/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '12T', '6.67英寸', '1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '华星光电OLED（120Hz）', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '12T Pro', '6.67英寸', '1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E5 AMOLED（120Hz）', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 12', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马OLED（120Hz）', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 12 Pro', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '三星AMOLED（120Hz）', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 12 Pro+', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '三星AMOLED（120Hz）', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 12 发现', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '华星光电OLED（120Hz）', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K60', '6.67英寸', '1440 x 3200', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '华星光电2K OLED（120Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K60E', '6.67英寸', '1440 x 3200', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马OLED（120Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K60 Pro', '6.67英寸', '1440 x 3200', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（2K 120Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 12 Pro Speed', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马OLED（120Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 12', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马LCD（90Hz）', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco X5', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马OLED（120Hz）', '2/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco X5 Pro', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '三星AMOLED（120Hz）', '2/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '13 Lite', '6.55英寸', '1080 x 2400', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '华星光电OLED（120Hz）', '2/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '13', '6.36英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 LTPO AMOLED（2K 120Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '13 Pro', '6.73英寸', 'LTPO 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 LTPO AMOLED（2K 120Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 12 4g', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '京东方LCD（90Hz）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 12 Turbo', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '华星光电OLED（144Hz）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 12 Pro 4g', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马LCD（120Hz）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 12S', '6.43英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '侧装', '天马OLED（90Hz）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '13 Ultra', '6.73英寸', 'LTPO 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 LTPO AMOLED（2K 120Hz）', '4/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco F5', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马OLED（120Hz）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco F5 Pro', '6.67英寸', '1440 x 3200', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E5 AMOLED（120Hz）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Civi 3', '6.55英寸', '1080 x 2400', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（曲面屏）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 12R Pro', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马LCD（90Hz）', '6/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K60 Ultra', '6.67英寸', '1220 x 2712', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '华星光电1.5K OLED（144Hz）', '8/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 13', '6.67英寸', '1080 x 2400', '直屏中置单孔', '144Hz', '', '', '', '', '侧装', '天马OLED（120Hz）', '9/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '13t', '6.67英寸', '1220 x 2712', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（120Hz）', '9/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '13t Pro', '6.67英寸', '1220 x 2712', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星E6 LTPO AMOLED（2K 120Hz）', '9/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '14 Pro', '6.73英寸', 'LTPO 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E7 LTPO AMOLED（2K 120Hz）', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 13R Pro', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '京东方LCD（90Hz）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K70E', '6.67英寸', '1220 x 2712', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（1.5K 120Hz）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K70', '6.67英寸', '1440 x 3200', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '华星光电2K OLED（120Hz）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K70 Pro', '6.67英寸', '1440 x 3200', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E7 LTPO AMOLED（2K 120Hz）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco M6 Pro', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（120Hz）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco X6', '6.67英寸', '1220 x 2712', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（120Hz）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco X6 Pro', '6.67英寸', '1220 x 2712', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（120Hz）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 13 Pro', '6.67英寸', '1220 x 2712', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '9/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 13 Pro+', '6.67英寸', '1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '9/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 13 4G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD（90Hz）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 13 Pro 4g', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（120Hz）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '14 Ultra', '6.73英寸', 'LTPO 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E7 LTPO AMOLED（2K 120Hz）', '2/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '14', '6.36英寸', 'LTPO 1200 x 2670', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E7 LTPO AMOLED（2K 120Hz）', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco X6 Neo', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马LCD（90Hz）', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Civi 4 Pro', '6.55英寸', '1236 x 2750', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（四曲面屏）', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Turbo 3', '6.67英寸', '1220 x 2712', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '华星光电OLED（144Hz）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco F6', '6.67英寸', '1220 x 2712', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（120Hz）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco F6 Pro', '6.67英寸', '1440 x 3200', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（120Hz）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '14 Civi', '6.55英寸', '1236 x 2750', '曲面屏中置双孔', '120Hz', '', '', '', '', '光学指纹', '华星光电OLED（曲面屏）', '6/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K70 Ultra', '6.67英寸', '1220 x 2712', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '华星光电2K OLED（144Hz）', '7/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 14 5g', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马OLED（120Hz）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '14t', '6.67英寸', '1220 x 2712', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（120Hz）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '14t Pro', '6.67英寸', '1220 x 2712', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星E6 LTPO AMOLED（2K 120Hz）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '15', '6.36英寸', 'LTPO 1200 x 2670', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星E7 LTPO AMOLED（2K 144Hz）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '15 Pro', '6.73英寸', 'LTPO 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星E7 LTPO AMOLED（2K 144Hz）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K80', '6.67英寸', '1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '华星光电2K OLED（120Hz）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K80 Pro', '6.67英寸', '1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星E7 LTPO AMOLED（2K 144Hz）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 14 Pro+ 5G (India)', '6.67英寸', '1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 14 Pro 5G (India)', '6.67英寸', '1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（120Hz）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 14 5G (India)', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（90Hz）', '12/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco M7 Pro 5g', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（120Hz）', '12/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Turbo 4', '6.67英寸', '1220 x 2712', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '华星光电OLED（144Hz）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco X7', '6.67英寸', '1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（120Hz）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco X7 Pro', '6.67英寸', '1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（120Hz）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 14 4G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD（90Hz）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 14 5G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（120Hz）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 14 Pro 4G', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD（120Hz）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 14 Pro 5G', '6.67英寸', '1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 14 Pro+ 5G', '6.67英寸', '1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '15 Ultra', '6.73英寸', 'LTPO 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星E7 LTPO AMOLED（2K 144Hz）', '2/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 14S', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma, CSOT', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco F7 Pro', '6.67英寸', '1440 x 3200', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', 'CSOT, Visionox', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco F7 Ultra', '6.67英寸', '1440 x 3200', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', 'BOE, CSOT', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Turbo 4 Pro', '6.83英寸', '1280 x 2772', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma, Visionox', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Civi 5 Pro', '6.55英寸', '1236 x 2750', '直屏中置双孔', '120Hz', '', '', '', '', '光学指纹', 'TCL CSOT', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '15S Pro', '6.73英寸', 'LTPO 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', 'BOE', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Poco F7', '6.83英寸', '1280 x 2772', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K80 Ultra', '6.83英寸', '1280 x 2772', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', '', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 15', '6.77英寸', '1080 x 2392', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 15 Pro', '6.83英寸', '1220 x 2772', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi Note 15 Pro+', '6.83英寸', '1220 x 2772', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '15T', '6.83英寸', '1280 x 2772', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '15T Pro', '6.83英寸', '1280 x 2772', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '17', '6.3英寸', 'LTPO 1220 x 2656', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '17 Pro', '6.3英寸', 'LTPO 1220 x 2656', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', '17 Pro Max', '6.9英寸', 'LTPO 1200 x 2608', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K90 Pro Max', '6.9英寸', '1200 x 2608', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '小米', 'Redmi K90', '6.59英寸', '1156 x 2510', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno', '6.4英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED', '4/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno 10x zoom', '6.6英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED', '6/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Oppo Reno 5G', '6.6英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED', '5/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'K3', '6.5英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED', '5/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno Z', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方LCD', '6/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno2 Z', '6.53英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED', '8/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno2 F', '6.5英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED', '10/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno2', '6.5英寸', '1080 x 2400', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED', '8/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno A', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno Ace', '6.5英寸', '1080 x 2400', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '10/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'K5', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED', '10/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A91', '6.4英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED', '12/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno3 5G', '6.4英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（集成5G基带）', '12/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno3 Youth', '6.4英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED', '2/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno3 Pro 5G', '6.5英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（主供）', '12/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F15', '6.4英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '/ 京东方（副供）', '1/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno3 Pro', '6.4英寸', '1080 x 2400', '曲面屏左置双孔', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X2', '6.7英寸', '1440 x 3168', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（曲面屏）', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X2 Pro', '6.7英寸', '1440 x 3168', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E3 AMOLED（2K 120Hz）', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno3', '6.4英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星E3 AMOLED（2K 120Hz）', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Ace2', '6.55英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X2 Lite', '6.4英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'K7 5G', '6.4英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED', '8/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X2 Neo', '6.5英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '​三星AMOLED', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno4 5G', '6.43英寸', '1080 x 2400', '直屏左置双孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '6/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno4 Pro 5G', '6.55英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星柔性曲面AMOLED', '6/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno4', '6.4英寸', '1080 x 2400', '直屏左置双孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（60Hz）', '7/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno4 Pro', '6.5英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星/京东方双供', '7/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F17', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F17 Pro', '6.43英寸', '1080 x 2400', '直屏左置双孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（入门级）', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno4 SE', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno4 Lite', '6.43英寸', '1080 x 2400', '直屏左置双孔', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A93', '6.43英寸', '1080 x 2400', '直屏左置双孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED', '10/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A73', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '10/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno4 F', '6.43英寸', '1080 x 2400', '直屏左置双孔', '60Hz', '', '', '', '', '光学指纹', '天马OLED（区域特供）', '10/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno5 F', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（60Hz）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A94', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno5 Lite', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F19 Pro', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（E3材质）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F19 Pro + 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（双版本）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno5 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方柔性OLED', '12/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno5 Pro 5G', '6.55英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星2.5D柔性屏', '12/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno5 Pro+ 5G', '6.55英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星LTPO顶级屏', '12/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno5 4G', '6.4英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '天马OLED（区域特供）', '12/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno5 K', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（中国特供）', '2/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X3 Lite', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X3 Neo', '6.55英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方QHD+屏', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X3', '6.7英寸', 'LTPO 1440 x 3216', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO 2K屏', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X3 Pro', '6.7英寸', 'LTPO 1440 x 3216', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星Dynamic AMOLED 2X', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F19', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A74', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno5 Z', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（区域版）', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A94 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '天马OLED（入门5G）', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A95 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '京东', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'K9', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星', '5/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno6 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz直屏）', '5/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno6 Pro 5G', '6.55英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方柔性曲面屏（1.5K）', '5/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno6 Pro+ 5G', '6.55英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（120Hz）', '5/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno6 Z', '6.4英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '天马OLED（60Hz）', '7/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno6', '6.4英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方AMOLED（全球版）', '7/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno6 Pro 5g (骁龙)', '6.55英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（中国特供版）', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'K9 Pro', '6.43英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E4 AMOLED（电竞屏）', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F19s', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A95', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（FHD+）', '11/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno7 SE 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '天马OLED（直屏）', '11/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno7 5g (中国)', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方Q9+柔性屏（1.5K）', '11/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno7 Pro 5G', '6.55英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星E5 AMOLED（LTPO 2.0）', '11/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A96 (中国)', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '天马OLED（中端定位）', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno6 Lite', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '京东方LCD', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno7 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方/三星双供（全球版）', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X5 Lite', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X5', '6.55英寸', '1080 x 2400', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO 2K屏', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X5 Pro', '6.7英寸', 'LTPO2 1440 x 3216', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星Dynamic AMOLED 2X（QHD+）', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno7 Z 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '天马OLED（区域特供）', '3/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno7', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（全球版）', '3/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F21 Pro', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '天马OLED（入门级）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F21 Pro 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（E4材质）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno7 Lite', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '京东方LCD', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'K10 Pro', '6.62英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E5 AMOLED（电竞屏）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno8 (China)', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方Q9+ 1.5K屏', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno8 Pro (China)', '6.62英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（120Hz LTPO）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno8 Pro+', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO 3.0（2K分辨率）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno8 Lite', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '6/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno8', '6.4英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方/天马双供（全球版）', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno8 Pro', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（全球版）', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno8 Z', '6.43英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '天马OLED（区域特供）', '8/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno8 4G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（60Hz）', '8/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A1 Pro', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（曲面屏）', '11/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno9', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方Q9柔性屏（1.5K）', '11/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno9 Pro', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（LTPO 3.0）', '11/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno9 Pro+', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星顶级2K LTPO屏', '11/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno8 T 5G', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（区域版）', '2/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno8 T', '6.43英寸', '1080 x 2400', '曲面屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（60Hz）', '2/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X6', '6.74英寸', '1240 x 2772', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO 2K AMOLED', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X6 Pro', '6.82英寸', 'LTPO3 1440 x 3168', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星Dynamic AMOLED 2X（QHD+）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno10 (中国)', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方Q9+升级版（1.5K）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno10 Pro (中国)', '6.74英寸', '1240 x 2772', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（LTPO 3.0）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno10 Pro +', '6.74英寸', '1240 x 2772', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO 3.0（2K分辨率）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A78 4G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '天马LCD', '7/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno10', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方/天马双供（全球版）', '7/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno10 Pro', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（全球版）', '7/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'K11', '6.7英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E4 AMOLED（电竞屏）', '7/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A58 4G', '6.72英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '侧装', '京东方LCD', '7/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A2 Pro', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（曲面屏）', '9/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno11 (中国)', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方Q10柔性屏（1.5K）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno11 Pro (中国)', '6.74英寸', '1240 x 2772', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E7 AMOLED（LTPO 4.0）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X7', '6.78英寸', 'LTPO 1264 x 2780', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO 2K AMOLED（2024技术）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X7 Ultra', '6.82英寸', 'LTPO 1440 x 3168', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno11', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno11 Pro', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（四曲柔边直屏）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno11 F', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（90Hz）', '2/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F25 Pro', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '2/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A3 Pro (中国)', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD（IP69防水）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'K12x (中国)', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '华星光电OLED（1.5K）', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno12 (中国)', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马柔性OLED（四曲屏）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno12 Pro (中国)', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马柔性OLED（ProXDR）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F27 Pro +', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（电竞屏）', '6/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno12', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（四曲柔边直屏）', '6/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno12 Pro', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（2K 120Hz）', '6/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno12 F', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '​天马OLED​（90Hz）', '6/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A3 (中国)', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '​京东方LCD​（IP69防水）', '7/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno12 F 4G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '​京东方LCD​（60Hz）', '7/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F27', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '​京东方OLED​（基础款）', '8/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'K12 Plus', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '​京东方OLED​（1.5K）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X8', '6.59英寸', '1256 x 2760', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '​天马U8+材质OLED​（1.5K）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X8 Pro', '6.78英寸', 'LTPO 1264 x 2780', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '​京东方X2材质OLED​（2K）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno13', '6.59英寸', '1256 x 2760', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '​天马OLED​（1.5K直屏）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno13 Pro', '6.83英寸', '1272 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '​京东方OLED​（四曲屏）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A5 Pro', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '​京东方OLED​（四微曲屏）', '12/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno13 F 4G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '​天马LCD​（90Hz）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F29 Pro', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma, Visionox', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X8s', '6.32英寸', '1216 x 2640', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE, Samsung Display', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X8s+', '6.59英寸', '1256 x 2760', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE, Visionox', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X8 Ultra', '6.82英寸', 'LTPO 1440 x 3168', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', 'Samsung Display', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'K13', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno14', '6.59 英寸', '1256 x 2760', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno14 Pro', '6.83英寸', '1272 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE, Visionox', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Reno14 F', '6.57英寸', '1080 x 2372', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'K13 Turbo Pro', '6.8英寸', '1280 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'K13 Turbo', '6.8英寸', '1280 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A6 Max', '6.8英寸', '1280 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A6 Pro（中国）', '6.57英寸', '1080 x 2372', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A6 GT', '6.8英寸', '1280 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F31', '6.57英寸', '1080 x 2372', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F31 Pro', '6.57英寸', '1080 x 2372', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'F31 Pro+', '6.8英寸', '1280 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A6 Pro 4G', '6.57英寸', '1080 x 2372', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'A6 Pro', '6.57英寸', '1080 x 2372', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X9', '6.59英寸', '1256 x 2760', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'OPPO', 'Find X9 Pro', '6.78英寸', 'LTPO 1272 x 2772', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'X2 Pro', '6.5英寸', '1080 x 2400', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '10/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'X2', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（60Hz）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'XT', '6.4英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星Super AMOLED', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'X', '6.53英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED', '7/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'X50 Pro 5G', '6.44英寸', '1080 x 2400', '直屏左置双孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '2/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'X7 Pro', '6.55英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E3 AMOLED（120Hz）', '2/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'X7', '6.4英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（60Hz）', '', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'X50 Pro Player', '6.44英寸', '1080 x 2400', '直屏左置双孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（电竞屏）', '5/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '7 Pro', '6.4英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（60Hz）', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'X7 (印度)', '6.4英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '天马OLED（60Hz）', '2/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'V15 5G', '6.4英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED', '1/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Q2 Pro', '6.4英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '天马OLED（90Hz）', '10/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Q3 Pro 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'X7 Pro Ultra', '6.55英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '侧装', '京东方柔性OLED（1.5K）', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT Neo', '6.43英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E4 AMOLED（120Hz）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '8 Pro', '6.4英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（60Hz）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '8', '6.4英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'X7 Max 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '5/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Q3 Pro嘉年华', '6.43英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（120Hz）', '5/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT Neo Flash', '6.43英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E4 AMOLED（120Hz）', '5/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT Master', '6.43英寸', '1080 x 2400', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（60Hz）', '7/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT Explorer Master', '6.55英寸', '1080 x 2400', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '7/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT Neo2', '6.62英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E4 AMOLED（120Hz）', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT Neo2T', '6.43英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '10/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT2', '6.62英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT2 Pro', '6.7英寸', 'LTPO2 1440 x 3216', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 120Hz）', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '9 Pro +', '6.4英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT Neo 3', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（120Hz）', '3/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT Neo 3 150W', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '3/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '9', '6.4英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '天马LCD', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Q5 Pro', '6.62英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Narzo 50 Pro', '6.4英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT Neo 3T', '6.62英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（120Hz）', '6/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT2 Explorer Master', '6.7英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K）', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '10', '6.4英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '侧装', '京东方OLED（90Hz）', '11/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '10 Pro +', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '11/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT Neo 5', '6.74英寸', '1240 x 2772', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方1.5K OLED（144Hz）', '2/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT Neo 5 240W', '6.74英寸', '1240 x 2772', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方OLED（144Hz）', '2/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT Neo5 SE', '6.74英寸', '1240 x 2772', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '天马OLED（120Hz）', '4/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '11 (中国)', '6.43英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（1.5K）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '11 Pro', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '11 Pro +', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方2K OLED（LTPO）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Narzo 60', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '天马LCD', '7/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Narzo 60 Pro', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '7/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT3', '6.74英寸', '1240 x 2772', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（144Hz）', '9/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT5 240W', '6.74英寸', '1240 x 2772', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方OLED（144Hz）', '8/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT5 Pro', '6.78英寸', 'LTPO 1264 x 2780', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星LTPO 2K AMOLED（144Hz）', '12/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '12 Pro', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '12 Pro +', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '12 +', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（90Hz）', '2/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Narzo 70 Pro', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT Neo6 SE', '6.78英寸', '1264 x 2780', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（120Hz）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'P1', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'P1 Pro', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Narzo 70', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT Neo6', '6.78英寸', 'LTPO 1264 x 2780', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方1.5K OLED（144Hz）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT 6T', '6.78英寸', 'LTPO 1264 x 2780', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（120Hz）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT 6', '6.78英寸', 'LTPO 1264 x 2780', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（144Hz）', '6/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '12 4G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD', '6/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT6 (中国)', '6.78英寸', 'LTPO 1264 x 2780', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方1.5K OLED（144Hz）', '7/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '13 Pro', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E7 AMOLED（LTPO）', '7/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '13 Pro +', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星2K LTPO AMOLED', '7/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '13 4G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD', '8/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '13 +', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '8/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Narzo 70 Turbo', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（120Hz）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'P2 Pro', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'P1 speed', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT 7 Pro', '6.78英寸', '1264 x 2780', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO 2K AMOLED（144Hz）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Neo7', '6.78英寸', 'LTPO 1264 x 2780', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '12/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '14 Pro +', '6.83英寸', '1272 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E7 AMOLED（LTPO）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '14 Pro', '6.77英寸', '1080 x 2392', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方2K OLED（LTPO）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT7 Pro Racing', '6.78英寸', 'LTPO 1264 x 2780', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO 2K AMOLED（144Hz）', '2/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'P3 Pro', '6.83英寸', '1272 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '2/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Neo7 SE', '6.78英寸', 'LTPO 1264 x 2780', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（120Hz）', '2/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Neo7x', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '2/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Neo7 SE', '6.78英寸', 'LTPO 1264 x 2780', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '2/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '14 Pro Lite', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'P3', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'CSOT', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'P3 Ultra', '6.83英寸', '1272 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '14', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Visionox', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Narzo 80 Pro', '6.72英寸', '1080 x 2392', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT7（中国）', '6.8 英寸', '1280 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', 'BOE', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '14T', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'CSOT', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT 7', '6.78 英寸', '1264 x 2780', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT 7T', '6.8英寸', '1280 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'Neo7 Turbo', '6.8 英寸', '1280 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '15', '6.8英寸', '1280 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '15 Pro', '6.8英寸', '1280 x 2800', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'P4 Pro', '6.8英寸', '1280 x 2800', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'P4', '6.77英寸', '1080 x 2392', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '15T', '6.57英寸', '1080 x 2372', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', '15 Lite', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT8（中国）', '6.79英寸', 'LTPO 1440 x 3136', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'GT 8 Pro', '6.79英寸', 'LTPO 1440 x 3136', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'REALME', 'C85 Pro', '6.8英寸', '1080 x 2344', '', '120Hz', '', '', '', '', '光学指纹', '', '11/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '7', '6.41英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '5/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '7 Pro', '6.67英寸', '1440 x 3120', '曲面屏无孔', '90Hz', '', '', '', '', '光学指纹', '三星定制2K AMOLED', '5/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '7 Pro 5G', '6.67英寸', '1440 x 3120', '曲面屏无孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '5/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '7T', '6.55英寸', '1080 x 2400', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '7T Pro', '6.67英寸', '1440 x 3120', '直屏无孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（2K+ 90Hz）', '10/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '7T Pro 5G McLaren', '6.67英寸', '1440 x 3120', '曲面屏无孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（定制版）', '10/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '8 5g (t-mobile)', '6.55英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '8 5G UW (Verizon)', '6.55英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '8', '6.55英寸', '1080 x 2400', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '8 Pro', '6.78英寸', '1440 x 3168', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星Dynamic AMOLED 2X（2K 120Hz）', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Nord', '6.44英寸', '1080 x 2400', '直屏左置双孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '7/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '8T + 5G', '6.55英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '10/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '8T', '6.55英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '10/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '9R', '6.55英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E3 AMOLED', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '9', '6.55英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '9 Pro', '6.7英寸', 'LTPO Fluid2 1440 x 3216', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 120Hz）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Nord CE 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '6/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Nord 2 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '7/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '9RT 5G', '6.62英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E4 AMOLED', '10/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Nord CE 2 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（入门级）', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '10 Pro', '6.7英寸', 'LTPO2 1440 x 3216', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO 2K AMOLED', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Ace', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（1.5K）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '10R', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（FHD+）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '10R 150W', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Nord N20 5G', '6.43英寸', '1080 x 2400', '直屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Nord 2T', '6.43英寸', '1080 x 2400', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '10T', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（1.5K）', '8/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Ace Pro', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方Q9+ OLED', '8/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '11', '6.7英寸', 'LTPO3 1440 x 3216', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 120Hz）', '1/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Ace 2', '6.74英寸', '1240 x 2772', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方Q9 OLED', '2/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '11R', '6.74英寸', '1240 x 2772', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（1.5K）', '2/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Ace 2V', '6.74英寸', '1240 x 2772', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（1.5K直屏）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Nord 3', '6.74英寸', '1240 x 2772', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '7/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Nord CE3', '6.7英寸', '1080 x 2412', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（FHD+）', '7/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Ace 2 Pro', '6.74英寸', '1240 x 2772', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方Q9+ OLED', '8/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Ace 3', '6.78英寸', 'LTPO 1264 x 2780', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方X1 OLED（LTPO）', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '12', '6.82英寸', 'LTPO 1440 x 3168', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方X1 OLED（2K LTPO）', '12/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '12R', '6.78英寸', 'LTPO4 1264 x 2780', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（1.5K）', '2/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Nord CE4', '6.7英寸', '1080 x 2412', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（入门级）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Ace 3V', '6.74英寸', '1240 x 2772', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（1.5K）', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Nord CE4 Lite (印度)', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD', '6/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Nord CE4 Lite', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD', '6/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Ace 3 Pro', '6.78英寸', '1264 x 2780', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方8T LTPO OLED', '6/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Nord 4', '6.74英寸', '1240 x 2772', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '7/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '13', '6.82英寸', 'LTPO 4.1 1440 x 3168', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '京东方X2 OLED（LTPO）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Ace 5', '6.78英寸', 'LTPO 1264 x 2780', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方Q9+ OLED', '12/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Ace 5 Pro', '6.78英寸', 'LTPO 1264 x 2780', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方Q9+ OLED', '12/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '13R', '6.78英寸', 'LTPO 4.1 1264 x 2780', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（1.5K）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '13T', '6.32英寸', 'LTPO 1216 x 2640', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE, CSOT', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Ace 5 Racing', '6.77英寸', '1080 x 2392', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Ace 5 Ultra', '6.83英寸', '1272 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'Samsung Display', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '13s', '6.32英寸', 'LTPO 1216 x 2640', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Nord CE5', '6.77英寸', '1080 x 2392', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Nord 5', '6.83英寸', '1272 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', 'Ace 6', '6.83英寸', 'LTPO  1272 x 2800', '直屏中置单孔', '165Hz', '', '', '', '', '超声波', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'oneplus', '15', '6.78英寸', 'LTPO 1272 x 2772', '直屏中置单孔', '165Hz', '', '', '', '', '超声波', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V15 Pro', '6.39英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '2/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO', '6.41英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（初代电竞屏）', '3/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X27', '6.39英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星Super AMOLED（升降前摄）', '3/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X27 Pro', '6.7英寸', '1080 x 2460', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '3/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S1 Pro (中国)', '6.39英寸', '1080 x 2340', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（水滴屏）', '5/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo', '6.38英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '7/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S1', '6.38英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '7/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Z5', '6.38英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（FHD+）', '7/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V17 Neo', '6.38英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马OLED（水滴屏）', '8/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Pro', '6.41英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（初代液冷散热）', '8/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Pro 5G', '6.41英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（5G版本）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Z1x', '6.38英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（FHD+）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'NEX 3', '6.89英寸', '1080 x 2256', '曲面屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星瀑布屏（无界全面屏）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'NEX 3 5G', '6.89英寸', '1080 x 2256', '曲面屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星瀑布屏（5G版）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V17 Pro', '6.44英寸', '1080 x 2400', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（双前摄）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo 855', '6.38英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '10/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S5', '6.44英寸', '1080 x 2400', '直屏右置单孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（打孔屏）', '11/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S1 Pro', '6.38英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马OLED（60Hz）', '11/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V17 (俄罗斯)', '6.38英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（区域特供）', '11/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y9s', '6.38英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '12/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo 855 Racing', '6.38英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（超频版）', '12/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V17', '6.44英寸', '1080 x 2400', '直屏右置单孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（FHD+）', '11/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X30', '6.44英寸', '1080 x 2400', '直屏右置单孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（60Hz）', '12/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X30 Pro', '6.44英寸', '1080 x 2400', '直屏右置单孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（60Hz）', '12/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 3 5G', '6.44英寸', '1080 x 2400', '直屏右置单孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（144Hz）', '2/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V19 (印度尼西亚)', '6.44英寸', '1080 x 2400', '直屏右置单孔', '60Hz', '', '', '', '', '光学指纹', '天马OLED（区域特供）', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'NEX 3S 5G', '6.89英寸', '1080 x 2256', '曲面屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星瀑布屏（升级版）', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S6 5G', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（挖孔屏）', '3/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V19', '6.44英寸', '1080 x 2400', '直屏右置双孔', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X50 Lite', '6.38英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（60Hz）', '5/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X50', '6.56英寸', '1080 x 2376', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '7/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X50 5G', '6.56英寸', '1080 x 2376', '直屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（5G版）', '6/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X50 Pro', '6.56英寸', '1080 x 2376', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（微云台技术）', '6/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X50 Pro +', '6.56英寸', '1080 x 2376', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（120Hz）', '6/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V19 Neo', '6.44英寸', '1080 x 2400', '直屏右置单孔', '60Hz', '', '', '', '', '光学指纹', '天马OLED（60Hz）', '6/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S7', '6.44英寸', '1080 x 2400', '直屏刘海', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '8/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S1 Prime', '6.38英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '8/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 5 5G', '6.56英寸', '1080 x 2376', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '8/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 5 Pro 5G', '6.56英寸', '1080 x 2376', '曲面屏左置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 120Hz）', '8/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y51 (2020年9月)', '6.38英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方LCD', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V20 Pro', '6.44英寸', '1080 x 2400', '直屏刘海', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V20 SE', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马OLED（60Hz）', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V20', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X50e', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马OLED（60Hz）', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y73s', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方LCD', '10/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y70', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '10/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X51 5G', '6.56英寸', '1080 x 2376', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（欧洲特供）', '10/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S7e', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '11/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V20 2021', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（升级版）', '12/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X60 (中国)', '6.56英寸', '1080 x 2376', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '12/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X60 Pro (China)', '6.56英寸', '1080 x 2376', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（120Hz）', '12/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 7', '6.62英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '1/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X60 Pro+', '6.56英寸', '1080 x 2376', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 120Hz）', '1/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S7t', '6.44英寸', '1080 x 2400', '直屏刘海', '60Hz', '', '', '', '', '光学指纹', '天马OLED（90Hz）', '2/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S9e', '6.44英寸', '1080 x 2404', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S9', '6.44英寸', '1080 x 2400', '直屏刘海', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo5', '6.62英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E4 AMOLED（120Hz）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X60', '6.56英寸', '1080 x 2376', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（全球版）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X60 Pro', '6.56英寸', '1080 x 2376', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（120Hz）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X60t', '6.56英寸', '1080 x 2376', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（区域特供）', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 7 (印度)', '6.62英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V21e', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马OLED（60Hz）', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V21', '6.44英寸', '1080 x 2400', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V21 5G', '6.44英寸', '1080 x 2400', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X60s', '6.56英寸', '1080 x 2376', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（区域版）', '5/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V21e 5G', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马OLED（60Hz）', '6/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X60t Pro +', '6.56英寸', '1080 x 2376', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K）', '6/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y73', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方LCD', '6/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S10', '6.44英寸', '1080 x 2400', '直屏刘海', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '7/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S10 Pro', '6.44英寸', '1080 x 2400', '直屏刘海', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '7/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 8', '6.56英寸', '1080 x 2376', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E5 AMOLED（120Hz）', '8/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 8 Pro', '6.78英寸', 'LTPO 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星LTPO AMOLED（2K 120Hz）', '8/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X70', '6.56英寸', '1080 x 2376', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X70 Pro', '6.56英寸', '1080 x 2376', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（120Hz）', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X70 Pro +', '6.78英寸', 'LTPO 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 120Hz）', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S10e', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '10/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y71t', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '10/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V23e', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（60Hz）', '11/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V23e 5G', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '11/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo5 S', '6.62英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E4 AMOLED（120Hz）', '12/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S12', '6.44英寸', '1080 x 2400', '直屏刘海', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '12/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S12 Pro', '6.56英寸', '1080 x 2400', '曲面屏中置刘海', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（90Hz）', '12/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V23 5G', '6.44英寸', '1080 x 2400', '直屏刘海', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V23 Pro', '6.56英寸', '1080 x 2376', '直屏刘海', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 9 (中国)', '6.78英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E5 AMOLED（120Hz）', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 9 Pro', '6.78英寸', 'LTPO2 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星LTPO AMOLED（2K 120Hz）', '1/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 9 SE', '6.62英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '2/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X Note', '7.0英寸', 'LTPO 1440 x 3080', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（7英寸巨屏）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo6 (中国)', '6.62英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E5 AMOLED（120Hz）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S15e', '6.44英寸', '1080 x 2404', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'T1 (骁龙778g)', '6.44英寸', '1080 x 2404', '直屏水滴', '90Hz', '', '', '', '', '侧装', '天马LCD', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X80', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E5 AMOLED（120Hz）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X80 Pro', '6.78英寸', 'LTPO3 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星LTPO AMOLED（2K 120Hz）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z6 44W', '6.44英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y55', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方LCD', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z6 Pro', '6.44英寸', '1080 x 2404', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（144Hz）', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'T1 (骁龙680)', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'T1 Pro', '6.44英寸', '1080 x 2404', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo6 SE', '6.62英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S15', '6.62英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S15 Pro', '6.56英寸', '1080 x 2376', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y75', '6.44英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马LCD', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'T2 (中国)', '6.62英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo 6', '6.62英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E5 AMOLED（120Hz）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 10', '6.78英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（144Hz）', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 10 Pro', '6.78英寸', 'LTPO3 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星LTPO AMOLED（2K 144Hz）', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 9T', '6.78英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（144Hz）', '8/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V25', '6.44英寸', '1080 x 2404', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '8/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V25 Pro', '6.56英寸', '1080 x 2376', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '8/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V25e', '6.44英寸', '1080 x 2404', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '8/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X80 Lite', '6.44英寸', '1080 x 2404', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '9/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo7 (中国)', '6.78英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（144Hz）', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V21s', '6.44英寸', '1080 x 2404', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '天马OLED（90Hz）', '11/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X90', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方Q9 OLED（1.5K）', '11/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X90 Pro', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 120Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X90 Pro +', '6.78英寸', 'LTPO4 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星LTPO AMOLED（2K 120Hz）', '11/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo7 SE', '6.78英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（144Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 11', '6.78英寸', 'LTPO4  1440 x 3200', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（144Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 11 Pro', '6.78英寸', 'LTPO4 1440 x 3200', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', '三星LTPO AMOLED（2K 144Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S16e', '6.62英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S16', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S16 Pro', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo7 Racing', '6.78英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（144Hz）', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo 7', '6.78英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（144Hz）', '2/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y100', '6.38英寸', '1080 x 2400', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '京东方LCD', '2/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V27e', '6.62英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V27', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V27 Pro', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z7', '6.38英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'T2 (印度)', '6.38英寸', '1080 x 2400', '直屏水滴', '90Hz', '', '', '', '', '光学指纹', '天马LCD', '4/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y78 +', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（FHD+）', '4/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z7s', '6.38英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '天马OLED（90Hz）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo8', '6.78英寸', '1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方1.5K OLED（144Hz）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo8 Pro', '6.78英寸', '1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（144Hz）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S17e', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y78', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S17t', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S17', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S17 Pro', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V29 Lite', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（60Hz）', '6/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X90s', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 120Hz）', '6/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo 7 Pro', '6.78英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方1.5K OLED（144Hz）', '7/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 11S', '6.78英寸', 'LTPO4 1440 x 3200', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED（144Hz）', '7/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V29', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '7/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V29e (印度)', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '8/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z7 Pro', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（144Hz）', '8/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'T2 Pro', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '9/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V29 Pro', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y200 (印度)', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V29e', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'T2', '6.62英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y100 (中国)', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 12', '6.78英寸', 'LTPO 1260 x 2800', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星E7 AMOLED（144Hz）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 12 Pro', '6.78英寸', 'LTPO 1440 x 3200', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 144Hz）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X100', '6.78英寸', 'LTPO 1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方Q9+ OLED（1.5K）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X100 Pro', '6.78英寸', 'LTPO 1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 120Hz）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S18e', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '12/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S18', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '12/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S18 Pro', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '12/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo9', '6.78英寸', 'LTPO 1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方1.5K OLED（144Hz）', '12/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo9 Pro (中国)', '6.78英寸', 'LTPO 1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星E7 AMOLED（144Hz）', '12/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V30 Lite', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD', '12/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y100 (IDN)', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD', '1/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V30', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '2/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y200e', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD', '2/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo9 Pro', '6.78英寸', 'LTPO 1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 144Hz）', '2/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V30 Pro', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 120Hz）', '2/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z9', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺AMOLED（1.5K 120Hz）', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'T3', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD（推测）', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V40 SE', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（推测）', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V30 Lite (ME)', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD（推测）', '2/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V30 Lite 4G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（推测）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z9 (中国)', '6.78英寸', '1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '维信诺AMOLED（1.5K 120Hz）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z9 Turbo', '6.78英寸', '1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '维信诺AMOLED（144Hz）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V30 SE', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD（推测）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V30e', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（推测）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y100 4G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD（推测）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X100s', '6.78英寸', 'LTPO 1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E7 AMOLED（2K）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X100s Pro', '6.78英寸', 'LTPO 1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X100 Ultra', '6.78英寸', 'LTPO 1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星LTPO AMOLED（2K）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y200 (中国)', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（推测）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y200 GT', '6.78英寸', '1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方OLED（推测）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo9S Pro', '6.78英寸', 'LTPO 1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星E7 AMOLED（144Hz）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y200 Pro', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（推测）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S19', '6.78英寸', '1260 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S19 Pro', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V40', '6.78英寸', '1260 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（推测）', '6/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo9S Pro +', '6.78英寸', 'LTPO 1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', '三星LTPO AMOLED（2K）', '7/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V40 Lite', '6.78英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（推测）', '7/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V40 Pro', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（推测）', '8/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z9s', '6.77英寸', '1080 x 2392', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺AMOLED（144Hz）', '8/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z9s Pro', '6.77英寸', '1080 x 2392', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K）', '8/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'T3 Pro', '6.77英寸', '1080 x 2392', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（推测）', '8/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y300 Pro', '6.77英寸', '1080 x 2392', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（推测）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'T3 Ultra', '6.78英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（推测）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V40e', '6.77英寸', '1080 x 2392', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（推测）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V40 Lite (IDN)', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD（推测）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V40 Lite 4G (IDN)', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（推测）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X200', '6.67英寸', '1260 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E7 AMOLED（2K）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X200 Pro', '6.78英寸', 'LTPO 1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星LTPO AMOLED（2K）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X200 Pro mini', '6.31英寸', 'LTPO 1216 x 2640', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（推测）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y300 Plus', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（推测）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 13', '6.82英寸', 'LTPO 1440 x 3168', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', '三星E7 AMOLED（144Hz）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y300', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD（推测）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S20', '6.67英寸', '1260 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S20 Pro', '6.67英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（120Hz）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo10 (中国)', '6.78英寸', 'LTPO 1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', '维信诺AMOLED（144Hz）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo10 Pro (中国)', '6.78英寸', 'LTPO 1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', '三星LTPO AMOLED（2K）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y300 (中国)', '6.77英寸', '1080 x 2392', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（推测）', '12/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z9 Turbo Endurance', '6.78英寸', '1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '维信诺AMOLED（144Hz）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y200', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD（推测）', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y200 (Asia)', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（推测）', '12/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y200 4G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD（推测）', '1/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V50', '6.77英寸', '1080 x 2392', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K）', '2/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo 10R', '6.78英寸', '1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'BOE', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z10', '6.77 英寸', '1080 x 2392', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X200s', '6.67英寸', '1260 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', 'BOE', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X200 Ultra', '6.82英寸', '1440 x 3168', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', 'Samsung Display', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'T4', '6.77 英寸', '1080 x 2392', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z10 Turbo', '6.78 英寸', '1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'Visionox', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z10 Turbo Pro', '6.78 英寸', '1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'Visionox', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y300 GT', '6.78 英寸', '1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'BOE', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo10 Pro+（中国）', '6.82英寸', '1440 x 3168', '直屏中置单孔', 'LTPO 144Hz', '', '', '', '', '超声波', 'BOE', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo 10', '6.78 英寸', '1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'BOE', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S30', '6.67英寸', '1260 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Visionox', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'S30 Pro mini', '6.31英寸', 'LTPO 1216 x 2640', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'T4 Ultra', '6.67英寸', '1260 x 2800', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y400 Pro', '6.77英寸', '1080 x 2392', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X200 FE', '6.31英寸', 'LTPO 1216 x 2640', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z10R（印度）', '6.77英寸', '1080 x 2392', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'T4R', '6.77英寸', '1080 x 2392', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y400 4G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y400', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Z10 Turbo+', '6.78英寸', '1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V60', '6.77英寸', '1080 x 2392', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'T4 Pro', '6.77英寸', '1080 x 2392', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y500（中国）', '6.77英寸', '1080 x 2392', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V60 Lite 4G', '6.77英寸', '1080 x 2392', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V60 Lite', '6.77英寸', '1080 x 2392', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'V60e', '6.77英寸', '1080 x 2392', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X300', '6.31英寸', 'LTPO 1216 x 2640', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'X300 Pro', '6.78英寸', 'LTPO 1260 x 2800', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO 15', '6.85英寸', 'LTPO 1440 x 3168', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'iQOO Neo11（中国）', '6.82英寸', 'LTPO 1440 x 3168', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', '', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'VIVO', 'Y500 Pro', '6.67英寸', '120Hz', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '', '11/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Pouvoir 3 Plus', '6.35英寸', '720 x 1548', '直屏水滴', '60Hz', '', '', '', '', '后置', '天马LCD（推测）', '8/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Phantom 9', '6.39英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '天马AMOLED（推测）', '7/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Camon 12 Pro', '6.35英寸', '720 x 1600', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '京东方OLED（推测）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Camon 18 Premier', '6.7英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '三星AMOLED', '10/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Pova 4 Pro', '6.66英寸', '1080 x 2460', '直屏水滴', '90Hz', '', '', '', '', '侧装', '天马AMOLED', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Phantom X2 Pro', '6.8英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED', '12/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Phantom X2', '6.7英寸', '1080 x 2340', '曲面屏左置双孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（推测）', '6/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Camon 20', '6.67英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '光学指纹', '天马LCD（推测）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Camon 20 Pro', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（推测）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Camon 20 Pro 5G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED（推测）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Camon 20 Premier', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Camon 20s Pro 5G', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺AMOLED（推测）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Spark 20 Pro+', '6.78英寸', '1080 x 2436', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（推测）', '12/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Camon 30', '6.78英寸', '1080 x 2436', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Camon 30 5G', '6.78英寸', '1080 x 2436', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺AMOLED（推测）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Camon 30 Pro', '6.78英寸', '1080 x 2436', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方1.5K OLED', '2/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Camon 30 Premier', '6.77英寸', 'LTPO 1264 x 2780', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED', '2/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Pova 6', '6.78英寸', '1080 x 2460', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Camon 30S Pro', '6.78英寸', '1080 x 2436', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（推测）', '7/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Spark 30 Pro', '6.78英寸', '1080 x 2436', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（推测）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Tecno Camon 30S', '6.78英寸', '1080 x 2436', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺OLED', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Camon 40', '6.78 英寸', '1080 x 2436', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Camon 40 Pro 4G', '6.78 英寸', '1080 x 2436', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Camon 40 Pro', '6.78 英寸', '1080 x 2436', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'Visionox', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Camon 40 Premier', '6.67英寸', 'LTPO 1260 x 2800', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'Visionox', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Pova Curve', '6.78 英寸', '1080 x 2436', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'Tianma', '6/5/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Pova 7 4G', '6.78英寸', '1080 x 2460', '直屏中置单孔', '120Hz', '', '', '', '', '?', '', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Pova 7 Ultra', '6.67英寸', '1260 x 2800', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Spark 40 Pro', '6.78英寸', '1224 x 2720', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Spark 40 Pro+', '6.78英寸', '1224 x 2720', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Pova 7 Pro', '6.78英寸', '1224 x 2720', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Spark Slim', '6.78英寸', '1224 x 2720', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'Tecno', 'Pova Slim', '6.78英寸', '1224 x 2720', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 6', '6.01英寸', '1080 x 2160', '直屏刘海', '60Hz', '', '', '', '', '后置', '天马LCD（推测）', '6/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Zero X', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（推测）', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Zero X Pro', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星AMOLED', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 11', '6.7英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '侧装', '天马LCD（推测）', '11/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 12', '6.7英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '侧装', '京东方OLED', '4/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 12 G96', '6.7英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '侧装', '维信诺AMOLED', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 12 VIP', '6.7英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '三星LTPO AMOLED', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 12 5G', '6.7英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '侧装', '京东方1.5K OLED', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 12 Pro 5G', '6.7英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '侧装', '天马AMOLED', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 12 Pro', '6.7英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '侧装', '维信诺AMOLED', '7/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 12i 2022', '6.7英寸', '1080 x 2400', '直屏水滴', '90Hz', '', '', '', '', '侧装', '天马LCD（推测）', '9/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Zero 20', '6.7英寸', '1080 x 2400', '直屏水滴', '90Hz', '', '', '', '', '侧装', '京东方OLED（推测）', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Zero Ultra', '6.8英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方 BF068XMM-TK3-7900', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 12 (2023)', '6.7英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '侧装', '维信诺AMOLED', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 30i', '6.66英寸', '1080 x 2400', '直屏水滴', '60Hz', '', '', '', '', '侧装', '京东方AMOLED', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 30 Pro', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '天马AMOLED', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 30 VIP', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星E6 AMOLED', '6/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix GT 10 Pro', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马AMOLED', '8/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Zero 30', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方OLED（推测）', '9/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Zero 30 4G', '6.78英寸', '1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（推测）', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 40', '6.78英寸', '1080 x 2436', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺AMOLED（推测）', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 40 Pro 4G', '6.78英寸', '1080 x 2436', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 40 Pro', '6.78英寸', '1080 x 2436', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 40 Pro +', '6.78英寸', '1080 x 2436', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方Q9+ OLED', '3/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix GT 20 Pro', '6.78英寸', '1080 x 2436', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '天马AMOLED', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 40 5G', '6.78英寸', '1080 x 2436', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺AMOLED（推测）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Zero 40 4G', '6.78英寸', '1080 x 2436', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（推测）', '8/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Zero 40', '6.78英寸', '1080 x 2436', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方OLED（推测）', '8/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Note 40S', '6.78英寸', '1080 x 2436', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺OLED（推测）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Infinix Hot 50 Pro + 4G', '6.78英寸', '1080 x 2436', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（推测）', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Hot 50 Pro 4G', '6.78 英寸', '1080 x 2436', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma', '11/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Note 50 4G', '6.78 英寸', '1080 x 2436', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'BOE', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Note 50 Pro 4G', '6.78 英寸', '1080 x 2436', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'BOE', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Note 50 Pro+', '6.78 英寸', '1080 x 2436', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'CSOT', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Note 50s', '6.78 英寸', '1080 x 2436', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'Tianma', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'GT 30 Pro', '6.78 英寸', '1224 x 2720', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'CSOT', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Hot 60 Pro', '6.78英寸', '1224 x 2720', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'Hot 60 Pro+', '6.78英寸', '1224 x 2720', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'iNfinit', 'GT 30', '6.78英寸', '1224 x 2720', '直屏中置单孔', '1224 x 2720', '', '', '', '', '光学指纹', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', 'zero', '5.99英寸', '1080 x 2340', '曲面屏无孔', '60Hz', '', '', '', '', '光学指纹', '维信诺（推测）', '1/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '16s', '6.2英寸', '1080 x 2232', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星 AMOLED', '4/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '16Xs', '6.2英寸', '1080 x 2232', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星 AMOLED', '6/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '16s Pro', '6.2英寸', '1080 x 2232', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星 AMOLED', '8/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '16t', '6.5英寸', '1080 x 2232', '直屏无孔', '60Hz', '', '', '', '', '光学指纹', '三星 Super AMOLED（定制）', '10/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '17', '6.6英寸', '1080 x 2340', '直屏右置单孔', '120Hz', '', '', '', '', '光学指纹', '三星 Super AMOLED（定制）', '5/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '17 Pro', '6.6英寸', '1080 x 2340', '直屏右置单孔', '120Hz', '', '', '', '', '光学指纹', '三星 Super AMOLED（定制）', '5/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '18', '6.2英寸', '1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 E4 AMOLED（2K+）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '18 Pro', '6.7英寸', '1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 E4 AMOLED（2K+）', '3/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '18x', '6.67英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方 OLED（推测）', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '18s', '6.2英寸', '1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 E4 AMOLED（2K+）', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '20', '6.55英寸', '1080 x 2400', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', '京东方/三星混合供应', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '18s Pro', '6.7英寸', '1440 x 3200', '曲面屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 E5 AMOLED（2K+）', '9/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '20 Pro', '6.81英寸', 'LTPO 1440 x 3200', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 E6 AMOLED（2K+）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '20 Infinity', '6.79英寸', 'LTPO 1368 x 3192', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方 Q9+ OLED（无界屏）', '3/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '20 Classic', '6.55英寸', '1080 x 2400', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', '京东方 OLED（直屏）', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '21', '6.55英寸', '1080 x 2340', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 E7 AMOLED（直屏）', '11/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', '21 Pro', '6.79英寸', 'LTPO 1368 x 3192', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星 LTPO AMOLED（2K）', '2/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', 'Meizu 21 Note', '6.78英寸', 'LTPO 1264 x 2780', '直屏中置单孔', '144Hz', '', '', '', '', '超声波', '天马 OLED（直屏）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', 'Lucky 08', '6.78英寸', 'LTPO 1264 x 2780', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方 LCD（推测）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', 'Note 22 4G', '6.78英寸', '1080 x 2436', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', 'Note 16 Pro', '6.78 英寸', '1224 x 2720', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'BOE', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '魅族', 'Note 22 Pro', '6.78英寸', '1224 x 2720', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', 'BOE', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '', '22', '6.3英寸', 'LTPO 1200 x 2670', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Motorola One Zoom', '6.39英寸', '1080 x 2340', '直屏水滴', '60Hz', '', '', '', '', '光学指纹', '三星AMOLED（FHD+）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Motorola Edge', '6.7英寸', '1080 x 2340', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '京东方OLED（曲面屏）', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Motorola Edge+ (2020)', '6.7英寸', '1080 x 2340', '曲面屏左置单孔', '90Hz', '', '', '', '', '光学指纹', '三星AMOLED（2K 90Hz）', '4/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Motorola Edge 20 Lite', '6.7英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '侧装', '天马LCD（90Hz）', '7/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 20', '6.7英寸', '1080 x 2400', '直屏中置单孔', '144Hz', '', '', '', '', '侧装', '京东方OLED（144Hz）', '7/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 20 Pro', '6.7英寸', '1080 x 2400', '直屏中置单孔', '144Hz', '', '', '', '', '侧装', '三星AMOLED（144Hz）', '7/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 20 Fusion', '6.7英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '侧装', '天马LCD（120Hz）', '8/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', '摩托罗拉Edge + (2023)', '6.67英寸', '1080 x 2400', '曲面屏中置单孔', '165Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 165Hz）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 40', '6.55英寸', 'P-oled 1080 x 2400', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方OLED（144Hz）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Moto G84', '6.5英寸', 'P-oled 1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（90Hz）', '8/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 40 Neo', '6.55英寸', 'P-oled 1080 x 2400', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方OLED（120Hz）', '9/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge (2023)', '6.6英寸', 'P-oled 1080 x 2400', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星AMOLED（144Hz）', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 50 Pro', '6.7英寸', 'P-oled 1220 x 2712', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方Q9+ OLED（1.5K 144Hz）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 50 Fusion', '6.7英寸', 'P-oled 1080 x 2400', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '天马LCD（120Hz）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 50 Ultra', '6.7英寸', 'P-oled 1220 x 2712', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 165Hz）', '4/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Moto G Stylus 5G (2024)', '6.7英寸', 'P-oled 1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Moto X50 Ultra', '6.7英寸', 'P-oled 1220 x 2712', '直屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '三星E7 AMOLED（2K 165Hz）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge (2024)', '6.6英寸', 'P-oled 1080 x 2400', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方X1 OLED（1.5K 144Hz）', '6/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'S50 Neo', '6.7英寸', 'P-oled 1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马LCD（60Hz）', '6/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Moto G85', '6.67英寸', 'P-oled 1080 x 2400', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方OLED（90Hz）', '6/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 50', '6.7英寸', 'P-oled 1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺AMOLED（144Hz）', '8/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 50 Neo', '6.4英寸', 'LTPO p-oled 1200 x 2670', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '天马OLED（90Hz）', '8/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Moto S50', '6.36英寸', 'LTPO p-oled 1272 x 2670', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方LCD（60Hz）', '9/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'ThinkPhone 25', '6.36英寸', 'P-oled 1220 x 2670', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO AMOLED（2K 120Hz）', '10/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 60 Fusion', '6.67英寸', 'P-OLED 1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'pOLED by LG or Tianma', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 60s', '6.67英寸', 'P-OLED 1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Moto G Stylus 5G (2025)', '6.7英寸', '1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 60 Stylus', '6.7英寸', 'P-OLED 1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 60', '6.67英寸', 'P-OLED 1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 60 Pro', '6.7英寸', 'P-OLED 1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '4/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge（2025）', '6.7英寸', 'P-OLED 1220 x 2712', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'BOE', '6/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Moto G86', '6.67英寸', 'P-OLED 1220 x 2712', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Moto G86 power', '6.67英寸', 'P-OLED 1220 x 2712', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Tianma', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'G96', '6.67英寸', 'P-OLED 1080 x 2400', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方', '7/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 60 Neo', '6.36英寸', 'LTPO P-OLED 1200 x 2670', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 70', '6.7英寸', 'P-OLED 1220 x 2712', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '京东方', '10/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Signature', '6.8英寸', 'LTPO 1264 x 2780', '曲面屏中置单孔', '165Hz', '', '', '', '', '超声波', '京东方', '1/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'G67', '6.78英寸', '1272 x 2772', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '华星', '1/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'G77', '6.78英寸', '1272 x 2772', '直屏中置单孔', '120Hz', '', '', '', '', '侧装', '华星', '1/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 70 Fusion', '6.78英寸', '1272 x 2772', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方', '3/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 70 Fusion+', '6.8英寸', '1272 x 2772', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方', '3/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'G Stylus (2026)', '6.7英寸', '1220 x 2712', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '维信诺', '4/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'Edge 70 Pro', '6.78英寸', '1272 x 2772', '曲面屏中置单孔', '144Hz', '', '', '', '', '光学指纹', '京东方', '4/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '摩托', 'G87', '6.78英寸', '1272 x 2772', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '华星', '5/1/26', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 3a', '5.6英寸', '1080 x 2220', '直屏无孔', '60Hz', '', '', '', '', '后置', '三星OLED', '5/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 3a XL', '6.0英寸', '1080 x 2160', '直屏无孔', '60Hz', '', '', '', '', '后置', '天马OLED（部分机型为三星）', '5/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 4', '5.7英寸', 'P-OLED 1080 x 2280', '直屏无孔', '90Hz', '', '', '', '', '无', 'LG Display OLED', '10/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 4 XL', '6.3英寸', 'P-OLED 1440 x 3040', '直屏无孔', '90Hz', '', '', '', '', '无', '三星OLED（2K 90Hz）', '10/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 4a', '5.81英寸', '1080 x 2340', '直屏左置单孔', '60Hz', '', '', '', '', '后置', 'LG Display OLED', '8/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 4a 5G', '6.2英寸', '1080 x 2340', '直屏左置单孔', '60Hz', '', '', '', '', '后置', 'LG Display OLED', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 5', '6.0英寸', '1080 x 2340', '直屏左置单孔', '90Hz', '', '', '', '', '后置', '三星OLED（90Hz）', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 5a 5G', '6.34英寸', '1080 x 2400', '直屏左置单孔', '60Hz', '', '', '', '', '后置', 'LG Display OLED', '8/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 6', '6.4英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '三星OLED（90Hz）', '10/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 6 Pro', '6.7英寸', 'LTPO 1440 x 3120', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO OLED（120Hz）', '10/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 6a', '6.1英寸', '1080 x 2400', '直屏中置单孔', '60Hz', '', '', '', '', '光学指纹', '三星刚性OLED（60Hz）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 7', '6.3英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '三星E6 OLED（90Hz）', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 7 Pro', '6.7英寸', 'LTPO 1440 x 3120', '曲面屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO OLED（120Hz）', '10/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 7a', '6.1英寸', '1080 x 2400', '直屏中置单孔', '90Hz', '', '', '', '', '光学指纹', '三星OLED（90Hz）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 8', '6.2英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星M12 OLED（120Hz）', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 8 Pro', '6.7英寸', 'LTPO 1344 x 2992', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星LTPO OLED（2K 120Hz）', '10/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 8a', '6.1英寸', '1080 x 2400', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', '三星OLED（120Hz）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 9', '6.3英寸', '1080 x 2424', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星M14 OLED（首发）', '8/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 9 Pro', '6.3英寸', 'LTPO 1280 x 2856', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星M14 OLED（LTPO）', '8/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 9 Pro XL', '6.8英寸', 'LTPO 1344 x 2992', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '三星M14 OLED（2K LTPO）', '8/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 9a', '6.3 英寸', 'P-OLED 1080 x 2424', '直屏中置单孔', '120Hz', '', '', '', '', '光学指纹', 'Samsung Display', '3/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 10', '6.3英寸', '1080 x 2424', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 10 Pro', '6.3英寸', 'LTPO 1280 x 2856', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', '谷歌', 'Pixel 10 Pro XL', '6.8英寸', 'LTPO 1344 x 2992', '直屏中置单孔', '120Hz', '', '', '', '', '超声波', '', '8/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia XZ3', '6.0英寸', 'P-OLED1440 x 2880', '曲面屏无孔', '60Hz', '', '', '', '', '后置', 'LG Display（推测）', '8/1/18', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 1', '6.5英寸', '1644 x 3840', '直屏无孔', '60Hz', '', '', '', '', '侧装', '三星OLED（4K HDR）', '2/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 5', '6.1英寸', '1080 x 2520', '直屏无孔', '60Hz', '', '', '', '', '侧装', '三星OLED（FHD+）', '9/1/19', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 10 II', '6.0英寸', '1080 x 2520', '直屏无孔', '60Hz', '', '', '', '', '侧装', '京东方OLED', '2/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 1 II', '6.5英寸', '1644 x 3840', '直屏无孔', '60Hz', '', '', '', '', '侧装', '三星OLED（4K HDR）', '2/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 5 II', '6.1英寸', '1080 x 2520', '直屏无孔', '120Hz', '', '', '', '', '侧装', '三星OLED（120Hz）', '9/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia Pro', '6.5英寸', '1644 x 3840', '直屏无孔', '60Hz', '', '', '', '', '侧装', '三星OLED（4K HDR）', '2/1/20', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 10 III', '6.0英寸', '1080 x 2520', '直屏无孔', '60Hz', '', '', '', '', '侧装', '京东方OLED', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 5 III', '6.1英寸', '1080 x 2520', '曲面屏无孔', '120Hz', '', '', '', '', '侧装', '三星OLED（120Hz）', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 1 III', '6.5英寸', '1644 x 3840', '曲面屏无孔', '120Hz', '', '', '', '', '侧装', '三星OLED（4K 120Hz）', '4/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 10 III Lite', '6.0英寸', '1080 x 2520', '直屏无孔', '60Hz', '', '', '', '', '侧装', '天马LCD（推测）', '8/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia pro-i', '6.5英寸', '1644 x 3840', '直屏无孔', '120Hz', '', '', '', '', '侧装', '三星OLED（4K）', '10/1/21', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 10 IV', '6.0英寸', '1080 x 2520', '直屏无孔', '60Hz', '', '', '', '', '侧装', '京东方OLED', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 1 IV', '6.5英寸', '1644 x 3840', '直屏无孔', '120Hz', '', '', '', '', '侧装', '三星LTPO OLED（4K）', '5/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 5 IV', '6.1英寸', '1080 x 2520', '直屏无孔', '120Hz', '', '', '', '', '侧装', '三星OLED（120Hz）', '9/1/22', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 10 v', '6.1英寸', '1080 x 2520', '直屏无孔', '60Hz', '', '', '', '', '侧装', '天马OLED（推测）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 1 v', '6.5英寸', '1644 x 3840', '直屏无孔', '120Hz', '', '', '', '', '侧装', '三星OLED（4K）', '5/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 5 v', '6.1英寸', '1080 x 2520', '直屏无孔', '120Hz', '', '', '', '', '侧装', '三星OLED（FHD+）', '9/1/23', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 10 VI', '6.1英寸', '1080 x 2520', '直屏无孔', '60Hz', '', '', '', '', '侧装', '京东方OLED（推测）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 1 VI', '6.5英寸', 'LTPO 1080 x 2340', '直屏无孔', '120Hz', '', '', '', '', '侧装', '三星OLED（FHD+）', '5/1/24', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 1 VII', '6.5英寸', 'LTPO 1080 x 2340', '直屏无孔', '120Hz', '', '', '', '', '侧装', 'JDI (Japan Display Inc.)', '5/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
INSERT INTO screen_model (category, brand, model, screen_size, resolution, screen_type, refresh_rate,
        sub_size, sub_resolution, sub_screen_type, sub_refresh_rate,
        fingerprint, panel_supplier, release_date, remark,
        company_id, create_time, update_time) SELECT 'AMOLED', 'SONY', 'Xperia 10 VII', '6.1英寸', '1080 x 2340', '直屏无孔', '120Hz', '', '', '', '', '侧装', '', '9/1/25', '', c.id, NOW(), NOW() FROM sys_company c;
