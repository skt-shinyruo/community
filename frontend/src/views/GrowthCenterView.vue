<template>
  <div class="page growth-page">
    <UiBreadcrumb />

    <section class="growth-head">
      <div class="growth-hero-actions">
        <UiButton variant="secondary" :disabled="loading || submitting" @click="reload">
          {{ loading ? '刷新中…' : '刷新' }}
        </UiButton>
        <UiButton :disabled="submitting || header.checkedInToday" @click="handleCheckIn">
          {{ header.checkedInToday ? '今日已签到' : (submitting ? '签到中…' : '立即签到') }}
        </UiButton>
      </div>
      <div class="growth-hero-grid">
        <div v-if="header.showUserLevelCard" class="growth-hero-card">
          <span class="growth-hero-label">用户等级</span>
          <strong>{{ header.userLevelLabel }}</strong>
          <p>{{ header.heroText }}</p>
        </div>
        <div class="growth-hero-card">
          <span class="growth-hero-label">奖励余额</span>
          <strong>{{ header.rewardBalance }}</strong>
          <p>{{ header.balanceText }}</p>
        </div>
        <div class="growth-hero-card">
          <span class="growth-hero-label">签到节奏</span>
          <strong>{{ header.currentStreak }}</strong>
          <p>{{ header.streakText }}</p>
        </div>
      </div>
    </section>

    <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
    <div v-else-if="loading && !ready" class="muted growth-state">正在加载成长中心…</div>

    <div v-else class="growth-layout">
      <TaskCenterView :groups="state.groups" :loading="loading" />
      <SignInCalendarView
        :year="calendarYear"
        :month="calendarMonth"
        :checked-in-dates="calendar.checkedInDates"
        :checked-in-today="header.checkedInToday"
        :current-streak="header.currentStreak"
        :max-streak="header.maxStreak"
        :total-check-in-days="header.totalCheckInDays"
        :biz-date="bizDate"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  getCheckInCalendar,
  getCheckInStatus,
  getGrowthSummary,
  getGrowthTasks,
  submitCheckIn
} from '../api/services/growthService'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import { buildGrowthCenterState } from './growthCenterState'
import SignInCalendarView from './SignInCalendarView.vue'
import TaskCenterView from './TaskCenterView.vue'

function todayString() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

const bizDate = ref(todayString())
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const summary = ref({})
const checkInStatus = ref({})
const tasks = ref({ bizDate: bizDate.value, items: [] })
const calendar = ref({ year: Number(bizDate.value.slice(0, 4)), month: Number(bizDate.value.slice(5, 7)), checkedInDates: [] })

const state = computed(() =>
  buildGrowthCenterState({
    summary: summary.value,
    checkInStatus: checkInStatus.value,
    tasks: tasks.value
  })
)

const header = computed(() => state.value.header)
const ready = computed(() => Array.isArray(state.value.groups))
const calendarYear = computed(() => Number(bizDate.value.slice(0, 4)))
const calendarMonth = computed(() => Number(bizDate.value.slice(5, 7)))

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const [{ data: growthSummary }, { data: status }, { data: taskCenter }, { data: calendarView }] = await Promise.all([
      getGrowthSummary(),
      getCheckInStatus({ date: bizDate.value }),
      getGrowthTasks({ date: bizDate.value }),
      getCheckInCalendar({ year: calendarYear.value, month: calendarMonth.value })
    ])

    summary.value = growthSummary || {}
    checkInStatus.value = status || {}
    tasks.value = taskCenter || { bizDate: bizDate.value, items: [] }
    calendar.value = calendarView || { year: calendarYear.value, month: calendarMonth.value, checkedInDates: [] }
  } catch (e) {
    error.value = e?.message || '加载成长中心失败'
  } finally {
    loading.value = false
  }
}

async function handleCheckIn() {
  if (header.value.checkedInToday || submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    await submitCheckIn({ date: bizDate.value })
    await reload()
  } catch (e) {
    error.value = e?.message || '签到失败'
  } finally {
    submitting.value = false
  }
}

onMounted(reload)
</script>

<style scoped>
.growth-page {
  max-width: 1100px;
  margin: 0 auto;
  gap: var(--space-5);
}

.growth-head {
  display: grid;
  gap: var(--space-4);
}

.growth-hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.growth-hero-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.growth-hero-card {
  padding: 18px 20px;
  border-radius: var(--radius-lg);
  border: 1px solid color-mix(in srgb, var(--border) 84%, var(--accent) 16%);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--surface) 92%, white 8%), var(--surface));
  display: grid;
  gap: 6px;
}

.growth-hero-card strong {
  font-size: clamp(1.75rem, 3vw, 2.35rem);
  line-height: 1;
}

.growth-hero-card p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.55;
}

.growth-hero-label {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 700;
}

.growth-state {
  padding: 24px 0;
}

.growth-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 18px;
  align-items: start;
}

@media (max-width: 960px) {
  .growth-hero-grid,
  .growth-layout {
    grid-template-columns: 1fr;
  }
}
</style>
