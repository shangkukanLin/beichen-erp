<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template>
  <div style="display:flex;align-items:center;justify-content:center;height:300px;color:var(--app-text-secondary);font-size:var(--app-font-xl)">
    {{ (route.meta.title as string) || '页面开发中' }}
  </div>
</template>
