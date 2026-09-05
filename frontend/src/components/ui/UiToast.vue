<template>
  <TransitionGroup name="toast" tag="div" class="toast-container">
    <div 
      v-for="msg in messages" 
      :key="msg.id" 
      class="toast" 
      :class="[msg.type]"
    >
      <div class="toast-content">
        <div class="toast-title" v-if="msg.title">{{ msg.title }}</div>
        <div class="toast-message">{{ msg.text }}</div>
        <div class="toast-actions" v-if="msg.actionText && typeof msg.onAction === 'function'">
          <UiButton size="sm" type="button" @click="handleAction(msg)">{{ msg.actionText }}</UiButton>
        </div>
      </div>
      <UiIconButton aria-label="关闭通知" title="关闭通知" size="sm" @click="remove(msg.id)">
        <X :size="16" aria-hidden="true" />
      </UiIconButton>
    </div>
  </TransitionGroup>
</template>

<script setup>
import { ref } from 'vue'
import { X } from 'lucide-vue-next'
import UiButton from './UiButton.vue'
import UiIconButton from './UiIconButton.vue'

const messages = ref([])
let idCounter = 0

function show({ title, text, type = 'info', duration = 3000, actionText, onAction } = {}) {
  const id = ++idCounter
  messages.value.push({ id, title, text, type, actionText, onAction })
  if (duration > 0) {
    setTimeout(() => remove(id), duration)
  }
}

function remove(id) {
  const idx = messages.value.findIndex(m => m.id === id)
  if (idx !== -1) messages.value.splice(idx, 1)
}

function handleAction(msg) {
  if (!msg || typeof msg.onAction !== 'function') return
  try {
    msg.onAction()
  } finally {
    remove(msg.id)
  }
}

defineExpose({ show })
</script>

<style scoped>
.toast-container {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: var(--z-toast);
  display: flex;
  flex-direction: column;
  gap: 12px;
  pointer-events: none;
}

.toast {
  pointer-events: auto;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  padding: 16px;
  width: 320px;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  transform-origin: bottom right;
}

.toast.success { border-left: 4px solid var(--success); }
.toast.error { border-left: 4px solid var(--danger); }
.toast.warning { border-left: 4px solid var(--warning); }
.toast.info { border-left: 4px solid var(--accent); }

.toast-content { flex: 1; }
.toast-title { font-weight: 700; margin-bottom: 4px; font-size: 14px; }
.toast-message { font-size: 13px; color: var(--text-2); line-height: 1.4; }
.toast-actions { margin-top: 8px; display: flex; justify-content: flex-start; gap: 8px; }

/* Transitions */
.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.3s cubic-bezier(0.25, 1, 0.5, 1), transform 0.3s cubic-bezier(0.25, 1, 0.5, 1);
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(30px) scale(0.9);
}

@media (max-width: 768px) {
  .toast-container {
    left: 14px;
    right: 14px;
    bottom: calc(96px + env(safe-area-inset-bottom, 0px));
  }

  .toast {
    width: 100%;
  }
}
</style>
