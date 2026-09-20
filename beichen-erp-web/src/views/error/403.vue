<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

/**
 * 2026-09-20（F7-200）：原实现固定跳 `/dashboard` —— 若该账号可见菜单里没有首页
 * （或首页配置下所有业务 TAB 都不可见），会**再次落回 403 形成死循环**。
 * 现口径：菜单已加载时优先首页，否则退到「该账号可见的第一个页面」；
 * 菜单尚未加载（长度 0）时保持原来的 /dashboard，避免把用户困在 403。
 */
const homeTarget = computed(() => {
  const paths = (userStore.menuPaths || []).filter((p: string) => p && p !== '/403')
  if (paths.length === 0) return '/dashboard'
  return paths.includes('/dashboard') ? '/dashboard' : paths[0]
})

function goHome() {
  if (homeTarget.value) router.push(homeTarget.value)
}
</script>

<template>
  <div class="forbidden">
    <div class="forbidden-content">
      <h1 class="code">403</h1>
      <p class="text">无权限访问</p>
      <el-button type="primary" @click="goHome">返回首页</el-button>
    </div>
  </div>
</template>

<style scoped>
.forbidden {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f0f2f5;
}

.forbidden-content {
  text-align: center;
}

.code {
  font-size: 96px;
  margin: 0;
  color: var(--app-color-primary);
  letter-spacing: 4px;
  line-height: 1;
}

.text {
  margin: 16px 0 28px;
  font-size: var(--app-font-xl);
  color: var(--app-text-regular);
}
</style>
