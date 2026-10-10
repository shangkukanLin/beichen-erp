<!--
  库存金额合计条（2026-10-09 用户需求：物料仓 / 成品仓库存金额）。
  口径唯一来源 = 后端 /warehouse/stock/amount-summary（见 StockCosts.CALIBER），本组件只负责展示：
    · 合计 + 成品 / 物料 分项（mode 决定突出哪个）
    · 「成本未维护」明示（**未计入有效合计**，避免金额偏小却不自知）
    · 悬停给出完整口径文案与**按仓库**明细

  为什么不放进列表做成一列：成品库存主表 12 列实测 colSum = avail（余量 0），再加一列必然横向滚动，
  与项目既有"表格一行放得下"的约定冲突（列宽够的页面——物料库存、仓库详情、明细页——是直接加列的）。
-->
<template>
  <div class="stock-amount-bar" v-loading="loading">
    <!-- 2026-10-10 用户口径：这个数字是**合计**（成品页=成品额、物料页=物料额、all=两者之和）
         ⇒ 标签由「库存金额」改成「**库存总金额**」，并把**标签与金额都标红**以示强调 ✓。 -->
    <span class="label">库存总金额</span>
    <span class="total">{{ fmtMoney(total) }}</span>
    <!-- 只在「不限定范围」时才展开成品/物料分项：范围已限定（成品页=成品额、物料页=物料额）时，
         分项与上面的合计是**同一个数字**，重复一遍纯属噪声（2026-10-09 用户反馈）。 -->
    <span class="parts" v-if="mode === 'all'">
      成品 {{ fmtMoney(productAmount) }} · 物料 {{ fmtMoney(materialAmount) }}
    </span>
    <el-tag v-if="missingHint" size="small" type="warning" effect="plain" :title="missingHint">成本未维护</el-tag>
    <el-popover placement="bottom-start" :width="340" trigger="hover">
      <template #reference>
        <span class="caliber">口径</span>
      </template>
      <div class="caliber-box">
        <p v-if="scopeLabel" class="scope-text">本页范围：{{ scopeLabel }}</p>
        <p class="caliber-text">{{ caliber || '——' }}</p>
        <p v-if="missingHint" class="missing-text">{{ missingHint }}</p>
        <div v-if="byWarehouse.length" class="wh-list">
          <div class="wh-head">按仓库</div>
          <div v-for="w in byWarehouse" :key="String(w.warehouseId)" class="wh-row">
            <span class="wh-name">{{ w.warehouseName || '未命名仓库' }}</span>
            <span class="wh-amt">{{ fmtMoney(w.totalAmount) }}</span>
          </div>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import request from '@/utils/request'
import { fmtMoney } from '@/utils/format'

const props = withDefaults(defineProps<{
  /** 只统计某个仓库（仓库详情页用） */
  warehouseId?: number | string
  /** 多仓筛选（与列表页查询条一致；数组或已逗号拼接的字符串都可） */
  warehouseIds?: string | number[]
  /** 突出哪一项：all=合计、product=成品、material=物料 */
  mode?: 'all' | 'product' | 'material'
}>(), { mode: 'all' })

const loading = ref(false)
const data = ref<any>({})

const total = computed(() => props.mode === 'product'
  ? (data.value.productAmount || 0)
  : props.mode === 'material'
    ? (data.value.materialAmount || 0)
    : (data.value.totalAmount || 0))
const productAmount = computed(() => data.value.productAmount || 0)
const materialAmount = computed(() => data.value.materialAmount || 0)
/** 限定了范围时，把"看的是哪一类"写进口径弹层（界面上不再重复数字，避免看起来像两份金额） */
const scopeLabel = computed(() => props.mode === 'product' ? '仅成品' : props.mode === 'material' ? '仅物料' : '')
const showProduct = computed(() => props.mode !== 'material')
const showMaterial = computed(() => props.mode !== 'product')
const caliber = computed(() => data.value.caliber || '')
const byWarehouse = computed<any[]>(() => (Array.isArray(data.value.byWarehouse) ? data.value.byWarehouse : []))

/** 「成本未维护」提示：数量 + SKU 数（后端给的是原样数量，这里直接展示，不做二次换算） */
const missingHint = computed(() => {
  const pq = Number(data.value.productCostMissingQty || 0)
  const mq = Number(data.value.materialCostMissingQty || 0)
  if (!pq && !mq) return ''
  const parts: string[] = []
  if (pq) parts.push(`成品 ${pq} 件 / ${Number(data.value.productCostMissingSkuCount || 0)} 个 SKU`)
  if (mq) parts.push(`物料 ${mq} 件 / ${Number(data.value.materialCostMissingSkuCount || 0)} 个 SKU`)
  return '成本未维护（按 0 计、未计入合计）：' + parts.join('，')
})

async function load() {
  loading.value = true
  try {
    const params: any = {}
    if (props.warehouseId) params.warehouseId = props.warehouseId
    const ids = props.warehouseIds
    if (ids && (Array.isArray(ids) ? ids.length : String(ids).length)) {
      params.warehouseIds = Array.isArray(ids) ? ids.join(',') : ids
    }
    const res = await request.get<any, any>('/warehouse/stock/amount-summary', { params })
    data.value = res || {}
  } catch {
    // 静默：金额拿不到就不显示数字，绝不弹错（页面里大量守卫断言"无 JS/API 报错"）
    data.value = {}
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => [props.warehouseId, props.warehouseIds], load)
defineExpose({ reload: load })
</script>

<style scoped>
.stock-amount-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 6px 10px;
  margin-bottom: 8px;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 13px;
}
/* 2026-10-10 用户口径：**标签与金额都标红**（强调这是"总金额"）。
   颜色取项目语义色变量、并带字面兜底 ⇒ 变量缺失时也一定是红色 ✓。 */
.stock-amount-bar .label { color: var(--app-color-danger, #f56c6c); }
.stock-amount-bar .total { font-size: 15px; font-weight: 600; color: var(--app-color-danger, #f56c6c); }
.stock-amount-bar .parts { color: #606266; }
.stock-amount-bar .caliber {
  color: #409eff;
  cursor: help;
  border-bottom: 1px dashed #a0cfff;
}
.caliber-box p { margin: 0 0 6px; line-height: 1.6; }
.caliber-text { color: #606266; }
.missing-text { color: #e6a23c; }
.wh-list { border-top: 1px solid #ebeef5; padding-top: 6px; }
.wh-head { color: #909399; margin-bottom: 4px; }
.wh-row { display: flex; justify-content: space-between; line-height: 1.8; }
.wh-name { color: #606266; }
.wh-amt { color: #303133; }
</style>
