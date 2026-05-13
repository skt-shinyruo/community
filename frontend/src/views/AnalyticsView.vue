<template>
  <div class="page analytics-page">
    <UiCard flat class="admin-page-header">
      <UiPageHeader>
        <template #title>统计</template>
        <template #subtitle>用更安静的方式查看增长指标、时间范围和当前数据成熟度。</template>
        <template #actions>
          <UiButton variant="secondary" @click="query" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
        </template>
      </UiPageHeader>
    </UiCard>

    <UiState v-if="!auth.isAdminOrModerator" variant="error" class="analytics-state">无权限访问</UiState>
    <UiState v-else-if="error" variant="error" class="analytics-state">{{ error }}</UiState>

    <div v-else class="analytics-layout">
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
            <UiInput type="date" v-model="start" />
          </div>
          <div class="analytics-filter-arrow">→</div>
          <div class="analytics-filter-field">
            <div class="analytics-label">结束日期</div>
            <UiInput type="date" v-model="end" />
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
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { uv, dau } from '../api/services/analyticsService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiState from '../components/ui/UiState.vue'

const emit = defineEmits(['trace'])
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

async function query() {
  if (!auth.isAdminOrModerator) return
  error.value = ''
  loading.value = true
  try {
    const [uvResp, dauResp] = await Promise.all([uv({ start: start.value, end: end.value }), dau({ start: start.value, end: end.value })])
    uResult.value = uvResp?.data ?? 0
    dResult.value = dauResp?.data ?? 0
    emit('trace', uvResp?.traceId || dauResp?.traceId || '')
  } catch (e) {
    error.value = e?.message || '加载统计失败'
    uResult.value = '—'
    dResult.value = '—'
  } finally {
    loading.value = false
  }
}
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
