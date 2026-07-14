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

function saveTabs(state: { tabs: Tab[]; activePath: string }) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ tabs: state.tabs, activePath: state.activePath }))
  } catch {}
}

export const useTabStore = defineStore('tabs', {
  state: () => {
    const saved = loadTabs()
    return {
      tabs: saved.tabs || [] as Tab[],
      activePath: saved.activePath || '' as string
    }
  },
  actions: {
    openTab(path: string, title: string) {
      const exists = this.tabs.find(t => t.path === path)
      if (!exists) {
        this.tabs.push({ path, title })
      }
      this.activePath = path
      saveTabs(this.$state)
    },
    removeTab(path: string) {
      const idx = this.tabs.findIndex(t => t.path === path)
      if (idx === -1) return
      this.tabs.splice(idx, 1)
      if (this.activePath === path) {
        if (idx > 0) {
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
      this.activePath = path
      saveTabs(this.$state)
    },
    clearAll() {
      this.tabs = []
      this.activePath = ''
      localStorage.removeItem(STORAGE_KEY)
    }
  }
})
