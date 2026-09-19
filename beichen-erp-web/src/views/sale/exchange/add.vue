<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <span>{{ isEdit ? '编辑销售换货单' : '新增销售换货单' }}</span>
      </template>
      <el-form :model="form" label-width="100px" ref="formRef">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="客户" required>
              <!-- RemoteSelect 的 update:model-value 只回传值，选中项需通过 pick 事件取（否则拿不到单号/客户/仓库） -->
              <RemoteSelect :model-value="form.customerId" :fetch="fetchCustomers"
                label-key="name" placeholder="选择客户" :disabled="isEdit" style="width:100%"
                @update:model-value="(v:any)=>{ form.customerId = v; form.saleOrderId = null; form.saleOrderCode = ''; items = [] }" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源销售单" required>
              <RemoteSelect :model-value="form.saleOrderId" :fetch="fetchSaleOrders"
                label-key="code" placeholder="先选客户" :disabled="isEdit" style="width:100%"
                @update:model-value="(v:any)=>{ form.saleOrderId = v }"
                @pick="(opts:any[])=>{ onSaleOrderChange(form.saleOrderId, opts?.[0]) }" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换货日期" required>
              <el-date-picker v-model="form.exchangeDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换入仓" required>
              <RemoteSelect v-model="form.warehouseInId" :fetch="fetchAfterSaleWarehouses"
                label-key="warehouseName" placeholder="成品仓" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换出仓" required>
              <RemoteSelect v-model="form.warehouseOutId" :fetch="fetchFinishedWarehouses"
                label-key="warehouseName" placeholder="成品仓" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="是否收费">
              <el-switch v-model="form.chargeFlag" :active-value="1" :inactive-value="0"
                active-text="收费" inactive-text="不收费" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="收费类型" :required="form.chargeFlag === 1">
              <el-select v-model="form.chargeType" placeholder="请选择" clearable
                style="width:100%" :disabled="form.chargeFlag !== 1">
                <el-option v-for="o in chargeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="收费金额" :required="form.chargeFlag === 1">
              <el-input-number v-model="form.chargeAmount" :min="0" :precision="2" :step="10"
                controls-position="right" style="width:100%" :disabled="form.chargeFlag !== 1" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="收费说明">
              <el-input v-model="form.chargeReason" placeholder="选填，如：客户人为损坏 / 超出保修期"
                :disabled="form.chargeFlag !== 1" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider content-position="left">
        换货明细
        <span style="font-weight:normal;color:#909399;margin-left:8px">
          只支持同品换货：换出产品与退回产品相同，换出数量可与退回数量不等（如退2换1）
        </span>
      </el-divider>
      <el-table :data="items" border size="small" max-height="380">
        <!-- ===== 退回侧：客户退回，入成品仓（品质待分类 PENDING）待整理 ===== -->
        <el-table-column label="退回（客户退回，入成品仓待分类）" align="center">
          <el-table-column label="SKU" width="130">
            <template #default="{ row }">
              <span v-if="row.sku">{{ row.sku }}</span>
              <span v-else style="color:var(--app-text-secondary)">自动生成</span>
            </template>
          </el-table-column>
          <el-table-column prop="productName" label="退回产品" min-width="150" show-overflow-tooltip />
          <el-table-column label="可换数量" width="90" align="right">
            <template #default="{ row }">{{ row.maxQuantity ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="退回数量" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="0" :step="1" controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column prop="unitPrice" label="原单价" width="90" align="right" />
        </el-table-column>

        <!-- ===== 换出侧：发给客户，从成品仓扣减；只支持同品，产品固定为退回产品 ===== -->
        <el-table-column label="换出（同品换货，从成品仓扣减）" align="center">
          <el-table-column label="换出数量" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.outQuantity" :min="0" :precision="0" :step="1" controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="换出品质" width="120">
            <template #default="{ row }">
              <el-select v-model="row.outQualityType" style="width:100%">
                <el-option v-for="o in qualityOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="换出单价" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.outUnitPrice" :min="0" :precision="2" controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
        </el-table-column>

        <el-table-column label="备注" min-width="120">
          <template #default="{ row }"><el-input v-model="row.remark" /></template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeItem($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="items.length===0" style="text-align:center;color:#999;padding:16px">
        请先选择客户与来源销售单，系统将自动带出可换明细
      </div>

      <div class="footer">
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { onMounted, reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import {
  ProductQualityType, ProductQualityTypeLabel,
  WarehouseType, ExchangeChargeType, ExchangeChargeTypeLabel,
  SALE_EXCHANGE_DIRTY_KEY,
} from '@/api/enums'
import {
  getSaleExchange, createSaleExchange, updateSaleExchange,
  getSaleExchangeSaleOrders, getSaleExchangeSaleOrderItems, getSaleExchangeSourceOrder,
} from '@/api/sale'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const saving = ref(false)
const isEdit = ref(false)

const form = reactive({
  id: null as number | null,
  saleOrderId: null as number | null,
  saleOrderCode: '',
  customerId: null as number | null,
  warehouseInId: null as number | null,
  warehouseOutId: null as number | null,
  exchangeDate: localDate(),
  chargeFlag: 0,
  chargeType: '' as string,
  chargeAmount: 0,
  chargeReason: '',
  remark: '',
})
const items = ref<any[]>([])

const qualityOptions = computed(() =>
  [ProductQualityType.A, ProductQualityType.B, ProductQualityType.C].map((v) => ({
    value: v, label: ProductQualityTypeLabel[v] || v
  }))
)
const chargeTypeOptions = computed(() =>
  Object.values(ExchangeChargeType).map((v) => ({
    value: v, label: ExchangeChargeTypeLabel[v] || v
  }))
)
// ===== 下拉 =====
const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 200, name: kw } })
const fetchSaleOrders = async (kw: string) => {
  // 换货必须关联销售单：按客户过滤，未选客户时返回空，避免跨客户挂单
  if (!form.customerId) return { data: { records: [] } }
  // 期 3（2026-09-19 读隔离）：来源销售单下拉改走换货页自身前缀（原读退货页的 /sale/return/sale-orders 需 sale:return）
  const rows: any[] = await getSaleExchangeSaleOrders(form.customerId)
  const list = (rows || []).filter((r: any) => !kw || (r.code || '').includes(kw))
  return { data: { records: list } }
}
// 2026-09-16 方案 A：换入仓(退回品) 与 换出仓(良品) **都只能是自有成品仓**，同仓内按品质分行 → 允许两者相同
const fetchAfterSaleWarehouses = (kw: string) => request.get('/warehouse/page',
  { params: { pageSize: 200, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: WarehouseType.FINISHED } })
