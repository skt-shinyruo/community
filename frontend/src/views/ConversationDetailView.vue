<template>
  <div class="page chat-page">
    <UiCard class="chat-card">
      <div class="chat-header">
        <div class="chat-header-main">
          <nav aria-label="页面层级">
            <UiButton variant="ghost" :to="{ name: 'messages' }">
              <ArrowLeft :size="16" aria-hidden="true" />
              返回收件箱
            </UiButton>
          </nav>

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

        <div v-if="model.hasMoreHistory" class="chat-history-more">
          <UiButton
            data-testid="load-earlier-messages"
            variant="secondary"
            :disabled="model.loadingHistory || model.loading"
            @click="actions.loadEarlier"
          >
            <LoaderCircle v-if="model.loadingHistory" :size="14" aria-hidden="true" class="chat-history-spinner" />
            {{ model.loadingHistory ? '正在加载…' : '加载更早消息' }}
          </UiButton>
        </div>

        <UiState v-if="model.error && model.messages.length === 0" variant="error" class="chat-state" :title="model.error">
          <template #description>会话消息加载失败，可以重试或返回收件箱。</template>
          <template #actions>
            <UiButton variant="secondary" :disabled="model.loading" @click="actions.refresh">重试</UiButton>
          </template>
        </UiState>
        <div v-else-if="model.loading && model.messages.length === 0" class="chat-state">
          <UiSkeleton variant="list" :rows="4" label="加载会话消息" />
        </div>
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
            <div v-if="m.deliveryState === 'pending'" class="message-delivery">发送中…</div>
            <div v-else-if="m.deliveryState === 'failed'" class="message-delivery">
              <span class="error">发送失败</span>
              <UiButton
                variant="ghost"
                class="message-retry"
                :disabled="!model.realtimeReady"
                @click="actions.retrySend(m.clientMsgId)"
              >
                重试
              </UiButton>
            </div>
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
import { ArrowLeft, LoaderCircle } from 'lucide-vue-next'
import ConversationComposer from '../components/scene/ConversationComposer.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiState from '../components/ui/UiState.vue'
import { useConversationDetailWorkflow } from './useConversationDetailWorkflow'

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
}

.chat-header {
  padding: var(--space-5) var(--space-6);
  display: flex;
  justify-content: space-between;
  gap: var(--space-5);
  align-items: flex-start;
}

.chat-header-main {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.chat-header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.chat-status-pill {
  border-radius: var(--radius-full);
  padding: var(--space-1) var(--space-3);
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--text-3);
  background: var(--surface-2);
  border: 1px solid var(--border);
}

.chat-status-pill.online {
  color: var(--accent-text);
  background: var(--accent-weak);
  border-color: transparent;
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
  background: var(--bg);
  overflow-y: auto;
  padding: var(--space-6);
  display: flex;
  flex-direction: column;
}

.chat-timeline-label {
  align-self: center;
  margin-bottom: var(--space-5);
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-full);
  background: var(--surface-2);
  color: var(--text-3);
  font-size: var(--text-xs);
  font-weight: 700;
}

.chat-history-more {
  display: flex;
  justify-content: center;
  margin-bottom: var(--space-4);
}

.chat-history-spinner {
  animation: chat-history-spin 0.8s linear infinite;
}

@keyframes chat-history-spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .chat-history-spinner {
    animation: none;
  }
}

.chat-state {
  margin: auto 0;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.message-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: min(72%, 620px);
  align-self: flex-start;
  gap: var(--space-1);
}

.message-row.mine {
  align-items: flex-end;
  align-self: flex-end;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-2);
}

.message-author {
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--text-2);
}

.message-time {
  font-size: var(--text-xs);
  color: var(--text-3);
}

.message-bubble {
  padding: var(--space-4) var(--space-5);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  border-top-left-radius: var(--radius-sm);
  font-size: var(--text-md);
  line-height: var(--line-normal);
  color: var(--text-1);
  white-space: pre-wrap;
  word-break: break-word;
}

.message-row.mine .message-bubble {
  background: var(--accent);
  border-color: transparent;
  color: var(--accent-contrast);
  border-top-left-radius: var(--radius-lg);
  border-top-right-radius: var(--radius-sm);
}

.message-delivery {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-2);
  font-size: var(--text-xs);
  color: var(--text-3);
}

.message-retry {
  flex: none;
}

.chat-composer {
  padding: var(--space-5) var(--space-6) var(--space-6);
  display: grid;
  gap: var(--space-4);
}

.chat-composer-copy {
  display: grid;
  gap: var(--space-1);
}

.chat-composer-label {
  font-size: var(--text-sm);
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
    padding: var(--space-4);
    flex-direction: column;
  }

  .chat-area {
    padding: var(--space-4);
    max-height: none;
  }

  .chat-composer {
    padding: var(--space-3);
  }

  .message-row {
    max-width: 88%;
  }
}
</style>
