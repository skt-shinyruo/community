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
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="22" y1="2" x2="11" y2="13"></line>
        <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
      </svg>
    </UiIconButton>
  </div>
</template>

<script setup>
import { computed } from 'vue'

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
  padding: 12px;
  background: color-mix(in srgb, var(--surface) 75%, var(--bg) 25%);
  border: 1px solid var(--border);
  border-radius: 24px;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.chat-input {
  flex: 1;
  background: transparent;
  border: none;
  border-radius: 16px;
  padding: 10px 12px;
  font-family: inherit;
  font-size: 15px;
  line-height: 1.5;
  outline: none;
  resize: none;
  min-height: 48px;
}

.chat-input:focus {
  box-shadow: none;
}

.chat-input:disabled {
  cursor: not-allowed;
}

.send-btn {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--accent);
  color: var(--accent-contrast);
  border: none;
  transition: transform 0.12s ease, opacity 0.12s ease;
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

@media (max-width: 768px) {
  .chat-input-area {
    border-radius: 20px;
  }
}
</style>
