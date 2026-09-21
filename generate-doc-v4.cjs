const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, AlignmentType, HeadingLevel, BorderStyle, WidthType,
  TableOfContents, PageNumber, PageBreak, ImageRun, ShadingType
} = require("docx");

const BLUE = "2E75B6", LIGHT_GRAY = "F2F2F2", DARK_GRAY = "404040", BORDER_COLOR = "CCCCCC";
const border = { style: BorderStyle.SINGLE, size: 1, color: BORDER_COLOR };
const borders = { top: border, bottom: border, left: border, right: border };
const cellMargins = { top: 60, bottom: 60, left: 100, right: 100 };
const IMG_DIR = path.join(__dirname, "diagrams", "png");

const h1 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun({ text: t, bold: true, size: 32, color: BLUE, font: "Microsoft YaHei" })] });
const h2 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun({ text: t, bold: true, size: 28, color: BLUE, font: "Microsoft YaHei" })] });
const h3 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun({ text: t, bold: true, size: 24, color: DARK_GRAY, font: "Microsoft YaHei" })] });
const p = (t, o = {}) => new Paragraph({ spacing: { after: 120, line: 360 }, children: [new TextRun({ text: t, font: "Microsoft YaHei", size: 22, ...o })] });
const bullet = (t) => new Paragraph({ numbering: { reference: "bullets", level: 0 }, spacing: { after: 60, line: 340 }, children: [new TextRun({ text: t, font: "Microsoft YaHei", size: 22 })] });
const emptyLine = () => new Paragraph({ spacing: { after: 60 }, children: [] });
const pageBreak = () => new Paragraph({ children: [new PageBreak()] });

function cell(text, opts = {}) {
  return new TableCell({
    children: [new Paragraph({ children: [new TextRun({ text, bold: !!opts.bold, font: "Microsoft YaHei", size: 20, color: opts.color || DARK_GRAY })], alignment: opts.align || AlignmentType.LEFT })],
    width: opts.width ? { size: opts.width, type: WidthType.PERCENTAGE } : undefined,
    shading: opts.shading ? { fill: opts.shading, type: ShadingType.CLEAR } : undefined,
    verticalAlign: "center", margins: cellMargins, borders,
  });
}
const imagePara = (file, w, hh) => new Paragraph({
  spacing: { before: 120, after: 120 }, alignment: AlignmentType.CENTER,
  children: [new ImageRun({ data: fs.readFileSync(path.join(IMG_DIR, file)), transformation: { width: w || 560, height: hh || 160 }, type: "png" })],
});
const notePara = (t) => new Paragraph({ spacing: { before: 80, after: 80 }, shading: { fill: "FFFDE7", type: ShadingType.CLEAR }, children: [new TextRun({ text: "说明：" + t, font: "Microsoft YaHei", size: 20, color: "666666" })] });
const menuTable = (rows) => new Table({
  width: { size: 100, type: WidthType.PERCENTAGE },
  rows: [new TableRow({ children: [cell("子菜单", { shading: BLUE, bold: true, color: "FFFFFF", width: 24 }), cell("功能说明", { shading: BLUE, bold: true, color: "FFFFFF", width: 76 })] }),
  ...rows.map(([m, d]) => new TableRow({ children: [cell(m, { bold: true }), cell(d)] }))],
  borders,
});
const headerTable = (headers, rows) => new Table({
  width: { size: 100, type: WidthType.PERCENTAGE },
  rows: [new TableRow({ children: headers.map(h => cell(h, { shading: BLUE, bold: true, color: "FFFFFF" })) }),
  ...rows.map(r => new TableRow({ children: r.map(c => cell(c)) }))],
  borders,
});
const twoCol = (rows, w0 = 28) => new Table({
  width: { size: 100, type: WidthType.PERCENTAGE },
  rows: rows.map(([k, v]) => new TableRow({ children: [cell(k, { width: w0, shading: LIGHT_GRAY, bold: true }), cell(v, { width: 100 - w0 })] })),
  borders,
});

