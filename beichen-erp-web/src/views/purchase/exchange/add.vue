<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <span>{{ isEdit ? '编辑采购换货单' : '新增采购换货单' }}</span>
      </template>
      <el-form :model="form" label-width="110px" ref="formRef">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="供货商" required>
              <!-- RemoteSelect 的 update:model-value 只回传值，选中项需通过 pick 事件取（否则拿不到单号/仓库） -->
              <RemoteSelect :model-value="form.supplierId" :fetch="fetchSuppliers"
                label-key="name" placeholder="选择供货商" :disabled="isEdit" style="width:100%"
                @update:model-value="(v:any)=>{ form.supplierId = v; form.purchaseOrderId = null; form.purchaseOrderCode = ''; items = [] }" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源采购单" required>
              <RemoteSelect :model-value="form.purchaseOrderId" :fetch="fetchPurchaseOrders"
                label-key="code" placeholder="请先选择供货商" :disabled="isEdit" style="width:100%"
                @update:model-value="(v:any)=>{ form.purchaseOrderId = v }"
                @pick="(opts:any[])=>{ onPurchaseOrderChange(form.purchaseOrderId, opts?.[0]) }" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换货日期" required>
              <el-date-picker v-model="form.exchangeDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退回出库仓" required>
              <RemoteSelect v-model="form.warehouseOutId" :fetch="fetchFinishedWarehouses"
                label-key="warehouseName" placeholder="退给供货商，从我方仓扣减" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换入入库仓" required>
              <RemoteSelect v-model="form.warehouseInId" :fetch="fetchFinishedWarehouses"
                label-key="warehouseName" placeholder="换回良品入我方仓（可与退回仓相同）" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider content-position="left">
        换货明细
        <span style="font-weight:normal;color:#909399;margin-left:8px">
          同品换货：退回产品与换入产品相同；退回默认「不良品」出库、换入默认「A规」入库；换入单价高于退回单价即为加价换新（差额进应付）
        </span>
      </el-divider>
      <el-table :data="items" border size="small" max-height="380">
        <!-- ===== 退回侧：退回供货商，从我方仓出库 ===== -->
        <el-table-column label="退回（退给供货商，从我方仓扣减）" align="center">
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
          <el-table-column label="退回品质" width="120">
            <template #default="{ row }">
              <el-select v-model="row.qualityType" style="width:100%">
                <el-option v-for="o in qualityOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="退回单价" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
        </el-table-column>

        <!-- ===== 换入侧：供货商换回，入我方仓 ===== -->
        <el-table-column label="换入（供货商换回，入我方仓）" align="center">
          <el-table-column label="换入数量" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.inQuantity" :min="0" :precision="0" :step="1" controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="换入品质" width="120">
            <template #default="{ row }">
              <el-select v-model="row.inQualityType" style="width:100%">
                <el-option v-for="o in qualityOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="换入单价" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.inUnitPrice" :min="0" :precision="2" controls-position="right" style="width:100%" />
            </template>
          </el-table-column>
        </el-table-column>

        <el-table-column label="应付净额" width="110" align="right">
          <template #default="{ row }">
            {{ ((Number(row.inQuantity) || 0) * (Number(row.inUnitPrice) || 0) - (Number(row.quantity) || 0) * (Number(row.unitPrice) || 0)).toFixed(2) }}
          </template>
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
        请先选择供货商与来源采购单，系统将自动带出可换明细
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
  WarehouseType, WarehouseCategory, PURCHASE_EXCHANGE_DIRTY_KEY,
} from '@/api/enums'
import {
  getPurchaseExchange, createPurchaseExchange, updatePurchaseExchange,
  getPurchaseExchangePurchaseOrders, getPurchaseExchangePurchaseOrderItems,
} from '@/api/purchase'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const saving = ref(false)
const isEdit = ref(false)

