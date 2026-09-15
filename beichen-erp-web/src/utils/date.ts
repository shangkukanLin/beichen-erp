/**
 * 本地时区日期工具 —— 全站统一入口（2026-09-14 立）。
 *
 * ## 为什么必须有这个文件
 * 前端原先大量使用 `new Date().toISOString().slice(0, 10)` 取"今天"，这是**错的**：
 * `toISOString()` 返回 **UTC**，而业务日期都是**本地时区**概念。东八区（UTC+8）
 * 在**每天 08:00 之前**，UTC 日期仍是前一天，于是：
 * - 新增单据的默认日期会填成**昨天**（销售单/采购单/退货/费用/发票/移仓/盘点…）；
 * - 报表查询的默认区间从昨天起算；
 * - 导出文件名里的日期是昨天；
 * - "本月"统计在**每月 1 日 08:00 之前**会算成**上个月**（用 `localMonth()`）。
 *
 * ## 使用约定
 * - **业务日期一律走本模块**（默认值、查询区间、文件名、看板口径）。
 * - 只有确实需要 **UTC 时间戳**（如把瞬时时刻按 ISO 上报给后端）才用 `toISOString()`。
 *
 * @example
 * const form = reactive({ orderDate: localDate() })   // 而不是 toISOString().slice(0, 10)
 * const curMonth = localMonth()                       // 而不是 toISOString().slice(0, 7)
 */

/** 补零到两位 */
const pad = (n: number): string => String(n).padStart(2, '0')

/**
 * 本地时区日期 `yyyy-MM-dd`。
 * @param d 基准时刻，默认当前时间（便于测试时传入固定时间）
 */
export function localDate(d: Date = new Date()): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/**
 * 本地时区月份 `yyyy-MM`（用于"本月"口径的 `startsWith` 比较）。
 * @param d 基准时刻，默认当前时间
 */
export function localMonth(d: Date = new Date()): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`
}
