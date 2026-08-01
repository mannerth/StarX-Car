export type UserType = 'admin' | 'passenger' | 'driver'
export type Gender = 'male' | 'female'
export type UserStatus = 'active' | 'disabled' | 'cancelled'

export interface User {
  id: number
  username: string
  email: string | null
  gender: Gender | null
  type: UserType
  realName: string | null
  avatarUrl: string | null
  status: UserStatus
  createTime: string | null
  lastLoginTime: string | null
  updateTime: string | null
}

export interface UserPage {
  page: number
  size: number
  total: number
  records: User[]
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: User
}

export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}
