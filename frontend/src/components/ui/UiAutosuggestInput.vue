<template>
  <div class="ui-autosuggest">
    <input
      ref="inputRef"
      class="input ui-autosuggest-control"
      :id="id || undefined"
      :name="name || undefined"
      :placeholder="placeholder"
      :autocomplete="autocomplete"
      :disabled="disabled"
      :value="modelValue"
      :aria-expanded="open ? 'true' : 'false'"
      :aria-controls="listboxId"
      :aria-activedescendant="activeOptionId"
      @focus="onFocus"
      @blur="onBlur"
      @input="onInput"
      @keydown="onKeydown"
    />

    <div v-if="open && normalizedSuggestions.length > 0" :id="listboxId" class="ui-autosuggest-panel" role="listbox">
      <button
        v-for="(suggestion, index) in normalizedSuggestions"
        :id="optionId(index)"
        :key="suggestion"
        class="ui-autosuggest-option"
        :class="{ 'is-active': index === activeIndex }"
        type="button"
        tabindex="-1"
        role="option"
        :data-value="suggestion"
        :aria-selected="index === activeIndex ? 'true' : 'false'"
        @mousedown.prevent
        @click="selectSuggestion(suggestion)"
      >
        {{ suggestion }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, useId, watch } from 'vue'

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

const emit = defineEmits(['update:modelValue', 'select', 'commit', 'keydown'])

const uid = useId()
const listboxId = `ui-autosuggest-listbox-${uid}`
const inputRef = ref(null)
const open = ref(false)
const activeIndex = ref(-1)

const normalizedSuggestions = computed(() =>
  (Array.isArray(props.suggestions) ? props.suggestions : []).map((item) => String(item || '').trim()).filter(Boolean)
)

const activeOptionId = computed(() => (open.value && activeIndex.value >= 0 ? optionId(activeIndex.value) : undefined))

watch(
  () => props.disabled,
  (disabled) => {
    if (disabled) {
      open.value = false
      activeIndex.value = -1
    }
  }
)

watch(
  () => props.suggestions,
  () => {
    if (!open.value) return
    activeIndex.value = resolveInitialActiveIndex()
  }
)

function optionId(index) {
  return `${listboxId}-option-${index}`
}

function normalizeValue(value) {
  let next = String(value ?? '')
  if (props.modelModifiers?.trim) next = next.trim()
  return next
}

function resolveInitialActiveIndex(value = props.modelValue) {
  if (normalizedSuggestions.value.length === 0) return -1
  const exactIndex = normalizedSuggestions.value.findIndex((suggestion) => suggestion === normalizeValue(value))
  return exactIndex >= 0 ? exactIndex : -1
}

function openPanel() {
  if (props.disabled || normalizedSuggestions.value.length === 0) return
  open.value = true
  activeIndex.value = resolveInitialActiveIndex()
}

function closePanel() {
  open.value = false
  activeIndex.value = -1
}

function moveActive(step) {
  if (!open.value) {
    openPanel()
    return
  }
  const total = normalizedSuggestions.value.length
  if (total === 0) return
  if (activeIndex.value < 0) {
    activeIndex.value = step > 0 ? 0 : total - 1
    return
  }
  const current = activeIndex.value
  activeIndex.value = (current + step + total) % total
}

function selectSuggestion(value, shouldCommit = false) {
  if (props.disabled) return
  emit('update:modelValue', value)
  emit('select', value)
  if (shouldCommit) emit('commit', value)
  closePanel()
}

function commitCurrent() {
  emit('commit', normalizeValue(props.modelValue))
  closePanel()
}

function onFocus() {
  openPanel()
}

function onBlur() {
  if (props.disabled) return
  if (props.commitOnBlur) emit('commit', normalizeValue(props.modelValue))
  closePanel()
}

function onInput(event) {
  if (props.disabled) return
  const nextValue = normalizeValue(event?.target?.value ?? '')
  emit('update:modelValue', nextValue)
  if (normalizedSuggestions.value.length > 0) {
    open.value = true
    activeIndex.value = resolveInitialActiveIndex(nextValue)
  }
}

function onKeydown(event) {
  emit('keydown', event)
  if (props.disabled) return

  const key = String(event?.key || '')
  if (key === 'ArrowDown') {
    event?.preventDefault?.()
    moveActive(1)
    return
  }
  if (key === 'ArrowUp') {
    event?.preventDefault?.()
    moveActive(-1)
    return
  }
  if (key === 'Enter') {
    if (!props.commitOnEnter) return
    event?.preventDefault?.()
    if (open.value && activeIndex.value >= 0) {
      selectSuggestion(normalizedSuggestions.value[activeIndex.value], true)
      return
    }
    commitCurrent()
  }
}
</script>
