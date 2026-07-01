<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  router.push('/dashboard')
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
  color: #409eff;
  letter-spacing: 4px;
  line-height: 1;
}

.text {
  margin: 16px 0 28px;
  font-size: 18px;
  color: #606266;
}
</style>
