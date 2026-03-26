<template>
  <div class="topbar-search">
    <div class="topbar-search-field">
      <span class="topbar-search-icon">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"></circle>
          <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
        </svg>
      </span>
      <input
        id="topbar-global-search"
        ref="inputRef"
        type="search"
        name="global-search"
        class="input topbar-search-input"
        :placeholder="placeholder"
        aria-label="全局搜索"
        :value="modelValue"
        @input="onInput"
        @keydown.enter.prevent="emitSubmit"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  modelModifiers: { type: Object, default: () => ({}) },
  isMac: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])
const inputRef = ref(null)

const placeholder = computed(() => `搜索… (${props.isMac ? '⌘' : 'Ctrl'} K)`)

function normalizeValue(value) {
  let next = String(value ?? '')
  if (props.modelModifiers?.trim) next = next.trim()
  return next
}

function onInput(event) {
  emit('update:modelValue', normalizeValue(event?.target?.value ?? ''))
}

function emitSubmit() {
  emit('submit')
}

function onWindowKeydown(event) {
  const key = String(event?.key || '').toLowerCase()
  if (key !== 'k') return
  if (!(event?.metaKey || event?.ctrlKey)) return
  event.preventDefault()
  inputRef.value?.focus?.()
}

onMounted(() => {
  window.addEventListener('keydown', onWindowKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onWindowKeydown)
})
</script>
