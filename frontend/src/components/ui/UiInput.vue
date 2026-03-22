<!-- 基础输入框：与现有 .input 样式对齐，支持 v-model。 -->
<template>
  <input
    class="input"
    :class="inputClass"
    :type="type"
    :id="resolvedId"
    :name="resolvedName"
    :placeholder="placeholder"
    :autocomplete="autocomplete"
    :value="modelValue"
    v-bind="$attrs"
    @input="onInput"
  />
</template>

<script setup>
import { computed, useAttrs, useId } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  modelModifiers: { type: Object, default: () => ({}) },
  type: { type: String, default: 'text' },
  placeholder: { type: String, default: '' },
  autocomplete: { type: String, default: '' },
  multiline: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])
const attrs = useAttrs()
const uid = useId()

const inputClass = computed(() => (props.multiline ? 'multiline' : ''))
const resolvedId = computed(() => String(attrs.id || `ui-input-${uid}`))
const resolvedName = computed(() => String(attrs.name || attrs.id || `ui-input-${uid}`))

function onInput(e) {
  let value = e?.target?.value ?? ''
  if (props.modelModifiers?.trim) value = String(value).trim()
  if (props.modelModifiers?.number) {
    if (value === '') {
      emit('update:modelValue', '')
      return
    }
    const n = Number(value)
    emit('update:modelValue', Number.isNaN(n) ? value : n)
    return
  }
  emit('update:modelValue', value)
}
</script>
