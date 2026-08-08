<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { activatePricingRule, archivePricingRule, createPricingRule, fetchPricingRules, updatePricingRule } from '@/api/pricing'
import { useAuthStore } from '@/stores/auth'
import type { FareChargeType, PricingRule, PricingRuleStatus, PricingRuleWriteRequest } from '@/types/api'

interface TierForm {
  chargeType: FareChargeType
  rangeStart: number
  rangeEnd: number | null
  unitSize: number
  unitPrice: number
}

const auth = useAuthStore()
const router = useRouter()
const status = ref<PricingRuleStatus | ''>('')
const page = ref(1)
const size = 10
const total = ref(0)
const rules = ref<PricingRule[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const expandedId = ref<number | null>(null)
const editing = ref<PricingRule | 'new' | null>(null)
const form = ref({ name: '', baseFare: 5, includedDistanceM: 2000, minimumFare: 5, tiers: [] as TierForm[] })
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

const statusLabel: Record<PricingRuleStatus, string> = { draft: '草稿', active: '已生效', inactive: '已停用', archived: '已归档' }
const typeLabel: Record<FareChargeType, string> = { DISTANCE: '里程', DRIVING_TIME: '行程时间', WAITING_TIME: '等待时间' }
const chargeTypes: FareChargeType[] = ['DISTANCE', 'DRIVING_TIME', 'WAITING_TIME']

function initialTiers(): TierForm[] {
  return [
    { chargeType: 'DISTANCE', rangeStart: 0, rangeEnd: 2000, unitSize: 1000, unitPrice: 0 },
    { chargeType: 'DISTANCE', rangeStart: 2000, rangeEnd: 12000, unitSize: 1000, unitPrice: 3 },
    { chargeType: 'DISTANCE', rangeStart: 12000, rangeEnd: null, unitSize: 1000, unitPrice: 1.2 },
    { chargeType: 'DRIVING_TIME', rangeStart: 0, rangeEnd: 1800, unitSize: 60, unitPrice: 0.2 },
    { chargeType: 'DRIVING_TIME', rangeStart: 1800, rangeEnd: null, unitSize: 60, unitPrice: 0.5 },
    { chargeType: 'WAITING_TIME', rangeStart: 0, rangeEnd: 300, unitSize: 60, unitPrice: 0 },
    { chargeType: 'WAITING_TIME', rangeStart: 300, rangeEnd: null, unitSize: 60, unitPrice: 0.5 },
  ]
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchPricingRules({ page: page.value, size, status: status.value || undefined })
    rules.value = result.records
    total.value = result.total
    page.value = result.page
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '加载计费规则失败。' }
  finally { loading.value = false }
}

function openCreate() {
  editing.value = 'new'
  form.value = { name: '默认即时叫车规则', baseFare: 5, includedDistanceM: 2000, minimumFare: 5, tiers: initialTiers() }
}
function openEdit(rule: PricingRule) {
  editing.value = rule
  form.value = { name: rule.name, baseFare: Number(rule.baseFare), includedDistanceM: rule.includedDistanceM, minimumFare: Number(rule.minimumFare), tiers: rule.tiers.map((tier) => ({ chargeType: tier.chargeType, rangeStart: tier.rangeStart, rangeEnd: tier.rangeEnd, unitSize: tier.unitSize, unitPrice: Number(tier.unitPrice) })) }
}
function closeModal() { editing.value = null }
function tiersFor(type: FareChargeType) { return form.value.tiers.filter((tier) => tier.chargeType === type) }
function addTier(type: FareChargeType) {
  const group = tiersFor(type)
  const last = group.at(-1)
  const start = last?.rangeEnd ?? last?.rangeStart ?? 0
  form.value.tiers.push({ chargeType: type, rangeStart: start, rangeEnd: null, unitSize: type === 'DISTANCE' ? 1000 : 60, unitPrice: 0 })
}
function removeTier(tier: TierForm) {
  if (tiersFor(tier.chargeType).length <= 1) { error.value = '每种收费类型至少需要一个分段。'; return }
  form.value.tiers.splice(form.value.tiers.indexOf(tier), 1)
}
function payload(): PricingRuleWriteRequest {
  const tiers = chargeTypes.flatMap((type) => tiersFor(type).map((tier, index) => {
    const rangeEnd = String(tier.rangeEnd ?? '').trim()
    return { chargeType: tier.chargeType, rangeStart: Number(tier.rangeStart), rangeEnd: rangeEnd ? Number(rangeEnd) : null, unitSize: Number(tier.unitSize), unitPrice: Number(tier.unitPrice), roundingMode: 'CEIL' as const, sortNo: (index + 1) * 10 }
  }))
  return { name: form.value.name.trim(), baseFare: Number(form.value.baseFare), includedDistanceM: Number(form.value.includedDistanceM), minimumFare: Number(form.value.minimumFare), tiers }
}
async function save() {
  if (!editing.value) return
  saving.value = true
  error.value = ''
  try {
    const data = payload()
    if (editing.value === 'new') await createPricingRule(data)
    else await updatePricingRule(editing.value.id, data)
    closeModal(); await load()
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '保存计费规则失败。' }
  finally { saving.value = false }
}
async function activate(rule: PricingRule, setAsDefault: boolean) {
  const action = setAsDefault ? '激活并设为默认规则' : '激活规则'
  if (!window.confirm(`确定${action}“${rule.name} v${rule.version}”吗？`)) return
  error.value = ''
  try { await activatePricingRule(rule.id, setAsDefault); await load() }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '激活规则失败。' }
}
async function archive(rule: PricingRule) {
  if (!window.confirm(`确定归档“${rule.name} v${rule.version}”吗？历史订单仍会保留该规则。`)) return
  error.value = ''
  try { await archivePricingRule(rule.id); await load() }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '归档规则失败。' }
}
function unitLabel(type: FareChargeType) { return type === 'DISTANCE' ? '米' : '秒' }
function logout() { auth.logout(); void router.replace({ name: 'login' }) }
onMounted(() => void load())
</script>

