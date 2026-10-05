<template>
  <!-- 统一骨架（2026-09-23 全站最终口径）：页头左端=返回 → 标题 → 右端=操作（保存） -->
  <PageShell :title="isEdit ? '编辑销售退货单' : '新增销售退货单'" back-fallback="/sale/return">
    <template #actions>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>

    <el-card shadow="never">
      <!-- label 宽度用 **xl 档**（2026-09-28 全局扫描）：「收费合计（自动）」这类带全角括号的长标签实测需 124px -->
      <el-form :model="form" :rules="rules" ref="formRef" label-width="var(--app-label-width-xl)">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="客户" prop="customerId">
              <RemoteSelect v-model="form.customerId" add-route="/inventory/customer/add" :fetch="fetchCustomers" placeholder="请选择客户" style="width: 100%"
                @change="onCustomerChange" domain="customer" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货仓库" prop="warehouseId">
              <RemoteSelect v-model="form.warehouseId" add-route="/inventory/warehouse" :fetch="fetchWarehouses" label-key="warehouseName" placeholder="请选择仓库" style="width: 100%" domain="warehouse" />
              <div style="font-size: var(--app-font-xs); color: #909399; margin-top: 4px; line-height: 1.4;">提示：销售退货只能退到自有成品仓（退回品按「待整理」品质入库，后续用退货整理单分流）</div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关联销售单">
              <RemoteSelect v-model="form.saleOrderId" :fetch="fetchSaleOrders" label-key="code"
                :placeholder="sourceLocked ? '' : '选填，可追溯原销售单'" style="width: 100%"
                :disabled="!form.customerId || sourceLocked" domain="saleOrder" />
              <!-- 2026-10-01（第 3 步，用户口径「销售单里面的退换货和销售单强关联」）：带 ?fromOrder= 进入时
                   来源锁死，不允许清空退化成无来源退货；只有从子菜单裸进时才可自选/不选。
                   （2026-10-02 统一参数名：进货侧原本就是 fromOrder，这里同步；旧的 saleOrderId 仍兼容。） -->
              <span v-if="sourceLocked" style="font-size: var(--app-font-xs); color: #909399">
                由来源销售单发起，不可更改
              </span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货日期" prop="returnDate">
              <el-date-picker v-model="form.returnDate" type="date" value-format="YYYY-MM-DD"
                placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" style="max-width: 600px" />
        </el-form-item>
        <!-- 收费（2026-09-21 用户口径：**收费精确到产品**；同日再改：**去掉「收费类型（批量）」**）——
             类型一律在明细行按产品各自选（单行金额 > 0 就必须选，校验见 doSubmit）；
             单据级不再手填金额（= Σ明细，后端回写），这里只留 合计（只读）+ 整单说明。
             ⚠️ 方向：**向客户收取** ⇒ 审核生成一条正向应收（单号 -FEE，金额 = Σ明细）。 -->
        <el-row :gutter="16">
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
        <!-- 收费方向（2026-09-21 用户确认）：**向客户收取** ⇒ 审核后生成一条正向**应收**（单号后缀 -FEE）。
             与采购换货的「我方付给供货商」（生成应付）方向相反，界面上写清楚，避免与"付费"混淆。 -->
        <div style="margin: 0 0 12px 100px; font-size: var(--app-font-xs); color: var(--app-text-secondary); line-height: 1.5">
          收费方向：<b style="color: var(--app-color-warning)">向客户收取</b>（我方应收客户）
          <span v-if="chargeTotal > 0"> ⇒ 审核后生成一条应收 <b style="color: var(--app-color-warning)">{{ chargeTotal.toFixed(2) }}</b>（单号后缀 -FEE，可整单核销）</span>
          <span v-else> ⇒ 在明细行逐产品填收费金额，审核时汇总成一条应收</span>
        </div>

        <el-divider content-position="left">退货明细</el-divider>
        <!-- 2026-09-21（UI + 逐产品收费）：原来 9 列 1220px ⇒ 横向滚动 272px。现：
             ① 删掉「SKU」独占列 —— 产品下拉已按 productLabel 显示「SKU | 名称」，信息不丢
             ② 控件 size=small、数量/单价 :controls=false ⇒ 更窄
             ③ 新增「收费」列（金额 + 类型，**逐行可不同**；金额 0 = 该产品不收费）
             ④ 列宽合计 934px < 内容区 948px ⇒ 一行显示完、不左右滑动（有守卫断言） -->
        <el-table :data="form.items" border size="small" max-height="420">
          <el-table-column label="产品" width="176" show-overflow-tooltip>
            <template #default="{ row }">
              <RemoteSelect v-model="row.productId" add-route="/product/add" :fetch="fetchProducts" :label-key="productLabel" placeholder="请选择（可输SKU）" style="width: 100%"
                @change="(id: number) => onProductChange(row, id)" domain="product" />
            </template>
          </el-table-column>
          <el-table-column label="品质等级" width="88" align="center">
            <template #default="{ row }">
              <el-tag :type="ProductQualityTypeTag[row.qualityType] || 'info'">{{ ProductQualityTypeLabel[row.qualityType] || '待整理' }}</el-tag>
            </template>
          </el-table-column>
          <!-- 2026-10-01（用户口径）：原「可退数量」改为「库存数量」= 该**退货仓库** + 该产品 +
               PENDING（待整理）品质的库存（stockForm=MATERIAL）。**仅作展示，不参与上限** ——
               销售退货是入库（客户把货退回来），不消耗我方库存，故数量上限仍为「来源销售单的销售数量」。 -->
          <el-table-column label="库存数量" width="80" align="center">
            <template #default="{ row }">
              <span v-if="row.stock !== undefined" :style="{ color: Number(row.stock) <= 0 ? 'red' : '' }">{{ row.stock }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="退货数量" width="88">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="0" :step="1" size="small" :controls="false"
                :max="row.canReturn !== undefined ? row.canReturn : undefined" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="88">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" :controls="false" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="90" align="right">
            <template #default="{ row }">{{ lineAmount(row) }}</template>
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
          <el-table-column label="备注" min-width="92">
            <template #default="{ row }">
              <el-input v-model="row.remark" size="small" placeholder="选填" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="56" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top: 8px">
          <el-button type="primary" plain :icon="Plus" @click="addItem">添加明细</el-button>
          <el-button type="success" plain :icon="Download" :disabled="!form.saleOrderId" @click="loadFromSaleOrder">从销售单带入明细</el-button>
          <span style="margin-left: 16px">合计金额：<b>{{ totalAmount }}</b></span>
        </div>
      </el-form>
    </el-card>
  </PageShell>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Download } from '@element-plus/icons-vue'
