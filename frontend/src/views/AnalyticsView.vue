<template>
  <div class="page reading">
    <UiCard>
      <UiPageHeader>
        <template #title>统计</template>
        <template #subtitle>社区增长概览</template>
        <template #actions>
          <UiButton variant="secondary" @click="query" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
        </template>
      </UiPageHeader>

      <UiEmpty v-if="!auth.isAdminOrModerator" type="error" style="margin-top: 12px">无权限访问</UiEmpty>

      <div v-else style="margin-top: 16px">
        <!-- Date Filter -->
        <UiCard style="margin-bottom: 24px">
           <div class="row" style="align-items: center; gap: 16px; flex-wrap: wrap">
              <div class="stack" style="gap: 4px; flex: 1">
                 <div class="muted" style="font-size: 12px">开始日期</div>
                 <UiInput type="date" v-model="start" style="width: 100%" />
              </div>
              <div class="arrow">→</div>
              <div class="stack" style="gap: 4px; flex: 1">
                 <div class="muted" style="font-size: 12px">结束日期</div>
                 <UiInput type="date" v-model="end" style="width: 100%" />
              </div>
           </div>
        </UiCard>

        <!-- Stats Grid -->
        <div class="stats-grid">
           <!-- Unique Visitors -->
           <div class="stat-box">
              <div class="stat-icon purple">
                 <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><path d="M23 21v-2a4 4 0 0 0-3-3.87"></path><path d="M16 3.13a4 4 0 0 1 0 7.75"></path></svg>
              </div>
              <div class="stat-main">
                 <div class="stat-num">{{ uvResult }}</div>
                 <div class="stat-name">UV（独立访客）</div>
              </div>
           </div>

           <!-- DAU -->
           <div class="stat-box">
              <div class="stat-icon blue">
                 <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"></polyline></svg>
              </div>
              <div class="stat-main">
                 <div class="stat-num">{{ dauResult }}</div>
                 <div class="stat-name">DAU（日活）</div>
              </div>
           </div>
        </div>
        
        <!-- Placeholder Chart -->
        <UiCard style="margin-top: 24px; min-height: 260px; display: flex; align-items: center; justify-content: center">
           <div class="muted" style="font-size: 14px">图表占位（待接入）</div>
        </UiCard>
      </div>
    </UiCard>
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
import UiEmpty from '../components/ui/UiEmpty.vue'

const emit = defineEmits(['trace'])
const auth = useAuthStore()

const today = new Date().toISOString().slice(0, 10)
const start = ref(today)
const end = ref(today)

const loading = ref(false)
const uResult = ref('-')
const dResult = ref('-')

const uvResult = computed(() => uResult.value)
const dauResult = computed(() => dResult.value)

async function query() {
  if (!auth.isAdminOrModerator) return
  loading.value = true
  try {
    const [uvResp, dauResp] = await Promise.all([uv({ start: start.value, end: end.value }), dau({ start: start.value, end: end.value })])
    uResult.value = uvResp?.data ?? 0
    dResult.value = dauResp?.data ?? 0
    emit('trace', uvResp?.traceId || dauResp?.traceId || '')
  } catch (e) {
    uResult.value = '—'
    dResult.value = '—'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.stats-grid {
   display: grid;
   grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
   gap: 24px;
}
.stat-box {
   background: var(--surface);
   border-radius: var(--radius-lg);
   padding: 24px;
   box-shadow: var(--shadow-sm);
   display: flex;
   align-items: center;
   gap: 20px;
}
.stat-icon {
   width: 60px; height: 60px;
   border-radius: 16px;
   display: flex; align-items: center; justify-content: center;
   color: white;
}
.stat-icon.purple { background: linear-gradient(135deg, #A855F7, #9333EA); }
.stat-icon.blue { background: linear-gradient(135deg, #3B82F6, #2563EB); }

.stat-num { font-size: 32px; font-weight: 800; line-height: 1; margin-bottom: 4px; }
.stat-name { font-size: 14px; color: var(--muted); font-weight: 500; }
.arrow { font-size: 20px; color: var(--muted); }
</style>