<template>
  <div class="admin-shell">
    <aside><div class="brand"><span>S</span><strong>StarX-Car</strong></div><nav><RouterLink to="/users">用户管理</RouterLink><RouterLink to="/drivers">司机审核</RouterLink><RouterLink to="/pricing">计费管理</RouterLink><RouterLink to="/profile">个人中心</RouterLink></nav><button class="logout" @click="logout">退出登录</button></aside>
    <main class="content">
      <header><div><p class="eyebrow">运营配置</p><h1>计费方式管理</h1><p>版本化维护起步价、最低消费及里程和时长分段。</p></div><div class="header-actions"><div class="operator">{{ auth.user?.username }}<small>管理员</small></div><button @click="openCreate">新建规则</button></div></header>
      <section class="panel">
        <div class="filters compact"><select v-model="status" @change="page = 1; load()"><option value="">全部规则状态</option><option value="draft">草稿</option><option value="active">已生效</option><option value="inactive">已停用</option><option value="archived">已归档</option></select></div>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="rule-list">
          <div v-if="loading" class="empty">正在加载...</div><div v-else-if="!rules.length" class="empty">暂无计费规则</div>
          <article v-for="rule in rules" :key="rule.id" class="rule-row">
            <button class="rule-summary" @click="expandedId = expandedId === rule.id ? null : rule.id"><span><strong>{{ rule.name }} <em>v{{ rule.version }}</em></strong><small>起步 {{ rule.baseFare }} 元 / 含 {{ rule.includedDistanceM }} 米 · 最低消费 {{ rule.minimumFare }} 元</small></span><span class="rule-meta"><span v-if="rule.defaultRule" class="status default">当前默认</span><span class="status" :class="rule.status">{{ statusLabel[rule.status] }}</span><b>{{ expandedId === rule.id ? '收起' : '明细' }}</b></span></button>
            <div v-if="expandedId === rule.id" class="rule-detail"><div v-for="type in chargeTypes" :key="type" class="tier-group"><h3>{{ typeLabel[type] }}</h3><div v-for="tier in rule.tiers.filter(item => item.chargeType === type)" :key="tier.id" class="tier-line"><span>{{ tier.rangeStart }} - {{ tier.rangeEnd ?? '不限' }} {{ unitLabel(type) }}</span><strong>{{ tier.unitPrice }} 元 / {{ tier.unitSize }} {{ unitLabel(type) }}</strong><span>不足单位向上取整</span></div></div><div class="rule-actions"><button v-if="rule.status === 'draft'" class="secondary" @click="openEdit(rule)">编辑草稿</button><button v-if="rule.status === 'draft'" class="secondary" @click="activate(rule, false)">仅激活</button><button v-if="rule.status === 'draft'" @click="activate(rule, true)">激活并设为默认</button><button v-if="!rule.defaultRule && rule.status !== 'archived'" class="danger-button" @click="archive(rule)">归档</button></div></div>
          </article>
        </div>
        <footer class="pagination"><span>共 {{ total }} 条规则</span><div><button :disabled="page <= 1 || loading" @click="page--; load()">上一页</button><span>第 {{ page }} / {{ totalPages }} 页</span><button :disabled="page >= totalPages || loading" @click="page++; load()">下一页</button></div></footer>
      </section>
    </main>

    <div v-if="editing" class="modal-backdrop" @click.self="closeModal"><form class="modal pricing-modal" @submit.prevent="save"><div class="modal-title"><div><h2>{{ editing === 'new' ? '新建计费规则' : '编辑计费规则' }}</h2><p>区间必须连续，每类最后一段的终点留空表示不限。</p></div><button type="button" class="close" @click="closeModal">×</button></div><div class="pricing-basics"><label>规则名称<input v-model="form.name" required maxlength="50" :disabled="editing !== 'new'" /></label><label>起步价（元）<input v-model.number="form.baseFare" required type="number" min="0" step="0.01" /></label><label>包含距离（米）<input v-model.number="form.includedDistanceM" required type="number" min="0" step="1" /></label><label>最低消费（元）<input v-model.number="form.minimumFare" required type="number" min="0" step="0.01" /></label></div>
      <section v-for="type in chargeTypes" :key="type" class="tier-editor"><div class="tier-heading"><h3>{{ typeLabel[type] }}分段</h3><button type="button" class="secondary" @click="addTier(type)">添加分段</button></div><div class="tier-grid tier-labels"><span>起点（{{ unitLabel(type) }}）</span><span>终点（留空不限）</span><span>计费单位</span><span>单位价格（元）</span><span></span></div><div v-for="tier in tiersFor(type)" :key="form.tiers.indexOf(tier)" class="tier-grid"><input v-model.number="tier.rangeStart" required type="number" min="0" step="1" /><input v-model.number="tier.rangeEnd" type="number" min="0" step="1" /><input v-model.number="tier.unitSize" required type="number" min="1" step="1" /><input v-model.number="tier.unitPrice" required type="number" min="0" step="0.01" /><button type="button" class="icon-delete" title="删除分段" @click="removeTier(tier)">×</button></div></section>
      <div class="modal-actions"><button type="button" class="secondary" @click="closeModal">取消</button><button :disabled="saving">{{ saving ? '保存中...' : '保存草稿' }}</button></div></form></div>
  </div>
</template>
