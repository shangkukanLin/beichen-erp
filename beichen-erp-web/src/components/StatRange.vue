<script setup lang="ts">
import { computed } from 'vue'

/**
 * 统计区间选择器（**全站统一组件**，2026-09-15 用户要求：统一使用「利润表」那套 UI）。
 *
 * 收口了这几件事（唯一真源，改这里即全站生效）：
 * 1. 文案「统计区间」与 **7 项固定顺序**：昨日 / 今日 / 本周 / 本月 / 本季 / 本年 / 自定义；
 * 2. 仅 `自定义` 时显示日期区间（宽度 260px、左边距 12px、不可清空、value-format=YYYY-MM-DD）；
 * 3. **切换预设时自动清空已选日期**（与利润表一致）；
 * 4. 日期选满 2 个才触发；统一 emit `change`，页面只写一句 `load()`。
 *
 * 用法：
 *   <StatRange v-model:preset="preset" v-model:range="range" @change="loadData" />
 * 可选：`:presets="['month','quarter','year','custom']"`（子集）、`size="small"`、`label=""`（隐藏文案）。
 */
const PRESET_LABELS: Record<string, string> = {
  yesterday: '昨日', today: '今日', week: '本周', month: '本月',
  quarter: '本季', year: '本年', custom: '自定义'
}
/** 默认全部预设（顺序即 UI 顺序） */
const ALL_PRESETS = ['yesterday', 'today', 'week', 'month', 'quarter', 'year', 'custom']

const props = withDefaults(defineProps<{
  /** 当前预设（v-model:preset） */
  preset: string
  /** 自定义日期区间（v-model:range），非自定义时为 null */
  range?: [string, string] | null
  /** 允许出现的预设子集；默认全部 7 项 */
  presets?: string[]
  /** 尺寸，默认与利润表一致（default） */
  size?: 'default' | 'small' | 'large'
  /** 前置文案，默认「统计区间」；传空串可隐藏 */
  label?: string
}>(), {
  range: null,
  // ⚠️ 这里必须是**内联字面量**：defineProps/withDefaults 会被提升到 setup 之外，
  // 引用 `<script setup>` 内声明的常量（如 ALL_PRESETS）会被 Vite 编译器拒绝（类型检查查不出来）。
  presets: () => ['yesterday', 'today', 'week', 'month', 'quarter', 'year', 'custom'],
  size: 'default',
  label: '统计区间'
})

const emit = defineEmits<{
  (e: 'update:preset', v: string): void
  (e: 'update:range', v: [string, string] | null): void
  (e: 'change', v: { preset: string; start?: string; end?: string }): void
}>()

/** 预设子集 → 选项（过滤未知 key，保持 PRESET_LABELS 的固定顺序） */
const options = computed(() =>
  ALL_PRESETS.filter((k) => props.presets.includes(k)).map((k) => ({ value: k, label: PRESET_LABELS[k] }))
)
/** El 组件尺寸：default 时不传，保持 Element Plus 默认外观（与利润表一致） */
const elSize = computed(() => (props.size === 'default' ? undefined : props.size))

function onPresetChange(v: string | number | boolean | undefined) {
  const next = String(v)
  emit('update:preset', next)
  emit('update:range', null)          // 切换预设 → 清空已选日期（与利润表一致）
  if (next !== 'custom') emit('change', { preset: next })
}
function onRangeChange(v: any) {
  if (Array.isArray(v) && v.length === 2) {
    emit('update:range', [v[0], v[1]])
    emit('change', { preset: 'custom', start: v[0], end: v[1] })
  }
}
</script>

<template>
  <span class="stat-range">
    <span v-if="label" class="stat-range-label">{{ label }}</span>
    <el-radio-group :model-value="preset" :size="elSize" @change="onPresetChange">
      <el-radio-button v-for="o in options" :key="o.value" :value="o.value">{{ o.label }}</el-radio-button>
    </el-radio-group>
    <el-date-picker v-if="preset === 'custom'" :model-value="range" type="daterange" value-format="YYYY-MM-DD"
      :size="elSize" start-placeholder="开始日期" end-placeholder="结束日期" :clearable="false"
      class="stat-range-date" @change="onRangeChange" />
  </span>
</template>

<style scoped>
/* 间距与利润表原实现一致：文案右间距 8px、日期选择器左间距 12px */
.stat-range { display: inline-flex; align-items: center; }
.stat-range-label { margin-right: 8px; font-size: var(--app-font-base); color: var(--app-text-secondary); }
.stat-range-date { width: 260px; margin-left: 12px; }
</style>
