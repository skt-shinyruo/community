<template>
  <div class="page analytics-page">
    <UiCard flat class="admin-page-header">
      <UiPageHeader>
        <template #title>统计</template>
        <template #subtitle>用更安静的方式查看增长指标、时间范围和当前数据成熟度。</template>
        <template #actions>
          <UiButton variant="secondary" @click="query" :disabled="loading">{{ loading ? '正在加载…' : '刷新' }}</UiButton>
        </template>
      </UiPageHeader>
    </UiCard>

    <UiState v-if="!auth.isAdminOrModerator" variant="error" class="analytics-state">无权限访问</UiState>
    <template v-else>
    <UiState v-if="error" variant="error" class="analytics-state">{{ error }}</UiState>

    <div class="analytics-layout">
      <UiCard class="analytics-filter-card">
        <div class="analytics-filter-head">
          <div>
            <div class="analytics-eyebrow">Scope</div>
            <div class="analytics-title">数据范围</div>
          </div>
        </div>
        <div class="analytics-filter-grid">
          <div class="analytics-filter-field">
            <div class="analytics-label">开始日期</div>
            <input v-model="start" class="input" type="date" />
          </div>
          <div class="analytics-filter-arrow">→</div>
          <div class="analytics-filter-field">
            <div class="analytics-label">结束日期</div>
            <input v-model="end" class="input" type="date" />
          </div>
        </div>
      </UiCard>

      <div class="stats-grid">
        <div class="stat-box">
          <div class="stat-meta">
            <div class="stat-kicker">Traffic</div>
            <div class="stat-name">UV（独立访客）</div>
          </div>
          <div class="stat-num">{{ uvResult }}</div>
        </div>

        <div class="stat-box">
          <div class="stat-meta">
            <div class="stat-kicker">Activity</div>
            <div class="stat-name">DAU（日活）</div>
          </div>
          <div class="stat-num">{{ dauResult }}</div>
        </div>
      </div>

      <UiCard class="analytics-insight-card">
        <div class="analytics-insight-head">
          <div>
            <div class="analytics-eyebrow">Freshness</div>
            <div class="analytics-title">数据新鲜度</div>
          </div>
        </div>
        <div class="analytics-insight-copy muted">
          当前读数按所选区间刷新；暂不可用的趋势和对比能力不会展示。
        </div>
      </UiCard>
    </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { uv, dau } from '../api/services/analyticsService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiState from '../components/ui/UiState.vue'
import { normalizeOpaqueId } from '../utils/opaqueId'
import { settleNamedRequests } from '../utils/settledRequests'
import { createLatestRequestTracker } from '../utils/latestRequest'

const auth = useAuthStore()

const today = new Date().toISOString().slice(0, 10)
const start = ref(today)
const end = ref(today)

const loading = ref(false)
const error = ref('')
const uResult = ref('-')
const dResult = ref('-')

const uvResult = computed(() => uResult.value)
const dauResult = computed(() => dResult.value)
const sessionScope = computed(() => [
  auth.tokenGeneration,
  normalizeOpaqueId(auth.userId),
  [...auth.authorities].sort().join(',')
].join(':'))
const requestTracker = createLatestRequestTracker({
  getScope: () => `${sessionScope.value}:${start.value}:${end.value}`
})

function isCurrentQuery(requestHandle) {
  return requestTracker.isCurrent(requestHandle) &&
    auth.authed &&
    auth.isAdminOrModerator
}

async function query() {
  if (!auth.authed || !auth.isAdminOrModerator) return
  const requestHandle = requestTracker.begin()
  const request = { start: start.value, end: end.value }
  error.value = ''
  loading.value = true
  try {
    const outcome = await settleNamedRequests({ uv: () => uv(request), dau: () => dau(request) })
    if (!isCurrentQuery(requestHandle)) return
    if (outcome.results.uv.ok) uResult.value = outcome.results.uv.value?.data ?? 0
    else uResult.value = '—'
    if (outcome.results.dau.ok) dResult.value = outcome.results.dau.value?.data ?? 0
    else dResult.value = '—'
    if (!outcome.allSucceeded) {
      const firstError = outcome.results[outcome.failedKeys[0]]?.error
      error.value = outcome.anySucceeded
        ? `部分统计加载失败：${firstError?.message || '请稍后重试'}`
        : (firstError?.message || '加载统计失败')
    }
  } finally {
    if (isCurrentQuery(requestHandle)) loading.value = false
  }
}

function invalidateResults() {
  requestTracker.invalidate()
  loading.value = false
  error.value = ''
  uResult.value = '-'
  dResult.value = '-'
}

watch([start, end], invalidateResults)
watch(sessionScope, invalidateResults)
onBeforeUnmount(() => {
  requestTracker.invalidate()
})
</script>

<style scoped>
.analytics-page {
  max-width: 960px;
}

.analytics-state {
  margin-top: 12px;
}

.analytics-layout {
  display: grid;
  gap: 18px;
}

.analytics-filter-card,
.analytics-insight-card {
  display: grid;
  gap: 14px;
}

.analytics-filter-head,
.analytics-insight-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.analytics-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-3);
  margin-bottom: 4px;
}

.analytics-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--text-1);
}

.analytics-filter-grid {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 16px;
  align-items: center;
}

.analytics-filter-field {
  display: grid;
  gap: 6px;
}

.analytics-label {
  font-size: 12px;
  color: var(--text-3);
}

/* 退役全局 components.css 中仍被本页引用的 .input 规则，按行为等价原样迁入 scoped（DOM 类名不变）。 */
.input {
  width: 100%;
  height: var(--control-height);
  padding: 0 var(--control-padding-x);
  border: 1px solid var(--border);
  border-radius: 14px;
  outline: none;
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
  color: var(--text-1);
  font-size: var(--text-sm);
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
  box-shadow: var(--shadow-sm);
}

.input:hover {
  border-color: var(--border-strong);
}

.input:focus {
  border-color: var(--accent);
}

.input:focus-visible {
  box-shadow: var(--shadow-sm), var(--focus-ring);
}

.input::placeholder {
  color: var(--text-3);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
}

.stat-box {
  background: color-mix(in srgb, var(--admin-surface) 88%, var(--surface) 12%);
  border: 1px solid var(--admin-border);
  border-radius: var(--radius-lg);
  padding: 18px;
  display: grid;
  gap: 10px;
}

.stat-meta {
  display: grid;
  gap: 4px;
}

.stat-kicker {
  font-size: 11px;
  color: var(--text-3);
  text-transform: uppercase;
  letter-spacing: 0.12em;
  font-weight: 700;
}

.stat-num {
  font-size: 34px;
  font-weight: 800;
  line-height: 1;
  color: var(--text-1);
}

.stat-name {
  font-size: 14px;
  color: var(--text-2);
  font-weight: 600;
}

.analytics-insight-copy {
  font-size: 14px;
  line-height: 1.7;
}

.analytics-filter-arrow {
  font-size: 20px;
  color: var(--text-3);
}

@media (max-width: 768px) {
  .analytics-filter-grid {
    grid-template-columns: 1fr;
  }

  .analytics-filter-arrow {
    display: none;
  }
}
</style>
