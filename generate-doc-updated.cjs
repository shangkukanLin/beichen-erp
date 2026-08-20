const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, AlignmentType, LevelFormat,
  TableOfContents, HeadingLevel, BorderStyle, WidthType, ShadingType,
  PageNumber, PageBreak, ImageRun
} = require("docx");

// ==================== 常量 ====================
const PAGE_WIDTH = 11906;
const PAGE_HEIGHT = 16838;
const MARGIN = 1440;
const BLUE = "2E75B6";
const LIGHT_GRAY = "F2F2F2";
const DARK_GRAY = "404040";
const BORDER_COLOR = "CCCCCC";

const border = { style: BorderStyle.SINGLE, size: 1, color: BORDER_COLOR };
const borders = { top: border, bottom: border, left: border, right: border };
const cellMargins = { top: 60, bottom: 60, left: 100, right: 100 };

// ==================== 辅助函数 ====================
function heading1(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun(text)] });
}
function heading2(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun(text)] });
}
function heading3(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun(text)] });
}
function para(text) {
  return new Paragraph({
    spacing: { after: 120, line: 360 },
    children: [new TextRun({ text, font: "Arial", size: 22 })],
  });
}
function boldPara(text) {
  return new Paragraph({
    spacing: { after: 120, line: 360 },
    children: [new TextRun({ text, font: "Arial", size: 22, bold: true })],
  });
}
function bulletItem(text) {
  return new Paragraph({
    numbering: { reference: "bullets", level: 0 },
    spacing: { after: 60, line: 340 },
    children: [new TextRun({ text, font: "Arial", size: 22 })],
  });
}
function emptyLine() {
  return new Paragraph({ spacing: { after: 60 }, children: [] });
}
function pageBreak() {
  return new Paragraph({ children: [new PageBreak()] });
}

function tableCell(text, opts = {}) {
  const { width, shading, bold } = opts;
  return new TableCell({
    borders,
    width: width ? { size: width, type: WidthType.DXA } : undefined,
    shading: shading ? { fill: shading, type: ShadingType.CLEAR } : undefined,
    margins: cellMargins,
    verticalAlign: "center",
    children: [
      new Paragraph({
        children: [
          new TextRun({
            text: String(text),
            font: "Arial",
            size: bold ? 21 : 20,
            bold: !!bold,
            color: (shading === BLUE) ? "FFFFFF" : undefined,
          }),
        ],
      }),
    ],
  });
}

function th(text, width) { return tableCell(text, { width, shading: BLUE, bold: true }); }

function createTable(headers, rows, colWidths) {
  return new Table({
    columnWidths: colWidths,
    rows: [
      new TableRow({ children: headers.map((h, i) => th(h, colWidths[i])), tableHeader: true }),
      ...rows.map((row, idx) =>
        new TableRow({
          children: row.map((cell, i) =>
            tableCell(cell, { width: colWidths[i], shading: idx % 2 === 1 ? LIGHT_GRAY : undefined })
          ),
        })
      ),
    ],
  });
}

// ==================== 页眉页脚 ====================
const bodyHeader = new Header({
  children: [
    new Paragraph({
      alignment: AlignmentType.RIGHT,
      border: { bottom: { style: BorderStyle.SINGLE, size: 1, color: BORDER_COLOR, space: 4 } },
      children: [new TextRun({ text: "北辰ERP系统 - 系统架构与业务说明文档", font: "Arial", size: 18, color: "999999", italics: true })],
    }),
  ],
});

const bodyFooter = new Footer({
  children: [
    new Paragraph({
      alignment: AlignmentType.CENTER,
      children: [
        new TextRun({ text: "- ", font: "Arial", size: 18, color: "999999" }),
        new TextRun({ children: [PageNumber.CURRENT], font: "Arial", size: 18, color: "999999" }),
        new TextRun({ text: " -", font: "Arial", size: 18, color: "999999" }),
      ],
    }),
  ],
});

