<script setup lang="ts">
import { computed, ref } from 'vue'
import { useOrderRealtime } from '@/api/realtime'
import { useRouter } from 'vue-router'
import { dispatchReservation, fetchOrders } from '@/api/orders'
import { useAuthStore } from '@/stores/auth'
import type { AdminOrderSummary } from '@/types/api'
import { orderStatusLabel } from '@/utils/display'

const auth = useAuthStore(); const router = useRouter(); const page = ref(1); const size = 20; const total = ref(0); const records = ref<AdminOrderSummary[]>([]); const loading = ref(false); const error = ref(''); const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
function format(value: string | null) { return value ? value.replace('T', ' ') : '—' }
function countdown(value: string | null) { if (!value) return '—'; const seconds = Math.round((new Date(value.replace(' ', 'T')).getTime() - Date.now()) / 1000); if (seconds <= 0) return '已到预约时间'; const h = Math.floor(seconds / 3600); const m = Math.floor((seconds % 3600) / 60); return `${h ? `${h}小时` : ''}${m}分钟后` }
async function load() { loading.value = true; error.value = ''; try { const result = await fetchOrders({ page: page.value, size, orderType: 'SCHEDULED' }); records.value = result.records; total.value = result.total; page.value = result.page } catch (reason) { error.value = reason instanceof Error ? reason.message : '加载预约订单失败。' } finally { loading.value = false } }
async function dispatch(order: AdminOrderSummary) { const reason = window.prompt(`提前派发预约订单 ${order.orderNo} 的原因：`); if (!reason?.trim()) return; try { await dispatchReservation(order.orderNo, reason.trim()); await load() } catch (reason) { error.value = reason instanceof Error ? reason.message : '提前派单失败。' } }
function open(orderNo: string) { void router.push({ name: 'order-detail', params: { orderNo } }) };
useOrderRealtime(load)
</script>

<template><section class="page-content"><header><div><p class="eyebrow">预约运营</p><h1>预约订单运营</h1><p>关注等待派单、待抢和接近预约时间的订单。</p></div><div class="operator">{{ auth.user?.username }}<small>订单变化时实时更新</small></div></header><section class="panel"><p v-if="error" class="error">{{ error }}</p><div class="table-wrap"><table class="order-table"><thead><tr><th>预约时间</th><th>乘客 / 行程</th><th>当前状态</th><th>派单时间</th><th>司机</th><th>操作</th></tr></thead><tbody><tr v-if="loading"><td colspan="6" class="empty">正在加载...</td></tr><tr v-else-if="!records.length"><td colspan="6" class="empty">暂无预约订单</td></tr><tr v-for="order in records" :key="order.orderNo"><td><strong>{{ format(order.scheduledPickupTime) }}</strong><small :class="countdown(order.scheduledPickupTime).includes('已到') ? 'risk' : ''">{{ countdown(order.scheduledPickupTime) }}</small></td><td>{{ order.passengerName || '乘客' }}<small>{{ order.passengerDisplayPhone || '—' }} · {{ order.pickupName }} → {{ order.destinationName }}</small></td><td><span class="status" :class="order.status.toLowerCase()">{{ orderStatusLabel[order.status] }}</span></td><td>{{ format(order.dispatchTime) }}</td><td>{{ order.driverName || '暂未接单' }}<small>{{ order.driverDisplayPhone || '' }}</small></td><td class="actions"><button v-if="order.status === 'SCHEDULED'" class="text-button" @click="dispatch(order)">提前派单</button><button class="text-button" @click="open(order.orderNo)">详情</button></td></tr></tbody></table></div><footer class="pagination"><span>共 {{ total }} 笔预约订单</span><div><button :disabled="page <= 1 || loading" @click="page--; load()">上一页</button><span>第 {{ page }} / {{ totalPages }} 页</span><button :disabled="page >= totalPages || loading" @click="page++; load()">下一页</button></div></footer></section></section>
</template>
