<!-- 徽章组件：用于状态（置顶/加精/已删除/已解决等）。 -->
<template>
  <span class="badge" :class="variantClass">
    <slot />
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: { type: String, default: 'default' } // default | accent | danger | success | warning
})

const variantClass = computed(() => {
  const v = String(props.variant || '').trim()
  if (!v || v === 'default') return ''
  return `badge-${v}`
})
</script>