const cover = [
  new Paragraph({ spacing: { before: 2400 }, children: [] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 400 }, children: [new TextRun({ text: "北辰 ERP", bold: true, size: 72, color: BLUE, font: "Microsoft YaHei" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new TextRun({ text: "系统架构与业务说明文档", size: 44, color: DARK_GRAY, font: "Microsoft YaHei" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 }, children: [new TextRun({ text: "V4.0", size: 28, color: "666666", font: "Microsoft YaHei" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: `生成日期：${new Date().toLocaleDateString("zh-CN")}`, size: 24, color: "666666", font: "Microsoft YaHei" })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: "状态：正式发布版", size: 24, color: "666666", font: "Microsoft YaHei" })] }),
  pageBreak(),
];

const revision = [
  h2("文档修订记录"),
  headerTable(["版本", "日期", "修订内容"], [
    ["V1.0", "2026-08-11", "初始版本：模块说明与业务流程文字版"],
    ["V2.0", "2026-08-11", "新增业务流程图，业务流程大白话重写"],
    ["V3.0", "2026-08-14", "每个模块业务流程 + 每个子菜单功能说明"],
    ["V4.0", new Date().toLocaleDateString("zh-CN"), "依据 8/15–8/28 最新代码重做：新增销售换货、退货整理两大模块；委外加工重组为11个子菜单；基础数据新增客户/供应商/供货商；补充委外双模式、结单报表、品质待整理等新业务"],
  ]),
  emptyLine(),
  notePara("本版依据最新代码（DataInitializer 菜单配置 + 全部 Controller + 前端路由）完整重做，覆盖 9 大板块、58 个菜单项，并新增退货整理、销售换货两张业务流程图。"),
  pageBreak(),
];

const toc = [
  new TableOfContents("目录", { hyperlink: true, headingStyleRange: "1-3", stylesWithLevels: [{ styleId: "Heading1", level: 0 }, { styleId: "Heading2", level: 1 }, { styleId: "Heading3", level: 2 }] }),
  new Paragraph({ spacing: { before: 300, after: 200 }, children: [new TextRun({ text: "（提示：打开文档后，右键本页目录 → 更新域 → 更新整个目录，即可显示完整页码。）", color: "C00000", size: 20, font: "Microsoft YaHei" })] }),
  pageBreak(),
];

const S = [];

// 一、项目概述
S.push(
  h1("一、项目概述"),
  h2("1.1 北辰 ERP 是什么"),
  p("北辰 ERP 是一款面向屏幕总成加工行业的企业资源管理系统。它把公司核心业务——研发设计、物料采购、委外加工、成品入库、销售出库、售后退换、财务收付款——全部串成一条线，做到每一笔钱、每一件货都有据可查。"),
  p("系统解决三个核心问题：", { bold: true }),
  bullet("货在哪里：实时知道每个仓库有什么产品、多少数量、什么品质等级。"),
  bullet("钱在哪里：清楚每单采购欠供应商多少、每笔销售客户欠多少、已收已付多少。"),
  bullet("事到哪里：研发项目有没有延期、委外加工进度如何、单据有没有审核、售后退货是否超期。"),
  h2("1.2 系统板块总览"),
  p("系统共 9 大板块、58 个菜单项，覆盖企业日常经营全流程："),
  menuTable([
    ["首页", "系统工作台，展示关键经营概览数据"],
    ["基础数据", "客户管理、产品管理、品牌管理、供货商管理、供应商管理、物料类型管理、物料信息、模版管理（8项）"],
    ["研发管理", "研发立项、BOM管理、图纸文档、研发物料管理（4项）"],
    ["委外加工", "加工订单、物料订单、物料收发单、物料其他出入库、加工退货、物料退货、委外仓库、自有物料仓、供应商管理（10项；物料信息已迁「基础数据」，加工合同模板已并入「模版管理」）"],
    ["进货业务", "成品采购单、采购退货单、采购换货单（3项；供货商管理已并入「基础数据」，2026-09-18 去重）"],
    ["销售业务", "销售单、销售退单、销售换货单（3项；客户管理已并入「基础数据」，退货整理已移到「成品库存」，2026-09-18）"],
    ["成品库存", "成品移仓单、退货整理、成品库存情况、成品库存流水、库存盘点、成品报损、成品其他出入库、成品品质重分类、成品仓库管理（9项，2026-09-18 按用户序列重排；退货整理自「销售业务」迁入、「成品库存」查询页已于当日下线并入「成品库存情况」）"],
    ["财务管理", "账单生成、收款管理、付款管理、费用管理、应收管理、应付管理、资金流水、账户管理、发票管理、应付转应收（10项，2026-09-18 按用户序列重排；807 同日由「资金账户」改名为「账户管理」）"],
    ["设置", "智能管理、用户管理、权限管理、系统信息、数据管理、角色管理、菜单管理、清空数据（8项）"],
  ]),
  h2("1.3 技术栈"),
  twoCol([
    ["前端", "Vue3 + Element Plus + Vite"],
    ["后端", "SpringBoot 3.2.5 + Java 21 + MyBatis-Plus 3.5.5 + Sa-Token"],
    ["数据库", "MySQL 8"],
    ["部署", "前后端分离，后端端口 8080（前缀 /api），前端端口 5173"],
  ]),
  pageBreak()
);

// 二、核心业务流程总览
S.push(
  h1("二、核心业务流程总览"),
  h2("2.1 整体业务全景"),
  imagePara("fig1-overview.png", 560, 160),
  p("业务主线：研发设计产品 → 采购/委外买料 → 发料给代工厂 → 收回成品入库 → 销售发货 → 售后退换 → 财务收钱付款。"),
  p("每条线上都有系统自动生成的单据和记录，环环相扣，避免人工漏记、错记。"),
  h2("2.2 单据通用状态流转（全系统统一）"),
  imagePara("fig7-status.png", 560, 130),
  p("通俗理解：任何单据都从「草稿」开始，审核后正式生效并触发库存/财务变动；出错可「反审核」退回草稿修正，或「作废」弃用。全系统所有单据（采购、销售、委外、库存、收付款、退货、换货、整理）均统一采用这套流程。"),
  h2("2.3 品质等级体系"),
  p("成品库存按品质等级精细管理，不同等级用途与价格不同："),
  headerTable(["品质", "含义", "典型去向"], [
    ["A规（A）", "最优品质", "正常销售"],
    ["B规（B）", "良品，有轻微瑕疵", "降价销售"],
    ["C规（C）", "次品，瑕疵明显", "低价处理"],
    ["不良品（DEFECT）", "不可用", "退供应商/报废"],
    ["待整理（PENDING）", "刚退回尚未检验分选", "暂存售后仓，等待退货整理"],
  ]),
  pageBreak()
);

// 三、基础数据
S.push(
  h1("三、基础数据模块"),
  p("基础数据是系统的「字典」，所有业务模块都要引用。先维护好这里，后面的采购、销售、研发才能正常关联。"),
  h2("3.1 业务流程"),
  p("基础数据不直接产生库存或资金变动，但它是所有单据的「源头」：录入采购单、销售单、BOM 前，必须先在此建立产品、客户、供应商等主数据，后续单据只需选择，无需重复输入。"),
  p("供应商与供货商的区别：", { bold: true }),
  bullet("供应商：提供加工服务或物料的一方，分方案商、加工厂、辅料商三类。"),
  bullet("供货商：直接提供成品的一方，进货业务向其采购成品。"),
  h2("3.2 子菜单功能"),
  menuTable([
    ["客户管理", "维护客户主数据（名称、联系人、关联品牌等），销售单、销售退货单、换货单的引用对象"],
    ["供应商管理", "维护方案商/加工厂/辅料商三类供应商，新建供应商时系统自动为其创建委外仓库"],
    ["供货商管理", "维护成品供货商，进货业务（成品采购单）的采购对象"],
    ["物料类型管理", "维护物料分类（玻璃/驱动IC/触摸IC/码片IC/排线/盖板/背贴/钢板/COP 共9类），9类为系统默认不可删除"],
    ["产品管理", "维护成品主数据（名称、规格、单位、品牌等），可按品牌筛选"],
    ["品牌管理", "维护品牌主数据，产品、研发项目、客户均可关联品牌"],
    ["阶段模板管理", "配置研发项目阶段模板（如立项/打样/小批量/结项），新建项目时一键套用，可设置是否同步产品状态"],
  ]),
  pageBreak()
);

// 四、研发管理
S.push(
  h1("四、研发管理模块"),
  p("研发管理是业务的起点。系统用项目化的方式管理从立项到量产前的全过程。"),
  h2("4.1 业务流程"),
  p("新建研发项目（套用阶段模板自动生成项目阶段）→ 填写原机配置与改配信息（改配自动同步至 BOM）→ 维护 BOM 用料清单 → 上传图纸文档 → 记录研发缺陷 → 管理研发专用物料 → 项目结项后同步产品状态为「正常」。" ),
  p("改配信息与 BOM 双向同步：", { bold: true }),
  bullet("驱动 IC 作为 BOM 独立行。"),
  bullet("触摸 IC、码片 IC 挂在「排线」物料的子物料上（排线物料缺失时按「排线-项目名」自动创建）。"),
  bullet("修改改配信息会自动更新 BOM；反之 BOM 变更也会回写到改配信息，始终保持两边一致。"),
  h2("4.2 子菜单功能"),
  menuTable([
    ["研发项目", "管理项目全生命周期：基本信息、品牌、原机配置（原机尺寸/分辨率/驱动IC/触摸IC）、改配信息（玻璃尺寸/分辨率/驱动IC/触摸IC/码片IC）、项目阶段、状态（进行中/已结项/已取消）"],
    ["BOM管理", "维护物料清单（BOM），按 物料类型分组，记录单套用量、损耗率、版本，支持版本管理"],
    ["图纸文档", "管理项目关联的设计图纸与技术文档，支持版本控制与上传下载"],
    ["研发物料管理", "管理研发专用物料（可关联项目，也可独立管理），记录类型、存放位置与位置详情"],
  ]),
  pageBreak()
);

// 五、委外加工
S.push(
  h1("五、委外加工模块"),
  p("委外加工是北辰 ERP 最核心、最复杂的业务板块。公司负责研发和接单，把生产环节委托给外协工厂。"),
  h2("5.1 业务流程"),
  imagePara("fig2-outsource.png", 560, 260),
  p("通俗理解：公司出设计 → 买材料发给代工厂 → 工厂加工 → 收回成品验收入库（按 A/B/C/不良 分级）→ 结单核算用料与超损 → 财务结算料款与加工费。"),
  h2("5.2 两种加工模式"),
  headerTable(["模式", "说明", "结算方式"], [
    ["来料加工（我方供料）", "我方提供全部物料，工厂只负责加工", "只结算加工费"],
    ["包工包料（工厂供料）", "工厂给出一口价总价（含加工与包料）", "按「交货数量 × 折算单价」结算，折算单价 = 总价 ÷ 数量"],
  ]),
  h2("5.3 结单与超损"),
  bullet("加工完成必须走「结单流程」，结单时生成结单报表：统计用料总数、退料、超损数量与超损总价。"),
  bullet("超损（用料超过标准）自动生成负应付，从加工费中扣减。"),
  bullet("结单时须选择退回仓库，剩余物料退回该仓（不能是工厂委外仓）。"),
  bullet("结单填错可「反结单」，系统自动冲回应付、回退库存，订单恢复为生产中。"),
  h2("5.4 子菜单功能"),
  menuTable([
    ["加工订单", "管理委外加工订单（原「委外加工单」）：支持来料加工/包工包料双模式、BOM 物料清单、交货记录、退不良（按 A/B/C/不良 分规格退回）、结单报表"],
    ["物料订单", "向供应商采购委外所需物料（原「委外物料订单」），状态：待审核→收货中→已完成，收货收满不自动完成，须手动「结单」"],
    ["物料信息", "维护委外物料主数据，含单价、物料类型、子物料嵌套（如排线挂触摸IC/码片IC）、供应商关联"],
    ["物料收发单", "管理发料给工厂 / 从工厂收料 / 调拨，分发料、收料、退料等类型，需审核后生效并影响库存"],
    ["物料其他出入库", "委外场景下的非标出入库（盘点调整、报废等），草稿→审核→反审核→作废"],
    ["加工退货", "委外加工成品退货（原「委外退货」），退回工厂加工的成品，需审核生效"],
    ["物料退货", "委外物料退货，把物料退回给物料商，不关联物料订单，允许负应付，需审核生效"],
    ["委外仓库", "管理外协工厂的仓库（新增供应商时自动创建），可查看物料库存与流水"],
    ["自有物料仓", "管理公司自有的物料仓库，与委外工厂仓区分"],
    ["供应商管理", "维护委外业务相关的供应商（方案商/加工厂/辅料商）"],
    ["加工合同模板", "维护委外加工合同模板，便于快速生成标准合同"],
  ]),
  pageBreak()
);

// 六、进货业务
S.push(
  h1("六、进货业务模块"),
  p("进货业务处理公司向供货商采购成品，以及向供货商退货、换货。"),
  h2("6.1 业务流程"),
  imagePara("fig3-purchase.png", 560, 140),
  p("通俗理解：采购下「成品采购单」→ 领导审核（库存自动增加并记应付）→ 货有问题做「采购退货单」（库存减少、应付冲减）或「采购换货单」（退回不良品 + 换回良品，一张单管一出一进）。采购退货、采购换货都可关联原采购单，系统自动校验数量不超过已购未退未换量。"),
  h2("6.2 子菜单功能"),
  menuTable([
    ["成品采购单", "向供货商采购成品的订单，审核后自动入库并生成应付账款；详情页可「发起退货」并查看该单退货情况"],
    ["采购退货单", "向供货商退回成品（原「成品退货单」），可关联原采购单并校验可退量，审核后库存减少、应付冲减"],
    ["采购换货单", "把采购的成品退回供货商换新（同品换货）：退回侧从成品仓按品质出库（默认不良品）并生成负向应付，换入侧收到供货商换回的良品入库并生成正向应付，两条台账净额即差价；可关联采购单并校验可换量（已购 − 已退 − 已换）"],
  ]),
  pageBreak()
);

// 七、销售业务
S.push(
  h1("七、销售业务模块"),
  p("销售业务是公司收入来源，同时涵盖售后退换，是本次版本变化最大的板块。"),
  h2("7.1 业务流程"),
  imagePara("fig4-sale.png", 560, 140),
  p("通俗理解：客户下「销售单」→ 审核（检查库存、生成应收）→ 销售出库（库存扣减）→ 后续发生退货走「销售退货单」，换货走「销售换货单」，收费维修走「收费售后」。"),
  h2("7.2 销售退货 → 售后仓 → 退货整理"),
  imagePara("fig8-return-sort.png", 560, 180),
  p("这是本次新增的重要链路：客户退回的货不再直接判定为不良，而是先入「售后仓」标记为「待整理」，再由仓库人员检验分选："),
  bullet("A规 / B规 / C规 → 入成品仓，可继续销售。"),
  bullet("不良品 → 入不良仓，退回供应商或报废。"),
  bullet("分选后的 B/C/不良 折损，可向客户收取折损款，系统自动生成应收。"),
  bullet("售后仓待整理库存停留超过 3 天会标红预警，提醒仓库及时处理。"),
  bullet("退货整理可追溯来源退货单（按退货日期先后 FIFO 推定），记录已整理数量。"),
  h2("7.3 销售换货"),
  imagePara("fig9-exchange.png", 560, 150),
  p("通俗理解：客户买了货想换同款（品质可以不同）→ 建「销售换货单」并关联原销售单 → 系统校验可换量（已售 − 已退 − 已换）→ 审核后退回的货入售后仓（待整理），换给客户的新货从成品仓扣减。支持多次部分换货，不处理差价。"),
  h2("7.4 子菜单功能"),
  menuTable([
    ["销售单", "向客户销售成品的订单，审核时校验库存并生成应收；列表点击行进入详情页，草稿可在详情页直接编辑"],
    ["销售退货单", "客户退回成品，可关联原销售单并校验可退量；审核后入售后仓（品质待整理）并生成负向应收；支持折损收款"],
    ["收费售后", "处理销售后的收费售后业务（如维修收费），不关联加工单"],
    ["销售换货单", "【新增】同品换货，强关联销售单；审核后退回入售后仓（待整理）、换出从成品仓扣减；可换量 = 已售 − 已退 − 已换"],
  ]),
  pageBreak()
);

// 八、成品库存
S.push(
  h1("八、成品库存模块"),
  p("成品库存是公司货物的「大本营」，所有库存变动都在这里汇总和查询。"),
  h2("8.1 业务流程"),
  imagePara("fig5-inventory.png", 560, 170),
  p("通俗理解：库存像一本账，每次变动都要登记原因和对应单据，随时可查「为什么变了、变了多少」。库存按「仓库 + 产品 + 品质等级」三维精确管理。"),
  h2("8.2 子菜单功能"),
  menuTable([
    ["成品移仓单", "仓库之间调拨，审核后自动更新两个仓库库存"],
    ["退货整理", "【新增】把售后仓的「待整理」库存分选为 A/B/C/不良，分别入成品仓与不良仓；支持追溯来源退货单、折损收款、超期预警"],
    ["成品库存情况", "按产品维度看跨仓库库存汇总（A规/B规/C规/不良/待整理），点行进详情看该产品在各仓库的分布"],
    ["成品库存流水", "查看每次库存变动的原因、单据号、变动前后数量，完整审计链路，可反查来源单据"],
    ["库存盘点", "每月每仓盘点一次，录入实盘数量后差异自动调整库存，仓库列表与盘点页显示待盘点/超期提醒"],
    ["成品报损", "破损/报废等损耗登记，审核后按 LOSS_OUT 流水扣减成品库存，可反审核回滚"],
    ["成品其他出入库", "非标出入库操作（盘点调整、报废等），草稿→审核→反审核→作废"],
    ["成品品质重分类", "同一仓库内产品在不同品质等级之间的转换（如 A规 降级为 B规）"],
    ["成品仓库管理", "维护自有仓库，分成品仓/不良仓/售后仓/辅料仓四种类型，记录地址与联系人"],
  ]),
  pageBreak()
);

// 九、财务管理
S.push(
  h1("九、财务管理模块"),
  p("财务管理是业务的最后一环，核心是把「应收应付」管清楚、把钱收回来付出去。它与业务单据自动联动，减少人工重复录入。"),
  h2("9.1 业务流程"),
  imagePara("fig6-finance.png", 560, 170),
  p("通俗理解：买货欠供应商（应付）、卖货客户欠我（应收）→ 按往来单位汇总成账单 → 付款/收款冲账 → 每笔资金记入流水 → 看板一眼看清谁欠多少、还了多少。"),
  h2("9.2 自动化记账规则"),
  headerTable(["业务动作", "自动生成"], [
    ["成品采购单审核", "应付账款（欠供应商）"],
    ["采购退货单审核", "负向应付（冲减欠款）"],
    ["采购换货单审核", "退回侧负向应付（冲减欠款）+ 换入侧正向应付，两条净额即差价"],
    ["销售单审核", "应收账款（客户欠款）"],
    ["销售退货单审核", "负向应收 + 折损正向应收"],
    ["委外加工交货", "应付账款（加工费）"],
    ["委外退不良", "负向应付（冲减加工费）"],
    ["委外结单超损", "负向应付（超损扣款）"],
    ["收款单/付款单审核", "资金流水 + 核销记录"],
  ]),
  h2("9.3 子菜单功能"),
  menuTable([
    ["账单生成", "按客户/供应商汇总应收应付生成结算账单，支持审核/反审核/作废，付款后自动更新已付金额"],
    ["收款管理", "记录向客户收款，关联应收账单自动核销，状态：草稿→已审核→已作废"],
    ["付款管理", "记录向供应商付款，关联应付账单自动核销；超额付款自动生成预付单（负数应付）"],
    ["费用管理", "登记办公/差旅/招待等费用，审核后扣减账户并生成「费用支出」流水，供利润分析取数；可反审核冲回"],
    ["应收管理", "管理客户应收账款，来源含销售单、销售退货、折损收款等，跟踪结清状态（未结清/部分/已结清）"],
    ["应付管理", "管理供应商应付账款，来源含采购单、采购退货、采购换货、委外交货、退不良、超损等，支持负数应付（预付/多付）"],
    ["资金流水", "记录所有资金变动（收款/付款/期初/冲正），余额实时累计计算，可按账户查询"],
    ["账户管理", "维护现金/银行/微信等收付款账户；开户时写入期初余额（落「期初」流水），实时余额 = 期初 + 收支流水累计（2026-09-18 由「资金账户」改名）"],
    ["发票管理", "登记销项/进项发票（税务口径），供税务分析的发票汇总取数"],
    ["应付转应收", "退货/超损扣款形成的负向应付在无货款可抵时，转为向该供货商收款（一张单双向冲抵）"],
  ]),
  pageBreak()
);

// 十、设置
S.push(
  h1("十、设置模块（系统管理）"),
  p("设置模块保障系统安全、可控、可维护。"),
  h2("10.1 业务流程"),
  p("管理员在「用户管理」创建账号 → 在「角色管理」定义角色并通过「权限管理」分配菜单权限 → 账号绑定角色后即获得对应菜单访问权。若某个人的可见页面需要与角色不同，可直接在「用户管理」里点该用户的「权限」，把权限模式切到「自定义」单独勾选页面（默认「跟随角色」）。系统信息展示运行参数，数据管理用于备份/重置数据。"),
  h2("10.2 子菜单功能"),
  menuTable([
    ["智能管理", "系统智能化相关功能入口"],
    ["用户管理", "维护系统用户账号（多公司隔离、密码加密、逻辑删除）；可在本页直接编辑**单个用户的页面权限**（跟随角色 / 自定义勾选），并配置其首页业务 TAB"],
    ["权限管理", "基于角色分配菜单权限（RBAC 模型），控制谁能看哪些菜单"],
    ["系统信息", "查看与配置系统运行参数"],
    ["数据管理", "整库导出（全库 JSON 备份；若有表读取失败会明确列出，不再静默少表）/ 数据导入（导入前**预检**：备份表数/空表清单/"将被清空的表"风险清单 + 二次确认；兼容「页面下载的备份」与「接口原始响应」两种文件）/ 清空本公司数据（系统数据不受影响）"],
    ["角色管理", "维护角色（超管/管理员/普通用户），为每个角色分配菜单权限"],
    ["菜单管理", "维护系统菜单结构（与系统初始化菜单配置对应）"],
    ["清空数据", "清空业务数据（用于演示或重置，谨慎使用）"],
  ]),
  pageBreak()
);

// 十一、关键设计
S.push(
  h1("十一、系统关键设计"),
  headerTable(["设计点", "说明"], [
    ["多公司隔离", "按 company_id 隔离，不同公司只能看到自己的数据"],
    ["权限控制", "RBAC 模型：用户 → 角色 → 菜单，前端路由守卫 + 后端 Sa-Token 双重校验"],
    ["单据状态统一", "全系统统一：草稿 → 审核 → 反审核 → 作废；业务中间态（生产中/收货中）保留"],
    ["枚举统一", "数据库存 code（英文），界面显示 label（中文），杜绝硬编码与魔法值"],
    ["存 ID 不存名", "业务单据只存关联 ID，展示时实时查名；财务单据保留名称留痕以便审计"],
    ["库存安全", "所有库存变动使用事务与原子 SQL，防止部分成功导致账实不符"],
    ["审计链路", "库存流水 + 资金流水 + 核销流水，完整追溯到原始单据"],
    ["余额实时化", "供应商/客户/账户余额均由台账实时汇总计算，不保留快照字段，消除数据漂移"],
    ["平滑升级", "系统启动时自动检测表/列是否存在并补齐，支持旧库无停机升级"],
    ["下拉实时查", "所有选择下拉框展开时实时查库，其他用户新增的数据立即可见"],
  ]),
  pageBreak()
);

// 十二、规划
S.push(
  h1("十二、后续规划"),
  bullet("报表中心：采购分析、销售分析、库存周转、利润分析"),
  bullet("审批流：关键单据支持自定义审批流程"),
  bullet("移动端：仓库收发货、审批消息移动端处理"),
  bullet("供应链协同：与供应商/客户系统对接，自动同步订单和库存"),
  emptyLine(),
  p("以上就是北辰 ERP 的系统架构与业务说明（V4.0）。如需进一步细化某个模块或补充流程图，请随时提出。"),
);

const doc = new Document({
  title: "北辰ERP系统架构与业务说明文档",
  subject: "北辰ERP系统架构、业务流程、模块与子菜单说明",
  creator: "北辰ERP项目组",
  description: "北辰ERP系统说明文档，含每个模块业务流程与子菜单功能",
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
    children: [...cover, ...revision, ...toc, ...S],
  }],
});

const outPath = path.join(__dirname, "北辰ERP系统架构与业务说明文档.docx");
Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync(outPath, buf);
  console.log("文档已生成:", outPath, "大小:", (buf.length / 1024).toFixed(1) + "KB");
});
