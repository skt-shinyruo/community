<template>
  <div class="chat-input-area">
    <textarea
      id="conversation-message-input"
      name="conversation-message"
      class="chat-input"
      :value="modelValue"
      :disabled="disabled"
      :placeholder="placeholder"
      rows="1"
      @input="onInput"
      @keydown.enter.prevent="emitSubmit"
    ></textarea>

    <UiIconButton
      class="send-btn"
      aria-label="发送消息"
      title="发送"
      :disabled="submitDisabled"
      @click="emitSubmit"
    >
      <SendHorizontal :size="20" aria-hidden="true" />
    </UiIconButton>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { SendHorizontal } from 'lucide-vue-next'

import UiIconButton from '../ui/UiIconButton.vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '写一条清晰、具体的消息…' }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const submitDisabled = computed(() => props.disabled || !String(props.modelValue || '').trim())

function onInput(event) {
  emit('update:modelValue', event?.target?.value ?? '')
}

function emitSubmit() {
  if (submitDisabled.value) return
  emit('submit')
}
</script>

<style scoped>
.chat-input-area {
  padding: var(--space-3);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  display: flex;
  gap: var(--space-3);
  align-items: flex-end;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.chat-input-area:focus-within {
  border-color: var(--accent);
  box-shadow: var(--focus-ring);
}

.chat-input {
  flex: 1;
  background: transparent;
  border: none;
  padding: var(--space-2) var(--space-3);
  font-family: inherit;
  font-size: var(--text-md);
  line-height: var(--line-normal);
  outline: none;
  resize: none;
  min-height: var(--space-9);
}

.chat-input:disabled {
  cursor: not-allowed;
}

.send-btn {
  width: var(--space-9);
  height: var(--space-9);
  border-radius: var(--radius-full);
  background: var(--accent);
  color: var(--accent-contrast);
  border: none;
  transition:
    transform var(--duration-fast) var(--ease-standard),
    opacity var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.send-btn:hover {
  background: var(--accent-hover);
}

.send-btn:active {
  transform: scale(0.96);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: default;
  background: var(--accent);
}
</style>
