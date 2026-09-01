<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { changeMyPassword, fetchMyProfile, updateMyProfile } from '@/api/profile'
import { useAuthStore } from '@/stores/auth'
import type { Gender } from '@/types/api'

const auth = useAuthStore()
const router = useRouter()
const profile = ref({ username: '', email: '', gender: 'male' as Gender, realName: '', avatarUrl: '' })
const password = ref({ current: '', next: '', confirmation: '' })
const error = ref('')
const success = ref('')
const loading = ref(false)

async function load() {
  try {
    const user = await fetchMyProfile()
    auth.updateUser(user)
    profile.value = { username: user.username, email: user.email ?? '', gender: user.gender ?? 'male', realName: user.realName ?? '', avatarUrl: user.avatarUrl ?? '' }
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '加载个人资料失败。' }
}
async function saveProfile() {
  error.value = ''; success.value = ''; loading.value = true
  try {
    const user = await updateMyProfile({ username: profile.value.username.trim(), email: profile.value.email.trim() || null, gender: profile.value.gender, realName: profile.value.realName.trim() || null, avatarUrl: profile.value.avatarUrl.trim() || null })
    auth.updateUser(user); success.value = '个人资料已保存。'
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '保存个人资料失败。' } finally { loading.value = false }
}
async function savePassword() {
  error.value = ''; success.value = ''
  if (password.value.next !== password.value.confirmation) { error.value = '两次输入的新密码不一致。'; return }
  if (password.value.next.length < 6) { error.value = '新密码至少需要 6 个字符。'; return }
  loading.value = true
  try {
    await changeMyPassword(password.value.current, password.value.next)
    password.value = { current: '', next: '', confirmation: '' }
    success.value = '密码已修改。'
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '修改密码失败。' } finally { loading.value = false }
}
onMounted(() => void load())
</script>

<template>
  <section class="page-content">
      <header><div><p class="eyebrow">账户设置</p><h1>个人中心</h1><p>维护管理员个人资料与登录密码。</p></div><div class="operator">{{ auth.user?.username }}<small>管理员</small></div></header>
      <p v-if="error" class="error">{{ error }}</p><p v-if="success" class="success">{{ success }}</p>
      <div class="profile-grid">
        <form class="panel settings-form" @submit.prevent="saveProfile"><h2>个人资料</h2><label>用户名<input v-model="profile.username" required minlength="3" maxlength="50" /></label><label>真实姓名<input v-model="profile.realName" maxlength="50" /></label><label>头像地址<input v-model="profile.avatarUrl" type="url" maxlength="255" /></label><label>邮箱<input v-model="profile.email" type="email" maxlength="50" /></label><label>性别<select v-model="profile.gender"><option value="male">男</option><option value="female">女</option></select></label><button :disabled="loading">保存资料</button></form>
        <form class="panel settings-form" @submit.prevent="savePassword"><h2>修改密码</h2><label>原密码<input v-model="password.current" required type="password" autocomplete="current-password" /></label><label>新密码<input v-model="password.next" required type="password" minlength="6" maxlength="72" autocomplete="new-password" /></label><label>确认新密码<input v-model="password.confirmation" required type="password" minlength="6" maxlength="72" autocomplete="new-password" /></label><button :disabled="loading">修改密码</button></form>
      </div>
    </section>
</template>
