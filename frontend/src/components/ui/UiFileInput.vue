<template>
  <div class="ui-file-input" :class="{ disabled: disabled }">
    <input
      ref="inputRef"
      class="ui-file-input-native"
      type="file"
      :id="resolvedId"
      :name="resolvedName"
      :accept="accept || undefined"
      :disabled="disabled"
      @change="onChange"
    />

    <div class="ui-file-input-surface input">
      <button
        class="btn secondary ui-file-input-trigger"
        type="button"
        :disabled="disabled"
        @click="openPicker"
      >
        {{ buttonText }}
      </button>

      <span class="ui-file-input-name" :class="{ 'is-empty': !fileName }">
        {{ fileName || emptyText }}
      </span>

      <button
        v-if="clearable && modelValue"
        class="btn ghost ui-file-input-clear"
        type="button"
        :disabled="disabled"
        @click="clearFile"
      >
        清除
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, useAttrs, useId, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Object, default: null },
  accept: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  id: { type: String, default: '' },
  name: { type: String, default: '' },
  buttonText: { type: String, default: '选择文件' },
  emptyText: { type: String, default: '未选择文件' },
  clearable: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])
const attrs = useAttrs()
const uid = useId()
const inputRef = ref(null)

const resolvedId = computed(() => String(props.id || attrs.id || `ui-file-input-${uid}`))
const resolvedName = computed(() => String(props.name || attrs.name || props.id || attrs.id || `ui-file-input-${uid}`))
const fileName = computed(() => String(props.modelValue?.name || '').trim())

watch(
  () => props.modelValue,
  (value) => {
    if (value || !inputRef.value) return
    inputRef.value.value = ''
  }
)

function openPicker() {
  if (props.disabled) return
  inputRef.value?.click()
}

function onChange(event) {
  if (props.disabled) return
  emit('update:modelValue', event?.target?.files?.[0] || null)
}

function clearFile() {
  if (props.disabled) return
  if (inputRef.value) inputRef.value.value = ''
  emit('update:modelValue', null)
}
</script>
