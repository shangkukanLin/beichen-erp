// 绘制 V4.0 新增流程图：退货整理链路、销售换货链路
const fs = require("fs");
const path = require("path");
const { createCanvas, GlobalFonts } = require("@napi-rs/canvas");

const OUT_DIR = path.join(__dirname, "diagrams", "png");
if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
GlobalFonts.registerFromPath("C:\\Windows\\Fonts\\msyh.ttc", "Microsoft YaHei");
GlobalFonts.registerFromPath("C:\\Windows\\Fonts\\msyhbd.ttc", "Microsoft YaHei Bold");

const C = {
  green: "#4CAF50", blue: "#2196F3", orange: "#FF9800",
  purple: "#9C27B0", pink: "#E91E63", cyan: "#00BCD4",
  gray: "#607D8B", dark: "#404040", line: "#787878",
};

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

function node(ctx, x, y, w, h, text, color, sub) {
  ctx.fillStyle = color;
  roundRect(ctx, x, y, w, h, 8);
  ctx.fill();
  ctx.fillStyle = "#FFFFFF";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  const lines = text.split("\n");
  const lh = 24;
  let sy = y + h / 2 - ((lines.length - 1) * lh) / 2;
  if (sub) sy -= 8;
  ctx.font = "20px 'Microsoft YaHei'";
  lines.forEach((ln, i) => ctx.fillText(ln, x + w / 2, sy + i * lh));
  if (sub) {
    ctx.font = "13px 'Microsoft YaHei'";
    ctx.fillStyle = "#E6E6E6";
    ctx.fillText(sub, x + w / 2, sy + lines.length * lh + 4);
  }
}

function arrow(ctx, x1, y1, x2, y2, label) {
  ctx.strokeStyle = C.line; ctx.fillStyle = C.line; ctx.lineWidth = 2;
  ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke();
  const ang = Math.atan2(y2 - y1, x2 - x1), len = 11;
  ctx.beginPath();
  ctx.moveTo(x2, y2);
  ctx.lineTo(x2 - len * Math.cos(ang - Math.PI / 7), y2 - len * Math.sin(ang - Math.PI / 7));
  ctx.lineTo(x2 - len * Math.cos(ang + Math.PI / 7), y2 - len * Math.sin(ang + Math.PI / 7));
  ctx.closePath(); ctx.fill();
  if (label) {
    ctx.fillStyle = C.dark; ctx.font = "13px 'Microsoft YaHei'"; ctx.textAlign = "center";
    ctx.fillText(label, (x1 + x2) / 2, (y1 + y2) / 2 - 8);
  }
}

function title(ctx, text) {
  ctx.fillStyle = C.dark; ctx.font = "bold 24px 'Microsoft YaHei'";
  ctx.textAlign = "left"; ctx.textBaseline = "alphabetic";
  ctx.fillText(text, 40, 42);
}

function note(ctx, text, x, y) {
  ctx.fillStyle = C.dark; ctx.font = "15px 'Microsoft YaHei'";
  ctx.textAlign = "left"; ctx.textBaseline = "alphabetic";
  ctx.fillText(text, x, y);
}

async function make(file, w, h, fn) {
  const cv = createCanvas(w, h);
  const ctx = cv.getContext("2d");
  ctx.fillStyle = "#FFFFFF"; ctx.fillRect(0, 0, w, h);
  await fn(ctx);
  fs.writeFileSync(path.join(OUT_DIR, file), cv.toBuffer("image/png"));
  console.log("生成:", file);
}

// 图8：销售退货 → 售后仓 → 退货整理
async function fig8() {
  await make("fig8-return-sort.png", 1180, 380, async (ctx) => {
    title(ctx, "退货整理业务流程（售后仓 → 分选 → 成品仓/不良仓）");
    const row1 = [
      { t: "客户退货", s: "销售退货单", c: C.pink, x: 40 },
      { t: "审核入售后仓", s: "品质=待分类", c: C.orange, x: 270 },
      { t: "待整理库存", s: "超3天预警", c: C.purple, x: 500 },
      { t: "退货整理单", s: "分选 A/B/C/不良", c: C.blue, x: 730 },
    ];
    const w = 210, h = 70;
    row1.forEach(n => node(ctx, n.x, 110, w, h, n.t, n.c, n.s));
    for (let i = 0; i < row1.length - 1; i++) {
      arrow(ctx, row1[i].x + w, 145, row1[i + 1].x, 145);
    }
    const row2 = [
      { t: "A规/B规/C规", s: "入成品仓", c: C.green, x: 560 },
      { t: "不良品", s: "入不良仓", c: C.gray, x: 790 },
      { t: "折损收款", s: "生成应收", c: C.cyan, x: 40 },
    ];
    row2.forEach(n => node(ctx, n.x, 250, w, h, n.t, n.c, n.s));
    arrow(ctx, 835, 180, 665, 250);
    arrow(ctx, 835, 180, 895, 250);
    arrow(ctx, 250, 145, 145, 250);
    note(ctx, "通俗理解：客户退货先入「售后仓」，品质标记为「待分类」→ 仓库人员按 A/B/C/不良 分选 → 好的进成品仓、坏的进不良仓。", 40, 350);
  });
}

// 图9：销售换货
async function fig9() {
  await make("fig9-exchange.png", 1180, 330, async (ctx) => {
    title(ctx, "销售换货业务流程（同品换货，退回待整理 + 换出成品）");
    const nodes = [
      { t: "已审核销售单", s: "作为换货依据", c: C.blue, x: 40 },
      { t: "创建换货单", s: "选销售单带出明细", c: C.purple, x: 290 },
      { t: "校验可换量", s: "已售−已退−已换", c: C.gray, x: 540 },
      { t: "审核换货", s: "库存双向变动", c: C.orange, x: 790 },
    ];
    const w = 220, h = 70;
    nodes.forEach(n => node(ctx, n.x, 110, w, h, n.t, n.c, n.s));
    for (let i = 0; i < nodes.length - 1; i++) {
      arrow(ctx, nodes[i].x + w, 145, nodes[i + 1].x, 145);
    }
    node(ctx, 290, 230, 220, 60, "退回：售后仓 +N", "品质待分类", C.pink);
    node(ctx, 540, 230, 220, 60, "换出：成品仓 −N", "按原品质扣", C.green);
    arrow(ctx, 400, 180, 400, 230);
    arrow(ctx, 650, 180, 650, 230);
    note(ctx, "通俗理解：客户买了货要换同款（品质可不同）→ 强关联原销售单，退回的货入售后仓待整理，换给客户的新货从成品仓扣。", 40, 310);
  });
}

(async () => {
  await fig8();
  await fig9();
  console.log("V4.0 新增流程图完成");
})();
