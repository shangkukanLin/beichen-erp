import { useRouter } from 'vue-router'

export const ADD_MARKER = '__ADD_NEW__'

/**
 * 为下拉框选项追加"新增"项，并返回 change 处理函数
 * @param addRoute  点击"新增"后跳转的路由路径
 * @param onNormalChange  正常选中某一项时的回调（可选）
 * @returns  { handleChange }
 */
export function useSelectWithAdd(addRoute: string, onNormalChange?: (val: string) => void) {
  const router = useRouter()

  function handleChange(val: string) {
    if (val === ADD_MARKER) {
      router.push(addRoute)
      return
    }
    onNormalChange?.(val)
  }

  return { handleChange, ADD_MARKER }
}

/**
 * 为选项数组末尾追加"新增"项
 * @param list  原始选项 label 数组
 * @returns  追加了"+ 新增"的新数组
 */
export function appendAddOption(list: string[]): string[] {
  return [...list, ADD_MARKER]
}

/**
 * 为对象选项数组末尾追加"新增"项
 * @param list  原始选项对象数组
 * @param labelKey  label 字段名
 * @param valueKey  value 字段名
 * @returns  追加了"+ 新增"的新数组
 */
export function appendAddOptionObj<T extends Record<string, any>>(
  list: T[],
  labelKey = 'label',
  valueKey = 'value'
): (T | Record<string, any>)[] {
  return [...list, { [labelKey]: '+ 新增', [valueKey]: ADD_MARKER }]
}
