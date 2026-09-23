<script setup lang="ts">
// 2026-09-20（F7-159）：显式声明组件名（便于 DevTools 辨认与将来按名 exclude）
defineOptions({ name: 'OutsourceMaterialReturnAdd' })
import { localDate } from '@/utils/date'
import { reactive, ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, MaterialReturnType, MaterialReturnTypeLabel, MaterialOrderStatus, MaterialOrderStatusLabel } from '@/api/enums'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useTabStore } from '@/stores/tabs'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'

const router = useRouter()
const route = useRoute()
const tabStore = useTabStore()
/**
 * 来源参数（2026-09-17）：从「物料收货」发起退货时带出。
 * <p>?sourceDeliveryId= 按某张收料单退（带出供应商/源仓/物料与「已收 − 已退 = 可退」）；
 * ?supplierId= 订单维度入口，只预填退回对象。</p>
 */
const prefillDeliveryId = Number(route.query.sourceDeliveryId) || 0
const prefillSupplierId = Number(route.query.supplierId) || 0
/** ?returnType=REPAIR 直接从列表页签的「新增维修退货」进入（2026-09-17 两类型；术语 2026-09-21 统一为"维修退货"） */
const prefillReturnType = String(route.query.returnType || '')
/**
 * 编辑草稿（D 档 2026-09-21，与加工退货页对称）：路由 `/outsource/material-return/edit/:id`，0 = 新增。
 * <p>后端 `PUT /api/outsource/material-return/{id}` 早已存在（仅 DRAFT 可编辑，明细整体替换，草稿不动库存/应付），
 * 本页只补前端回填与提交分支。</p>
 */
const editId = Number(route.params.id) || 0
const editing = ref(false)
/** 编辑时保留原「来源收料单」：显式回传（后端 updateById 会忽略 null，但显式传值更稳，不会被误清） */
const editSourceDeliveryId = ref<number | null>(null)

const form = reactive({
  // 退货类型（2026-09-17；术语 2026-09-21 统一为"维修退货"）：REFUND 退货退款（冲减应付）/ REPAIR 维修退货（不冲应付，修好登记返回入库）
  returnType: (prefillReturnType === MaterialReturnType.REPAIR ? MaterialReturnType.REPAIR : MaterialReturnType.REFUND) as string,
  supplierId: undefined as any, fromWarehouseId: undefined as any, returnDate: localDate(), remark: '',
  // 关联物料订单（2026-09-17 维修退货闭环）：可清空；不选=不关联（靠本单「送修/已返回」跟踪）
  materialOrderId: undefined as any
})
/** 维修退货：不冲减应付；审核后在详情页登记「维修返回」把物料入回来 */
const isRepair = computed(() => form.returnType === MaterialReturnType.REPAIR)
const warehouseOptions = ref<any[]>([])
const stockList = ref<any[]>([])
const loading = ref(false)
const submitting = ref(false)

/**
 * 退回对象 / 维修供应商 实时查库（Odoo 风格）。
 * <p>2026-09-21（用户口径）：**物料退货只允许退给辅料商 + 供应商，不能退给供货商** ⇒
 * `excludeSupplierType: 'product'`（供货商=成品商 product；其余 辅料商/方案商/加工厂 都放行）。
 * 后端 `create` / `update` 有同一口径的兜底校验 ✓。</p>
 */
const fetchSuppliers = (kw: string) =>
  request.get('/supplier/page', { params: { pageSize: 500, name: kw, excludeSupplierType: 'product' } })

// ===== 关联物料订单（维修退货闭环）=====
/** 选中的物料订单行（含状态，用于提示"扣减订单收料 / 靠本单跟踪"两种收尾方式） */
const pickedOrder = ref<any>(null)
/** 该物料商的物料订单（收货中/已完成）：收货中的单审核会扣减收料数，已完成的靠本单跟踪 */
// 期 3（2026-09-19 读隔离）：改走本页前缀（原读 /outsource/material-order/page 需 outsource:material-order）
const fetchMaterialOrders = (kw: string) => request.get('/outsource/material-return/material-orders', {
  params: {
    pageSize: 500, code: kw || undefined,
    supplierId: form.supplierId || undefined,
    statuses: `${MaterialOrderStatus.RECEIVING},${MaterialOrderStatus.FINISHED}`
  }
})
// 状态可能缺失（preset 回填项只带 id/code）→ 缺状态时只显示单号，避免出现"（undefined）"
function materialOrderLabel(o: any) {
  const st = o && o.status ? (MaterialOrderStatusLabel[o.status] || o.status) : ''
  return st ? `${o.code}（${st}）` : `${o.code}`
}
const orderStatus = computed(() => pickedOrder.value?.status || '')
/** 收尾方式提示：未完成=扣减订单收料（修好回补）；已完成/未关联=靠本单「送修/已返回」+ 结案跟踪 */
const orderHint = computed(() => {
  if (!isRepair.value) return ''
  if (!form.materialOrderId || !pickedOrder.value) return '未关联物料订单：返回情况在本单「送修 / 已返回」中跟踪，全部返回后（未返回=0）可结案。'
  if (orderStatus.value === MaterialOrderStatus.RECEIVING) return '该订单未完成（收货中）：审核会把送修数量从该订单收料数中扣减（净收料 = 收料总数 − 送修数），供应商修好「登记维修返回」时自动回补，订单台账自动闭环。'
  if (orderStatus.value === MaterialOrderStatus.FINISHED) return '该订单已完成：不扣减订单收料数，返回情况在本单「送修 / 已返回」中跟踪，全部返回后（未返回=0）可结案。'
  return ''
})
/** 预填期间不因 supplierId 变化清空已带出的关联订单 */
let prefilling = false
watch(() => form.supplierId, (nv, ov) => {
  if (prefilling || nv === ov) return
  if (form.materialOrderId || pickedOrder.value) { form.materialOrderId = undefined; pickedOrder.value = null }
})