// ==================== 封面 ====================
function coverSection() {
  return {
    properties: {
      page: { size: { width: PAGE_WIDTH, height: PAGE_HEIGHT }, margin: { top: MARGIN, right: MARGIN, bottom: MARGIN, left: MARGIN } },
    },
    children: [
      emptyLine(), emptyLine(), emptyLine(), emptyLine(), emptyLine(), emptyLine(), emptyLine(),

      new Paragraph({
        alignment: AlignmentType.CENTER,
        spacing: { after: 200 },
        children: [new TextRun({ text: "北辰ERP系统", font: "Arial", size: 56, bold: true, color: BLUE })],
      }),

      new Paragraph({
        alignment: AlignmentType.CENTER,
        spacing: { after: 100 },
        children: [new TextRun({ text: "系统架构与业务说明文档", font: "Arial", size: 36, color: DARK_GRAY })],
      }),

      emptyLine(), emptyLine(),

      new Paragraph({
        alignment: AlignmentType.CENTER,
        spacing: { after: 300 },
        border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: BLUE, space: 1 } },
        children: [],
      }),

      emptyLine(), emptyLine(),

      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 80 }, children: [new TextRun({ text: "版本：V1.1", font: "Arial", size: 24, color: "666666" })] }),
      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 80 }, children: [new TextRun({ text: "日期：2026年8月11日", font: "Arial", size: 24, color: "666666" })] }),
      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 80 }, children: [new TextRun({ text: "状态：核心功能开发完成", font: "Arial", size: 24, color: "666666" })] }),

      pageBreak(),
      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new TextRun({ text: "修订记录", font: "Arial", size: 32, bold: true, color: BLUE })] }),
      createTable(
        ["版本", "日期", "修订内容", "作者"],
        [
          ["V1.0", "2026-08-11", "初始版本，覆盖全部13个模块说明", "系统自动生成"],
          ["V1.1", "2026-08-11", "新增业务流程图（结构化流程描述），更新最新模块状态", "系统自动生成"],
        ],
        [1200, 1400, 4426, 2000]
      ),
    ],
  };
}

// ==================== 目录 ====================
function tocSection() {
  return {
    properties: { page: { size: { width: PAGE_WIDTH, height: PAGE_HEIGHT }, margin: { top: MARGIN, right: MARGIN, bottom: MARGIN, left: MARGIN } } },
    headers: { default: bodyHeader },
    footers: { default: bodyFooter },
    children: [
      heading1("目录"),
      new Paragraph({
        spacing: { after: 200 },
        children: [new TextRun({ text: "（提示：打开文档后，右键此处 → 更新域 → 更新整个目录，即可显示页码）", font: "Arial", size: 20, color: "CC0000", italics: true })],
      }),
      new TableOfContents("目录", { hyperlink: true, headingStyleRange: "1-3" }),
    ],
  };
}

