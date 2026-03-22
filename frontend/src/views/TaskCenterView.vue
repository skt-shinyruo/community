<template>
  <UiCard class="growth-panel">
    <div class="growth-panel-head">
      <div>
        <div class="growth-eyebrow">Task Center</div>
        <h2>任务清单</h2>
        <p>把签到、创作和互动整理成一张可执行的任务面板，不让奖励逻辑散落在各个页面里。</p>
      </div>
    </div>

    <div v-if="loading && !hasAnyTasks" class="muted growth-panel-state">正在整理任务进度…</div>

    <div v-for="group in groups" :key="group.key" class="task-group">
      <div class="task-group-head">
        <div>
          <h3>{{ group.title }}</h3>
          <p>{{ group.description }}</p>
        </div>
        <span class="task-group-meta">{{ group.items.length }} 项</span>
      </div>

      <div v-if="group.items.length === 0" class="task-group-empty muted">当前分组还没有可显示的任务。</div>

      <div v-else class="task-list">
        <article
          v-for="task in group.items"
          :key="`${group.key}-${task.taskCode}`"
          class="task-row"
          :class="`is-${task.uiState}`"
        >
          <div class="task-copy">
            <div class="task-title-row">
              <strong>{{ task.title }}</strong>
              <UiTag>{{ task.claimLabel }}</UiTag>
            </div>
            <p>{{ task.description }}</p>
          </div>

          <div class="task-metrics">
            <div class="task-progress">{{ task.progressText }}</div>
            <div class="task-reward">{{ task.rewardText }}</div>
          </div>
        </article>
      </div>
    </div>
  </UiCard>
</template>

<script setup>
import { computed } from 'vue'
import UiCard from '../components/ui/UiCard.vue'
import UiTag from '../components/ui/UiTag.vue'

const props = defineProps({
  groups: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const hasAnyTasks = computed(() => (props.groups || []).some((group) => Array.isArray(group?.items) && group.items.length > 0))
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
}

.growth-eyebrow {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 700;
}

.growth-panel-head h2,
.task-group-head h3 {
  margin: 6px 0 4px;
}

.growth-panel-head p,
.task-group-head p,
.task-copy p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.55;
}

.growth-panel-state {
  padding: 28px 24px;
}

.task-group {
  padding: 20px 24px;
  border-top: 1px solid var(--border);
  display: grid;
  gap: 14px;
}

.task-group:first-of-type {
  border-top: none;
}

.task-group-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-end;
}

.task-group-meta {
  color: var(--text-3);
  font-size: 12px;
  white-space: nowrap;
}

.task-group-empty {
  padding: 10px 0 2px;
}

.task-list {
  display: grid;
  gap: 12px;
}

.task-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 16px 18px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 94%, white 6%);
}

.task-row.is-claimed {
  border-color: color-mix(in srgb, var(--success) 24%, var(--border) 76%);
  background: color-mix(in srgb, var(--success-weak) 24%, var(--surface) 76%);
}

.task-row.is-claimable {
  border-color: color-mix(in srgb, var(--warning) 30%, var(--border) 70%);
  background: color-mix(in srgb, var(--warning-weak) 26%, var(--surface) 74%);
}

.task-copy {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.task-title-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.task-metrics {
  display: grid;
  gap: 4px;
  text-align: right;
  justify-items: end;
}

.task-progress {
  font-size: 1.05rem;
  font-weight: 800;
  color: var(--text-1);
}

.task-reward {
  color: var(--text-2);
  font-size: 13px;
}

@media (max-width: 720px) {
  .task-group-head,
  .task-row {
    grid-template-columns: 1fr;
  }

  .task-metrics {
    text-align: left;
    justify-items: start;
  }
}
</style>
