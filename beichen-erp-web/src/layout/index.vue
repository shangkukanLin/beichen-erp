<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Fold, Expand, User, ArrowDown } from '@element-plus/icons-vue'
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
const currentTitle = computed(() => (route.meta.title as string) || '')
const displayCompanyName = ref('')

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
  if (route.path === path) {
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
          <span class="header-title">{{ currentTitle }}</span>
        </div>
        <div class="header-right">
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
        <div class="tabs-wrapper">
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
        <span class="tab-close-btn" @click="handleClosePage" title="关闭当前页">关闭</span>
      </div>

      <el-main class="layout-main">
        <router-view v-slot="{ Component, route: r }">
          <keep-alive :include="tabStore.tabs.length > 0 ? undefined : []">
            <component :is="Component" :key="r.fullPath" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout-container { height: 100%; }
.layout-aside { background-color: #304156; transition: width 0.28s; overflow-y: auto; overflow-x: hidden; }
.logo { height: 60px; display: flex; align-items: center; justify-content: center; color: #fff; background-color: #2b3a4d; }
.logo-text { font-size: 18px; font-weight: 600; letter-spacing: 1px; }
.logo-text-mini { font-size: 16px; font-weight: 600; }
.layout-header { display: flex; align-items: center; justify-content: space-between; background-color: #fff; border-bottom: 1px solid #e6e6e6; padding: 0 16px; height: 48px; }
.header-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
.collapse-btn { font-size: 20px; cursor: pointer; color: #5a5e66; }
.header-title { font-size: 16px; font-weight: 500; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.header-right { display: flex; align-items: center; }
.user-info { display: flex; align-items: center; gap: 6px; cursor: pointer; color: #5a5e66; }
.username { font-size: 14px; }

/* 页签栏 */
.tab-bar { display: flex; align-items: center; background: #f5f5f5; border-bottom: 1px solid #e4e7ed; padding: 0 8px 0 4px; height: 46px; }
.tabs-wrapper { display: flex; align-items: center; flex: 1; overflow-x: auto; overflow-y: hidden; min-width: 0; }
.tab-item { display: flex; align-items: center; gap: 2px; padding: 4px 8px 4px 12px; margin: 0 2px; border-radius: 4px 4px 0 0; cursor: pointer; font-size: 12px; color: #606266; background: #e8eaed; white-space: nowrap; max-width: 160px; flex-shrink: 0; }
.tab-item.active { background: #fff; color: #409eff; border-bottom: 2px solid #409eff; }
.tab-item:hover { color: #409eff; }
.tab-label { overflow: hidden; text-overflow: ellipsis; }
.tab-close { margin-left: 2px; padding: 0 3px; border-radius: 2px; font-size: 14px; line-height: 1; }
.tab-close:hover { background: #c0c4cc; color: #fff; }
.tab-close-btn { flex-shrink: 0; margin-left: 8px; padding: 2px 10px; font-size: 12px; line-height: 20px; color: #909399; cursor: pointer; border-radius: 3px; border: 1px solid #dcdfe6; user-select: none; }
.tab-close-btn:hover { background: #f56c6c; color: #fff; border-color: #f56c6c; }

.layout-main { background-color: #f0f2f5; padding: 16px; }

/* 移动端：抽屉内菜单与遮罩样式 */
.mobile-drawer :deep(.el-drawer__body) { padding: 0; background-color: #304156; overflow-y: auto; }
.mobile-drawer :deep(.el-drawer__body) .el-menu { border-right: none; }
</style>
