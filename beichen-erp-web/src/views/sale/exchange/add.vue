<template>
  <!-- 统一骨架（2026-09-23 全站最终口径）：页头左端=返回 → 标题 → 右端=操作（保存） -->
  <PageShell :title="isEdit ? '编辑销售换货单' : '新增销售换货单'" back-fallback="/sale/exchange">
    <template #actions>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>

    <el-card shadow="never">
      <!-- 2026-09-20（F7-147）：本页没有 rules，校验靠 submit 里的逐项 if ⇒ 不再挂无用的 formRef -->
      <!-- label 宽度用 **xl 档**（2026-09-28 全局扫描）：本页含「收费合计（自动）」这类带全角括号的长标签（实测需 124px） -->
      <el-form :model="form" label-width="var(--app-label-width-xl)">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="客户" required>
              <!-- RemoteSelect 的 update:model-value 只回传值，选中项需通过 pick 事件取（否则拿不到单号/客户/仓库） -->
              <RemoteSelect :model-value="form.customerId" add-route="/inventory/customer/add" :fetch="fetchCustomers"
                label-key="name" placeholder="选择客户" :disabled="isEdit" style="width:100%"
                @update:model-value="(v:any)=>{ const hadSource = !!form.saleOrderId; form.customerId = v; form.saleOrderId = null; form.saleOrderCode = ''; if (hadSource) items = [] }" domain="customer" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <!-- 2026-10-01（F8 第 2 步，用户口径「子菜单裸进可以不关联」）：来源销售单由**必填改为选填**。
                 不选 = 无单换货（线下/历史/其他渠道补录），此时手工添加明细，退回/换出以审核时库存校验为准；
                 与进货侧「来源采购单」的可空口径一致。 -->
            <el-form-item label="来源销售单">
              <RemoteSelect :model-value="form.saleOrderId" :fetch="fetchSaleOrders"
                label-key="code" :placeholder="sourceLocked ? '' : '选填：先选客户；不选则手工录入明细'"
                :disabled="isEdit || sourceLocked" style="width:100%"
                @update:model-value="(v:any)=>{ form.saleOrderId = v }"
                @pick="(opts:any[])=>{ onSaleOrderChange(form.saleOrderId, opts?.[0]) }" domain="saleOrder" />
              <!-- 2026-10-01（第 3 步，用户口径「销售单里的退换货与销售单强关联」）：带 ?saleOrderId= 进入时
                   来源锁死，不能清空退化成无单换货；只有从子菜单裸进时才可自选/不选。 -->
              <span v-if="sourceLocked" style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">
                由来源销售单发起，不可更改
              </span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换货日期" required>
              <el-date-picker v-model="form.exchangeDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换入仓" required>
              <RemoteSelect v-model="form.warehouseInId" add-route="/inventory/warehouse" :fetch="fetchAfterSaleWarehouses"
                label-key="warehouseName" placeholder="成品仓" style="width:100%" domain="warehouse" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换出仓" required>
              <RemoteSelect v-model="form.warehouseOutId" add-route="/inventory/warehouse" :fetch="fetchFinishedWarehouses"
                label-key="warehouseName" placeholder="成品仓" style="width:100%" domain="warehouse" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
          </el-col>
          <!-- 2026-09-21（用户口径）：去掉「收费类型（批量）」+「套用全部」—— 类型一律在明细行按产品选 -->
          <el-col :span="8">
            <el-form-item label="收费合计（自动）">
              <span style="font-weight:600;color:#e6a23c">{{ chargeTotal.toFixed(2) }}</span>
              <span style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">= Σ 明细行收费</span>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="收费说明">
              <el-input v-model="form.chargeReason" placeholder="选填，整单共用一句话（会写入财务台账备注）" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 收费（2026-09-21 用户口径：**收费精确到产品**）：单据级不再手填金额（= Σ明细，后端回写），
             上方只作「批量默认」（类型/说明可一键套用），金额在明细行逐产品填。
             ⚠️ 方向：**向客户收取** ⇒ 审核生成一条正向应收（单号 -FEE，金额 = Σ明细）。 -->
        <div style="margin: 0 0 12px 100px; font-size: var(--app-font-xs); color: var(--app-text-secondary); line-height: 1.5">
          收费方向：<b style="color: var(--app-color-warning)">向客户收取</b>（我方应收客户）
          <span v-if="chargeTotal > 0"> ⇒ 审核后生成一条应收 <b style="color: var(--app-color-warning)">{{ chargeTotal.toFixed(2) }}</b>（单号后缀 -FEE，可整单核销）</span>
          <span v-else> ⇒ 在明细行逐产品填收费金额，审核时汇总成一条应收</span>
        </div>
      </el-form>

      <el-divider content-position="left">
        换货明细
        <span style="font-weight:normal;color:#909399;margin-left:8px">
          只支持同品换货：换出产品与退回产品相同，换出数量可与退回数量不等（如退2换1）
        </span>
      </el-divider>
      <!-- 工具条（2026-10-01 第 2 步）：**仅在无来源换货时出现** —— 有来源时明细由来源销售单带出，
           手工加行会因没有 saleOrderItemId 而无法参与可换量校验，语义上无意义（与进货侧同款口径）。 -->
      <div v-if="!form.saleOrderId" style="margin-bottom:8px">
        <el-button type="primary" size="small" @click="addItem">添加明细</el-button>
        <span style="margin-left:8px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">
          未关联销售单（无单换货）：请手工添加并选择产品；能否出库以审核时的库存校验为准
        </span>
      </div>
      <!-- 2026-09-21（UI + 逐产品收费）：原来 10 列 1160px ⇒ 横向滚动 212px。现：
           ① 删「SKU」独占列 —— 退回产品列直接显示「SKU | 名称」（后端 saleOrderItems 已回 sku）
           ② 控件 size=small、数量/单价 :controls=false ⇒ 更窄
           ③ 新增「收费」列（金额 + 类型，**逐产品**；金额 0 = 不收费）
           ④ 列宽合计 886px < 内容区 948px ⇒ 一行显示完、不左右滑动（有守卫断言） -->
      <el-table :data="items" border size="small" max-height="380">
        <!-- ===== 退回侧：客户退回，入成品仓（品质待整理 PENDING）待整理 ===== -->
        <el-table-column label="退回（客户退回，入成品仓待整理）" align="center">
          <el-table-column label="退回产品" width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <!-- 无来源换货：手工选产品（可输 SKU 远程搜）；有来源：产品由销售明细带出，只读 -->
              <el-select v-if="!form.saleOrderId" v-model="row.productId" placeholder="选择产品（可输SKU）"
                size="small" filterable remote :remote-method="loadProducts" style="width:100%"
                @change="(v: number) => onProductChange(v, row)">
                <el-option v-for="m in productOptions" :key="m.id" :label="productLabel(m)" :value="m.id" />
                <template #footer>
                  <div style="padding:6px 12px;cursor:pointer;text-align:center;font-size:12px;color:var(--app-color-primary,#409eff);border-top:1px solid var(--app-color-border,#ebeef5)"
                    @click="$router.push('/product/add')">+ 新增</div>
                </template>
              </el-select>
              <span v-else>{{ row.sku ? row.sku + ' | ' + row.productName : row.productName }}</span>
            </template>
          </el-table-column>
          <!-- 2026-10-01（用户口径）：原「可换数量」改为「库存数量」= 该**换出仓** + 该产品 +
               该**换出品质**的库存（stockForm=MATERIAL，与审核换出扣减口径一致）；
               退回数量的 :max = min(来源销售单数量, 库存数量)；无来源（无单换货）⇒ :max = 库存数量。 -->
          <el-table-column label="库存数量" width="74" align="right">
            <template #default="{ row }">
              <span v-if="row.stock !== undefined" :style="{ color: Number(row.stock) <= 0 ? 'red' : '' }">{{ row.stock }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="退回数量" width="88">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="0" :step="1" size="small" :controls="false" :max="row.quantityLimit" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column prop="unitPrice" label="原单价" width="88" align="right" />
        </el-table-column>

        <!-- ===== 换出侧：发给客户，从成品仓扣减；只支持同品，产品固定为退回产品 ===== -->
        <el-table-column label="换出（同品换货，从成品仓扣减）" align="center">
          <el-table-column label="换出数量" width="88">
            <template #default="{ row }">
              <el-input-number v-model="row.outQuantity" :min="0" :precision="0" :step="1" size="small" :controls="false" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="换出品质" width="88">
            <template #default="{ row }">
              <el-select v-model="row.outQualityType" size="small" style="width:100%" @change="() => refreshStock(row)">
                <el-option v-for="o in qualityOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="换出单价" width="92">
            <template #default="{ row }">
              <el-input-number v-model="row.outUnitPrice" :min="0" :precision="2" size="small" :controls="false" style="width:100%" />
            </template>
          </el-table-column>
        </el-table-column>

        <!-- 逐产品收费：金额 > 0 即向客户收取该产品的费用；类型留空时套用上方「收费类型（批量）」 -->
        <el-table-column label="收费" width="176" align="center">
          <template #default="{ row }">
            <div style="display:flex;gap:4px">
              <el-input-number v-model="row.chargeAmount" :min="0" :precision="2" size="small" :controls="false"
                placeholder="金额" style="width:80px" />
              <el-select v-model="row.chargeType" size="small" placeholder="类型" clearable style="width:88px"
                :disabled="!(Number(row.chargeAmount) > 0)">
                <el-option v-for="o in chargeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="52" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeItem($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <!-- 空态：有来源 = 自动带出可换明细；无来源 = 手工添加（与进货侧同一套三分支文案口径） -->
      <div v-if="items.length===0" style="text-align:center;color:#999;padding:16px">
        {{ form.saleOrderId
          ? '明细已按来源销售单带出，可增删行；退回数量受「可换数量」约束'
          : '未关联销售单（无单换货）：请手工添加并选择产品；能否出库以审核时的库存校验为准' }}
      </div>

    </el-card>
  </PageShell>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { onMounted, reactive, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'
import {
  ProductQualityType, ProductQualityTypeLabel,
  WarehouseType, ExchangeChargeType, ExchangeChargeTypeLabel,
  SALE_EXCHANGE_DIRTY_KEY,
} from '@/api/enums'
import {
  getSaleExchange, createSaleExchange, updateSaleExchange,
  getSaleExchangeSaleOrders, getSaleExchangeSaleOrderItems, getSaleExchangeSourceOrder,
} from '@/api/sale'
import { invalidate } from '@/utils/dataFreshness'

const route = useRoute()
const router = useRouter()
const tabStore = useTabStore()
const saving = ref(false)
const isEdit = ref(false)

/**
 * 由来源销售单发起（`?saleOrderId=` 进入）⇒ 来源锁定不可改（2026-10-01 第 3 步，用户口径
 * 「销售单里面的退换货和销售单强关联」）。从子菜单裸进时才允许自选/不选。
 */
const sourceLocked = computed(() => !!route.query.saleOrderId)

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

/**
 * 无来源换货的手工选品候选（2026-10-01 第 2 步）：有来源时明细由销售单带出，本列表不用。
 * 与进货侧采购换货同款接口与格式（`/product/page`，展示「SKU | 名称」，可输入 SKU 远程搜）。
 */
const productOptions = ref<any[]>([])
function productLabel(m: any) {
  return m?.sku ? `${m.sku} | ${m.name || m.productName || ''}` : (m?.name || m?.productName || '')
}

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
/**
 * 收费合计 = Σ 明细行收费（2026-09-21 逐产品口径）。
 * <p>单据级 charge_amount 不再是"手填一个总数"，而是本合计 —— 保存时后端也会按 Σ明细 回写一次。</p>
 */
const chargeTotal = computed(() =>
  items.value.reduce((s: number, it: any) => s + (Number(it.chargeAmount) || 0), 0))
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
 * 2026-10-01（用户口径）：后端 canExchange 现在 = **该销售明细的数量**，前端存为 sourceQty；
 * 真正的数量上限 = min(sourceQty, 换出仓 + 该产品 + 该换出品质的库存数量)，由 refreshStock 计算。
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
      // SKU：明细表把「SKU | 名称」并到一列（2026-09-21 UI），后端已随可换明细一并返回
      sku: r.sku || '',
      quantity: qty,
      // 来源销售单数量（**不是上限**）：上限 = min(本值, 库存)，见 refreshStock
      sourceQty: qty,
      unitPrice: price,
      // ===== 换出侧：只支持同品，产品即退回产品；数量默认 1:1、单价取原销售单价 =====
      outQuantity: qty,
      outUnitPrice: price,
      outQualityType: r.qualityType || ProductQualityType.A,
      // 逐产品收费（2026-09-21）：默认不收费，金额逐行填
      chargeAmount: 0,
      chargeType: '',
      remark: ''
    }
  })
  // 补齐「库存数量」与上限（依赖换出仓 + 换出品质）
  await Promise.all(items.value.map((it: any) => refreshStock(it)))
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
    chargeAmount: Number(h.chargeAmount || 0),
    chargeReason: h.chargeReason || '',
    remark: h.remark || '',
  })
  items.value = (res?.items || []).map((it: any) => ({
    ...it,
    quantity: Number(it.quantity),
    outQuantity: Number(it.outQuantity ?? it.quantity),
    outUnitPrice: Number(it.outUnitPrice ?? it.unitPrice ?? 0),
    // 逐产品收费（2026-09-21）：明细接口已回传逐行收费字段
    chargeAmount: Number(it.chargeAmount || 0),
    chargeType: it.chargeType || ''
  }))
  // 2026-09-21（去掉批量类型后）：旧草稿里"靠单据级类型兜底"的行显性化到行上 —— 否则编辑这类旧单
  // 会被新的"逐行必选类型"校验拦住、存不回去。单据级类型本就是按各明细类型一致派生的，落回无损。
  const docChargeType = h.chargeType || ''
  if (docChargeType) items.value.forEach((it: any) => { if (!it.chargeType) it.chargeType = docChargeType })
}

