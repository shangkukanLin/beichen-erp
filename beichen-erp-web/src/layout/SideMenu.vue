<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Fold, Expand, User, ArrowDown, HomeFilled, Cpu, Shop, Setting,
  Goods, Money, Tools, Notebook, Tickets, Files, Connection, OfficeBuilding,
  GoodsFilled, Box, Document, Switch, Timer, TakeawayBox, ShoppingCart,
  Download, Odometer, Sell, Upload, Wallet, CreditCard, Postcard,
  TrendCharts, UserFilled, Avatar, Menu, CollectionTag, Delete, DataBoard,
  Refresh, Rank, Lock
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

// 图标字符串名 → 组件映射
const iconMap: Record<string, any> = {
  HomeFilled, Cpu, Shop, Setting, Goods, Money, Tools, Notebook, Tickets,
  Files, Connection, OfficeBuilding, GoodsFilled, Box, Document, Switch,
  Timer, TakeawayBox, ShoppingCart, Download, Odometer, Sell, Upload,
  Wallet, CreditCard, Postcard, TrendCharts, UserFilled, Avatar, Menu,
  CollectionTag, Delete, DataBoard, Refresh, Rank, Lock
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

/** 点击叶子菜单项时导航（el-menu 的 @select 只在叶子项触发，子菜单标题展开不会触发） */
function handleMenuSelect(index: string) {
  if (!index) return
  // 规范化为绝对路径（确保前导 /），并去除尾部斜杠以便与当前路由比较
  const path = ('/' + index.replace(/^\//, '')).replace(/\/$/, '') || '/'
  // 避免重复导航到当前路径
  if (path === route.path) return
  // 捕获懒加载 chunk 失败等异常，避免未处理的 Promise rejection
  router.push(path).catch((err: any) => {
    if (err?.name === 'NavigationDuplicated' || err?.message?.includes('NavigationDuplicated')) return
    console.warn('菜单导航失败:', err)
  })
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