const fetchFinishedWarehouses = (kw: string) => request.get('/warehouse/page',
  { params: { pageSize: 200, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: WarehouseType.FINISHED } })

/**
 * 选择来源销售单后带出明细。
 * 可换数量 = 已售 − 已退 − 已换，直接取后端 sale/order-items 接口返回的 canExchange，
 * 与保存/审核时的后端校验口径完全一致。
 */
async function onSaleOrderChange(saleOrderId: number | null, opt: any) {
  form.saleOrderCode = opt?.code || ''
  form.customerId = opt?.customerId ?? form.customerId
  form.warehouseOutId = opt?.warehouseId ?? form.warehouseOutId
  if (!saleOrderId) { items.value = []; return }
  const rows: any[] = await getSaleExchangeSaleOrderItems(saleOrderId)
  items.value = (rows || []).map((r: any) => {
    const qty = Number(r.canExchange ?? 0)
    const price = Number(r.unitPrice ?? 0)
    return {
      saleOrderItemId: r.saleOrderItemId,
      // ===== 退回侧 =====
      productId: r.productId,
      productName: r.productName,
      quantity: qty,
      maxQuantity: qty,
      unitPrice: price,
      // ===== 换出侧：只支持同品，产品即退回产品；数量默认 1:1、单价取原销售单价 =====
      outQuantity: qty,
      outUnitPrice: price,
      outQualityType: r.qualityType || ProductQualityType.A,
      remark: ''
    }
  })
}

/**
 * 从销售单详情「换货」按钮跳转过来时（?saleOrderId=xxx）：
 * 反查销售单带出客户与单号，自动载入可换明细；换出仓默认取原销售出库仓（成品仓）。
 */
async function initFromSaleOrder(saleOrderId: number) {
  try {
    // 期 3（2026-09-19 读隔离）：来源销售单头改走换货页自身前缀（原读 /inventory/sale/{id} 需 sale:order）
    const so: any = await getSaleExchangeSourceOrder(saleOrderId)
    if (!so) { ElMessage.warning('来源销售单不存在'); return }
    form.customerId = so.customerId
    form.saleOrderId = so.id
    form.saleOrderCode = so.code || ''
    if (so.warehouseId) form.warehouseOutId = so.warehouseId
    // 换入仓与换出仓现在都是成品仓（允许相同，同仓按品质分行），仍由用户选择
    await onSaleOrderChange(so.id, so)
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '加载来源销售单失败')
  }
}