function removeItem(i: number) { items.value.splice(i, 1) }

/**
 * 添加明细（无来源换货用，2026-10-01 第 2 步）：一行 = 退回侧 + 换出侧配对。
 * 退回入仓品质由后端固定 PENDING（待整理），故明细无需退回品质；换出默认 A 规、
 * 数量与退回一致（可改，支持退 2 换 1）；产品与单价在选中后带出。
 */
function addItem() {
  const row: any = {
    saleOrderItemId: null,
    productId: null,
    productName: '',
    sku: '',
    quantity: 1,
    // 无来源 ⇒ 没有来源数量，上限 = 换出仓 + 该产品 + 该换出品质的库存（见 refreshStock）
    sourceQty: null,
    unitPrice: 0,
    outQuantity: 1,
    outQualityType: ProductQualityType.A,
    outUnitPrice: 0,
    chargeAmount: 0,
    chargeType: '',
  }
  items.value.push(row)
  refreshStock(row)
}

/** 产品候选：远程搜（可输 SKU），与采购退货/采购换货同款接口 */
async function loadProducts(query?: string) {
  const params: any = { pageSize: 100 }
  if (query) params.keyword = query
  const res: any = await request.get('/product/page', { params })
  productOptions.value = res?.records || []
}

/**
 * 2026-10-01（用户口径）：刷新某行的「库存数量」与**数量上限**。
 * 上限 = min(来源销售单数量, 该**换出仓** + 该产品 + 该**换出品质**的库存)；无来源 ⇒ 上限 = 库存。
 * ⚠️ 库存按 stockForm=MATERIAL 求和：审核换出扣减走的 changeStock 重载缺省形态即 MATERIAL。
 */
