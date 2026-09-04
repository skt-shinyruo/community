<!-- 单行输入原语：v-model（含 trim / number 修饰符）与原生属性透传；在 UiField 内自动继承 label 关联、描述与校验状态。 -->
<template>
  <input
    v-bind="controlAttrs"
    class="ui-input"
    :class="[sizeClass, variantClass]"
    :value="modelValue"
    @input="onInput"
  />
</template>

<script setup>
import { computed, useAttrs } from 'vue'
import { useFieldControlAttrs } from './fieldContext'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  modelModifiers: { type: Object, default: () => ({}) },
  size: { type: String, default: 'md' }, // md | sm
  variant: { type: String, default: 'outline' } // outline | ghost
})

const emit = defineEmits(['update:modelValue'])

// md / outline 是默认外观，不追加修饰类；命名沿用 UiButton 的 variant 约定。
const SIZE_CLASS_MAP = Object.freeze({ sm: 'ui-input--sm' })
const VARIANT_CLASS_MAP = Object.freeze({ ghost: 'ui-input--ghost' })

const sizeClass = computed(() => SIZE_CLASS_MAP[props.size] || '')
const variantClass = computed(() => VARIANT_CLASS_MAP[props.variant] || '')

const controlAttrs = useFieldControlAttrs(useAttrs())

function onInput(event) {
  let value = event?.target?.value ?? ''
  if (props.modelModifiers?.trim) value = value.trim()
  if (props.modelModifiers?.number) {
    const parsed = Number.parseFloat(value)
    value = Number.isNaN(parsed) ? value : parsed
  }
  emit('update:modelValue', value)
}
</script>

<style scoped>
.ui-input {
  width: 100%;
  height: var(--control-height);
  padding: 0 var(--control-padding-x);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  outline: none;
  background: var(--bg);
  color: var(--text-1);
  font-size: var(--text-sm);
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.ui-input::placeholder {
  color: var(--muted);
}

.ui-input:hover:not(:disabled) {
  border-color: var(--border-strong);
}

.ui-input:focus {
  border-color: var(--accent);
}

.ui-input:focus-visible {
  box-shadow: var(--focus-ring);
}

.ui-input[aria-invalid='true'],
.ui-input[aria-invalid='true']:focus {
  border-color: var(--danger);
}

.ui-input[aria-invalid='true']:focus-visible {
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--danger) 24%, transparent);
}

.ui-input:disabled {
  background: var(--surface-2);
  color: var(--muted);
  cursor: not-allowed;
}

.ui-input--sm {
  height: clamp(28px, calc(var(--control-height) - 4px), 36px);
  padding: 0 calc(var(--control-padding-x) - 4px);
  font-size: var(--text-xs);
}

.ui-input--ghost,
.ui-input--ghost:hover:not(:disabled) {
  background: transparent;
  border-color: transparent;
}

.ui-input--ghost:focus {
  background: var(--bg);
  border-color: var(--accent);
}
</style>
