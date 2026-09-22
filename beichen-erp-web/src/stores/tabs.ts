import { defineStore } from 'pinia'

interface Tab {
  path: string
  title: string
  /** 该页签最近一次访问的完整地址（含 query），切回页签时用它恢复页内状态（如 ?tab=bills、?id=） */
  fullPath?: string
}

const STORAGE_KEY = 'beichen_tabs'

/**
 * 页签身份 = **去掉 query/hash 的 path**。
 *
 * ⚠️ 2026-09-19 修（用户报「点整理单后顶部导航只剩首页」）：历史实现把 `route.fullPath`（含 query）当页签身份，
 * 带来两个连锁问题：
 *   1) 同一页因 query 不同（`/inventory/return-sort?tab=bills`）会再新增一个**重复页签**；
 *   2) 布局面包屑拿 `activePath` 去匹配菜单 `routePath`（纯路径）⇒ 匹配失败 ⇒ 面包屑塌陷成只剩「首页」。
 * 现在：身份用 `path`（query-free），**完整地址记在 `fullPath`** 里用于切回页签时恢复页内状态。
 */
export function normalizeTabPath(p: unknown): string {
  return String(p ?? '').split('?')[0].split('#')[0]
}

function saveTabs(state: { tabs: Tab[]; activePath: string; lastActivePath: string }) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ tabs: state.tabs, activePath: state.activePath, lastActivePath: state.lastActivePath }))
  } catch {}
}

function loadTabs(): { tabs: Tab[]; activePath: string; lastActivePath: string } {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) || {}
      // 自愈旧数据：按 query-free path 归并重复页签（老版本留下的 `?tab=...` 页签会被合并回原页签）
      const tabs: Tab[] = []
      const seen = new Set<string>()
      for (const t of Array.isArray(parsed.tabs) ? parsed.tabs : []) {
        const p = normalizeTabPath(t?.path)
        if (!p || seen.has(p)) continue
        seen.add(p)
        // ⚠️ 必须优先取 t.fullPath（含 query 的"最近访问地址"）；早期写成 t.path 会把记忆擦成纯路径，
        //    表现为"刷新后点页签回不到 ?tab=bills"（由 ui-e2e-1-nav.ps1 的 tab-memory 断言抓到）
        tabs.push({ path: p, title: t?.title || p, fullPath: String(t?.fullPath || t?.path || p) })
      }
      const active = normalizeTabPath(parsed.activePath)
      const last = normalizeTabPath(parsed.lastActivePath)
      return {
        tabs,
        activePath: tabs.some(t => t.path === active) ? active : (tabs.length > 0 ? tabs[tabs.length - 1].path : ''),
        lastActivePath: last,
      }
    }
  } catch {}
  return { tabs: [], activePath: '', lastActivePath: '' }
}

export const useTabStore = defineStore('tabs', {
  state: () => {
    const saved = loadTabs()
    return {
      tabs: saved.tabs as Tab[],
      activePath: saved.activePath as string,
      /** 上一个活跃的 tab 路径，用于关闭当前 tab 时回退 */
      lastActivePath: saved.lastActivePath as string,
      /** 每个路由 path 的打开序号（仅内存态，不持久化）：
       *  用于 keep-alive 的组件 key —— 关闭 Tab 后重新打开时序号 +1，key 变化使组件重新挂载，不残留上次状态 */
      tabSeq: {} as Record<string, number>
    }
  },
  actions: {
    /**
     * 打开/激活页签。
     * @param path 路由 path（可传 fullPath，内部会归一化；**页签身份是 query-free path**）
     * @param title 页签标题（一般取 route.meta.title）
     * @param fullPath 当前完整地址（含 query），用于切回页签时恢复页内状态
     */
    openTab(path: string, title: string, fullPath?: string) {
      const key = normalizeTabPath(path)
      if (!key) return
      const full = fullPath || (path.includes('?') ? path : key)
      const exists = this.tabs.find(t => t.path === key)
      if (!exists) {
        this.tabs.push({ path: key, title, fullPath: full })
        this.tabSeq[key] = (this.tabSeq[key] || 0) + 1
      } else {
        // 同一页签：仅更新"最近访问地址"与标题（不再产生第 2 个页签）
        exists.fullPath = full
        if (title) exists.title = title
      }
      // 记录上一个活跃 tab
      if (this.activePath && this.activePath !== key) {
        this.lastActivePath = this.activePath
      }
      this.activePath = key
      saveTabs(this.$state)
    },
    removeTab(path: string) {
      const key = normalizeTabPath(path)
      const idx = this.tabs.findIndex(t => t.path === key)
      if (idx === -1) return
      this.tabs.splice(idx, 1)
      if (this.activePath === key) {
        // 优先回到上一个活跃 tab（如果它还存在于列表中）
        if (this.lastActivePath && this.tabs.some(t => t.path === this.lastActivePath)) {
          this.activePath = this.lastActivePath
        } else if (idx > 0) {
          this.activePath = this.tabs[idx - 1].path
        } else if (this.tabs.length > 0) {
          this.activePath = this.tabs[0].path
        } else {
          this.activePath = ''
        }
      }
      saveTabs(this.$state)
    },
    /**
     * 统一「返回」的关页签动作（2026-09-23）：关闭指定页签（默认当前活跃页签），
     * 并把 activePath 指向回退目标，返回**回退目标的完整地址**（含 query，供 router.push 恢复页内状态）。
     *
     * 为什么放 store：页内「返回」按钮（usePageBack）与页签栏 × / 「关闭当前页」（layout）必须同源，
     * 否则三处各写一遍 → 早晚不一致（改造前 `router.back()` 与「写死列表路径」并存就是这个原因）。
     * 调用方负责真正导航 ⇒ 关页签与导航只此一处实现。
     */
    closeTabAndBack(path?: string): string {
      const key = normalizeTabPath(path ?? this.activePath)
      if (key) this.removeTab(key)
      const next = this.tabs.length > 0 ? (this.activePath || '/dashboard') : '/dashboard'
      return this.tabs.find(t => t.path === next)?.fullPath || next
    },

    setActive(path: string) {
      const key = normalizeTabPath(path)
      if (this.activePath && this.activePath !== key) {
        this.lastActivePath = this.activePath
      }
      this.activePath = key
      saveTabs(this.$state)
    },
    updateTabTitle(path: string, title: string) {
      const tab = this.tabs.find(t => t.path === normalizeTabPath(path))
      if (tab) {
        tab.title = title
        saveTabs(this.$state)
      }
    },
    clearAll() {
      this.tabs = []
      this.activePath = ''
      this.lastActivePath = ''
      this.tabSeq = {}
      localStorage.removeItem(STORAGE_KEY)
    }
  }
})
