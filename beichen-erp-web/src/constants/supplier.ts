/** 供应商类型统一定义 */
export const TYPE_MAP: Record<string, string> = {
  solution: '方案商',
  factory: '加工厂',
  product: '成品商',
  material: '辅料商',
}

export const TYPE_TABS = [
  { name: 'all', label: '全部' },
  { name: 'solution', label: TYPE_MAP.solution },
  { name: 'factory', label: TYPE_MAP.factory },
  { name: 'product', label: TYPE_MAP.product },
  { name: 'material', label: TYPE_MAP.material },
]

export const TYPE_OPTIONS = TYPE_TABS.filter(x => x.name !== 'all')

export const TYPE_TAG: Record<string, string> = {
  solution: 'primary',
  factory: 'warning',
  product: 'success',
  material: 'info',
}
