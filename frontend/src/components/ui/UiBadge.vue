<!-- 徽章组件：用于状态（置顶/加精/已删除/已解决等）。 -->
<template>
  <span class="badge" :class="variantClass">
    <slot />
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: { type: String, default: 'default' } // default | accent | danger | success | warning | pending | unread
})

const variantClass = computed(() => {
  const v = String(props.variant || '').trim()
  if (!v || v === 'default') return ''
  return `badge-${v}`
})
</script>

<!-- 样式自全局 components.css 退役后迁入（原 .badge 一族，类名与外观不变；
     letter-spacing 按规范归零）。 -->
<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: var(--text-xs);
  font-weight: 700;
  border: 1px solid var(--border);
  background: var(--surface-2);
  color: var(--text-2);
  letter-spacing: 0;
}

.badge-accent {
  background: var(--accent-weak);
  border-color: color-mix(in srgb, var(--accent) 22%, var(--border) 78%);
  color: var(--accent-text);
}

.badge-danger {
  background: var(--danger-weak);
  border-color: color-mix(in srgb, var(--danger) 25%, var(--border) 75%);
  color: var(--danger);
}

.badge-success {
  background: color-mix(in srgb, var(--success) 14%, transparent 86%);
  border-color: color-mix(in srgb, var(--success) 25%, var(--border) 75%);
  color: var(--success);
}

.badge-warning {
  background: color-mix(in srgb, var(--warning) 14%, transparent 86%);
  border-color: color-mix(in srgb, var(--warning) 25%, var(--border) 75%);
  color: var(--warning);
}

.badge-pending {
  background: var(--pending-weak);
  border-color: color-mix(in srgb, var(--pending) 28%, var(--border) 72%);
  color: var(--pending);
}
</style>
