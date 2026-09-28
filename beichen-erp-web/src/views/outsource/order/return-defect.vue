<script setup lang="ts">
// 加工退货（拆分还料）（2026-09-23 用户要求：原 780px 弹框改为独立页面）
// —— 从「成品收货」页进入（orderId 走路径），按订单产品行逐规格拆数量、选扣减的成品仓，
//    逐规格保存为加工退货草稿（审核时统一落账），与弹框口径完全一致。
//
// 2026-09-28（用户口径「在关联退货页面上，也可以新增关联退货」）：本页**被两个入口共用** ——
//   ①「成品收货」列表/详情（不带 query，行为一字不变）；
//   ②「关联退货」台账（`?from=return-order`，见 return-order/index.vue 的 openLinkedAdd）：
//      该入口只负责选加工单，录入仍走本页（表单只有一条路，不复制）⇒ 返回/提交后按 from 回台账。
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'
import { applyPageTitle } from '@/utils/pageTitle'
import { OUTSOURCE_RETURN_ORDER_DIRTY_KEY } from '@/api/enums'

const route = useRoute()
const router = useRouter()
const tabStore = useTabStore()
const orderId = Number(route.params.orderId)
/** 来源=关联退货台账（`?from=return-order`）：返回与提交后都回台账，并置脏标志让它刷新 */
const fromLedger = computed(() => String(route.query.from || '') === 'return-order')
/**
 * 页面名（2026-09-28 用户口径：「页头标题也要跟随」——与物料侧同改）：**页头 / 顶部页签 / 浏览器标签页三处同源**。
 * <p>成品收货入口（不带 `from`）保持路由 `meta.title`「加工退货（拆分还料）」不变（原行为、既有断言依赖）；
 * 关联退货台账入口（`?from=return-order`）统一为「新增关联加工退货」。</p>
 */
const pageTitle = computed(() => (fromLedger.value ? '新增关联加工退货' : (route.meta.title as string) || ''))

const loading = ref(false)
const saving = ref(false)
const items = ref<any[]>([])
const warehouseId = ref<number>()

/** 扣减仓库限定为我方（自有）成品仓 —— 与成品收货页同一口径 */
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED' } })

const emptyStock = () => ({ a: 0, b: 0, c: 0, defect: 0 })
/** 返回去向按来源分流：台账入口回「关联退货」，成品收货入口回该单收货详细（原口径） */
const backPath = () => fromLedger.value ? '/outsource/return-order' : `/outsource/order/delivery/${orderId}`

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
  // 数据加载完成 ⇒ 建立"未保存"基线（必须在加载之后，否则会把回填误判成用户修改）
  takeBaseline()
}

/**
 * 未保存拦截（2026-09-23 统一模板）：本页逐规格填退货数量 ⇒ 属"能改数据"，接守卫。
 * ⚠️ 必须写在 items / warehouseId 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ items: items.value, warehouseId: warehouseId.value }))

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
    ElMessage.success(fromLedger.value
      ? '加工退货草稿已保存，请在「关联退货」列表审核'
      : '加工退货草稿已保存，请在收货记录中审核')
    // 提交成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本页签并回原页
    markClean()
    // 台账入口：置脏标志让「关联退货」列表在 onActivated 时自动刷新（与新增无单退货同范式）
    if (fromLedger.value) sessionStorage.setItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY, '1')
    tabStore.closeTabAndBack(route.path)
    router.push(backPath())
  } catch (e: any) { ElMessage.error(e?.message || '加工退货失败') } finally { saving.value = false }
}
onMounted(() => {
  // 页签 + 浏览器标题取同一个 pageTitle（页头由模板 :title 绑定同一个值）⇒ **三处同源**。
  // ⚠️ 两个入口共用同一条路由 path（页签按 path 去重）⇒ **两个入口都要同步一次**：
  //   只在台账入口改名会让"上一次访问的标题"残留在页签上（页头/浏览器标题已是 meta.title）。
  tabStore.updateTabTitle(route.path, pageTitle.value)
  applyPageTitle(pageTitle.value)
  loadProducts()
})
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作（确认加工退货） -->
  <PageShell :title="pageTitle" :loading="loading" :back-fallback="fromLedger ? '/outsource/return-order' : '/outsource/order/delivery'">
    <template #actions>
      <el-button type="warning" :loading="saving" @click="submit">确认加工退货</el-button>
    </template>

    <el-card shadow="never">
      <template #header>
        <span style="font-weight:600">{{ pageTitle }}</span>
      </template>

      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
        title="按规格拆数量后保存为加工退货草稿（审核时才扣减成品、按 BOM 还料并冲减应付）。" />

      <!-- label 宽度显式给 **lg 档**（2026-09-28 用户实测）：本页没有 el-form 包裹，
           page.css 的 `.page-shell .el-form-item__label { width: 90px }` 会兜住 ⇒ 「加工退货仓库」6 字被挤成两行 -->
      <el-form-item label="加工退货仓库" label-width="var(--app-label-width-lg)" style="margin-bottom:12px">
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
      </div>
    </el-card>
  </PageShell>
</template>

<style scoped>/* 页头已统一到全局骨架（PageShell）；原 .p 局部样式已删除 */</style>
