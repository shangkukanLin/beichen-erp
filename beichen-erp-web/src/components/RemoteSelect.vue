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
import { ref, watch, onMounted, onUnmounted } from 'vue'
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
  preset?: any                                // 编辑回显预置当前项 {valueKey, labelKey}，后端已随详情返回名称，免查库
  disableCache?: boolean                      // true: 不启用会话缓存，每次展开实时查库（用于 fetch 依赖行参数的过滤下拉）
}>(), {
  valueKey: 'id',
  labelKey: 'name',
  placeholder: '请选择',
  multiple: false,
  collapseTags: true,
  pageSize: 500,
  lazy: true,
  disableCache: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', v: any): void
  (e: 'pick', opts: any[]): void
}>()

const model = ref<any>(props.modelValue)
/** 当前 options 是否已包含所选值（用于编辑回显判断） */
function isResolved(v: any): boolean {
  if (v == null || v === '') return true
  if (Array.isArray(v)) {
    if (v.length === 0) return true
    return v.every((x: any) => options.value.some(o => getVal(o) === x))
  }
  return options.value.some(o => getVal(o) === v)
}
/** 用后端随详情返回的预置项直接回填，避免额外查库；命中返回 true */
function seedPreset(v: any): boolean {
  if (props.preset == null || v == null || v === '') return false
  const pv = getVal(props.preset)
  const targets = Array.isArray(v) ? v : [v]
  if (!targets.includes(pv)) return false
  // 规范化为 {valueKey, labelKey}：label 为空时兜底取 preset.name，避免与 label-key 不一致时显示成 ID
  const normalized: any = { ...props.preset, [props.valueKey]: pv, [props.labelKey]: props.preset[props.labelKey] ?? props.preset.name ?? '' }
  if (targets.every(t => options.value.some(o => getVal(o) === t))) return true
  if (Array.isArray(v)) {
    if (!options.value.some(o => getVal(o) === pv)) options.value = [...options.value, normalized]
  } else {
    options.value = [...options.value, normalized]
  }
  return true
}
watch(() => props.modelValue, v => {
  model.value = v
  // 编辑回显：后端已随详情返回名称时，优先用 preset 免查库；否则懒查一次解析 label
  if (!isResolved(v) && !seedPreset(v) && props.lazy) load('')
})
// 父组件带着正确名称重渲染后，立即把 preset 注入选项，避免依赖下拉的类型过滤
watch(() => props.preset, () => {
  if (!isResolved(props.modelValue)) seedPreset(props.modelValue)
})

const options = ref<any[]>([])
const loading = ref(false)

function getVal(o: any) { return o?.[props.valueKey] }
function getLabel(o: any) {
  return typeof props.labelKey === 'function' ? props.labelKey(o) : o?.[props.labelKey]
}

// 会话级缓存：已成功加载过全量（无关键字）后，再次展开直接复用，避免网络不佳时每次展开都请求。
// 输入关键字搜索仍实时查库；顶栏"刷新数据"派发 refresh:dropdown-data 事件清空缓存强制重查。
let loadedOnce = false
let cachedAll: any[] = []
async function load(kw: string) {
  if (kw === '' && loadedOnce) { options.value = cachedAll; return }
  loading.value = true
  try {
    const res = await props.fetch(kw)
    options.value = res?.records || res || []
    if (kw === '' && !props.disableCache) { loadedOnce = true; cachedAll = options.value }
  } catch (e) { options.value = [] }
  finally { loading.value = false }
}

// 刷新下拉缓存（新增/修改页"刷新数据"按钮触发）：清空已缓存数据并重新加载
function onRefreshDropdownData() {
  loadedOnce = false
  cachedAll = []
  options.value = []
  const mv = props.modelValue
  if (mv != null && mv !== '' && !(Array.isArray(mv) && mv.length === 0)) {
    load('')
  }
}

// 展开即查全量（走会话级缓存，已加载过则不再请求）
function onVisible(v: boolean) {
  if (v && props.lazy) load('')
}
// 输入即查库（搜索始终实时，能看到新增数据）
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

// 编辑时已有值：后端已返回名称则直接预置免查库；否则挂载即查一次以确保显示 label
onMounted(() => {
  window.addEventListener('refresh:dropdown-data', onRefreshDropdownData)
  if (!props.lazy) { load(''); return }
  const mv = props.modelValue
  if (mv != null && mv !== '' && !(Array.isArray(mv) && mv.length === 0)) {
    if (!seedPreset(mv)) load('')
  }
})
onUnmounted(() => {
  window.removeEventListener('refresh:dropdown-data', onRefreshDropdownData)
})
</script>
