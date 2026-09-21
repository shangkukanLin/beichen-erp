<script setup lang="ts">
/**
 * 成品收货（委外加工 → 成品收货）
 * <p>只看**正在加工**（PRODUCING）的加工单 —— 与后端「只有生产中的加工单可录入收货」口径一致。
 * 行内「收货」直接进入该单收货详细页并自动弹出新增收货弹窗；「收货详细」只看记录。</p>
 * <p>2026-09-21（用户口径）：页面下方新增「**无单加工退货**」区块 —— 与「加工单收货详细页 → 加工退货」
 * 是**同一个动作**（红冲收货：写一条负数 DEFECT_RETURN 记录、审核时扣成品 + BOM 料还回工厂委外仓 +
 * 冲减应付），唯一区别是**不关联加工单**（改由所选加工厂定位委外仓与应付对象，
 * 还料按该产品的最新 BOM 快照，冲减额按还回物料的 FIFO 价值）。
 * 该入口替代了原先「加工退货页 → 新增不良退货」那条独立单据路线（其本意就是"可以不关联加工单"）。</p>
 */
import { reactive, ref, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { OutsourceOrderStatusLabel, OutsourceOrderStatusTag } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import RemoteSelect from '@/components/RemoteSelect.vue'

defineOptions({ name: 'OutsourceOrderDelivery' })

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const query = reactive({ code: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/order-delivery/order-page', {
      params: { page: pagination.pageNum, size: pagination.pageSize, code: query.code || undefined }
    })
    tableData.value = r?.records || []
    pagination.total = Number(r?.total || 0)
  } catch (e: any) {
    ElMessage.error('加载待收货订单失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { loading.value = false }
}
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; handleQuery() }

/** 收货进度（口径与后端 summary 一致：只算已审核收货） */
function progressOf(row: any) {
  const total = Number(row.totalQuantity || 0)
  if (!total) return 0
  return Math.min(100, Math.round(Number(row.deliveredQuantity || 0) / total * 100))
}
/**
 * 进入收货详细页：带 add=1 时自动打开新增收货弹窗，一步完成收货。
 * 追加时间戳是为了让每次点击都是「新的 fullPath」——layout 的 keep-alive 以 fullPath 为 key，
 * 否则复用缓存实例会导致弹窗不再自动弹出。
 */
function goDelivery(row: any) { router.push(`/outsource/order/delivery/${row.id}?add=1&_t=${Date.now()}`) }
function goDetail(row: any) { router.push(`/outsource/order/delivery/${row.id}`) }

// ==================== 无单加工退货（2026-09-21 用户口径） ====================
// 与「加工单收货详细页 → 加工退货」同一个动作（红冲收货），唯一区别是**不关联加工单**：
// 没有加工单可以挂（或不想挂）时在这里登记。草稿不影响库存/应付，审核才落账，可反审核、草稿可删除。
const noOrderRows = ref<any[]>([])
const noOrderLoading = ref(false)
const noOrderVisible = ref(false)
const noOrderSaving = ref(false)
/** 加工退货规格：与收货详细页的加工退货弹窗同一口径（A/B/C/不良） */
const NO_ORDER_SPECS = [
  { value: 'A', label: 'A规' }, { value: 'B', label: 'B规' },
  { value: 'C', label: 'C规' }, { value: 'DEFECT', label: '不良' }
]
const noOrderForm = reactive({
  factoryId: undefined as any, warehouseId: undefined as any, productMasterId: undefined as any,
  qualityType: 'A' as string, quantity: '' as any, remark: ''
})
/** 加工厂（= 应付对象与委外仓的归属） */
const fetchFactories = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
/** 加工退货仓库：我方（自有）成品仓 —— 与收货详细页的加工退货弹窗同口径 */
const fetchFinishedWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED' } })
/** 产品主数据（无单时没有加工单产品行可选） */
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 500, keyword: kw } })

function specText(q?: string) { return q === 'DEFECT' ? '不良' : (q ? q + '规' : '-') }

