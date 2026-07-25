import { request } from '@/api/http'
import type { Gender, User, UserPage, UserType } from '@/types/api'

export function fetchUsers(params: { page: number; size: number; keyword?: string; type?: UserType }) {
  const query = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.type) query.set('type', params.type)
  return request<UserPage>(`/admin/users?${query.toString()}`)
}

export function updateUser(id: number, data: { username: string; email: string | null; gender: Gender }) {
  return request<User>(`/admin/users/${id}`, { method: 'PUT', data })
}

export function deleteUser(id: number) {
  return request<void>(`/admin/users/${id}`, { method: 'DELETE' })
}

export function resetUserPassword(id: number) {
  return request<void>(`/admin/users/${id}/reset-password`, { method: 'POST' })
}
