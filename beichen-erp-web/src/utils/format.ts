/**
 * 金额 / 比率展示口径（F7-260，2026-09-30 审核批 G —— 前端 6 个分析页原先各自实现、口径不一）。
 *
 * <p>统一后的口径（新页面请一律使用本文件，勿再各写 `toFixed`）：</p>
 * <ul>
 *   <li><b>比率（占比 / 率）</b>：`fmtPct` —— 默认 <b>1 位小数</b>（原 `sale.vue` 占比 1 位、`overview.vue`
 *       净利率 2 位、`tax.vue` 合计占比 2 位、`sale.vue` 的 `fmtPct` 2 位 ⇒ 四种写法；统一取多数）。</li>
 *   <li><b>金额（表格内）</b>：`fmtMoneyPlain` —— 纯数字 2 位小数、无千分位（表格列宽稳定，
 *       不触发列宽扫描器告警）。</li>
 *   <li><b>金额（卡片 / 看板 / 汇总大字）</b>：`fmtMoney` —— 千分位 + 2 位小数（可读性优先）。</li>
 * </ul>
 *
 * <p>空值一律按 0 展示（与后端 `IFNULL(...,0)` 口径一致），不显示 `NaN`。</p>
 */

/** 数值归一：非数字 / 空 ⇒ 0（避免 `null.toFixed` 抛错与 `NaN` 上屏） */
function num(v: any): number {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

/** 金额（表格内）：纯数字 2 位小数 */
export function fmtMoneyPlain(v: any, digits = 2): string {
  return num(v).toFixed(digits)
}

/** 金额（卡片 / 看板）：千分位 + 2 位小数 */
export function fmtMoney(v: any, digits = 2): string {
  return num(v).toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

/**
 * 金额（统一显示口径，2026-10-10 用户需求）：**精确到分，并去掉小数末尾无意义的 0**。
 *
 * <p>规则：先四舍五入到 **2 位**（分），再剥掉小数末尾的 `0`；若小数全为 `0`，连小数点一起去掉。
 * ⚠️ **必须"先 round 再裁"** —— 后端金额是 `DECIMAL(18,4)`（`StockCosts.SCALE = 4`），
 * 前端拿到的是 `200.0000` 这类值；只做 `replace(/\.00$/,'')` 会把 `123.4567` 这种 4 位小数原样暴露 ✗。</p>
 *
 * <p>例（含用户 2026-10-10 二次补充的口径）：
 * `1234.00 → 1234` ✓、**`1234.50 → 1234.5`** ✓、`1234.05 → 1234.05` ✓（末尾 5 是有效精度，保留 ✓）、
 * `1234.567 → 1234.57` ✓、`0.00 → 0` ✓。</p>
 *
 * <p>⚠️ **只用于"金额"**：单价、损耗率、税率、占比、良率等是**有效精度**，不要套用 ✗
 * （单价 `12.00` 去掉 `.00` 会被误读成"只报整数价" ✓）。
 * 需要"必须定长 2 位"的场合继续用 {@link fmtMoney} / {@link fmtMoneyPlain} ✓（它们语义就是固定 2 位 ✓）。</p>
 */
export function fmtAmount(v: any): string {
  // toFixed(2) 保证一定带小数点（如 "100.00"）⇒ 下面的剥离不会误伤整数部分的 0 ✓
  return num(v).toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
}

/**
 * 比率：**入参为百分数**（如 12.5 表示 12.5%），默认 1 位小数。
 * （与本项目既有页面一致：后端返回的就是百分数，前端不做 ×100。）
 */
export function fmtPct(v: any, digits = 1): string {
  return num(v).toFixed(digits) + '%'
}

/** 比率：由"部分 / 整体"算百分比文本（整体为 0 时返回 fallback，默认 0.0%） */
export function pctOf(part: any, total: any, digits = 1, fallback = '0.0%'): string {
  const t = num(total)
  if (t === 0) return fallback
  return ((num(part) / t) * 100).toFixed(digits) + '%'
}
