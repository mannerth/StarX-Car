import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import type { ApiEnvelope, TokenResponse } from '@/types/api'
import { useAuthStore } from '@/stores/auth'

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api'
let refreshRequest: Promise<string> | null = null

declare module 'axios' {
  export interface InternalAxiosRequestConfig {
    _retry?: boolean
  }
}

const http = axios.create({ baseURL: baseUrl })

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const accessToken = localStorage.getItem('starx-car-admin-access-token')
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const original = error.config
    const isAuthenticationRequest = original?.url?.includes('/auth/login') || original?.url?.includes('/auth/refresh')
    if (error.response?.status !== 401 || !original || original._retry || isAuthenticationRequest) {
      return Promise.reject(error)
    }

    original._retry = true
    try {
      const accessToken = await refreshAccessToken()
      original.headers.Authorization = `Bearer ${accessToken}`
      return http(original)
    } catch {
      const auth = useAuthStore()
      auth.logout()
      if (!window.location.pathname.endsWith('/login')) {
        window.location.assign(`${import.meta.env.BASE_URL}login`)
      }
      return Promise.reject(new Error('登录状态已过期，请重新登录。'))
    }
  },
)

async function refreshAccessToken(): Promise<string> {
  if (!refreshRequest) {
    const refreshToken = localStorage.getItem('starx-car-admin-refresh-token')
    if (!refreshToken) return Promise.reject(new Error('缺少刷新令牌'))
    refreshRequest = axios.post<ApiEnvelope<TokenResponse>>(`${baseUrl}/auth/refresh`, { refreshToken })
      .then((response) => {
        if (response.data.code !== 0 || !response.data.data.accessToken) throw new Error(response.data.message)
        const { accessToken, refreshToken: newRefreshToken, user } = response.data.data
        useAuthStore().updateSession(accessToken, newRefreshToken, user)
        return accessToken
      })
      .finally(() => { refreshRequest = null })
  }
  return refreshRequest
}

export async function request<T>(path: string, options: AxiosRequestConfig = {}): Promise<T> {
  let response: AxiosResponse<ApiEnvelope<T>>
  try {
    response = await http.request<ApiEnvelope<T>>({ url: path, ...options })
  } catch (reason) {
    if (reason instanceof Error && reason.message === '登录状态已过期，请重新登录。') throw reason
    const axiosError = reason as AxiosError<ApiEnvelope<unknown>>
    if (!axiosError.response) throw new Error('无法连接后端服务，请确认服务已启动。')
    throw new Error(axiosError.response.data?.message ?? '请求失败，请稍后重试。')
  }
  if (response.data.code !== 0) throw new Error(response.data.message)
  return response.data.data
}
