<script setup lang="ts">
// 盘点明细（2026-09-23 用户要求：原 900px「盘点明细」弹框改为独立页面）
// —— 表头字段（仓库/单号/状态/范围的）走 URL 查询参数（列表页自己知道），明细走接口 ⇒ 刷新/直链都可用。
//    可编辑仅限草稿（与弹框口径一致）；物料盘点不分品质、无 SKU。
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getStockTakeItems, saveStockTakeItems, type StockTakeItem } from '@/api/inventory'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const items = ref<StockTakeItem[]>([])

const id = Number(route.params.id)
const isMaterial = computed(() => route.query.scope === 'MATERIAL')
const status = computed(() => String(route.query.status || ''))
const warehouseName = computed(() => String(route.query.warehouseName || '-'))
const takeNo = computed(() => String(route.query.takeNo || ''))
const editable = computed(() => status.value === 'DRAFT')

function nameOf(it: StockTakeItem) { return it.productName || it.materialName || '' }
/** 实时差异 = 实盘 − 账面 */
function diffOf(it: StockTakeItem) {
  const a = Number(it.actualQuantity || 0), b = Number(it.bookQuantity || 0)
  return Math.round((a - b) * 10000) / 10000
}
const diffRows = computed(() => items.value.filter(it => diffOf(it) !== 0))

async function loadItems() {
  if (!id) return
  loading.value = true
  try { items.value = await getStockTakeItems(id) } catch { items.value = [] } finally { loading.value = false }
}
async function saveItems() {
  try {
    await saveStockTakeItems(id, items.value)
    ElMessage.success('实盘数量已保存')
    await loadItems()
  } catch { /* 失败提示由 request 拦截器统一给出 */ }
}
onMounted(loadItems)
</script>

<template>
  <div class="p" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">盘点明细 - {{ warehouseName }}<template v-if="takeNo">（{{ takeNo }}）</template></span>
          <el-button @click="router.back()">返回</el-button>
        </div>
      </template>

      <div style="margin-bottom:8px;font-size:var(--app-font-base)">
        账面数量来自建单时快照；修改实盘数量后自动算差异。当前差异行：<b :style="{color: diffRows.length ? 'var(--app-color-danger)' : ''}">{{ diffRows.length }}</b>
        <span v-if="!editable" style="margin-left:8px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">（非草稿单据只能查看）</span>
      </div>

      <el-table :data="items" border stripe size="small" max-height="560">
        <!-- SKU 仅成品有；品质仅成品区分（物料库存不分品质，恒为良品） -->
        <el-table-column v-if="!isMaterial" prop="sku" label="SKU" width="130" />
        <el-table-column :label="isMaterial ? '物料' : '名称'" min-width="160" show-overflow-tooltip><template #default="{row}">{{ nameOf(row) }}</template></el-table-column>
        <el-table-column v-if="!isMaterial" prop="qualityType" label="品质" width="80" align="center" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="bookQuantity" label="账面数量" width="100" align="right" />
        <el-table-column label="实盘数量" width="140" align="right">
          <template #default="{row}">
            <el-input-number v-model="row.actualQuantity" :min="0" :precision="0" :step="1" controls-position="right" size="small" style="width:100%" :disabled="!editable" />
          </template>
        </el-table-column>
        <el-table-column label="差异" width="100" align="right">
          <template #default="{row}">
            <span :style="{ color: diffOf(row) ? 'var(--app-color-danger)' : '', fontWeight: diffOf(row) ? 'bold' : '' }">{{ diffOf(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="{row}"><el-input v-model="row.remark" size="small" :disabled="!editable" /></template>
        </el-table-column>
      </el-table>

      <div style="margin-top:12px;display:flex;gap:8px;justify-content:flex-end">
        <el-button @click="router.back()">返回</el-button>
        <el-button v-if="editable" type="primary" @click="saveItems">保存实盘</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>.p{display:flex;flex-direction:column;gap:12px}</style>
