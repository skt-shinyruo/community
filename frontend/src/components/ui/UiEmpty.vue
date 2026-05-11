<!-- 空状态提示组件。 -->
<template>
  <div class="empty-state">
    <UiState class="empty-card" :variant="props.variant" :title="title">
      <template v-if="$slots.description" #description>
        <slot name="description" />
      </template>
      <template v-if="$slots.actions" #actions>
        <slot name="actions" />
      </template>
    </UiState>
  </div>
</template>

<script setup>
import { computed, useSlots } from 'vue'

import UiState from './UiState.vue'

defineOptions({ name: 'UiEmpty' })

const props = defineProps({
  variant: { type: String, default: 'empty' }
})

const slots = useSlots()

function collectText(nodes) {
  return (nodes || [])
    .map((node) => {
      if (!node) return ''
      if (typeof node.children === 'string') return node.children
      if (Array.isArray(node.children)) return collectText(node.children)
      return ''
    })
    .join('')
    .trim()
}

const title = computed(() => {
  const text = collectText(slots.default?.() || [])
  return text || '暂无数据'
})
</script>