// ==================== 正文 ====================
function bodySection() {
  const children = [];

  // ======== 一、项目概述 ========
  children.push(heading1("一、项目概述"));

  children.push(heading2("1.1 项目定位"));
  children.push(para("北辰ERP是一套面向屏幕总成加工行业的全链路企业资源管理系统，覆盖从研发立项、物料采购、委外加工、成品入库、销售出库到财务结算的完整业务闭环。系统采用前后端分离架构，支持多公司独立运营，实现业务流程标准化、数据管理精细化。目前已开发完成13个业务模块、56张数据表、200余个后端接口。"));

  children.push(heading2("1.2 核心价值"));
  children.push(bulletItem("业务全链路覆盖：研发→采购→委外→库存→销售→财务，数据贯通无断点"));
  children.push(bulletItem("多公司隔离运营：基于公司级数据隔离，一套系统支撑多个独立主体"));
  children.push(bulletItem("库存实时管控：每次入库/出库/移仓实时更新库存，完整审计链路可追溯"));
  children.push(bulletItem("委外加工精细化管理：物料收发、品质分级、交货跟踪、合同模板全覆盖"));
  children.push(bulletItem("财务自动记账：业务单据审核后自动生成应收/应付，资金流水全记录"));
  children.push(bulletItem("操作可追溯：库存流水 + 资金流水 + 收发单强关联，完整审计链路"));

  children.push(heading2("1.3 技术栈"));
  children.push(createTable(
    ["层级", "技术选型", "版本/说明"],
    [
      ["后端框架", "Spring Boot", "3.2.5"],
      ["开发语言", "Java", "21 (LTS)"],
      ["ORM框架", "MyBatis-Plus", "3.5.5"],
      ["权限认证", "Sa-Token", "1.37.0"],
      ["数据库", "MySQL", "8.0 (InnoDB + utf8mb4)"],
      ["前端框架", "Vue 3 + TypeScript", "3.4"],
      ["UI组件库", "Element Plus", "2.6"],
      ["状态管理", "Pinia", "2.1"],
      ["构建工具", "Vite / Maven", "5.2 / 3.9"],
    ],
    [2000, 3400, 3626]
  ));

  children.push(pageBreak());

  // ======== 二、系统架构 ========
  children.push(heading1("二、系统架构"));

  children.push(heading2("2.1 部署架构"));
  children.push(para("系统采用前后端分离部署模式："));
  children.push(bulletItem("前端（beichen-erp-web）：Vue3 SPA应用，Nginx静态托管，端口5173（开发）/ 80（生产）"));
  children.push(bulletItem("后端（beichen-erp-server）：SpringBoot Jar包运行，内嵌Tomcat，端口8080，路由前缀 /api"));
  children.push(bulletItem("数据库：MySQL 8.0，InnoDB引擎，utf8mb4字符集"));
  children.push(bulletItem("文件存储：本地磁盘 uploads/ 目录"));

  children.push(heading2("2.2 分层架构"));
  children.push(para("后端采用经典分层架构，职责清晰，保持代码可维护性："));
  children.push(createTable(
    ["层级", "包路径", "职责说明", "关键组件"],
    [
      ["Controller", "com.beichen.erp.controller", "HTTP请求接收，参数校验", "RESTful风格，返回 R<T>"],
      ["Service", "com.beichen.erp.service", "核心业务逻辑，事务管理", "@Transactional 保障一致性"],
      ["Mapper", "com.beichen.erp.mapper", "数据库访问，SQL执行", "LambdaQueryWrapper"],
      ["Entity", "com.beichen.erp.entity", "数据库表映射，直通Controller", "Lombok @Data"],
      ["Config", "com.beichen.erp.config", "系统配置与初始化", "DataInitializer 幂等建表"],
      ["Common", "com.beichen.erp.common", "工具类、异常、分页", "R.java / PageParam.java"],
    ],
    [1400, 2200, 2400, 3026]
  ));

  children.push(heading2("2.3 权限架构"));
  children.push(para("系统采用 RBAC（基于角色的访问控制）模型，前后端双重鉴权，确保系统安全："));
  children.push(para("权限关系链路：用户(sys_user) → 用户角色关联(sys_user_role) → 角色(sys_role) → 角色菜单关联(sys_role_menu) → 菜单(sys_menu)"));
  children.push(bulletItem("后端鉴权：Sa-Token 框架，支持注解式权限校验，未登录请求自动拦截返回 401"));
  children.push(bulletItem("前端鉴权：路由守卫 + 菜单白名单机制，未授权页面自动跳转 403 禁止访问页"));
  children.push(bulletItem("三级角色体系：超级管理员(super_admin) > 管理员(admin) > 普通用户(user)"));

  children.push(heading2("2.4 多租户架构"));
  children.push(para("系统通过 CompanyContext（公司上下文）实现公司级数据隔离。每个用户归属于一个公司，所有业务数据（供应商、客户、仓库、订单、财务等）均携带 company_id 字段，MyBatis-Plus 多租户插件自动拦截 SQL 注入租户条件，确保不同公司数据完全隔离，互不可见。"));

  children.push(pageBreak());

  // ======== 三、核心业务模块 ========
  children.push(heading1("三、核心业务模块"));

  children.push(heading2("3.1 模块总览"));
  children.push(para("系统共包含13个业务模块，按业务域划分为六大板块："));
  children.push(createTable(
    ["板块", "业务模块", "核心功能"],
    [
      ["基础数据", "供应商管理", "方案商/加工厂/成品商/辅料商分类管理"],
      ["基础数据", "客户管理", "客户信息维护，关联品牌"],
      ["基础数据", "品牌管理", "品牌主数据维护"],
      ["基础数据", "产品管理", "成品/物料主数据，SKU/规格/单位"],
      ["研发管理", "研发管理", "项目/BOM/图纸/缺陷/阶段模板/研发物料"],
      ["委外加工", "委外加工", "加工单/物料订单/收发单/仓库/交货/售后/合同模板"],
      ["进销存", "进销存", "仓库/库存/流水/移仓/出入库/品质重分类"],
      ["采购管理", "采购管理", "采购订单/采购入库/采购退货"],
      ["销售管理", "销售管理", "销售订单/销售出库/销售退货，含库存检查"],
      ["财务管理", "财务管理", "应收/应付/账单/流水/收款/付款/清算看板"],
      ["系统管理", "系统管理", "用户/角色/菜单/权限/公司/数据管理"],
      ["公共功能", "公共模块", "登录认证/文件上传/统一返回/全局异常处理"],
    ],
    [1200, 1800, 6026]
  ));

  children.push(heading2("3.2 研发管理模块"));
  children.push(para("研发管理是项目起点，管理屏幕总成从立项到量产的全过程："));
  children.push(bulletItem("研发项目(dev_project)：管理全生命周期（进行中→已关闭/已取消），含项目基本信息、适配机型、屏体参数、关联成品"));
  children.push(bulletItem("BOM物料清单(dev_bom)：按类型（玻璃/驱动IC/码片IC/触摸IC/排线/背贴/盖板）分组，含单套用量、损耗率、版本管理"));
  children.push(bulletItem("BOM类型(dev_bom_type)：独立管理物料分类体系，与物料类型枚举解耦"));
  children.push(bulletItem("阶段模板(dev_phase_template)：预定义项目阶段（EVT/DVT/PVT），自动生成时间线，支持触发产品状态同步"));
  children.push(bulletItem("图纸文档(dev_drawing)：关联项目的设计图纸和技术文档，支持版本控制"));
  children.push(bulletItem("缺陷管理(dev_bug)：记录研发过程中的 Bug，按严重等级分类跟踪（严重/一般/轻微）"));
  children.push(bulletItem("研发物料(dev_purchase_item)：独立管理研发用物料，支持关联项目或独立存档，按仓库类型实时定位"));

  children.push(heading2("3.3 委外加工模块"));
  children.push(para("委外加工是屏幕总成行业的核心环节，系统提供完整的委外管理能力："));
  children.push(bulletItem("委外加工单(outsource_order)：管理外协工厂的加工订单，状态流转 待确认→生产中→已完成/已取消"));
  children.push(bulletItem("委外物料订单(outsource_material_order)：向供应商采购/委外物料，待确认→收货中→已完成/已取消"));
  children.push(bulletItem("物料收发单(outsource_delivery)：发往工厂/从工厂收回，区分良品/不良品，支持维修返还/折现退款，草稿→已审核→已作废"));
  children.push(bulletItem("委外仓库(outsource_warehouse)：外协工厂独立库存管理，完整库存流水，删除前校验引用关系"));
  children.push(bulletItem("加工交货(outsource_order_delivery)：按品质等级（A/B/C/不良品）分级统计交货"));
  children.push(bulletItem("委外售后(outsource_after_sale)：委外售后收费管理"));
  children.push(bulletItem("委外退货(outsource_return_order)：加工成品退货处理"));
  children.push(bulletItem("合同模板：委外加工合同模板管理"));

  children.push(pageBreak());

  children.push(heading2("3.4 进销存模块"));
  children.push(para("进销存是库存管理的核心，所有库存变更均有完整流水记录："));
  children.push(bulletItem("仓库管理(inventory_warehouse)：自有仓库信息维护，含地址、联系人，删除前校验库存及物料引用"));
  children.push(bulletItem("库存管理(inventory_stock)：按 产品+仓库+品质等级 三维管理，实时查询当前库存"));
  children.push(bulletItem("成品移仓(inventory_warehouse_move)：仓库间调拨，草稿→已审核→已作废，审核后自动更新两仓库存"));
  children.push(bulletItem("其他出入库(inventory_other_io)：盘点调整、报废等非标操作"));
  children.push(bulletItem("品质重分类：同一仓库内不同品质等级间的库存转换"));
  children.push(bulletItem("库存流水(inventory_stock_log)：每次库存变动完整记录（操作类型、单据号、变更数量、操作人、时间戳）"));

  children.push(heading2("3.5 采购与销售模块"));
  children.push(bulletItem("采购管理(purchase_*)：采购订单→审核→采购入库→审核（自动增加库存）；采购退货→审核（自动扣减库存）"));
  children.push(bulletItem("销售管理(sale_*)：销售订单→审核→库存检查→销售出库→审核（自动扣减库存）；销售退货→审核（自动增加库存）"));
  children.push(bulletItem("所有单据均遵循统一状态机：草稿 → 已审核 → 已作废，支持审核/反审核/取消操作"));

  children.push(heading2("3.6 财务管理模块"));
  children.push(para("财务模块实现业务-财务一体化，自动记账减少人工操作："));
  children.push(bulletItem("应收管理(finance_receivable)：销售出库审核后自动生成应收账款"));
  children.push(bulletItem("应付管理(finance_payable)：采购入库审核后自动生成应付账款"));
  children.push(bulletItem("账单生成(finance_bill)：按客户/供应商汇总，支持批量生成结算账单"));
  children.push(bulletItem("收款/付款(finance_receipt/payment)：关联账单操作，草稿→审核"));
  children.push(bulletItem("资金流水(finance_cashflow)：所有资金变动完整记录，按时间段/供应商/客户查询"));
  children.push(bulletItem("清算看板：供应商结算概览，应付/已付可视化对比"));

  children.push(pageBreak());

  // ======== 四、核心业务流程 ========
  children.push(heading1("四、核心业务流程"));

  children.push(heading2("4.1 端到端业务主流程"));
  children.push(para("下图展示屏幕总成加工行业从研发到结算的完整业务链路。注：完整的 Mermaid 流程图源码见项目 diagrams/ 目录，可在 mermaid.live 中可视化查看。"));

  children.push(createTable(
    ["步骤", "业务环节", "操作说明", "涉及模块"],
    [
      ["①", "研发立项", "创建研发项目，设置阶段模板(EVT/DVT/PVT)，管理BOM和图纸文档", "研发管理"],
      ["②", "物料采购", "根据BOM创建委外物料订单，向供应商采购物料，跟踪收货进度", "委外加工"],
      ["③", "物料收发", "物料发往外协工厂 / 从工厂收回，区分良品/不良品，记录收发流水", "委外加工"],
      ["④", "委外加工", "创建委外加工单，委托外协工厂加工，跟踪生产进度", "委外加工"],
      ["⑤", "成品交货", "加工完成，按品质等级(A/B/C/不良)交货入库，更新库存", "委外/进销存"],
      ["⑥", "成品采购", "向成品供应商采购成品入库（非委外场景），审核后自动加库存", "采购管理"],
      ["⑦", "销售出库", "客户下单，库存检查后出库发货，审核后自动扣库存", "销售管理"],
      ["⑧", "财务结算", "自动生成应收/应付，收款/付款核销，可视化清算", "财务管理"],
    ],
    [500, 1200, 4826, 2500]
  ));

  children.push(heading2("4.2 单据状态流转（通用状态机）"));
  children.push(para("所有业务单据（采购入库/出库、销售出库/退货、移仓、收款/付款等）遵循统一状态机："));

  children.push(createTable(
    ["状态", "英文标识", "含义", "可执行操作", "库存/资金影响"],
    [
      ["草稿", "DRAFT", "单据刚创建，尚未生效，可任意编辑", "修改、删除、审核、作废", "无任何影响"],
      ["已审核", "AUDITED", "审核通过正式生效，库存/资金已变更", "反审核（回退到草稿）", "＜——库存/资金已变更"],
      ["已作废", "CANCELLED", "单据被废弃，不可再进行任何操作", "不可操作", "＜——审核时变更已回滚"],
    ],
    [1200, 1600, 2200, 2200, 1826]
  ));
  children.push(para("状态流转方向：草稿 —[审核]→ 已审核 —[反审核]→ 草稿；草稿 —[作废]→ 已作废；已审核 —[取消]→ 已作废"));

  children.push(heading2("4.3 委外加工单状态流转"));
  children.push(createTable(
    ["状态", "英文标识", "说明", "后续操作"],
    [
      ["待确认", "PENDING", "加工单已创建，等待确认", "确认进入生产 / 取消"],
      ["生产中", "PRODUCING", "工厂正在加工中", "完成交货 / 取消"],
      ["已完成", "FINISHED", "全部交货完成，订单关闭", "查看结单报表"],
      ["已取消", "CANCELLED", "加工单被取消", "不可操作，仅可查看"],
    ],
    [1500, 2000, 3000, 2526]
  ));

  children.push(pageBreak());

  children.push(heading2("4.4 库存变更全景视图"));
  children.push(para("下表汇总所有会触发库存变更的业务操作及其影响方向："));

  children.push(createTable(
    ["业务操作", "库存方向", "触发条件", "影响维度"],
    [
      ["采购入库", "目标仓库 +入库", "入库单审核通过", "产品 + 仓库 + 品质"],
      ["采购退货", "来源仓库 -出库", "退货单审核通过", "产品 + 仓库 + 品质"],
      ["销售出库", "来源仓库 -出库", "出库单审核通过", "产品 + 仓库 + 品质"],
      ["销售退货", "目标仓库 +入库", "退货单审核通过", "产品 + 仓库 + 品质"],
      ["成品移仓", "出库仓- / 入库仓+", "移仓单审核通过", "产品 + 仓库 + 品质"],
      ["委外交货", "目标仓库 +入库", "交货记录确认", "产品 + 仓库 + 品质"],
      ["品质重分类", "同仓品质转换", "重分类确认", "产品 + 仓库 + 品质"],
    ],
    [1600, 2200, 2200, 3026]
  ));

  children.push(heading2("4.5 委外加工完整流程"));
  children.push(para("委外加工是系统的核心业务场景，完整流程如下："));
  children.push(bulletItem("步骤1 — 研发输出BOM：研发项目完成后，BOM物料清单确认"));
  children.push(bulletItem("步骤2 — 委外物料订单：根据BOM创建物料采购订单，向供应商下单物料"));
  children.push(bulletItem("步骤3 — 物料收发：物料到货后发往外协工厂，或在工厂间调拨"));
  children.push(bulletItem("步骤4 — 委外加工单：创建加工单委托工厂生产，跟踪加工进度"));
  children.push(bulletItem("步骤5 — 成品交货：加工完成按品质等级(A/B/C/不良)分级交货入库"));
  children.push(bulletItem("步骤6 — 售后退货：处理委外售后收费和退货"));
  children.push(bulletItem("步骤7 — 财务结算：物料采购应付 + 加工费应付自动生成，统一核算"));

  children.push(heading2("4.6 财务自动记账流程"));
  children.push(bulletItem("销售出库审核 → 自动生成应收账款"));
  children.push(bulletItem("采购入库审核 → 自动生成应付账款"));
  children.push(bulletItem("按客户/供应商汇总 → 生成结算账单"));
  children.push(bulletItem("收款单/付款单 → 关联账单 → 审核 → 记录资金流水"));
  children.push(bulletItem("清算看板 → 应付/已付可视化对比"));

  children.push(pageBreak());

  // ======== 五、关键技术设计 ========
  children.push(heading1("五、关键技术设计"));

  children.push(heading2("5.1 数据安全设计"));
  children.push(bulletItem("多公司隔离：company_id 字段 + MyBatis-Plus 多租户插件自动注入，杜绝跨公司数据泄露"));
  children.push(bulletItem("密码安全：用户密码采用 BCrypt 单向加密存储，不可逆"));
  children.push(bulletItem("接口鉴权：Sa-Token 框架，未登录请求自动拦截返回 401"));
  children.push(bulletItem("权限粒度：RBAC 菜单级权限控制，前后端双重校验"));

  children.push(heading2("5.2 数据一致性保障"));
  children.push(bulletItem("事务管理：所有涉及库存增减、单据变更、资金变动的操作均添加 @Transactional 注解，异常自动回滚"));
  children.push(bulletItem("库存流水(inventory_stock_log)：每次库存变更记录完整流水（操作类型、单据号、变更数量、操作人、时间）"));
  children.push(bulletItem("资金流水(finance_cashflow)：每次收款/付款记录完整流水，支持按维度追溯"));
  children.push(bulletItem("逻辑删除：sys_user 使用 @TableLogic + 委外物料订单使用 deleted 字段，保留审计线索"));
  children.push(bulletItem("业务单据用 status 状态管理生命周期，不使用物理删除"));

  children.push(heading2("5.3 扩展性设计"));
  children.push(bulletItem("DataInitializer 幂等初始化：系统启动自动检测表/列是否存在，缺失则自动创建，支持零停机平滑升级"));
  children.push(bulletItem("菜单动态渲染：后端返回菜单树，前端递归渲染，新增功能仅需数据库插入菜单记录 + DataInitializer 同步"));
  children.push(bulletItem("枚举统一管理：业务状态枚举集中在 DocStatus.java，前端通过枚举接口动态获取"));
  children.push(bulletItem("存ID不存名：全链路明细表存储关联ID，展示时再 JOIN 查询名称，避免数据冗余和不一致"));

  children.push(heading2("5.4 委外加工专项优化"));
  children.push(bulletItem("收发单强关联：物料收发单新增 source_order_id 字段，与委外物料订单建立强关联，替代原有 remark LIKE 模糊匹配"));
  children.push(bulletItem("物料订单逻辑删除：删除改为逻辑删除(deleted=1)，保留审计记录与收发流水关联，buildItemMaps 显式过滤"));
  children.push(bulletItem("存放位置实时化：研发物料不存位置快照，按 warehouseId 批量查仓库表建 Map 实时获取名称地址"));
  children.push(bulletItem("批量查询优化：列表展示关联名称时使用 selectBatchIds 批量查询建 Map，避免 N+1"));

  children.push(pageBreak());

  // ======== 六、系统功能清单 ========
  children.push(heading1("六、系统功能清单"));

  children.push(createTable(
    ["功能模块", "功能点", "状态"],
    [
      ["系统管理", "用户管理 / 角色管理 / 菜单管理 / 权限分配 / 公司管理 / 数据清空", "✅ 已完成"],
      ["供应商管理", "供应商CRUD / 类型筛选（方案商/加工厂/成品商/辅料商）", "✅ 已完成"],
      ["客户管理", "客户CRUD / 关联品牌", "✅ 已完成"],
      ["品牌管理", "品牌CRUD", "✅ 已完成"],
      ["产品管理", "产品CRUD / SKU管理", "✅ 已完成"],
      ["研发管理", "项目 / BOM / BOM类型 / 阶段模板 / 图纸 / 缺陷 / 研发物料", "✅ 已完成"],
      ["委外加工", "加工单 / 物料订单 / 收发单 / 仓库 / 交货 / 退货 / 售后 / 合同模板", "✅ 已完成"],
      ["进销存", "仓库 / 库存 / 库存流水 / 移仓 / 其他出入库 / 品质重分类", "✅ 已完成"],
      ["采购管理", "采购订单 / 采购入库 / 采购退货", "✅ 已完成"],
      ["销售管理", "销售订单(含库存检查) / 销售出库 / 销售退货", "✅ 已完成"],
      ["财务管理", "应收 / 应付 / 账单 / 流水 / 收款 / 付款 / 清算看板", "✅ 已完成"],
      ["公共功能", "登录/退出 / 文件上传 / 统一异常 / 菜单多Tab / 前端路由守卫", "✅ 已完成"],
    ],
    [1600, 5000, 2426]
  ));

  children.push(pageBreak());

  // ======== 七、数据库设计概览 ========
  children.push(heading1("七、数据库设计概览"));

  children.push(heading2("7.1 数据表统计"));
  children.push(createTable(
    ["业务板块", "模块", "表数量", "核心表"],
    [
      ["系统管理", "system", "6", "sys_user, sys_role, sys_role_menu, sys_user_role, sys_menu, sys_company"],
      ["研发管理", "dev", "7", "dev_project, dev_bom, dev_bom_type, dev_drawing, dev_bug, dev_phase_template, dev_purchase_item"],
      ["委外加工", "outsource", "14", "outsource_order, outsource_order_product, outsource_order_material, outsource_order_delivery, outsource_material, outsource_material_component, outsource_material_order, outsource_material_order_item, outsource_delivery, outsource_delivery_item, outsource_warehouse, outsource_after_sale, outsource_return_order, outsource_contract_template"],
      ["进销存", "inventory", "7", "inventory_warehouse, inventory_stock, inventory_stock_log, inventory_warehouse_move, inventory_warehouse_move_item, inventory_other_io, inventory_other_io_item"],
      ["采购管理", "purchase", "6", "purchase_order, purchase_order_item, purchase_inbound, purchase_inbound_item, purchase_return, purchase_return_item"],
      ["销售管理", "sale", "6", "sale_order, sale_order_item, sale_outbound, sale_outbound_item, sale_return, sale_return_item"],
      ["财务管理", "finance", "6", "finance_receivable, finance_payable, finance_bill, finance_cashflow, finance_receipt, finance_payment"],
      ["基础数据", "基础", "4", "supplier, customer, brand, product (含 product_sku)"],
    ],
    [1400, 1400, 1000, 5226]
  ));
  children.push(para("系统共计 56 张业务表，采用 InnoDB 引擎 + utf8mb4 字符集，表名和字段名统一使用下划线命名。"));

  children.push(heading2("7.2 关键设计原则"));
  children.push(bulletItem("多租户隔离：所有业务表携带 company_id 字段，MyBatis-Plus 多租户插件自动隔离"));
  children.push(bulletItem("存ID不存名：明细表存储关联ID，展示时再 JOIN 查询名称，避免冗余"));
  children.push(bulletItem("逻辑删除：仅 sys_user（@TableLogic）和 outsource_material_order（deleted 字段）使用逻辑删除"));
  children.push(bulletItem("状态管理：业务单据用 status 字段管理生命周期，支持草稿/已审核/已作废"));
  children.push(bulletItem("完整审计：库存流水 + 资金流水 + 收发单强关联，全链路可追溯"));

  children.push(pageBreak());

  // ======== 八、项目现状与规划 ========
  children.push(heading1("八、项目现状与规划"));

  children.push(heading2("8.1 已完成功能"));
  children.push(bulletItem("13个业务模块核心功能全部开发完成"));
  children.push(bulletItem("56张数据库表设计完毕，DataInitializer 幂等初始化"));
  children.push(bulletItem("前后端完整联调通过，所有接口可用"));
  children.push(bulletItem("多公司数据隔离机制运行正常"));
  children.push(bulletItem("RBAC 三级权限体系完整，前后端双重鉴权"));
  children.push(bulletItem("库存实时管理 + 完整审计流水 + 所有单据状态机规范"));
  children.push(bulletItem("财务自动记账 + 资金流水追溯 + 清算看板"));
  children.push(bulletItem("委外加工全流程覆盖 + 收发单强关联优化 + 逻辑删除保留审计"));

  children.push(heading2("8.2 后续规划"));
  children.push(bulletItem("数据报表与看板：销售趋势、库存周转率、委外交付准时率等统计分析"));
  children.push(bulletItem("消息通知：库存预警、订单状态变更、审批待办等实时推送"));
  children.push(bulletItem("移动端适配：核心业务移动端操作（审批、库存查询等）"));
  children.push(bulletItem("数据导出增强：Excel 批量导出、PDF 单据打印"));
  children.push(bulletItem("操作日志：完整用户操作审计日志"));
  children.push(pageBreak());

  // ======== 附录 ========
  children.push(heading1("附录：模块目录结构"));
  children.push(heading2("后端包结构 (beichen-erp-server)"));
  children.push(para("src/main/java/com/beichen/erp/"));
  children.push(bulletItem("controller/ — 接口控制器层"));
  children.push(bulletItem("service/ + service/impl/ — 业务接口与实现层"));
  children.push(bulletItem("mapper/ — 数据访问层（MyBatis-Plus Mapper）"));
  children.push(bulletItem("entity/ — 数据库实体层（直接映射表，无VO/DTO分层）"));
  children.push(bulletItem("common/ — R.java(统一返回)、PageParam.java(分页参数)、工具类"));
  children.push(bulletItem("config/ — DataInitializer(数据初始化)、SaTokenConfig、MyBatisPlusConfig"));
  children.push(bulletItem("exception/ — GlobalExceptionHandler(全局异常处理)"));

  children.push(heading2("前端目录结构 (beichen-erp-web)"));
  children.push(para("src/"));
  children.push(bulletItem("views/ — 页面视图，按模块分目录(system/dev/outsource/inventory/purchase/sale/finance/material/supplier/customer/brand/auth)"));
  children.push(bulletItem("router/index.ts — 路由配置 + 前端守卫白名单"));
  children.push(bulletItem("api/ — 后端接口调用层，按模块分文件"));
  children.push(bulletItem("components/ — 可复用组件（如 MaterialFormDialog 共用弹窗）"));
  children.push(bulletItem("stores/ — Pinia 状态管理（用户信息、菜单树等）"));
  children.push(bulletItem("utils/ — 工具函数（请求拦截器、Token 管理等）"));

  return {
    properties: { page: { size: { width: PAGE_WIDTH, height: PAGE_HEIGHT }, margin: { top: MARGIN, right: MARGIN, bottom: MARGIN, left: MARGIN } } },
    headers: { default: bodyHeader },
    footers: { default: bodyFooter },
    children,
  };
}

