import { defineStore } from 'pinia'
import { ElMessageBox } from 'element-plus'
import { normalizeTabPath } from '@/stores/tabs'

/**
 * 次级页面「未保存内容」守卫（2026-09-23 用户口径）。
 *
 * 为什么用 Pinia 存"脏页集合"而不是局部变量：
 * 拦截点分散在**三个不同组件**里，必须共享同一份状态，否则会重复弹框或漏拦：
 *   ① 页面内的「返回」按钮（PageShell）           → usePageBack
 *   ② 顶部页签栏的 × / 「关闭当前页」（layout）    → layout/index.vue
 *   ③ 侧栏切菜单、浏览器后退（组件内路由守卫）      → useUnsavedGuard
 *
 * 状态由页面自己维护（useUnsavedGuard 深度监听表单并同步本集合），拦截方只读它 ⇒ 文案与判定只有一处。
 */
export const usePageGuardStore = defineStore('pageGuard', {
  state: () => ({
    /** 处于"已修改、尚未保存"状态的页面 path 集合（query-free path） */
    dirtyPaths: [] as string[]
  }),

  actions: {
    isDirty(path: string): boolean {
      return this.dirtyPaths.includes(normalizeTabPath(path))
    },

    markDirty(path: string) {
      const key = normalizeTabPath(path)
      if (key && !this.dirtyPaths.includes(key)) this.dirtyPaths.push(key)
    },

    markClean(path: string) {
      const key = normalizeTabPath(path)
      this.dirtyPaths = this.dirtyPaths.filter(p => p !== key)
    },

    /**
     * 统一的"未保存"确认框 —— **全站只有这一处文案**（页面返回 / 页签 × / 侧栏切换共用）。
     * 按钮文案刻意用「确定返回 / 继续编辑」而不是「确定 / 取消」：这是"离开确认"，
     * 与弹窗 footer 的「取消 / 确定」（放弃本次录入）语义不同，避免用户误点。
     */
    async confirmLeave(): Promise<boolean> {
      try {
        await ElMessageBox.confirm('当前内容尚未保存，确定返回？', '未保存提醒', {
          confirmButtonText: '确定返回',
          cancelButtonText: '继续编辑',
          type: 'warning'
        })
        return true
      } catch {
        return false
      }
    }
  }
})
