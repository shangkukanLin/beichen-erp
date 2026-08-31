<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Fold, Expand, User, ArrowDown, Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useTabStore } from '@/stores/tabs'
import { logout as logoutApi } from '@/api/auth'
import request from '@/utils/request'
import SideMenu from './SideMenu.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const tabStore = useTabStore()

const isCollapse = ref(false)
const isMobile = ref(typeof window !== 'undefined' ? window.innerWidth <= 768 : false)
const drawerOpen = ref(false)

const activeMenu = computed(() => route.path)
const displayCompanyName = ref('')

// 面包屑：根据当前激活 tab 的菜单父链生成（首页 / 一级菜单 / 二级菜单 / 操作名 ...）
const breadcrumbItems = computed(() => {
  const path = tabStore.activePath || route.path
  const menus = userStore.menus || []
  const crumbs: { name: string; path?: string }[] = [{ name: '首页', path: '/dashboard' }]
  if (!Array.isArray(menus) || menus.length === 0) return crumbs
  const findIn = (list: any[], exact: boolean): any | null => {
    for (const m of list) {
      if (m.routePath && (exact ? path === m.routePath : path.startsWith(m.routePath + '/'))) return m
      if (m.children?.length) {
        const f = findIn(m.children, exact)
        if (f) return f
      }
    }
    return null
  }
  // 先精确匹配当前 path 的菜单节点；未命中则按前缀匹配（新增/编辑/详情等子页面）
  const matched = findIn(menus, true) || findIn(menus, false)
  if (!matched) return crumbs
  const chain: any[] = []
  const collect = (list: any[], target: any, ancestors: any[] = []): boolean => {
    for (const m of list) {
      if (m === target) { chain.push(...ancestors, m); return true }
      if (m.children?.length && collect(m.children, target, [...ancestors, m])) return true
    }
    return false
  }
  collect(menus, matched)
  for (const c of chain) {
    if (c.menuName) crumbs.push({ name: c.menuName, path: c.routePath || undefined })
  }
  // 非菜单精确路径（新增/编辑/详情等子页面）：追加操作名作为最后一级
  if (!findIn(menus, true)) {
    const op = route.meta.title as string
    if (op && op !== matched.menuName) crumbs.push({ name: op })
  }
  return crumbs
})

function goCrumb(path?: string) {
  if (path && path !== route.path) router.push(path)
}

// 获取公司名称（优先 userInfo，否则调 API 从 session 读取）
async function fetchCompanyName() {
  if (userStore.userInfo?.companyName) {
    displayCompanyName.value = userStore.userInfo.companyName
    return
  }
  try {
    const name = await request.get<string, string>('/auth/company-name')
    if (name) displayCompanyName.value = name
  } catch { /* ignore */ }
}

// 路由变化时自动打开页签
watch(() => route.fullPath, (path) => {
  if (path !== '/login' && path !== '/company-manage') {
    const title = (route.meta.title as string) || path
    tabStore.openTab(path, title)
  }
}, { immediate: true })

function toggleSidebar() {
  if (isMobile.value) drawerOpen.value = !drawerOpen.value
  else isCollapse.value = !isCollapse.value
}

// 路由变化时：移动端自动关闭抽屉
watch(() => route.path, () => {
  if (isMobile.value) drawerOpen.value = false
})

function switchTab(path: string) {
  tabStore.setActive(path)
  router.push(path)
}

function closeTab(path: string, e: MouseEvent) {
  e.preventDefault()
  e.stopPropagation()
  tabStore.removeTab(path)
  const next = tabStore.tabs.length > 0 ? tabStore.activePath || '/dashboard' : '/dashboard'
  if (route.fullPath === path) {
    router.push(next)
  }
}

function handleClosePage() {
  tabStore.removeTab(route.fullPath)
  const next = tabStore.tabs.length > 0 ? tabStore.activePath || '/dashboard' : '/dashboard'
  router.push(next)
}

function handleTabMouseDown(path: string, e: MouseEvent) {
  // 仅阻止中键默认行为（自动滚动），关闭逻辑在 mouseup 中处理
  if (e.button === 1) {
    e.preventDefault()
  }
}

function handleTabMouseUp(path: string, e: MouseEvent) {
  // 中键关闭 Tab，与点击 × 行为一致
  if (e.button === 1) {
    closeTab(path, e)
  }
}

// 页签滚动：隐藏滚动条后，鼠标滚轮/触摸板/箭头按钮横向滑动
const tabsWrapperRef = ref<HTMLElement | null>(null)
const canScrollLeft = ref(false)
const canScrollRight = ref(false)

