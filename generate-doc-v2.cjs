const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, AlignmentType, HeadingLevel, BorderStyle, WidthType,
  TableOfContents, PageNumber, PageBreak, ImageRun, ShadingType
} = require("docx");

const BLUE = "2E75B6";
const LIGHT_GRAY = "F2F2F2";
const DARK_GRAY = "404040";
const BORDER_COLOR = "CCCCCC";
const border = { style: BorderStyle.SINGLE, size: 1, color: BORDER_COLOR };
const borders = { top: border, bottom: border, left: border, right: border };
const cellMargins = { top: 60, bottom: 60, left: 100, right: 100 };

const IMG_DIR = path.join(__dirname, "diagrams", "png");

function h1(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun({ text, bold: true, size: 32, color: BLUE })] });
}
function h2(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun({ text, bold: true, size: 28, color: BLUE })] });
}
function h3(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun({ text, bold: true, size: 24, color: DARK_GRAY })] });
}
function p(text, opts = {}) {
  return new Paragraph({
    spacing: { after: 120, line: 360 },
    children: [new TextRun({ text, font: "Microsoft YaHei", size: 22, ...opts })],
  });
}
function bold(text) {
  return new TextRun({ text, bold: true, font: "Microsoft YaHei", size: 22 });
}
function bullet(text) {
  return new Paragraph({
    numbering: { reference: "bullets", level: 0 },
    spacing: { after: 60, line: 340 },
    children: [new TextRun({ text, font: "Microsoft YaHei", size: 22 })],
  });
}
function emptyLine() {
  return new Paragraph({ spacing: { after: 60 }, children: [] });
}
function pageBreak() {
  return new Paragraph({ children: [new PageBreak()] });
}
function cell(text, opts = {}) {
  const { width, shading, bold } = opts;
  return new TableCell({
    children: [new Paragraph({
      children: [new TextRun({ text, bold: !!bold, font: "Microsoft YaHei", size: 20, color: opts.color || DARK_GRAY })],
      alignment: opts.align || AlignmentType.LEFT,
    })],
    width: width ? { size: width, type: WidthType.PERCENTAGE } : undefined,
    shading: shading ? { fill: shading, type: ShadingType.CLEAR } : undefined,
    verticalAlign: "center",
    margins: cellMargins,
    borders,
  });
}
function imagePara(file, opts = {}) {
  const data = fs.readFileSync(path.join(IMG_DIR, file));
  return new Paragraph({
    spacing: { before: 120, after: 120 },
    alignment: AlignmentType.CENTER,
    children: [new ImageRun({
      data,
      transformation: { width: opts.width || 580, height: opts.height || 180 },
      type: "png",
    })],
  });
}
function notePara(text) {
  return new Paragraph({
    spacing: { before: 80, after: 80 },
    shading: { fill: "FFFDE7", type: ShadingType.CLEAR },
    children: [new TextRun({ text: "说明：" + text, font: "Microsoft YaHei", size: 20, color: "666666" })],
  });
}

function twoColumnTable(rows, widths = [30, 70]) {
  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    rows: rows.map(([k, v]) => new TableRow({
      children: [
        cell(k, { width: widths[0], shading: LIGHT_GRAY, bold: true }),
        cell(v, { width: widths[1] }),
      ],
    })),
    borders,
  });
}

function headerTable(headers, rows) {
  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    rows: [
      new TableRow({ children: headers.map(h => cell(h, { shading: BLUE, bold: true, color: "FFFFFF" })) }),
      ...rows.map(r => new TableRow({ children: r.map(c => cell(c)) })),
    ],
    borders,
  });
}

