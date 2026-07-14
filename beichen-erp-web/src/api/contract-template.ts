import request from '@/utils/request'

export interface ContractTemplate {
  id?: number
  templateName: string
  content: string
  status?: number
  templateType?: string
}

export function getTemplateList(templateType?: string) {
  const params: any = {}
  if (templateType) params.templateType = templateType
  return request.get<unknown, ContractTemplate[]>('/outsource/contract-template/list', { params })
}

export function createTemplate(data: ContractTemplate) {
  return request.post<unknown, void>('/outsource/contract-template', data)
}

export function updateTemplate(id: number, data: ContractTemplate) {
  return request.put<unknown, void>(`/outsource/contract-template/${id}`, data)
}

export function deleteTemplate(id: number) {
  return request.delete<unknown, void>(`/outsource/contract-template/${id}`)
}

export function setDefaultTemplate(id: number) {
  return request.put<unknown, void>(`/outsource/contract-template/${id}/default`)
}

export function exportContractPdf(orderId: number, templateId?: number) {
  const url = templateId
    ? `/outsource/contract-template/export/${orderId}?templateId=${templateId}`
    : `/outsource/contract-template/export/${orderId}`
  return url
}

export function exportMaterialOrderPdf(orderId: number, templateId?: number) {
  const url = templateId
    ? `/outsource/contract-template/export-material-order/${orderId}?templateId=${templateId}`
    : `/outsource/contract-template/export-material-order/${orderId}`
  return url
}
