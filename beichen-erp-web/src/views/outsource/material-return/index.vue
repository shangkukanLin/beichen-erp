<script setup lang="ts">
/**
 * 委外物料退货（页面 = 物料退货；两个页签 = 物料退货 / 维修退货）
 *
 * <p>2026-09-21（用户口径「加工退货页面和物料退货的 UI 需要优化和统一，按 A+B+C+D 做」）：
 * 本页与「加工退货」页（`outsource/return-order`）**对齐成同一套列表页家规** ——
 * ①**一页一张卡片**：页签 → 筛选行 → 表格 → 分页（原先「筛选卡片 + 表格卡片」两张卡片）；
 * ②**筛选行统一**：单号 / 供应商 / 状态 /（维修页签）返回进度 + [查询][重置]，**新增按钮靠右且随页签切换**
 *   （原先两个新增按钮常驻，不随页签）；
 * ③**列宽瘦身、消除横向滚动**（原先两页签都横向滚动：实测 1055px / 1265px ＞ 内容区 948px）；
 * ④**术语统一**：页签①「退货退款」→「**物料退货**」（与菜单/页面同名，与加工侧「加工退货」同模式）、
 *   页签②「维修返还」→「**维修退货**」（与加工侧一致）；列「金额」→「**退货金额**」；
 *   列「退货/送修内容」「送修/已返回」「状态」与加工侧同名；
 * ⑤**返回进度不再单列**（原先「送修/已返回」+「返回进度」两列）：合并且与加工侧一致 ——
 *   「送修/已返回」显示 `sent / returned`（橙=还有未返回、绿=已全部返回，悬停给未返回数），
 *   **已结案直接显示在「状态」列**（加工侧同款做法）；
 * ⑥**动作集与顺序统一**（与加工侧一致）：详情 → 编辑 → 审核 → 反审核 → 作废 → 结案 → 撤销结案；
 *   「编辑」为 D 档新增（复用新增页 `/outsource/material-return/edit/:id`，后端 `PUT /{id}` 早已支持，仅允许草稿）。
 *
 * <p>📏 列宽预算（家规：合计 ≤ 930，纵向滚动条出现时内容区从 963 缩到约 948，故留余量）：
 * 物料退货页签 = 132+100+96+86+96+78+174 = 762 固定 ＋ 内容列 min 136 = **898** ✓；
 * 维修退货页签 = 132+100+86+96+116+78+174 = 782 固定 ＋ 内容列 min 136 = **918** ✓
 * （两页签的公共列**同宽**；维修页签不再单列「出库源仓」——该字段在详情页可查）。</p>
 *
 * <p>📌 详情入口规则（与加工侧同一条家规）：**有独立详情页的单据 → 行点击 / 「详情」跳详情页**；
 * 只有「收货台账」那种没有独立页的记录才用抽屉。</p>
 */
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { DocStatus, DocStatusLabel, DocStatusTag, OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY, MaterialReturnType, MaterialReturnTypeLabel } from '@/api/enums'

defineOptions({ name: 'OutsourceMaterialReturn' })

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const query = reactive({ code: '', supplierId: undefined as any, status: '', progress: '' })
/** 类型页签（2026-09-17）：REFUND 物料退货（退回并冲减应付）/ REPAIR 维修退货（送修，修好登记返回入库） */
const activeType = ref<string>(MaterialReturnType.REFUND)

/**
 * 页签文案（2026-09-21 术语统一）：①与菜单/页面同名（「物料退货」），与加工侧「加工退货 / 维修退货」同模式；
 * ②「维修退货」与加工侧、与单据类型 label 全链一个词（沿革 维修返还 → 定稿 **维修退货**）。
 */
const TAB_LABELS: Record<string, string> = {
  [MaterialReturnType.REFUND]: '物料退货',
  [MaterialReturnType.REPAIR]: '维修退货'
}

/**
 * 退货对象（辅料商/供应商）实时查库。
 * <p>2026-09-21（用户口径）：**物料退货的对方只可能是辅料商或供应商，不会是供货商** ⇒
 * 筛选与表单下拉一律 `excludeSupplierType: 'product'`（与新增页、与后端兜底校验同一口径 ✓）。</p>
 */
const fetchSuppliers = (kw: string) =>
  request.get('/supplier/page', { params: { pageSize: 500, name: kw, excludeSupplierType: 'product' } })

const isRepairTab = () => activeType.value === MaterialReturnType.REPAIR

