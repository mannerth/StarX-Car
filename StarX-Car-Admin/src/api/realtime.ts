import { onMounted, onUnmounted, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { refreshAccessToken } from './http'

/** Event-driven refresh with reconnect resync and a single in-flight load. */
export function useOrderRealtime(load: () => Promise<void>, loadLocation?: () => Promise<void>) {
  const auth = useAuthStore()
  let socket: WebSocket | undefined
  let retry: ReturnType<typeof setTimeout> | undefined
  let heartbeat: ReturnType<typeof setInterval> | undefined
  let disposed = false
  let failures = 0
  let running = false
  let dirty = false
  let locationRunning = false
  let locationDirty = false
  let lastMessage = Date.now()

  async function refresh() {
    dirty = true
    if (running || document.hidden || disposed) return
    running = true
    try {
      while (dirty && !disposed && !document.hidden) { dirty = false; await load() }
    } finally { running = false }
  }
  async function refreshLocation() {
    locationDirty = true
    if (locationRunning || document.hidden || disposed || !loadLocation) return
    locationRunning = true
    try {
      while (locationDirty && !disposed && !document.hidden) { locationDirty = false; await loadLocation() }
    } finally { locationRunning = false }
  }
  function schedule() {
    if (disposed || !auth.isAdmin) return
    retry = setTimeout(connect, Math.min(30000, 1000 * 2 ** Math.min(failures++, 5)))
  }
  function connect() {
    if (disposed || !auth.isAdmin) return
    const base = import.meta.env.VITE_API_BASE_URL ?? '/api'
    const url = new URL(`${base.replace(/\/+$/, '')}/realtime`, window.location.href)
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    const connection = new WebSocket(url)
    socket = connection
    lastMessage = Date.now()
    connection.onopen = () => connection.send(JSON.stringify({ type: 'auth', token: auth.accessToken }))
    connection.onmessage = (message) => {
      let frame: { type: string; code?: number }
      try { frame = JSON.parse(String(message.data)) as typeof frame } catch { connection.close(); return }
      lastMessage = Date.now()
      if (frame.type === 'ready') { failures = 0; void refresh() }
      if (frame.type === 'orders.changed') void refresh()
      if (frame.type === 'location.changed' && loadLocation) void refreshLocation()
    }
    connection.onclose = async (event) => {
      if (socket !== connection || disposed) return
      socket = undefined
      clearInterval(heartbeat)
      if (event.code === 1003) { auth.logout(); return }
      if (event.code === 1008 && auth.isAdmin) {
        try { await refreshAccessToken() } catch { auth.logout(); return }
      }
      schedule()
    }
    connection.onerror = () => connection.close()
    heartbeat = setInterval(() => {
      if (Date.now() - lastMessage > 60000) connection.close()
      else if (connection.readyState === WebSocket.OPEN) connection.send(JSON.stringify({ type: 'ping' }))
    }, 20000)
  }
  function visible() { if (!document.hidden) { void refresh(); if (loadLocation) void refreshLocation() } }
  const stop = watch(() => auth.isAdmin, (active) => {
    if (!active) { clearTimeout(retry); clearInterval(heartbeat); socket?.close() }
  })
  onMounted(() => { void refresh(); connect(); document.addEventListener('visibilitychange', visible) })
  onUnmounted(() => {
    disposed = true; stop(); clearTimeout(retry); clearInterval(heartbeat); socket?.close()
    document.removeEventListener('visibilitychange', visible)
  })
}
