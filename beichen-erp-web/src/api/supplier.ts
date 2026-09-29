/**
 * 供应商 API（F8-26 整理 · 2026-09-30）
 *
 * <p>背景：这些函数的路径全是 `/supplier/**`，与"系统设置"无关，却一直塞在 `api/system.ts` 里
 * ⇒ 按端点归属放错文件。实现已迁到本文件；`api/system.ts` 保留 **re-export** 兼容既有 import，
 * 各页面可逐步把 `from '@/api/system'` 改为 `from '@/api/supplier'`。</p>
 *
 * <p>类型定义暂时仍留在 `system.ts`（迁类型会牵动更多引用），故这里按需 `import type`；
 * 后续可连同类型一起迁过来。</p>
 */
import request from '@/utils/request'
import type { PageResult } from '@/api/product'
import type { SupplierVO, SupplierDTO, SupplierQueryParams, SupplierProductVO, SupplierProductDTO } from '@/api/system'

export function getSupplierPage(params: SupplierQueryParams) {
  return request.get<PageResult<SupplierVO>>('/supplier/page', { params })
}

export function getSupplier(id: number | string) {
  return request.get<SupplierVO>(`/supplier/${id}`)
}

export function getSupplierProducts(id: number | string) {
  return request.get<SupplierProductVO[]>(`/supplier/${id}/products`)
}

export function addSupplier(data: SupplierDTO) {
  return request.post<void>('/supplier', data)
}

export function updateSupplier(data: SupplierDTO) {
  return request.put<void>('/supplier', data)
}

export function deleteSupplier(id: number | string) {
  return request.delete<void>(`/supplier/${id}`)
}

export function toggleSupplierStatus(id: number | string) {
  return request.put<void>(`/supplier/${id}/status`)
}

export function saveSupplierProducts(id: number | string, products: SupplierProductDTO[]) {
  return request.put<void>(`/supplier/${id}/products`, products)
}
