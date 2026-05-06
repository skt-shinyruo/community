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
      <h2 class="ui-state-title">{{ title }}</h2>
      <p v-if="description || $slots.description" class="ui-state-description">
        <slot name="description">{{ description }}</slot>
      </p>
      <p v-if="traceId" class="ui-state-trace">Trace ID: {{ traceId }}</p>
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
  title: { type: String, required: true },
  description: { type: String, default: '' },
  traceId: { type: String, default: '' }
})

const variants = ['empty', 'loading', 'error', 'forbidden', 'unavailable', 'pending', 'development']

const safeVariant = computed(() => {
  const value = String(props.variant || '').trim()
  return variants.includes(value) ? value : 'empty'
})

const iconText = computed(() => {
  if (safeVariant.value === 'loading') return '...'
  if (safeVariant.value === 'error') return '!'
  if (safeVariant.value === 'forbidden') return '403'
  if (safeVariant.value === 'unavailable') return '?'
  if (safeVariant.value === 'pending') return '~'
  if (safeVariant.value === 'development') return 'DEV'
  return '-'
})
</script>
