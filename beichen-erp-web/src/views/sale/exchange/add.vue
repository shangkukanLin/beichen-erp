<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <span>{{ isEdit ? '编辑销售换货单' : '新增销售换货单' }}</span>
      </template>
      <!-- 2026-09-20（F7-147）：本页没有 rules，校验靠 submit 里的逐项 if ⇒ 不再挂无用的 formRef -->
      <el-form :model="form" label-width="100px">
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
            <el-form-item label="收费类型（批量）">
              <el-select v-model="form.chargeType" placeholder="选后点「套用全部」" clearable style="width:100%">
                <el-option v-for="o in chargeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="收费合计（自动）">
              <span style="font-weight:600;color:#e6a23c">{{ chargeTotal.toFixed(2) }}</span>
              <span style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">= Σ 明细行收费</span>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="收费说明（批量）">
              <el-input v-model="form.chargeReason" placeholder="选填，逐行未填时套用" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label=" ">
              <el-button plain :disabled="!form.chargeType" @click="applyChargeTypeToAll">套用到全部明细</el-button>
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
      <!-- 2026-09-21（UI + 逐产品收费）：原来 10 列 1160px ⇒ 横向滚动 212px。现：
           ① 删「SKU」独占列 —— 退回产品列直接显示「SKU | 名称」（后端 saleOrderItems 已回 sku）
           ② 控件 size=small、数量/单价 :controls=false ⇒ 更窄
           ③ 新增「收费」列（金额 + 类型，**逐产品**；金额 0 = 不收费）
           ④ 列宽合计 914px < 内容区 948px ⇒ 一行显示完、不左右滑动（有守卫断言） -->
      <el-table :data="items" border size="small" max-height="380">
        <!-- ===== 退回侧：客户退回，入成品仓（品质待分类 PENDING）待整理 ===== -->
        <el-table-column label="退回（客户退回，入成品仓待分类）" align="center">
          <el-table-column label="退回产品" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.sku ? row.sku + ' | ' + row.productName : row.productName }}</template>
          </el-table-column>
          <el-table-column label="可换数量" width="64" align="right">
            <template #default="{ row }">{{ row.maxQuantity ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="退回数量" width="78">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="0" :step="1" size="small" :controls="false" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column prop="unitPrice" label="原单价" width="78" align="right" />
        </el-table-column>

        <!-- ===== 换出侧：发给客户，从成品仓扣减；只支持同品，产品固定为退回产品 ===== -->
        <el-table-column label="换出（同品换货，从成品仓扣减）" align="center">
          <el-table-column label="换出数量" width="78">
            <template #default="{ row }">
              <el-input-number v-model="row.outQuantity" :min="0" :precision="0" :step="1" size="small" :controls="false" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="换出品质" width="78">
            <template #default="{ row }">
              <el-select v-model="row.outQualityType" size="small" style="width:100%">
                <el-option v-for="o in qualityOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="换出单价" width="82">
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

        <el-table-column label="备注" min-width="88">
          <template #default="{ row }"><el-input v-model="row.remark" size="small" /></template>
        </el-table-column>
        <el-table-column label="操作" width="52" align="center">
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
/**
 * 收费合计 = Σ 明细行收费（2026-09-21 逐产品口径）。
 * <p>单据级 charge_amount 不再是"手填一个总数"，而是本合计 —— 保存时后端也会按 Σ明细 回写一次。</p>
 */
const chargeTotal = computed(() =>
  items.value.reduce((s: number, it: any) => s + (Number(it.chargeAmount) || 0), 0))
/** 把批量类型套用到全部明细行（逐行仍可单独改；金额逐行填 —— 金额才是"收多少"） */
function applyChargeTypeToAll() {
  if (!form.chargeType) return
  items.value.forEach((it: any) => {
    if (!(Number(it.chargeAmount) > 0)) it.chargeAmount = 0
    it.chargeType = form.chargeType
  })
  ElMessage.success(`已把「${ExchangeChargeTypeLabel[form.chargeType] || form.chargeType}」套用到 ${items.value.length} 条明细`)
}
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
      // SKU：明细表把「SKU | 名称」并到一列（2026-09-21 UI），后端已随可换明细一并返回
      sku: r.sku || '',
      quantity: qty,
      maxQuantity: qty,
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
    outUnitPrice: Number(it.outUnitPrice ?? it.unitPrice ?? 0),
    // 逐产品收费（2026-09-21）：明细接口已回传逐行收费字段
    chargeAmount: Number(it.chargeAmount || 0),
    chargeType: it.chargeType || ''
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
  // 收费校验（2026-09-21 逐产品口径）：**金额填在哪一行就算哪个产品收费**（金额 0 = 不收费）；
  // 填了金额的行必须能确定类型（行类型或批量类型）——后端会再逐行校验一次
  for (const it of its) {
    if (Number(it.chargeAmount) > 0 && !(it.chargeType || form.chargeType)) {
      ElMessage.warning(`产品「${it.productName || it.productId}」已填收费金额，请选择收费类型（可用上方「收费类型（批量）」套用）`)
      return
    }
  }
  saving.value = true
  try {
    const body = {
      saleOrderId: form.saleOrderId, saleOrderCode: form.saleOrderCode, customerId: form.customerId,
      warehouseInId: form.warehouseInId, warehouseOutId: form.warehouseOutId,
      exchangeDate: form.exchangeDate,
      // 收费：单据级只作「批量默认」外带；金额由后端按 Σ明细 回写（这里传 0）
      chargeFlag: chargeTotal.value > 0 ? 1 : 0,
      chargeType: form.chargeType || '',
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
        // 逐产品收费：金额 > 0 才收费；类型缺省套用批量类型
        chargeAmount: Number(i.chargeAmount) || 0,
        chargeType: Number(i.chargeAmount) > 0 ? (i.chargeType || form.chargeType || '') : '',
        chargeReason: form.chargeReason || '',
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
