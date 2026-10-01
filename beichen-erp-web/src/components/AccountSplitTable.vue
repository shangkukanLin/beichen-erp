<template>
  <div class="account-split" :data-split-debug="dbg">
    <!-- 总额：默认带出（销售单=应收总额），可改小 = 部分收款。为空时退化为"各行独立录入"。 -->
    <div v-if="showTotalInput" class="account-split__head">
      <span class="account-split__label">{{ totalLabel }}</span>
      <el-input-number
        :model-value="total ?? undefined"
        :min="0"
        :precision="2"
        :controls="false"
        :disabled="disabled"
        class="account-split__total"
        data-role="total"
        @update:model-value="onTotalInput"
      />
      <span v-if="upperLimit != null" class="account-split__limit">
        （{{ upperLabel }} ¥{{ fmt(upperLimit) }}<template v-if="remaining > 0.004">，本次待收 <b>¥{{ fmt(remaining) }}</b> 将挂预收</template><template v-else-if="remaining < -0.004">，<b class="is-err">已超出 ¥{{ fmt(-remaining) }}</b></template>）
      </span>
    </div>

    <el-table :data="rows" size="small" border class="account-split__table">
      <el-table-column type="index" label="#" width="48" align="center" />
      <el-table-column :label="accountColumnLabel" min-width="200">
        <template #default="{ row }">
          <el-select
            v-model="row.accountId"
            data-role="account"
            filterable
            clearable
            :disabled="disabled"
            :placeholder="accountPlaceholder"
            style="width:100%"
            @change="onRowsChanged"
          >
            <el-option
              v-for="a in optionsFor(row)"
              :key="a[accountValueKey]"
              :label="labelOf(a)"
              :value="a[accountValueKey]"
            />
            <template v-if="addRoute" #footer>
              <div class="account-split__add" @click="goAddAccount">{{ addLabel }}</div>
            </template>
          </el-select>
        </template>
      </el-table-column>
      <el-table-column :label="amountColumnLabel" width="180">
        <template #default="{ row }">
          <!-- 不用 v-model：手改一行要触发"锁定该行 + 自动行移交 + 重算"，必须显式处理事件 -->
          <el-input-number
            :model-value="row.amount ?? undefined"
            data-role="amount"
            :min="0"
            :precision="2"
            :controls="false"
            :disabled="disabled"
            style="width:100%"
            @update:model-value="(v: any) => onAmountInput(row, v)"
          />
          <span v-if="autoMarked(row)" class="account-split__auto">自动</span>
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.remark" :disabled="disabled" placeholder="可选" @change="onRowsChanged" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center">
        <template #default="{ $index }">
          <el-button link type="danger" :disabled="disabled || rows.length <= 1" @click="removeRow($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="account-split__foot">
      <el-button link type="primary" :disabled="disabled" data-role="add" @click="addRow">+ 添加账户</el-button>
      <span class="account-split__sum">
        合计 ¥{{ fmt(sum) }}
        <b v-if="!balanced" class="is-err">（与{{ totalLabel }}不一致）</b>
      </span>
    </div>

    <div v-if="warning" class="account-split__warn" data-role="warn">{{ warning }}</div>
  </div>
</template>

<script setup lang="ts">
/**
 * 多账户金额分摊表（2026-09-30）
 *
 * 用于销售单「现金收款」、收款单、付款单三处的同一交互：总额由用户录入，账户可多行，
 * 金额自动分摊。规则（用户确认口径）——**单一自动吸收行**：
 *
 *   · 永远有且只有一行是「自动行」，其金额 = 总额 − 其它行合计（尾差也由它吸收）；
 *   · 用户手改某行后，该行**锁定**（记住用户输入值），自动权移交给下一个未被手改的行，
 *     没有则给最后一行；
 *   · 新增行不抢自动权（所以「加第二个账户」时第一行仍是自动行，自动变成 总额−200）；
 *   · 删除自动行 ⇒ 自动权移交；改总额 ⇒ 仅重算自动行；
 *   · 其它行合计 > 总额 ⇒ 自动行钳制为 0 并给出警告（阻止提交，不自动调减其它行）。
 *
 * 「总额为空」时退化为**各行独立录入**（与原收款/付款单行为一致），此时会自动把合计回填成总额，
 * 避免用户以为必须先用总额才能录金额。
 *
 * 落库协议不在本组件内：对外只暴露 [{accountId, amount, remark}]，由各页面走自己的 DTO。
 */
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

/** 行结构：与收款单/付款单既有的 accounts 明细同形（accountId/amount/remark） */
interface AccountSplitRow {
  accountId?: number | null
  amount?: number | null
  remark?: string
}

