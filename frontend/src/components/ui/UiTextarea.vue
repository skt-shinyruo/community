<!-- 基础文本域：与现有 .input 样式对齐，支持 v-model。 -->
<template>
  <textarea
    class="input multiline"
    :rows="rows"
    :placeholder="placeholder"
    :value="modelValue"
    v-bind="$attrs"
    @input="onInput"
  />
</template>

<script setup>
const props = defineProps({
  modelValue: { type: String, default: '' },
  modelModifiers: { type: Object, default: () => ({}) },
  rows: { type: [Number, String], default: 3 },
  placeholder: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

function onInput(e) {
  let value = e?.target?.value ?? ''
  if (props.modelModifiers?.trim) value = String(value).trim()
  emit('update:modelValue', value)
}
</script>