async function loadNoOrder() {
  noOrderLoading.value = true
  try {
    noOrderRows.value = (await request.get<any, any>('/outsource/order-delivery/return-defect-no-order/list')) || []
  } catch (e: any) {
    ElMessage.error('加载无单加工退货失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { noOrderLoading.value = false }
}

function openNoOrder() {
  Object.assign(noOrderForm, {
    factoryId: undefined, warehouseId: undefined, productMasterId: undefined,
    qualityType: 'A', quantity: '', remark: ''
  })
  noOrderVisible.value = true
}

async function submitNoOrder() {
  if (!noOrderForm.factoryId) { ElMessage.warning('请选择加工厂'); return }
  if (!noOrderForm.warehouseId) { ElMessage.warning('请选择加工退货仓库'); return }
  if (!noOrderForm.productMasterId) { ElMessage.warning('请选择产品'); return }
  const qty = Math.round(Number(noOrderForm.quantity) || 0)
  if (!(qty > 0)) { ElMessage.warning('请输入加工退货数量'); return }
  noOrderSaving.value = true
  try {
    await request.post('/outsource/order-delivery/return-defect-no-order', {
      factoryId: noOrderForm.factoryId, warehouseId: noOrderForm.warehouseId,
      productMasterId: noOrderForm.productMasterId, qualityType: noOrderForm.qualityType,
      quantity: qty, remark: noOrderForm.remark
    })
    ElMessage.success('加工退货草稿已保存，请在下方列表审核')
    noOrderVisible.value = false
    await loadNoOrder()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { noOrderSaving.value = false }
}

async function auditNoOrder(row: any) {
  try {
    await ElMessageBox.confirm('确定审核该加工退货吗？审核后将扣减成品库存、把 BOM 料还回工厂委外仓并冲减应付。', '审核', { type: 'warning' })
  } catch { return }
  try { await request.put(`/outsource/order-delivery/${row.id}/audit`); ElMessage.success('已审核'); await loadNoOrder() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
async function unauditNoOrder(row: any) {
  try {
    await ElMessageBox.confirm('确定反审核吗？将回滚成品库存、扣回已还的料并冲回应付，回到草稿。', '反审核', { type: 'warning' })
  } catch { return }
  try { await request.put(`/outsource/order-delivery/${row.id}/un-audit`); ElMessage.success('已反审核'); await loadNoOrder() }
  catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
async function deleteNoOrder(row: any) {
  try { await ElMessageBox.confirm('确定删除该加工退货草稿吗？', '删除', { type: 'warning' }) } catch { return }
  try { await request.delete(`/outsource/order-delivery/${row.id}`); ElMessage.success('已删除'); await loadNoOrder() }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

onActivated(() => { loadData(); loadNoOrder() })
</script>

<template>
  <div>
    <el-card shadow="never">
      <template #header><div style="display:flex;justify-content:space-between;align-items:center"><span style="font-weight:600">成品收货（正在加工的加工单）</span></div></template>
      <el-form :inline="true" :model="query" style="margin-bottom:12px">
        <el-form-item label="单号"><el-input v-model="query.code" placeholder="加工单号" clearable style="width:200px" @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <!-- 表格/按钮/标签一律不设 size="small"：与「加工订单」等列表页保持同一字号（2026-09-16）。
           列宽合计 ≈942px（**留 20px 余量**）＜ 内容区，保证「一行显示完、不横向滑动」。
           注意：内容区宽度不是固定的 —— 本页行数多、页面变高时会出现**纵向滚动条**，
           内容区会再少约 15px（实测 963 → 948），所以不能贴着 963 排满。
           2026-09-17：①按用户要求「下单/已收/剩余」+40（114→154）、「收货进度」+20（80→100），
           由弹性列「产品」min-width 等量减回（100→77）；②新增行内「退货」按钮（良品退回加工厂 →
           加工退货单），操作列 84→138，由「加工单号 −12、下单/已收/剩余 −10、收货进度 −8、
           最近收货 −8、计划完成 −8、状态 −8」抵平。
           2026-09-21（用户口径「成品收货页只留加工退货、退回走红冲收货」）：**移除**行内「退货」按钮 ⇒
           操作列 124→84；腾出的 40px **自动归弹性列「产品」**（无需手工抵平，合计仍 ≤ 容器 ⇒ 依旧不横向滑动）。 -->
      <el-table :data="tableData" border stripe v-loading="loading" style="width:100%" @row-click="goDetail">
        <el-table-column label="加工单号" width="140" show-overflow-tooltip>
          <template #default="{ row }"><el-button type="primary" link @click.stop="goDetail(row)">{{ row.code }}</el-button></template>
        </el-table-column>
        <el-table-column prop="factoryName" label="加工厂" min-width="90" show-overflow-tooltip />
        <el-table-column prop="productNames" label="产品" min-width="70" show-overflow-tooltip />
        <el-table-column label="下单/已收/剩余" width="144" align="center">
          <template #default="{ row }">
            <span>{{ row.totalQuantity }}</span>
            <span style="color:var(--app-text-placeholder)"> / </span>
            <span style="color:var(--app-color-success);font-weight:500">{{ row.deliveredQuantity }}</span>
            <span style="color:var(--app-text-placeholder)"> / </span>
            <span :style="{ color: Number(row.remainingQuantity) <= 0 ? 'var(--app-color-success)' : 'var(--app-color-warning)', fontWeight: 500 }">{{ row.remainingQuantity }}</span>
          </template>
        </el-table-column>
        <el-table-column label="收货进度" width="92">
          <template #default="{ row }"><el-progress :percentage="progressOf(row)" :stroke-width="10" :color="progressOf(row) >= 100 ? 'var(--app-color-success)' : 'var(--app-color-primary)'" /></template>
        </el-table-column>
        <el-table-column label="最近收货" width="98">
          <template #default="{ row }">{{ $fmtDate(row.latestDeliveryDate) }}</template>
        </el-table-column>
        <el-table-column label="计划完成" width="98">
          <template #default="{ row }">{{ $fmtDate(row.planEndDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="82" align="center">
          <template #default="{ row }"><el-tag :type="OutsourceOrderStatusTag[row.status] || 'info'" size="small">{{ OutsourceOrderStatusLabel[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="84" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDelivery(row)">收货</el-button>
            <!-- 2026-09-21（用户口径）：退回进详情页做「加工退货」红冲收货，原「退货」按钮已移除 -->
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery" />
      </div>
    </el-card>

    <!-- 无单加工退货（2026-09-21 用户口径）：与「加工单 → 加工退货」同一个动作（红冲收货），
         唯一区别是**不关联加工单** —— 所以列表在这里单独一块，不与某张加工单的收货记录混排。 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">无单加工退货（不关联加工单 · 红冲收货）</span>
          <el-button type="danger" size="small" @click="openNoOrder">新增无单加工退货</el-button>
        </div>
      </template>
      <p style="margin:0 0 10px;color:var(--app-text-secondary);font-size:var(--app-font-xs);line-height:1.5">
        退回某批已收的成品，但**不挂加工单**（工序不明/单据已结/无需挂单）时在此登记：审核后同样
        <b>扣减所选规格的成品库存</b>、把 <b>BOM 料还回所选加工厂的委外仓</b>，并按 <b>还回料的 FIFO 价值冲减应付</b>；
        有关联加工单的退回请到该单的收货详细页用「加工退货」。
      </p>
      <el-table :data="noOrderRows" border stripe size="small" v-loading="noOrderLoading">
        <el-table-column label="日期" width="100"><template #default="{ row }">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
        <el-table-column prop="factoryName" label="加工厂" min-width="110" show-overflow-tooltip />
        <el-table-column prop="productName" label="产品" min-width="130" show-overflow-tooltip />
        <el-table-column label="规格" width="70" align="center"><template #default="{ row }">{{ specText(row.qualityType) }}</template></el-table-column>
        <el-table-column label="退货数量" width="90" align="right">
          <template #default="{ row }"><span style="color:var(--app-color-danger);font-weight:500">{{ Math.abs(Number(row.quantity || 0)) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button type="success" link size="small" v-if="row.status === DocStatus.DRAFT" @click="auditNoOrder(row)">审核</el-button>
            <el-button type="warning" link size="small" v-if="row.status === DocStatus.AUDITED" @click="unauditNoOrder(row)">反审核</el-button>
            <el-button type="danger" link size="small" v-if="row.status === DocStatus.DRAFT" @click="deleteNoOrder(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增无单加工退货弹窗 -->
    <el-dialog v-model="noOrderVisible" title="新增无单加工退货" width="560px" :close-on-click-modal="false">
      <el-form :model="noOrderForm" label-width="104px" size="small">
        <el-form-item required label="加工厂">
          <RemoteSelect v-model="noOrderForm.factoryId" :fetch="fetchFactories" :label-key="(row:any)=>row.name" style="width:100%" placeholder="加工厂（还料/应付对象）" />
        </el-form-item>
        <el-form-item required label="加工退货仓库">
          <RemoteSelect v-model="noOrderForm.warehouseId" :fetch="fetchFinishedWarehouses" :label-key="(row:any)=>`${row.warehouseName} (${row.code})`" style="width:100%" placeholder="选择扣减的成品仓库" />
        </el-form-item>
        <el-form-item required label="产品">
          <RemoteSelect v-model="noOrderForm.productMasterId" :fetch="fetchProducts" :label-key="(row:any)=>row.name" style="width:100%" placeholder="选择产品" />
        </el-form-item>
        <el-form-item required label="退货规格">
          <el-select v-model="noOrderForm.qualityType" style="width:100%">
            <el-option v-for="o in NO_ORDER_SPECS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item required label="退货数量">
          <el-input v-model="noOrderForm.quantity" type="number" placeholder="整数" @change="noOrderForm.quantity = Math.round(Number(noOrderForm.quantity) || 0)" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="noOrderForm.remark" placeholder="选填，如退回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="noOrderVisible = false">取消</el-button>
        <el-button type="primary" :loading="noOrderSaving" @click="submitNoOrder">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>
