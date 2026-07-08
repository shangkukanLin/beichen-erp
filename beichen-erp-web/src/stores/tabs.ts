import { defineStore } from 'pinia'

interface Tab {
  path: string
  title: string
}

export const useTabStore = defineStore('tabs', {
  state: () => ({
    tabs: [] as Tab[],
    activePath: '' as string
  }),
  actions: {
    openTab(path: string, title: string) {
      const exists = this.tabs.find(t => t.path === path)
      if (!exists) {
        this.tabs.push({ path, title })
      }
      this.activePath = path
    },
    removeTab(path: string) {
      const idx = this.tabs.findIndex(t => t.path === path)
      if (idx === -1) return
      this.tabs.splice(idx, 1)
      // 如果关闭的是当前激活的 tab，切到相邻 tab
      if (this.activePath === path) {
        if (idx > 0) {
          this.activePath = this.tabs[idx - 1].path
        } else if (this.tabs.length > 0) {
          this.activePath = this.tabs[0].path
        } else {
          this.activePath = ''
        }
      }
    },
    setActive(path: string) {
      this.activePath = path
    }
  }
})
