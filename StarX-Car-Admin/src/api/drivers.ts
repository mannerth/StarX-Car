import { request } from '@/api/http'
import type { AdminDriver, AdminDriverPage, DriverApprovalStatus } from '@/types/api'

export function fetchDrivers(params: { page: number; size: number; keyword?: string; approvalStatus?: DriverApprovalStatus }) {
  const query = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.approvalStatus) query.set('approvalStatus', params.approvalStatus)
  return request<AdminDriverPage>(`/admin/drivers?${query.toString()}`)
}

export function reviewDriver(id: number, approvalStatus: Exclude<DriverApprovalStatus, 'pending'>) {
  return request<AdminDriver>(`/admin/drivers/${id}/approval`, { method: 'PUT', data: { approvalStatus } })
}
