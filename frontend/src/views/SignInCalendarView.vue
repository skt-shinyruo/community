<template>
  <UiCard class="growth-panel calendar-panel">
    <div class="growth-panel-head">
      <div>
        <div class="growth-eyebrow">Check-In</div>
        <h2>签到日历</h2>
        <p>签到状态和本月节奏放在同一张卡上，避免用户只看到按钮却看不到历史趋势。</p>
      </div>
      <div class="calendar-panel-meta">
        <strong>{{ checkedInToday ? '已签到' : '待签到' }}</strong>
        <span>{{ monthLabel }}</span>
      </div>
    </div>

    <div class="calendar-summary">
      <div class="calendar-summary-card">
        <span>当前连续</span>
        <strong>{{ currentStreak }}</strong>
      </div>
      <div class="calendar-summary-card">
        <span>历史最高</span>
        <strong>{{ maxStreak }}</strong>
      </div>
      <div class="calendar-summary-card">
        <span>累计签到</span>
        <strong>{{ totalCheckInDays }}</strong>
      </div>
    </div>

    <div class="calendar-weekdays">
      <span v-for="day in weekdays" :key="day">{{ day }}</span>
    </div>

    <div class="calendar-grid">
      <div
        v-for="cell in cells"
        :key="cell.key"
        class="calendar-cell"
        :class="{
          'is-blank': !cell.day,
          'is-checked': cell.checked,
          'is-today': cell.today
        }"
      >
        <span>{{ cell.day || '' }}</span>
      </div>
    </div>
  </UiCard>
</template>

<script setup>
import { computed } from 'vue'
import UiCard from '../components/ui/UiCard.vue'

const props = defineProps({
  year: {
    type: Number,
    required: true
  },
  month: {
    type: Number,
    required: true
  },
  checkedInDates: {
    type: Array,
    default: () => []
  },
  checkedInToday: {
    type: Boolean,
    default: false
  },
  currentStreak: {
    type: Number,
    default: 0
  },
  maxStreak: {
    type: Number,
    default: 0
  },
  totalCheckInDays: {
    type: Number,
    default: 0
  },
  bizDate: {
    type: String,
    default: ''
  }
})

const weekdays = ['一', '二', '三', '四', '五', '六', '日']

const checkedSet = computed(() => new Set((props.checkedInDates || []).map((value) => String(value))))

const monthLabel = computed(() => `${props.year} 年 ${props.month} 月`)

const cells = computed(() => {
  const first = new Date(props.year, props.month - 1, 1)
  const last = new Date(props.year, props.month, 0)
  const firstWeekday = (first.getDay() + 6) % 7
  const totalDays = last.getDate()
  const result = []

  for (let i = 0; i < firstWeekday; i += 1) {
    result.push({ key: `blank-${i}`, day: 0, checked: false, today: false })
  }

  for (let day = 1; day <= totalDays; day += 1) {
    const date = `${props.year}-${String(props.month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    result.push({
      key: date,
      day,
      checked: checkedSet.value.has(date),
      today: props.bizDate === date
    })
  }

  return result
})
</script>

<style scoped>
.growth-panel {
  padding: 0;
  overflow: hidden;
}

.growth-panel-head {
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-end;
}

.growth-eyebrow {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 700;
}

.growth-panel-head h2 {
  margin: 6px 0 4px;
}

.growth-panel-head p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.55;
}

.calendar-panel-meta {
  display: grid;
  gap: 4px;
  text-align: right;
}

.calendar-panel-meta strong {
  font-size: 1.1rem;
}

.calendar-panel-meta span {
  color: var(--text-3);
  font-size: 12px;
}

.calendar-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 20px 24px 0;
}

.calendar-summary-card {
  padding: 16px 18px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 94%, white 6%);
  display: grid;
  gap: 4px;
}

.calendar-summary-card span {
  color: var(--text-3);
  font-size: 12px;
}

.calendar-summary-card strong {
  font-size: clamp(1.4rem, 3vw, 2rem);
  line-height: 1;
}

.calendar-weekdays,
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
  padding: 18px 24px 0;
}

.calendar-weekdays span {
  text-align: center;
  color: var(--text-3);
  font-size: 12px;
}

.calendar-grid {
  padding-bottom: 24px;
}

.calendar-cell {
  min-height: 48px;
  border-radius: 14px;
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 96%, white 4%);
  display: grid;
  place-items: center;
  font-weight: 700;
  color: var(--text-2);
}

.calendar-cell.is-blank {
  border-style: dashed;
  color: transparent;
}

.calendar-cell.is-checked {
  border-color: color-mix(in srgb, var(--success) 28%, var(--border) 72%);
  background: color-mix(in srgb, var(--success-weak) 30%, var(--surface) 70%);
  color: var(--success);
}

.calendar-cell.is-today {
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent) 50%, transparent);
}

@media (max-width: 720px) {
  .growth-panel-head {
    flex-direction: column;
    align-items: stretch;
  }

  .calendar-panel-meta {
    text-align: left;
  }

  .calendar-summary {
    grid-template-columns: 1fr;
  }
}
</style>
