<!-- 多行输入原语：v-model（含 trim 修饰符）与原生属性透传；在 UiField 内自动继承 label 关联、描述与校验状态。 -->
<template>
  <textarea
    v-bind="controlAttrs"
    class="ui-textarea"
    :value="modelValue"
    @input="onInput"
  />
</template>

<script setup>
import { useAttrs } from 'vue'
import { useFieldControlAttrs } from './fieldContext'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  modelValue: { type: String, default: '' },
  modelModifiers: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue'])

const controlAttrs = useFieldControlAttrs(useAttrs())

function onInput(event) {
  let value = event?.target?.value ?? ''
  if (props.modelModifiers?.trim) value = value.trim()
  emit('update:modelValue', value)
}
</script>

<style scoped>
.ui-textarea {
  width: 100%;
  min-height: calc(var(--control-height) * 2 + var(--control-padding-y) * 2);
  padding: var(--control-padding-y) var(--control-padding-x);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  outline: none;
  background: var(--bg);
  color: var(--text-1);
  font-size: var(--text-sm);
  line-height: var(--line-normal);
  resize: vertical;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.ui-textarea::placeholder {
  color: var(--muted);
}

.ui-textarea:hover:not(:disabled) {
  border-color: var(--border-strong);
}

.ui-textarea:focus {
  border-color: var(--accent);
}

.ui-textarea:focus-visible {
  box-shadow: var(--focus-ring);
}

.ui-textarea[aria-invalid='true'],
.ui-textarea[aria-invalid='true']:focus {
  border-color: var(--danger);
}

.ui-textarea[aria-invalid='true']:focus-visible {
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--danger) 24%, transparent);
}

.ui-textarea:disabled {
  background: var(--surface-2);
  color: var(--muted);
  cursor: not-allowed;
  resize: none;
}
</style>
