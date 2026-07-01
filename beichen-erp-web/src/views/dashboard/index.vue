<script setup lang="ts">
</script>

<template>
  <div class="dashboard">
    <el-card shadow="never">
      <div class="welcome">
        <el-icon class="welcome-icon"><HomeFilled /></el-icon>
        <div class="welcome-text">
          <h2>欢迎使用北辰ERP管理系统</h2>
          <p>北辰公司屏幕ERP系统 · 高效的企业资源计划管理平台</p>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard {
  padding: 0;
}

.welcome {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px;
}

.welcome-icon {
  font-size: 56px;
  color: #409eff;
}

.welcome-text h2 {
  margin: 0 0 10px;
  font-size: 22px;
  color: #303133;
}

.welcome-text p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}
</style>