// 封面
const cover = [
  new Paragraph({ spacing: { before: 2400 }, children: [] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 400 }, children: [new TextRun({ text: "北辰 ERP", bold: true, size: 72, color: BLUE, font: "Microsoft YaHei" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new TextRun({ text: "系统架构与业务说明文档", size: 44, color: DARK_GRAY, font: "Microsoft YaHei" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 }, children: [new TextRun({ text: "V2.0", size: 28, color: "666666", font: "Microsoft YaHei" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: `生成日期：${new Date().toLocaleDateString("zh-CN")}`, size: 24, color: "666666", font: "Microsoft YaHei" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: "状态：正式发布版", size: 24, color: "666666", font: "Microsoft YaHei" })] }),
  pageBreak(),
];

// 修订记录
const revision = [
  h2("文档修订记录"),
  headerTable(["版本", "日期", "修订内容", "修订人"], [
    ["V1.0", "2026-08-11", "初始版本：模块说明与业务流程文字版", "AI 辅助"],
    ["V1.1", "2026-08-11", "补充业务流程表格与模块细化", "AI 辅助"],
    ["V2.0", new Date().toLocaleDateString("zh-CN"), "全面重构：新增真实业务流程图，业务流程大白话重写，面向非技术领导", "AI 辅助"],
  ]),
  emptyLine(),
  notePara("本版重点改进：把晦涩的表格改成直观的流程图，所有业务都用「通俗理解」一段话讲清楚，不懂技术也能看懂。"),
  pageBreak(),
];

// 目录
const toc = [
  new TableOfContents("目录", {
    hyperlink: true,
    headingStyleRange: "1-3",
    stylesWithLevels: [
      { styleId: "Heading1", level: 0 },
      { styleId: "Heading2", level: 1 },
      { styleId: "Heading3", level: 2 },
    ],
  }),
  new Paragraph({ spacing: { before: 300, after: 200 }, children: [
    new TextRun({ text: "（提示：打开文档后，右键本页目录 → 更新域 → 更新整个目录，即可显示完整页码。）", color: "C00000", size: 20, font: "Microsoft YaHei" })
  ]}),
  pageBreak(),
];

const sections = [];

// 一、项目概述
sections.push(
  h1("一、项目概述"),
  h2("1.1 北辰 ERP 是什么"),
  p("北辰 ERP 是一款面向屏幕总成加工行业的企业资源管理系统。它把公司的核心业务——从研发设计、物料采购、委外加工、成品入库、销售出库到财务收钱付款——全部串成一条线，做到每一笔钱、每一件货都有据可查。"),
  p("简单来说，北辰 ERP 要解决三个问题：", { bold: true }),
  bullet("货在哪里：实时知道每个仓库里有什么产品、有多少、品质等级如何。"),
  bullet("钱在哪里：清楚每一单采购欠供应商多少、每一笔销售客户欠多少、已收已付了多少。"),
  bullet("事到哪里：研发项目有没有延期、委外加工进度如何、单据有没有审核、下一步该做什么。"),
  h2("1.2 核心价值"),
  headerTable(["价值点", "具体说明"], [
    ["流程规范", "所有业务单据统一走「草稿→审核→生效」流程，防止随意改数。"],
    ["数据联动", "采购入库自动增加库存并产生应付；销售出库自动扣减库存并产生应收。"],
    ["责任清晰", "每家公司、每个用户、每张单据都能追溯到人。"],
    ["决策可视", "清算看板、库存流水、资金流水让领导一眼看清经营情况。"],
  ]),
  h2("1.3 技术栈"),
  twoColumnTable([
    ["前端", "Vue3 + Element Plus + Vite"],
    ["后端", "SpringBoot 3.2.5 + Java 21 + MyBatis-Plus 3.5.5 + Sa-Token"],
    ["数据库", "MySQL 8"],
    ["部署", "前后端分离，后端端口 8080（前缀 /api），前端端口 5173"],
  ]),
  pageBreak()
);

// 二、功能模块总览
sections.push(
  h1("二、功能模块总览"),
  p("系统共分为 13 个业务模块，覆盖了屏幕总成加工企业日常经营的方方面面。"),
  headerTable(["业务板块", "模块", "核心功能"], [
    ["基础数据", "供应商 / 客户 / 品牌 / 产品", "维护供应商分类、客户信息、品牌、成品/物料主数据"],
    ["研发管理", "研发管理", "项目立项、BOM 用料清单、图纸文档、缺陷管理、阶段模板、研发物料"],
    ["委外加工", "委外加工", "委外加工单、物料订单、物料收发、委外仓库、交货分级、售后退货"],
    ["进销存", "进销存", "自有仓库、库存实时查询、移仓调拨、其他出入库、品质重分类"],
    ["采购管理", "采购管理", "采购订单、采购入库、采购退货"],
    ["销售管理", "销售管理", "销售订单、销售出库、销售退货"],
    ["财务管理", "财务管理", "应收、应付、账单、收款单、付款单、资金流水、清算看板"],
    ["系统管理", "系统管理", "用户、角色、菜单、权限、公司、数据管理"],
  ]),
  p("下面会重点用流程图的方式，把领导最关心的几条核心业务流程讲清楚。", { bold: true }),
  pageBreak()
);

// 三、核心业务流程（大白话+流程图）
sections.push(
  h1("三、核心业务流程"),
  h2("3.1 整体业务全景"),
  p("先放一张总图，让大家对公司业务在系统里怎么跑起来有个整体印象。"),
  imagePara("fig1-overview.png", { width: 580, height: 170 }),
  p("", { bold: true }),
  p("业务主线只有一条：研发设计产品 → 采购物料 → 发给代工厂加工 → 收回成品入库 → 销售发货 → 财务收钱付款。"),
  p("每条线上都有系统自动生成的单据和记录，环环相扣，避免人工漏记、错记。"),
  h2("3.2 委外加工流程（核心业务）"),
  p("屏幕总成加工企业最重要的业务就是「委外加工」：公司负责研发和接单，原材料买回来发给外协工厂，工厂加工完成后再把成品交回来。"),
  imagePara("fig2-outsource.png", { width: 580, height: 280 }),
  p("", { bold: true }),
  p("通俗理解："),
  bullet("研发先出 BOM（用料清单），告诉采购和生产需要哪些物料。"),
  bullet("采购创建「委外物料订单」，向供应商买这些物料，订单会经过「待确认→收货中→已完成」的状态。"),
  bullet("物料到了之后，通过「物料收发单」发给代工厂，或者从代工厂收回边角余料。"),
  bullet("创建「委外加工单」，工厂接单后状态变为「生产中」，做完后交货。"),
  bullet("交货时要验品质：A级/B级/C级/不良品，不同等级入库后的用途和价格都不一样。"),
  bullet("最后财务结算：既要付材料款给物料供应商，也要付加工费给加工厂。"),
  h2("3.3 采购入库流程"),
  p("除了委外采购，公司也会直接向供应商采购成品或原材料。采购入库流程如下："),
  imagePara("fig3-purchase.png", { width: 580, height: 150 }),
  p("", { bold: true }),
  p("通俗理解：采购先在系统里下「采购订单」→ 领导审核确认 → 货到仓库后做「采购入库单」→ 再审核入库，库存自动增加，同时系统生成一笔「应付账款」（欠供应商的钱）。如果货有问题，做「采购退货」，库存和应付都会相应扣减。"),
  h2("3.4 销售出库流程"),
  p("销售是公司收入的主要来源，流程和采购正好反过来："),
  imagePara("fig4-sale.png", { width: 580, height: 150 }),
  p("", { bold: true }),
  p("通俗理解：客户在系统里下「销售订单」→ 审核时系统会先检查仓库有没有货 → 有货就做「销售出库单」→ 审核出库后库存自动扣减，同时生成一笔「应收账款」（客户欠公司的钱）。客户退货则做「销售退货单」，库存和应收都会加回来。"),
  h2("3.5 库存管理流程"),
  p("库存是所有业务的交汇点。采购、销售、委外交货、移仓、盘点都会影响库存："),
  imagePara("fig5-inventory.png", { width: 580, height: 180 }),
  p("", { bold: true }),
  p("通俗理解：系统把库存当成一个「账本」，每次变动都要登记："),
  bullet("采购入库 → 库存增加"),
  bullet("销售出库 → 库存减少"),
  bullet("移仓调拨 → 一个仓库少、另一个仓库多"),
  bullet("其他出入库 → 盘点、报废、赠送等非标场景"),
  bullet("品质重分类 → 同一仓库内，产品从 A 级改成 B 级"),
  p("每次变动都会留下「库存流水」，写清楚是哪张单据导致的、变动前后数量是多少，方便对账。"),
  h2("3.6 财务结算流程"),
  p("财务模块是业务的最后一环，核心就是把「应收应付」管清楚、把钱收回来付出去："),
  imagePara("fig6-finance.png", { width: 580, height: 180 }),
  p("", { bold: true }),
  p("通俗理解："),
  bullet("公司买货，欠供应商的钱 → 叫「应付账款」"),
  bullet("公司卖货，客户欠公司的钱 → 叫「应收账款」"),
  bullet("按供应商/客户汇总 → 生成「账单」"),
  bullet("给供应商付款 → 填「付款单」；收客户的钱 → 填「收款单」"),
  bullet("每一笔收付款都记到「资金流水」，最后在看板上一眼看清：谁欠多少、已还多少、还剩多少。"),
  h2("3.7 单据通用状态流转"),
  p("无论是采购单、销售单、出入库单还是收付款单，它们在系统里的状态流转都是统一的："),
  imagePara("fig7-status.png", { width: 580, height: 140 }),
  p("", { bold: true }),
  p("通俗理解："),
  bullet("草稿：刚填好，还没生效，可以随时改。"),
  bullet("审核：领导确认没问题后点「审核」，单据正式生效，同时触发库存或财务变动。"),
  bullet("反审核：发现错了，可以反审核回草稿状态重新改。"),
  bullet("作废/取消：单据彻底不用了。"),
  pageBreak()
);

// 四、关键业务模块说明
sections.push(
  h1("四、关键业务模块说明"),
  h2("4.1 研发管理"),
  p("研发是业务的起点。系统用项目化的方式管理研发全过程。"),
  twoColumnTable([
    ["核心功能", "项目立项、BOM 用料清单、图纸文档、缺陷管理、阶段模板、研发物料"],
    ["解决的问题", "避免项目进度靠口头汇报、BOM 版本混乱、研发用料和实际采购脱节"],
    ["关键表", "dev_project、dev_bom、dev_bom_type、dev_drawing、dev_bug、dev_phase_template、dev_purchase_item"],
  ]),
  h2("4.2 委外加工"),
  p("委外加工是北辰 ERP 最复杂的业务板块，也是本系统的核心能力。"),
  twoColumnTable([
    ["核心功能", "外协物料主数据、委外加工单、委外物料订单、物料收发单、委外仓库、加工交货、售后退货"],
    ["解决的问题", "工厂进度不透明、物料发给工厂后账实不符、交货品质等级混乱、加工费结算扯皮"],
    ["关键表", "outsource_order、outsource_material、outsource_material_order、outsource_delivery、outsource_warehouse 等"],
  ]),
  h2("4.3 进销存"),
  p("进销存是公司货物的「大本营」，所有库存变动都在这里汇总。"),
  twoColumnTable([
    ["核心功能", "仓库管理、库存查询、库存流水、移仓调拨、其他出入库、品质重分类"],
    ["解决的问题", "库存数量不准、不知道货在哪、盘点对不上、品质等级没分开管"],
    ["关键表", "inventory_warehouse、inventory_stock、inventory_stock_log、inventory_warehouse_move、inventory_other_io"],
  ]),
  h2("4.4 采购与销售"),
  p("采购和销售是业务的入口和出口。"),
  twoColumnTable([
    ["采购管理", "采购订单 → 采购入库 → 采购退货，入库自动增加库存并产生应付"],
    ["销售管理", "销售订单 → 销售出库 → 销售退货，出库自动扣减库存并产生应收"],
    ["共同点", "都支持审核/反审核/作废，都有完整的单据状态流转"],
  ]),
  h2("4.5 财务管理"),
  p("财务模块把业务数据自动转成财务数据，减少人工重复录入。"),
  twoColumnTable([
    ["核心功能", "应收管理、应付管理、账单、收款单、付款单、资金流水、清算看板"],
    ["业务联动", "采购入库自动生成应付；销售出库自动生成应收"],
    ["关键表", "finance_receivable、finance_payable、finance_bill、finance_cashflow、finance_receipt、finance_payment"],
  ]),
  h2("4.6 系统管理"),
  p("系统管理是保障多公司、多用户安全使用的基础。"),
  twoColumnTable([
    ["核心功能", "用户管理、角色管理、菜单权限、公司管理、数据管理"],
    ["权限模型", "RBAC：用户 → 角色 → 菜单，前后端双重校验"],
    ["数据隔离", "按 company_id 隔离，不同公司只能看到自己的数据"],
  ]),
  pageBreak()
);

// 五、系统关键设计
sections.push(
  h1("五、系统关键设计"),
  p("为了让系统稳定、安全、可扩展，我们在设计上做了以下关键选择："),
  headerTable(["设计点", "说明"], [
    ["多公司隔离", "CompanyContext + MyBatis-Plus 多租户插件，不同公司数据完全隔离"],
    ["权限控制", "RBAC 模型：用户-角色-菜单，前端路由守卫 + 后端 Sa-Token 注解双重校验"],
    ["数据安全", "密码 BCrypt 加密；业务单据用 status 状态管理生命周期；用户表使用逻辑删除"],
    ["库存安全", "所有库存变动加 @Transactional 事务，避免部分成功导致账实不符"],
    ["审计链路", "库存流水 + 资金流水 + 物料收发单强关联，追溯到原始单据"],
    ["存 ID 不存名", "明细表只存关联 ID，展示时关联查询名称，避免数据冗余和不一致"],
    ["平滑升级", "DataInitializer 启动时检测表/列是否存在，支持旧库无停机升级"],
  ]),
  pageBreak()
);

// 六、数据库与接口规模
sections.push(
  h1("六、数据库与接口规模"),
  p("目前系统数据库约 56 张业务表，覆盖上述所有模块。"),
  headerTable(["模块", "表数量", "主要表"], [
    ["系统管理", "6", "sys_user、sys_role、sys_menu、sys_company、sys_user_role、sys_role_menu"],
    ["研发管理", "7", "dev_project、dev_bom、dev_bom_type、dev_drawing、dev_bug、dev_phase_template、dev_purchase_item"],
    ["委外加工", "14", "outsource_order、outsource_material、outsource_material_order、outsource_delivery、outsource_warehouse 等"],
    ["进销存", "7", "inventory_warehouse、inventory_stock、inventory_stock_log、inventory_warehouse_move 等"],
    ["采购管理", "6", "purchase_order、purchase_order_item、purchase_inbound、purchase_inbound_item、purchase_return 等"],
    ["销售管理", "6", "sale_order、sale_order_item、sale_outbound、sale_outbound_item、sale_return 等"],
    ["财务管理", "6", "finance_receivable、finance_payable、finance_bill、finance_cashflow、finance_receipt、finance_payment"],
    ["基础数据", "4", "supplier、customer、brand、product"],
  ]),
  p("后端接口统一返回 R<T> 泛型封装体，采用 RESTful 风格命名；前端按模块封装 API，便于维护。"),
  pageBreak()
);

// 七、项目现状与后续规划
sections.push(
  h1("七、项目现状与后续规划"),
  h2("7.1 已完成功能"),
  bullet("基础数据：供应商/客户/品牌/产品管理"),
  bullet("研发管理：项目、BOM、图纸、缺陷、阶段模板、研发物料"),
  bullet("委外加工：加工单、物料订单、收发单、委外仓库、交货分级、售后退货"),
  bullet("进销存：仓库、库存、流水、移仓、其他出入库、品质重分类"),
  bullet("采购销售：采购订单/入库/退货，销售订单/出库/退货"),
  bullet("财务管理：应收应付、账单、收款付款、资金流水、清算看板"),
  bullet("系统管理：用户、角色、菜单、权限、公司、数据管理"),
  h2("7.2 后续规划"),
  bullet("报表中心：采购分析、销售分析、库存周转、利润报表"),
  bullet("审批流：关键单据支持自定义审批流程"),
  bullet("移动端：仓库收发货、审批消息移动端处理"),
  bullet("供应链协同：与供应商/客户系统对接，自动同步订单和库存"),
  emptyLine(),
  p("以上就是北辰 ERP 的系统架构与业务说明。如需进一步细化某个模块或补充更多流程图，请随时提出。"),
);

const doc = new Document({
  title: "北辰ERP系统架构与业务说明文档",
  subject: "北辰ERP系统架构、业务流程、模块说明",
  creator: "北辰ERP项目组",
  description: "面向领导的北辰ERP系统说明文档，包含业务流程图",
  styles: {
    default: {
      document: {
        run: { font: "Microsoft YaHei", size: 22 },
        paragraph: { spacing: { line: 360, after: 120 } },
      },
    },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", run: { font: "Microsoft YaHei", bold: true, size: 32, color: BLUE }, paragraph: { spacing: { before: 240, after: 120 } } },
      { id: "Heading2", name: "Heading 2", run: { font: "Microsoft YaHei", bold: true, size: 28, color: BLUE }, paragraph: { spacing: { before: 200, after: 100 } } },
      { id: "Heading3", name: "Heading 3", run: { font: "Microsoft YaHei", bold: true, size: 24, color: DARK_GRAY }, paragraph: { spacing: { before: 160, after: 80 } } },
    ],
  },
  numbering: {
    config: [{
      reference: "bullets",
      levels: [{
        level: 0,
        format: "bullet",
        text: "•",
        alignment: AlignmentType.LEFT,
        style: { paragraph: { indent: { left: 720, hanging: 360 } } },
      }],
    }],
  },
  sections: [{
    properties: { page: { margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } } },
    headers: { default: new Header({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: "北辰 ERP 系统架构与业务说明文档", color: "999999", size: 18, font: "Microsoft YaHei" })] })] }) },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: "第 ", size: 18, font: "Microsoft YaHei" }), new TextRun({ children: [PageNumber.CURRENT], size: 18 }), new TextRun({ text: " 页 / 共 ", size: 18, font: "Microsoft YaHei" }), new TextRun({ children: [PageNumber.TOTAL_PAGES], size: 18 }), new TextRun({ text: " 页", size: 18, font: "Microsoft YaHei" })] })] }) },
    children: [
      ...cover,
      ...revision,
      ...toc,
      ...sections,
    ],
  }],
});

const outPath = path.join(__dirname, "北辰ERP系统架构与业务说明文档.docx");
Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync(outPath, buf);
  console.log("文档已生成:", outPath);
});