async function refreshStock(row: any) {
  row.stock = undefined
  row.quantityLimit = undefined
  if (!row.productId || !form.warehouseOutId) return
  try {
    const p: any = { warehouseId: form.warehouseOutId, productId: row.productId, pageSize: 500 }
    if (row.outQualityType) p.qualityType = row.outQualityType
    const res: any = await request.get('/warehouse/stock/page', { params: p })
    const arr: any[] = res?.records || []
    const stock = arr
      .filter((x: any) => !x.stockForm || x.stockForm === 'MATERIAL')
      .reduce((s: number, x: any) => s + (Number(x.quantity) || 0), 0)
    row.stock = stock
    row.quantityLimit = row.sourceQty != null ? Math.min(Number(row.sourceQty), stock) : stock
  } catch { row.stock = undefined; row.quantityLimit = undefined }
}

/**
 * 选中产品后带出 SKU/名称与参考单价（销售侧取标准售价 price），并同步换出侧默认值、刷新库存与上限。
 */
async function onProductChange(val: number, row: any) {
  const m = productOptions.value.find((x: any) => x.id === val)
  if (!m) return
  row.productName = m.name || ''
  row.sku = m.sku || ''
  const price = Number(m.price ?? 0)
  row.unitPrice = price
  if (!row.outUnitPrice) row.outUnitPrice = price
  if (!row.outQuantity) row.outQuantity = row.quantity
  await refreshStock(row)
}

