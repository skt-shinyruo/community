<template>
  <button
    class="btn-icon ui-icon-button"
    :class="[variantClass, sizeClass]"
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
  variant: { type: String, default: 'default' },
  size: { type: String, default: 'md' },
  type: { type: String, default: 'button' }
})

defineEmits(['click'])

const variantClass = computed(() => {
  const variant = String(props.variant || '').trim()
  if (!variant || variant === 'default') return ''
  return `ui-icon-button--${variant}`
})

const sizeClass = computed(() => {
  const size = String(props.size || '').trim()
  if (!size || size === 'md') return ''
  return `ui-icon-button--${size}`
})
</script>
