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
  return new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun({ text, bold: true, size: 32, color: BLUE, font: "Microsoft YaHei" })] });
}
function h2(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun({ text, bold: true, size: 28, color: BLUE, font: "Microsoft YaHei" })] });
}
function h3(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun({ text, bold: true, size: 24, color: DARK_GRAY, font: "Microsoft YaHei" })] });
}
function p(text, opts = {}) {
  return new Paragraph({ spacing: { after: 120, line: 360 }, children: [new TextRun({ text, font: "Microsoft YaHei", size: 22, ...opts })] });
}
function bullet(text) {
  return new Paragraph({ numbering: { reference: "bullets", level: 0 }, spacing: { after: 60, line: 340 }, children: [new TextRun({ text, font: "Microsoft YaHei", size: 22 })] });
}
function emptyLine() { return new Paragraph({ spacing: { after: 60 }, children: [] }); }
function pageBreak() { return new Paragraph({ children: [new PageBreak()] }); }
function cell(text, opts = {}) {
  const { width, shading, bold } = opts;
  return new TableCell({
    children: [new Paragraph({ children: [new TextRun({ text, bold: !!bold, font: "Microsoft YaHei", size: 20, color: opts.color || DARK_GRAY })], alignment: opts.align || AlignmentType.LEFT })],
    width: width ? { size: width, type: WidthType.PERCENTAGE } : undefined,
    shading: shading ? { fill: shading, type: ShadingType.CLEAR } : undefined,
    verticalAlign: "center", margins: cellMargins, borders,
  });
}
function imagePara(file, opts = {}) {
  const data = fs.readFileSync(path.join(IMG_DIR, file));
  return new Paragraph({ spacing: { before: 120, after: 120 }, alignment: AlignmentType.CENTER, children: [new ImageRun({ data, transformation: { width: opts.width || 560, height: opts.height || 160 }, type: "png" })] });
}
function notePara(text) {
  return new Paragraph({ spacing: { before: 80, after: 80 }, shading: { fill: "FFFDE7", type: ShadingType.CLEAR }, children: [new TextRun({ text: "说明：" + text, font: "Microsoft YaHei", size: 20, color: "666666" })] });
}
function twoColumnTable(rows, widths = [28, 72]) {
  return new Table({ width: { size: 100, type: WidthType.PERCENTAGE }, rows: rows.map(([k, v]) => new TableRow({ children: [cell(k, { width: widths[0], shading: LIGHT_GRAY, bold: true }), cell(v, { width: widths[1] })] })), borders });
}
function headerTable(headers, rows) {
  return new Table({ width: { size: 100, type: WidthType.PERCENTAGE }, rows: [new TableRow({ children: headers.map(h => cell(h, { shading: BLUE, bold: true, color: "FFFFFF" })) }), ...rows.map(r => new TableRow({ children: r.map(c => cell(c)) }))], borders });
}
function menuTable(rows) {
  return new Table({ width: { size: 100, type: WidthType.PERCENTAGE }, rows: [new TableRow({ children: [cell("子菜单", { shading: BLUE, bold: true, color: "FFFFFF", width: 22 }), cell("功能说明", { shading: BLUE, bold: true, color: "FFFFFF", width: 78 })] }), ...rows.map(([m, d]) => new TableRow({ children: [cell(m, { bold: true }), cell(d)] }))], borders });
}

