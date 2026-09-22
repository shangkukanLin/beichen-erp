<script setup lang="ts">
import { ref, reactive, computed, onMounted, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'
import { addProduct, updateProduct, ProductStatus, ProductStatusLabel, type Product } from '@/api/product'
// 2026-09-21：规格枚举（原「分类」自由文本替换而来）
import { ProductSpec, ProductSpecLabel } from '@/api/enums'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'

const route = useRoute(); const router = useRouter()

/** 新增（/product/add）与编辑（/product/detail/:id）共用本页：无 id 即新增模式 */
const id = computed(() => (route.params.id != null && route.params.id !== '' ? Number(route.params.id) : undefined))
const isNew = computed(() => id.value == null)

const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const statusOptions = [
  { label: ProductStatusLabel.NORMAL, value: ProductStatus.NORMAL },
  { label: ProductStatusLabel.DISCONTINUED, value: ProductStatus.DISCONTINUED },
  { label: ProductStatusLabel.DEVELOPING, value: ProductStatus.DEVELOPING }
]

/** 规格下拉（2026-09-21：原「分类」自由文本改为固定三选一） */
const specOptions = Object.values(ProductSpec).map(v => ({ value: v, label: ProductSpecLabel[v] || v }))

const defaultForm = (): Product => ({
  id: undefined,
  name: '',
  sku: '',
  brandId: undefined,
  supplierId: undefined,
  specType: '',
  generalModel: '',
  unit: 'pcs',
  safetyStock: undefined,
  costPrice: undefined,
  costManual: 0,
  lastInPrice: undefined,
  status: ProductStatus.NORMAL,
  remark: '',
} as Product)

const form = reactive<Product>(defaultForm())

/** 编辑态进入时的原始 SKU：改 SKU 前需二次确认（历史单据的 SKU 是快照，不会一并更新） */
const originalSku = ref('')

const rules: FormRules = {
  name: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  specType: [{ required: true, message: '请选择规格', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const brandOptions = ref<{ id: number; brandName: string }[]>([])
const warehouses = ref<any[]>([])
const stocks = ref<any[]>([])

function fmtQty(v?: number) { return v == null ? '0' : String(Number(v)) }
function warehouseName(wid?: number) { const w = warehouses.value.find(x => x.id === wid); return w ? (w.warehouseName || w.name || '') : '' }

/**
 * 库存分布：库存表按「产品+仓库+品质」存行，同一仓库会有 A/B/C 多行，
 * 这里按仓库合并成一行（数量为各品质合计，品质以标签并列展示），数量合计为 0 的仓库不显示。
 */
const stockRows = computed(() => {
  const map = new Map<number, { warehouseId: number; quantity: number; qualities: { qualityType: string; quantity: number }[] }>()
  for (const s of stocks.value as any[]) {
    const wid = Number(s.warehouseId)
    const qty = Number(s.quantity) || 0
    if (!map.has(wid)) map.set(wid, { warehouseId: wid, quantity: 0, qualities: [] })
    const row = map.get(wid)!
    row.quantity += qty
    if (qty !== 0) row.qualities.push({ qualityType: String(s.qualityType || ''), quantity: qty })
  }
  return [...map.values()]
    .filter(r => r.quantity !== 0)
    .map(r => ({ ...r, qualities: r.qualities.sort((a, b) => a.qualityType.localeCompare(b.qualityType)) }))
    .sort((a, b) => b.quantity - a.quantity)
})

/** 已加载的数据标识（新增/编辑 或 切换产品时重新拉取，避免重复请求与误覆盖未保存修改） */
const loadedKey = ref('')

async function init() {
  const key = id.value != null ? `edit-${id.value}` : 'add'
  if (loadedKey.value === key) return
  loadedKey.value = key

  Object.assign(form, defaultForm())
  stocks.value = []
  originalSku.value = ''
  if (id.value == null) {
    // 2026-09-21：新增时预填一个自动生成的 SKU（用户可修改）；拉取失败不阻塞，留空则由后端自动生成
    // 2026-09-21（供货SKU）：进入新增时尚未选供货商 ⇒ 前缀为默认 SKU-；选中供货商后由 onSupplierChange 重取
    form.supplierId = undefined
    await prefillSku()
    return
  }

  loading.value = true
  try {
    const [p, b, w, s] = await Promise.all([
      request.get(`/product/${id.value}`) as Promise<Product>,
      request.get('/brand/enabled') as Promise<any>,
      request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: '' } }) as Promise<any>,
      request.get('/warehouse/stock/page', { params: { productId: id.value, stockType: 'PRODUCT', pageSize: 500 } }) as Promise<any>
    ])
    Object.assign(form, defaultForm(), p || {})
    originalSku.value = form.sku || ''
    brandOptions.value = Array.isArray(b) ? b : (b?.records || [])
    warehouses.value = w?.records || []
    stocks.value = s?.records || []
  } catch {
    ElMessage.error('加载产品信息失败')
  } finally { loading.value = false }
}

async function loadBrands() {
  try {
    const res = await request.get<any, any>('/brand/enabled')
    brandOptions.value = Array.isArray(res) ? res : (res?.records || [])
  } catch { brandOptions.value = [] }
}

/**
 * 供货商下拉（2026-09-21 新增字段）：口径与采购单/采购退货一致 —— 只列类型为 product 的「供货商」。
 * 刻意**不按状态过滤**：编辑老产品时若该供货商已停用，过滤后下拉里找不到对应项会退化成显示原始数字 id。
 */
const supplierOptions = ref<{ id: number; name: string; supplySku?: string }[]>([])
async function loadSuppliers() {
  try {
    const res = await request.get<any, any>('/supplier/page', { params: { pageSize: 500, supplierType: 'product' } })
    supplierOptions.value = res?.records || []
  } catch { supplierOptions.value = [] }
}

/**
 * 上一次自动预填的 SKU 值：用来判断「用户是否手工改过 SKU」——
 * 只有没改过（或为空）时才允许切换供货商覆盖，避免把用户已输入的自定义编码静默冲掉。
 */
const autoSku = ref('')

/** 预填下一个可用 SKU：带供货商 ⇒ 后端按该供货商的「供货SKU」作前缀取号；失败不阻塞（留空由后端生成） */
async function prefillSku(supplierId?: number) {
  try {
    const v = String((await request.get<any, any>('/product/next-sku', { params: { supplierId } })) || '')
    autoSku.value = v
    form.sku = v
  } catch { autoSku.value = '' }
}

/**
 * 切换供货商（2026-09-21 供货SKU）：**仅新增态**重新预填 SKU —— 前缀变成该供货商的「供货SKU」。
 * 编辑态刻意不动 SKU：SKU 是既有编码，且历史单据里存的是快照，改它会对不上账。
 */
async function onSupplierChange(v: any) {
  if (v === ADD_MARKER) { form.supplierId = undefined; router.push('/outsource/supplier/manage'); return }
  if (!isNew.value) return
  if (form.sku && form.sku !== autoSku.value) return
  await prefillSku(v != null && v !== '' ? Number(v) : undefined)
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    const newSku = (form.sku || '').trim()
    form.sku = newSku
    // 2026-09-21：编辑态修改 SKU 需二次确认 —— 历史单据里的 SKU 是当时的快照，不会被一并更新
    if (form.id && newSku && newSku !== originalSku.value) {
      try {
        await ElMessageBox.confirm(
          `SKU 将由「${originalSku.value}」改为「${newSku}」。历史单据中的 SKU 是当时的快照，不会一并更新。确认修改？`,
          '修改 SKU 确认',
          { type: 'warning', confirmButtonText: '确认修改', cancelButtonText: '取消' }
        )
      } catch { return }
    }
    saving.value = true
    try {
      if (form.id) {
        await updateProduct(form.id, form)
        ElMessage.success('修改成功')
      } else {
        await addProduct(form)
        ElMessage.success('新增成功')
      }
      router.push('/product')
    } catch {
      // 错误已在拦截器中提示
    } finally { saving.value = false }
  })
}

