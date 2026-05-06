<!-- 空状态提示组件。 -->
<template>
  <div class="empty-state empty-state--compat">
    <UiState class="empty-card" :variant="stateVariant" :title="title">
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
  type: { type: String, default: 'data' }
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

const stateVariant = computed(() => {
  if (props.type === 'error') return 'error'
  return 'empty'
})

const title = computed(() => {
  const text = collectText(slots.default?.() || [])
  return text || '暂无数据'
})
</script>
