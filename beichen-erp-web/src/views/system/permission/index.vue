<template>
  <div class="permission-page">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="角色管理" name="role">
        <RoleIndex />
      </el-tab-pane>
      <el-tab-pane label="菜单管理" name="menu">
        <MenuIndex />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import RoleIndex from '@/views/system/role/index.vue'
import MenuIndex from '@/views/system/menu/index.vue'

const activeTab = ref('role')
</script>
