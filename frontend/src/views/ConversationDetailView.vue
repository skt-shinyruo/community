<template>
  <div class="page" style="padding: 0; height: calc(100vh - 80px); display: flex; flex-direction: column; max-width: 900px; margin: 0 auto">
    <!-- Chat Header -->
    <div style="padding: 16px 24px; border-bottom: 1px solid var(--border); background: var(--surface); display: flex; justify-content: space-between; align-items: center">
       <div class="row" style="gap: 12px">
          <RouterLink to="/messages" class="btn-icon" style="margin-left: -8px">
             <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"></polyline></svg>
          </RouterLink>
          <div class="stack" style="gap: 2px">
             <div style="font-weight: 700; font-size: 16px">{{ displayTitle }}</div>
             <div class="muted" style="font-size: 12px">Conversation Details</div>
          </div>
       </div>
       <UiButton variant="secondary" @click="load" :disabled="loading" size="sm">Refresh</UiButton>
    </div>

    <!-- Messages Area -->
    <div class="chat-area" ref="chatArea">
       <div v-if="loading && items.length === 0" class="muted" style="text-align: center; margin-top: 20px">Loading...</div>
       <UiEmpty v-else-if="items.length === 0">No messages yet. Say hello!</UiEmpty>
       
       <div v-else class="message-list">
          <div 
             v-for="m in sortedItems" 
             :key="m.id" 
             class="message-row"
             :class="{ 'mine': m.fromId === meId }"
          >
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
          placeholder="Type a message..." 
          @keydown.enter.prevent="send"
          rows="1"
       ></textarea>
       <button class="send-btn" @click="send" :disabled="sending || !content.trim()">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>
       </button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { listLetters, markRead, sendMessage } from '../api/services/messageService'
import UiButton from '../components/ui/UiButton.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'

const emit = defineEmits(['trace'])
const props = defineProps({ conversationId: String })
const auth = useAuthStore()
const meId = computed(() => Number(auth.userId || 0))

const loading = ref(false)
const items = ref([])
const content = ref('')
const sending = ref(false)
const chatArea = ref(null)

const conversationId = computed(() => props.conversationId || '')

// Try to guess other user name or just show Conversation ID
const displayTitle = computed(() => {
   // Ideally we would have user info, but for now reuse conversationId logic or what we have
   return 'Chat' 
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
  loading.value = true
  try {
    const { data, traceId } = await listLetters(conversationId.value, { page: 0, size: 50 })
    items.value = data
    emit('trace', traceId || '')

    const unreadIds = data
      .filter((m) => m?.toId === meId.value && m?.status === 0)
      .map((m) => m.id)
      .filter((id) => typeof id === 'number' && id > 0)
    if (unreadIds.length > 0) {
      await markRead(unreadIds)
    }
    
    scrollToBottom()
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

async function send() {
  if (!content.value.trim()) return
  const toId = parseTargetId()
  if (!toId) return
  
  sending.value = true
  try {
    await sendMessage({ toId, content: content.value })
    content.value = ''
    await load()
  } catch (e) {
    console.error(e)
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
</script>

<style scoped>
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
   color: var(--muted);
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
   box-shadow: 0 0 0 2px rgba(0,113,227,0.1);
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
</style>
