<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
const path = route.path
</script>

<template>
  <div class="placeholder-page">
    <div class="placeholder-card">
      <el-icon class="placeholder-icon"><Clock /></el-icon>
      <h2>{{ path }}</h2>
      <p>该模块正在开发中，敬请期待</p>
    </div>
  </div>
</template>

<style scoped>
.placeholder-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-card {
  text-align: center;
  padding: 48px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.placeholder-icon {
  font-size: 64px;
  color: #c0c4cc;
  margin-bottom: 16px;
}

.placeholder-card h2 {
  margin: 0 0 12px;
  font-size: 18px;
  color: #606266;
}

.placeholder-card p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}
</style>