function updateTabsScrollState() {
  const el = tabsWrapperRef.value
  if (!el) return
  canScrollLeft.value = el.scrollLeft > 1
  canScrollRight.value = el.scrollLeft + el.clientWidth < el.scrollWidth - 1
}
function onTabsScroll() { updateTabsScrollState() }
function onTabsWheel(e: WheelEvent) {
  // 垂直滚轮量转为横向滚动
  if (Math.abs(e.deltaY) > Math.abs(e.deltaX)) {
    e.preventDefault()
    const el = e.currentTarget as HTMLElement
    el.scrollLeft += e.deltaY
  }
}
function scrollTabs(delta: number) {
  const el = tabsWrapperRef.value
  if (el) el.scrollBy({ left: delta, behavior: 'smooth' })
}
watch(() => [tabStore.tabs.length, tabStore.activePath], () => { nextTick(updateTabsScrollState) })

// 刷新数据：列表/详情页整页刷新；新增/修改页仅清空下拉缓存（避免丢失未保存表单内容）
function handleRefreshData() {
  const p = route.path
  if (p.includes('/add') || p.includes('/edit')) {
    window.dispatchEvent(new Event('refresh:dropdown-data'))
    ElMessage.success('下拉数据已刷新')
  } else {
    window.location.reload()
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    })
    try { await logoutApi() } catch {}
    tabStore.clearAll()
    userStore.logout()
  } catch {}
}

function checkMobile() {
  const mobile = typeof window !== 'undefined' && window.innerWidth <= 768
  isMobile.value = mobile
  if (!mobile) drawerOpen.value = false
}
onMounted(() => { checkMobile(); window.addEventListener('resize', checkMobile); userStore.fetchMenus(); fetchCompanyName() })
onUnmounted(() => { window.removeEventListener('resize', checkMobile) })

// 登录后 userInfo 更新时同步公司名称
watch(() => userStore.userInfo?.companyName, (name) => {
  if (name) displayCompanyName.value = name
})
</script>

