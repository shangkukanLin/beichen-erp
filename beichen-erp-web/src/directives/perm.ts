import type { App, Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * F3-3（2026-09-18）按钮级权限指令 —— **方案 A：动作码默认跟随页面**。
 *
 * <p>用法：`<el-button v-perm="'purchase:exchange:audit'">审核</el-button>`。
 * 权限码来自登录响应 `userInfo.perms`（页面码 + 按钮动作码，后端 `sys_menu` 中
 * `menu_type='button'` 行由 `DataInitializer.initButtonPerms()` 登记）。</p>
 *
 * <p>无码时直接移除元素（权限在会话内不变，无需响应式恢复）。真正的兜底仍在后端：
 * `ApiPermGuard` 会按动作后缀（`/audit`、`/un-audit`、`/cancel`、`DELETE`）再校验一次 ——
 * 前端隐藏只是体验，不是安全边界。</p>
 */
const perm: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    const code = binding.value
    if (!code) return
    const store = useUserStore()
    if (!store.hasPerm(code)) {
      el.parentNode?.removeChild(el)
    }
  }
}

export function setupPermDirective(app: App) {
  app.directive('perm', perm)
}
