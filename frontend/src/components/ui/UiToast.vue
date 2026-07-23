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
          <button class="btn sm" type="button" @click="handleAction(msg)">{{ msg.actionText }}</button>
        </div>
      </div>
      <button class="btn-icon sm" @click="remove(msg.id)">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
      </button>
    </div>
  </TransitionGroup>
</template>

<script setup>
import { ref } from 'vue'

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
  z-index: 1000;
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
  transition: all 0.3s cubic-bezier(0.25, 1, 0.5, 1);
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