/** 编辑模式：加载已有单据。明细拆「退回侧 + 换出侧」，保存时后端已回填换出侧，此处补齐展示字段 */
async function loadEdit(id: number) {
  const res: any = await getSaleExchange(id)
  const h = res?.head || {}
  Object.assign(form, {
    id: h.id,
    saleOrderId: h.saleOrderId,
    saleOrderCode: h.saleOrderCode || '',
    customerId: h.customerId,
    warehouseInId: h.warehouseInId,
    warehouseOutId: h.warehouseOutId,
    exchangeDate: h.exchangeDate ? String(h.exchangeDate).slice(0, 10) : '',
    chargeFlag: Number(h.chargeFlag || 0),
    chargeType: h.chargeType || '',
    chargeAmount: Number(h.chargeAmount || 0),
    chargeReason: h.chargeReason || '',
    remark: h.remark || '',
  })
  items.value = (res?.items || []).map((it: any) => ({
    ...it,
    quantity: Number(it.quantity),
    outQuantity: Number(it.outQuantity ?? it.quantity),
    outUnitPrice: Number(it.outUnitPrice ?? it.unitPrice ?? 0)
  }))
}

function removeItem(i: number) { items.value.splice(i, 1) }

async function submit() {
  if (!form.saleOrderId) { ElMessage.warning('请选择来源销售单'); return }
  if (!form.warehouseInId) { ElMessage.warning('请选择换入仓(成品仓)'); return }
  if (!form.warehouseOutId) { ElMessage.warning('请选择换出仓(成品仓)'); return }
  const its = items.value.filter((i) => Number(i.quantity) > 0)
  if (its.length === 0) { ElMessage.warning('请至少录入一条换货明细'); return }
  for (const it of its) {
    // 可换量只约束退回数量（换出属正常出库，不受限）
    if (it.maxQuantity != null && Number(it.quantity) > it.maxQuantity) {
      ElMessage.warning(`产品「${it.productName}」退回数量(${it.quantity})超过可退数量(${it.maxQuantity})`)
      return
    }
    if (!(Number(it.outQuantity) > 0)) {
      ElMessage.warning(`产品「${it.productName}」换出数量必须大于 0`)
      return
    }
  }
  // 收费校验：选择收费时必须指定类型且金额 > 0（后端会再校验一次，此处提前给提示）
  const charged = Number(form.chargeFlag) === 1
  if (charged) {
    if (!form.chargeType) { ElMessage.warning('已选择收费，请选择收费类型'); return }
    if (!(Number(form.chargeAmount) > 0)) { ElMessage.warning('已选择收费，收费金额必须大于 0'); return }
  }
  saving.value = true
  try {
    const body = {
      saleOrderId: form.saleOrderId, saleOrderCode: form.saleOrderCode, customerId: form.customerId,
      warehouseInId: form.warehouseInId, warehouseOutId: form.warehouseOutId,
      exchangeDate: form.exchangeDate,
      chargeFlag: charged ? 1 : 0,
      chargeType: charged ? form.chargeType : '',
      chargeAmount: charged ? Number(form.chargeAmount) : 0,
      chargeReason: charged ? (form.chargeReason || '') : '',
      remark: form.remark,
      items: its.map((i) => ({
        // 退回侧
        saleOrderItemId: i.saleOrderItemId, productId: i.productId, productName: i.productName,
        quantity: i.quantity, unitPrice: i.unitPrice,
        // 换出侧（只支持同品：产品即退回产品；数量可不等如退2换1）
        outQuantity: i.outQuantity, outUnitPrice: i.outUnitPrice,
        outQualityType: i.outQualityType,
        remark: i.remark
      }))
    }
    if (isEdit.value) {
      await updateSaleExchange(form.id!, body)
      ElMessage.success('保存成功')
      sessionStorage.setItem(SALE_EXCHANGE_DIRTY_KEY, '1')
      router.push(`/sale/exchange/detail/${form.id}`)
    } else {
      const res: any = await createSaleExchange(body)
      ElMessage.success('新增成功')
      sessionStorage.setItem(SALE_EXCHANGE_DIRTY_KEY, '1')
      router.push('/sale/exchange')
    }
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  if (isEdit.value) router.push(`/sale/exchange/detail/${form.id}`)
  else router.push('/sale/exchange')
}

onMounted(async () => {
  const id = route.query.id
  if (id !== undefined && id !== '') {
    isEdit.value = true
    await loadEdit(Number(id))
    return
  }
  // 从销售单详情页「换货」跳转：预填来源销售单并自动带入可换明细
  const soId = route.query.saleOrderId
  if (soId !== undefined && soId !== '') {
    await initFromSaleOrder(Number(soId))
  }
})
</script>

<style scoped>
.footer { margin-top: 20px; text-align: right; }
</style>
