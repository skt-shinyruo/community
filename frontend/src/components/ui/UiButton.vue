<!-- 基础按钮组件：统一 primary/secondary、loading/disabled 的交互表现。 -->
<template>
  <button
    class="btn"
    :class="variantClass"
    :type="type"
    :disabled="disabled || loading"
    :title="title"
    @click="$emit('click', $event)"
  >
    <slot>{{ loading ? '处理中...' : '' }}</slot>
  </button>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: { type: String, default: 'primary' }, // primary | secondary | ghost | danger | dangerSecondary
  type: { type: String, default: 'button' },
  title: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

defineEmits(['click'])

const variantClass = computed(() => {
  if (props.variant === 'secondary') return 'secondary'
  if (props.variant === 'ghost') return 'ghost'
  if (props.variant === 'danger') return 'danger'
  if (props.variant === 'dangerSecondary') return 'danger secondary'
  return ''
})
</script>
