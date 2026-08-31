import { request } from '@/api/http'
import type { Complaint, ComplaintPage, ComplaintResolutionType } from '@/types/api'

export const fetchComplaints = (params: { page: number; size: number; status?: string }) => {
  const query = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.status) query.set('status', params.status)
  return request<ComplaintPage>(`/admin/complaints?${query.toString()}`)
}
export const fetchComplaint = (complaintNo: string) => request<Complaint>(`/admin/complaints/${complaintNo}`)
export const processComplaint = (complaintNo: string, data: { resolutionType: ComplaintResolutionType; refundAmount: number; content: string }) => request<Complaint>(`/admin/complaints/${complaintNo}/process`, { method: 'POST', data })
