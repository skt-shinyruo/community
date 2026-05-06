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

const VARIANT_CLASS_MAP = Object.freeze({
  secondary: 'secondary',
  ghost: 'ghost',
  danger: 'danger',
  dangerSecondary: 'danger secondary'
})

const variantClass = computed(() => {
  return VARIANT_CLASS_MAP[props.variant] || ''
})
</script>
