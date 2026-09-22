<script setup lang="ts">
// 加工退货（拆分还料）（2026-09-23 用户要求：原 780px 弹框改为独立页面）
// —— 从「成品收货」页进入（orderId 走路径），按订单产品行逐规格拆数量、选扣减的成品仓，
//    逐规格保存为加工退货草稿（审核时统一落账），与弹框口径完全一致。
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'

const route = useRoute()
const router = useRouter()
const orderId = Number(route.params.orderId)

const loading = ref(false)
const saving = ref(false)
const items = ref<any[]>([])
const warehouseId = ref<number>()

/** 扣减仓库限定为我方（自有）成品仓 —— 与成品收货页同一口径 */
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED' } })

const emptyStock = () => ({ a: 0, b: 0, c: 0, defect: 0 })
const backPath = () => `/outsource/order/delivery/${orderId}`

async function loadProducts() {
  loading.value = true
  try {
    const prods: any[] = await request.get<any, any>(`/outsource/order/${orderId}/products`) || []
    items.value = (prods || []).map((p: any) => ({
      productId: p.id, productName: p.productName, masterId: p.productId,
      aQty: undefined as any, bQty: undefined as any, cQty: undefined as any, defectQty: undefined as any,
      stocks: emptyStock()
    }))
  } catch { items.value = [] } finally { loading.value = false }
}

/** 换仓：按产品主数据ID查该仓各规格库存（productName 是快照名，不能用名称匹配） */
function onWhChange(whId: number) {
  warehouseId.value = whId
  if (!whId) { items.value = items.value.map((it: any) => ({ ...it, stocks: emptyStock() })); return }
  request.get<any, any>('/warehouse/stock/page', { params: { pageSize: 500, stockType: 'PRODUCT' } }).then((r: any) => {
    const stocks = r?.records || []
    const qmap: Record<string, 'a' | 'b' | 'c' | 'defect'> = { A: 'a', B: 'b', C: 'c', DEFECT: 'defect' }
    items.value = items.value.map((it: any) => {
      const own = emptyStock()
      for (const row of stocks.filter((s: any) => s.warehouseId === whId && s.productId === it.masterId)) {
        const key = qmap[row.qualityType]; if (key) own[key] = Number(row.quantity || 0)
      }
      return { ...it, stocks: own }
    })
  }).catch(() => { /* 库存拉取失败不影响填写 */ })
}

async function submit() {
  const data: any[] = []
  for (const r of items.value) {
    for (const [qualityType, qtyKey] of [['A', 'aQty'], ['B', 'bQty'], ['C', 'cQty'], ['DEFECT', 'defectQty']] as const) {
      const q = Number(r[qtyKey]); if (q > 0) data.push({ productId: r.productId, qualityType, quantity: q })
    }
  }
  if (data.length === 0) { ElMessage.warning('请输入加工退货数量'); return }
  if (!warehouseId.value) { ElMessage.warning('请选择加工退货仓库'); return }
  saving.value = true
  try {
    // 逐规格存草稿，审核时统一落账
    for (const r of data) {
      await request.post(`/outsource/order-delivery/return-defect/${orderId}`, { productId: r.productId, qualityType: r.qualityType, quantity: r.quantity, warehouseId: warehouseId.value })
    }
    ElMessage.success('加工退货草稿已保存，请在收货记录中审核')
    router.push(backPath())
  } catch (e: any) { ElMessage.error(e?.message || '加工退货失败') } finally { saving.value = false }
}
onMounted(loadProducts)
</script>

<template>
  <div class="p" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">加工退货（拆分还料）</span>
          <el-button @click="router.push(backPath())">返回</el-button>
        </div>
      </template>

      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
        title="按规格拆数量后保存为加工退货草稿（审核时才扣减成品、按 BOM 还料并冲减应付）。" />

      <el-form-item label="加工退货仓库" style="margin-bottom:12px">
        <RemoteSelect v-model="warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>`${row.warehouseName} (${row.code})`" style="width:100%" placeholder="选择扣减的成品仓库" @change="onWhChange" />
      </el-form-item>

      <el-table :data="items" border size="small">
        <el-table-column prop="productName" label="产品" min-width="160" />
        <el-table-column label="A规" width="130"><template #default="{ row }"><div style="font-size:var(--app-font-xs);color:var(--app-text-regular)">库存 {{ row.stocks?.a ?? 0 }}</div><el-input-number v-model="row.aQty" size="small" :controls="false" :precision="0" :step="1" style="width:100%" /></template></el-table-column>
        <el-table-column label="B规" width="130"><template #default="{ row }"><div style="font-size:var(--app-font-xs);color:var(--app-text-regular)">库存 {{ row.stocks?.b ?? 0 }}</div><el-input-number v-model="row.bQty" size="small" :controls="false" :precision="0" :step="1" style="width:100%" /></template></el-table-column>
        <el-table-column label="C规" width="130"><template #default="{ row }"><div style="font-size:var(--app-font-xs);color:var(--app-text-regular)">库存 {{ row.stocks?.c ?? 0 }}</div><el-input-number v-model="row.cQty" size="small" :controls="false" :precision="0" :step="1" style="width:100%" /></template></el-table-column>
        <el-table-column label="不良" width="130"><template #default="{ row }"><div style="font-size:var(--app-font-xs);color:var(--app-text-regular)">库存 {{ row.stocks?.defect ?? 0 }}</div><el-input-number v-model="row.defectQty" size="small" :controls="false" :precision="0" :step="1" style="width:100%" /></template></el-table-column>
      </el-table>

      <div style="margin-top:12px;display:flex;gap:8px;justify-content:flex-end">
        <el-button @click="router.push(backPath())">取消</el-button>
        <el-button type="warning" :loading="saving" @click="submit">确认加工退货</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>.p{display:flex;flex-direction:column;gap:12px}</style>
