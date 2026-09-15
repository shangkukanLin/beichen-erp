<script setup lang="ts">
import { ref, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { getPurchaseReturn, getPurchaseReturnItems, getPurchaseReturnPurchaseOrderItems, auditPurchaseReturn, cancelPurchaseReturn, unAuditPurchaseReturn, ReturnStatus, ReturnStatusLabel, type PurchaseReturn, type PurchaseReturnItem } from '@/api/purchase'
import { PURCHASE_RETURN_DIRTY_KEY } from '@/api/enums'

const route = useRoute(); const router = useRouter()
const id = Number(route.params.id)
const loading = ref(false)
const saving = ref(false)
const detail = ref<Partial<PurchaseReturn>>({})
const items = ref<any[]>([])
const warehouseOptions = ref<any[]>([])

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function warehouseName(wid?: number) { const w = warehouseOptions.value.find(x => x.id === wid); return w ? (w.warehouseName || w.name || '') : '' }
function productName(it?: PurchaseReturnItem) { return it?.productName || (it?.productId != null ? `#${it.productId}` : '') }
function rowAmount(row: any) {
  const q = Number(row.quantity) || 0
  const p = Number(row.unitPrice) || 0
  return Math.round(q * p * 100) / 100
}
function statusLabel(s?: number) { return s != null ? (ReturnStatusLabel[s] || '') : '' }
function statusType(s?: any): 'success' | 'warning' | 'info' | 'danger' | 'primary' | undefined {
  if (s === ReturnStatus.DRAFT) return 'info'
  if (s === ReturnStatus.AUDITED) return 'success'
  if (s === ReturnStatus.CANCELLED) return 'danger'
  return undefined
}
// status 接口返回为字符串（'DRAFT'/'AUDITED'/'CANCELLED'），与 ReturnStatus 常量字符串比较
function isDraft() { return String(detail.value.status) === ReturnStatus.DRAFT }
function isAudited() { return String(detail.value.status) === ReturnStatus.AUDITED }

function goSupplier(id?: number) { if (id) router.push(`/supplier/detail/${id}`) }
function goWarehouse(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
function goPurchaseOrder(id?: number) { if (id) router.push(`/inventory/purchase/detail/${id}`) }

async function loadWarehouseOptions() { try { const r: any = await request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: '' } }); warehouseOptions.value = r?.records || [] } catch { warehouseOptions.value = [] } }

async function loadData() {
  loading.value = true
  try {
    const d = await getPurchaseReturn(id)
    detail.value = d || {}
    // 后端随明细返回 productName，前端不再逐条查库
    items.value = await getPurchaseReturnItems(id) || []
  } finally { loading.value = false }
}

