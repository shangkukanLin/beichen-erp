import { defineStore } from 'pinia'

interface Tab {
  path: string
  title: string
}

const STORAGE_KEY = 'beichen_tabs'

function loadTabs(): { tabs: Tab[]; activePath: string } {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return JSON.parse(raw)
  } catch {}
  return { tabs: [], activePath: '' }
}

function saveTabs(state: { tabs: Tab[]; activePath: string; lastActivePath: string }) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ tabs: state.tabs, activePath: state.activePath, lastActivePath: state.lastActivePath }))
  } catch {}
}

export const useTabStore = defineStore('tabs', {
  state: () => {
    const saved = loadTabs()
    return {
      tabs: saved.tabs || [] as Tab[],
      activePath: saved.activePath || '' as string,
      /** 上一个活跃的 tab 路径，用于关闭当前 tab 时回退 */
      lastActivePath: (saved as any).lastActivePath || '' as string
    }
  },
  actions: {
    openTab(path: string, title: string) {
      const exists = this.tabs.find(t => t.path === path)
      if (!exists) {
        this.tabs.push({ path, title })
      }
      // 记录上一个活跃 tab
      if (this.activePath && this.activePath !== path) {
        this.lastActivePath = this.activePath
      }
      this.activePath = path
      saveTabs(this.$state)
    },
    removeTab(path: string) {
      const idx = this.tabs.findIndex(t => t.path === path)
      if (idx === -1) return
      this.tabs.splice(idx, 1)
      if (this.activePath === path) {
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
    setActive(path: string) {
      if (this.activePath && this.activePath !== path) {
        this.lastActivePath = this.activePath
      }
      this.activePath = path
      saveTabs(this.$state)
    },
    clearAll() {
      this.tabs = []
      this.activePath = ''
      this.lastActivePath = ''
      localStorage.removeItem(STORAGE_KEY)
    }
  }
})
