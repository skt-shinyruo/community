<template>
  <div class="page" style="max-width: 800px; margin: 0 auto">
    <UiPageHeader>
        <template #title>Analytics Dashboard</template>
        <template #subtitle>Overview of community growth.</template>
        <template #actions>
           <UiButton @click="query" :disabled="loading">{{ loading ? 'Loading...' : 'Refresh' }}</UiButton>
        </template>
    </UiPageHeader>

    <div v-if="!auth.isAdminOrModerator" class="error" style="margin-top: 12px">Access Denied.</div>
    
    <div v-else style="margin-top: 24px">
        <!-- Date Filter -->
        <UiCard style="margin-bottom: 24px">
           <div class="row" style="align-items: center; gap: 16px; flex-wrap: wrap">
              <div class="stack" style="gap: 4px; flex: 1">
                 <div class="muted" style="font-size: 12px">Start Date</div>
                 <UiInput type="date" v-model="start" style="width: 100%" />
              </div>
              <div class="arrow">→</div>
              <div class="stack" style="gap: 4px; flex: 1">
                 <div class="muted" style="font-size: 12px">End Date</div>
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
                 <div class="stat-name">Total Unique Visitors</div>
              </div>
           </div>

           <!-- DAU -->
           <div class="stat-box">
              <div class="stat-icon blue">
                 <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"></polyline></svg>
              </div>
              <div class="stat-main">
                 <div class="stat-num">{{ dauResult }}</div>
                 <div class="stat-name">Daily Active Users</div>
              </div>
           </div>
        </div>
        
        <!-- Placeholder Chart -->
        <UiCard style="margin-top: 24px; min-height: 300px; display: flex; align-items: center; justify-content: center; background: linear-gradient(180deg, var(--surface) 0%, var(--bg) 100%)">
           <div class="muted" style="font-size: 14px; opacity: 0.5">Chart Visualization Placeholder</div>
        </UiCard>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { uv, dau } from '../api/services/analyticsService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiInput from '../components/ui/UiInput.vue'

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
    uResult.value = 'Err'
    dResult.value = 'Err'
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
