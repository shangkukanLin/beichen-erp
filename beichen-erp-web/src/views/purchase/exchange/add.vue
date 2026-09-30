<template>
  <!-- 统一骨架（2026-09-23 全站最终口径）：页头左端=返回 → 标题 → 右端=操作（保存） -->
  <PageShell :title="isEdit ? '编辑采购换货单' : '新增采购换货单'" back-fallback="/inventory/purchase-exchange">
    <template #actions>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>

    <el-card shadow="never">
      <!-- label 宽度用 **lg 档**（2026-09-28 全局扫描）：本页含「退回出库仓」「换入入库仓」等 5 字+星号标签 -->
      <el-form :model="form" label-width="var(--app-label-width-lg)" ref="formRef">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="供货商" required>
              <!-- RemoteSelect 的 update:model-value 只回传值，选中项需通过 pick 事件取（否则拿不到单号/仓库） -->
              <RemoteSelect :model-value="form.supplierId" add-route="/supplier/manage/add" :fetch="fetchSuppliers"
                label-key="name" placeholder="选择供货商" :disabled="isEdit" style="width:100%"
                @update:model-value="(v:any)=>{ form.supplierId = v; form.purchaseOrderId = null; form.purchaseOrderCode = ''; items = [] }" domain="supplier" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <!-- 2026-09-21（用户口径「可以不强关联采购单」）：来源采购单**可选** ——
                 不选 = 无单换货（下方「添加明细」手工选产品）；选了则自动带出可换明细（可换量受采购单约束）。 -->
            <el-form-item label="来源采购单">
              <RemoteSelect :model-value="form.purchaseOrderId" :fetch="fetchPurchaseOrders"
                label-key="code" placeholder="可不选（不选则手工录入明细）" :disabled="isEdit" style="width:100%"
                @update:model-value="(v:any)=>{ form.purchaseOrderId = v ?? null }"
                @pick="(opts:any[])=>{ onPurchaseOrderChange(form.purchaseOrderId, opts?.[0]) }" domain="purchaseOrder" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换货日期" required>
              <el-date-picker v-model="form.exchangeDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退回出库仓" required>
              <RemoteSelect v-model="form.warehouseOutId" add-route="/inventory/warehouse" :fetch="fetchFinishedWarehouses"
                label-key="warehouseName" placeholder="退给供货商，从我方仓扣减" style="width:100%" domain="warehouse" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换入入库仓" required>
              <RemoteSelect v-model="form.warehouseInId" add-route="/inventory/warehouse" :fetch="fetchFinishedWarehouses"
                label-key="warehouseName" placeholder="换回良品入我方仓（可与退回仓相同）" style="width:100%" domain="warehouse" />
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
      <!-- 明细工具条（2026-09-21）：无单换货手工加行；关联采购单时明细已自动带出（仍可增删行） -->
      <div style="margin-bottom:8px">
        <el-button type="primary" size="small" @click="addItem">添加明细</el-button>
        <span style="margin-left:8px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">
          {{ form.purchaseOrderId
            ? '明细已按来源采购单带出，可增删行；退回数量受「可换数量」约束'
            : '未关联采购单（无单换货）：请手工添加并选择产品；能否出库以审核时的库存校验为准' }}
        </span>
      </div>
      <el-table :data="items" border size="small" max-height="380">
        <!-- ===== 退回侧：退回供货商，从我方仓出库 ===== -->
        <el-table-column label="退回（退给供货商）" align="center">
          <!-- 2026-09-21（UI 优化）：明细表原来 12 列、合计 1440px ⇒ 必然横向滚动。
               ① 删掉「SKU」独占列（产品格显示「SKU | 名称」，与选品下拉同一格式，信息不丢）
               ② 输入控件统一 size="small"，数量/单价用 :controls="false"（不带加减按钮 ⇒ 更窄）
               ③ 按内容重新配宽 ⇒ 合计 920px < 内容区 948px（1262 窗口），**一行显示完、不左右滑动** -->
          <el-table-column label="退回产品" width="128" show-overflow-tooltip>
            <template #default="{ row }">
              <!-- 无单换货：手工选产品（可输 SKU 远程搜）；关联采购单：产品由采购明细带出，只读 -->
              <el-select v-if="!form.purchaseOrderId" v-model="row.productId" placeholder="选择产品（可输SKU）"
                size="small" filterable remote :remote-method="loadProducts" style="width:100%"
                @change="(v: number) => onProductChange(v, row)">
                <el-option v-for="m in productOptions" :key="m.id" :label="productLabel(m)" :value="m.id" />
              
                <template #footer><div style="padding:6px 12px;cursor:pointer;text-align:center;font-size:12px;color:var(--app-color-primary,#409eff);border-top:1px solid var(--app-color-border,#ebeef5)" @click="$router.push('/product/add')">+ 鏂板</div></template>
              </el-select>
              <span v-else>{{ productText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="可换数量" width="64" align="right">
            <template #default="{ row }">{{ row.maxQuantity ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="退回数量" width="78">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="0" :step="1" size="small" :controls="false" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="退回品质" width="78">
            <template #default="{ row }">
              <el-select v-model="row.qualityType" size="small" style="width:100%">
                <el-option v-for="o in qualityOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="退回单价" width="78">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" :controls="false" style="width:100%" />
            </template>
          </el-table-column>
        </el-table-column>

        <!-- ===== 换入侧：供货商换回，入我方仓 ===== -->
        <el-table-column label="换入（供货商换回）" align="center">
          <el-table-column label="换入数量" width="78">
            <template #default="{ row }">
              <el-input-number v-model="row.inQuantity" :min="0" :precision="0" :step="1" size="small" :controls="false" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="换入品质" width="78">
            <template #default="{ row }">
              <el-select v-model="row.inQualityType" size="small" style="width:100%">
                <el-option v-for="o in qualityOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="换入单价" width="78">
            <template #default="{ row }">
              <el-input-number v-model="row.inUnitPrice" :min="0" :precision="2" size="small" :controls="false" style="width:100%" />
            </template>
          </el-table-column>
        </el-table-column>

        <!-- 逐产品付费（2026-09-21 用户口径：采购换货也要有付费、且精确到产品；方向=我方付给供货商）。
             金额 > 0 即该产品付费；类型**必选**（与销售侧一致：填了金额必须能定类型）。
             原来这列是「应付净额」——每行净额已由下方合计给出，改用它换付费列，表格仍一行显示完。 -->
        <el-table-column label="付费" width="146" align="center">
          <template #default="{ row }">
            <div style="display:flex;gap:4px">
              <el-input-number v-model="row.chargeAmount" :min="0" :precision="2" size="small" :controls="false"
                placeholder="金额" style="width:68px" />
              <el-select v-model="row.chargeType" size="small" placeholder="类型" clearable style="width:66px"
                :disabled="!(Number(row.chargeAmount) > 0)">
                <el-option v-for="o in payTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="88">
          <template #default="{ row }"><el-input v-model="row.remark" size="small" /></template>
        </el-table-column>
        <el-table-column label="操作" width="52" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeItem($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <!-- 合计（净额列已挪到行尾，整单位汇总在这里看；加价换新的差额一眼可见） -->
      <div v-if="items.length" style="margin-top:8px;font-size:var(--app-font-xs);color:var(--app-text-secondary)">
        合计：退回 {{ totalReturnAmount.toFixed(2) }} ｜ 换入 {{ totalInAmount.toFixed(2) }} ｜
        应付净额 <b style="color:var(--app-text-primary)">{{ (totalInAmount - totalReturnAmount).toFixed(2) }}</b>
      </div>
      <div v-if="items.length===0" style="text-align:center;color:#999;padding:16px">
        选择供货商后：<b>选了来源采购单</b>会自动带出可换明细；<b>不选采购单</b>（无单换货）请点「添加明细」手工录入
      </div>

      <!-- 是否付费（2026-09-21 用户口径）：⚠️ 方向是「**我们向供货商付费**」⇒ 审核生成一条正向应付 -->
      <!-- 逐产品付费（2026-09-21 用户口径）：金额填在**明细行的「付费」列**上（一行 = 一个产品），
           这里只留「合计（自动，只读）」+「整单说明」；是否付费由明细推导（合计 > 0 ⇒ 付费）。
           ⚠️ 方向：**我方付给供货商** ⇒ 审核生成一条正向应付（金额 = Σ明细，remark 逐产品）。 -->
      <el-row :gutter="16" style="margin-top:8px">
        <!-- 2026-09-28 全局扫描：以下两个是**独立 el-form-item**（没有 el-form 包裹 ⇒ 被 page.css
             的 90px 兜底规则压住），而「付费合计（自动）/付费说明（整单）」实测需 124px ⇒ 显式指定 xl 档；
             合计列 6→8 是为它腾出内容宽度（label 变宽后 span=6 只剩约 100px） -->
        <el-col :span="8">
          <el-form-item label="付费合计（自动）" label-width="var(--app-label-width-xl)">
            <span style="font-weight:600;color:#e6a23c">{{ chargeTotal.toFixed(2) }}</span>
            <span style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">= Σ 明细行付费</span>
          </el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label="付费说明（整单）" label-width="var(--app-label-width-xl)">
            <el-input v-model="form.chargeReason" placeholder="选填，如：换货服务费 / 补差价（落到该单付费台账备注）" />
          </el-form-item>
        </el-col>
      </el-row>
      <div v-if="chargeTotal > 0" style="margin:0 0 10px 110px;font-size:var(--app-font-xs);color:var(--app-color-warning)">
        付费方向：<b>我方付给供货商</b> ⇒ 审核后额外生成一条正向应付（我方欠供货商 +{{ chargeTotal.toFixed(2) }}）
      </div>

    </el-card>
  </PageShell>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { onMounted, reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'
import {
  ProductQualityType, ProductQualityTypeLabel,
  WarehouseType, WarehouseCategory, PurchaseChargeType, PurchaseChargeTypeLabel,
  PURCHASE_EXCHANGE_DIRTY_KEY,
} from '@/api/enums'
import { invalidate } from '@/utils/dataFreshness'
// 产品下拉（无单换货手工加行时用）：与采购退货同款 productLabel（"SKU | 名称"）
import { productLabel } from '@/api/product'
import {
  getPurchaseExchange, createPurchaseExchange, updatePurchaseExchange,
  getPurchaseExchangePurchaseOrders, getPurchaseExchangePurchaseOrderItems,
  getPurchaseExchangeSourceOrder,
} from '@/api/purchase'

const route = useRoute()
const router = useRouter()
const tabStore = useTabStore()
const formRef = ref()
const saving = ref(false)
const isEdit = ref(false)

const form = reactive({
  id: null as number | null,
  supplierId: null as number | null,
  // 来源采购单**可选**（2026-09-21）：为空即无单换货，明细手工录入
  purchaseOrderId: null as number | null,
  purchaseOrderCode: '',
  warehouseOutId: null as number | null,
  warehouseInId: null as number | null,
  exchangeDate: localDate(),
  // 是否付费（2026-09-21）：⚠️ 方向 = 我们向供货商付费 ⇒ 审核生成正向应付
  chargeFlag: 0,
  chargeType: '',
  chargeAmount: 0,
  chargeReason: '',
  remark: '',
})
const items = ref<any[]>([])
/**
 * 未保存拦截（2026-09-23 统一模板）
 * ⚠️ 必须写在 form / items 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form, items: items.value }))
/** 无单换货手工加行时的产品候选（远程搜） */
const productOptions = ref<any[]>([])

const qualityOptions = computed(() =>
  [ProductQualityType.A, ProductQualityType.B, ProductQualityType.C, ProductQualityType.DEFECT].map((v) => ({
    value: v, label: ProductQualityTypeLabel[v] || v
  }))
)
/** 付费类型（对应后端 purchase/common/PurchaseChargeType；方向：我们向供货商付费） */
const payTypeOptions = computed(() =>
  Object.values(PurchaseChargeType).map((v) => ({ value: v, label: PurchaseChargeTypeLabel[v] || v }))
)
/**
 * 付费合计 = Σ 明细行付费（2026-09-21 逐产品口径）。
 * <p>单据级 charge_amount 不再是"手填一个总数"，而是本合计 —— 保存时后端也会按 Σ明细 回写一次；
 * 是否付费（chargeFlag）同样由它推导 ⇒ 前端不再单独放"是否付费"开关。</p>
 */
const chargeTotal = computed(() =>
  items.value.reduce((s: number, it: any) => s + (Number(it.chargeAmount) || 0), 0))
/** 明细合计（表下方）：净额 = 换入 − 退回，「加价换新」为正、等价换货为 0 */
const totalReturnAmount = computed(() =>
  items.value.reduce((s, i) => s + (Number(i.quantity) || 0) * (Number(i.unitPrice) || 0), 0))
const totalInAmount = computed(() =>
  items.value.reduce((s, i) => s + (Number(i.inQuantity) || 0) * (Number(i.inUnitPrice) || 0), 0))

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
      // SKU 由后端随可换明细一起返回（2026-09-21）：明细表把「SKU | 名称」并到一列，不再单列 SKU
      sku: r.sku || '',
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
    // 期 3（2026-09-19 读隔离）：来源采购单改走换货页自身前缀（原读 /inventory/purchase/{id} 需 purchase:order）
    const po: any = await getPurchaseExchangeSourceOrder(orderId)
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
    purchaseOrderId: h.purchaseOrderId ?? null,
    purchaseOrderCode: h.purchaseOrderCode || '',
    warehouseOutId: h.warehouseOutId,
    warehouseInId: h.warehouseInId,
    exchangeDate: h.exchangeDate ? String(h.exchangeDate).slice(0, 10) : '',
    // 是否付费（2026-09-21）：方向 = 我们向供货商付费
    chargeFlag: Number(h.chargeFlag || 0),
    chargeType: h.chargeType || '',
    chargeAmount: Number(h.chargeAmount || 0),
    chargeReason: h.chargeReason || '',
    remark: h.remark || '',
  })
  items.value = (res?.items || []).map((it: any) => ({
    ...it,
    quantity: Number(it.quantity),
    unitPrice: Number(it.unitPrice ?? 0),
    inQuantity: Number(it.inQuantity ?? it.quantity),
    inUnitPrice: Number(it.inUnitPrice ?? it.unitPrice ?? 0),
    // 逐产品付费（2026-09-21）：明细接口已回传逐行付费字段
    chargeAmount: Number(it.chargeAmount || 0),
    chargeType: it.chargeType || '',
  }))
}

function removeItem(i: number) { items.value.splice(i, 1) }

/** 明细表「退回产品」只读展示：有 SKU 时用 `SKU | 名称`（与选品下拉 productLabel 同格式，所以不再单列 SKU） */
function productText(row: any) {
  const name = row?.productName || ''
  return row?.sku ? `${row.sku} | ${name}` : name
}

/**
 * 添加明细（无单换货用，2026-09-21）：一行 = 退回侧 + 换入侧配对。
 * 退回默认不良品 DEFECT、换入默认 A 规；数量各 1；产品/单价由用户选择后补齐。
 */
function addItem() {
  items.value.push({
    purchaseOrderItemId: null,
    productId: null,
    productName: '',
    sku: '',
    // 逐产品付费（2026-09-21）：默认不付费，金额逐行填；类型在金额 > 0 时必选
    chargeAmount: 0,
    chargeType: '',
    quantity: 1,
    maxQuantity: null,
    qualityType: ProductQualityType.DEFECT,
    unitPrice: 0,
    inQuantity: 1,
    inQualityType: ProductQualityType.A,
    inUnitPrice: 0,
    remark: ''
  })
}

/** 产品候选：远程搜（可输 SKU），与采购退货同款接口 */
async function loadProducts(query?: string) {
  const params: any = { pageSize: 100 }
  if (query) params.keyword = query
  const res: any = await request.get('/product/page', { params })
  productOptions.value = res?.records || []
}

/**
 * 选中产品后带出 SKU/名称与默认单价（最近进价 lastInPrice，其次标准价 price）。
 * <p>⚠️ 无单换货**不设"可换数量"上限**（没有采购单可算）——退回能否出库由**审核时的库存校验**把关：
 * 后端会按品质逐行算出缺口并列清（产品/品质/需量/可用/缺口），比前端预判更准。</p>
 */
function onProductChange(val: number, row: any) {
  const m = productOptions.value.find((x: any) => x.id === val)
  if (!m) return
  row.productName = m.name || ''
  row.sku = m.sku || ''
  const price = Number(m.lastInPrice ?? m.price ?? 0)
  row.unitPrice = price
  if (!row.inUnitPrice) row.inUnitPrice = price
  if (!row.inQuantity) row.inQuantity = row.quantity
}

async function submit() {
  if (!form.supplierId) { ElMessage.warning('请选择供货商'); return }
  // 2026-09-21（用户口径）：来源采购单**可选** —— 不选即无单换货，明细由下方手工录入
  if (!form.warehouseOutId) { ElMessage.warning('请选择退回出库仓'); return }
  if (!form.warehouseInId) { ElMessage.warning('请选择换入入库仓'); return }
  const its = items.value.filter((i) => Number(i.quantity) > 0)
  if (its.length === 0) { ElMessage.warning('请至少录入一条换货明细'); return }
  if (its.some((i) => !i.productId)) { ElMessage.warning('请为每条明细选择退回产品'); return }
  for (const it of its) {
    // 可换量只约束退回数量（换入属正常入库，不受限）；无单换货没有上限，靠审核时的库存校验
    if (it.maxQuantity != null && Number(it.quantity) > it.maxQuantity) {
      ElMessage.warning(`产品「${it.productName}」退回数量(${it.quantity})超过可换数量(${it.maxQuantity})`)
      return
    }
    if (!(Number(it.inQuantity) > 0)) {
      ElMessage.warning(`产品「${it.productName}」换入数量必须大于 0`)
      return
    }
  }
  // 付费校验（2026-09-21 逐产品口径）：**金额填在哪一行就算哪个产品付费**（金额 0 = 不付费）；
  // 填了金额的行必须选类型（本行必选，没有批量类型可继承）——后端会再逐行校验一次
  for (const it of its) {
    if (Number(it.chargeAmount) > 0 && !it.chargeType) {
      ElMessage.warning(`产品「${it.productName || it.productId}」已填付费金额，请选择付费类型`)
      return
    }
  }
  const charged = chargeTotal.value > 0
  saving.value = true
  try {
    const body = {
      supplierId: form.supplierId,
      // 无单换货：purchaseOrderId 传 null（后端不再要求必填，也不会去核可换量）
      purchaseOrderId: form.purchaseOrderId || null,
      purchaseOrderCode: form.purchaseOrderId ? form.purchaseOrderCode : '',
      warehouseOutId: form.warehouseOutId, warehouseInId: form.warehouseInId,
      exchangeDate: form.exchangeDate,
      // 是否付费（方向：我们向供货商付费）⇒ 审核生成一条正向应付；
      // 金额由后端按 Σ明细 回写 ⇒ 这里只外带"是否付费"与整单说明（chargeAmount 传 0 不参与计算）
      chargeFlag: charged ? 1 : 0,
      chargeType: '',
      chargeAmount: 0,
      chargeReason: charged ? (form.chargeReason || '') : '',
      remark: form.remark,
      items: its.map((i) => ({
        // 退回侧（无单换货时 purchaseOrderItemId 为 null，后端跳过可换量校验）
        purchaseOrderItemId: i.purchaseOrderItemId ?? null, productId: i.productId, productName: i.productName,
        qualityType: i.qualityType, quantity: i.quantity, unitPrice: i.unitPrice,
        // 换入侧（同品：产品即退回产品；数量可不等如退2换1）
        inQuantity: i.inQuantity, inQualityType: i.inQualityType, inUnitPrice: i.inUnitPrice,
        // 逐产品付费：金额 > 0 才付费；类型必选（行内已校验）
        chargeAmount: Number(i.chargeAmount) || 0,
        chargeType: Number(i.chargeAmount) > 0 ? (i.chargeType || '') : '',
        chargeReason: form.chargeReason || '',
        remark: i.remark
      }))
    }
    if (isEdit.value) {
      await updatePurchaseExchange(form.id!, body)
      ElMessage.success('已保存')
      invalidate('purchaseExchange')
      // 保存成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本页签
      markClean()
      tabStore.closeTabAndBack(route.path)
      router.push(`/inventory/purchase-exchange/detail/${form.id}`)
    } else {
      await createPurchaseExchange(body)
      ElMessage.success('已新增')
      invalidate('purchaseExchange')
      markClean()
      tabStore.closeTabAndBack(route.path)
      router.push('/inventory/purchase-exchange')
    }
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  // 取消返回：先清脏标记（否则离开会被未保存确认拦住），再关掉本页签
  markClean()
  tabStore.closeTabAndBack(route.path)
  if (isEdit.value) router.push(`/inventory/purchase-exchange/detail/${form.id}`)
  else router.push('/inventory/purchase-exchange')
}

onMounted(async () => {
  const id = route.query.id
  if (id !== undefined && id !== '') {
    isEdit.value = true
    await loadEdit(Number(id))
    takeBaseline()
    return
  }
  const poId = route.query.fromOrder
  if (poId !== undefined && poId !== '') {
    await initFromPurchaseOrder(Number(poId))
  }
  // 初始化/预填完成 ⇒ 建立"未保存"基线（必须在加载之后，否则会把回填误判成用户修改）
  takeBaseline()
})
</script>

<style scoped>
/* 页头/底部操作条已统一到全局骨架（PageShell + styles/page.css） */
</style>
