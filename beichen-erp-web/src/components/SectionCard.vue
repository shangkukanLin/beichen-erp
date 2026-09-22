<script setup lang="ts">
/**
 * 内容分块卡片（2026-09-23 次级页面统一模板）。
 * 替代各页重复书写的 <el-card shadow="never"> + <template #header> + 手写 card-header 布局，
 * 让"标题在左、右侧动作在右"的卡片头在全站保持一致。
 *
 * 用法：
 * ```vue
 * <SectionCard title="产品明细">
 *   <template #extra><el-button type="primary" size="small">添加产品</el-button></template>
 *   ... 内容 ...
 * </SectionCard>
 * ```
 */
defineProps<{
  /** 分块标题；不传且没有 #extra 插槽时不渲染卡片头 */
  title?: string
}>()
</script>

<template>
  <el-card shadow="never" class="section-card">
    <template v-if="title || $slots.extra" #header>
      <div class="section-card__header">
        <span class="section-card__title">{{ title }}</span>
        <slot name="extra" />
      </div>
    </template>
    <slot />
  </el-card>
</template>