async function handleAudit() {
  try {
    await ElMessageBox.confirm(`确认审核退货单「${detail.value.code}」？审核后出库减库存并冲减应付账款。`, '确认审核', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await auditPurchaseReturn(id)
    ElMessage.success('审核成功')
    sessionStorage.setItem(PURCHASE_RETURN_DIRTY_KEY, '1')
    loadData()
  } catch { /* */ }
}

async function handleUnAudit() {
  try {
    await ElMessageBox.confirm(`确认反审核退货单「${detail.value.code}」？反审核后恢复库存、清除应付台账，回到草稿状态。`, '确认反审核', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await unAuditPurchaseReturn(id)
    ElMessage.success('反审核成功')
    sessionStorage.setItem(PURCHASE_RETURN_DIRTY_KEY, '1')
    loadData()
  } catch { /* */ }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm(`确认作废退货单「${detail.value.code}」？`, '确认作废', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await cancelPurchaseReturn(id)
    ElMessage.success('已作废')
    sessionStorage.setItem(PURCHASE_RETURN_DIRTY_KEY, '1')
    loadData()
  } catch { /* */ }
}

// ==================== 草稿内联编辑 ====================
const poDialogVisible = ref(false)
const poItems = ref<any[]>([])
const poSelection = ref<any[]>([])

/** 添加行：从关联采购单明细中选择（含可退数量），已在本单明细中的行自动排除 */
async function openAddRowDialog() {
  const oid = detail.value.purchaseOrderId
  if (!oid) { ElMessage.warning('该退货单未关联采购单，无法添加行'); return }
  try {
    const rows = await getPurchaseReturnPurchaseOrderItems(oid) || []
    const existIds = new Set(items.value.map((it: any) => it.purchaseOrderItemId).filter(Boolean))
    poItems.value = (rows as any[]).filter(r => r.canReturn > 0 && !existIds.has(r.purchaseOrderItemId))
    poSelection.value = []
    poDialogVisible.value = true
    if (!poItems.value.length) ElMessage.info('该采购单所有明细均已退完或已加入')
  } catch (e: any) { ElMessage.error('加载采购单明细失败: ' + (e?.message || '')) }
}

function confirmAddRows() {
  if (!poSelection.value.length) { ElMessage.warning('请先勾选要退货的明细'); return }
  for (const r of poSelection.value) {
    items.value.push({
      purchaseOrderItemId: r.purchaseOrderItemId,
      productId: r.productId,
      sku: r.sku || '',
      productName: r.productName || productName({ productId: r.productId } as any),
      qualityType: r.qualityType || 'A',
      quantity: Number(r.canReturn),
      unitPrice: Number(r.unitPrice || 0),
      remark: '',
      canReturn: Number(r.canReturn),
    })
  }
  poDialogVisible.value = false
}

function removeItem(idx: number) { items.value.splice(idx, 1) }

/** 保存：仅草稿。后端校验可退数量并自动重算总金额 */
async function handleSave() {
  if (!items.value.length) { ElMessage.warning('退货明细不能为空'); return }
  for (const it of items.value) {
    if (!(Number(it.quantity) > 0)) { ElMessage.warning('明细数量必须大于 0'); return }
    if (Number(it.unitPrice) < 0) { ElMessage.warning('单价不能为负数'); return }
  }
  // 前端预校验：同采购单明细行的本次退货合计不可超过可退数量
  const qtyMap: Record<number, number> = {}
  for (const it of items.value) {
    const poi = Number(it.purchaseOrderItemId)
    if (!poi) continue
    const can = Number((it as any).canReturn ?? Infinity)
    if (can !== Infinity) {
      qtyMap[poi] = (qtyMap[poi] || 0) + (Number(it.quantity) || 0)
      if (qtyMap[poi] > can) { ElMessage.warning('存在明细本次退货数量超过可退数量，请检查'); return }
    }
  }
  saving.value = true
  try {
    const payload = {
      supplierId: detail.value.supplierId,
      warehouseId: detail.value.warehouseId,
      purchaseOrderId: detail.value.purchaseOrderId,
      purchaseOrderCode: detail.value.purchaseOrderCode || '',
      returnDate: detail.value.returnDate,
      remark: detail.value.remark || '',
      items: items.value.map((it: any) => ({
        purchaseOrderItemId: it.purchaseOrderItemId,
        productId: it.productId,
        qualityType: it.qualityType || 'A',
        quantity: it.quantity,
        unitPrice: it.unitPrice,
        remark: it.remark || '',
      })),
    }
    await request.put(`/inventory/purchase-return/${id}`, payload)
    ElMessage.success('已保存')
    sessionStorage.setItem(PURCHASE_RETURN_DIRTY_KEY, '1')
    loadData()
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '')) }
  finally { saving.value = false }
}

