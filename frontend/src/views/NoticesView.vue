<template>
  <div class="page" style="max-width: 800px; margin: 0 auto">
    <UiPageHeader>
        <template #title>Inbox</template>
        <template #subtitle>Stay updated with your community.</template>
        <template #actions>
           <UiButton variant="secondary" @click="load" :disabled="loading">{{ loading ? 'Refreshing...' : 'Refresh' }}</UiButton>
        </template>
    </UiPageHeader>

    <div v-if="error" class="error" style="margin-top: 12px">{{ error }}</div>
    
    <div style="margin-top: 24px">
       <UiEmpty v-if="items.length === 0 && !loading">No notifications yet.</UiEmpty>
       
       <div class="inbox-list">
          <RouterLink 
            v-for="it in items" 
            :key="it.topic" 
            :to="`/notices/${it.topic}`"
            class="inbox-item"
          >
             <!-- Icon -->
             <div class="inbox-icon" :class="it.topic">
                <svg v-if="it.topic === 'like'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path></svg>
                <svg v-else-if="it.topic === 'comment'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path></svg>
                <svg v-else-if="it.topic === 'follow'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="8.5" cy="7" r="4"></circle><line x1="20" y1="8" x2="20" y2="14"></line><line x1="23" y1="11" x2="17" y2="11"></line></svg>
                <svg v-else-if="it.topic === 'moderation'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l7 4v6c0 5-3 9-7 10-4-1-7-5-7-10V6l7-4z"></path></svg>
                <span v-else>#</span>
             </div>
             
             <!-- Content -->
             <div class="inbox-content">
                <div class="inbox-title">{{ getTopicTitle(it.topic) }}</div>
                <div class="inbox-sub">{{ it.noticeCount }} notifications · <span :class="{ 'unread-text': it.unreadCount > 0 }">{{ it.unreadCount }} unread</span></div>
             </div>
             
             <!-- Chevron -->
             <div class="muted">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"></polyline></svg>
             </div>
          </RouterLink>
       </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { topicSummary } from '../api/services/noticeService'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'

const emit = defineEmits(['trace'])
const loading = ref(false)
const error = ref('')
const items = ref([])

function getTopicTitle(topic) {
  const map = {
    'like': 'Likes',
    'comment': 'Comments',
    'follow': 'New Followers',
    'moderation': 'Moderation'
  }
  return map[topic] || topic
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    const { data, traceId } = await topicSummary()
    items.value = data
    emit('trace', traceId || '')
  } catch (e) {
    error.value = e?.message || 'Failed to load notifications'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.inbox-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.inbox-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: var(--surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  text-decoration: none;
  color: var(--text-1);
  transition: all 0.2s;
}
.inbox-item:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

.inbox-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}
.inbox-icon.like { background: #FF3B3015; color: #FF3B30; }
.inbox-icon.comment { background: #007AFF15; color: #007AFF; }
.inbox-icon.follow { background: #34C75915; color: #34C759; }
.inbox-icon.moderation { background: #FF950015; color: #FF9500; }

.inbox-content { flex: 1; }
.inbox-title { font-weight: 700; margin-bottom: 2px; }
.inbox-sub { font-size: 13px; color: var(--muted); }
.unread-text { color: var(--accent); font-weight: 600; }
</style>