const form = reactive({
  id: null as number | null,
  supplierId: null as number | null,
  purchaseOrderId: null as number | null,
  purchaseOrderCode: '',
  warehouseOutId: null as number | null,
  warehouseInId: null as number | null,
  exchangeDate: localDate(),
  remark: '',
})
const items = ref<any[]>([])

const qualityOptions = computed(() =>
  [ProductQualityType.A, ProductQualityType.B, ProductQualityType.C, ProductQualityType.DEFECT].map((v) => ({
    value: v, label: ProductQualityTypeLabel[v] || v
  }))
)

// ===== 下拉 =====
/** 供货商：进货侧只选"成品商"（与采购单/采购退货口径一致） */
const fetchSuppliers = (kw: string) => request.get('/supplier/page',
  { params: { pageSize: 500, name: kw, supplierType: 'product' } })
/** 来源采购单：必须强关联，按供货商过滤（未选供货商时返回空，避免跨供货商挂单） */
const fetchPurchaseOrders = async (kw: string) => {
  if (!form.supplierId) return { records: [] }
  const rows: any[] = await getPurchaseExchangePurchaseOrders(form.supplierId, kw)
  return { records: rows || [] }
}
/** 退回出库仓 / 换入入库仓都只能是**自有成品仓**（同仓允许，仓内按品质分行） */
const fetchFinishedWarehouses = (kw: string) => request.get('/warehouse/page',
  { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY, warehouseType: WarehouseType.FINISHED } })

/**
 * 选择来源采购单后带出明细。
 * 可换数量 = 已购 − 已退 − 已换，直接取后端 purchase-order-items 返回的 canExchange，
 * 与保存/审核时的后端校验口径完全一致。
 */
async function onPurchaseOrderChange(purchaseOrderId: number | null, opt: any) {
  form.purchaseOrderCode = opt?.code || ''
  form.supplierId = opt?.supplierId ?? form.supplierId
  form.warehouseOutId = form.warehouseOutId ?? opt?.warehouseId ?? null
  form.warehouseInId = form.warehouseInId ?? opt?.warehouseId ?? null
  if (!purchaseOrderId) { items.value = []; return }
  const rows: any[] = await getPurchaseExchangePurchaseOrderItems(purchaseOrderId)
  items.value = (rows || []).map((r: any) => {
    const qty = Number(r.canExchange ?? 0)
    const price = Number(r.unitPrice ?? 0)
    return {
      purchaseOrderItemId: r.purchaseOrderItemId,
      // ===== 退回侧（默认不良品，采购价）=====
      productId: r.productId,
      productName: r.productName,
      quantity: qty,
      maxQuantity: qty,
      qualityType: ProductQualityType.DEFECT,
      unitPrice: price,
      // ===== 换入侧：同品，数量默认 1:1、默认 A 规、单价默认 = 退回单价（改高即加价换新）=====
      inQuantity: qty,
      inQualityType: ProductQualityType.A,
      inUnitPrice: price,
      remark: ''
    }
  })
}

/**
 * 从采购单详情「换货」跳转过来时（?fromOrder=xxx）：
 * 反查采购单带出供货商/单号/默认仓，并自动载入可换明细。
 */
async function initFromPurchaseOrder(orderId: number) {
  try {
    const po: any = await request.get(`/inventory/purchase/${orderId}`)
    if (!po) { ElMessage.warning('来源采购单不存在'); return }
    form.supplierId = po.supplierId
    form.purchaseOrderId = po.id
    form.purchaseOrderCode = po.code || ''
    if (po.warehouseId) { form.warehouseOutId = po.warehouseId; form.warehouseInId = po.warehouseId }
    await onPurchaseOrderChange(po.id, po)
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '加载来源采购单失败')
  }
}

