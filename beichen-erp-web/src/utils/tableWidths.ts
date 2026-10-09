/**
 * 表格列宽记忆（2026-10-09，用户需求）。
 *
 * 【口径（用户拍板）】① 全局兜底：一次覆盖**所有** el-table（项目里 228 个，不逐个页面改）；
 * ② 偏好存**服务端**：跨设备、跟用户走；③ **不跟公司走**：同一用户换公司共用同一套列宽。
 *
 * 【实现路径（2026-10-09 实测后的结论）】
 *   最初走"纯 DOM + `!important` 样式"（不碰 EP 内部）—— **实测行不通**：CSS 把渲染宽度钉死后，
 *   用户**再也拖不动列**（EP 改的是它自己的列模型，渲染被我们压住；实测只动了 3px），
 *   且"钉死"和"再次拖拽"不可兼得。
 *   最终走：**通过表格根元素暴露的组件实例读写 EP 的列模型**（`el.__vueParentComponent.store.states.columns`）
 *   —— 这是唯一能让"还原记忆宽度"与"之后仍可自由拖拽"同时成立的路径。所有访问都做了防御式判断，
 *   拿不到就**静默降级为"不记忆"**，绝不报错、绝不改页面。
 *
 * 【版本耦合与守卫】该路径依赖 Element Plus 的 `store.states.columns`；守卫
 * `tools/regression/verify-table-column-widths.ps1` 会端到端断言"拖拽→落库→重进保持→**再拖仍可改**→重置回默认"，
 * 一旦 EP 升级破坏该路径，守卫立即变红（这是刻意留的探针）。
 *
 * 【安全边界】未拖动 / 未登录 / 读不到偏好时**完全不动**：不设一列宽度，与"没有这个功能"一致。
 */
import type { App, ComponentOptions } from 'vue'
import request from '@/utils/request'
import router from '@/router'
import { ElMessage, ElMessageBox } from 'element-plus'

/** 列宽夹取区间（对外导出，守卫按同一口径断言） */
export const MIN_W = 60
export const MAX_W = 400
/** 页面显式指定表格键的属性名：<el-table data-cw-key="xxx"> */
export const KEY_ATTR = 'cwKey'
/** 上报节流窗口（拖动结束 → 静默 700ms 才发请求） */
const SAVE_DEBOUNCE_MS = 700
/** 拖动结束后**延后**这么久再采样（见 onUp 注释） */
const SAMPLE_DELAY_MS = 30

type WidthMap = Record<string, number>

const API_MINE = '/system/table-prefs'
const API_ITEM = '/system/table-prefs/item'

/** prefKey → { 列标识: 宽度 } */
const cache = new Map<string, WidthMap>()
/** 已按哪个用户加载过（换用户必须重新拉，避免 A 的列宽串到 B） */
let loadedForUserId = ''
let loading: Promise<void> | null = null
/** 每个表格键的待上报定时器（节流合并） */
const pendingSaves = new Map<string, number>()
/** 已挂载表格的"重新应用"回调（偏好拉回来之后补应用一次，解决"表格先挂载、偏好后到"） */
const liveAppliers = new Set<() => void>()

/* ------------------------------ 存储层 ------------------------------ */

function clampWidth(w: number): number {
  if (!Number.isFinite(w)) return MIN_W
  return Math.max(MIN_W, Math.min(MAX_W, Math.round(w)))
}

/** 当前登录用户 id（未登录返回空串）—— 用来判断"是否需要重新拉取偏好" */
function currentUserId(): string {
  try {
    const raw = localStorage.getItem('beichen_erp_user')
    if (!raw) return ''
    const u = JSON.parse(raw) as { id?: string | number }
    return u?.id != null ? String(u.id) : ''
  } catch {
    return ''
  }
}

function hasToken(): boolean {
  try {
    return !!localStorage.getItem('beichen_erp_token')
  } catch {
    return false
  }
}

/** 解析服务端返回的 JSON 字符串（坏数据一律忽略，绝不让页面炸） */
function parsePrefs(raw: string): WidthMap {
  const out: WidthMap = {}
  try {
    const obj = JSON.parse(raw) as Record<string, unknown>
    if (!obj || typeof obj !== 'object') return out
    for (const [k, v] of Object.entries(obj)) {
      const n = Number(v)
      if (k && Number.isFinite(n)) out[k] = n
    }
  } catch {
    /* 忽略坏数据 */
  }
  return out
}

