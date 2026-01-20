<!-- 空状态提示组件。 -->
<template>
  <div class="empty-state">
    <div class="empty-card" :class="`empty-card--${type}`">
      <div class="empty-icon" aria-hidden="true">
        <!-- Search Empty -->
        <svg
          v-if="type === 'search'"
          width="72"
          height="72"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
        >
          <circle cx="11" cy="11" r="8"></circle>
          <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
        </svg>
        <!-- Error -->
        <svg
          v-else-if="type === 'error'"
          width="72"
          height="72"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
        >
          <circle cx="12" cy="12" r="10"></circle>
          <line x1="12" y1="8" x2="12" y2="12"></line>
          <line x1="12" y1="16" x2="12.01" y2="16"></line>
        </svg>
        <!-- Default / Data -->
        <svg
          v-else
          width="72"
          height="72"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
        >
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
          <polyline points="14 2 14 8 20 8"></polyline>
          <line x1="16" y1="13" x2="8" y2="13"></line>
          <line x1="16" y1="17" x2="8" y2="17"></line>
          <polyline points="10 9 9 9 8 9"></polyline>
        </svg>
      </div>

      <div class="empty-title"><slot>暂无数据</slot></div>
      <div v-if="$slots.description" class="empty-desc"><slot name="description" /></div>
      <div v-if="$slots.actions" class="empty-actions"><slot name="actions" /></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  type: { type: String, default: 'data' } // data, search, error
})
</script>

<style scoped>
.empty-state {
  display: grid;
  place-items: center;
  padding: 40px 16px;
}

.empty-card {
  width: min(560px, 100%);
  padding: 28px 22px;
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
  border: 1px dashed var(--border-strong);
  box-shadow: var(--shadow-sm);
  text-align: center;
}

.empty-icon {
  width: 92px;
  height: 92px;
  margin: 0 auto 14px auto;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: var(--surface-2);
  color: var(--text-3);
}

.empty-card--error {
  border-style: solid;
  border-color: color-mix(in srgb, var(--danger) 28%, var(--border) 72%);
}

.empty-card--error .empty-icon {
  background: color-mix(in srgb, var(--danger) 10%, var(--surface-2) 90%);
  color: var(--danger);
}

.empty-title {
  color: var(--text-1);
  font-weight: 700;
  font-size: 15px;
  line-height: var(--line-tight);
}

.empty-desc {
  margin-top: 8px;
  color: var(--text-2);
  font-size: 13px;
  line-height: var(--line-normal);
}

.empty-actions {
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
