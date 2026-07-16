<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Fold, Expand, User, ArrowDown, HomeFilled, Cpu, Shop, Setting,
  Goods, Money, Tools, Notebook, Tickets, Files, Connection, OfficeBuilding,
  GoodsFilled, Box, Document, Switch, Timer, TakeawayBox, ShoppingCart,
  Download, Odometer, Sell, Upload, Wallet, CreditCard, Postcard,
  TrendCharts, UserFilled, Avatar, Menu, CollectionTag, Delete, DataBoard
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

// 图标字符串名 → 组件映射
const iconMap: Record<string, any> = {
  HomeFilled, Cpu, Shop, Setting, Goods, Money, Tools, Notebook, Tickets,
  Files, Connection, OfficeBuilding, GoodsFilled, Box, Document, Switch,
  Timer, TakeawayBox, ShoppingCart, Download, Odometer, Sell, Upload,
  Wallet, CreditCard, Postcard, TrendCharts, UserFilled, Avatar, Menu,
  CollectionTag, Delete, DataBoard
}
function resolveIcon(iconName: string): any {
  if (!iconName) return Menu
  return iconMap[iconName] || Menu
}

const props = defineProps<{ collapse?: boolean }>()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const activeMenu = computed(() => route.path)

/** 收集所有叶子菜单的 routePath，用于判断 @select 是否来自真正的菜单项 */
const leafRoutePaths = computed(() => {
  const paths = new Set<string>()
  function collect(menus: any[]) {
    for (const m of menus) {
      if (m.menuType === 'menu') paths.add(m.routePath)
      if (m.children) collect(m.children)
    }
  }
  collect(userStore.menus)
  return paths
})

/** 将 index 转为绝对路径后导航，仅在点击叶子菜单项时生效 */
function handleMenuSelect(index: string) {
  if (!index) return
  // 只对已知的叶子菜单路径导航，避免子菜单标题展开时触发意外跳转
  if (!leafRoutePaths.value.has(index)) return
  const path = '/' + index.replace(/^\//, '')
  router.push(path)
}
</script>

<template>
  <el-menu
    :default-active="activeMenu"
    :collapse="props.collapse"
    :collapse-transition="false"
    background-color="#304156"
    text-color="#bfcbd9"
    active-text-color="#409EFF"
    @select="handleMenuSelect"
  >
    <template v-for="item in userStore.menus" :key="item.id">
      <!-- catalog 且有子项 → 子菜单 -->
      <el-sub-menu
        v-if="item.menuType === 'catalog' && item.children && item.children.length > 0"
        :index="String(item.routePath || item.id)"
      >
        <template #title>
          <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
          <span>{{ item.menuName }}</span>
        </template>
        <!-- 递归：子项可能是 menu 或二级 catalog -->
        <template v-for="child in item.children" :key="child.id">
          <el-sub-menu
            v-if="child.menuType === 'catalog' && child.children && child.children.length > 0"
            :index="String(child.routePath || child.id)"
          >
            <template #title>
              <el-icon><component :is="resolveIcon(child.icon)" /></el-icon>
              <span>{{ child.menuName }}</span>
            </template>
            <el-menu-item v-for="sub in child.children" :key="sub.id" :index="sub.routePath">
              <el-icon><component :is="resolveIcon(sub.icon)" /></el-icon>
              <template #title>{{ sub.menuName }}</template>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else-if="child.menuType === 'menu'" :index="child.routePath">
            <el-icon><component :is="resolveIcon(child.icon)" /></el-icon>
            <template #title>{{ child.menuName }}</template>
          </el-menu-item>
        </template>
      </el-sub-menu>
      <!-- 一级 menu（如首页） -->
      <el-menu-item v-else-if="item.menuType === 'menu'" :index="item.routePath">
        <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
        <template #title>{{ item.menuName }}</template>
      </el-menu-item>
    </template>
  </el-menu>
</template>

<style scoped>
:deep(.el-menu) { border-right: none; }
/* 禁用子菜单展开/折叠动画，解决菜单项多时卡顿 */
:deep(.el-sub-menu .el-menu) { transition: none !important; }
:deep(.el-sub-menu__title) { transition: none !important; }
</style>