async function loadOptions() {
  // F7-129（2026-09-20）：加载失败不再静默 —— 留痕，避免"空下拉"被误认为"没有数据"
  try { const r = await request.get<any, any>('/outsource/material-return/warehouse-options'); warehouseOptions.value = r || [] } catch (e: any) { console.warn('加载仓库选项失败', e?.message || e) }
}

async function onWarehouseChange() {
  stockList.value = []
  if (!form.fromWarehouseId) return
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/material-return/material-stock', { params: { warehouseId: form.fromWarehouseId } })
    stockList.value = (r || []).map((m: any) => ({ ...m, returnQuantity: undefined as any, unitPrice: undefined as any }))
  } catch (e: any) { ElMessage.error(e?.message || '加载库存失败') } finally { loading.value = false }
}

/**
 * 从「物料收货」带来源参数进入时预填（2026-09-17）：
 * 退回对象（物料商）/出库源仓/该收料单的物料与「已收 − 已退 = 可退」数量（不超过源仓当前良品库存）。
 */
async function loadFromQuery() {
  if (prefillSupplierId) form.supplierId = prefillSupplierId
  if (!prefillDeliveryId) return
  loading.value = true
  // 预填期间关闭"换供应商清空关联订单"的联动，避免刚带出的订单被清掉
  prefilling = true
  try {
    const d: any = await request.get('/outsource/material-return/return-prefill', { params: { deliveryId: prefillDeliveryId } })
    if (d.supplierId) form.supplierId = d.supplierId
    if (d.fromWarehouseId) form.fromWarehouseId = d.fromWarehouseId
    // 关联物料订单：按收料单的来源订单自动带出（2026-09-17）
    if (d.materialOrderId) {
      form.materialOrderId = d.materialOrderId
      pickedOrder.value = { id: d.materialOrderId, code: d.materialOrderCode, status: d.materialOrderStatus }
    }
    await onWarehouseChange() // 载入该源仓的可退物料（良品库存）
    const lines: any[] = (d.lines || []).filter((l: any) => Number(l.returnableQty) > 0)
    if (lines.length === 0) { ElMessage.warning('该收料单已无可退数量（可能已全部退货）'); return }
    const qtyMap: Record<string, number> = {}
    for (const l of lines) qtyMap[String(l.materialId)] = Number(l.returnableQty)
    // 只保留该收料单涉及的物料；默认数量取「可退」与源仓良品库存的较小值
    const kept = stockList.value.filter((m: any) => qtyMap[String(m.materialId)] != null)
    stockList.value = kept.map((m: any) => ({ ...m, returnQuantity: Math.min(qtyMap[String(m.materialId)], Number(m.quantity) || 0) }))
    if (kept.length === 0) ElMessage.warning('该收料单的物料在当前源仓已无良品库存，无法按单退货')
    else ElMessage.success(`已按收料单 ${d.sourceCode || ''} 带出 ${kept.length} 行退货明细，请核对数量`)
  } catch (e: any) {
    ElMessage.error('带出退货明细失败：' + (e?.message || '未知错误'))
  } finally { loading.value = false; prefilling = false }
}