const props = withDefaults(defineProps<{
  /** 行数据（v-model） */
  modelValue: AccountSplitRow[]
  /** 可选的账户列表（一般是 /finance/account/list 的结果） */
  accounts?: any[]
  accountValueKey?: string
  /** 下拉项文本：字段名，或 (row) => string */
  accountLabelKey?: string | ((row: any) => string)
  accountColumnLabel?: string
  amountColumnLabel?: string
  accountPlaceholder?: string
  /** 总额（v-model:total） */
  total?: number | null
  totalLabel?: string
  showTotalInput?: boolean
  /** 上限（如销售单的应收总额）；仅用于提示与校验，不做自动调减 */
  upperLimit?: number | null
  upperLabel?: string
  disabled?: boolean
  /** 账户下拉底部的「+ 新增」入口（复用 RemoteSelect 的 footer 做法，不占用 change 事件） */
  addRoute?: string
  addLabel?: string
}>(), {
  accounts: () => [],
  accountValueKey: 'id',
  accountLabelKey: 'accountName',
  accountColumnLabel: '账户',
  amountColumnLabel: '金额',
  accountPlaceholder: '请选择账户',
  total: null,
  totalLabel: '总额',
  showTotalInput: true,
  upperLimit: null,
  upperLabel: '上限',
  disabled: false,
  addRoute: '',
  addLabel: '+ 新增',
})

const emit = defineEmits<{
  (e: 'update:modelValue', v: AccountSplitRow[]): void
  (e: 'update:total', v: number | null): void
}>()

const rows = computed(() => props.modelValue)

/** 用户手改过的行（按对象引用记，避免把标记写进要提交的数据里） */
let locked = new WeakSet<object>()
/** 当前自动吸收行 */
const autoRow = ref<AccountSplitRow | null>(null)
/** 提示文案（不足/超限等） */
const warning = ref('')

const sum = computed(() => round2(rows.value.reduce((s, r) => s + Number(r.amount || 0), 0)))
const remaining = computed(() => (props.upperLimit == null ? 0 : round2(Number(props.upperLimit) - Number(props.total ?? 0))))
const balanced = computed(() => props.total == null || Math.abs(sum.value - Number(props.total ?? 0)) < 0.005)

function fmt(v: number) { return Number(v || 0).toFixed(2) }
function round2(n: number) { return Math.round((Number(n) + Number.EPSILON) * 100) / 100 }
function labelOf(a: any) {
  return typeof props.accountLabelKey === 'function' ? props.accountLabelKey(a) : a?.[props.accountLabelKey]
}
/**
 * 2026-10-01（用户口径）：**同一个账户只能被选一次** —— 某行的下拉里不再显示「其它行已经选过」的账户，
 * 避免选重后提交才报错。
 * <p>本行自己已选的账户必须保留，否则 el-select 找不到匹配选项、会把原始 id 直接显示出来。</p>
 * <p>本函数在模板里被调用 → 渲染副作用会收集它同步访问到的响应式数据（modelValue / accounts），
 * 所以任一行改账户后，其它行的选项会自动刷新，无需额外的 watch。</p>
 */
function optionsFor(row: AccountSplitRow) {
  const used = new Set<any>()
  for (const r of rows.value) {
    if (r === row) continue
    if (r.accountId != null && (r.accountId as any) !== '') used.add(r.accountId)
  }
  if (used.size === 0) return props.accounts
  return props.accounts.filter(a => {
    const v = a?.[props.accountValueKey]
    return v === row.accountId || !used.has(v)
  })
}
function autoMarked(row: AccountSplitRow) { return autoRow.value === row }
function emitRows() { emit('update:modelValue', [...rows.value]) }

/** 确保存在自动行：优先沿用现有自动行，否则取第一个未锁定行，再否则取最后一行 */
function ensureAuto() {
  const list = rows.value
  if (list.length === 0) { autoRow.value = null; return }
  if (autoRow.value && list.includes(autoRow.value)) return
  autoRow.value = list.find(r => !locked.has(r)) || list[list.length - 1]
}

/** 把「总额 − 其它行合计」写到自动行；总额为空时不分配（各行独立录入模式） */
function recalc() {
  ensureAuto()
  const auto = autoRow.value
  if (!auto || props.total == null) { refreshWarning(); return }
  const others = rows.value.filter(r => r !== auto).reduce((s, r) => s + Number(r.amount || 0), 0)
  const next = Math.max(0, round2(Number(props.total) - others))
  // 只有真的变了才 emit：父页面可能用 watch 把总额再同步回来，无守卫会形成回写循环
  if (auto.amount !== next) {
    auto.amount = next
    emitRows()
  }
  refreshWarning()
}

function refreshWarning() {
  if (props.total == null) { warning.value = ''; return }
  const otherSum = round2(rows.value.filter(r => r !== autoRow.value).reduce((s, r) => s + Number(r.amount || 0), 0))
  if (otherSum > Number(props.total) + 0.004) {
    warning.value = `其它账户合计 ¥${fmt(otherSum)} 已超出${props.totalLabel} ¥${fmt(Number(props.total))}，请调整金额`
  } else {
    warning.value = ''
  }
}

/** 手改某行金额：锁定该行；若它就是自动行则移交；再重算新的自动行 */
function onAmountInput(row: AccountSplitRow, v: any) {
  const nv = v == null || v === '' ? null : round2(Number(v))
  if (row.amount === nv) return
  row.amount = nv
  locked.add(row)
  if (autoRow.value === row) autoRow.value = null
  if (props.total == null) {
    // 总额为空：按各行独立录入，合计回填总额（保持与旧行为一致）
    onRowsChanged()
    return
  }
  recalc()
  onRowsChanged()
}

