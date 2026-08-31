<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchOrderMetrics, fetchOrders } from '@/api/orders'
import { useAuthStore } from '@/stores/auth'
import type { AdminOrderMetrics, AdminOrderSummary, OrderStatus, OrderType } from '@/types/api'

const auth = useAuthStore(); const router = useRouter()
const keyword = ref(''); const orderType = ref<OrderType | ''>(''); const status = ref<OrderStatus | ''>('')
const page = ref(1); const size = 20; const total = ref(0); const records = ref<AdminOrderSummary[]>([]); const metrics = ref<AdminOrderMetrics | null>(null)
const loading = ref(false); const error = ref(''); const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
let timer: number | undefined
const labels: Record<OrderStatus, string> = { SCHEDULED: '等待派单', PENDING: '待抢单', ACCEPTED: '已接单', DRIVER_ARRIVED: '司机已到达', IN_SERVICE: '服务中', COMPLETED: '已完成', CANCELLED: '已取消' }
function format(value: string | null) { return value ? value.replace('T', ' ') : '—' }
function money(value: number) { return `¥${Number(value).toFixed(2)}` }
async function load() { loading.value = true; error.value = ''; try { const [orders, summary] = await Promise.all([fetchOrders({ page: page.value, size, keyword: keyword.value.trim() || undefined, orderType: orderType.value || undefined, status: status.value || undefined }), fetchOrderMetrics()]); records.value = orders.records; total.value = orders.total; page.value = orders.page; metrics.value = summary } catch (reason) { error.value = reason instanceof Error ? reason.message : '加载订单失败。' } finally { loading.value = false } }
function search() { page.value = 1; void load() }
function open(orderNo: string) { void router.push({ name: 'order-detail', params: { orderNo } }) }
function logout() { auth.logout(); void router.replace({ name: 'login' }) }
function visible() { if (!document.hidden) void load() }
onMounted(() => { void load(); timer = window.setInterval(() => { if (!document.hidden) void load() }, 30000); document.addEventListener('visibilitychange', visible) })
onUnmounted(() => { if (timer) window.clearInterval(timer); document.removeEventListener('visibilitychange', visible) })
</script>

<template>
  <div class="admin-shell"><aside><div class="brand"><span>S</span><strong>StarX-Car</strong></div><nav><RouterLink to="/orders">订单监控</RouterLink><RouterLink to="/reservations">预约运营</RouterLink><RouterLink to="/users">用户管理</RouterLink><RouterLink to="/drivers">司机审核</RouterLink><RouterLink to="/pricing">计费管理</RouterLink><RouterLink to="/profile">个人中心</RouterLink></nav><button class="logout" @click="logout">退出登录</button></aside>
    <main class="content"><header><div><p class="eyebrow">运营管理</p><h1>订单监控</h1><p>查看即时与预约订单的状态、费用和运营风险。</p></div><div class="operator">{{ auth.user?.username }}<small>管理员</small></div></header>
      <section v-if="metrics" class="metrics"><div><small>订单总数</small><strong>{{ metrics.total }}</strong></div><div><small>预约订单</small><strong>{{ metrics.scheduled }}</strong></div><div><small>待派单</small><strong>{{ metrics.scheduledWaitingDispatch }}</strong></div><div><small>预约风险</small><strong class="risk">{{ metrics.reservationAtRisk }}</strong></div><div><small>已完成金额</small><strong>{{ money(metrics.completedFare) }}</strong></div></section>
      <section class="panel"><form class="filters order-filters" @submit.prevent="search"><input v-model="keyword" placeholder="订单号、乘客或司机" /><select v-model="orderType"><option value="">全部订单类型</option><option value="INSTANT">即时订单</option><option value="SCHEDULED">预约订单</option></select><select v-model="status"><option value="">全部状态</option><option v-for="(label, value) in labels" :key="value" :value="value">{{ label }}</option></select><button>查询</button></form><p v-if="error" class="error">{{ error }}</p>
        <div class="table-wrap"><table class="order-table"><thead><tr><th>订单</th><th>乘客 / 司机</th><th>行程</th><th>预约 / 派单</th><th>状态</th><th>金额</th><th>操作</th></tr></thead><tbody><tr v-if="loading"><td colspan="7" class="empty">正在加载...</td></tr><tr v-else-if="!records.length"><td colspan="7" class="empty">没有符合条件的订单</td></tr><tr v-for="order in records" :key="order.orderNo"><td><strong>{{ order.orderNo }}</strong><small>{{ order.orderType === 'SCHEDULED' ? '预约订单' : '即时订单' }}</small><small>{{ format(order.createdTime) }}</small></td><td>{{ order.passengerName || '乘客' }}<small>{{ order.passengerDisplayPhone || '—' }}</small><template v-if="order.driverName || order.driverDisplayPhone"><hr />{{ order.driverName || '司机' }}<small>{{ order.driverDisplayPhone }}</small></template></td><td>{{ order.pickupName }}<small>→ {{ order.destinationName }}</small></td><td>{{ order.scheduledPickupTime ? format(order.scheduledPickupTime) : '—' }}<small>派单：{{ format(order.dispatchTime) }}</small></td><td><span class="status" :class="order.status.toLowerCase()">{{ labels[order.status] }}</span></td><td>{{ money(order.totalFare) }}<small>{{ order.estimatedFare ? '预估' : '已结算' }}</small></td><td><button class="text-button" @click="open(order.orderNo)">查看详情</button></td></tr></tbody></table></div>
        <footer class="pagination"><span>共 {{ total }} 笔订单</span><div><button :disabled="page <= 1 || loading" @click="page--; load()">上一页</button><span>第 {{ page }} / {{ totalPages }} 页</span><button :disabled="page >= totalPages || loading" @click="page++; load()">下一页</button></div></footer></section>
    </main></div>
</template>
