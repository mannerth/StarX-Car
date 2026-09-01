<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { deleteUser, fetchUsers, resetUserPassword, updateUser } from '@/api/users'
import { useAuthStore } from '@/stores/auth'
import type { Gender, User, UserStatus, UserType } from '@/types/api'
import { userStatusLabel, userTypeLabel } from '@/utils/display'

const auth = useAuthStore()
const router = useRouter()
const keyword = ref('')
const type = ref<UserType | ''>('')
const page = ref(1)
const size = 10
const total = ref(0)
const users = ref<User[]>([])
const loading = ref(false)
const error = ref('')
const editing = ref<User | null>(null)
const form = ref({ username: '', email: '', gender: 'male' as Gender, realName: '', avatarUrl: '', status: 'active' as UserStatus })
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

const genderLabel: Record<Gender, string> = { male: '男', female: '女' }

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchUsers({ page: page.value, size, keyword: keyword.value.trim(), type: type.value || undefined })
    users.value = result.records
    total.value = result.total
    page.value = result.page
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '加载用户失败。'
  } finally { loading.value = false }
}

function search() { page.value = 1; void load() }
function format(value: string | null) { return value ? value.replace('T', ' ') : '—' }
function edit(user: User) {
  editing.value = user
  form.value = { username: user.username, email: user.email ?? '', gender: user.gender ?? 'male', realName: user.realName ?? '', avatarUrl: user.avatarUrl ?? '', status: user.status }
}
function closeModal() { editing.value = null }
async function save() {
  if (!editing.value) return
  error.value = ''
  try {
    await updateUser(editing.value.id, { ...form.value, username: form.value.username.trim(), email: form.value.email.trim() || null, realName: form.value.realName.trim() || null, avatarUrl: form.value.avatarUrl.trim() || null })
    closeModal(); await load()
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '保存失败。' }
}
async function remove(user: User) {
  if (!window.confirm(`确定删除“${user.username}”吗？该操作会同时删除其乘客/司机资料，且不可恢复。`)) return
  error.value = ''
  try {
    await deleteUser(user.id)
    if (users.value.length === 1 && page.value > 1) page.value--
    await load()
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '删除失败。' }
}
async function resetPassword(user: User) {
  if (!window.confirm(`确定将“${user.username}”的密码重置为系统默认密码吗？`)) return
  error.value = ''
  try {
    await resetUserPassword(user.id)
    window.alert('密码已重置为系统默认密码。')
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '重置密码失败。' }
}
onMounted(() => void load())
</script>

<template>
  <section class="page-content">
      <header><div><p class="eyebrow">系统管理</p><h1>用户管理</h1><p>查询、维护及删除乘客与司机账号。</p></div><div class="operator">{{ auth.user?.username }}<small>管理员</small></div></header>
      <section class="panel">
        <form class="filters" @submit.prevent="search"><input v-model="keyword" placeholder="搜索用户名或邮箱" /><select v-model="type"><option value="">全部用户类型</option><option value="admin">管理员</option><option value="passenger">乘客</option><option value="driver">司机</option></select><button>查询</button></form>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="table-wrap"><table><thead><tr><th>ID</th><th>用户名 / 姓名</th><th>邮箱</th><th>性别</th><th>类型</th><th>状态</th><th>最近登录</th><th>操作</th></tr></thead><tbody><tr v-if="loading"><td colspan="8" class="empty">正在加载…</td></tr><tr v-else-if="!users.length"><td colspan="8" class="empty">没有符合条件的用户</td></tr><tr v-for="user in users" :key="user.id"><td>#{{ user.id }}</td><td>{{ user.username }}<small>{{ user.realName || '未实名' }}</small></td><td>{{ user.email || '—' }}</td><td>{{ user.gender ? genderLabel[user.gender] : '—' }}</td><td><span class="tag" :class="user.type">{{ userTypeLabel[user.type] }}</span></td><td>{{ userStatusLabel[user.status] }}</td><td>{{ format(user.lastLoginTime) }}</td><td class="actions"><button class="text-button" @click="edit(user)">编辑</button><button class="text-button" @click="resetPassword(user)">重置密码</button><button v-if="user.type !== 'admin'" class="text-button danger" @click="remove(user)">删除</button></td></tr></tbody></table></div>
        <footer class="pagination"><span>共 {{ total }} 位用户</span><div><button :disabled="page <= 1 || loading" @click="page--; load()">上一页</button><span>第 {{ page }} / {{ totalPages }} 页</span><button :disabled="page >= totalPages || loading" @click="page++; load()">下一页</button></div></footer>
      </section>
    </section>
    <div v-if="editing" class="modal-backdrop" @click.self="closeModal"><form class="modal" @submit.prevent="save"><div class="modal-title"><h2>编辑用户</h2><button type="button" class="close" @click="closeModal">×</button></div><label>用户名<input v-model="form.username" required minlength="3" maxlength="50" /></label><label>真实姓名<input v-model="form.realName" maxlength="50" /></label><label>头像地址<input v-model="form.avatarUrl" type="url" maxlength="255" /></label><label>邮箱<input v-model="form.email" type="email" maxlength="50" /></label><label>性别<select v-model="form.gender"><option value="male">男</option><option value="female">女</option></select></label><label>账号状态<select v-model="form.status"><option value="active">正常</option><option value="disabled">停用</option><option value="cancelled">已注销</option></select></label><div class="modal-actions"><button type="button" class="secondary" @click="closeModal">取消</button><button>保存更改</button></div></form></div>
</template>
