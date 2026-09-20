/**
 * 饼图外侧标签工具（2026-09-20 新增）
 *
 * 背景：原先饼图的**具体数值只在 tooltip 里**（鼠标悬停才看得到），外侧标签只写
 * `{b} {d}%`（名称 + 占比）、个别图甚至 `label.show:false` 完全没有标签。
 * 现在把**数值直接画在饼图外侧**，无需悬停。
 *
 * 为什么收口到本模块：
 * - 原先外侧标签文案散落在 3 个分析页各自的内联 `label.formatter`，
 *   数值写法（千分位/小数位/单位）也在各页 tooltip 里各写一份 ⇒ 口径易漂。
 * - 这里同时提供「外侧标签」与「tooltip」两个 formatter，**保证二者数值写法完全一致**
 *   （外侧看到的 == 悬停看到的），后续口径变更只改这一处。
 *
 * 数值格式与各页卡片合计（页内 `fmtN` / `fmtQty`）保持一致：
 * - 金额：千分位 + 固定 2 位小数 + ` 元`
 * - 数量：千分位 + 最多 2 位小数 + ` 件`
 */

/** 金额：千分位 + 固定 2 位小数（与页面 fmtN 同口径） */
export function pieAmount(v: any): string {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 数量：千分位 + 最多 2 位小数（与页面 fmtQty 同口径） */
export function pieQty(v: any): string {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0'
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

/**
 * 数值 + 单位。
 * @param unit `'件'` 走数量口径，其余（`'元'` 等）走金额口径；空串则不带单位。
 */
export function pieValueText(value: any, unit: string): string {
  const text = unit === '件' ? pieQty(value) : pieAmount(value)
  return unit ? `${text} ${unit}` : text
}

/**
 * 饼图外侧标签 formatter（两行）：
 *   第一行 = 分片名称
 *   第二行 = 具体数值 单位（占比%）
 *
 * @param unit 数值单位（`'件'` / `'元'` / `''`），由调用方按该图的量纲传入。
 */
export function pieOutsideLabel(unit: string) {
  return (p: any): string => `${p.name}\n${pieValueText(p.value, unit)}（${p.percent}%）`
}

/**
 * tooltip formatter：与外侧标签**同一数值口径**。
 * 之所以一并收口，是为了避免同一张卡片上「外侧标签」与「悬停提示」出现两种数值写法。
 */
export function pieTooltip(unit: string) {
  return (p: any): string => `${p.marker}${p.name}<br/>${pieValueText(p.value, unit)}（${p.percent}%）`
}