import request from '@/utils/request'
// F7-271（2026-10-04 审核批 2）：库存取数收口到单一实现（口径见 utils/stock.ts）
import { fetchStockQty } from '@/utils/stock'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'
import { ProductQualityTypeLabel, ProductQualityTypeTag, WarehouseType, SALE_RETURN_DIRTY_KEY,
  SaleReturnChargeType, SaleReturnChargeTypeLabel } from '@/api/enums'
import { productLabel } from '@/api/product'
import {
  getSaleReturn,
  getSaleReturnItems,
  createSaleReturn,
  updateSaleReturn,
  getSaleReturnSaleOrders,
  getSaleReturnSaleOrderItems,
  getSaleReturnSourceOrder,
} from '@/api/sale'
import { invalidate } from '@/utils/dataFreshness'

const route = useRoute()
const router = useRouter()
const tabStore = useTabStore()
const formRef = ref()
const saving = ref(false)
const isEdit = ref(false)

/**
 * 由来源销售单发起（`?fromOrder=` 进入）⇒ 来源锁定不可改（2026-10-01 第 3 步，用户口径
 * 「销售单里面的退换货和销售单强关联」）。从子菜单裸进时才允许自选/不选。
 *
 * 2026-10-02（用户口径「统一跳转参数名」）：来源单参数统一为 **fromOrder**（与进货侧一致）；
 * 旧链接/旧书签仍可能带 `saleOrderId` ⇒ 两者都认，故这里读 fromOrderQuery 而不是直接读 route.query。
 */
const fromOrderQuery = computed<string | undefined>(() => {
  const v = route.query.fromOrder ?? route.query.saleOrderId
  return v == null ? undefined : String(v)
})
const sourceLocked = computed(() => !!fromOrderQuery.value)

// Odoo 风格：下拉框展开/搜索时实时查库（不预缓存全量）
const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
// 退货入库仓：自有**成品仓**（2026-09-16 方案 A：原"售后仓"取消，退回品直接入成品仓、品质 PENDING 待整理）
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseType: WarehouseType.FINISHED } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 500, keyword: kw } })
// 2026-09-20（F7-145）：原写法声明了 kw 却完全没用 ⇒ "关联销售单"下拉**看起来可搜索、实际搜索无效**
//（只剩客户维度过滤）。接口必须按客户查（防跨客户挂单），单号搜索在本地过滤
//（与 sale/exchange/add.vue 的做法一致）。接口返回 R<List<Map>>，拦截器已解包为数组。
const fetchSaleOrders = async (kw: string) => {
  const rows: any = await request.get<any, any>('/sale/return/sale-orders', { params: { pageSize: 200, customerId: form.customerId } })
  const list: any[] = Array.isArray(rows) ? rows : []
  return kw ? list.filter((r: any) => (r.code || '').includes(kw)) : list
}

