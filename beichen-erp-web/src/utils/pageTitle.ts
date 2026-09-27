/**
 * 统一「页面标题」工具（2026-09-27）
 *
 * <p>背景：浏览器标签页标题 = 页面名 + 固定后缀。此前这个后缀在 **5 处各写一遍**
 * （router 守卫 + 供应商/供货商详情 + 采购退货新增 + 移仓新增 ×2），既容易漂移，也让
 * 「页面名跟随实际单据类型」的动态页只能自己再拼一次后缀。这里收敛成一处。</p>
 *
 * <p>⚠️ 顶部**页签**（tab bar）标题是另一套（`stores/tabs` 的 `openTab` / `updateTabTitle`）。
 * 需要「页签 + 浏览器标题」跟着类型一起变时，两处都要调（见 return-order / material-return 的
 * add/detail：`syncTabTitle()` 里 `updateTabTitle(...)` + `applyPageTitle(...)` 并排）。</p>
 */
export const APP_TITLE_SUFFIX = ' - 北辰ERP管理系统'

/** 写浏览器标签页标题（页面名 + 固定后缀）。空值忽略（保留上一次标题，避免闪成空） */
export function applyPageTitle(name?: string | null) {
  const t = (name || '').trim()
  if (!t) return
  document.title = t + APP_TITLE_SUFFIX
}
