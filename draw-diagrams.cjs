// 使用 @napi-rs/canvas 绘制通俗易懂的业务流程图（中文），输出 PNG 嵌入 Word
const fs = require("fs");
const path = require("path");
const { createCanvas } = require("@napi-rs/canvas");

const FONT_PATH = "C:\\Windows\\Fonts\\msyh.ttc"; // 微软雅黑
const FONT_BOLD = "C:\\Windows\\Fonts\\msyhbd.ttc"; // 微软雅黑粗体
const OUT_DIR = path.join(__dirname, "diagrams", "png");
if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });

const COLORS = {
  green: "#4CAF50",
  blue: "#2196F3",
  orange: "#FF9800",
  purple: "#9C27B0",
  pink: "#E91E63",
  cyan: "#00BCD4",
  gray: "#607D8B",
  dark: "#404040",
  white: "#FFFFFF",
  line: "#787878",
};

function drawNode(ctx, x, y, w, h, text, color, sub) {
  const r = 8;
  ctx.fillStyle = color;
  roundRect(ctx, x, y, w, h, r);
  ctx.fill();
  ctx.fillStyle = COLORS.white;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  const lines = text.split("\n");
  const lineH = 24;
  let startY = y + h / 2 - ((lines.length - 1) * lineH) / 2;
  if (sub) startY -= 8;
  ctx.font = "20px 'Microsoft YaHei'";
  lines.forEach((ln, i) => ctx.fillText(ln, x + w / 2, startY + i * lineH));
  if (sub) {
    ctx.font = "13px 'Microsoft YaHei'";
    ctx.fillStyle = "#E6E6E6";
    ctx.fillText(sub, x + w / 2, startY + lines.length * lineH + 4);
  }
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

function drawArrow(ctx, x1, y1, x2, y2, label) {
  ctx.strokeStyle = COLORS.line;
  ctx.fillStyle = COLORS.line;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();
  const ang = Math.atan2(y2 - y1, x2 - x1);
  const len = 11;
  ctx.beginPath();
  ctx.moveTo(x2, y2);
  ctx.lineTo(x2 - len * Math.cos(ang - Math.PI / 7), y2 - len * Math.sin(ang - Math.PI / 7));
  ctx.lineTo(x2 - len * Math.cos(ang + Math.PI / 7), y2 - len * Math.sin(ang + Math.PI / 7));
  ctx.closePath();
  ctx.fill();
  if (label) {
    ctx.fillStyle = COLORS.dark;
    ctx.font = "13px 'Microsoft YaHei'";
    ctx.textAlign = "center";
    ctx.fillText(label, (x1 + x2) / 2, (y1 + y2) / 2 - 8);
  }
}

function drawTitle(ctx, text, w) {
  ctx.fillStyle = COLORS.dark;
  ctx.font = "bold 24px 'Microsoft YaHei'";
  ctx.textAlign = "left";
  ctx.textBaseline = "alphabetic";
  ctx.fillText(text, 40, 42);
}

function drawNote(ctx, text, x, y) {
  ctx.fillStyle = COLORS.dark;
  ctx.font = "15px 'Microsoft YaHei'";
  ctx.textAlign = "left";
  ctx.textBaseline = "alphabetic";
  ctx.fillText(text, x, y);
}

async function makeImage(filename, width, height, drawFn) {
  const canvas = createCanvas(width, height);
  const ctx = canvas.getContext("2d");
  ctx.fillStyle = "#FFFFFF";
  ctx.fillRect(0, 0, width, height);
  await drawFn(ctx, width, height);
  const buf = canvas.toBuffer("image/png");
  fs.writeFileSync(path.join(OUT_DIR, filename), buf);
  console.log("生成:", filename);
}

// ==================== 图1：整体业务全景 ====================
async function fig1() {
  await makeImage("fig1-overview.png", 1180, 340, async (ctx) => {
    drawTitle(ctx, "整体业务全景：从研发到收款的一条龙", 1180);
    const nodes = [
      { t: "① 研发立项", s: "设计产品/BOM", c: COLORS.green, x: 40 },
      { t: "② 采购物料", s: "买原料/委外料", c: COLORS.blue, x: 210 },
      { t: "③ 委外加工", s: "工厂代工生产", c: COLORS.orange, x: 380 },
      { t: "④ 成品入库", s: "验收入库", c: COLORS.orange, x: 550 },
      { t: "⑤ 销售发货", s: "客户下单发货", c: COLORS.pink, x: 720 },
      { t: "⑥ 财务结算", s: "收款付款对账", c: COLORS.cyan, x: 890 },
    ];
    const y = 130, w = 150, h = 70;
    nodes.forEach((n) => drawNode(ctx, n.x, n.y = y, w, h, n.t, n.c, n.s));
    for (let i = 0; i < nodes.length - 1; i++) {
      drawArrow(ctx, nodes[i].x + w, y + h / 2, nodes[i + 1].x, y + h / 2);
    }
    drawNote(ctx, "说明：研发部门先设计产品 → 采购/委外买来原材料 → 外协工厂代工 → 成品验收入库 → 销售卖给客户并发货 → 财务收款付款完成对账。", 40, 250);
    drawNote(ctx, "每一步都会在系统里留下单据和记录，环环相扣，确保库存、资金都清清楚楚。", 40, 280);
  });
}

// ==================== 图2：委外加工流程（核心） ====================
async function fig2() {
  await makeImage("fig2-outsource.png", 1180, 560, async (ctx) => {
    drawTitle(ctx, "委外加工业务流程（核心业务）", 1180);
    const steps = [
      { t: "研发输出BOM", s: "用料清单", c: COLORS.green, x: 40 },
      { t: "创建委外物料订单", s: "向供应商买料", c: COLORS.blue, x: 250 },
      { t: "物料收发单", s: "发料/收料", c: COLORS.blue, x: 460 },
      { t: "创建委外加工单", s: "委托工厂加工", c: COLORS.orange, x: 670 },
    ];
    const w = 200, h = 70;
    steps.forEach((n) => drawNode(ctx, n.x, n.y = 90, w, h, n.t, n.c, n.s));
    for (let i = 0; i < steps.length - 1; i++) {
      drawArrow(ctx, steps[i].x + w, steps[i].y + h / 2, steps[i + 1].x, steps[i].y + h / 2);
    }
    drawNode(ctx, 250, 200, 200, 40, "待确认→收货中→已完成", COLORS.gray);
    drawArrow(ctx, 350, 160, 350, 200);
    drawNode(ctx, 670, 200, 200, 40, "待确认→生产中→已完成", COLORS.gray);
    drawArrow(ctx, 770, 160, 770, 200);

    const row2 = [
      { t: "加工成品交货", s: "按品质等级入库", c: COLORS.orange, x: 460 },
      { t: "委外售后/退货", s: "质量问题处理", c: COLORS.purple, x: 670 },
      { t: "财务结算", s: "料款+加工费", c: COLORS.cyan, x: 880 },
    ];
    row2.forEach((n) => drawNode(ctx, n.x, n.y = 300, w, h, n.t, n.c, n.s));
    drawArrow(ctx, 770, 240, 560, 300);
    drawArrow(ctx, 660, 335, 670, 335);
    drawArrow(ctx, 660, 335, 880, 335);
    drawNote(ctx, "交货按品质分级：A级品（优）/ B级品（良）/ C级品（次）/ 不良品（退回）", 40, 430);
    drawNote(ctx, "通俗理解：公司出设计 → 买材料发给代工厂 → 工厂加工 → 收回成品验收入库 → 有质量问题走售后 → 最后和供应商算加工费和材料款。", 40, 465);
    drawNote(ctx, "注：入库后的成品，可走「采购入库」或「销售出库」继续后续流程。", 40, 500);
  });
}

// ==================== 图3：采购入库流程 ====================
async function fig3() {
  await makeImage("fig3-purchase.png", 1180, 300, async (ctx) => {
    drawTitle(ctx, "采购入库业务流程", 1180);
    const nodes = [
      { t: "采购订单", s: "向供应商下单", c: COLORS.blue, x: 40 },
      { t: "审核订单", s: "确认无误", c: COLORS.gray, x: 250 },
      { t: "采购入库", s: "货到验收", c: COLORS.green, x: 460 },
      { t: "审核入库", s: "库存+", c: COLORS.green, x: 670 },
      { t: "生成应付", s: "欠供应商钱", c: COLORS.cyan, x: 880 },
    ];
    const y = 110, w = 190, h = 70;
    nodes.forEach((n) => drawNode(ctx, n.x, n.y = y, w, h, n.t, n.c, n.s));
    for (let i = 0; i < nodes.length - 1; i++) {
      drawArrow(ctx, nodes[i].x + w, y + h / 2, nodes[i + 1].x, y + h / 2);
    }
    drawNote(ctx, "退货：采购退货单审核后，库存自动减少（库存-），应付相应冲减。", 40, 230);
  });
}

// ==================== 图4：销售出库流程 ====================
async function fig4() {
  await makeImage("fig4-sale.png", 1180, 300, async (ctx) => {
    drawTitle(ctx, "销售出库业务流程", 1180);
    const nodes = [
      { t: "销售订单", s: "客户要货", c: COLORS.pink, x: 40 },
      { t: "审核+库存检查", s: "有货才接单", c: COLORS.gray, x: 250 },
      { t: "销售出库", s: "仓库发货", c: COLORS.orange, x: 460 },
      { t: "审核出库", s: "库存-", c: COLORS.orange, x: 670 },
      { t: "生成应收", s: "客户欠钱", c: COLORS.cyan, x: 880 },
    ];
    const y = 110, w = 190, h = 70;
    nodes.forEach((n) => drawNode(ctx, n.x, n.y = y, w, h, n.t, n.c, n.s));
    for (let i = 0; i < nodes.length - 1; i++) {
      drawArrow(ctx, nodes[i].x + w, y + h / 2, nodes[i + 1].x, y + h / 2);
    }
    drawNote(ctx, "退货：客户退回货品，销售退货单审核后，库存自动增加（库存+），应收相应冲减。", 40, 230);
  });
}

// ==================== 图5：库存管理流程 ====================
async function fig5() {
  await makeImage("fig5-inventory.png", 1180, 360, async (ctx) => {
    drawTitle(ctx, "库存管理业务流程", 1180);
    drawNode(ctx, 490, 150, 200, 70, "库存", "实时数量", COLORS.dark);
    const around = [
      { t: "采购入库", s: "库存+", c: COLORS.green, x: 40, y: 60 },
      { t: "销售出库", s: "库存-", c: COLORS.orange, x: 940, y: 60 },
      { t: "移仓调拨", s: "仓到仓", c: COLORS.blue, x: 40, y: 240 },
      { t: "其他出入库", s: "盘点/报废", c: COLORS.purple, x: 940, y: 240 },
      { t: "品质重分类", s: "等级互转", c: COLORS.cyan, x: 490, y: 290 },
    ];
    const w = 200, h = 70;
    around.forEach((n) => drawNode(ctx, n.x, n.y, w, h, n.t, n.c, n.s));
    drawArrow(ctx, 240, 95, 490, 175);
    drawArrow(ctx, 690, 175, 940, 95);
    drawArrow(ctx, 240, 275, 490, 200);
    drawArrow(ctx, 690, 200, 940, 275);
    drawArrow(ctx, 590, 220, 590, 290);
    drawNote(ctx, "每次库存变动都会记录一张「库存流水」，写清楚为什么变、对应哪张单据，方便随时查账。", 40, 340);
  });
}

// ==================== 图6：财务结算流程 ====================
async function fig6() {
  await makeImage("fig6-finance.png", 1180, 360, async (ctx) => {
    drawTitle(ctx, "财务结算业务流程", 1180);
    const w = 200, h = 60;
    drawNode(ctx, 40, 90, w, h, "采购入库", "产生应付", COLORS.green);
    drawNode(ctx, 300, 90, w, h, "销售出库", "产生应收", COLORS.orange);
    drawNode(ctx, 40, 190, w, h, "应付账款", "欠供应商", COLORS.cyan);
    drawNode(ctx, 300, 190, w, h, "应收账款", "客户欠我", COLORS.cyan);
    drawArrow(ctx, 140, 150, 140, 190);
    drawArrow(ctx, 400, 150, 400, 190);
    drawNode(ctx, 170, 270, w, h, "汇总账单", "按往来单位", COLORS.gray);
    drawArrow(ctx, 240, 220, 270, 270);
    drawArrow(ctx, 400, 220, 370, 270);
    drawNode(ctx, 40, 270, w, h, "付款单", "还供应商", COLORS.purple);
    drawNode(ctx, 300, 270, w, h, "收款单", "收客户钱", COLORS.pink);
    drawArrow(ctx, 140, 300, 170, 300);
    drawArrow(ctx, 400, 300, 370, 300);
    drawNode(ctx, 560, 270, w, h, "资金流水", "每笔都记", COLORS.dark);
    drawNode(ctx, 820, 270, w, h, "清算看板", "对账可视化", COLORS.gray);
    drawArrow(ctx, 370, 300, 560, 300);
    drawArrow(ctx, 760, 300, 820, 300);
    drawNote(ctx, "通俗理解：买货欠供应商钱（应付），卖货客户欠我钱（应收）→ 汇总成账单 → 付款/收款冲账 → 每一笔资金都留记录 → 看板一眼看清谁欠多少、还了多少。", 40, 345);
  });
}

// ==================== 图7：单据状态流转 ====================
async function fig7() {
  await makeImage("fig7-status.png", 1180, 290, async (ctx) => {
    drawTitle(ctx, "单据通用状态流转（所有业务单据通用）", 1180);
    const nodes = [
      { t: "草稿", s: "刚填好", c: COLORS.gray, x: 60 },
      { t: "已审核", s: "确认生效", c: COLORS.green, x: 360 },
      { t: "已作废", s: "不能使用", c: COLORS.pink, x: 760 },
    ];
    const y = 110, w = 200, h = 70;
    nodes.forEach((n) => drawNode(ctx, n.x, n.y = y, w, h, n.t, n.c, n.s));
    drawArrow(ctx, 260, 145, 360, 145, "审核");
    drawArrow(ctx, 160, 180, 760, 215, "作废");
    drawArrow(ctx, 360, 175, 260, 190, "反审核");
    drawArrow(ctx, 560, 175, 760, 175, "取消");
    drawNote(ctx, "说明：任何单据都从「草稿」开始，审核后正式生效并触发库存/财务变动；出错可「反审核」退回或「作废」作废。", 40, 240);
  });
}

(async () => {
  // 注册字体
  const { GlobalFonts } = require("@napi-rs/canvas");
  GlobalFonts.registerFromPath(FONT_PATH, "Microsoft YaHei");
  GlobalFonts.registerFromPath(FONT_BOLD, "Microsoft YaHei Bold");
  await fig1();
  await fig2();
  await fig3();
  await fig4();
  await fig5();
  await fig6();
  await fig7();
  console.log("全部流程图生成完成，目录:", OUT_DIR);
})();
