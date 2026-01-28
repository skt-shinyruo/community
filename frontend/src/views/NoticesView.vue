<template>
  <div class="page reading">
    <UiCard>
      <UiPageHeader>
        <template #title>通知</template>
        <template #subtitle>系统与互动消息汇总</template>
        <template #actions>
          <UiButton variant="secondary" @click="load" :disabled="loading">{{ loading ? '刷新中…' : '刷新' }}</UiButton>
        </template>
      </UiPageHeader>

      <div style="margin-top: 12px">
        <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
        <UiEmpty v-else-if="items.length === 0 && !loading">暂无通知</UiEmpty>

        <div v-else class="inbox-list">
          <RouterLink v-for="it in items" :key="it.topic" :to="`/notices/${it.topic}`" class="inbox-item">
            <div class="inbox-icon" :class="it.topic" aria-hidden="true">
              <svg v-if="it.topic === 'like'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path
                  d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"
                ></path>
              </svg>
              <svg v-else-if="it.topic === 'comment'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path
                  d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"
                ></path>
              </svg>
              <svg v-else-if="it.topic === 'follow'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                <circle cx="8.5" cy="7" r="4"></circle>
                <line x1="20" y1="8" x2="20" y2="14"></line>
                <line x1="23" y1="11" x2="17" y2="11"></line>
              </svg>
              <svg v-else-if="it.topic === 'moderation'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2l7 4v6c0 5-3 9-7 10-4-1-7-5-7-10V6l7-4z"></path>
              </svg>
              <span v-else style="font-weight: 900">#</span>
            </div>

            <div class="inbox-content">
              <div class="inbox-title">{{ getTopicTitle(it.topic) }}</div>
              <div class="inbox-sub">
                共 {{ it.noticeCount }} 条 ·
                <span :class="{ 'unread-text': it.unreadCount > 0 }">未读 {{ it.unreadCount }}</span>
              </div>
            </div>

            <div class="muted" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="9 18 15 12 9 6"></polyline>
              </svg>
            </div>
          </RouterLink>
        </div>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { topicSummary } from '../api/services/noticeService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'

const emit = defineEmits(['trace'])
const loading = ref(false)
const error = ref('')
const items = ref([])

function getTopicTitle(topic) {
  const map = {
    like: '点赞',
    comment: '评论',
    follow: '关注',
    moderation: '治理'
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
    error.value = e?.message || '加载通知失败'
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
  gap: 10px;
  margin-top: 12px;
}
.inbox-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 14px;
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  text-decoration: none;
  color: var(--text-1);
  transition: background 0.18s ease-out, border-color 0.18s ease-out, box-shadow 0.18s ease-out;
}
.inbox-item:hover {
  background: var(--surface);
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.inbox-item:focus-visible {
  box-shadow: var(--shadow-sm), var(--focus-ring);
  outline: none;
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
.inbox-icon.like { background: var(--danger-weak); color: var(--danger); }
.inbox-icon.comment { background: var(--accent-weak); color: var(--accent); }
.inbox-icon.follow { background: var(--success-weak); color: var(--success); }
.inbox-icon.moderation { background: var(--warning-weak); color: var(--warning); }

.inbox-content { flex: 1; }
.inbox-title { font-weight: 800; margin-bottom: 2px; }
.inbox-sub { font-size: 13px; color: var(--text-2); }
.unread-text { color: var(--accent); font-weight: 600; }

@media (max-width: 768px) {
  .inbox-item {
    padding: 12px;
  }

  .inbox-icon {
    width: 42px;
    height: 42px;
    border-radius: 10px;
  }
}
</style>
