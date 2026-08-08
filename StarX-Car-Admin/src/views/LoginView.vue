<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    await router.replace({ name: 'users' })
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '登录失败，请重试。'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <form class="login-card" @submit.prevent="submit">
      <div class="brand"><span>S</span><div><strong>StarX-Car</strong><small>管理后台</small></div></div>
      <h1>管理员登录</h1>
      <p>使用已创建的管理员账号登录。</p>
      <label>用户名<input v-model="username" required minlength="3" autocomplete="username" /></label>
      <label>密码<input v-model="password" required type="password" autocomplete="current-password" /></label>
      <p v-if="error" class="error">{{ error }}</p>
      <button :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
    </form>
  </main>
</template>
