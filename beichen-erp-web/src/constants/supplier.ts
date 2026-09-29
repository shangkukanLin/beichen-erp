/** 供应商类型统一定义 */
export const TYPE_MAP: Record<string, string> = {
  solution: '方案商',
  factory: '加工厂',
  product: '供货商',
  material: '辅料商',
}

/**
 * 主体类型页签顺序。
 * <p>2026-09-28（用户口径「供应商管理页面，TAB 加工厂排在最前面」）：**加工厂提到类型页签的第一位**
 * （「全部」仍按全站惯例留在首位 —— 它是过滤器而不是业务类型；若要把"全部"也排到加工厂后面，改这一行即可）。</p>
 * <p>⚠️ 本常量被**供应商管理**（`views/supplier/manage.vue`，过滤掉供货商）与**应付列表**
 * （`views/finance/payable.vue`，注释写明"与供应商管理页交互一致"）共用；`TYPE_OPTIONS` 也由它派生
 * （详情/新增页的类型多选）。⇒ 顺序一改**这几处同步**，这正是想要的：同一套主体类型不该有两种排列。</p>
 */
export const TYPE_TABS = [
  { name: 'all', label: '全部' },
  { name: 'factory', label: TYPE_MAP.factory },
  { name: 'solution', label: TYPE_MAP.solution },
  { name: 'product', label: TYPE_MAP.product },
  { name: 'material', label: TYPE_MAP.material },
]

export const TYPE_OPTIONS = TYPE_TABS.filter(x => x.name !== 'all')

export const TYPE_TAG: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
  solution: 'primary',
  factory: 'warning',
  product: 'success',
  material: 'info',
}

