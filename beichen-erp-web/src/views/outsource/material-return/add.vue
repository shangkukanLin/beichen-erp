<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, MaterialReturnType, MaterialReturnTypeLabel, MaterialOrderStatus, MaterialOrderStatusLabel } from '@/api/enums'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useTabStore } from '@/stores/tabs'
import RemoteSelect from '@/components/RemoteSelect.vue'

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
/** ?returnType=REPAIR 直接从列表页签的「新增维修返还」进入（2026-09-17 两类型） */
const prefillReturnType = String(route.query.returnType || '')

const form = reactive({
  // 退货类型（2026-09-17）：REFUND 退货退款（冲减应付）/ REPAIR 维修返还（不冲应付，修好登记返纳入库）
  returnType: (prefillReturnType === MaterialReturnType.REPAIR ? MaterialReturnType.REPAIR : MaterialReturnType.REFUND) as string,
  supplierId: undefined as any, fromWarehouseId: undefined as any, returnDate: localDate(), remark: '',
  // 关联物料订单（2026-09-17 维修返还闭环）：可清空；不选=不关联（靠本单「送修/已返回」跟踪）
  materialOrderId: undefined as any
})
/** 维修返还：不冲减应付；审核后在详情页登记「维修返回」把物料入回来 */
const isRepair = computed(() => form.returnType === MaterialReturnType.REPAIR)
const warehouseOptions = ref<any[]>([])
const stockList = ref<any[]>([])
const loading = ref(false)
const submitting = ref(false)

// Odoo 风格：退回对象（物料商）实时查库
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })

// ===== 关联物料订单（维修返还闭环）=====
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
  try { const r = await request.get<any, any>('/outsource/material-return/warehouse-options'); warehouseOptions.value = r || [] } catch {}
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
    await request.post('/outsource/material-return', {
      supplierId: form.supplierId, fromWarehouseId: form.fromWarehouseId,
      returnDate: form.returnDate, remark: form.remark,
      // 类型（2026-09-17）：REFUND 退货退款 / REPAIR 维修返还（原先写死 MATERIAL，业务从不读）
      returnType: form.returnType, items,
      // 来源收料单（从「物料收货」发起时落库，用于按记录算可退数量并追溯）
      sourceDeliveryId: prefillDeliveryId || null,
      // 关联物料订单（维修返还闭环，2026-09-17）：未选/清空=不关联
      materialOrderId: isRepair.value ? (form.materialOrderId || null) : null
    })
    ElMessage.success('退货单草稿已保存，请在列表中审核生效'); sessionStorage.setItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, '1')
    tabStore.removeTab(window.location.hash.replace('#', ''))
    router.replace('/outsource/material-return')
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { submitting.value = false }
}

// 顶栏"刷新数据"：重新加载出库源仓下拉
async function handleRefreshData() { await loadOptions() }
onMounted(async () => {
  await loadOptions()      // 先备好仓库下拉，再按来源预填源仓（否则下拉只显示 ID）
  await loadFromQuery()
  window.addEventListener('refresh:dropdown-data', handleRefreshData)
})
onUnmounted(() => window.removeEventListener('refresh:dropdown-data', handleRefreshData))

</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never">
      <template #header><span style="font-weight:600">{{ isRepair ? '维修返还信息' : '退货信息' }}</span></template>
      <!-- 类型说明整行展示（2026-09-17）：两类型的库存/应付/后续动作不同，写在字段区里会把同行字段挤窄 -->
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            {{ isRepair
              ? '维修返还：把物料送供应商维修 —— 审核只扣源仓、不冲减应付；供应商修好后在详情页「登记维修返回」把物料入回来（可分批、可撤销）。关联物料订单且订单未完成时，审核会同时扣减该订单收料数（修好返回自动回补）；订单已完成或不关联时，靠本单「送修 / 已返回」跟踪，全部返回后结案。'
              : '退货退款：物料退回供应商 —— 审核扣源仓并生成负向应付（冲减应付账款）；供应商把货款退给我们后走付款/核销。' }}
          </span>
        </template>
      </el-alert>
      <el-form :model="form" label-width="100px" size="small">
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
          <!-- 关联物料订单（2026-09-17 维修返还闭环）：可清空=不关联；订单未完成时审核会扣减其收料数 -->
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
      <div style="margin-top:12px;text-align:right"><el-button type="primary" :loading="submitting" @click="handleSubmit">保存草稿</el-button></div>
    </el-card>
  </div>
</template>
