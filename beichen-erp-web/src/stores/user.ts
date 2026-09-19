import { defineStore } from 'pinia'
import router from '@/router'
import { getUserMenuTree, getMyDashboardTabs, type MenuVO } from '@/api/system'
import { getMyPerms } from '@/api/auth'
import { SUPER_ADMIN_ROLE_CODE, ADMIN_ROLE_CODE } from '@/constants/system'

interface UserInfo {
  id?: number | string
  username?: string
  phone?: string
  dept?: string | null
  status?: number
  /** F3-3：接口权限码（页面码 + 按钮动作码），登录时由后端下发 */
  perms?: string[]
  avatar?: string
  roles?: string[]
  companyId?: number
  companyName?: string
  [key: string]: unknown
}

const TOKEN_KEY = 'beichen_erp_token'
const MENUS_KEY = 'beichen_erp_menus'
const TABS_KEY = 'beichen_erp_dashboard_tabs'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: (JSON.parse(localStorage.getItem('beichen_erp_user') || 'null') || null) as UserInfo | null,
    menus: (JSON.parse(localStorage.getItem(MENUS_KEY) || 'null') || []) as MenuVO[],
    /** 首页业务 TAB 勾选；null=未配置（全部可见） */
    dashboardTabs: (JSON.parse(localStorage.getItem(TABS_KEY) || 'null') || null) as string[] | null
  }),
  getters: {
    isLogin: (state) => !!state.token,
    /**
     * F3-3（2026-09-18）按钮级权限：接口权限码（页面码 + 按钮动作码）。
     * 来源＝登录响应 userInfo.perms（方案 A：动作码跟随页面自动带出）。
     */
    perms: (state) => (state.userInfo?.perms || []) as string[],
    /** 是否持有某权限码（`v-perm` 指令与页面内判定共用同一口径） */
    hasPerm: (state) => (code: string) => (state.userInfo?.perms || []).includes(code),
    isAdmin: (state) => {
      const roles = state.userInfo?.roles || []
      return roles.includes(SUPER_ADMIN_ROLE_CODE) || roles.includes(ADMIN_ROLE_CODE)
    },
    /** P2-34：平台级（跨租户）能力判定 —— 整库导入/导出等仅超级管理员可用 */
    isSuperAdmin: (state) => {
      const roles = state.userInfo?.roles || []
      return roles.includes(SUPER_ADMIN_ROLE_CODE)
    },
    menuPaths: (state) => {
      const paths: string[] = []
      function collectMenus(menuList: MenuVO[]) {
        for (const menu of menuList) {
          if (menu.routePath) paths.push(menu.routePath)
          if (menu.children && menu.children.length > 0) collectMenus(menu.children)
        }
      }
      collectMenus(state.menus)
      return paths
    }
  },
  actions: {
    setToken(token: string) {
      this.token = token
      if (token) localStorage.setItem(TOKEN_KEY, token)
      else localStorage.removeItem(TOKEN_KEY)
    },
    setUserInfo(info: UserInfo) {
      this.userInfo = info
      localStorage.setItem('beichen_erp_user', JSON.stringify(info))
    },
    setMenus(menus: MenuVO[]) {
      this.menus = menus
      localStorage.setItem(MENUS_KEY, JSON.stringify(menus))
    },
    setDashboardTabs(tabs: string[] | null | undefined) {
      this.dashboardTabs = tabs && tabs.length > 0 ? tabs : null
      if (this.dashboardTabs) localStorage.setItem(TABS_KEY, JSON.stringify(this.dashboardTabs))
      else localStorage.removeItem(TABS_KEY)
    },
    /** 从服务端拉取最新菜单（每次页面加载时调用，确保菜单始终最新） */
    async fetchMenus() {
      try {
        const menus = await getUserMenuTree()
        if (menus && menus.length > 0) {
          this.menus = menus
          localStorage.setItem(MENUS_KEY, JSON.stringify(menus))
        }
        // 顺带刷新首页 TAB 勾选（管理员可能改过）
        const tabs = await getMyDashboardTabs()
        this.setDashboardTabs(tabs)
        // 顺带刷新权限码（页面码 + 按钮动作码）：管理员改过授权后，按钮显隐随之更新
        const perms = await getMyPerms()
        if (perms && this.userInfo) this.setUserInfo({ ...this.userInfo, perms })
      } catch { /* 网络异常时保留当前菜单 */ }
    },
    logout() {
      this.token = ''
      this.userInfo = null
      this.menus = []
      this.dashboardTabs = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('beichen_erp_user')
      localStorage.removeItem(TABS_KEY)
      router.push('/login')
    }
  }
})

export type { UserInfo }
