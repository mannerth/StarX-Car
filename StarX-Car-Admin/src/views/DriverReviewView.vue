<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchDrivers, reviewDriver } from '@/api/drivers'
import { useAuthStore } from '@/stores/auth'
import type { AdminDriver, DriverApprovalStatus } from '@/types/api'
import { driverApprovalLabel, userStatusLabel } from '@/utils/display'

const auth = useAuthStore()
const router = useRouter()
const keyword = ref('')
const approvalStatus = ref<DriverApprovalStatus | ''>('pending')
const page = ref(1)
const size = 10
const total = ref(0)
const drivers = ref<AdminDriver[]>([])
const loading = ref(false)
const reviewingId = ref<number | null>(null)
const error = ref('')
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

const workLabel = { offline: '未出车', online: '听单中', busy: '服务中' }

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchDrivers({ page: page.value, size, keyword: keyword.value.trim() || undefined, approvalStatus: approvalStatus.value || undefined })
    drivers.value = result.records
    total.value = result.total
    page.value = result.page
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '加载司机失败。' }
  finally { loading.value = false }
}

function search() { page.value = 1; void load() }
function formatDate(value: string | null) { return value ? value.replace('T', ' ') : '—' }
function maskIdCard(value: string | null) {
  if (!value || value.length < 8) return value || '—'
  return `${value.slice(0, 4)}**********${value.slice(-4)}`
}
async function review(driver: AdminDriver, decision: 'approved' | 'rejected') {
  const action = decision === 'approved' ? '通过' : '驳回'
  if (!window.confirm(`确定${action}司机“${driver.realName || driver.username || driver.id}”的申请吗？审核后不能再次修改。`)) return
  reviewingId.value = driver.id
  error.value = ''
  try { await reviewDriver(driver.id, decision); await load() }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '审核失败。' }
  finally { reviewingId.value = null }
}
onMounted(() => void load())
</script>

<template>
  <section class="page-content">
      <header><div><p class="eyebrow">运营管理</p><h1>司机审核</h1><p>核对司机身份、驾驶证和当前车辆资料。</p></div><div class="operator">{{ auth.user?.username }}<small>管理员</small></div></header>
      <section class="panel">
        <form class="filters" @submit.prevent="search"><input v-model="keyword" placeholder="搜索姓名、账号、手机号或证件号" /><select v-model="approvalStatus"><option value="">全部审核状态</option><option value="pending">待审核</option><option value="approved">已通过</option><option value="rejected">已驳回</option></select><button>查询</button></form>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="table-wrap"><table class="driver-table"><thead><tr><th>司机</th><th>联系方式</th><th>证件资料</th><th>当前车辆</th><th>状态</th><th>申请时间</th><th>操作</th></tr></thead><tbody>
          <tr v-if="loading"><td colspan="7" class="empty">正在加载...</td></tr><tr v-else-if="!drivers.length"><td colspan="7" class="empty">没有符合条件的司机</td></tr>
          <tr v-for="driver in drivers" :key="driver.id"><td><strong>{{ driver.realName || '未填写姓名' }}</strong><small>#{{ driver.id }} · {{ driver.username || '账号缺失' }}</small></td><td>{{ driver.displayPhone || driver.phoneNumber }}<small>账号：{{ driver.userStatus ? userStatusLabel[driver.userStatus] : '未知' }}</small></td><td>驾驶证 {{ driver.licenseNumber }}<small>身份证 {{ maskIdCard(driver.idCardNo) }}</small><small>有效期 {{ driver.licenseExpireDate || '未填写' }}</small></td><td>{{ driver.carNumber || '未绑定车辆' }}<small v-if="driver.carId">{{ driver.carBrand || '品牌未填' }} · {{ driver.carColor || '颜色未填' }}</small></td><td><span class="status" :class="driver.approvalStatus">{{ driverApprovalLabel[driver.approvalStatus] }}</span><small>{{ workLabel[driver.workStatus] }}</small></td><td>{{ formatDate(driver.createTime) }}</td><td class="actions"><template v-if="driver.approvalStatus === 'pending'"><button class="text-button" :disabled="reviewingId === driver.id" @click="review(driver, 'approved')">通过</button><button class="text-button danger" :disabled="reviewingId === driver.id" @click="review(driver, 'rejected')">驳回</button></template><span v-else class="muted">已处理</span></td></tr>
        </tbody></table></div>
        <footer class="pagination"><span>共 {{ total }} 位司机</span><div><button :disabled="page <= 1 || loading" @click="page--; load()">上一页</button><span>第 {{ page }} / {{ totalPages }} 页</span><button :disabled="page >= totalPages || loading" @click="page++; load()">下一页</button></div></footer>
      </section>
    </section>
</template>
