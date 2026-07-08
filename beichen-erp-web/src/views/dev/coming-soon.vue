<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
const pageTitle = route.meta.title || '功能'
</script>

<template>
  <div class="coming-soon">
    <el-result icon="info" :title="`「${pageTitle}」功能开发中`" sub-title="该模块正在紧急开发中，敬请期待！">
      <template #extra>
        <el-button type="primary" @click="$router.push('/dashboard')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.coming-soon { display:flex; align-items:center; justify-content:center; min-height:60vh; }
</style>
