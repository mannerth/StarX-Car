import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { request } from '@/api/http'
import type { TokenResponse, User } from '@/types/api'

const tokenKey = 'starx-car-admin-access-token'
const refreshTokenKey = 'starx-car-admin-refresh-token'
const userKey = 'starx-car-admin-user'

function restoreUser(): User | null {
  const value = localStorage.getItem(userKey)
  if (!value) return null
  try { return JSON.parse(value) as User } catch { return null }
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(localStorage.getItem(tokenKey) ?? '')
  const refreshToken = ref(localStorage.getItem(refreshTokenKey) ?? '')
  const user = ref<User | null>(restoreUser())
  const isAdmin = computed(() => Boolean(accessToken.value) && user.value?.type === 'admin')

  async function login(username: string, password: string) {
    const result = await request<TokenResponse>('/auth/login', {
      method: 'POST', data: { username, password },
    })
    if (result.user.type !== 'admin') throw new Error('该账号不是管理员，不能登录管理端。')
    updateSession(result.accessToken, result.refreshToken, result.user)
  }

  function updateSession(nextAccessToken: string, nextRefreshToken: string, nextUser: User) {
    accessToken.value = nextAccessToken
    refreshToken.value = nextRefreshToken
    user.value = nextUser
    localStorage.setItem(tokenKey, nextAccessToken)
    localStorage.setItem(refreshTokenKey, nextRefreshToken)
    localStorage.setItem(userKey, JSON.stringify(nextUser))
  }

  function updateUser(nextUser: User) {
    user.value = nextUser
    localStorage.setItem(userKey, JSON.stringify(nextUser))
  }

  function logout() {
    accessToken.value = ''
    refreshToken.value = ''
    user.value = null
    localStorage.removeItem(tokenKey)
    localStorage.removeItem(refreshTokenKey)
    localStorage.removeItem(userKey)
  }

  return { accessToken, refreshToken, user, isAdmin, login, updateSession, updateUser, logout }
})
