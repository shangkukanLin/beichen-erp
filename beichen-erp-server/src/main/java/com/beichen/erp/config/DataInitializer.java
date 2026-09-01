package com.beichen.erp.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.DefaultBomTypes;
import com.beichen.erp.common.DefaultContractTemplate;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.entity.PhaseTemplate;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.dev.mapper.PhaseTemplateMapper;
import com.beichen.erp.outsource.entity.ContractTemplate;
import com.beichen.erp.outsource.mapper.ContractTemplateMapper;
import com.beichen.erp.system.entity.Menu;
import com.beichen.erp.system.entity.Role;
import com.beichen.erp.system.entity.UserRole;
import com.beichen.erp.system.mapper.MenuMapper;
import com.beichen.erp.system.mapper.RoleMapper;
import com.beichen.erp.system.mapper.UserRoleMapper;
import com.beichen.erp.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据初始化器：启动时自动初始化系统基础数据（角色、菜单、用户、BOM类型、阶段模板）
 * 表结构由 schema.sql 统一管理，本类仅负责业务初始化数据的写入
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final MenuMapper menuMapper;
    private final RoleService roleService;
    private final BomTypeMapper bomTypeMapper;
    private final PhaseTemplateMapper phaseTemplateMapper;
    private final ContractTemplateMapper contractTemplateMapper;
    private final JdbcTemplate jdbcTemplate;
    private final com.beichen.erp.warehouse.service.CostService costService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        // 清空所有业务数据（保留表结构），仅当启动参数含 --clear-data 时执行
        if (args.containsOption("clear-data")) {
            clearAllData();
            log.info("===== 数据已清空，仅保留表结构 =====");
        }
        initCompany();
        initRoles();
        syncMenus();
        initRoleMenus();
        initSuperAdmin();
        initBomTypes();
        initPhaseTemplates();
        initContractTemplates();
        initSchemaColumns();
    }

    /** 幂等初始化默认合同模板：加工合同、采购合同各建一条默认模板（无默认模板时才插入） */
    private void initContractTemplates() {
        initContractTemplate(DefaultContractTemplate.TYPE_PROCESSING, DefaultContractTemplate.NAME_PROCESSING, DefaultContractTemplate.PROCESSING_CONTRACT_HTML);
        initContractTemplate(DefaultContractTemplate.TYPE_PURCHASE, DefaultContractTemplate.NAME_PURCHASE, DefaultContractTemplate.PURCHASE_CONTRACT_HTML);
    }

    private void initContractTemplate(String type, String name, String content) {
        // 清理 company_id 为 NULL 的历史脏数据（早期初始化遗漏 companyId 导致）
        contractTemplateMapper.delete(new LambdaQueryWrapper<ContractTemplate>()
                .eq(ContractTemplate::getTemplateType, type)
                .isNull(ContractTemplate::getCompanyId));
        Long count = contractTemplateMapper.selectCount(new LambdaQueryWrapper<ContractTemplate>()
                .eq(ContractTemplate::getTemplateType, type)
                .eq(ContractTemplate::getCompanyId, 1L));
        if (count != null && count > 0) {
            // 已存在默认模板：若 content 仍是旧版含占位符的内容，重置为新的纯条款内容
            resetLegacyTemplate(type, content);
            return;
        }
        ContractTemplate tpl = new ContractTemplate();
        tpl.setTemplateName(name);
        tpl.setContent(content);
        tpl.setTemplateType(type);
        tpl.setStatus(1);
        tpl.setIsDefault(1);
        tpl.setCompanyId(1L);
        contractTemplateMapper.insert(tpl);
        log.info("===== 已初始化默认合同模板：{} =====", type);
    }

    /** 旧版模板 content 含占位符（如 {产品表格}/{签名区}），导出改为固定结构后需重置为纯条款内容 */
    private void resetLegacyTemplate(String type, String content) {
        ContractTemplate existing = contractTemplateMapper.selectOne(new LambdaQueryWrapper<ContractTemplate>()
                .eq(ContractTemplate::getTemplateType, type)
                .eq(ContractTemplate::getCompanyId, 1L)
                .eq(ContractTemplate::getIsDefault, 1)
                .last("LIMIT 1"));
        if (existing == null || existing.getContent() == null) return;
        // 仅当 content 含旧占位符时才重置，避免覆盖用户已自行编辑的条款
        if (existing.getContent().contains("{产品表格}") || existing.getContent().contains("{物料明细表格}")
                || existing.getContent().contains("{签名区}") || existing.getContent().contains("{合同信息}")) {
            ContractTemplate upd = new ContractTemplate();
            upd.setId(existing.getId());
            upd.setContent(content);
            contractTemplateMapper.updateById(upd);
            log.info("===== 已重置旧版默认合同模板内容：{} =====", type);
        }
    }

    /** 幂等补列：为存量库平滑升级（schema.sql 的 CREATE TABLE IF NOT EXISTS 不会给已存在表加列） */
    private void initSchemaColumns() {
        // 研发项目品牌（brand.id）
        addColumnIfAbsent("dev_project", "brand_id",
                "ALTER TABLE dev_project ADD COLUMN brand_id BIGINT DEFAULT NULL COMMENT '品牌ID(brand.id)' AFTER product_id");
        // BOM类型默认标记（默认类型不可删除）
        addColumnIfAbsent("dev_bom_type", "is_default",
                "ALTER TABLE dev_bom_type ADD COLUMN is_default TINYINT DEFAULT 0 COMMENT '1默认类型(不可删除) 0自定义' AFTER status");
        // 研发项目 LCD 规格/改配字段（与 schema.sql 对齐，存量库补列，addColumnIfAbsent 幂等）
        addColumnIfAbsent("dev_project", "display_supplier_name",
                "ALTER TABLE dev_project ADD COLUMN display_supplier_name VARCHAR(100) COMMENT '显示方案供应商'");
        addColumnIfAbsent("dev_project", "touch_supplier_name",
                "ALTER TABLE dev_project ADD COLUMN touch_supplier_name VARCHAR(100) COMMENT '触摸方案供应商'");
        addColumnIfAbsent("dev_project", "adapt_model",
                "ALTER TABLE dev_project ADD COLUMN adapt_model VARCHAR(100) COMMENT '适配机型'");
        addColumnIfAbsent("dev_project", "original_size",
                "ALTER TABLE dev_project ADD COLUMN original_size VARCHAR(50) COMMENT '原始尺寸'");
        addColumnIfAbsent("dev_project", "original_resolution",
                "ALTER TABLE dev_project ADD COLUMN original_resolution VARCHAR(50) COMMENT '原始分辨率'");
        addColumnIfAbsent("dev_project", "original_drive_ic",
                "ALTER TABLE dev_project ADD COLUMN original_drive_ic VARCHAR(100) COMMENT '原机驱动IC型号'");
        addColumnIfAbsent("dev_project", "original_touch_ic",
                "ALTER TABLE dev_project ADD COLUMN original_touch_ic VARCHAR(100) COMMENT '原机触摸IC型号'");
        addColumnIfAbsent("dev_project", "glass_size",
                "ALTER TABLE dev_project ADD COLUMN glass_size VARCHAR(50) COMMENT '玻璃尺寸'");
        addColumnIfAbsent("dev_project", "glass_resolution",
                "ALTER TABLE dev_project ADD COLUMN glass_resolution VARCHAR(50) COMMENT '玻璃分辨率'");
        addColumnIfAbsent("dev_project", "config_drive_ic_id",
                "ALTER TABLE dev_project ADD COLUMN config_drive_ic_id BIGINT DEFAULT NULL COMMENT '改配驱动IC物料ID(outsource_material.id)'");
        addColumnIfAbsent("dev_project", "config_touch_ic_id",
                "ALTER TABLE dev_project ADD COLUMN config_touch_ic_id BIGINT DEFAULT NULL COMMENT '改配触摸IC物料ID(outsource_material.id)'");
        addColumnIfAbsent("dev_project", "config_code_ic_id",
                "ALTER TABLE dev_project ADD COLUMN config_code_ic_id BIGINT DEFAULT NULL COMMENT '改配码片IC物料ID(outsource_material.id)'");
        // 加工单供料模式（来料加工/包工包料）
        addColumnIfAbsent("outsource_order", "supply_mode",
                "ALTER TABLE outsource_order ADD COLUMN supply_mode VARCHAR(20) DEFAULT 'OURS' COMMENT '供料模式:OURS来料加工 FACTORY包工包料' AFTER status");
        // 加工单物料供料方（我方供/工厂包）
        addColumnIfAbsent("outsource_order_material", "supply_type",
                "ALTER TABLE outsource_order_material ADD COLUMN supply_type VARCHAR(20) DEFAULT 'OURS' COMMENT '供料方:OURS我方供 FACTORY工厂包' AFTER loss_rate");
        // 外协物料单价（价格字段）
        addColumnIfAbsent("outsource_material", "price",
                "ALTER TABLE outsource_material ADD COLUMN price DECIMAL(18,2) DEFAULT 0 COMMENT '单价' AFTER status");
        addColumnIfAbsent("finance_receivable", "source_id",
                "ALTER TABLE finance_receivable ADD COLUMN source_id BIGINT DEFAULT NULL COMMENT '来源记录ID'");
        // 废弃余额快照字段：余额改由台账实时 SUM 汇总，物理删除冗余快照列
        dropColumnIfExists("supplier", "payable_balance");
        dropColumnIfExists("customer", "receivable_balance");
        dropColumnIfExists("customer", "prepaid_balance");
        // 业务单据冗余名字快照列：改为存 ID 查询时 JOIN 查名（财务单据与库存流水留痕列保留）
        dropColumnIfExists("purchase_order", "supplier_name");
        dropColumnIfExists("sale_order", "customer_name");
        dropColumnIfExists("sale_outbound", "customer_name");
        dropColumnIfExists("sale_return", "customer_name");
        dropColumnIfExists("sale_return_item", "product_name");
        dropColumnIfExists("inventory_stock_reclass_item", "product_name");
        // 账户余额实时算：删余额快照列，加期初余额列（期初余额落流水，余额由流水实时累计）
        addColumnIfAbsent("finance_account", "opening_balance",
                "ALTER TABLE finance_account ADD COLUMN opening_balance DECIMAL(18,4) DEFAULT 0 COMMENT '期初余额(开户时初始资金，之后不可变)' AFTER account_no");
        dropColumnIfExists("finance_account", "balance");
        dropColumnIfExists("finance_cashflow", "balance");
        // 账单明细补 source_id（来源台账ID，核销时反向联动账单进度）
        addColumnIfAbsent("finance_bill_item", "source_id",
                "ALTER TABLE finance_bill_item ADD COLUMN source_id BIGINT DEFAULT NULL COMMENT '来源台账ID(应付/应收台账主键，核销联动用)' AFTER source_bill_no");
        // 库存流水 change_type 扩长：StockChangeType 枚举名超 20 字符（如 OUTSOURCE_CANCEL_DELIVERY=24），原 varchar(20) 会 Data truncation
        modifyColumn("warehouse_stock_log", "change_type",
                "ALTER TABLE warehouse_stock_log MODIFY COLUMN change_type VARCHAR(50) NOT NULL COMMENT '变动类型'");
        // 结单报表物料明细：缺失改手动填写，新增 missing_qty 列
        addColumnIfAbsent("outsource_order_close_report_item", "missing_qty",
                "ALTER TABLE outsource_order_close_report_item ADD COLUMN missing_qty DECIMAL(18,4) DEFAULT NULL COMMENT '缺失(手动填写)' AFTER factory_retain_qty");
        // 委外成品库存产品ID语义修正：加工单产品明细关联产品主数据，交货库存落主表ID
        addColumnIfAbsent("outsource_order_product", "product_id",
                "ALTER TABLE outsource_order_product ADD COLUMN product_id BIGINT DEFAULT NULL COMMENT '关联产品主数据ID(product.id)' AFTER project_id");
        addColumnIfAbsent("outsource_order_delivery", "product_master_id",
                "ALTER TABLE outsource_order_delivery ADD COLUMN product_master_id BIGINT DEFAULT NULL COMMENT '关联产品主数据ID(product.id)' AFTER product_id");
        addColumnIfAbsent("outsource_order_delivery", "quality_type",
                "ALTER TABLE outsource_order_delivery ADD COLUMN quality_type VARCHAR(20) DEFAULT NULL COMMENT '退不良规格(A/B/C/DEFECT)' AFTER product_master_id");
        // delivery_type 历史列宽10放不下 DEFECT_RETURN(13字符)，统一扩到20
        modifyColumn("outsource_order_delivery", "delivery_type",
                "ALTER TABLE outsource_order_delivery MODIFY COLUMN delivery_type VARCHAR(20) DEFAULT '正常' COMMENT '正常/退不良'");
        // 委外加工退货状态机改造：草稿-审核-取消审核，加审计字段
        addColumnIfAbsent("outsource_return_order", "auditor_id",
                "ALTER TABLE outsource_return_order ADD COLUMN auditor_id BIGINT DEFAULT NULL COMMENT '审核人ID' AFTER status");
        addColumnIfAbsent("outsource_return_order", "auditor_name",
                "ALTER TABLE outsource_return_order ADD COLUMN auditor_name VARCHAR(50) DEFAULT NULL COMMENT '审核人姓名' AFTER auditor_id");
        addColumnIfAbsent("outsource_return_order", "audit_time",
                "ALTER TABLE outsource_return_order ADD COLUMN audit_time DATETIME DEFAULT NULL COMMENT '审核时间' AFTER auditor_name");
        // 销售单审核时间（财务分析利润表按审核时间归月；存量单据为 NULL 时分析端按 createTime 兜底）
        addColumnIfAbsent("sale_order", "audit_time",
                "ALTER TABLE sale_order ADD COLUMN audit_time DATETIME DEFAULT NULL COMMENT '审核时间' AFTER status");
        // 订单税额列（单价含税口径：打开收税后从含税总额中按税率拆出税额）
        addColumnIfAbsent("sale_order", "tax_amount",
                "ALTER TABLE sale_order ADD COLUMN tax_amount DECIMAL(18,4) DEFAULT 0 COMMENT '税额' AFTER tax_rate");
        addColumnIfAbsent("purchase_order", "tax_amount",
                "ALTER TABLE purchase_order ADD COLUMN tax_amount DECIMAL(18,4) DEFAULT 0 COMMENT '税额' AFTER tax_rate");
        addColumnIfAbsent("outsource_order", "tax_amount",
                "ALTER TABLE outsource_order ADD COLUMN tax_amount DECIMAL(18,4) DEFAULT 0 COMMENT '税额' AFTER tax_rate");
        // 存量已审核销售单回填审核时间（无记录审核先后，用建单时间兜底，幂等：只补 NULL）
        try {
            jdbcTemplate.update("UPDATE sale_order SET audit_time = create_time WHERE status = 'AUDITED' AND audit_time IS NULL");
        } catch (Exception e) {
            log.warn("回填销售单审核时间失败: {}", e.getMessage());
        }
        // 费用登记表（存量库补建表，schema.sql 同步维护；CREATE TABLE IF NOT EXISTS 幂等）
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS finance_expense (\n" +
                "    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '费用单ID',\n" +
                "    expense_no VARCHAR(50) NOT NULL COMMENT '费用单号',\n" +
                "    expense_type VARCHAR(50) COMMENT '费用类型',\n" +
                "    amount DECIMAL(18,4) NOT NULL COMMENT '费用金额',\n" +
                "    expense_date DATE COMMENT '费用日期（利润表按此归月）',\n" +
                "    account_id BIGINT COMMENT '支出账户ID',\n" +
                "    account_name VARCHAR(100) COMMENT '支出账户名称',\n" +
                "    remark VARCHAR(500) COMMENT '备注',\n" +
                "    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT/AUDITED/CANCELLED',\n" +
                "    company_id BIGINT COMMENT '公司ID',\n" +
                "    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "    INDEX idx_expense_type (expense_type),\n" +
                "    INDEX idx_expense_date (expense_date),\n" +
                "    INDEX idx_status (status),\n" +
                "    INDEX idx_company_id (company_id)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用登记表'");
        // 移动加权平均成本：产品/物料成本列 + 入库批次记录表（存量回填见 backfillCostPrice）
        addColumnIfAbsent("product", "cost_price",
                "ALTER TABLE product ADD COLUMN cost_price DECIMAL(18,4) DEFAULT NULL COMMENT '移动加权平均成本价' AFTER safety_stock");
        addColumnIfAbsent("product", "cost_manual",
                "ALTER TABLE product ADD COLUMN cost_manual TINYINT DEFAULT 0 COMMENT '成本价是否手工锁定 0否 1是' AFTER cost_price");
        addColumnIfAbsent("product", "last_in_price",
                "ALTER TABLE product ADD COLUMN last_in_price DECIMAL(18,4) DEFAULT NULL COMMENT '最近入库单价' AFTER cost_manual");
        addColumnIfAbsent("outsource_material", "cost_price",
                "ALTER TABLE outsource_material ADD COLUMN cost_price DECIMAL(18,4) DEFAULT NULL COMMENT '移动加权平均成本价' AFTER price");
        addColumnIfAbsent("outsource_material", "cost_manual",
                "ALTER TABLE outsource_material ADD COLUMN cost_manual TINYINT DEFAULT 0 COMMENT '成本价是否手工锁定 0否 1是' AFTER cost_price");
        addColumnIfAbsent("outsource_material", "last_in_price",
                "ALTER TABLE outsource_material ADD COLUMN last_in_price DECIMAL(18,4) DEFAULT NULL COMMENT '最近入库单价' AFTER cost_manual");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS cost_inbound_log (\n" +
                "    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',\n" +
                "    target_type VARCHAR(20) NOT NULL COMMENT '成本对象类型: PRODUCT/MATERIAL',\n" +
                "    target_id BIGINT NOT NULL COMMENT '成本对象ID',\n" +
                "    change_type VARCHAR(50) COMMENT '库存变动类型',\n" +
                "    related_bill_id BIGINT COMMENT '关联单据ID(反审核冲销依据)',\n" +
                "    related_bill_no VARCHAR(50) COMMENT '关联单号',\n" +
                "    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '入库数量',\n" +
                "    unit_cost DECIMAL(18,4) DEFAULT 0 COMMENT '入库单价',\n" +
                "    total_cost DECIMAL(18,4) DEFAULT 0 COMMENT '入库总成本',\n" +
                "    cost_after DECIMAL(18,4) COMMENT '入库后加权成本快照',\n" +
                "    company_id BIGINT COMMENT '公司ID',\n" +
                "    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "    INDEX idx_target (target_type, target_id),\n" +
                "    INDEX idx_bill (change_type, related_bill_id),\n" +
                "    INDEX idx_company_id (company_id)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库批次成本记录表'");
        backfillCostPrice();
        // 发票登记表（销项/进项，税务口径；存量库幂等补建）
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS finance_invoice (\n" +
                "    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '发票ID',\n" +
                "    invoice_no VARCHAR(50) NOT NULL COMMENT '发票号码',\n" +
                "    direction VARCHAR(20) NOT NULL COMMENT '方向: SALE=销项 PURCHASE=进项',\n" +
                "    invoice_kind VARCHAR(30) COMMENT '发票类型',\n" +
                "    invoice_date DATE COMMENT '开票日期',\n" +
                "    partner_name VARCHAR(100) COMMENT '对方单位(销项=购买方,进项=销售方)',\n" +
                "    amount DECIMAL(18,4) DEFAULT 0 COMMENT '不含税金额',\n" +
                "    tax_rate DECIMAL(18,4) DEFAULT 0 COMMENT '税率(%)',\n" +
                "    tax_amount DECIMAL(18,4) DEFAULT 0 COMMENT '税额',\n" +
                "    total_amount DECIMAL(18,4) DEFAULT 0 COMMENT '价税合计',\n" +
                "    source_bill_code VARCHAR(50) COMMENT '关联业务单号(可选)',\n" +
                "    remark VARCHAR(500) COMMENT '备注',\n" +
                "    status VARCHAR(20) DEFAULT 'REGISTERED' COMMENT '状态: REGISTERED=已登记 CANCELLED=已作废',\n" +
                "    company_id BIGINT COMMENT '公司ID',\n" +
                "    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "    INDEX idx_invoice_no (invoice_no),\n" +
                "    INDEX idx_direction (direction),\n" +
                "    INDEX idx_invoice_date (invoice_date),\n" +
                "    INDEX idx_status (status),\n" +
                "    INDEX idx_company_id (company_id)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票登记表'");
        // 销售退货关联销售单（追溯）
        addColumnIfAbsent("sale_return", "sale_order_id",
                "ALTER TABLE sale_return ADD COLUMN sale_order_id BIGINT DEFAULT NULL COMMENT '关联销售单ID(sale_order.id)' AFTER warehouse_id");
        addColumnIfAbsent("sale_return", "sale_order_code",
                "ALTER TABLE sale_return ADD COLUMN sale_order_code VARCHAR(30) DEFAULT NULL COMMENT '关联销售单号' AFTER sale_order_id");
        addColumnIfAbsent("sale_return_item", "sale_order_item_id",
                "ALTER TABLE sale_return_item ADD COLUMN sale_order_item_id BIGINT DEFAULT NULL COMMENT '关联销售单明细ID(sale_order_item.id)' AFTER return_id");
        // 采购退货关联采购单（追溯）
        addColumnIfAbsent("purchase_return", "purchase_order_id",
                "ALTER TABLE purchase_return ADD COLUMN purchase_order_id BIGINT DEFAULT NULL COMMENT '关联采购单ID(purchase_order.id)' AFTER warehouse_id");
        addColumnIfAbsent("purchase_return", "purchase_order_code",
                "ALTER TABLE purchase_return ADD COLUMN purchase_order_code VARCHAR(30) DEFAULT NULL COMMENT '关联采购单号' AFTER purchase_order_id");
        addColumnIfAbsent("purchase_return_item", "purchase_order_item_id",
                "ALTER TABLE purchase_return_item ADD COLUMN purchase_order_item_id BIGINT DEFAULT NULL COMMENT '关联采购单明细ID(purchase_order_item.id)' AFTER return_id");
        // 退货整理追溯销售退货单：退货明细记录已整理数量，整理明细记录来源退货明细
        addColumnIfAbsent("sale_return_item", "sorted_quantity",
                "ALTER TABLE sale_return_item ADD COLUMN sorted_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '已整理数量(退货整理单审核后累加，反审核扣回)' AFTER quantity");
        addColumnIfAbsent("return_sort_item", "sale_return_item_id",
                "ALTER TABLE return_sort_item ADD COLUMN sale_return_item_id BIGINT DEFAULT NULL COMMENT '来源销售退货明细ID(sale_return_item.id)，用于追溯' AFTER product_id");
        // 销售退货折损收款：整理后 B/C/不良 的折损，金额由用户填写，审核时生成正向应收
        addColumnIfAbsent("sale_return", "loss_amount",
                "ALTER TABLE sale_return ADD COLUMN loss_amount DECIMAL(18,2) DEFAULT 0 COMMENT '折损收款金额(整理后B/C/不良的折损，向客户收取)' AFTER total_amount");
        // 销售退货收费：与换货单一致（chargeFlag 控制，金额手工填写，审核后生成 -FEE 正向应收）
        addColumnIfAbsent("sale_return", "charge_flag",
                "ALTER TABLE sale_return ADD COLUMN charge_flag TINYINT DEFAULT 0 COMMENT '是否收费: 0否 1是' AFTER loss_amount");
        addColumnIfAbsent("sale_return", "charge_type",
                "ALTER TABLE sale_return ADD COLUMN charge_type VARCHAR(20) DEFAULT NULL COMMENT '收费类型: SERVICE服务费/DIFF品质差价/FULL全额货值/OTHER其他' AFTER charge_flag");
        addColumnIfAbsent("sale_return", "charge_amount",
                "ALTER TABLE sale_return ADD COLUMN charge_amount DECIMAL(18,2) DEFAULT 0 COMMENT '收费金额(手工填写，审核后生成正向应收，单号后缀 -FEE)' AFTER charge_type");
        addColumnIfAbsent("sale_return", "charge_reason",
                "ALTER TABLE sale_return ADD COLUMN charge_reason VARCHAR(200) COMMENT '收费说明(原因备注)' AFTER charge_amount");
        // ==================== 售后改造：销售退单 + 销售换货单(可收费) + 统一待整理批次 ====================
        // 销售换货单收费：是否收费 + 收费类型/金额/说明（审核后生成正向应收，单号后缀 -FEE）
        addColumnIfAbsent("sale_exchange", "total_amount",
                "ALTER TABLE sale_exchange ADD COLUMN total_amount DECIMAL(18,2) DEFAULT 0 COMMENT '换出货值合计(Σ数量×单价，仅展示)' AFTER status");
        addColumnIfAbsent("sale_exchange", "charge_flag",
                "ALTER TABLE sale_exchange ADD COLUMN charge_flag TINYINT DEFAULT 0 COMMENT '是否收费: 0否 1是' AFTER total_amount");
        addColumnIfAbsent("sale_exchange", "charge_type",
                "ALTER TABLE sale_exchange ADD COLUMN charge_type VARCHAR(20) DEFAULT NULL COMMENT '收费类型: SERVICE服务费/DIFF品质差价/FULL全额货值/OTHER其他' AFTER charge_flag");
        addColumnIfAbsent("sale_exchange", "charge_amount",
                "ALTER TABLE sale_exchange ADD COLUMN charge_amount DECIMAL(18,2) DEFAULT 0 COMMENT '收费金额(手工填写，审核后生成正向应收)' AFTER charge_type");
        addColumnIfAbsent("sale_exchange", "charge_reason",
                "ALTER TABLE sale_exchange ADD COLUMN charge_reason VARCHAR(200) COMMENT '收费说明(原因备注)' AFTER charge_amount");
        addColumnIfAbsent("sale_exchange_item", "amount",
                "ALTER TABLE sale_exchange_item ADD COLUMN amount DECIMAL(18,2) DEFAULT 0 COMMENT '换出金额(数量×单价，仅展示)' AFTER unit_price");
        // 折损收款由销售退单迁移到退货整理单：整理后才知道 B/C/不良 各多少，金额应在整理环节确定
        addColumnIfAbsent("return_sort", "loss_amount",
                "ALTER TABLE return_sort ADD COLUMN loss_amount DECIMAL(18,2) DEFAULT 0 COMMENT '折损收款金额(整理后B/C/不良品的折损，向客户收取，审核后生成正向应收)' AFTER status");
        addColumnIfAbsent("return_sort", "loss_remark",
                "ALTER TABLE return_sort ADD COLUMN loss_remark VARCHAR(200) COMMENT '折损收款说明' AFTER loss_amount");
        // 整理明细追溯锚点改为统一的售后待整理批次（退单/换货共用），sale_return_item_id 保留兼容但不再写入
        addColumnIfAbsent("return_sort_item", "pending_id",
                "ALTER TABLE return_sort_item ADD COLUMN pending_id BIGINT DEFAULT NULL COMMENT '来源售后待整理批次ID(after_sale_pending.id)' AFTER product_id");
        addColumnIfAbsent("after_sale_pending", "source_date",
                "ALTER TABLE after_sale_pending ADD COLUMN source_date DATE DEFAULT NULL COMMENT '来源单据业务日期(退单的退货日期/换货的换货日期)' AFTER source_code");
        // 销售换货单换出侧：只支持同品换货（换出产品固定为退回产品），保留数量/单价/品质字段
        addColumnIfAbsent("sale_exchange_item", "out_quantity",
                "ALTER TABLE sale_exchange_item ADD COLUMN out_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '换出数量(可与退回数量不等，如退2换1)' AFTER amount");
        addColumnIfAbsent("sale_exchange_item", "out_unit_price",
                "ALTER TABLE sale_exchange_item ADD COLUMN out_unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '换出单价(默认取原销售单价，可手工改)' AFTER out_quantity");
        addColumnIfAbsent("sale_exchange_item", "out_amount",
                "ALTER TABLE sale_exchange_item ADD COLUMN out_amount DECIMAL(18,2) DEFAULT 0 COMMENT '换出金额(换出数量×换出单价)' AFTER out_unit_price");
        addColumnIfAbsent("sale_exchange_item", "out_quality_type",
                "ALTER TABLE sale_exchange_item ADD COLUMN out_quality_type VARCHAR(10) DEFAULT 'A' COMMENT '换出品质: A/B/C/DEFECT' AFTER out_amount");
        // 旧 quality_type 语义即「换出品质」，已由 out_quality_type 取代；保留会造成两个品质字段混淆
        dropColumnIfExists("sale_exchange_item", "quality_type");
        // 单据状态列类型统一：历史库 status 为 int(0/1/2)，代码层统一为字符串 DRAFT/AUDITED/CANCELLED，幂等迁移
        for (String t : new String[]{
                "purchase_order", "purchase_return", "purchase_inbound",
                "sale_order", "sale_outbound", "sale_return",
                "inventory_warehouse_move", "inventory_other_io",
                "finance_receipt", "finance_payment"}) {
            migrateStatusToVarchar(t);
        }
        // 收费售后(sourceType=AFTER_SALE)不关联加工单：order_id 必须可空，否则插入报
        // "Field 'order_id' doesn't have a default value"（@Transactional 下整单回滚，库存也不入库）
        makeColumnNullable("outsource_order_delivery", "order_id",
                "ALTER TABLE outsource_order_delivery MODIFY COLUMN order_id BIGINT DEFAULT NULL COMMENT '订单ID(收费售后来源不关联加工单，可为空)'");
        // 产品 SKU（产品级唯一编码，必填）：先补列 → 存量补号 → 再建唯一索引（否则存量重复/空值会建索引失败）
        addColumnIfAbsent("product", "sku",
                "ALTER TABLE product ADD COLUMN sku VARCHAR(64) DEFAULT NULL COMMENT 'SKU编码(产品级唯一，新增留空自动生成)' AFTER name");
        backfillProductSku();
        addIndexIfAbsent("product", "uk_company_sku",
                "ALTER TABLE product ADD UNIQUE INDEX uk_company_sku (company_id, sku)");
        // 产品品质等级「未知(UNKNOWN)」更名为「待分类(PENDING)」：历史数据幂等迁移
        migrateQualityTypeUnknownToPending();
    }

    /**
     * 存量产品补 SKU：按 id 升序分配 SKU-000001…（起始号取已有最大流水 +1，避免与手工编码撞号）。
     * 幂等：已存在 SKU 的记录不参与，重复执行不会改号。
     */
    private void backfillProductSku() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id FROM product WHERE sku IS NULL OR sku = '' ORDER BY id");
            if (rows.isEmpty()) return;
            Integer maxSeq = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(CAST(SUBSTRING(sku, 5) AS UNSIGNED)), 0) FROM product WHERE sku LIKE 'SKU-%'",
                    Integer.class);
            int seq = (maxSeq == null ? 0 : maxSeq) + 1;
            for (Map<String, Object> r : rows) {
                jdbcTemplate.update("UPDATE product SET sku = ? WHERE id = ?",
                        String.format("SKU-%06d", seq++), r.get("id"));
            }
            log.info("已为 {} 个存量产品补生成 SKU", rows.size());
        } catch (Exception e) {
            log.warn("回填产品 SKU 失败: {}", e.getMessage());
        }
    }

    /**
     * 产品品质等级 UNKNOWN(未知) → PENDING(待分类) 幂等迁移。
     * 覆盖所有存储 ProductQualityType 编码的表；品质重分类单使用的是 from_quality/to_quality 列名，单独处理。
     */
    private void migrateQualityTypeUnknownToPending() {
        String[] tables = {
                "warehouse_stock", "warehouse_stock_log",
                "sale_return_item", "sale_order_item", "sale_outbound_item",
                "purchase_order_item", "purchase_return_item",
                "inventory_warehouse_move_item", "inventory_other_io_item"
        };
        for (String t : tables) {
            try {
                int rows = jdbcTemplate.update("UPDATE " + t + " SET quality_type = 'PENDING' WHERE quality_type = 'UNKNOWN'");
                if (rows > 0) log.info("已将 {}.quality_type 由 UNKNOWN 迁移为 PENDING，共 {} 行", t, rows);
            } catch (Exception e) {
                log.warn("迁移 {}.quality_type 失败: {}", t, e.getMessage());
            }
        }
        for (String col : new String[]{"from_quality", "to_quality"}) {
            try {
                int rows = jdbcTemplate.update("UPDATE inventory_stock_reclass_item SET " + col + " = 'PENDING' WHERE " + col + " = 'UNKNOWN'");
                if (rows > 0) log.info("已将 inventory_stock_reclass_item.{} 由 UNKNOWN 迁移为 PENDING，共 {} 行", col, rows);
            } catch (Exception e) {
                log.warn("迁移 inventory_stock_reclass_item.{} 失败: {}", col, e.getMessage());
            }
        }
    }

    /** 单据状态列 int→varchar 幂等迁移：历史库 status 是 TINYINT(0/1/2)，新版统一为 DRAFT/AUDITED/CANCELLED 字符串 */
    private void migrateStatusToVarchar(String table) {
        try {
            Integer numeric = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'status' AND DATA_TYPE IN ('tinyint','smallint','mediumint','int','integer','bigint')",
                    Integer.class, table);
            if (numeric == null || numeric == 0) return; // 已是字符串或列不存在
            int rows = jdbcTemplate.update(
                    "UPDATE " + table + " SET status = CASE status WHEN 0 THEN 'DRAFT' WHEN 1 THEN 'AUDITED' WHEN 2 THEN 'CANCELLED' ELSE 'DRAFT' END");
            jdbcTemplate.execute("ALTER TABLE " + table + " MODIFY COLUMN status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT=草稿 AUDITED=已审核 CANCELLED=已作废'");
            log.info("已将 {}.status 从 int 迁移为 varchar，共转换 {} 行", table, rows);
        } catch (Exception e) {
            log.warn("迁移 {}.status 失败: {}", table, e.getMessage());
        }
    }

    /** 幂等扩长/修改列：当列长度不足时执行 ALTER MODIFY（用于枚举 code 超长的平滑升级） */
    private void modifyColumn(String table, String column, String alterSql) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                    Integer.class, table, column);
            if (cnt != null && cnt > 0) {
                jdbcTemplate.execute(alterSql);
                log.info("已修改列 {}.{}", table, column);
            }
        } catch (Exception e) {
            log.warn("修改列 {}.{} 失败: {}", table, column, e.getMessage());
        }
    }

    /** 判断列是否存在，不存在则执行 ALTER 补列 */
    private void addColumnIfAbsent(String table, String column, String alterSql) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                    Integer.class, table, column);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute(alterSql);
                log.info("已补充列 {}.{}", table, column);
            }
        } catch (Exception e) {
            log.warn("补充列 {}.{} 失败: {}", table, column, e.getMessage());
        }
    }

    /** 判断列是否存在，存在则执行 ALTER 删列（用于废弃冗余快照字段的平滑下线） */
    private void dropColumnIfExists(String table, String column) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                    Integer.class, table, column);
            if (cnt != null && cnt > 0) {
                jdbcTemplate.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
                log.info("已删除冗余列 {}.{}", table, column);
            }
        } catch (Exception e) {
            log.warn("删除列 {}.{} 失败: {}", table, column, e.getMessage());
        }
    }

    /** 列当前为 NOT NULL 时才改为可空（避免每次启动都执行 DDL） */
    private void makeColumnNullable(String table, String column, String alterSql) {
        try {
            Integer notNull = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema = DATABASE()"
                            + " AND table_name = ? AND column_name = ? AND is_nullable = 'NO'",
                    Integer.class, table, column);
            if (notNull != null && notNull > 0) {
                jdbcTemplate.execute(alterSql);
                log.info("已将列 {}.{} 改为可空", table, column);
            }
        } catch (Exception e) {
            log.warn("修改列 {}.{} 为可空失败: {}", table, column, e.getMessage());
        }
    }

    /**
     * 存量成本回填（幂等：cost_inbound_log 已有记录则整体跳过）。
     * 顺序：先物料类入库（其他出入库 IN/物料收货/委外发料），再采购入库，最后委外交货
     * （交货依赖物料成本；材料成本按当前物料加权成本估算，包工包料为 0）。
     */
    private void backfillCostPrice() {
        try {
            Long done = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cost_inbound_log", Long.class);
            if (done != null && done > 0) return;
            int n = 0;
            // 1) 委外物料：其他出入库 IN
            for (Map<String, Object> r : jdbcTemplate.queryForList(
                    "SELECT io.id AS bill_id, io.code AS bill_no, io.company_id, it.material_id, it.quantity, it.unit_price " +
                    "FROM outsource_other_io io JOIN outsource_other_io_item it ON it.other_io_id = io.id " +
                    "WHERE io.status = 'AUDITED' AND io.io_type = 'IN' ORDER BY io.id, it.id")) {
                n += replayMaterial(r, "OTHER_IN");
            }
            // 2) 委外物料：物料订单收货单
            for (Map<String, Object> r : jdbcTemplate.queryForList(
                    "SELECT d.id AS bill_id, d.code AS bill_no, d.company_id, it.material_id, it.quantity, it.unit_price " +
                    "FROM outsource_delivery d JOIN outsource_delivery_item it ON it.delivery_id = d.id " +
                    "WHERE d.status = 'AUDITED' AND d.delivery_type = 'RECEIVE' ORDER BY d.id, it.id")) {
                n += replayMaterial(r, "RECEIVE_IN");
            }
            // 3) 委外物料：委外收发单发料
            for (Map<String, Object> r : jdbcTemplate.queryForList(
                    "SELECT d.id AS bill_id, d.code AS bill_no, d.company_id, it.material_id, it.quantity, it.unit_price " +
                    "FROM outsource_delivery d JOIN outsource_delivery_item it ON it.delivery_id = d.id " +
                    "WHERE d.status = 'AUDITED' AND d.delivery_type = 'DELIVERY' ORDER BY d.id, it.id")) {
                n += replayMaterial(r, "DELIVERY_IN");
            }
            // 4) 产品：已审核采购单明细
            for (Map<String, Object> r : jdbcTemplate.queryForList(
                    "SELECT o.id AS bill_id, o.code AS bill_no, o.company_id, it.product_id, it.quantity, it.unit_price " +
                    "FROM purchase_order o JOIN purchase_order_item it ON it.order_id = o.id " +
                    "WHERE o.status = 'AUDITED' ORDER BY o.id, it.id")) {
                Long cid = num(r.get("company_id"));
                try {
                    if (cid != null && cid > 0) CompanyContext.set(cid);
                    costService.applyProduct(num(r.get("product_id")), toBd(r.get("quantity")), toBd(r.get("unit_price")),
                            "PURCHASE_IN", num(r.get("bill_id")), (String) r.get("bill_no"));
                    n++;
                } finally {
                    CompanyContext.clear();
                }
            }
            // 5) 产品：已审核委外交货（单价 = (加工费+材料成本估算) ÷ 数量）
            for (Map<String, Object> r : jdbcTemplate.queryForList(
                    "SELECT d.id AS bill_id, d.company_id, d.quantity, p.product_master_id, p.unit_price, d.order_id " +
                    "FROM outsource_order_delivery d JOIN outsource_order_product p ON p.id = d.product_id " +
                    "WHERE d.status = 'AUDITED' AND IFNULL(d.is_reverse, 0) = 0 AND d.warehouse_id IS NOT NULL " +
                    "AND p.product_master_id IS NOT NULL ORDER BY d.id")) {
                java.math.BigDecimal qty = toBd(r.get("quantity"));
                if (qty.compareTo(java.math.BigDecimal.ZERO) <= 0) continue;
                java.math.BigDecimal fee = toBd(r.get("unit_price")).multiply(qty);
                java.math.BigDecimal matCost = estimateMaterialCost(num(r.get("order_id")), qty);
                java.math.BigDecimal unitCost = fee.add(matCost).divide(qty, 4, java.math.RoundingMode.HALF_UP);
                Long cid = num(r.get("company_id"));
                try {
                    if (cid != null && cid > 0) CompanyContext.set(cid);
                    costService.applyProduct(num(r.get("product_master_id")), qty, unitCost,
                            "OUTSOURCE_FINISH_IN", num(r.get("bill_id")), null);
                    n++;
                } finally {
                    CompanyContext.clear();
                }
            }
            if (n > 0) log.info("成本回填完成：共 {} 条入库批次", n);
        } catch (Exception e) {
            log.warn("成本回填失败(不影响启动): {}", e.getMessage());
        }
    }

    /** 重放一行物料入库批次（设置公司上下文） */
    private int replayMaterial(Map<String, Object> r, String changeType) {
        Long cid = num(r.get("company_id"));
        try {
            if (cid != null && cid > 0) CompanyContext.set(cid);
            costService.applyMaterial(num(r.get("material_id")), toBd(r.get("quantity")), toBd(r.get("unit_price")),
                    changeType, num(r.get("bill_id")), (String) r.get("bill_no"));
            return 1;
        } catch (Exception e) {
            log.warn("回填单行失败 bill={}: {}", r.get("bill_id"), e.getMessage());
            return 0;
        } finally {
            CompanyContext.clear();
        }
    }

    /** 估算委外交货的材料成本（BOM 需求 × 物料当前加权成本；无法解析时返回 0） */
    private java.math.BigDecimal estimateMaterialCost(Long orderId, java.math.BigDecimal qty) {
        try {
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            for (Map<String, Object> m : jdbcTemplate.queryForList(
                    "SELECT it.material_id, it.demand_quantity FROM outsource_order_material it " +
                    "WHERE it.order_id = ? AND IFNULL(it.supply_type, 'OURS') = 'OURS'", orderId)) {
                Long mid = num(m.get("material_id"));
                if (mid == null) continue;
                java.math.BigDecimal demand = toBd(m.get("demand_quantity")).multiply(qty);
                java.math.BigDecimal cost = jdbcTemplate.queryForObject(
                        "SELECT IFNULL(cost_price, IFNULL(price, 0)) FROM outsource_material WHERE id = ?",
                        java.math.BigDecimal.class, mid);
                total = total.add((cost == null ? java.math.BigDecimal.ZERO : cost).multiply(demand));
            }
            return total;
        } catch (Exception e) {
            return java.math.BigDecimal.ZERO;
        }
    }

    private Long num(Object v) { return v == null ? null : Long.valueOf(v.toString()); }

    private java.math.BigDecimal toBd(Object v) {
        return v == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(v.toString());
    }

    /** 判断索引是否存在，不存在则执行 ALTER 加索引 */
    private void addIndexIfAbsent(String table, String index, String alterSql) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                    Integer.class, table, index);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute(alterSql);
                log.info("已补充索引 {}.{}", table, index);
            }
        } catch (Exception e) {
            log.warn("补充索引 {}.{} 失败: {}", table, index, e.getMessage());
        }
    }

    /** 清空所有业务数据（保留表结构） */
    private void clearAllData() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        String sql = "SELECT CONCAT('DELETE FROM ', table_name, ';') FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'";
        jdbcTemplate.queryForList(sql).forEach(row -> {
            jdbcTemplate.execute(row.values().iterator().next().toString());
        });
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    /** 初始化默认公司：北辰科技 */
    private void initCompany() {
        try {
            Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_company", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.update("INSERT INTO sys_company (company_name, status) VALUES ('北辰科技', 1)");
                log.info("已初始化默认公司：北辰科技");
            }
        } catch (Exception e) {
            log.warn("初始化公司异常: {}", e.getMessage());
        }
    }

    /** 初始化6个角色（管理员/研发工程师/销售专员/仓管员/跟单专员/财务） */
    private void initRoles() {
        jdbcTemplate.update("INSERT IGNORE INTO sys_role (role_name, role_code, status, remark, company_id) VALUES " +
            "('管理员', 'admin', 1, '系统管理员，拥有全部权限', 0), " +
            "('研发工程师', 'dev_engineer', 1, '研发工程师，负责项目研发和BOM管理', 0), " +
            "('销售专员', 'sales', 1, '销售专员，负责销售和客户管理', 0), " +
            "('仓管员', 'warehouse', 1, '仓管员，负责库存和仓库管理', 0), " +
            "('跟单专员', 'merchandiser', 1, '跟单专员，负责委外加工跟进', 0), " +
            "('财务', 'finance', 1, '财务人员，负责应收应付和资金管理', 0)"
        );
        log.info("初始化角色数据完成");
    }

    /** 初始化超级管理员 lin（密码123），关联 admin 角色 */
    private void initSuperAdmin() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = 'lin'", Long.class);
        if (count != null && count > 0) {
            log.info("超级管理员 lin 已存在，跳过初始化");
            ensureLinRole();
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password, status, company_id, deleted, create_time, update_time) " +
                "VALUES (?, ?, 1, 1, 0, NOW(), NOW())",
                "lin", passwordEncoder.encode("123"));
        ensureLinRole();
        log.info("初始化超级管理员 lin 完成（角色: admin）");
    }

    /** 确保 lin 用户与 admin 角色关联 */
    private void ensureLinRole() {
        User lin = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "lin"));
        if (lin == null) return;

        Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, "admin"));
        if (adminRole == null) return;

        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, lin.getId())
                .eq(UserRole::getRoleId, adminRole.getId()));
        if (count != null && count > 0) return;

        UserRole ur = new UserRole();
        ur.setUserId(lin.getId());
        ur.setRoleId(adminRole.getId());
        userRoleMapper.insert(ur);
    }

    /** 同步标准菜单（upsert）并自动授权给管理员角色 */
    private void syncMenus() {
        Object[][] menus = {
            {1L, 0L, "首页", "menu", "/dashboard", "Dashboard", "HomeFilled", 1},
            {2L, 0L, "基础数据", "catalog", "", "", "DataBoard", 2},
            {3L, 0L, "研发管理", "catalog", "", "", "Cpu", 3},
            {4L, 0L, "委外加工", "catalog", "", "", "Setting", 4},
            {5L, 0L, "进货业务", "catalog", "", "", "ShoppingCart", 5},
            {6L, 0L, "销售业务", "catalog", "", "", "Sell", 6},
            {7L, 0L, "成品库存业务", "catalog", "", "", "Odometer", 7},
            {8L, 0L, "财务管理", "catalog", "", "", "Money", 8},
            {9L, 0L, "设置", "catalog", "", "", "Tools", 9},
            {105L, 2L, "客户管理", "menu", "/inventory/customer", "InventoryCustomer", "UserFilled", 1},
            {106L, 2L, "供应商管理", "menu", "/supplier/manage", "SupplierManage", "OfficeBuilding", 2},
            {107L, 2L, "供货商管理", "menu", "/outsource/supplier/manage", "OutsourceSupplierManage", "Van", 3},
            {103L, 2L, "BOM表类型管理", "menu", "/dev/bom-type", "DevBomType", "Tickets", 4},
            {101L, 2L, "产品管理", "menu", "/material", "MaterialManage", "TakeawayBox", 5},
            {102L, 2L, "品牌管理", "menu", "/inventory/brand", "InventoryBrand", "CollectionTag", 6},
            {104L, 2L, "阶段模板管理", "menu", "/dev/phase-template", "DevPhaseTemplate", "Timer", 7},
            {301L, 3L, "研发项目", "menu", "/dev/project", "DevProject", "Notebook", 1},
            {302L, 3L, "BOM管理", "menu", "/dev/bom", "DevBom", "Tickets", 2},
            {303L, 3L, "图纸文档", "menu", "/dev/drawing", "DevDrawing", "Files", 3},
            {304L, 3L, "研发物料管理", "menu", "/dev/material", "DevMaterial", "Box", 4},
            {401L, 4L, "加工订单", "menu", "/outsource/order", "OutsourceOrder", "Document", 1},
            {402L, 4L, "物料订单", "menu", "/outsource/material-order", "OutsourceMaterialOrder", "ShoppingCart", 2},
            {403L, 4L, "物料信息", "menu", "/outsource/material-info", "OutsourceMaterialInfo", "Switch", 3},
            {406L, 4L, "物料收发单", "menu", "/outsource/delivery", "OutsourceDelivery", "Tickets", 4},
            {407L, 4L, "物料其他出入库", "menu", "/outsource/other-io", "OutsourceOtherIo", "Files", 5},
            {408L, 4L, "加工退货", "menu", "/outsource/return-order", "OutsourceReturnOrder", "CircleClose", 6},
            {411L, 4L, "物料退货", "menu", "/outsource/material-return", "OutsourceMaterialReturn", "Refrigerator", 7},
            {404L, 4L, "委外仓库", "menu", "/outsource/warehouse", "Warehouse", "Odometer", 8},
            {410L, 4L, "自有物料仓", "menu", "/outsource/material-warehouse", "OutsourceMaterialWarehouse", "Box", 9},
            {409L, 4L, "供应商管理", "menu", "/supplier/manage", "OutsourceSupplierManage", "UserFilled", 10},
            {405L, 4L, "加工合同模板", "menu", "/outsource/contract-template", "OutsourceContractTemplate", "Document", 11},
            {501L, 5L, "成品采购单", "menu", "/inventory/purchase", "InventoryPurchase", "ShoppingCart", 1},
            {502L, 5L, "采购退货单", "menu", "/inventory/purchase-return", "InventoryPurchaseReturn", "Refrigerator", 2},
            // 进货业务→供货商管理：指向 /outsource/supplier/manage（供货商=成品商，双模式页面）
            {503L, 5L, "供货商管理", "menu", "/outsource/supplier/manage", "OutsourceSupplierManage", "UserFilled", 3},
            {601L, 6L, "销售单", "menu", "/inventory/sale", "InventorySale", "Sell", 1},
            {602L, 6L, "客户管理", "menu", "/inventory/customer", "InventoryCustomer", "User", 5},
            // 售后：销售退单 → 退货整理 → 销售换货单（换货可选择性收费）
            {603L, 6L, "销售退单", "menu", "/sale/return", "SaleReturn", "Refund", 2},
            {605L, 6L, "销售换货单", "menu", "/sale/exchange", "SaleExchange", "Refresh", 3},
            // 委外域售后：仅退回不良品入不良仓，与销售的换货收费不是同一概念，改名避免混淆
            {604L, 6L, "委外售后退不良", "menu", "/outsource/after-sale", "AfterSale", "Service", 6},
            {701L, 7L, "成品库存", "menu", "/inventory/stock", "InventoryStock", "Odometer", 1},
            {702L, 7L, "成品仓库管理", "menu", "/inventory/warehouse", "Warehouse", "Odometer", 2},
            {703L, 7L, "成品库存流水", "menu", "/inventory/stock-log", "WarehouseStockLog", "TrendCharts", 3},
            {704L, 7L, "成品其他出入库", "menu", "/inventory/other-io", "InventoryOtherIo", "Upload", 4},
            {705L, 7L, "成品品质重分类", "menu", "/inventory/reclassify", "InventoryReclassify", "Refresh", 5},
            {706L, 7L, "成品移仓单", "menu", "/inventory/warehouse-move", "InventoryWarehouseMove", "Rank", 6},
            // 退货整理归属「销售」模块（售后链路的一环：退单/换货退回 → 整理分选 → 入成品仓/不良仓）
            // ID 仍保留 707（存量角色授权按 ID 关联，换 ID 会导致历史授权失效），仅迁移 parent_id
            {707L, 6L, "退货整理", "menu", "/inventory/return-sort", "InventoryReturnSort", "RefreshRight", 4},
            {801L, 8L, "应收管理", "menu", "/finance/receivable", "FinanceReceivable", "Wallet", 1},
            {802L, 8L, "应付管理", "menu", "/finance/payable", "FinancePayable", "CreditCard", 2},
            {803L, 8L, "账单生成", "menu", "/finance/bill", "FinanceBill", "Postcard", 3},
            {804L, 8L, "资金流水", "menu", "/finance/cashflow", "FinanceCashflow", "TrendCharts", 4},
            // 资金账户自「资金流水」页拆分为独立子菜单（807）
            {807L, 8L, "资金账户", "menu", "/finance/account", "FinanceAccount", "Coin", 5},
            {805L, 8L, "收款管理", "menu", "/finance/receipt", "FinanceReceipt", "Money", 6},
            {806L, 8L, "付款管理", "menu", "/finance/payment", "FinancePayment", "Sell", 7},
            // 费用登记：审核扣减资金账户并生成「费用支出」流水，供财务分析利润表取数
            {809L, 8L, "费用管理", "menu", "/finance/expense", "FinanceExpense", "Tickets", 8},
            // 财务分析：经营概览/利润表/资金趋势/应收应付账龄（纯查询报表）
            {808L, 8L, "财务分析", "menu", "/finance/analysis", "FinanceAnalysis", "DataAnalysis", 9},
            // 发票登记：销项/进项发票（税务口径），供税务分析发票汇总取数
            {810L, 8L, "发票管理", "menu", "/finance/invoice", "FinanceInvoice", "Stamp", 10},
            {901L, 9L, "智能管理", "menu", "/system/smart", "SystemSmart", "Cpu", 1},
            {902L, 9L, "用户管理", "menu", "/system/user", "SystemUser", "UserFilled", 2},
            {903L, 9L, "权限管理", "menu", "/system/permission", "SystemPermission", "Lock", 3},
            {904L, 9L, "系统信息", "menu", "/system/settings", "SystemSettings", "Setting", 4},
            {905L, 9L, "数据管理", "menu", "/system/data-manage", "SystemDataManage", "Folder", 5},
            {906L, 9L, "角色管理", "menu", "/system/role", "SystemRole", "Avatar", 6},
            {907L, 9L, "菜单管理", "menu", "/system/menu", "SystemMenu", "Menu", 7},
            {908L, 9L, "清空数据", "menu", "/system/clear-data", "SystemClearData", "Delete", 8},
        };
        // ON DUPLICATE KEY UPDATE 实现 upsert
        int processed = 0;
        for (Object[] m : menus) {
            try {
                jdbcTemplate.update(
                    "INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, route_path, route_name, icon, sort_order, visible, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1) " +
                    "ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), " +
                    "menu_type=VALUES(menu_type), route_path=VALUES(route_path), route_name=VALUES(route_name), " +
                    "icon=VALUES(icon), sort_order=VALUES(sort_order), visible=1, status=1",
                    m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7]);
                processed++;
            } catch (Exception e) {
                log.warn("同步菜单失败: id={}, err={}", m[0], e.getMessage());
            }
        }
        log.info("同步菜单完成，处理 {} 条", processed);

        // 删除非标准菜单（旧ID已废弃）
        Long[] newMenuIds = {1L,2L,3L,4L,5L,6L,7L,8L,9L,101L,102L,103L,104L,105L,106L,107L,301L,302L,303L,304L,401L,402L,403L,404L,405L,406L,407L,408L,409L,410L,411L,501L,502L,503L,601L,602L,603L,604L,605L,701L,702L,703L,704L,705L,706L,707L,801L,802L,803L,804L,805L,806L,807L,808L,809L,810L,901L,902L,903L,904L,905L,906L,907L,908L};
        Set<Long> newIds = new HashSet<>(Arrays.asList(newMenuIds));
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE menu_id NOT IN (" +
            String.join(",", newIds.stream().map(String::valueOf).toArray(String[]::new)) + ")");
        int deleted = jdbcTemplate.update("DELETE FROM sys_menu WHERE id NOT IN (" +
            String.join(",", newIds.stream().map(String::valueOf).toArray(String[]::new)) + ")");
        log.info("已清理 {} 个废弃旧菜单", deleted);

        // 为 admin 角色授权所有标准菜单
        for (Object[] m : menus) {
            try {
                jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, ? FROM sys_role r WHERE r.role_code = 'admin'",
                    m[0]);
            } catch (Exception ignored) {}
        }
        log.info("已为管理员角色授权标准菜单");

        // 为研发工程师补充授权研发模块菜单
        Long[] devMenuIds = {301L, 302L, 303L, 304L, 101L};
        for (Long mid : devMenuIds) {
            try {
                jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, ? FROM sys_role r WHERE r.role_code = 'dev_engineer'",
                    mid);
            } catch (Exception ignored) {}
        }
        log.info("已为研发工程师角色补充授权研发模块菜单");
    }

    /** 为6个角色分别授权对应菜单 */
    private void initRoleMenus() {
        // 管理员：全部权限
        assignRoleMenus("admin", Arrays.asList(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L,
                101L, 102L, 103L, 104L, 105L, 106L, 107L,
                301L, 302L, 303L, 304L,
                401L, 402L, 403L, 404L, 405L, 406L, 407L, 408L, 409L, 410L, 411L,
                501L, 502L, 503L,
                601L, 602L, 603L, 604L,
                701L, 702L, 703L, 704L, 705L, 706L,
                801L, 802L, 803L, 804L, 805L, 806L, 807L, 808L, 809L, 810L,
                901L, 902L, 903L, 904L, 905L, 906L, 907L, 908L));
        // 研发工程师：项目研发 + BOM + 基础产品
        assignRoleMenus("dev_engineer", Arrays.asList(
                1L, 3L, 301L, 302L, 303L, 304L, 101L));
        // 销售专员：销售业务 + 客户 + 产品
        assignRoleMenus("sales", Arrays.asList(
                1L, 6L, 601L, 602L, 603L, 604L, 101L));
        // 仓管员：进货+库存 + 仓库
        assignRoleMenus("warehouse", Arrays.asList(
                1L, 5L, 7L, 501L, 502L, 701L, 702L, 703L, 704L, 705L, 706L, 603L, 604L, 101L));
        // 跟单专员：委外加工全部
        assignRoleMenus("merchandiser", Arrays.asList(
                1L, 4L, 401L, 402L, 403L, 404L, 405L, 406L, 407L, 408L, 409L, 410L, 101L, 602L, 502L, 702L, 705L));
        // 财务：财务管理
        assignRoleMenus("finance", Arrays.asList(
                1L, 8L, 801L, 802L, 803L, 804L, 805L, 806L, 807L, 808L, 809L, 810L, 101L));
        ensureReturnSortMenuAuth();
        ensureFinanceExtraMenuAuth();
    }

    /**
     * 存量库补齐授权：财务管理拆分/新增的子菜单（807=资金账户、808=财务分析、809=费用管理、810=发票管理），
     * admin/finance 角色需能看到。assignRoleMenus 仅在角色尚无菜单时执行，故单独幂等补授。
     */
    private void ensureFinanceExtraMenuAuth() {
        try {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, m.menu_id FROM sys_role r " +
                    "CROSS JOIN (SELECT 807 AS menu_id UNION SELECT 808 UNION SELECT 809 UNION SELECT 810) m " +
                    "WHERE r.role_code IN ('admin', 'finance')");
        } catch (Exception e) {
            log.warn("补齐财务管理扩展菜单授权失败: {}", e.getMessage());
        }
    }

    /**
     * 存量库补齐授权：退货整理(707)已迁到「销售」模块（售后链路一环），销售角色需能看到。
     * <p>assignRoleMenus 仅在角色尚无任何菜单时执行，存量角色不会自动更新，故此处单独幂等补授。</p>
     */
    private void ensureReturnSortMenuAuth() {
        try {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, 707 FROM sys_role r WHERE r.role_code IN ('admin', 'sales', 'warehouse')");
        } catch (Exception e) {
            log.warn("补齐退货整理菜单授权失败: {}", e.getMessage());
        }
    }

    /** 为指定角色授权菜单（仅当角色尚无菜单权限时执行） */
    private void assignRoleMenus(String roleCode, List<Long> menuIds) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, roleCode));
        if (role == null) return;
        List<Long> existingMenuIds = roleService.getMenuIdsByRoleId(role.getId());
        if (existingMenuIds == null || existingMenuIds.isEmpty()) {
            roleService.saveRoleMenus(role.getId(), menuIds);
            log.info("初始化 {} 菜单权限完成", roleCode);
        }
    }

    /** 初始化默认BOM类型（玻璃/驱动IC/触摸IC/码片IC/排线/盖板/背贴/钢板/COP） */
    private void initBomTypes() {
        // 补齐默认BOM类型：表空时全量初始化；已有部分时仅补缺失的默认类型（幂等）
        List<BomType> existing = bomTypeMapper.selectList(
                new LambdaQueryWrapper<BomType>().eq(BomType::getCompanyId, 1L));
        java.util.Set<String> existingNames = new java.util.HashSet<>();
        int maxSort = 0;
        for (BomType b : existing) {
            if (b.getTypeName() != null) existingNames.add(b.getTypeName());
            if (b.getSortOrder() != null && b.getSortOrder() > maxSort) maxSort = b.getSortOrder();
        }
        String[] defaultTypes = DefaultBomTypes.TYPES;
        int nextSort = maxSort;
        int added = 0;
        for (String name : defaultTypes) {
            if (existingNames.contains(name)) continue;
            BomType bt = new BomType();
            bt.setTypeName(name);
            bt.setSortOrder(++nextSort);
            bt.setStatus(1);
            bt.setIsDefault(1);
            bt.setCompanyId(1L);
            bomTypeMapper.insert(bt);
            added++;
        }
        if (added > 0) log.info("补齐默认BOM类型 {} 条", added);
    }

    /** 初始化14个研发阶段模板 */
    private void initPhaseTemplates() {
        Long count = phaseTemplateMapper.selectCount(null);
        if (count != null && count > 0) {
            log.info("阶段模板数据已存在，跳过初始化");
            return;
        }
        // 阶段模板：name, defaultDays, sortOrder, remark, productStatusSync
        // 小批量/结项阶段需触发关联产品状态由"研发中"改为"正常"
        Object[][] defaultPhases = {
            {"立项", 0, 1, "", 0},
            {"结构评估", 2, 2, "根据玻璃尺寸和摄像头孔位与R角来综合评估结构是否支持立项。", 0},
            {"立项准备", 5, 3, "根据项目型号收手机，拆分成机板和屏幕分体状态，交给触摸方案公司抓取触摸协议，明确是否可以破解协议以及用哪颗物料可以满足技术标准。", 0},
            {"显示评估", 2, 4, "提供机板和原屏给到显示方案公司，并告知触摸方案商建议使用的触摸IC料号及规格书与触摸原理图，让显示方案公司抓取显示协议，根据手机的分辨率与刷新率和玻璃的分辨率综合评估用哪颗码片物料，以及驱动IC。", 0},
            {"排线图纸", 3, 5, "根据触摸方案公司建议的触摸IC和显示方案公司建议的码片，开始画图纸，一般都可以画。", 0},
            {"排线打样", 4, 6, "出图纸后，把图纸给到排线工厂打样，一般打10PCS，码片和触摸IC需要找方案公司提供。", 0},
            {"FOG打样", 2, 7, "排线打样好之后直接让工厂寄给打样加工厂，同时需要寄驱动IC过去和玻璃过去，一般先打样5PCS。", 0},
            {"显示调试", 5, 8, "FOG打样直接寄到显示方案公司，并且提供机板，开始调试显示功能。", 0},
            {"触摸调试", 5, 9, "初版显示做好以后，移交机板和FOG去触摸方案公司调试触摸。同时保留一个机板和FOG去盖板厂开模做盖板样品。", 0},
            {"背贴盖板打样", 2, 10, "使用保留的一个机板和FOG去盖板厂根据屏幕的实际显示效果开模做盖板样品，然后去背贴厂开背贴样品。", 0},
            {"总成样品", 2, 11, "将盖板和背贴样品寄到加工厂做成总成，需要寄2PCS总成和机板过去方案公司优化触摸。", 0},
            {"测试", 5, 12, "开始测试，需要测试结构/显示/触摸，详见测试文档。", 0},
            {"小批量", 3, 13, "测试没问题之后，下物料寄到工厂，先进行100PCS的小批量，到货后过一遍，没有批次问题，就可以结项了。", 1},
            {"结项", 0, 14, "结项，通知工厂开始量产。", 1}
        };
        for (Object[] p : defaultPhases) {
            PhaseTemplate t = new PhaseTemplate();
            t.setName((String) p[0]);
            t.setDefaultDays((Integer) p[1]);
            t.setSortOrder((Integer) p[2]);
            t.setRemark((String) p[3]);
            t.setProductStatusSync((Integer) p[4]);
            t.setCompanyId(1L);
            phaseTemplateMapper.insert(t);
        }
        log.info("初始化阶段模板数据完成（共 {} 条）", defaultPhases.length);
    }
}