// 字典类只需加载一次
onMounted(() => { loadWarehouseOptions() })
// 单据数据每次进入都重新拉取：keep-alive 缓存下再次进入会复用组件、onMounted 不再触发
onActivated(() => { loadData() })
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">采购退货单详情 — {{ detail.code }}</span>
          <div>
            <template v-if="isDraft()">
              <el-button type="primary" size="small" :loading="saving" @click="handleSave">保存</el-button>
              <el-button size="small" @click="loadData">取消</el-button>
              <el-button type="success" size="small" @click="handleAudit">审核</el-button>
              <el-button type="danger" size="small" @click="handleCancel">作废</el-button>
            </template>
            <el-button v-if="isAudited()" type="warning" size="small" @click="handleUnAudit">反审核</el-button>
          </div>
        </div>
      </template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="退货单号">{{ detail.code }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusType(detail.status)">{{ statusLabel(detail.status) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="供货商">
          <el-button v-if="detail.supplierId" type="primary" link @click="goSupplier(detail.supplierId)">{{ detail.supplierName || '—' }}</el-button>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="退货仓库">
          <el-button v-if="detail.warehouseId" type="primary" link @click="goWarehouse(detail.warehouseId)">{{ warehouseName(detail.warehouseId) || '—' }}</el-button>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="来源采购单">
          <el-button v-if="detail.purchaseOrderId" type="primary" link @click="goPurchaseOrder(detail.purchaseOrderId)">{{ detail.purchaseOrderCode || '—' }}</el-button>
          <span v-else>{{ detail.purchaseOrderCode || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="退货日期">
          <el-date-picker v-if="isDraft()" v-model="detail.returnDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
          <span v-else>{{ detail.returnDate }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="退货总金额">{{ fmt(detail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="备注">
          <el-input v-if="isDraft()" v-model="detail.remark" placeholder="备注" />
          <span v-else>{{ detail.remark || '—' }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">
        <span>退货明细</span>
        <el-button v-if="isDraft()" type="primary" size="small" style="margin-left:12px" @click="openAddRowDialog">+ 添加行</el-button>
      </el-divider>
      <el-table :data="items" border stripe size="small">
        <el-table-column prop="sku" label="SKU" width="130" />
        <el-table-column label="产品" min-width="140">
          <template #default="{ row }">{{ row.productName || productName(row) }}</template>
        </el-table-column>
        <el-table-column label="数量" width="150" align="right">
          <template #default="{ row }">
            <el-input-number v-if="isDraft()" v-model="row.quantity" :min="1" :precision="0" size="small" style="width:120px" />
            <span v-else>{{ row.quantity }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="150" align="right">
          <template #default="{ row }">
            <el-input-number v-if="isDraft()" v-model="row.unitPrice" :min="0" :precision="2" size="small" style="width:120px" />
            <span v-else>{{ row.unitPrice }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">{{ fmt(rowAmount(row)) }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="130">
          <template #default="{ row }">
            <el-input v-if="isDraft()" v-model="row.remark" size="small" placeholder="备注" />
            <span v-else>{{ row.remark || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="isDraft()" label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="removeItem($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="isDraft()" style="margin-top:12px;color:var(--app-text-secondary);font-size:12px">
        提示：保存后总金额按「数量 × 单价」自动重算；审核时将校验本次退货数量不超过可退数量。
      </div>
    </el-card>

    <div style="text-align:center;margin-top:20px">
      <el-button @click="router.back()">返回</el-button>
    </div>

    <!-- 添加行：从关联采购单明细选择 -->
    <el-dialog v-model="poDialogVisible" title="从采购单明细添加退货行" width="760px">
      <el-table
        :data="poItems"
        border
        size="small"
        max-height="380"
        @selection-change="(rows: any[]) => poSelection = rows"
      >
        <el-table-column type="selection" width="45" align="center" />
        <el-table-column prop="sku" label="SKU" width="120" />
        <el-table-column prop="productName" label="产品" min-width="140" show-overflow-tooltip />
        <el-table-column prop="qualityType" label="品质" width="70" align="center" />
        <el-table-column prop="quantity" label="已入库" width="90" align="right" />
        <el-table-column label="可退数量" width="90" align="right">
          <template #default="{ row }"><span style="color:var(--app-color-primary);font-weight:600">{{ row.canReturn }}</span></template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="100" align="right" />
      </el-table>
      <template #footer>
        <el-button @click="poDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAddRows">加入明细</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-page { display: flex; flex-direction: column; gap: 12px; }
</style>
