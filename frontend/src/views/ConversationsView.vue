<template>
  <div class="page conversations-page">
    <div v-if="error && items.length > 0" class="error conversations-banner">{{ error }}</div>

    <UiCard class="conversations-shell">
      <div class="conversations-shell-head">
        <UiPageHeader>
          <template #title>私信</template>
          <template #subtitle>把仍在推进的讨论、协作和跟进放回同一个收件箱里。</template>
          <template #actions>
            <UiButton variant="secondary" @click="load" :disabled="loading">刷新</UiButton>
          </template>
        </UiPageHeader>
        <div class="conversations-head-meta muted">
          {{ items.filter((c) => Number(c?.unreadCount || 0) > 0).length }} 个对话待处理
        </div>
      </div>

      <UiState v-if="error && items.length === 0" variant="error" class="conversations-empty">{{ error }}</UiState>
      <div v-else-if="loading && items.length === 0" class="muted conversations-state">正在整理你的收件箱…</div>
      <UiState v-else-if="items.length === 0" class="conversations-empty">
        暂无会话
        <template #description>当有人与你发起私信后，这里会显示最新线程和未读状态。</template>
      </UiState>

      <div v-else class="conv-list">
        <RouterLink
          v-for="c in items"
          :key="c.conversationId"
          :to="`/messages/${encodeURIComponent(c.conversationId)}`"
          class="conv-item"
          :class="{ unread: c.unreadCount > 0 }"
        >
          <div class="conv-avatar-wrap">
            <UiAvatar :src="''" :name="`社区成员 ${c?.otherUserId || ''}`" :size="52" />
            <span v-if="c.unreadCount > 0" class="conv-dot" aria-hidden="true"></span>
          </div>

          <div class="conv-content">
            <div class="conv-top">
              <div class="conv-heading">
                <span class="conv-name">{{ c.unreadCount > 0 ? '有新消息待查看' : '继续这段对话' }}</span>
                <span class="conv-context">成员 #{{ c?.otherUserId || '?' }}</span>
              </div>
              <span class="conv-time" v-if="c.lastMessage">{{ formatTimeShort(c.lastMessage.createdAtEpochMs) }}</span>
            </div>

            <div class="conv-preview">
              {{ c.lastMessage?.content || '暂时还没有文本消息，打开线程可以继续交流。' }}
            </div>

            <div class="conv-footer">
              <span class="conv-status">{{ c.unreadCount > 0 ? '等待你的回复' : '线程已同步' }}</span>
              <span v-if="c.unreadCount > 0" class="unread-badge">{{ c.unreadCount }} 条未读</span>
            </div>
          </div>
        </RouterLink>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { listImConversations } from '../api/services/imCoreChatService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiState from '../components/ui/UiState.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'

const emit = defineEmits(['trace'])
const loading = ref(false)
const error = ref('')
const items = ref([])

function formatTimeShort(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const isToday =
    d.getDate() === now.getDate() && d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear()

  if (isToday) {
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString()
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    items.value = await listImConversations({ page: 0, size: 20 })
    emit('trace', '')
  } catch (e) {
    error.value = e?.message || '加载会话失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.conversations-page {
  max-width: 960px;
  margin: 0 auto;
  gap: var(--space-5);
}

.conversations-banner {
  margin-top: -6px;
}

.conversations-shell {
  padding: 0;
  overflow: hidden;
}

.conversations-shell-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-end;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
}

.conversations-shell-head :deep(.page-header) {
  gap: 0;
}

.conversations-shell-head :deep(.page-header-subtitle) {
  margin: 4px 0 0;
}

.conversations-head-meta {
  white-space: nowrap;
}

.conversations-state,
.conversations-empty {
  padding: 48px 24px;
}

.conv-list {
  display: flex;
  flex-direction: column;
}

.conv-item {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  padding: 20px 24px;
  text-decoration: none;
  color: var(--text-1);
  border-bottom: 1px solid var(--border);
  transition: background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.conv-item:last-child {
  border-bottom: none;
}

.conv-item:hover {
  background: color-mix(in srgb, var(--surface) 92%, var(--accent-weak) 8%);
}

.conv-item.unread {
  background: color-mix(in srgb, var(--surface) 88%, var(--accent-weak) 12%);
}

.conv-item.unread:hover {
  background: color-mix(in srgb, var(--surface) 84%, var(--accent-weak) 16%);
}

.conv-avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.conv-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 12px;
  height: 12px;
  border-radius: 999px;
  background: var(--accent);
  border: 2px solid var(--surface);
}

.conv-content {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 10px;
}

.conv-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.conv-heading {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.conv-name {
  font-weight: 700;
  font-size: 15px;
}

.conv-context {
  font-size: 12px;
  color: var(--text-3);
}

.conv-time {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}

.conv-preview {
  font-size: 14px;
  color: var(--text-2);
  line-height: 1.55;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.conv-item.unread .conv-preview {
  color: var(--text-1);
}

.conv-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.conv-status {
  font-size: 12px;
  color: var(--text-3);
}

.unread-badge {
  background: color-mix(in srgb, var(--accent) 18%, white 82%);
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
  padding: 6px 10px;
  border-radius: 999px;
  min-width: 18px;
  text-align: center;
}

@media (max-width: 768px) {
  .conversations-shell-head,
  .conv-item {
    padding-left: 18px;
    padding-right: 18px;
  }

  .conversations-shell-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .conv-top,
  .conv-footer {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