// 列表/拼装用的本地轻量列表（组件内维护，不再依赖全局 optionsStore）
// 2026-09-20（F7-146②）：删除 warehouses / loadWarehouses —— 拉回后**未被使用**
// （仓库下拉走 RemoteSelect 的 fetchWarehouses 实时查库）；customers / products 仍被
// onCustomerChange / onProductChange 用于回填名称，**保留**（报告原述"C 两 ref 均未使用"与实际不符）。
const customers = ref<{ id: number; name: string }[]>([])
const products = ref<{ id: number; name: string; sku?: string }[]>([])
async function loadCustomers() { try { const r: any = await fetchCustomers(''); customers.value = r?.records || [] } catch { customers.value = [] } }
async function loadProducts() { try { const r: any = await fetchProducts(''); products.value = r?.records || [] } catch { products.value = [] } }

const form = reactive({
  id: undefined as number | undefined,
  customerId: undefined as number | undefined,
  customerName: '',
  warehouseId: undefined as number | undefined,
  saleOrderId: undefined as number | undefined,
  saleOrderCode: '',
  returnDate: '',
  chargeFlag: 0,
  chargeType: '' as string,
  chargeAmount: 0,
  chargeReason: '',
  remark: '',
  items: [] as any[],
})

// 2026-10-02（用户口径）：销售**退货**单的收费类型只保留「盖板划伤」「其他」两项
// （此前借用销售换货的 ExchangeChargeType ⇒ 服务费/品质差价/全额货值/其他，与退单场景不符）
const chargeTypeOptions = computed(() =>
  Object.values(SaleReturnChargeType).map((v) => ({
    value: v, label: SaleReturnChargeTypeLabel[v] || v
  }))
)
/**
 * 收费合计 = Σ 明细行收费（2026-09-21 逐产品口径）。
 * <p>单据级 charge_amount 不再是"手填一个总数"，而是本合计 —— 保存时后端也会按明细 Σ 回写一次，
 * 两边口径一致（前端只用于展示与提前校验）。</p>
 */
const chargeTotal = computed(() =>
  form.items.reduce((s: number, it: any) => s + (Number(it.chargeAmount) || 0), 0))

const rules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择退货仓库', trigger: 'change' }],
  returnDate: [{ required: true, message: '请选择退货日期', trigger: 'change' }],
}

const totalAmount = computed(() => {
  const sum = (form.items || []).reduce((acc, it) => acc + (Number(it.quantity) || 0) * (Number(it.unitPrice) || 0), 0)
  return sum.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
})

function lineAmount(row: any) {
  const v = (Number(row.quantity) || 0) * (Number(row.unitPrice) || 0)
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function addItem() {
  form.items.push({ productId: undefined, quantity: 1, unitPrice: 0, remark: '', qualityType: 'PENDING' })
}
function removeItem(i: number) {
  form.items.splice(i, 1)
}

function onCustomerChange(id: number) {
  const c = customers.value.find((x) => x.id === id)
  form.customerName = c ? c.name : ''
  // 客户变更后，原销售单与明细失效，清空
  form.saleOrderId = undefined
  form.saleOrderCode = ''
  form.items = []
  addItem()
}

/**
 * 从销售单带入明细。
 * 2026-10-01（用户口径）：后端 canReturn = **该销售明细的销售数量**，本页把它作为**退货数量上限**
 * （销售退货是入库、不消耗我方库存，故**不参与"与库存取小"**）；列「库存数量」仅作展示。
 */
async function loadFromSaleOrder() {
  if (!form.saleOrderId) { ElMessage.warning('请先选择销售单'); return }
  try {
    const rows = await getSaleReturnSaleOrderItems(form.saleOrderId)
    form.items = (rows || []).map((r) => ({
      productId: r.productId,
      productName: r.productName,
      saleOrderItemId: r.saleOrderItemId,
      // = 该销售明细的销售数量：本页作为退货数量上限（不与该仓库存取小）
      canReturn: Number(r.canReturn),
      quantity: 0,
      unitPrice: Number(r.unitPrice || 0),
      // 逐产品收费（2026-09-21）：默认不收费，金额逐行填
      chargeAmount: 0,
      chargeType: '',
      remark: '',
      qualityType: 'PENDING',
    }))
    if (!form.items.length) ElMessage.info('该销售单暂无明细')
    // 补齐「库存数量」展示（依赖退货仓库 + PENDING 品质）
    await Promise.all(form.items.map((it: any) => refreshStock(it)))
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '加载销售单明细失败')
  }
}
/**
 * 刷新该行的「库存数量」展示（用户口径 2026-10-01；F7-271 收口到 utils/stock.ts 单一实现）。
 * 销售退货是**入库**（客户退回），不消耗我方库存 ⇒ 库存**仅作展示**，数量上限仍是
 * 「来源销售单的销售数量 − 历史已退」(row.canReturn，见列 :max)。本口径已与用户确认。
 * 品质取**明细品质**（与后端审核入库同源：`it.getQualityType()` 缺省 PENDING）——
 * 原先硬编码 PENDING，若该行品质不是 PENDING，页面显示的就不是货实际会被加进去的那个品质。
 */