async function handleSubmit() {
  if (!form.supplierId) { ElMessage.warning(isRepair.value ? '请选择维修供应商' : '请选择退回对象（物料商）'); return }
  if (!form.fromWarehouseId) { ElMessage.warning('请选择出库源仓'); return }
  const items = stockList.value
    .filter((m: any) => Number(m.returnQuantity) > 0)
    .map((m: any) => ({
      materialId: m.materialId, materialTypeId: m.materialTypeId, unit: m.unit,
      quantity: Number(m.returnQuantity), unitPrice: m.unitPrice || '', remark: ''
    }))
  if (items.length === 0) { ElMessage.warning(isRepair.value ? '请输入送修数量' : '请输入退货数量'); return }
  submitting.value = true
  try {
    const payload = {
      supplierId: form.supplierId, fromWarehouseId: form.fromWarehouseId,
      returnDate: form.returnDate, remark: form.remark,
      // 类型（2026-09-17）：REFUND 退货退款 / REPAIR 维修退货（原先写死 MATERIAL，业务从不读）
      returnType: form.returnType, items,
      // 来源收料单（从「物料收货」发起时落库，用于按记录算可退数量并追溯；编辑时保留原值）
      sourceDeliveryId: editId ? editSourceDeliveryId.value : (prefillDeliveryId || null),
      // 关联物料订单（维修退货闭环，2026-09-17）：未选/清空=不关联
      materialOrderId: isRepair.value ? (form.materialOrderId || null) : null
    }
    // D 档（2026-09-21）：编辑草稿走 PUT（后端仅允许 DRAFT 编辑，明细整体替换；草稿不动库存/应付）
    if (editId) {
      await request.put(`/outsource/material-return/${editId}`, payload)
      ElMessage.success('退货单已更新')
    } else {
      await request.post('/outsource/material-return', payload)
      ElMessage.success('退货单草稿已保存，请在列表中审核生效')
    }
    sessionStorage.setItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, '1')
    // 提交成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本次录入的页签并回列表
    markClean()
    tabStore.closeTabAndBack(window.location.hash.replace('#', ''))
    router.replace('/outsource/material-return')
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { submitting.value = false }
}

/**
 * 编辑草稿（D 档 2026-09-21）：回填表头 + 按明细回填数量/单价（明细整体替换交由后端）。
 * <p>⚠️ 源仓当前库存里已没有该物料的（例如被别的单占掉）也要带上，否则一保存就会把这行**静默删掉**。</p>
 */
async function loadForEdit(id: number) {
  loading.value = true
  prefilling = true // 回填期间压住"换供应商清空关联订单"的联动
  try {
    const d: any = await request.get(`/outsource/material-return/${id}`)
    editing.value = true
    editSourceDeliveryId.value = d.sourceDeliveryId ?? null
    Object.assign(form, {
      returnType: d.returnType || MaterialReturnType.REFUND,
      supplierId: d.supplierId, fromWarehouseId: d.fromWarehouseId,
      returnDate: d.returnDate ? String(d.returnDate).slice(0, 10) : localDate(),
      remark: d.remark || '',
      materialOrderId: d.materialOrderId ?? undefined
    })
    if (d.materialOrderId) pickedOrder.value = { id: d.materialOrderId, code: d.materialOrderCode, status: d.materialOrderStatus }
    // 载入该源仓当前可退物料，再按本单明细回填
    await onWarehouseChange()
    const byMaterial: Record<string, any[]> = {}
    for (const it of ((d.items || []) as any[])) {
      const k = String(it.materialId)
      byMaterial[k] = byMaterial[k] || []
      byMaterial[k].push(it)
    }
    const used = new Set<string>()
    stockList.value = stockList.value.map((m: any) => {
      const list = byMaterial[String(m.materialId)]
      if (!list) return m
      used.add(String(m.materialId))
      return {
        ...m,
        returnQuantity: list.reduce((s: number, x: any) => s + Number(x.quantity || 0), 0),
        unitPrice: list[0]?.unitPrice ?? undefined
      }
    })
    // 源仓库存里已经没有、但本单明细里有的物料：补到列表尾部，避免保存时被静默删除
    for (const [mid, list] of Object.entries(byMaterial)) {
      if (used.has(mid)) continue
      stockList.value.push({
        materialId: Number(mid), materialName: list[0]?.materialName || ('物料#' + mid),
        materialTypeId: list[0]?.materialTypeId, materialTypeName: list[0]?.materialTypeName, unit: list[0]?.unit || '',
        quantity: 0, returnQuantity: list.reduce((s: number, x: any) => s + Number(x.quantity || 0), 0),
        unitPrice: list[0]?.unitPrice ?? undefined
      })
    }
  } catch (e: any) {
    ElMessage.error('加载退货单失败：' + (e?.message || '未知错误'))
  } finally { loading.value = false; prefilling = false }
}

