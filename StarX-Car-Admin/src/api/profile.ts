import { request } from '@/api/http'
import type { Gender, User } from '@/types/api'

export function fetchMyProfile() {
  return request<User>('/users/me')
}

export function updateMyProfile(data: { username: string; email: string | null; gender: Gender; realName: string | null; avatarUrl: string | null }) {
  return request<User>('/users/me', { method: 'PUT', data })
}

export function changeMyPassword(currentPassword: string, newPassword: string) {
  return request<void>('/users/me/password', { method: 'POST', data: { currentPassword, newPassword } })
}
