<template>
  <el-select
    :model-value="model"
    :multiple="multiple"
    filterable
    clearable
    :loading="loading"
    :placeholder="placeholder"
    :collapse-tags="collapseTags"
    @update:model-value="onUpdate"
    @visible-change="onVisible"
    @filter-method="onSearch"
  >
    <el-option v-for="o in options" :key="getVal(o)" :label="getLabel(o)" :value="getVal(o)" :disabled="optionDisabled ? optionDisabled(o) : false" />
    <slot />
  </el-select>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import request from '@/utils/request'

// Odoo 风格下拉框：不预缓存全量，展开 / 输入搜索时实时查库。
// 数据保存在组件本地 state，不写入全局 optionsStore，因此天然支持多用户/多标签页实时一致。
const props = withDefaults(defineProps<{
  modelValue?: any
  fetch: (keyword: string) => Promise<any>   // 返回 {records:[]} 或 []
  valueKey?: string
  labelKey?: string | ((row: any) => string)
  placeholder?: string
  multiple?: boolean
  collapseTags?: boolean
  pageSize?: number
  lazy?: boolean                              // true: 展开才查（Odoo 默认）；false: 挂载即查
  optionDisabled?: (row: any) => boolean      // 自定义禁用（如子物料下拉排除自身）
}>(), {
  valueKey: 'id',
  labelKey: 'name',
  placeholder: '请选择',
  multiple: false,
  collapseTags: true,
  pageSize: 500,
  lazy: true,
})

const emit = defineEmits<{
  (e: 'update:modelValue', v: any): void
  (e: 'pick', opts: any[]): void
}>()

const model = ref<any>(props.modelValue)
watch(() => props.modelValue, v => { model.value = v })

const options = ref<any[]>([])
const loading = ref(false)

function getVal(o: any) { return o?.[props.valueKey] }
function getLabel(o: any) {
  return typeof props.labelKey === 'function' ? props.labelKey(o) : o?.[props.labelKey]
}

async function load(kw: string) {
  loading.value = true
  try {
    const res = await props.fetch(kw)
    options.value = res?.records || res || []
  } catch (e) { options.value = [] }
  finally { loading.value = false }
}

// 展开即查全量（Odoo：每次展开都查，保证看到其他用户刚新增的数据）
function onVisible(v: boolean) {
  if (v && props.lazy) load('')
}
// 输入即查库（Odoo 搜索即查）
function onSearch(kw: string) {
  load(kw)
}
function onUpdate(val: any) {
  model.value = val
  emit('update:modelValue', val)
  const arr = Array.isArray(val) ? val : [val]
  const picked = arr.map(v => options.value.find(o => getVal(o) === v)).filter(Boolean)
  emit('pick', picked)
}

// 编辑时已有值：挂载即查一次，确保已选项显示 label（仅显示用途）
onMounted(() => {
  if (!props.lazy) { load(''); return }
  const mv = props.modelValue
  if (mv != null && mv !== '' && !(Array.isArray(mv) && mv.length === 0)) load('')
})
</script>