/**
 * 确保偏好已从服务端拉取（每个用户只拉一次）。
 * 刻意**静默**：未登录、失败、超时都不提示、不抛错 —— 页面里大量守卫都断言"无 JS/API 报错"。
 */
export function ensureLoaded(): Promise<void> {
  if (!hasToken()) return Promise.resolve()
  const uid = currentUserId()
  if (uid && uid !== loadedForUserId) {
    cache.clear()
    loadedForUserId = uid
  } else if (loadedForUserId && uid === loadedForUserId && !loading) {
    return Promise.resolve()
  }
  if (loading) return loading
  loading = (async () => {
    try {
      const data = (await request.get(API_MINE + '/mine')) as unknown as Record<string, string>
      cache.clear()
      if (data && typeof data === 'object') {
        for (const [k, v] of Object.entries(data)) {
          if (typeof v === 'string') cache.set(k, parsePrefs(v))
        }
      }
      liveAppliers.forEach((fn) => {
        try { fn() } catch { /* 单张表失败不影响其它 */ }
      })
    } catch {
      /* 静默：拉不到就按"未自定义"处理 */
    } finally {
      loading = null
    }
  })()
  return loading
}

/** 换用户/退出登录时清掉内存缓存（避免 A 的列宽串给 B） */
export function forgetCache(): void {
  cache.clear()
  loadedForUserId = ''
  pendingSaves.forEach((t) => window.clearTimeout(t))
  pendingSaves.clear()
}

export function getWidths(prefKey: string): WidthMap | undefined {
  return cache.get(prefKey)
}

/** 保存某张表的列宽（节流上报；失败静默） */
export function saveWidths(prefKey: string, widths: WidthMap): void {
  cache.set(prefKey, widths)
  const exist = pendingSaves.get(prefKey)
  if (exist) window.clearTimeout(exist)
  const timer = window.setTimeout(() => {
    pendingSaves.delete(prefKey)
    request
      .put(API_MINE, { prefKey, prefs: JSON.stringify(widths) })
      .catch(() => { /* 静默 */ })
  }, SAVE_DEBOUNCE_MS)
  pendingSaves.set(prefKey, timer)
}

/** 重置单张表（内存 + 服务端） */
export function resetWidths(prefKey: string): void {
  cache.delete(prefKey)
  const exist = pendingSaves.get(prefKey)
  if (exist) {
    window.clearTimeout(exist)
    pendingSaves.delete(prefKey)
  }
  request.delete(API_ITEM, { params: { prefKey } }).catch(() => { /* 静默 */ })
}

/** 重置当前用户全部表格列宽 */
export async function resetAllWidths(): Promise<void> {
  cache.clear()
  pendingSaves.forEach((t) => window.clearTimeout(t))
  pendingSaves.clear()
  try {
    await request.delete(API_MINE + '/mine')
  } catch {
    /* 静默 */
  }
}

/* ------------------------------ Element Plus 列模型（防御式访问） ------------------------------ */

interface EpTable {
  doLayout?: () => void
  store?: { states?: { columns?: unknown[]; _columns?: unknown[] } }
}

/** 从表格根元素拿 EP 的组件实例（Vue 3 在元素上暴露的内部引用；拿不到返回 null） */
function epOf(tableEl: HTMLElement | undefined): EpTable | null {
  const inst = (tableEl as unknown as { __vueParentComponent?: { store?: unknown } } | undefined)
    ?.__vueParentComponent
  return inst && (inst as EpTable).store ? (inst as EpTable) : null
}

/** 列数组；拿不到返回 null ⇒ 调用方静默降级（不记忆，也不改页面） */
function columnsOf(tableEl: HTMLElement | undefined): any[] | null {
  const ep = epOf(tableEl)
  const st = ep?.store?.states as Record<string, unknown> | undefined
  if (!st) return null
  // ⚠️ 2026-10-09 实测：本构建里 `states.columns` 是 **Vue ref**（`columns=objectREF->array[7]`），
  // 直接 Array.isArray 判断永远为假 ⇒ 功能整体静默失效。三个候选都先解 ref 再判。
  const cand =
    unwrapRef<any[]>(st.columns) ||
    unwrapRef<any[]>(st._columns) ||
    unwrapRef<any[]>(st.originColumns)
  return Array.isArray(cand) && cand.length > 0 ? cand : null
}