// 封面
const cover = [
  new Paragraph({ spacing: { before: 2400 }, children: [] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 400 }, children: [new TextRun({ text: "北辰 ERP", bold: true, size: 72, color: BLUE, font: "Microsoft YaHei" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new TextRun({ text: "系统架构与业务说明文档", size: 44, color: DARK_GRAY, font: "Microsoft YaHei" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 }, children: [new TextRun({ text: "V3.0", size: 28, color: "666666", font: "Microsoft YaHei" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: `生成日期：${new Date().toLocaleDateString("zh-CN")}`, size: 24, color: "666666", font: "Microsoft YaHei" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: "状态：正式发布版", size: 24, color: "666666", font: "Microsoft YaHei" })] }),
  pageBreak(),
];

// 修订记录
const revision = [
  h2("文档修订记录"),
  headerTable(["版本", "日期", "修订内容"], [
    ["V1.0", "2026-08-11", "初始版本：模块说明与业务流程文字版"],
    ["V1.1", "2026-08-11", "补充业务流程表格与模块细化"],
    ["V2.0", "2026-08-11", "新增真实业务流程图，业务流程大白话重写"],
    ["V3.0", new Date().toLocaleDateString("zh-CN"), "按最新代码重做：每个模块业务流程 + 每个子菜单功能说明，覆盖全部9大板块"],
  ]),
  emptyLine(),
  notePara("本版重点改进：依据最新代码中的菜单配置（DataInitializer）完整梳理了9大业务板块的所有子菜单，逐一说明功能，并配以业务流程图。"),
  pageBreak(),
];

// 目录
const toc = [
  new TableOfContents("目录", { hyperlink: true, headingStyleRange: "1-3", stylesWithLevels: [{ styleId: "Heading1", level: 0 }, { styleId: "Heading2", level: 1 }, { styleId: "Heading3", level: 2 }] }),
  new Paragraph({ spacing: { before: 300, after: 200 }, children: [new TextRun({ text: "（提示：打开文档后，右键本页目录 → 更新域 → 更新整个目录，即可显示完整页码。）", color: "C00000", size: 20, font: "Microsoft YaHei" })] }),
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
  h2("1.2 系统整体结构"),
  p("系统按业务板块划分为 9 大模块，每个板块下挂若干子菜单，对应一项具体的业务功能。完整菜单结构如下："),
  menuTable([
    ["首页", "系统工作台，展示关键经营概览数据"],
    ["基础数据", "产品管理、品牌管理、BOM类型、阶段模板——业务运行所需的主数据"],
    ["研发管理", "研发项目、BOM管理、图纸文档、研发物料管理"],
    ["委外加工", "委外加工单、委外物料订单、物料信息管理、委外仓库、加工合同模板、物料收发单、物料其他出入库、委外退货、供应商管理、自有物料仓"],
    ["进货业务", "成品采购单、成品退货单、供应商管理"],
    ["销售业务", "销售单、客户管理、销售退货单、收费售后"],
    ["成品库存业务", "成品库存、成品仓库管理、成品库存流水、成品其他出入库、成品品质重分类、成品移仓单"],
    ["财务管理", "应收管理、应付管理、账单生成、资金流水、收款管理、付款管理"],
    ["设置", "智能管理、用户管理、权限管理、系统信息、数据管理、角色管理、菜单管理、清空数据"],
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

// 二、核心业务流程总览
sections.push(
  h1("二、核心业务流程总览"),
  p("先放一张总图，让大家对公司业务在系统里怎么跑起来有个整体印象。"),
  imagePara("fig1-overview.png", { width: 560, height: 160 }),
  p("", { bold: true }),
  p("业务主线只有一条：研发设计产品 → 采购物料 → 发给代工厂加工 → 收回成品入库 → 销售发货 → 财务收钱付款。"),
  p("每条线上都有系统自动生成的单据和记录，环环相扣，避免人工漏记、错记。"),
  h2("2.1 单据通用状态流转（所有单据通用）"),
  imagePara("fig7-status.png", { width: 560, height: 130 }),
  p("通俗理解：任何单据都从「草稿」开始，审核后正式生效并触发库存/财务变动；出错可「反审核」退回或「作废」作废。这与传统纸质单据的签批流程一致，便于管理层理解。"),
  pageBreak()
);

// 三、各模块业务流程与子菜单功能
const moduleSecs = [];

// 基础数据
moduleSecs.push(
  h1("三、基础数据模块"),
  p("基础数据是系统的「字典」，是所有业务模块引用的主数据。先把这些维护好，后面的采购、销售、研发才能正常关联。"),
  h2("3.1 业务流程"),
  p("基础数据不直接产生库存或资金变动，但它是所有业务单据的「源头」：在录入采购单、销售单、BOM 前，必须先在这里建立产品、品牌、BOM类型等主数据，后续单据只需选择，无需重复输入。"),
  h2("3.2 子菜单功能"),
  menuTable([
    ["产品管理", "维护成品/物料主数据（名称、规格、单位、分类等），是采购、销售、库存的基础对象"],
    ["品牌管理", "维护品牌主数据，销售和客户可按品牌归类管理"],
    ["BOM类型", "维护物料分类（如玻璃、驱动IC、盖板等），委外物料和研发BOM按此分类"],
    ["阶段模板", "维护研发项目阶段模板（如EVT/DVT/PVT），新建研发项目时可一键套用"],
  ]),
  pageBreak()
);

// 研发管理
moduleSecs.push(
  h1("四、研发管理模块"),
  p("研发管理是业务的起点。系统用项目化的方式管理从立项到量产前的全过程。"),
  h2("4.1 业务流程"),
  p("新建研发项目（套用阶段模板生成时间线）→ 维护 BOM 用料清单（按 BOM类型分类）→ 上传图纸文档 → 记录研发过程中的缺陷 → 管理研发专用物料。项目完成后可关闭或取消。"),
  imagePara("fig2-outsource.png", { width: 560, height: 260 }),
  p("说明：研发输出的 BOM 会直接作为委外加工和采购的用料依据，是连接研发与生产的关键纽带。"),
  h2("4.2 子菜单功能"),
  menuTable([
    ["研发项目", "管理研发项目全生命周期：基本信息、适配机型、阶段时间线、项目状态（进行中/关闭/取消）"],
    ["BOM管理", "维护物料清单，按 BOM类型分组，记录单套用量、损耗率、版本"],
    ["图纸文档", "管理项目关联的设计图纸、技术文档，支持版本控制"],
    ["研发物料管理", "管理研发专用物料（可关联项目，也可独立管理），记录存放位置与品质"],
  ]),
  pageBreak()
);

// 委外加工
moduleSecs.push(
  h1("五、委外加工模块"),
  p("委外加工是北辰 ERP 最核心、最复杂的业务板块。屏幕总成加工企业通常自己负责研发和接单，把生产环节委托给外协工厂。"),
  h2("5.1 业务流程"),
  p("研发输出 BOM → 创建委外物料订单向供应商买料 → 通过物料收发单把料发给工厂/从工厂收料 → 创建委外加工单委托生产 → 工厂交货（按品质等级入库）→ 有质量问题走售后/退货 → 财务结算料款与加工费。"),
  imagePara("fig2-outsource.png", { width: 560, height: 260 }),
  p("", { bold: true }),
  p("通俗理解：公司出设计，买材料发给代工厂，工厂加工完交回成品（验品质分A/B/C/不良），最后和供应商算材料款、和工厂算加工费。"),
  h2("5.2 子菜单功能"),
  menuTable([
    ["委外加工单", "管理委外加工订单，状态流转：待确认→生产中→已完成/已取消"],
    ["委外物料订单", "向供应商采购委外所需物料，状态：待确认→收货中→已完成/已取消"],
    ["物料信息管理", "维护委外加工涉及的物料主数据，支持BOM组件嵌套、关联供应商"],
    ["委外仓库", "管理外协工厂的库存，可查询委外仓库的库存流水"],
    ["加工合同模板", "维护委外加工合同模板，便于快速生成标准合同"],
    ["物料收发单", "管理发料给工厂/从工厂收料，区分良品/不良品，支持维修返还与折现退款"],
    ["物料其他出入库", "委外场景下的非标出入库（如盘点调整、报废）"],
    ["委外退货", "退回加工成品给工厂"],
    ["供应商管理", "维护委外业务相关的供应商（方案商/加工厂/辅料商）"],
    ["自有物料仓", "管理公司自有物料仓库（区别于委外工厂仓）"],
  ]),
  pageBreak()
);

// 进货业务
moduleSecs.push(
  h1("六、进货业务模块"),
  p("进货业务处理公司向供应商采购成品/原材料，以及对应的退货。"),
  h2("6.1 业务流程"),
  p("创建成品采购单 → 审核 → 货到做采购入库（库存增加）→ 生成应付；如有问题做成品退货单（库存减少、应付冲减）。"),
  imagePara("fig3-purchase.png", { width: 560, height: 140 }),
  p("通俗理解：采购下「成品采购单」→ 领导审核 → 货到验收做入库，库存自动增加，同时系统记下欠供应商的钱（应付）。"),
  h2("6.2 子菜单功能"),
  menuTable([
    ["成品采购单", "向供应商采购成品的订单，审核后作为入库依据，自动生成应付"],
    ["成品退货单", "向供应商退回成品，审核后库存减少、应付相应冲减"],
    ["供应商管理", "维护进货业务相关的供应商主数据及分类"],
  ]),
  pageBreak()
);

// 销售业务
moduleSecs.push(
  h1("七、销售业务模块"),
  p("销售业务是公司的收入来源，流程与进货相反。"),
  h2("7.1 业务流程"),
  p("客户下销售单 → 审核时检查库存是否充足 → 做销售出库（库存减少）→ 生成应收；客户退货做销售退货单（库存增加、应收冲减）。"),
  imagePara("fig4-sale.png", { width: 560, height: 140 }),
  p("通俗理解：客户下「销售单」→ 审核时系统先看仓库有没有货 → 发货出库，库存扣减，同时记客户欠公司的钱（应收）。"),
  h2("7.2 子菜单功能"),
  menuTable([
    ["销售单", "向客户销售成品的订单，审核时校验库存，出库后生成应收"],
    ["客户管理", "维护客户主数据，可关联品牌"],
    ["销售退货单", "客户退回成品，审核后库存增加、应收相应冲减"],
    ["收费售后", "处理销售后的收费售后业务（如维修收费）"],
  ]),
  pageBreak()
);

// 成品库存业务
moduleSecs.push(
  h1("八、成品库存业务模块"),
  p("成品库存业务是公司货物的「大本营」，所有库存变动都在这里汇总和查询。"),
  h2("8.1 业务流程"),
  p("库存是各业务的交汇点：采购入库使其增加、销售出库使其减少、移仓使其在仓库间转移、其他出入库处理盘点/报废、品质重分类处理等级互转。每次变动都记录库存流水。"),
  imagePara("fig5-inventory.png", { width: 560, height: 170 }),
  p("通俗理解：库存像一本账，每次变动都要登记原因和对应单据，随时可查「为什么变了、变了多少」。"),
  h2("8.2 子菜单功能"),
  menuTable([
    ["成品库存", "实时查询各仓库、各品质等级的成品库存数量"],
    ["成品仓库管理", "维护自有成品仓库（名称、地址、联系人等）"],
    ["成品库存流水", "查看每次库存变动的原因、单据号、前后数量，完整审计"],
    ["成品其他出入库", "非标出入库操作（盘点调整、报废等）"],
    ["成品品质重分类", "同一仓库内产品在不同品质等级（A/B/C/不良）间转换"],
    ["成品移仓单", "仓库间调拨，审核后自动更新两个仓库库存"],
  ]),
  pageBreak()
);

// 财务管理
moduleSecs.push(
  h1("九、财务管理模块"),
  p("财务管理是业务的最后一环，核心是把「应收应付」管清楚、把钱收回来付出去。它和业务单据自动联动，减少人工重复录入。"),
  h2("9.1 业务流程"),
  p("采购入库审核自动生成应付 → 销售出库审核自动生成应收 → 按供应商/客户汇总生成账单 → 填收款单/付款单冲账 → 每笔资金记入流水 → 清算看板可视化对账。"),
  imagePara("fig6-finance.png", { width: 560, height: 170 }),
  p("通俗理解：买货欠供应商（应付）、卖货客户欠我（应收）→ 汇总成账单 → 付款/收款冲账 → 每笔都留记录 → 看板一眼看清谁欠多少、还了多少。"),
  h2("9.2 子菜单功能"),
  menuTable([
    ["应收管理", "管理客户应收账款（来源：销售出库），跟踪催收状态"],
    ["应付管理", "管理供应商应付账款（来源：采购入库），跟踪付款状态"],
    ["账单生成", "按客户/供应商汇总应收应付，生成结算账单"],
    ["资金流水", "记录所有资金变动（收款/付款），完整资金审计链路"],
    ["收款管理", "记录向客户收款，关联应收账单，状态：草稿→已审核"],
    ["付款管理", "记录向供应商付款，关联应付账单，状态：草稿→已审核"],
  ]),
  pageBreak()
);

// 设置
moduleSecs.push(
  h1("十、设置模块（系统管理）"),
  p("设置模块保障系统安全、可控、可维护，是系统运行的基础。"),
  h2("10.1 业务流程"),
  p("管理员在「用户管理」创建账号 → 在「角色管理」定义角色并分配「权限管理」中的菜单权限 → 账号绑定角色后即获得对应菜单访问权。系统信息展示运行参数，数据管理用于备份/重置数据。"),
  h2("10.2 子菜单功能"),
  menuTable([
    ["智能管理", "系统智能化相关功能（如数据看板配置、智能提示等）"],
    ["用户管理", "维护系统用户账号（多公司、密码加密、逻辑删除）"],
    ["权限管理", "基于角色分配菜单权限（RBAC模型），控制谁能看哪些菜单"],
    ["系统信息", "查看系统运行信息（版本、环境等）"],
    ["数据管理", "系统数据维护（备份、导出等）"],
    ["角色管理", "维护角色（超管/管理员/普通用户），分配菜单权限"],
    ["菜单管理", "维护系统菜单结构（与DataInitializer初始化菜单对应）"],
    ["清空数据", "清空业务数据（用于演示或重置，谨慎使用）"],
  ]),
  pageBreak()
);

// 十一、系统关键设计
sections.push(
  h1("十一、系统关键设计"),
  p("为了让系统稳定、安全、可扩展，我们在设计上做了以下关键选择："),
  headerTable(["设计点", "说明"], [
    ["多公司隔离", "CompanyContext + MyBatis-Plus 多租户插件，不同公司数据完全隔离"],
    ["权限控制", "RBAC模型：用户-角色-菜单，前端路由守卫 + 后端 Sa-Token 注解双重校验"],
    ["数据安全", "密码 BCrypt 加密；业务单据用 status 状态管理生命周期；用户表使用逻辑删除"],
    ["库存安全", "所有库存变动加 @Transactional 事务，避免部分成功导致账实不符"],
    ["审计链路", "库存流水 + 资金流水 + 物料收发单强关联，追溯到原始单据"],
    ["存 ID 不存名", "明细表只存关联 ID，展示时关联查询名称，避免数据冗余和不一致"],
    ["平滑升级", "DataInitializer 启动时检测表/列是否存在，支持旧库无停机升级"],
  ]),
  pageBreak()
);

// 十二、后续规划
sections.push(
  h1("十二、后续规划"),
  bullet("报表中心：采购分析、销售分析、库存周转、利润报表"),
  bullet("审批流：关键单据支持自定义审批流程"),
  bullet("移动端：仓库收发货、审批消息移动端处理"),
  bullet("供应链协同：与供应商/客户系统对接，自动同步订单和库存"),
  emptyLine(),
  p("以上就是北辰 ERP 的系统架构与业务说明（V3.0）。如需进一步细化某个模块或补充更多流程图，请随时提出。"),
);

const doc = new Document({
  title: "北辰ERP系统架构与业务说明文档",
  subject: "北辰ERP系统架构、业务流程、模块与子菜单说明",
  creator: "北辰ERP项目组",
  description: "面向领导的北辰ERP系统说明文档，含每个模块业务流程与子菜单功能",
  styles: {
    default: { document: { run: { font: "Microsoft YaHei", size: 22 }, paragraph: { spacing: { line: 360, after: 120 } } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", run: { font: "Microsoft YaHei", bold: true, size: 32, color: BLUE }, paragraph: { spacing: { before: 240, after: 120 } } },
      { id: "Heading2", name: "Heading 2", run: { font: "Microsoft YaHei", bold: true, size: 28, color: BLUE }, paragraph: { spacing: { before: 200, after: 100 } } },
      { id: "Heading3", name: "Heading 3", run: { font: "Microsoft YaHei", bold: true, size: 24, color: DARK_GRAY }, paragraph: { spacing: { before: 160, after: 80 } } },
    ],
  },
  numbering: { config: [{ reference: "bullets", levels: [{ level: 0, format: "bullet", text: "•", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] }] },
  sections: [{
    properties: { page: { margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } } },
    headers: { default: new Header({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: "北辰 ERP 系统架构与业务说明文档", color: "999999", size: 18, font: "Microsoft YaHei" })] })] }) },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: "第 ", size: 18, font: "Microsoft YaHei" }), new TextRun({ children: [PageNumber.CURRENT], size: 18 }), new TextRun({ text: " 页 / 共 ", size: 18, font: "Microsoft YaHei" }), new TextRun({ children: [PageNumber.TOTAL_PAGES], size: 18 }), new TextRun({ text: " 页", size: 18, font: "Microsoft YaHei" })] })] }) },
    children: [...cover, ...revision, ...toc, ...sections, ...moduleSecs],
  }],
});

const outPath = path.join(__dirname, "北辰ERP系统架构与业务说明文档.docx");
Packer.toBuffer(doc).then(buf => { fs.writeFileSync(outPath, buf); console.log("文档已生成:", outPath, "大小:", (buf.length / 1024).toFixed(1) + "KB"); });
