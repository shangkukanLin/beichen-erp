<script setup lang="ts">
/**
 * 单元格「产品 / 物料」可点链接（2026-09-25 用户口径：列表里的产品、物料也要能点进详情）。
 *
 * <p>为什么做成组件：委外加工有 8 个列位要用同一套行为（加工订单/成品收货/加工退货台账/返回单/
 * 维修退货/物料订单/物料收货/物料退货），分散写 8 份必然跑偏。</p>
 *
 * <p>⚠️ 交互为什么不是"格内逐个链接"：表格单元格是 `overflow:hidden` + 省略号（一行显示完家规），
 * 被裁到可视区外的链接**点不到**。所以：
 *   ① 单项（实际数据里最常见）→ 名称直接是链接；
 *   ② 多项 → 显示「首个名称 等 N 项」，点击弹 Popover 列表，**每一项都可点**；
 *   ③ 没有任何 id（历史数据/后端未给）→ 渲染默认插槽（调用方原有的纯文本/tooltip），不改变现状。</p>
 *
 * <p>跳转目标（都已存在，无需新建页面）：
 *   产品 → `/product/detail/:id`（产品主数据详情）；
 *   物料 → `/outsource/material-stock/detail/:id`（物料档案 + 跨仓库存分布 + 可点进流水）。</p>
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps<{
  /** 行上的明细数组：产品 [{id,name,sku}] / 物料 [{materialId,materialName}] / 主体 [{id,name}] */
  items?: Array<Record<string, any>> | null
  /** 跳转目标类型（决定 id 字段名与详情路由） */
  target: 'product' | 'material' | 'supplier' | 'vendor' | 'customer'
  /**
   * 名称字段名（默认 product→name / material→materialName / 其余→name）。
   * 2026-10-10（用户口径「物料名称前显示物料类型」）：**也接受函数** `(it) => string` ——
   * 物料调用方直接传全局属性 `:name-key="$mLabel"` 即可拼出「类型 | 名称」✓（口径见 utils/materialLabel.ts）。
   */
  nameKey?: string | ((it: Record<string, any>) => string)
  /** 副标题字段名（如 sku），仅单项时随名称一起显示 */
  subKey?: string
  /** 数量字段名（Popover 里显示 ×N；无则显示名称） */
  qtyKey?: string
}>()

const router = useRouter()

/** 目标 → 详情路由前缀（全部为已有路由，无需新建页面） */
const ROUTE_PREFIX: Record<string, string> = {
  product: '/product/detail/',                        // 产品主数据详情
  material: '/outsource/material-stock/detail/',      // 物料库存分布详情（含物料档案摘要）
  supplier: '/supplier/detail/',                      // 供应商详情
  vendor: '/outsource/supplier/detail/',              // 供货商（成品商）详情
  customer: '/inventory/customer/detail/'             // 客户详情
}

const idKey = computed(() => (props.target === 'material' ? 'materialId' : 'id'))
const nameKey = computed(() => {
  if (props.nameKey) return props.nameKey
  return props.target === 'material' ? 'materialName' : 'name'
})

/** 归一化为 [{id,name,sub,qty}]；没有 id 的项直接剔除（不可点 ⇒ 交回默认插槽兜底） */
const list = computed(() => {
  return (props.items || [])
    .filter((it) => it && it[idKey.value] != null)
    .map((it) => ({
      id: it[idKey.value],
      // nameKey 支持函数（2026-10-10）：物料侧传 $mLabel ⇒ 显示「物料类型 | 物料名称」✓
      name: resolveName(it, idKey.value),
      sub: props.subKey ? it[props.subKey] : '',
      qty: props.qtyKey ? it[props.qtyKey] : undefined
    }))
})

/** 取显示名：nameKey 是函数就用它（如 `$mLabel`），是字符串就按字段取；取不到再回退 `#id` */
function resolveName(it: Record<string, any>, idField: string): string {
  const nk = nameKey.value
  const raw = typeof nk === 'function' ? nk(it) : it[nk]
  return raw || ('#' + it[idField])
}

const first = computed(() => list.value[0])

function go(id: any) {
  router.push((ROUTE_PREFIX[props.target] || ROUTE_PREFIX.product) + id)
}
</script>

<template>
  <!-- 无可用 id：保持调用方原样（纯文本 / tooltip） -->
  <span v-if="list.length === 0"><slot /></span>

  <!-- 单项：名称即链接（+ 可选副标题，如 SKU） -->
  <span v-else-if="list.length === 1">
    <el-button type="primary" link @click.stop="go(first.id)">{{ first.name }}</el-button>
    <span v-if="first.sub" style="color:var(--app-text-secondary)"> · {{ first.sub }}</span>
  </span>

  <!-- 多项：首个 + 等 N 项，点开 Popover 逐项可点（被省略号裁掉的项也能点到） -->
  <el-popover v-else placement="top-start" trigger="click" :width="300" popper-class="entity-links-popper">
    <template #reference>
      <el-button type="primary" link @click.stop>{{ first.name }} 等 {{ list.length }} 项</el-button>
    </template>
    <div class="entity-list">
      <div v-for="it in list" :key="it.id" class="entity-line">
        <el-button type="primary" link @click.stop="go(it.id)">{{ it.name }}</el-button>
        <span class="entity-qty">
          <span v-if="it.sub" style="margin-right:6px">{{ it.sub }}</span>
          <span v-if="it.qty != null">×{{ it.qty }}</span>
        </span>
      </div>
    </div>
  </el-popover>
</template>

<style scoped>
/* 多值列表：超过 ~8 行时可在弹层内滚动（避免长物料单撑满屏） */
.entity-list { max-height: 260px; overflow-y: auto; }
.entity-line { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 2px 0; }
.entity-qty { color: var(--app-text-secondary); font-size: var(--app-font-xs); white-space: nowrap; }
</style>
