import { request } from '@/api/http'
import type { PricingRule, PricingRulePage, PricingRuleStatus, PricingRuleWriteRequest } from '@/types/api'

export function fetchPricingRules(params: { page: number; size: number; status?: PricingRuleStatus }) {
  const query = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.status) query.set('status', params.status)
  return request<PricingRulePage>(`/admin/pricing-rules?${query.toString()}`)
}

export function createPricingRule(data: PricingRuleWriteRequest) {
  return request<PricingRule>('/admin/pricing-rules', { method: 'POST', data })
}

export function updatePricingRule(id: number, data: PricingRuleWriteRequest) {
  return request<PricingRule>(`/admin/pricing-rules/${id}`, { method: 'PUT', data })
}

export function activatePricingRule(id: number, setAsDefault: boolean) {
  return request<PricingRule>(`/admin/pricing-rules/${id}/activate`, { method: 'POST', data: { setAsDefault } })
}

export function archivePricingRule(id: number) {
  return request<PricingRule>(`/admin/pricing-rules/${id}/archive`, { method: 'POST' })
}
