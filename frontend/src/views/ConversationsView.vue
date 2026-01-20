<template>
  <div class="page" style="max-width: 800px; margin: 0 auto">
    <UiPageHeader>
        <template #title>私信</template>
        <template #actions>
           <UiButton variant="secondary" @click="load" :disabled="loading">刷新</UiButton>
        </template>
    </UiPageHeader>

    <div v-if="error && items.length > 0" class="error" style="margin-top: 12px">{{ error }}</div>

    <UiCard style="margin-top: 12px; padding: 0; overflow: hidden">
       <UiEmpty v-if="error && items.length === 0" type="error" style="padding: 40px">{{ error }}</UiEmpty>
       <div v-else-if="loading && items.length === 0" class="muted" style="padding: 40px; text-align: center">加载中…</div>
       <UiEmpty v-else-if="items.length === 0" style="padding: 40px">暂无会话</UiEmpty>
       
       <div class="conv-list">
          <RouterLink 
            v-for="c in items" 
            :key="c.conversationId" 
            :to="`/messages/${encodeURIComponent(c.conversationId)}`"
            class="conv-item"
            :class="{ unread: c.unreadCount > 0 }"
          >
             <UiAvatar :src="c?.targetUser?.headerUrl || ''" :name="c?.targetUser?.username || '?'" :size="48" />
             
             <div class="conv-content">
                <div class="conv-top">
                   <span class="conv-name">{{ c?.targetUser?.username || `User ${c?.targetUser?.id}` }}</span>
                   <span class="conv-time" v-if="c.lastMessage">{{ formatTimeShort(c.lastMessage.createTime) }}</span>
                </div>
                <div class="conv-bottom">
                   <span class="conv-preview">{{ c.lastMessage?.content || '（暂无消息）' }}</span>
                   <span v-if="c.unreadCount > 0" class="unread-badge">{{ c.unreadCount }}</span>
                </div>
             </div>
          </RouterLink>
       </div>
    </UiCard>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { listConversationItems } from '../api/services/messageService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'

const emit = defineEmits(['trace'])
const loading = ref(false)
const error = ref('')
const items = ref([])

function formatTimeShort(ts) {
   if (!ts) return ''
   const d = new Date(ts)
   const now = new Date()
   const isToday = d.getDate() === now.getDate() && d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear()
   
   if (isToday) {
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
   }
   return d.toLocaleDateString()
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    const { data, traceId } = await listConversationItems({ page: 0, size: 20 })
    items.value = data
    emit('trace', traceId || '')
  } catch (e) {
    error.value = e?.message || '加载会话失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.conv-list {
  display: flex;
  flex-direction: column;
}
.conv-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  text-decoration: none;
  color: var(--text-1);
  border-bottom: 1px solid var(--border);
  transition: background 0.2s;
}
.conv-item:last-child { border-bottom: none; }
.conv-item:hover { background: var(--surface-2); }
.conv-item.unread { background: var(--bg); }
.conv-item.unread:hover { background: var(--surface-2); }

.conv-content { flex: 1; min-width: 0; }
.conv-top { display: flex; justify-content: space-between; margin-bottom: 4px; }
.conv-name { font-weight: 600; font-size: 15px; }
.conv-time { font-size: 12px; color: var(--text-3); }

.conv-bottom { display: flex; justify-content: space-between; align-items: center; }
.conv-preview { font-size: 14px; color: var(--text-3); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 90%; }
.conv-item.unread .conv-preview { color: var(--text-1); font-weight: 500; }

.unread-badge {
   background: var(--accent);
   color: white;
   font-size: 11px;
   font-weight: 700;
   padding: 2px 6px;
   border-radius: 10px;
   min-width: 18px;
   text-align: center;
}
</style>