async function loadData() {
  loading.value = true
  try {
    // progress（维修退货）：PENDING_RETURN 还有未返回 / CLOSED 已结案（2026-09-17）
    const r = await request.get<any, any>('/outsource/material-return/page', {
      params: {
        pageNum: pagination.pageNum, pageSize: pagination.pageSize,
        code: query.code || undefined, supplierId: query.supplierId || undefined,
        status: query.status || undefined, returnType: activeType.value,
        progress: isRepairTab() ? (query.progress || undefined) : undefined
      }
    })
    list.value = r?.records || []; pagination.total = Number(r?.total || 0)
  } catch (e: any) {
    ElMessage.error('加载物料退货失败：' + (e?.msg || e?.message || '未知错误'))
  } finally { loading.value = false }
}
/** 切类型页签：重置到第 1 页再查（返回进度筛选只在维修页签有效，切换时清空） */
function handleTabChange() { pagination.pageNum = 1; query.progress = ''; loadData() }
function handleSearch() { pagination.pageNum = 1; loadData() }
function handleReset() { query.code = ''; query.supplierId = undefined; query.status = ''; query.progress = ''; handleSearch() }

/** 结案（仅维修退货）：全部送修数量已返回（未返回=0）后确认收尾 */
async function handleClose(row: any) {
  try { await ElMessageBox.confirm('确认结案？结案后不能再登记/撤销维修返回，也不能反审核（需先撤销结案）。', '确认结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/close`); ElMessage.success('已结案'); loadData() } catch (e: any) { ElMessage.error(e?.message || '结案失败') }
}
async function handleReOpen(row: any) {
  try { await ElMessageBox.confirm('确认撤销结案？将回到「送修中」跟踪状态，可继续登记维修返回。', '撤销结案', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/re-open`); ElMessage.success('已撤销结案'); loadData() } catch (e: any) { ElMessage.error(e?.message || '撤销失败') }
}

/** 审核提示按类型区分：物料退货冲减应付；维修退货只出库送修（不冲应付） */
async function handleAudit(row: any) {
  const repair = row.returnType === MaterialReturnType.REPAIR
  // 维修退货：关联订单未完成时审核会同时扣减该订单收料数（修好返回自动回补，2026-09-17）
  const tip = repair
    ? ('确认审核该维修退货单？审核后物料出源仓送供应商维修（不冲减应付）' + (row.materialOrderCode ? `；关联订单 ${row.materialOrderCode} 若未完成，将同时扣减其收料数` : ''))
    : '确认审核该退货单？审核后物料出源仓并冲减应付'
  try { await ElMessageBox.confirm(tip, '确认审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/audit`); ElMessage.success('已审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}

async function handleUnAudit(row: any) {
  const repair = row.returnType === MaterialReturnType.REPAIR
  const tip = repair
    ? '确认反审核？将送修物料回源仓（若有维修返回记录需先撤销；关联订单已扣减的收料数会一并回滚）'
    : '确认反审核？将物料回源仓并冲销应付'
  try { await ElMessageBox.confirm(tip, '确认反审核', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/un-audit`); ElMessage.success('已反审核'); loadData() } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}

async function handleCancel(row: any) {
  try { await ElMessageBox.confirm('确认作废该退货单？', '确认作废', { type: 'warning' }) } catch { return }
  try { await request.put(`/outsource/material-return/${row.id}/cancel`); ElMessage.success('已作废'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

/** 新增时带上类型（物料退货 / 维修退货），进新增页后表单按类型切换 */
function handleAdd(type?: string) { router.push(`/outsource/material-return/add?returnType=${type || MaterialReturnType.REFUND}`) }
/** 编辑草稿（D 档 2026-09-21）：复用新增页（后端 PUT /{id} 仅允许草稿） */
/* 2026-09-24（用户口径）：列表不再提供「编辑」 —— 草稿态统一在详情页内联改+存（handleEdit 已移除）；
   新增仍走 /outsource/material-return/add（可带 fromDelivery 等预填）。 */
function goDetail(row: any) { router.push(`/outsource/material-return/detail/${row.id}`) }

onActivated(() => {
  // 详情/新增页数据变动后置脏标志，返回列表时按需刷新；否则保留查询/分页现场
  if (sessionStorage.getItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY) === '1') {
    sessionStorage.removeItem(OUTSOURCE_MATERIAL_RETURN_DIRTY_KEY)
    loadData()
  }
})
onMounted(loadData)

</script>

<template>
  <!-- 一页一张卡片（家规）：页签 → 筛选行 → 表格 → 分页 -->
  <div class="page-list">
    <el-card shadow="never">
      <!-- 页签：①物料退货（退回并冲减应付）②维修退货（送修 → 登记返回 → 结案） -->
      <el-tabs v-model="activeType" style="margin-bottom:8px" @tab-change="handleTabChange">
        <el-tab-pane :label="TAB_LABELS[MaterialReturnType.REFUND]" :name="MaterialReturnType.REFUND" />
        <el-tab-pane :label="TAB_LABELS[MaterialReturnType.REPAIR]" :name="MaterialReturnType.REPAIR" />
      </el-tabs>

      <!-- 筛选行（与加工退货页同一布局）：左侧筛选 + 查询/重置，右侧新增按钮（随页签切换） -->
      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:12px">
        <el-input v-model="query.code" placeholder="退货单号" clearable style="width:180px" @keyup.enter="handleSearch" />
        <RemoteSelect v-model="query.supplierId" :fetch="fetchSuppliers" placeholder="供应商" style="width:170px" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width:130px">
          <el-option label="草稿" :value="DocStatus.DRAFT" />
          <el-option label="已审核" :value="DocStatus.AUDITED" />
          <el-option label="已作废" :value="DocStatus.CANCELLED" />
        </el-select>
        <!-- 返回进度（仅维修退货）：跟踪"还有多少货在供应商处没回来" -->
        <el-select v-if="isRepairTab()" v-model="query.progress" placeholder="返回进度" clearable style="width:130px">
          <el-option label="待返回" value="PENDING_RETURN" />
          <el-option label="已结案" value="CLOSED" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <div style="margin-left:auto;display:flex;gap:8px">
          <el-button v-if="!isRepairTab()" type="success" :icon="'Plus'" @click="handleAdd(MaterialReturnType.REFUND)">新增</el-button>
          <el-button v-else type="success" :icon="'Plus'" @click="handleAdd(MaterialReturnType.REPAIR)">新增</el-button>
        </div>
      </div>

      <!-- 列宽合计：物料退货页签 898px / 维修退货页签 918px ＜ 内容区（纵向滚动条下约 948）⇒ 一行显示完、不横向滑动。
           两页签公共列同宽；维修页签不单列「出库源仓」（详情页可查），
           「送修/已返回」+「返回进度」合并为一列、已结案显示在「状态」列（与加工侧一致）。 -->
      <el-table :data="list" border stripe v-loading="loading" @row-click="goDetail">
        <el-table-column prop="code" label="退货单号" width="132" />
        <el-table-column :label="isRepairTab() ? '维修供应商' : '供应商'" width="100" show-overflow-tooltip>
          <template #default="{row}"><el-button type="primary" link @click.stop="router.push(`/supplier/detail/${row.supplierId}`)">{{ row.supplierName }}</el-button></template>
        </el-table-column>
        <el-table-column v-if="!isRepairTab()" prop="warehouseName" label="出库源仓" width="96" show-overflow-tooltip />
        <el-table-column label="退货/送修内容" min-width="136" show-overflow-tooltip>
          <!-- 物料退货显示"退货物料"、维修退货显示"送修物料"（同一列，明细在详情页） -->
          <template #default="{ row }">{{ row.itemSummary || '-' }}</template>
        </el-table-column>
        <el-table-column label="退货金额" width="86" align="right">
          <template #default="{ row }">{{ row.totalAmount != null ? Number(row.totalAmount).toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column label="退货日期" width="96" align="center">
          <template #default="{ row }">{{ $fmtDate(row.returnDate) }}</template>
        </el-table-column>
        <!-- 送修 / 已返回（仅维修退货，与加工侧同名同口径）：橙=供应商还没送完、绿=已全部送回；悬停给未返回数 -->
        <el-table-column v-if="isRepairTab()" label="送修/已返回" width="116" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span :style="{ color: Number(row.unreturnedQty) > 0 ? 'var(--app-color-warning)' : 'var(--app-color-success)', fontWeight: 500 }"
              :title="Number(row.unreturnedQty) > 0 ? ('还有 ' + row.unreturnedQty + ' 件未返回') : '已全部返回'">
              {{ row.sentQty ?? '-' }} / {{ row.returnedQty ?? 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="78" align="center">
          <!-- 维修退货已结案时显示「已结案」（替代"已审核"），未结案按原状态（与加工侧一致） -->
          <template #default="{ row }">
            <el-tag v-if="row.returnType === MaterialReturnType.REPAIR && row.closedFlag === 1" type="success" size="small">已结案</el-tag>
            <el-tag v-else :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <!-- 动作集与顺序统一（与加工侧一致）：详情 → 审核 → 反审核 → 作废 → 结案 → 撤销结案 -->
        <!-- 2026-09-24（用户口径）：反审核与编辑都收进详情页（详情草稿态可就地改+存）⇒ 操作列 176→132。 -->
        <el-table-column label="操作" width="132" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goDetail(row)">详情</el-button>
            <el-button type="success" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleAudit(row)">审核</el-button>
            <el-button type="danger" link v-if="row.status===DocStatus.DRAFT" @click.stop="handleCancel(row)">作废</el-button>
            <!-- 结案（仅维修退货）：未返回=0 时才出现，代表跟踪终点 -->
            <el-button type="success" link v-if="isRepairTab() && row.status===DocStatus.AUDITED && row.closedFlag!==1 && Number(row.unreturnedQty)===0" @click.stop="handleClose(row)">结案</el-button>
            <el-button type="warning" link v-if="row.closedFlag===1" @click.stop="handleReOpen(row)">撤销结案</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="handleSearch" @current-change="loadData" />
      </div>
    </el-card>
  </div>
</template>
