<template>
  <label class="ui-checkbox" :class="{ disabled: disabled }">
    <input
      class="ui-checkbox-input"
      type="checkbox"
      :name="name || undefined"
      :checked="!!modelValue"
      :disabled="disabled"
      @change="onChange"
    />
    <span class="ui-checkbox-copy"><slot>{{ label }}</slot></span>
  </label>
</template>

<script setup>
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  name: { type: String, default: '' },
  label: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

function onChange(event) {
  emit('update:modelValue', !!event?.target?.checked)
}
</script>
