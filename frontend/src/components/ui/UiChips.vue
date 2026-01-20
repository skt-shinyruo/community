<!-- UiChips：轻量单选 chips/segmented 控件（适用于筛选、排序、视图切换）。 -->
<template>
  <div class="chips" :class="{ 'chips-wrap': wrap }" role="group" :aria-label="ariaLabel">
    <button
      v-for="opt in options"
      :key="String(opt.key)"
      class="chip"
      :class="{ active: String(opt.key) === String(modelValue), disabled: !!opt.disabled }"
      type="button"
      :disabled="!!opt.disabled"
      :aria-pressed="String(opt.key) === String(modelValue)"
      @click="onSelect(opt.key)"
    >
      <span v-if="opt.icon" class="chip-icon" aria-hidden="true">{{ opt.icon }}</span>
      <span class="chip-text">{{ opt.label }}</span>
    </button>
  </div>
</template>

<script setup>
const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, default: () => [] }, // [{ key, label, icon?, disabled? }]
  ariaLabel: { type: String, default: '' },
  wrap: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'change'])

function onSelect(key) {
  emit('update:modelValue', key)
  emit('change', key)
}
</script>

