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
            <template #title>{{ targetId ? '私信线程' : '当前对话' }}</template>
            <template #subtitle>
              <span v-if="targetId">与一位社区成员继续交流，保持这段线程的上下文完整。</span>
              <span v-else>在同一个线程里继续推进这段私信。</span>
            </template>
          </UiPageHeader>
        </div>

        <div class="chat-header-actions">
          <div class="chat-status-pill" :class="{ online: imRealtimeClient?.state?.connected }">
            {{ imRealtimeClient?.state?.connected ? '实时已连接' : '实时未连接' }}
          </div>
          <UiButton variant="secondary" @click="load" :disabled="loading">刷新</UiButton>
        </div>
      </div>

      <UiDivider />

      <div class="chat-area" ref="chatArea">
        <div class="chat-timeline-label">消息时间线</div>

        <UiEmpty v-if="error && items.length === 0" type="error" class="chat-state">{{ error }}</UiEmpty>
        <div v-else-if="loading && items.length === 0" class="muted chat-state">正在同步会话…</div>
        <UiEmpty v-else-if="items.length === 0" class="chat-state">
          暂无消息
          <template #description>你可以直接发出第一条消息，让这段对话开始流动起来。</template>
        </UiEmpty>

        <div v-else class="message-list">
          <div v-for="m in items" :key="m.id" class="message-row" :class="{ mine: m.fromId === meId }">
            <div class="message-meta">
              <span class="message-author">{{ m.fromId === meId ? '我' : '对方' }}</span>
              <span class="message-time">{{ formatTimeShort(m.createTime) }}</span>
            </div>
            <div class="message-bubble">{{ m.content }}</div>
          </div>
        </div>
      </div>

      <UiDivider />

      <div class="chat-composer">
        <div class="chat-composer-copy">
          <div class="chat-composer-label">继续这段对话</div>
          <div class="chat-composer-hint">按 Enter 即可发送新消息。</div>
        </div>

        <div v-if="error && items.length > 0" class="error chat-inline-error">{{ error }}</div>

        <ConversationComposer v-model="content" :disabled="sending" @submit="send" />
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { listImConversationMessages, markImConversationRead } from '../api/services/imCoreChatService'
import { imRealtimeClient } from '../im/imRealtimeClient'
import { normalizeOpaqueId, sameOpaqueId } from '../utils/opaqueId'
import { createLatestRequestTracker } from '../utils/latestRequest'
import ConversationComposer from '../components/scene/ConversationComposer.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiDivider from '../components/ui/UiDivider.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  findLatestConversationSeq,
  mapConversationMessage,
  mergeConversationMessages,
  parseConversationTargetId
} from './conversationDetailState'

const emit = defineEmits(['trace'])
const props = defineProps({ conversationId: String })
const auth = useAuthStore()
const meId = computed(() => normalizeOpaqueId(auth.userId))

const loading = ref(false)
const items = ref([])
const error = ref('')
const content = ref('')
const sending = ref(false)
const chatArea = ref(null)
const pendingClientMsgIds = new Set()
const loadRequestTracker = createLatestRequestTracker()

const conversationId = computed(() => String(props.conversationId || '').trim())
const targetId = computed(() => parseTargetId())

