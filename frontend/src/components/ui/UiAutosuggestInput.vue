<template>
  <UiInput
    :id="id || undefined"
    :name="name || undefined"
    :list="listId"
    :placeholder="placeholder"
    :autocomplete="autocomplete"
    :disabled="disabled"
    :model-value="modelValue"
    :model-modifiers="modelModifiers"
    @update:model-value="onModelUpdate"
    @blur="onBlur"
    @keydown="onKeydown"
  />
  <datalist :id="listId">
    <option v-for="suggestion in normalizedSuggestions" :key="suggestion" :value="suggestion" />
  </datalist>
</template>

<script setup>
import { computed, useId } from 'vue'
import UiInput from './UiInput.vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  modelModifiers: { type: Object, default: () => ({}) },
  suggestions: { type: Array, default: () => [] },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  name: { type: String, default: '' },
  id: { type: String, default: '' },
  autocomplete: { type: String, default: 'off' },
  commitOnBlur: { type: Boolean, default: false },
  commitOnEnter: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'commit', 'keydown'])

const listId = `ui-autosuggest-${useId()}`

const normalizedSuggestions = computed(() =>
  (Array.isArray(props.suggestions) ? props.suggestions : []).map((item) => String(item ?? '').trim()).filter(Boolean)
)

function normalizeValue(value) {
  let next = String(value ?? '')
  if (props.modelModifiers?.trim) next = next.trim()
  return next
}

function onModelUpdate(value) {
  if (props.disabled) return
  emit('update:modelValue', normalizeValue(value))
}

function onBlur(event) {
  if (props.disabled) return
  if (props.commitOnBlur) emit('commit', normalizeValue(event?.target?.value ?? props.modelValue))
}

function onKeydown(event) {
  emit('keydown', event)
  if (props.disabled) return

  if (event?.key === 'Enter' && props.commitOnEnter) {
    event?.preventDefault?.()
    emit('commit', normalizeValue(event?.target?.value ?? props.modelValue))
  }
}
</script>