// 顶栏"刷新数据"：重新加载出库源仓下拉
async function handleRefreshData() { await loadOptions() }
/**
 * 未保存拦截（2026-09-23 统一模板）：本页明细由来源单带入、只读 ⇒ 脏状态就是 form 本身。
 * ⚠️ 必须写在 form / editing 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form }))

onMounted(async () => {
  await loadOptions()      // 先备好仓库下拉，再按来源预填源仓（否则下拉只显示 ID）
  if (editId) await loadForEdit(editId)
  else await loadFromQuery()
  window.addEventListener('refresh:dropdown-data', handleRefreshData)
  // 初始化完成（含编辑回填 / 来源预填）⇒ 建立"未保存"基线
  takeBaseline()
})
onUnmounted(() => window.removeEventListener('refresh:dropdown-data', handleRefreshData))

</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作（保存/保存草稿） -->
  <PageShell back-fallback="/outsource/material-return">
    <template #actions>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">{{ editing ? (isRepair ? '编辑维修退货' : '编辑物料退货') : (isRepair ? '维修退货信息' : '退货信息') }}</span></template>
      <!-- 类型说明整行展示（2026-09-17）：两类型的库存/应付/后续动作不同，写在字段区里会把同行字段挤窄 -->
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            {{ isRepair
              ? '维修退货：把物料送供应商维修 —— 审核只扣源仓、不冲减应付；供应商修好后在详情页「登记维修返回」把物料入回来（可分批、可撤销）。关联物料订单且订单未完成时，审核会同时扣减该订单收料数（修好返回自动回补）；订单已完成或不关联时，靠本单「送修 / 已返回」跟踪，全部返回后结案。'
              : '退货退款：物料退回供应商 —— 审核扣源仓并生成负向应付（冲减应付账款）；供应商把货款退给我们后走付款/核销。' }}
          </span>
        </template>
      </el-alert>
      <el-form :model="form" label-width="var(--app-label-width)" size="small">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item required label="退货类型">
              <el-select v-model="form.returnType" style="width:100%">
                <el-option :label="MaterialReturnTypeLabel[MaterialReturnType.REFUND]" :value="MaterialReturnType.REFUND" />
                <el-option :label="MaterialReturnTypeLabel[MaterialReturnType.REPAIR]" :value="MaterialReturnType.REPAIR" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item required :label="isRepair ? '维修供应商' : '退回对象'"><RemoteSelect v-model="form.supplierId" :fetch="fetchSuppliers" :placeholder="isRepair ? '选择维修供应商' : '选择物料商'" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item required label="出库源仓"><el-select v-model="form.fromWarehouseId" filterable clearable style="width:100%" placeholder="选择物料所在仓库" @change="onWarehouseChange"><el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></el-form-item></el-col>
          <!-- 关联物料订单（2026-09-17 维修退货闭环）：可清空=不关联；订单未完成时审核会扣减其收料数 -->
          <el-col :span="8" v-if="isRepair">
            <el-form-item label="关联物料订单">
              <RemoteSelect v-model="form.materialOrderId" :fetch="fetchMaterialOrders" :label-key="materialOrderLabel" :disabled="!form.supplierId"
                :placeholder="form.supplierId ? '可不选（不关联）' : '请先选维修供应商'" style="width:100%" disable-cache filterable
                :preset="pickedOrder ? { id: pickedOrder.id, code: pickedOrder.code } : null" @pick="(opts: any[]) => pickedOrder = (opts && opts[0]) || null" />
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item :label="isRepair ? '送修日期' : '退货日期'"><el-input v-model="form.returnDate" type="date" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
          <!-- 收尾方式提示：按关联订单状态说明"扣减订单收料"还是"靠本单跟踪" -->
          <el-col :span="24" v-if="isRepair && orderHint">
            <el-alert :type="orderStatus === MaterialOrderStatus.RECEIVING ? 'success' : 'warning'" :closable="false" show-icon style="margin-bottom:8px">
              <template #title><span style="font-size:var(--app-font-xs);line-height:1.5">{{ orderHint }}</span></template>
            </el-alert>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never" v-if="form.fromWarehouseId" v-loading="loading">
      <template #header><span style="font-weight:600">{{ isRepair ? '可送修物料（源仓良品库存）' : '可退物料（源仓良品库存）' }}</span></template>
      <el-table :data="stockList" border size="small">
        <el-table-column prop="materialName" label="物料名称" min-width="140" />
        <el-table-column prop="materialTypeName" label="物料类型" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="可退数量" width="100" align="right">
          <template #default="{row}">{{ Number(row.quantity || 0) }}</template>
        </el-table-column>
        <el-table-column :label="isRepair ? '送修数量' : '退货数量'" width="120">
          <template #default="{row}"><el-input-number v-model="row.returnQuantity" size="small" :controls="false" :precision="0" :step="1" style="width:100%" placeholder="数量" /></template>
        </el-table-column>
        <el-table-column label="单价（留空自动FIFO）" width="150">
          <template #default="{row}"><el-input v-model="row.unitPrice" size="small" type="number" placeholder="自动" /></template>
        </el-table-column>
      </el-table>
    </el-card>
  </PageShell>
</template>
