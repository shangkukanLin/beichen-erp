<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template>
  <div style="display:flex;align-items:center;justify-content:center;height:300px;color:#909399;font-size:18px">
    {{ (route.meta.title as string) || '页面开发中' }}
  </div>
</template>
