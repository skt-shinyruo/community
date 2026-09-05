<template>
  <section
    class="ui-state"
    :class="`ui-state--${safeVariant}`"
    :data-development-only="safeVariant === 'development' ? 'true' : undefined"
    role="status"
  >
    <div class="ui-state-icon" aria-hidden="true">{{ iconText }}</div>
    <div class="ui-state-body">
      <div v-if="safeVariant === 'development'" class="ui-state-kicker">Development only</div>
      <h2 class="ui-state-title">
        <template v-if="title">{{ title }}</template>
        <slot v-else>暂无数据</slot>
      </h2>
      <p v-if="description || $slots.description" class="ui-state-description">
        <slot name="description">{{ description }}</slot>
      </p>
      <div v-if="$slots.actions" class="ui-state-actions">
        <slot name="actions" />
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

defineOptions({ name: 'UiState' })

const props = defineProps({
  variant: { type: String, default: 'empty' },
  title: { type: String, default: '' },
  description: { type: String, default: '' }
})

const variants = ['empty', 'error', 'development']

const safeVariant = computed(() => {
  const value = String(props.variant || '').trim()
  return variants.includes(value) ? value : 'empty'
})

const iconText = computed(() => {
  if (safeVariant.value === 'error') return '!'
  if (safeVariant.value === 'development') return 'DEV'
  return '-'
})
</script>

<!-- 样式自全局 components.css 退役后迁入（原 .ui-state 一族，类名与外观不变）。 -->
<style scoped>
.ui-state {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  width: 100%;
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: none;
}

.ui-state-icon {
  flex: none;
  min-width: 36px;
  height: 36px;
  padding: 0 8px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--surface-2);
  color: var(--text-2);
  font-size: 12px;
  font-weight: 800;
}

.ui-state-body {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.ui-state-kicker {
  color: var(--text-3);
  font-size: var(--text-xs);
  font-family: var(--font-mono);
}

.ui-state-title {
  margin: 0;
  color: var(--text-1);
  font-size: var(--text-md);
  line-height: var(--line-tight);
}

.ui-state-description {
  margin: 0;
  color: var(--text-2);
  font-size: var(--text-sm);
  line-height: var(--line-normal);
}

.ui-state-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.ui-state--error {
  border-color: color-mix(in srgb, var(--danger) 28%, var(--border) 72%);
}

.ui-state--pending {
  border-color: color-mix(in srgb, var(--pending) 28%, var(--border) 72%);
}

.ui-state--development {
  border-style: dashed;
}
</style>
