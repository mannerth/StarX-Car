import { request } from '@/api/http'
import type { AdminDriverLocation, AdminFareItem, AdminOrderDetail, AdminOrderMetrics, AdminOrderNote, AdminOrderPage, OrderStatus, OrderType } from '@/types/api'

export interface OrderFilters { page: number; size: number; orderNo?: string; orderType?: OrderType; status?: OrderStatus; keyword?: string; createdFrom?: string; createdTo?: string; scheduledFrom?: string; scheduledTo?: string }
const time = (value?: string) => value ? value.replace('T', ' ') + (value.length === 16 ? ':00' : '') : undefined
export function fetchOrders(params: OrderFilters) {
  const query = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  for (const [key, value] of Object.entries({ ...params, createdFrom: time(params.createdFrom), createdTo: time(params.createdTo), scheduledFrom: time(params.scheduledFrom), scheduledTo: time(params.scheduledTo) })) {
    if (value && !['page', 'size'].includes(key)) query.set(key, String(value))
  }
  return request<AdminOrderPage>(`/admin/orders?${query}`)
}
export function fetchOrderMetrics(from?: string, to?: string) {
  const query = new URLSearchParams()
  if (from) query.set('from', time(from)!)
  if (to) query.set('to', time(to)!)
  return request<AdminOrderMetrics>(`/admin/orders/summary${query.size ? `?${query}` : ''}`)
}
export const fetchOrderDetail = (orderNo: string) => request<AdminOrderDetail>(`/admin/orders/${orderNo}`)
export const fetchOrderNotes = (orderNo: string) => request<AdminOrderNote[]>(`/admin/orders/${orderNo}/notes`)
export const fetchFareItems = (orderNo: string) => request<AdminFareItem[]>(`/admin/orders/${orderNo}/fare-items`)
export const fetchDriverLocation = (orderNo: string) => request<AdminDriverLocation>(`/admin/orders/${orderNo}/driver-location`)
export const cancelOrder = (orderNo: string, reason: string) => request(`/admin/orders/${orderNo}/cancel`, { method: 'POST', data: { reason } })
export const dispatchReservation = (orderNo: string, reason: string) => request(`/admin/orders/${orderNo}/dispatch`, { method: 'POST', data: { reason } })
export const addOrderNote = (orderNo: string, content: string) => request<AdminOrderNote>(`/admin/orders/${orderNo}/notes`, { method: 'POST', data: { content } })