/** 解 Vue ref（本构建的 states 成员可能是 ref）；普通值原样返回 */
function unwrapRef<T>(v: unknown): T | undefined {
  if (v && typeof v === 'object' && 'value' in (v as Record<string, unknown>)) {
    return (v as { value: T }).value
  }
  return v as T | undefined
}

/** 列标识：优先 property，其次 label，都没有用序号（同表内重名加 #2/#3） */
function columnKeys(cols: any[]): string[] {
  const seen = new Map<string, number>()
  return cols.map((col, i) => {
    const base = String(col?.property || col?.label || `#${i + 1}`).trim() || `#${i + 1}`
    const n = (seen.get(base) || 0) + 1
    seen.set(base, n)
    return n === 1 ? base : `${base}#${n}`
  })
}

/** 当前路由路径，把动态参数归一化为 :id（详情页 `/x/detail/123` 与 `/x/detail/456` 共用一套列宽） */
function routeKeyPath(): string {
  const raw = router.currentRoute?.value?.path || window.location.pathname || ''
  return raw
    .replace(/\/\d+(?=\/|$)/g, '/:id')
    .replace(/\/[0-9a-fA-F-]{16,}(?=\/|$)/g, '/:id')
}

/** 当前路由下第几张表（挂载顺序；切换路由即重置 ⇒ 同一页面每次的键一致） */
let lastPath = ''
let ordinal = 0

function resolveKey(el: HTMLElement | undefined): string {
  const explicit = el?.dataset?.[KEY_ATTR]
  if (explicit) return `k:${explicit}`
  const path = routeKeyPath()
  if (path !== lastPath) {
    lastPath = path
    ordinal = 0
  }
  return `${path}#${++ordinal}`
}

/* ------------------------------ 应用 / 保存 / 重置 ------------------------------ */

/** 记下页面**默认**列宽（供"重置列宽"精确还原）；只记一次，之后的写入不会覆盖它 */
export function captureDefaults(tableEl: HTMLElement | undefined): void {
  const cols = columnsOf(tableEl)
  if (!cols) return
  cols.forEach((col) => {
    if (col.__cwDefault === undefined) col.__cwDefault = col.width ?? null
  })
}

/**
 * 把已保存的列宽写回 EP 的列模型（没有偏好则**完全不动**）。
 */
export function applyToTable(tableEl: HTMLElement | undefined, prefKey: string): void {
  const cols = columnsOf(tableEl)
  if (!cols) return
  captureDefaults(tableEl)
  const map = cache.get(prefKey)
  const keys = columnKeys(cols)
  if (!map) return
  let touched = false
  cols.forEach((col, i) => {
    const w = map[keys[i]]
    if (typeof w === 'number') {
      col.width = clampWidth(w)
      touched = true
    }
  })
  if (touched) {
    try { epOf(tableEl)?.doLayout?.() } catch { /* 忽略 */ }
  }
}

/** 采样列模型里"有明确宽度"的列（= 用户看到的宽度） */
function sampleWidths(tableEl: HTMLElement | undefined): WidthMap | null {
  const cols = columnsOf(tableEl)
  if (!cols) return null
  const keys = columnKeys(cols)
  const out: WidthMap = {}
  cols.forEach((col, i) => {
    const w = Number(col?.width)
    if (Number.isFinite(w) && w > 0) out[keys[i]] = clampWidth(w)
  })
  return Object.keys(out).length > 0 ? out : null
}

/** 重置某张表：删服务端偏好 + 把列宽还原成页面默认 */
export function applyReset(tableEl: HTMLElement | undefined, prefKey: string): void {
  const cols = columnsOf(tableEl)
  resetWidths(prefKey)
  if (!cols) return
  cols.forEach((col) => {
    if (col.__cwDefault !== undefined) col.width = col.__cwDefault
  })
  try { epOf(tableEl)?.doLayout?.() } catch { /* 忽略 */ }
}