function handleCancel() { router.push('/product') }
function goProject(pid?: number) { if (pid) router.push(`/dev/project/edit/${pid}`) }
function goStockLog(row: any) { router.push(`/inventory/warehouse/product-history/${row.warehouseId}/${id.value}`) }

onMounted(() => { loadBrands(); loadSuppliers(); init() })
// keep-alive 缓存下再次进入会复用组件，onMounted 不再触发；路由参数变化（切换产品/新增）也需重新加载
onActivated(() => { init() })
watch(() => route.fullPath, () => { init() })
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">{{ isNew ? '新增产品' : `产品详情 — ${form.name}` }}</span>
          <div>
            <el-button type="primary" size="small" :loading="saving" @click="handleSave">保存</el-button>
            <el-button size="small" @click="handleCancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="SKU">
              <!-- 2026-09-21：新增时预填自动生成的编码、允许修改；编辑时也可改（改前二次确认）。
                   留空则由后端自动生成 —— 故此处不做必填校验 -->
              <el-input v-model="form.sku" maxlength="64" placeholder="自动生成，可修改" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入产品名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <!-- 2026-09-21（供货SKU）：新增时选中供货商 ⇒ SKU 前缀取该供货商的「供货SKU」
                 （如 ABC ⇒ ABC-000001）；编辑态改供货商**不会**重算已有 SKU。
                 2026-09-21 排序调整：供货商**放在 SKU 正下方（第 2 行第 1 格）**——
                 它会改动上一行的 SKU，紧贴其下才能让联动一眼可见（原先与品牌互换位置，
                 被品牌隔在斜下方，视线要往左上回看）。品牌随之移到第 2 行第 2 格。 -->
            <el-form-item label="供货商">
              <el-select v-model="form.supplierId" placeholder="请选择供货商" clearable filterable style="width:100%"
                @change="onSupplierChange">
                <el-option v-for="s in supplierOptions" :key="s.id"
                  :label="s.supplySku ? `${s.name}（${s.supplySku}）` : s.name" :value="s.id" />
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-select v-model="form.brandId" placeholder="请选择品牌" clearable style="width:100%"
                @change="(v: any) => { if (v === ADD_MARKER) { form.brandId = undefined; router.push('/inventory/brand'); return } }">
                <el-option v-for="b in brandOptions" :key="b.id" :label="b.brandName" :value="b.id" />
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <!-- 2026-09-21：原「分类」（自由文本）替换为「规格」（固定三选一，必填） -->
            <el-form-item label="规格" prop="specType">
              <el-select v-model="form.specType" placeholder="请选择规格" style="width:100%">
                <el-option v-for="o in specOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="通用型号">
              <el-input v-model="form.generalModel" placeholder="适用多款机型" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <el-input v-model="form.unit" placeholder="pcs" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width:100%">
                <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安全库存">
              <el-input-number v-model="form.safetyStock" :min="0" :precision="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="成本价">
              <!-- 成本价默认由入库自动加权；手填后标记为手工锁定，自动加权将跳过 -->
              <el-input-number v-model="form.costPrice" :min="0" :precision="2" controls-position="right" style="width:100%"
                placeholder="入库后自动计算" @change="(v: any) => { if (v != null && form.id) form.costManual = 1 }" />
              <div v-if="form.lastInPrice != null" style="font-size:var(--app-font-xs);color:var(--el-text-color-secondary)">
                最近进价 {{ Number(form.lastInPrice).toFixed(2) }}
              </div>
            </el-form-item>
          </el-col>
          <el-col v-if="!isNew" :span="24">
            <el-form-item label="关联项目">
              <el-button v-if="form.projectId" type="primary" link @click="goProject(form.projectId)">查看项目</el-button>
              <span v-else style="color:var(--app-text-secondary)">—</span>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card v-if="!isNew" shadow="never">
      <template #header><span style="font-weight:600">库存分布</span></template>
      <el-table :data="stockRows" border stripe size="small" @row-click="goStockLog">
        <el-table-column label="仓库" min-width="160">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) || '—' }}</template>
        </el-table-column>
        <el-table-column label="品质分布" min-width="200">
          <template #default="{ row }">
            <template v-if="row.qualities.length">
              <el-tag v-for="q in row.qualities" :key="q.qualityType" size="small" type="info" style="margin-right:6px">
                {{ q.qualityType }} {{ fmtQty(q.quantity) }}
              </el-tag>
            </template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="库存数量" width="120" align="right">
          <template #default="{ row }">{{ fmtQty(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goStockLog(row)">库存流水</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!stockRows.length" style="padding:12px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">暂无库存记录</div>
    </el-card>

    <div style="text-align:center;margin-top:20px">
      <el-button @click="router.back()">返回</el-button>
    </div>
  </div>
</template>

<style scoped>
.detail-page { display: flex; flex-direction: column; gap: 12px; }
</style>
