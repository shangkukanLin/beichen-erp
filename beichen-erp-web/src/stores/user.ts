import { defineStore } from 'pinia'
import router from '@/router'
import type { MenuVO } from '@/api/system'

interface UserInfo {
  id?: number | string
  username?: string
  phone?: string
  dept?: string | null
  status?: number
  avatar?: string
  roles?: string[]
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
          if (menu.routePath) {
            paths.push(menu.routePath)
          }
          if (menu.children && menu.children.length > 0) {
            collectMenus(menu.children)
          }
        }
      }
      collectMenus(state.menus)
      return paths
    }
  },
  actions: {
    setToken(token: string) {
      this.token = token
      if (token) {
        localStorage.setItem(TOKEN_KEY, token)
      } else {
        localStorage.removeItem(TOKEN_KEY)
      }
    },
    setUserInfo(info: UserInfo) {
      this.userInfo = info
      localStorage.setItem('beichen_erp_user', JSON.stringify(info))
    },
    setMenus(menus: MenuVO[]) {
      this.menus = menus
      localStorage.setItem(MENUS_KEY, JSON.stringify(menus))
    },
    logout() {
      this.token = ''
      this.userInfo = null
      this.menus = []
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('beichen_erp_user')
      localStorage.removeItem(MENUS_KEY)
      router.push('/login')
    }
  }
})

export type { UserInfo }
