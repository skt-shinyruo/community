<template>
  <div class="page chat-page">
    <UiCard class="chat-card">
      <div class="chat-header">
        <div class="chat-header-main">
          <RouterLink to="/messages" class="chat-back-link" aria-label="返回会话列表" title="返回收件箱">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"></polyline>
            </svg>
            <span>返回收件箱</span>
          </RouterLink>

          <UiPageHeader class="chat-title-block">
            <template #title>{{ model.targetId ? '私信线程' : '当前对话' }}</template>
            <template #subtitle>
              <span v-if="model.targetId">与一位社区成员继续交流，保持这段线程的上下文完整。</span>
              <span v-else>在同一个线程里继续推进这段私信。</span>
            </template>
          </UiPageHeader>
        </div>

        <div class="chat-header-actions">
          <div class="chat-status-pill" :class="{ online: model.realtimeReady }">
            {{ model.realtimeStatusText }}
          </div>
          <UiButton variant="secondary" @click="actions.refresh" :disabled="model.loading">刷新</UiButton>
        </div>
      </div>

      <div class="chat-divider" role="separator" />

      <div class="chat-area" ref="chatArea">
        <div class="chat-timeline-label">消息时间线</div>

        <UiButton
          v-if="model.hasMoreHistory"
          data-testid="load-earlier-messages"
          variant="secondary"
          :disabled="model.loadingHistory || model.loading"
          @click="actions.loadEarlier"
        >
          {{ model.loadingHistory ? '加载中…' : '加载更早消息' }}
        </UiButton>

        <UiState v-if="model.error && model.messages.length === 0" variant="error" class="chat-state">{{ model.error }}</UiState>
        <div v-else-if="model.loading && model.messages.length === 0" class="muted chat-state">正在同步会话…</div>
        <UiState v-else-if="model.messages.length === 0" class="chat-state">
          暂无消息
          <template #description>你可以直接发出第一条消息，让这段对话开始流动起来。</template>
        </UiState>

        <div v-else class="message-list">
          <div v-for="m in model.messages" :key="m.id" class="message-row" :class="{ mine: m.fromId === model.meId }">
            <div class="message-meta">
              <span class="message-author">{{ m.fromId === model.meId ? '我' : '对方' }}</span>
              <span class="message-time">{{ m.timeLabel }}</span>
            </div>
            <div class="message-bubble">{{ m.content }}</div>
            <span v-if="m.deliveryState === 'pending'" class="message-delivery">发送中…</span>
            <span v-else-if="m.deliveryState === 'failed'" class="message-delivery error">发送失败</span>
          </div>
        </div>
      </div>

      <div class="chat-divider" role="separator" />

      <div class="chat-composer">
        <div class="chat-composer-copy">
          <div class="chat-composer-label">继续这段对话</div>
          <div class="chat-composer-hint">按 Enter 即可发送新消息。</div>
        </div>

        <div v-if="model.error && model.messages.length > 0" class="error chat-inline-error">{{ model.error }}</div>

        <ConversationComposer v-model="model.content" :disabled="model.sending || !model.canSend" @submit="actions.send" />
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import ConversationComposer from '../components/scene/ConversationComposer.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiState from '../components/ui/UiState.vue'
import { useConversationDetailWorkflow } from './useConversationDetailWorkflow'

defineEmits(['trace'])
const props = defineProps({ conversationId: String })
const chatArea = ref(null)
const conversationId = computed(() => props.conversationId)
const { model, actions, lifecycle } = useConversationDetailWorkflow({ conversationId, chatArea })

onMounted(lifecycle.mount)
onBeforeUnmount(lifecycle.unmount)
</script>

<style scoped>
.chat-page {
  max-width: 980px;
  margin: 0 auto;
  gap: var(--space-5);
}

.chat-card {
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 78vh;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--surface) 97%, white 3%), var(--surface));
}

.chat-header {
  padding: 18px 22px;
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
}

.chat-header-main {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.chat-back-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-2);
  text-decoration: none;
  font-size: 13px;
  font-weight: 600;
}

.chat-back-link:hover {
  color: var(--text-1);
}

.chat-title-block :deep(.ui-page-header) {
  gap: 0;
}

.chat-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.chat-status-pill {
  border-radius: 999px;
  padding: 7px 12px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-2);
  background: color-mix(in srgb, var(--surface) 82%, var(--bg) 18%);
  border: 1px solid var(--border);
}

.chat-status-pill.online {
  color: var(--success);
  background: color-mix(in srgb, var(--success-weak) 70%, white 30%);
  border-color: color-mix(in srgb, var(--success) 22%, var(--border) 78%);
}

.chat-divider {
  height: 1px;
  margin: var(--space-2) 0;
  background: var(--border);
}

.chat-area {
  flex: 1;
  min-height: 0;
  max-height: min(60vh, 720px);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--bg) 85%, var(--surface) 15%), var(--bg));
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.chat-timeline-label {
  align-self: center;
  margin-bottom: 18px;
  padding: 6px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface) 88%, var(--bg) 12%);
  color: var(--text-3);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-weight: 700;
}

.chat-state {
  margin: auto 0;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.message-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: min(72%, 620px);
  align-self: flex-start;
  gap: 6px;
}

.message-row.mine {
  align-items: flex-end;
  align-self: flex-end;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 6px;
}

.message-author {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-2);
}

.message-bubble {
  padding: 14px 18px;
  background: color-mix(in srgb, var(--surface) 90%, white 10%);
  border-radius: 18px;
  border-top-left-radius: 6px;
  box-shadow: var(--shadow-sm);
  font-size: 15px;
  line-height: 1.65;
  color: var(--text-1);
  white-space: pre-wrap;
  word-break: break-word;
}

.message-row.mine .message-bubble {
  background: color-mix(in srgb, var(--accent) 88%, white 12%);
  color: var(--accent-contrast);
  border-top-left-radius: 18px;
  border-top-right-radius: 6px;
}

.message-time {
  font-size: 11px;
  color: var(--text-3);
}

.message-delivery {
  font-size: 11px;
  color: var(--text-3);
}

.chat-composer {
  padding: 20px 24px 24px;
  display: grid;
  gap: 14px;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--surface) 96%, white 4%), var(--surface));
}

.chat-composer-copy {
  display: grid;
  gap: 4px;
}

.chat-composer-label {
  font-size: 14px;
  font-weight: 700;
}

.chat-composer-hint {
  font-size: 13px;
  color: var(--text-3);
}

.chat-inline-error {
  margin: 0;
}

@media (max-width: 768px) {
  .chat-card {
    min-height: 0;
    height: calc(100dvh - var(--topbar-height) - var(--space-4) - 96px - env(safe-area-inset-bottom, 0px));
  }

  .chat-header {
    padding: 16px;
    flex-direction: column;
  }

  .chat-area {
    padding: 16px;
    max-height: none;
  }

  .chat-composer {
    padding: 12px;
  }

  .message-row {
    max-width: 88%;
  }
}
</style>
