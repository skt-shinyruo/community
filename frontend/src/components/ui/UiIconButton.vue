<template>
  <button
    class="btn-icon ui-icon-button"
    :class="sizeClass"
    :type="type"
    :disabled="disabled"
    :aria-label="ariaLabel"
    :title="title || ariaLabel"
    @click="$emit('click', $event)"
  >
    <slot />
  </button>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  ariaLabel: { type: String, required: true },
  title: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  size: { type: String, default: 'md' },
  type: { type: String, default: 'button' }
})

defineEmits(['click'])

const sizeClass = computed(() => {
  const size = String(props.size || '').trim()
  if (!size || size === 'md') return ''
  return `ui-icon-button--${size}`
})
</script>

<!-- 样式自全局 components.css 退役后迁入（原 .btn-icon / .ui-icon-button，类名与外观不变；
     transition 按规范列出具体属性）。 -->
<style scoped>
/* :where() 保持迁入前的全局低权重级联：调用方作用域覆盖类照常获胜。 */
:where(.btn-icon) {
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  padding: 8px;
  border-radius: 999px;
  color: var(--text-2);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s, border-color 0.2s, color 0.2s, box-shadow 0.2s;
}

:where(.btn-icon:hover) {
  background: var(--surface-2);
  border-color: var(--border);
  color: var(--text-1);
}

:where(.btn-icon:active) {
  background: var(--active-bg);
}

:where(.btn-icon:focus-visible) {
  box-shadow: var(--focus-ring);
}

:where(.ui-icon-button) {
  min-width: 36px;
  min-height: 36px;
}

:where(.ui-icon-button:disabled) {
  opacity: 0.6;
  cursor: not-allowed;
  border-color: transparent;
  background: transparent;
  box-shadow: none;
}

:where(.ui-icon-button--sm) {
  min-width: 30px;
  min-height: 30px;
  padding: 6px;
}
</style>