function formatTimeShort(ts) {
   return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function parseTargetId() {
  return parseConversationTargetId(conversationId.value, meId.value)
}

async function load() {
  const token = loadRequestTracker.begin()
  error.value = ''
  loading.value = true
  try {
    const resp = await listImConversationMessages(conversationId.value, { afterSeq: 0, limit: 50 })
    if (!loadRequestTracker.isCurrent(token)) return
    const rows = Array.isArray(resp?.items) ? resp.items : []
    items.value = mergeConversationMessages([], rows.map((m) => mapConversationMessage(m)))

    const maxSeq = findLatestConversationSeq(items.value)
    if (maxSeq > 0) {
      await markImConversationRead(conversationId.value, maxSeq)
    }
    if (!loadRequestTracker.isCurrent(token)) return
    scrollToBottom()
  } catch (e) {
    if (!loadRequestTracker.isCurrent(token)) return
    error.value = e?.message || '加载失败'
  } finally {
    if (loadRequestTracker.isCurrent(token)) {
      loading.value = false
    }
  }
}

async function send() {
  if (!content.value.trim()) return
  const toId = targetId.value
  if (!toId) return
  
  sending.value = true
  try {
    if (!imRealtimeClient?.state?.connected) {
      throw new Error('IM 未连接')
    }
    const cmid = imRealtimeClient.sendPrivateText({ toUserId: toId, content: content.value })
    if (cmid) pendingClientMsgIds.add(String(cmid))
    content.value = ''
  } catch (e) {
    error.value = e?.message || '发送失败'
  } finally {
    sending.value = false
  }
}

function scrollToBottom() {
   nextTick(() => {
      if (chatArea.value) {
         chatArea.value.scrollTop = chatArea.value.scrollHeight
      }
   })
}

onMounted(load)

watch(conversationId, (nextConversationId, previousConversationId) => {
  if (!nextConversationId || nextConversationId === previousConversationId) return
  loadRequestTracker.invalidate()
  items.value = []
  error.value = ''
  pendingClientMsgIds.clear()
  load()
})

let offPrivate = null
let offSendCommitted = null
let offSendRejected = null
let offSendError = null
onMounted(() => {
  offPrivate = imRealtimeClient.on('privateMessage', async (msg) => {
    if (!msg || msg.conversationId !== conversationId.value) return
    const seq = Number(msg?.seq || 0)
    const message = mapConversationMessage({
      ...msg,
      createdAtEpochMs: msg?.createdAtEpochMs || Date.now()
    })

    items.value = mergeConversationMessages(items.value, [{
      ...message,
      seq
    }])
    scrollToBottom()

    // When this conversation is open, best-effort mark read to the latest seq.
    if (seq > 0 && sameOpaqueId(message.toId, meId.value)) {
      try { await markImConversationRead(conversationId.value, seq) } catch {}
    }
  })
})

onBeforeUnmount(() => {
  loadRequestTracker.invalidate()
  try { offPrivate?.() } catch {}
  try { offSendCommitted?.() } catch {}
  try { offSendRejected?.() } catch {}
  try { offSendError?.() } catch {}
})

onMounted(() => {
  offSendCommitted = imRealtimeClient.on('sendCommitted', (msg) => {
    if (String(msg?.cmd || '') !== 'sendPrivateText') return
    const cmid = String(msg?.clientMsgId || '')
    if (cmid) pendingClientMsgIds.delete(cmid)
  })

  offSendRejected = imRealtimeClient.on('sendRejected', (msg) => {
    if (String(msg?.cmd || '') !== 'sendPrivateText') return
    const cmid = String(msg?.clientMsgId || '')
    if (cmid && !pendingClientMsgIds.has(cmid)) return
    if (cmid) pendingClientMsgIds.delete(cmid)

    const message = String(msg?.message || '发送失败')
    error.value = message
    try {
      if (typeof window !== 'undefined' && window.$toast) {
        const traceId = String(msg?.traceId || '')
        const traceSuffix = traceId ? ` (traceId=${traceId})` : ''
        window.$toast({ type: 'error', title: '发送失败', text: `${message}${traceSuffix}` })
      }
    } catch {}
  })

  offSendError = imRealtimeClient.on('sendError', (msg) => {
    if (String(msg?.cmd || '') !== 'sendPrivateText') return
    const cmid = String(msg?.clientMsgId || '')
    if (cmid && !pendingClientMsgIds.has(cmid)) return
    if (cmid) pendingClientMsgIds.delete(cmid)

    const message = String(msg?.message || '发送失败')
    error.value = message
    try {
      if (typeof window !== 'undefined' && window.$toast) {
        const traceId = String(msg?.traceId || '')
        const traceSuffix = traceId ? ` (traceId=${traceId})` : ''
        window.$toast({ type: 'error', title: '发送失败', text: `${message}${traceSuffix}` })
      }
    } catch {}
  })
})
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

.chat-area {
  flex: 1;
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
  color: white;
  border-top-left-radius: 18px;
  border-top-right-radius: 6px;
}

.message-time {
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
  .chat-header {
    padding: 16px;
    flex-direction: column;
  }

  .chat-area {
    padding: 16px;
  }

  .chat-composer {
    padding: 12px;
  }

  .message-row {
    max-width: 88%;
  }
}
</style>
