export type UserType = 'admin' | 'passenger' | 'driver'
export type Gender = 'male' | 'female'

export interface User {
  id: number
  username: string
  email: string | null
  gender: Gender | null
  type: UserType
  createTime: string | null
  lastLoginTime: string | null
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
