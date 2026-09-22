import { onBeforeUnmount, onMounted, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { normalizeTabPath, useTabStore } from '@/stores/tabs'
import { usePageGuardStore } from '@/stores/pageGuard'

/**
 * 次级页面统一交互（2026-09-23 用户口径）——「返回」= **关闭当前页签 + 回到上一个页签**。
 *
 * 设计要点：
 *  1. 关页签 + 回退目标只有一处实现（`useTabStore.closeTabAndBack`），本文件只是"加上脏数据拦截 + 兜底"；
 *  2. 「返回」不是 `router.back()`：页签模型下浏览器历史与页签列表并不一致（`router.back()` 会把页签留在原地，
 *     这正是改造前"点了返回页签却还在"的根因）；
 *  3. 深链/刷新进入（没有上一个页签）⇒ 回落到列表页：优先页面显式传入的 `fallback`，否则由路径推导。
 */

/** 结尾这些"次级页标记"从路径里剥掉即可得到列表页（兜底用，页面可显式覆盖） */
const SECONDARY_SEGMENTS = new Set(['add', 'edit', 'detail', 'close', 'form', 'new', 'delivery', 'record'])

/** 由次级页面路径推导列表页路径，例如 /inventory/sale/detail/294 → /inventory/sale */
export function listPathOf(path: string): string {
  const parts = path.split('/').filter(Boolean)
  while (parts.length > 0) {
    const last = parts[parts.length - 1]
    if (SECONDARY_SEGMENTS.has(last) || /^\d+$/.test(last)) parts.pop()
    else break
  }
  return parts.length > 0 ? '/' + parts.join('/') : ''
}

/**
 * 统一「返回」。给 PageShell 与个别自建页头使用。
 * @param fallback 兜底列表页（深链直进、周边没有别的页签时用）；不传则按路径推导
 */
export function usePageBack(fallback?: string) {
  const route = useRoute()
  const router = useRouter()
  const tabs = useTabStore()
  const guard = usePageGuardStore()

  async function back() {
    const path = route.path
    // 未保存内容：先确认（与页签 ×、侧栏切换共用同一处文案）
    if (guard.isDirty(path)) {
      if (!(await guard.confirmLeave())) return
      guard.markClean(path)
    }
    // 关页签前先判断"当前是否还有别的页签"——决定能否回上一页签
    const hasOtherTab = tabs.tabs.some(t => normalizeTabPath(t.path) !== normalizeTabPath(path))
    const next = tabs.closeTabAndBack(path)
    router.push(hasOtherTab ? next : (fallback || listPathOf(path) || next))
  }

  return { back }
}

/**
 * 页面级「未保存」守卫。
 *
 * 覆盖范围（用户选定的 B 档）：
 *   · 页内「返回」按钮、页签 ×、侧栏切菜单 ⇒ 组件内路由守卫（onBeforeRouteLeave）
 *   · 浏览器后退 ⇒ 同上；浏览器刷新/关闭 ⇒ beforeunload
 *
 * 用法：
 * ```ts
 * const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form, items: items.value }))
 * // 数据加载完成后（此时才算"初始状态"）：
 * takeBaseline()
 * // 保存成功后（否则离开时会误报未保存）：
 * markClean()
 * ```
 */
export function useUnsavedGuard(snapshot: () => unknown) {
  const route = useRoute()
  const guard = usePageGuardStore()

  /** 初始快照（数据加载完成后的基线）；空串表示还没建立基线 ⇒ 此时不判定为脏 */
  let baseline = ''
  /** 首次求值是否失败过（用于把"静默失效"变成显式告警，见下） */
  let warned = false

  function serialize(): string {
    try {
      return JSON.stringify(snapshot())
    } catch (e) {
      // ⚠️ 2026-09-23 踩坑：watch(serialize) 会在**注册时立即求值**，若调用本 composable 时
      // snapshot 依赖的 reactive 状态还没声明（TDZ），这里会抛错并被吞掉 ⇒ 监听不到任何字段
      // ⇒ 脏标记永远不置位 ⇒ 守卫**静默失效**。由 ui-e2e-p17 抓到，故这里显式告警。
      if (!warned && import.meta.env.DEV) {
        warned = true
        console.warn('[useUnsavedGuard] snapshot() 求值失败，未保存守卫不会生效。' +
          '请把 useUnsavedGuard(...) 放在它所依赖的响应式状态**声明之后**。', e)
      }
      return ''
    }
  }

  function takeBaseline() {
    baseline = serialize()
    guard.markClean(route.path)
  }

  /** 保存成功后调用：把当前状态当作新基线，避免离开时误报 */
  function markClean() {
    takeBaseline()
  }

  function isDirty(): boolean {
    return guard.isDirty(route.path)
  }

  // 任意字段变化即同步脏标记（只比较序列化结果，避免逐字段接线）
  watch(serialize, () => {
    if (!baseline) return
    if (serialize() !== baseline) guard.markDirty(route.path)
    else guard.markClean(route.path)
  })

  onBeforeRouteLeave(async () => {
    if (!isDirty()) return true
    if (await guard.confirmLeave()) {
      markClean()
      return true
    }
    return false
  })

  function onBeforeUnload(e: BeforeUnloadEvent) {
    if (!isDirty()) return
    e.preventDefault()
    e.returnValue = ''
  }

  onMounted(() => window.addEventListener('beforeunload', onBeforeUnload))
  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', onBeforeUnload)
    guard.markClean(route.path)
  })

  return { takeBaseline, markClean, isDirty }
}