async function refreshStock(row: any) {
  row.stock = await fetchStockQty(form.warehouseId, row.productId, row.qualityType || 'PENDING')
}

async function onProductChange(row: any, id: number) {
  const p = products.value.find((x) => x.id === id)
  row.productName = p ? p.name : ''
  row.sku = p ? (p.sku || '') : ''
  await refreshStock(row)
}

/** 2026-10-01：切换「退货仓库」后库存展示失效（库存是按仓取的）⇒ 整表重算。 */
watch(() => form.warehouseId, () => { form.items.forEach((it: any) => refreshStock(it)) })

/**
 * 从销售单详情「退货」按钮跳转过来时（?fromOrder=xxx；旧的 ?saleOrderId= 仍兼容）：
 * 反查销售单带出客户与单号，并自动载入可退明细，省去手工选择与录入。
 */
async function initFromSaleOrder(saleOrderId: number) {
  try {
    // 期 3（2026-09-19 读隔离）：来源销售单头改走退货页自身前缀（原读 /inventory/sale/{id} 需 sale:order）
    const so: any = await getSaleReturnSourceOrder(saleOrderId)
    if (!so) { ElMessage.warning('来源销售单不存在'); return }
    form.saleOrderId = so.id
    form.saleOrderCode = so.code || ''
    form.customerId = so.customerId
    // 退货入自有成品仓（与销售单出库仓可能相同），但不预填，由用户选择
    await loadFromSaleOrder()
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '加载来源销售单失败')
  }
}

async function loadEdit(id: number) {
  const head = await getSaleReturn(id)
  Object.assign(form, {
    id: head.id,
    customerId: head.customerId,
    customerName: head.customerName,
    warehouseId: head.warehouseId,
    saleOrderId: head.saleOrderId,
    saleOrderCode: head.saleOrderCode,
    returnDate: head.returnDate,
    chargeFlag: Number(head.chargeFlag || 0),
    chargeAmount: Number(head.chargeAmount || 0),
    chargeReason: head.chargeReason || '',
    remark: head.remark,
  })
  const items = await getSaleReturnItems(id)
  form.items = (items || []).map((it) => ({
    productId: it.productId,
    productName: it.productName,
    saleOrderItemId: it.saleOrderItemId,
    quantity: it.quantity,
    unitPrice: it.unitPrice,
    // 逐产品收费（2026-09-21）：明细接口已回传逐行收费字段
    chargeAmount: Number(it.chargeAmount || 0),
    chargeType: it.chargeType || '',
    remark: it.remark,
    qualityType: it.qualityType,
  }))
  // 2026-09-21（去掉批量类型后）：旧草稿里"靠单据级类型兜底"的行要**显性化**到行上 ——
  // 单据级类型是后端按"各明细类型一致"派生的，落回缺类型的行是安全且无损的；
  // 不做这一步，编辑这类旧单会因为新的逐行校验而存不回去。
  const docChargeType = head.chargeType || ''
  if (docChargeType) form.items.forEach((it: any) => { if (!it.chargeType) it.chargeType = docChargeType })
  // 若关联了销售单，回填可退数量（用于数量上限提示）
  if (form.saleOrderId) {
    try {
      const rows = await getSaleReturnSaleOrderItems(form.saleOrderId)
      const map = Object.fromEntries((rows || []).map((r) => [r.saleOrderItemId, Number(r.canReturn)]))
      form.items.forEach((it) => { it.canReturn = it.saleOrderItemId != null ? map[it.saleOrderItemId] : undefined })
    } catch { /* 忽略 */ }
  }
}

