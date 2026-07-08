<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Fold, Expand, User, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useTabStore } from '@/stores/tabs'
import { logout as logoutApi } from '@/api/auth'
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

function handleSelect(index: string) {
  router.push(index)
  if (isMobile.value) drawerOpen.value = false
}

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

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    })
    try { await logoutApi() } catch {}
    userStore.logout()
  } catch {}
}

function checkMobile() {
  const mobile = typeof window !== 'undefined' && window.innerWidth <= 768
  isMobile.value = mobile
  if (!mobile) drawerOpen.value = false
}
onMounted(() => { checkMobile(); window.addEventListener('resize', checkMobile) })
onUnmounted(() => { window.removeEventListener('resize', checkMobile) })
</script>

<template>
  <el-container class="layout-container" :class="{ 'is-mobile': isMobile }">
    <el-aside v-if="!isMobile" :width="isCollapse ? '64px' : '210px'" class="layout-aside">
      <div class="logo">
        <span v-if="!isCollapse" class="logo-text">北辰ERP</span>
        <span v-else class="logo-text-mini">北辰</span>
      </div>
      <SideMenu :collapse="isCollapse" @select="handleSelect" />
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
        <span class="logo-text">北辰ERP</span>
      </div>
      <SideMenu :collapse="false" @select="handleSelect" />
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
        <div
          v-for="tab in tabStore.tabs" :key="tab.path"
          class="tab-item"
          :class="{ active: tab.path === tabStore.activePath }"
          @click="switchTab(tab.path)"
        >
          <span class="tab-label">{{ tab.title }}</span>
          <span class="tab-close" @click="(e: MouseEvent) => closeTab(tab.path, e)">×</span>
        </div>
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
.tab-bar { display: flex; align-items: center; background: #f5f5f5; border-bottom: 1px solid #e4e7ed; padding: 0 4px; height: 36px; overflow-x: auto; }
.tab-item { display: flex; align-items: center; gap: 2px; padding: 4px 8px 4px 12px; margin: 0 2px; border-radius: 4px 4px 0 0; cursor: pointer; font-size: 12px; color: #606266; background: #e8eaed; white-space: nowrap; max-width: 160px; }
.tab-item.active { background: #fff; color: #409eff; border-bottom: 2px solid #409eff; }
.tab-item:hover { color: #409eff; }
.tab-label { overflow: hidden; text-overflow: ellipsis; }
.tab-close { margin-left: 2px; padding: 0 3px; border-radius: 2px; font-size: 14px; line-height: 1; }
.tab-close:hover { background: #c0c4cc; color: #fff; }

.layout-main { background-color: #f0f2f5; padding: 16px; }

/* 移动端：抽屉内菜单与遮罩样式 */
.mobile-drawer :deep(.el-drawer__body) { padding: 0; background-color: #304156; overflow-y: auto; }
.mobile-drawer :deep(.el-drawer__body) .el-menu { border-right: none; }
</style>
