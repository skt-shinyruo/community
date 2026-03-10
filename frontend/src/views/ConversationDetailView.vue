<template>
  <div class="page chat-page">
    <UiCard class="chat-card">
      <!-- Header -->
      <div class="chat-header">
        <UiPageHeader>
          <template #title>
            <div class="row" style="gap: 10px; align-items: center">
              <RouterLink to="/messages" class="btn-icon" aria-label="返回会话列表" title="返回">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="15 18 9 12 15 6"></polyline>
                </svg>
              </RouterLink>
              <span>{{ displayTitle }}</span>
            </div>
          </template>
          <template #subtitle>会话详情</template>
          <template #actions>
            <UiButton variant="secondary" @click="load" :disabled="loading">刷新</UiButton>
          </template>
        </UiPageHeader>
      </div>

      <UiDivider />

      <!-- Messages Area -->
      <div class="chat-area" ref="chatArea">
        <UiEmpty v-if="error && items.length === 0" type="error">{{ error }}</UiEmpty>
        <div v-else-if="loading && items.length === 0" class="muted" style="text-align: center; margin-top: 20px">加载中…</div>
        <UiEmpty v-else-if="items.length === 0">暂无消息，打个招呼吧。</UiEmpty>

        <div v-else class="message-list">
          <div v-for="m in sortedItems" :key="m.id" class="message-row" :class="{ mine: m.fromId === meId }">
            <div class="message-bubble">{{ m.content }}</div>
            <div class="message-time">{{ formatTimeShort(m.createTime) }}</div>
          </div>
        </div>
      </div>

      <!-- Input Area -->
      <div class="chat-input-area">
        <textarea
          class="chat-input"
          v-model="content"
          placeholder="输入消息…"
          @keydown.enter.prevent="send"
          rows="1"
        ></textarea>
        <button
          class="send-btn"
          type="button"
          aria-label="发送消息"
          title="发送"
          @click="send"
          :disabled="sending || !content.trim()"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="22" y1="2" x2="11" y2="13"></line>
            <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
          </svg>
        </button>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { listImConversationMessages, markImConversationRead } from '../api/services/imCoreChatService'
import { imRealtimeClient } from '../im/imRealtimeClient'
import UiCard from '../components/ui/UiCard.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiDivider from '../components/ui/UiDivider.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'

const emit = defineEmits(['trace'])
const props = defineProps({ conversationId: String })
const auth = useAuthStore()
const meId = computed(() => Number(auth.userId || 0))

const loading = ref(false)
const items = ref([])
const error = ref('')
const content = ref('')
const sending = ref(false)
const chatArea = ref(null)

const conversationId = computed(() => props.conversationId || '')
const targetId = computed(() => parseTargetId())

// Try to guess other user name or just show Conversation ID
const displayTitle = computed(() => {
   if (targetId.value) return `与用户 #${targetId.value} 的对话`
   return '私信'
})

const sortedItems = computed(() => {
   // Ensure chronological order
   return [...items.value].sort((a, b) => new Date(a.createTime) - new Date(b.createTime))
})

function formatTimeShort(ts) {
   return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function parseTargetId() {
  const cid = conversationId.value
  const parts = cid.split('_').map((x) => parseInt(x, 10)).filter((x) => Number.isFinite(x))
  if (parts.length !== 2) return 0
  const [a, b] = parts
  return a === meId.value ? b : a
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    const resp = await listImConversationMessages(conversationId.value, { afterSeq: 0, limit: 50 })
    const rows = Array.isArray(resp?.items) ? resp.items : []
    items.value = rows.map((m) => ({
      id: Number(m?.messageId || m?.seq || 0),
      seq: Number(m?.seq || 0),
      fromId: Number(m?.fromUserId || 0),
      toId: Number(m?.toUserId || 0),
      content: String(m?.content || ''),
      createTime: Number(m?.createdAtEpochMs || 0)
    }))

    const maxSeq = items.value.reduce((acc, m) => Math.max(acc, Number(m?.seq || 0)), 0)
    if (maxSeq > 0) {
      await markImConversationRead(conversationId.value, maxSeq)
    }
    
    scrollToBottom()
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
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
    imRealtimeClient.sendPrivateText({ toUserId: toId, content: content.value })
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

let offPrivate = null
onMounted(() => {
  offPrivate = imRealtimeClient.on('privateMessage', async (msg) => {
    if (!msg || msg.conversationId !== conversationId.value) return
    const seq = Number(msg?.seq || 0)
    const messageId = Number(msg?.messageId || seq || 0)
    const fromId = Number(msg?.fromUserId || 0)
    const toId = Number(msg?.toUserId || 0)
    const createTime = Number(msg?.createdAtEpochMs || Date.now())
    const contentText = String(msg?.content || '')

    items.value.push({
      id: messageId,
      seq,
      fromId,
      toId,
      content: contentText,
      createTime
    })
    scrollToBottom()

    // When this conversation is open, best-effort mark read to the latest seq.
    if (seq > 0 && toId === meId.value) {
      try { await markImConversationRead(conversationId.value, seq) } catch {}
    }
  })
})

onBeforeUnmount(() => {
  try { offPrivate?.() } catch {}
})
</script>

<style scoped>
.chat-page {
  gap: var(--space-4);
}

.chat-card {
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 72vh;
}

.chat-header {
  padding: 14px 16px;
}

.chat-area {
   flex: 1;
   background: var(--bg);
   overflow-y: auto;
   padding: 24px;
   display: flex;
   flex-direction: column;
}

.message-list {
   display: flex;
   flex-direction: column;
   gap: 16px;
}

.message-row {
   display: flex;
   flex-direction: column;
   align-items: flex-start; /* Friend messages left */
   max-width: 70%;
   align-self: flex-start;
}
.message-row.mine {
   align-items: flex-end; /* My messages right */
   align-self: flex-end;
}

.message-bubble {
   padding: 12px 16px;
   background: var(--surface);
   border-radius: 12px;
   border-top-left-radius: 2px;
   box-shadow: var(--shadow-sm);
   font-size: 15px;
   line-height: 1.5;
   color: var(--text-1);
}
.message-row.mine .message-bubble {
   background: var(--accent);
   color: white;
   border-top-left-radius: 12px;
   border-top-right-radius: 2px;
}

.message-time {
   font-size: 11px;
   color: var(--text-3);
   margin-top: 4px;
   padding: 0 4px;
}

.chat-input-area {
   padding: 16px 24px;
   background: var(--surface);
   border-top: 1px solid var(--border);
   display: flex;
   gap: 12px;
   align-items: flex-end;
}
.chat-input {
   flex: 1;
   background: var(--bg);
   border: none;
   border-radius: 20px;
   padding: 12px 16px;
   font-family: inherit;
   font-size: 15px;
   outline: none;
   resize: none;
   min-height: 44px;
}
.chat-input:focus {
   box-shadow: var(--focus-ring);
}
.send-btn {
   width: 44px; height: 44px;
   border-radius: 50%;
   background: var(--accent);
   color: white;
   border: none;
   display: flex; 
   align-items: center; 
   justify-content: center;
   cursor: pointer;
   transition: transform 0.1s;
}
.send-btn:active { transform: scale(0.95); }
.send-btn:disabled { opacity: 0.5; cursor: default; }

@media (max-width: 768px) {
  .chat-header {
    padding: 12px;
  }

  .chat-area {
    padding: 16px;
  }

  .chat-input-area {
    padding: 12px;
  }
}
</style>