function buildPayload() {
  return {
    customerId: form.customerId,
    warehouseId: form.warehouseId,
    saleOrderId: form.saleOrderId,
    saleOrderCode: form.saleOrderCode,
    returnDate: form.returnDate,
    // 收费（2026-09-21 逐产品口径）：单据级金额由后端按 Σ 明细回写（这里传 0）；
    // 单据级类型**不再从界面下发**（后端按明细派生：各明细类型一致=该类型，否则空 ⇒ 详情显示"多类型"）
    chargeFlag: chargeTotal.value > 0 ? 1 : 0,
    chargeType: '',
    chargeAmount: 0,
    chargeReason: form.chargeReason || '',
    remark: form.remark,
    items: form.items.map((it) => ({
      productId: it.productId,
      saleOrderItemId: it.saleOrderItemId,
      qualityType: it.qualityType || 'PENDING',
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      amount: (Number(it.quantity) || 0) * (Number(it.unitPrice) || 0),
      // 逐产品收费：金额 > 0 才收费，且类型必须是**本行自己**选的（校验见 doSubmit）
      chargeAmount: Number(it.chargeAmount) || 0,
      chargeType: Number(it.chargeAmount) > 0 ? (it.chargeType || '') : '',
      chargeReason: form.chargeReason || '',
      remark: it.remark,
    })),
  }
}

/**
 * 未保存拦截（2026-09-23 统一模板）：本页明细挂在 form.items 上，故快照只需 form。
 * ⚠️ 必须写在 form 等响应式状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form }))

async function submit() {
  // 2026-09-20（F7-147）：Element Plus 的 validate() **不传回调时校验失败会 reject**，原先未包 try/catch
  // ⇒ 每次"表单不完整就点保存"都会产生未处理的 Promise rejection（校验提示其实已标在表单上，这里只需静默返回）。
  try {
    await formRef.value.validate()
  } catch { return }
  if (!form.items.length) {
    ElMessage.warning('请至少添加一条退货明细')
    return
  }
  // 关联销售单时：数量不能超过可退数量
  if (form.saleOrderId) {
    for (const it of form.items) {
      if (it.saleOrderItemId != null && it.canReturn !== undefined && (Number(it.quantity) || 0) > Number(it.canReturn)) {
        ElMessage.warning(`产品「${it.productName || it.productId}」退货数量不能超过可退数量 ${it.canReturn}`)
        return
      }
    }
  }
  // 收费校验（2026-09-21 逐产品口径）：**金额填在哪一行就算哪个产品收费**（金额 0 = 不收费）；
  // 去掉批量类型后，填了金额的行必须**自己**选了类型（没有可继承的兜底）——后端会再逐行校验一次
  for (const it of form.items) {
    if (Number(it.chargeAmount) > 0 && !it.chargeType) {
      ElMessage.warning(`产品「${it.productName || it.productId}」已填收费金额，请选择收费类型`)
      return
    }
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value) {
      await updateSaleReturn(form.id!, payload)
      ElMessage.success('已保存'); invalidate('saleReturn')
    } else {
      await createSaleReturn(payload)
      ElMessage.success('已新增'); invalidate('saleReturn')
    }
    // 保存成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本页签回列表
    markClean()
    tabStore.closeTabAndBack(route.path)
    router.push('/sale/return')
  } finally {
    saving.value = false
  }
}

function goBack() {
  // 取消返回：先清脏标记（否则离开会被未保存确认拦住），再关掉本页签
  markClean()
  tabStore.closeTabAndBack(route.path)
  router.push('/sale/return')
}

onMounted(async () => {
  loadCustomers()
  loadProducts()
  const id = route.query.id
  if (id) {
    isEdit.value = true
    await loadEdit(Number(id))
    takeBaseline()
    return
  }
  form.returnDate = localDate()
  // 从销售单详情页「退货」跳转：预填来源销售单并自动带入可退明细
  // 2026-10-02（统一跳转参数名）：来源单统一 fromOrder，兼容旧链接的 saleOrderId
  const soId = fromOrderQuery.value
  if (soId !== undefined && soId !== '') {
    await initFromSaleOrder(Number(soId))
  }
  if (!form.items.length) addItem()
  // 初始化/预填完成 ⇒ 建立"未保存"基线（必须在加载之后，否则会把回填误判成用户修改）
  takeBaseline()
})
</script>

<style scoped>
/* 页头/底部操作条已统一到全局骨架（PageShell + styles/page.css） */
</style>
