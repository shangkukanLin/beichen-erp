import { defineStore } from 'pinia'
import router from '@/router'
import { getUserMenuTree, type MenuVO } from '@/api/system'

interface UserInfo {
  id?: number | string
  username?: string
  phone?: string
  dept?: string | null
  status?: number
  avatar?: string
  roles?: string[]
  companyId?: number
  companyName?: string
  [key: string]: unknown
}

const TOKEN_KEY = 'beichen_erp_token'
const MENUS_KEY = 'beichen_erp_menus'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: (JSON.parse(localStorage.getItem('beichen_erp_user') || 'null') || null) as UserInfo | null,
    menus: (JSON.parse(localStorage.getItem(MENUS_KEY) || 'null') || []) as MenuVO[]
  }),
  getters: {
    isLogin: (state) => !!state.token,
    isAdmin: (state) => {
      const roles = state.userInfo?.roles || []
      return roles.includes('super_admin') || roles.includes('admin')
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
    /** 从服务端拉取最新菜单（每次页面加载时调用，确保菜单始终最新） */
    async fetchMenus() {
      try {
        const menus = await getUserMenuTree()
        if (menus && menus.length > 0) {
          this.menus = menus
          localStorage.setItem(MENUS_KEY, JSON.stringify(menus))
        }
      } catch { /* 网络异常时保留当前菜单 */ }
    },
    logout() {
      this.token = ''
      this.userInfo = null
      this.menus = []
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('beichen_erp_user')
      router.push('/login')
    }
  }
})

export type { UserInfo }