/** 表头右键 → 确认后重置本表列宽（全局兜底没有"每页放个按钮"的位置，右键最不打扰） */
async function confirmReset(tableEl: HTMLElement, prefKey: string): Promise<void> {
  try {
    await ElMessageBox.confirm('把这张表的列宽恢复为默认？', '重置列宽', {
      confirmButtonText: '重置',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 取消
  }
  applyReset(tableEl, prefKey)
  ElMessage.success('已重置本表列宽')
}

/* ------------------------------ 全局混入 ------------------------------ */

/**
 * 判断"这个组件实例是不是一张 el-table"：**以根元素类名为主判据**
 * （实测产物里 `$options.name` 为 undefined ⇒ 不能只认组件名；表格根带 `el-table` 类，
 * 而表头/表体/列子组件分别是 `el-table__header-wrapper` 等，不会误判）。
 */
function isTableComponent(inst: any): boolean {
  if (!inst) return false
  if (inst.$options?.name === 'ElTable') return true
  const root = inst.$el as HTMLElement | undefined
  return !!root?.classList?.contains?.('el-table')
}

/**
 * 全局混入：只对 el-table 生效，`app.mixin` 一次即覆盖全部表格，页面零改动。
 */
export const tableWidthMemoryMixin = {
  mounted(this: any) {
    if (!isTableComponent(this)) return
    const tableEl = this.$el as HTMLElement | undefined
    if (!tableEl) return

    const prefKey = resolveKey(tableEl)

    // ⚠️ 先同步记下"页面默认列宽"，再谈应用/落库：
    //   ① 若在**首次拖动之后**才第一次记，会把"拖后的宽度"当默认值 ⇒ 之后"重置"还原不回去；
    //   ② 若等到 `$nextTick`，Element Plus 可能已完成首次布局、把弹性列的**渲染宽度**写回列模型 ⇒
    //      记到的不是模板默认值（实测重置后是 107 而不是 96）。所以这里**同步**采集。
    captureDefaults(tableEl)

    // ① 应用已保存宽度（DOM 渲染完再写；偏好还没回来时先不动，ensureLoaded 完成后补一次）
    const reapply = () => applyToTable(tableEl, prefKey)
    try { this.$nextTick?.(reapply) } catch { /* 忽略 */ }
    liveAppliers.add(reapply)
    void ensureLoaded()

    // ② 拖拽检测：表头按下 → document 抬起 → **延后采样**后比对（Vue3 无 $on，只能走 DOM 事件）
    const header = tableEl.querySelector('.el-table__header-wrapper') as HTMLElement | null
    let armed = false
    const onDown = () => { armed = true }
    const onUp = () => {
      if (!armed) return
      armed = false
      // ⚠️ 必须延后一个宏任务：我们在 document 的**捕获**阶段监听，跑在 EP 自己的 mouseup 处理之前 ——
      // 那一刻它还没把新宽度写进列模型（实测 before/after 完全相同 ⇒ 被判成"没变"而漏记）。
      window.setTimeout(() => {
        const after = sampleWidths(tableEl)
        if (!after) return
        const last = cache.get(prefKey)
        if (last && JSON.stringify(last) === JSON.stringify(after)) return // 没变：不重复上报
        saveWidths(prefKey, after)
        applyToTable(tableEl, prefKey)
      }, SAMPLE_DELAY_MS)
    }
    header?.addEventListener('mousedown', onDown, true)
    document.addEventListener('mouseup', onUp, true)

    // ③ 表头右键 → 重置本表列宽
    const onCtx = (ev: Event) => {
      ev.preventDefault()
      void confirmReset(tableEl, prefKey)
    }
    header?.addEventListener('contextmenu', onCtx)

    this.__cwCleanup = () => {
      header?.removeEventListener('mousedown', onDown, true)
      document.removeEventListener('mouseup', onUp, true)
      header?.removeEventListener('contextmenu', onCtx)
      liveAppliers.delete(reapply)
    }
  },
  unmounted(this: any) {
    if (!isTableComponent(this)) return
    this.__cwCleanup?.()
  }
}

/**
 * 注册入口（与 main.ts 里 setupPermDirective 同一范式）：
 * `setupTableWidths(app)` 一次即可覆盖全部 el-table，页面零改动。
 */
export function setupTableWidths(app: App): void {
  app.mixin(tableWidthMemoryMixin as unknown as ComponentOptions)
}