/** 编辑模式：加载已有单据（退回侧 + 换入侧字段均已落库） */
async function loadEdit(id: number) {
  const res: any = await getPurchaseExchange(id)
  const h = res?.head || {}
  Object.assign(form, {
    id: h.id,
    supplierId: h.supplierId,
    purchaseOrderId: h.purchaseOrderId,
    purchaseOrderCode: h.purchaseOrderCode || '',
    warehouseOutId: h.warehouseOutId,
    warehouseInId: h.warehouseInId,
    exchangeDate: h.exchangeDate ? String(h.exchangeDate).slice(0, 10) : '',
    remark: h.remark || '',
  })
  items.value = (res?.items || []).map((it: any) => ({
    ...it,
    quantity: Number(it.quantity),
    unitPrice: Number(it.unitPrice ?? 0),
    inQuantity: Number(it.inQuantity ?? it.quantity),
    inUnitPrice: Number(it.inUnitPrice ?? it.unitPrice ?? 0),
  }))
}

function removeItem(i: number) { items.value.splice(i, 1) }

async function submit() {
  if (!form.supplierId) { ElMessage.warning('请选择供货商'); return }
  if (!form.purchaseOrderId) { ElMessage.warning('请选择来源采购单'); return }
  if (!form.warehouseOutId) { ElMessage.warning('请选择退回出库仓'); return }
  if (!form.warehouseInId) { ElMessage.warning('请选择换入入库仓'); return }
  const its = items.value.filter((i) => Number(i.quantity) > 0)
  if (its.length === 0) { ElMessage.warning('请至少录入一条换货明细'); return }
  for (const it of its) {
    // 可换量只约束退回数量（换入属正常入库，不受限）
    if (it.maxQuantity != null && Number(it.quantity) > it.maxQuantity) {
      ElMessage.warning(`产品「${it.productName}」退回数量(${it.quantity})超过可换数量(${it.maxQuantity})`)
      return
    }
    if (!(Number(it.inQuantity) > 0)) {
      ElMessage.warning(`产品「${it.productName}」换入数量必须大于 0`)
      return
    }
  }
  saving.value = true
  try {
    const body = {
      supplierId: form.supplierId, purchaseOrderId: form.purchaseOrderId,
      purchaseOrderCode: form.purchaseOrderCode,
      warehouseOutId: form.warehouseOutId, warehouseInId: form.warehouseInId,
      exchangeDate: form.exchangeDate, remark: form.remark,
      items: its.map((i) => ({
        // 退回侧
        purchaseOrderItemId: i.purchaseOrderItemId, productId: i.productId, productName: i.productName,
        qualityType: i.qualityType, quantity: i.quantity, unitPrice: i.unitPrice,
        // 换入侧（同品：产品即退回产品；数量可不等如退2换1）
        inQuantity: i.inQuantity, inQualityType: i.inQualityType, inUnitPrice: i.inUnitPrice,
        remark: i.remark
      }))
    }
    if (isEdit.value) {
      await updatePurchaseExchange(form.id!, body)
      ElMessage.success('保存成功')
      sessionStorage.setItem(PURCHASE_EXCHANGE_DIRTY_KEY, '1')
      router.push(`/inventory/purchase-exchange/detail/${form.id}`)
    } else {
      await createPurchaseExchange(body)
      ElMessage.success('新增成功')
      sessionStorage.setItem(PURCHASE_EXCHANGE_DIRTY_KEY, '1')
      router.push('/inventory/purchase-exchange')
    }
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  if (isEdit.value) router.push(`/inventory/purchase-exchange/detail/${form.id}`)
  else router.push('/inventory/purchase-exchange')
}

onMounted(async () => {
  const id = route.query.id
  if (id !== undefined && id !== '') {
    isEdit.value = true
    await loadEdit(Number(id))
    return
  }
  const poId = route.query.fromOrder
  if (poId !== undefined && poId !== '') {
    await initFromPurchaseOrder(Number(poId))
  }
})
</script>

<style scoped>
.footer { margin-top: 20px; text-align: right; }
</style>