/** 2026-10-01：切换「换出仓」后所有行的库存与上限都失效（库存是按仓取的）⇒ 整表重算。 */
watch(() => form.warehouseOutId, () => { items.value.forEach((it: any) => refreshStock(it)) })

/**
 * 未保存拦截（2026-09-23 统一模板）
 * ⚠️ 必须写在 form / items 等响应式状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form, items: items.value }))

async function submit() {
  // 2026-10-01（第 2 步）：不再要求「来源销售单」—— 未选即为无单换货（前端注释见表单区）。
  // 无来源时下方可换量校验因 maxQuantity 为空而自然跳过；退回/换出能否成立以审核时库存校验为准。
  if (!form.warehouseInId) { ElMessage.warning('请选择换入仓(成品仓)'); return }
  if (!form.warehouseOutId) { ElMessage.warning('请选择换出仓(成品仓)'); return }
  const its = items.value.filter((i) => Number(i.quantity) > 0)
  if (its.length === 0) { ElMessage.warning('请至少录入一条换货明细'); return }
  for (const it of its) {
    // 2026-10-01（用户口径）：上限 = min(来源销售单数量, 换出仓+产品+换出品质的库存数量)；
    // 无来源（无单换货）时上限 = 库存 ⇒ **两种情形都要校验**（原来无来源时完全不校验）。换出数量不受限。
    const lim = (it as any).quantityLimit
    if (lim != null && Number(it.quantity) > lim) {
      const how = (it as any).sourceQty != null ? '取「销售数量」与「库存数量」中的较小值' : '该仓库该品质的库存数量'
      ElMessage.warning(`产品「${it.productName}」退回数量(${it.quantity})超过上限(${lim}，${how})`)
      return
    }
    if (!(Number(it.outQuantity) > 0)) {
      ElMessage.warning(`产品「${it.productName}」换出数量必须大于 0`)
      return
    }
  }
  // 收费校验（2026-09-21 逐产品口径）：**金额填在哪一行就算哪个产品收费**（金额 0 = 不收费）；
  // 去掉批量类型后，填了金额的行必须**自己**选了类型（没有可继承的兜底）——后端会再逐行校验一次
  for (const it of its) {
    if (Number(it.chargeAmount) > 0 && !it.chargeType) {
      ElMessage.warning(`产品「${it.productName || it.productId}」已填收费金额，请选择收费类型`)
      return
    }
  }
  saving.value = true
  try {
    const body = {
      // 无来源换货：一律下发 null（而不是清空下拉可能产生的空串），避免后端 Long 反序列化失败
      saleOrderId: form.saleOrderId || null, saleOrderCode: form.saleOrderCode || null, customerId: form.customerId,
      warehouseInId: form.warehouseInId, warehouseOutId: form.warehouseOutId,
      exchangeDate: form.exchangeDate,
      // 收费：单据级金额由后端按 Σ明细 回写（传 0）；单据级类型不再从界面下发（后端按明细派生）
      chargeFlag: chargeTotal.value > 0 ? 1 : 0,
      chargeType: '',
      chargeAmount: 0,
      chargeReason: form.chargeReason || '',
      remark: form.remark,
      items: its.map((i) => ({
        // 退回侧
        saleOrderItemId: i.saleOrderItemId, productId: i.productId, productName: i.productName,
        quantity: i.quantity, unitPrice: i.unitPrice,
        // 换出侧（只支持同品：产品即退回产品；数量可不等如退2换1）
        outQuantity: i.outQuantity, outUnitPrice: i.outUnitPrice,
        outQualityType: i.outQualityType,
        // 逐产品收费：金额 > 0 才收费，且类型必须是**本行自己**选的（校验见 submit）
        chargeAmount: Number(i.chargeAmount) || 0,
        chargeType: Number(i.chargeAmount) > 0 ? (i.chargeType || '') : '',
        chargeReason: form.chargeReason || '',
        remark: i.remark
      }))
    }
    if (isEdit.value) {
      await updateSaleExchange(form.id!, body)
      ElMessage.success('已保存')
      invalidate('saleExchange')
      // 保存成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本页签
      markClean()
      tabStore.closeTabAndBack(route.path)
      router.push(`/sale/exchange/detail/${form.id}`)
    } else {
      const res: any = await createSaleExchange(body)
      ElMessage.success('已新增')
      invalidate('saleExchange')
      markClean()
      tabStore.closeTabAndBack(route.path)
      router.push('/sale/exchange')
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
  if (isEdit.value) router.push(`/sale/exchange/detail/${form.id}`)
  else router.push('/sale/exchange')
}

onMounted(async () => {
  const id = route.query.id
  if (id !== undefined && id !== '') {
    isEdit.value = true
    await loadEdit(Number(id))
    takeBaseline()
    return
  }
  // 从销售单详情页「换货」跳转：预填来源销售单并自动带入可换明细
  const soId = route.query.saleOrderId
  if (soId !== undefined && soId !== '') {
    await initFromSaleOrder(Number(soId))
  }
  // 初始化/预填完成 ⇒ 建立"未保存"基线（必须在加载之后，否则会把回填误判成用户修改）
  takeBaseline()
})
</script>

<style scoped>
.footer { margin-top: 20px; text-align: right; }
</style>