function onTotalInput(v: any) {
  const nv = v == null || v === '' ? null : round2(Number(v))
  emit('update:total', nv)
  if (nv != null) recalc()
  onRowsChanged()
}

function addRow() {
  rows.value.push({ accountId: null, amount: 0, remark: '' })
  ensureAuto()
  recalc()
  onRowsChanged()
}

function removeRow(i: number) {
  const removed = rows.value[i]
  rows.value.splice(i, 1)
  if (removed && autoRow.value === removed) autoRow.value = null
  ensureAuto()
  recalc()
  onRowsChanged()
}

/** 账户/备注/删行等非金额变化：同步出去（必要时重算尾部） */
function onRowsChanged() {
  if (props.total == null) {
    const s = sum.value
    if (s > 0 || props.total === 0) emit('update:total', s)
  } else {
    recalc()
  }
  emitRows()
}

/** 供页面在提交前调用；返回 null 表示通过 */
function validate(): string | null {
  if (rows.value.length === 0) return `请至少填写一个${props.accountColumnLabel}`
  const ids: any[] = []
  for (const r of rows.value) {
    // el-select 清空时可能是 undefined/null，个别场景会给 '' —— 都算未选择（类型上是 number，故用 any 比较）
    if (r.accountId == null || (r.accountId as any) === '') return `请为每一行选择${props.accountColumnLabel}`
    if (ids.includes(r.accountId)) return `${props.accountColumnLabel}不能重复`
    ids.push(r.accountId)
    if (!(Number(r.amount) > 0)) return `每一行的${props.amountColumnLabel}必须大于 0`
  }
  if (props.total == null) return `请填写${props.totalLabel}`
  if (props.upperLimit != null && Number(props.total) > Number(props.upperLimit) + 0.004) {
    return `${props.totalLabel}不能超过${props.upperLabel} ¥${fmt(Number(props.upperLimit))}`
  }
  if (Math.abs(sum.value - Number(props.total)) > 0.005) return `各行合计必须等于${props.totalLabel}`
  return null
}

defineExpose({ validate, recalc })

/**
 * 临时诊断（2026-10-01）：把内部状态映射到根元素的 data-split-debug，便于 e2e 精确定位
 * "总额有值但自动行没分摊"这类时序问题。定位完可删。
 */
const dbg = computed(() => {
  const a = rows.value.map((r: AccountSplitRow) => (r.accountId ?? '-') + ':' + (r.amount ?? '-')).join(',')
  return 'total=' + (props.total ?? 'null') + ';n=' + rows.value.length + ';rows=' + a + ';auto=' + (autoRow.value ? autoRow.value.amount : 'none')
})

/** 切换单据/重置后外部替换数组时，重建自动行 */
watch(() => rows.value.length, () => { autoRow.value = null; ensureAuto(); if (props.total != null) recalc() })

/**
 * 2026-10-01：父页面可能**程序化**设置总额（销售单切到「现金」时默认带出应收总额），
 * 这条路径不经过组件的 onTotalInput ⇒ 必须监听 props.total 重算，否则自动行停在 0。
 * （收款/付款单是用户手输总额，走 onTotalInput，不受影响。）
 */
// immediate 很关键：本组件是 v-if 挂载的（切到「现金」才渲染），挂载时 total 往往**已经是**应收总额，
// 不存在"从 null 变有值"的过程 ⇒ 不 immediate 就永远不会做初始分摊，自动行停在 0。
watch(() => props.total, () => { if (props.total != null) recalc() }, { immediate: true })

const router = useRouter()
function goAddAccount() {
  if (props.addRoute) router.push(props.addRoute)
}
</script>

<style scoped>
.account-split__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.account-split__label {
  font-size: var(--app-font-sm, 13px);
  color: var(--app-color-text-regular, #606266);
}
.account-split__total {
  width: 150px;
}
.account-split__limit {
  font-size: var(--app-font-xs, 12px);
  color: var(--app-color-text-secondary, #909399);
}
.account-split__limit .is-err,
.account-split__sum .is-err {
  color: var(--el-color-danger, #f56c6c);
}
.account-split__auto {
  position: absolute;
  right: 8px;
  top: -6px;
  font-size: 11px;
  color: var(--app-color-primary, #409eff);
  pointer-events: none;
}
.account-split__table :deep(td.el-table__cell) {
  position: relative;
  overflow: visible;
}
.account-split__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}
.account-split__sum {
  font-size: var(--app-font-sm, 13px);
  color: var(--app-color-text-regular, #606266);
}
.account-split__warn {
  margin-top: 6px;
  font-size: var(--app-font-xs, 12px);
  color: var(--el-color-danger, #f56c6c);
}
.account-split__add {
  padding: 6px 12px;
  cursor: pointer;
  text-align: center;
  font-size: var(--app-font-xs, 12px);
  color: var(--app-color-primary, #409eff);
  border-top: 1px solid var(--app-color-border, #ebeef5);
}
.account-split__add:hover {
  background: var(--app-color-fill, #f5f7fa);
}
</style>