// ==================== 生成文档 ====================
async function main() {
  const doc = new Document({
    styles: {
      default: {
        document: {
          run: { font: "Arial", size: 22 },
          paragraph: { spacing: { line: 360 } },
        },
      },
      paragraphStyles: [
        {
          id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
          run: { size: 32, bold: true, font: "Arial", color: BLUE },
          paragraph: { spacing: { before: 360, after: 200 }, outlineLevel: 0 },
        },
        {
          id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
          run: { size: 26, bold: true, font: "Arial", color: DARK_GRAY },
          paragraph: { spacing: { before: 240, after: 160 }, outlineLevel: 1 },
        },
        {
          id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
          run: { size: 24, bold: true, font: "Arial", color: "555555" },
          paragraph: { spacing: { before: 200, after: 120 }, outlineLevel: 2 },
        },
      ],
    },
    numbering: {
      config: [
        {
          reference: "bullets",
          levels: [{
            level: 0, format: LevelFormat.BULLET, text: "\u2022", alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 720, hanging: 360 } } },
          }],
        },
      ],
    },
    sections: [
      coverSection(),
      tocSection(),
      bodySection(),
    ],
  });

  const buffer = await Packer.toBuffer(doc);
  const outputPath = "c:/Users/75629/CodeBuddy/20260710123705/beichen-erp/北辰ERP系统架构与业务说明文档.docx";
  fs.writeFileSync(outputPath, buffer);
  console.log("文档已生成：" + outputPath);
}

main().catch(console.error);
