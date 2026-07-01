import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', requiresAuth: true }
      },
      {
        path: 'system/user',
        name: 'SystemUser',
        component: () => import('@/views/system/user/index.vue'),
        meta: { title: '用户管理', requiresAuth: true }
      },
      {
        path: 'system/role',
        name: 'SystemRole',
        component: () => import('@/views/system/role/index.vue'),
        meta: { title: '角色管理', requiresAuth: true }
      },
      {
        path: 'system/menu',
        name: 'SystemMenu',
        component: () => import('@/views/system/menu/index.vue'),
        meta: { title: '菜单管理', requiresAuth: true }
      },
      // 供应商管理（共用组件）
      {
        path: 'supplier/solution',
        name: 'SupplierSolution',
        component: () => import('@/views/supplier/index.vue'),
        meta: { title: '方案商', requiresAuth: true }
      },
      {
        path: 'supplier/factory',
        name: 'SupplierFactory',
        component: () => import('@/views/supplier/index.vue'),
        meta: { title: '委外加工厂', requiresAuth: true }
      },
      {
        path: 'supplier/product',
        name: 'SupplierProduct',
        component: () => import('@/views/supplier/index.vue'),
        meta: { title: '成品供应商', requiresAuth: true }
      },
      {
        path: 'supplier/material-supplier',
        name: 'SupplierMaterial',
        component: () => import('@/views/supplier/index.vue'),
        meta: { title: '辅料商', requiresAuth: true }
      },
      // 开发管理
      {
        path: 'dev/project',
        name: 'DevProject',
        component: () => import('@/views/dev/project/index.vue'),
        meta: { title: '研发项目', requiresAuth: true }
      },
      {
        path: 'dev/project/add',
        name: 'DevProjectAdd',
        component: () => import('@/views/dev/project/add.vue'),
        meta: { title: '新增项目', requiresAuth: true }
      },
      {
        path: 'dev/project/edit/:id',
        name: 'DevProjectEdit',
        component: () => import('@/views/dev/project/edit.vue'),
        meta: { title: '项目编辑', requiresAuth: true }
      },
      // BOM管理
      {
        path: 'dev/bom',
        name: 'DevBom',
        component: () => import('@/views/dev/bom/index.vue'),
        meta: { title: 'BOM管理', requiresAuth: true }
      },
      {
        path: 'dev/bom-type',
        name: 'DevBomType',
        component: () => import('@/views/dev/bom-type/index.vue'),
        meta: { title: 'BOM类型管理', requiresAuth: true }
      },
      {
        path: 'dev/drawing',
        name: 'DevDrawing',
        component: () => import('@/views/dev/drawing/index.vue'),
        meta: { title: '图纸文档', requiresAuth: true }
      },
      // 委外加工
      {
        path: 'outsource/material-info',
        name: 'OutsourceMaterialInfo',
        component: () => import('@/views/outsource/material-info.vue'),
        meta: { title: '物料信息管理', requiresAuth: true }
      },
      {
        path: 'outsource/warehouse',
        name: 'OutsourceWarehouse',
        component: () => import('@/views/outsource/warehouse.vue'),
        meta: { title: '委外仓库', requiresAuth: true }
      },
      // 占位路由：匹配菜单中有但尚未开发的路由
      {
        path: ':pathMatch(.*)*',
        name: 'Placeholder',
        component: () => import('@/views/dev/placeholder.vue'),
        meta: { title: '页面开发中', requiresAuth: true }
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限', requiresAuth: false }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  const isLogin = userStore.isLogin

  if (to.meta.title) {
    document.title = `${to.meta.title} - 北辰ERP管理系统`
  }

  if (to.meta.requiresAuth === false) {
    // 不需要鉴权的页面（如登录页），已登录则跳首页
    if (isLogin && to.path === '/login') {
      next('/')
      return
    }
    next()
    return
  }

  // 需要鉴权
  if (!isLogin) {
    next('/login')
    return
  }

  // 检查当前路径是否在用户菜单权限中（仅占位路由需要检查）
  if (to.name === 'Placeholder') {
    const allowedPaths = userStore.menuPaths
    if (allowedPaths.length > 0 && !allowedPaths.includes(to.path)) {
      next('/403')
      return
    }
  }

  next()
})

export default router