<template>
  <el-container class="layout-container" :class="{ 'is-mobile': isMobile }">
    <el-aside v-if="!isMobile" :width="isCollapse ? '64px' : '210px'" class="layout-aside">
      <div class="logo">
        <span v-if="!isCollapse" class="logo-text">{{ displayCompanyName || '北辰ERP' }}</span>
        <span v-else class="logo-text-mini">{{ (displayCompanyName || '北辰').substring(0, 2) }}</span>
      </div>
      <SideMenu :collapse="isCollapse" />
    </el-aside>

    <el-drawer
      v-if="isMobile"
      v-model="drawerOpen"
      direction="ltr"
      :with-header="false"
      size="210px"
      class="mobile-drawer"
    >
      <div class="logo">
        <span class="logo-text">{{ displayCompanyName || '北辰ERP' }}</span>
      </div>
      <SideMenu :collapse="false" />
    </el-drawer>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleSidebar">
            <Fold v-if="!isCollapse || isMobile" /><Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/" class="header-breadcrumb">
            <el-breadcrumb-item v-for="(c, i) in breadcrumbItems" :key="i">
              <span v-if="c.path" class="crumb-link" @click="goCrumb(c.path)">{{ c.name }}</span>
              <span v-else>{{ c.name }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-button text size="small" class="refresh-btn" @click="handleRefreshData" title="刷新数据">
            <el-icon style="margin-right:4px"><Refresh /></el-icon>刷新数据
          </el-button>
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-icon><User /></el-icon>
              <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 页签栏 -->
      <div v-if="tabStore.tabs.length > 0" class="tab-bar">
        <span v-if="canScrollLeft" class="tab-arrow" @click="scrollTabs(-160)" title="向左滚动">‹</span>
        <div ref="tabsWrapperRef" class="tabs-wrapper" @wheel="onTabsWheel" @scroll="onTabsScroll">
          <div
            v-for="tab in tabStore.tabs" :key="tab.path"
            class="tab-item"
            :class="{ active: tab.path === tabStore.activePath }"
            @click="switchTab(tab.path)"
            @mousedown="(e: MouseEvent) => handleTabMouseDown(tab.path, e)"
            @mouseup="(e: MouseEvent) => handleTabMouseUp(tab.path, e)"
          >
            <span class="tab-label">{{ tab.title }}</span>
            <span class="tab-close" @click="(e: MouseEvent) => closeTab(tab.path, e)">×</span>
          </div>
        </div>
        <span v-if="canScrollRight" class="tab-arrow" @click="scrollTabs(160)" title="向右滚动">›</span>
        <span class="tab-close-btn" @click="handleClosePage" title="关闭当前页">关闭</span>
      </div>

      <el-main class="layout-main">
        <router-view v-slot="{ Component, route: r }">
          <keep-alive :exclude="['PurchaseAdd', 'PurchaseReturnAdd', 'InventoryWarehouseMoveAdd']" :max="30">
            <component :is="Component" :key="r.fullPath + '-' + (tabStore.tabSeq[r.path] || 1)" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout-container { height: 100%; }
.layout-aside { background-color: #304156; transition: width 0.28s; overflow-y: auto; overflow-x: hidden; }
.layout-aside::-webkit-scrollbar { width: 4px; }
.layout-aside::-webkit-scrollbar-track { background: transparent; }
.layout-aside::-webkit-scrollbar-thumb { background: rgba(255,255,255,.15); border-radius: 2px; }
.layout-aside::-webkit-scrollbar-thumb:hover { background: rgba(255,255,255,.3); }
.logo { height: 60px; display: flex; align-items: center; justify-content: center; color: #fff; background-color: #2b3a4d; }
.logo-text { font-size: var(--app-font-xl); font-weight: 600; letter-spacing: 1px; }
.logo-text-mini { font-size: var(--app-font-lg); font-weight: 600; }
.layout-header { display: flex; align-items: center; justify-content: space-between; background-color: var(--app-bg-container); border-bottom: 1px solid var(--app-border-light); padding: 0 var(--app-space-base); height: 48px; }
.header-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
.collapse-btn { font-size: 20px; cursor: pointer; color: var(--app-text-regular); }
.header-breadcrumb { font-size: var(--app-font-base); }
.header-breadcrumb :deep(.el-breadcrumb__inner) { color: var(--app-text-regular); }
.header-breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) { color: var(--app-text-primary); font-weight: 600; }
.crumb-link { cursor: pointer; }
.crumb-link:hover { color: var(--app-color-primary); }
.header-right { display: flex; align-items: center; }
.refresh-btn { margin-right: 12px; color: var(--app-text-regular); }
.user-info { display: flex; align-items: center; gap: 6px; cursor: pointer; color: var(--app-text-regular); }
.username { font-size: var(--app-font-base); }

/* 页签栏 */
.tab-bar { display: flex; align-items: center; background: #fff; border-bottom: 1px solid #e5e6eb; padding: 4px 8px 0 4px; height: 46px; }
.tabs-wrapper { display: flex; align-items: center; flex: 1; overflow-x: auto; overflow-y: hidden; min-width: 0; scrollbar-width: none; -ms-overflow-style: none; }
.tabs-wrapper::-webkit-scrollbar { display: none; }
.tab-arrow { flex-shrink: 0; width: 22px; height: 22px; display: flex; align-items: center; justify-content: center; font-size: 16px; line-height: 1; color: var(--app-text-regular); cursor: pointer; border-radius: 3px; user-select: none; }
.tab-arrow:hover { background: #f2f3f5; color: var(--app-color-primary); }
.tab-item { display: flex; align-items: center; gap: 2px; padding: 6px 10px 6px 12px; margin: 0 3px; border-radius: 6px 6px 0 0; cursor: pointer; font-size: var(--app-font-base); color: #555; background: #fff; white-space: nowrap; max-width: 180px; flex-shrink: 0; }
.tab-item:hover { background: #f2f3f5; color: var(--app-color-primary); }
.tab-item.active { background: var(--app-color-primary); color: #fff; }
.tab-item.active::before { content: ''; display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: rgba(255,255,255,.95); margin-right: 4px; }
.tab-label { overflow: hidden; text-overflow: ellipsis; }
.tab-close { margin-left: 2px; padding: 0 3px; border-radius: var(--app-radius-sm); font-size: var(--app-font-base); line-height: 1; color: #999; }
.tab-close:hover { background: rgba(0,0,0,.12); color: #fff; }
.tab-item.active .tab-close { color: rgba(255,255,255,.85); }
.tab-item.active .tab-close:hover { background: rgba(255,255,255,.25); color: #fff; }
.tab-close-btn { flex-shrink: 0; margin-left: 8px; padding: 2px 10px; font-size: var(--app-font-xs); line-height: 20px; color: var(--app-text-secondary); cursor: pointer; border-radius: 3px; border: 1px solid var(--app-border-color); user-select: none; }
.tab-close-btn:hover { background: var(--app-color-danger); color: #fff; border-color: var(--app-color-danger); }

.layout-main { background-color: #f0f2f5; padding: 16px; }

/* 页面级 Tabs 固定在顶部：滚动时 TAB 栏不随内容滚走（弹窗内 el-tabs 不受影响） */
.layout-main :deep(.el-tabs__header) {
  position: sticky;
  top: 0;
  z-index: 50;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, .05);
}

/* 移动端：抽屉内菜单与遮罩样式 */
.mobile-drawer :deep(.el-drawer__body) { padding: 0; background-color: #304156; overflow-y: auto; }
.mobile-drawer :deep(.el-drawer__body) .el-menu { border-right: none; }
</style>
