<template>
  <div class="page conversations-page">
    <UiPageHeader>
      <template #title>私信</template>
      <template #subtitle>查看私信、未读消息和需要跟进的成员对话。</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="loading" @click="reload">刷新</UiButton>
      </template>
    </UiPageHeader>

    <div class="conversations-feed">
      <div v-if="loading && items.length === 0" class="conversations-skeletons">
        <UiSkeleton variant="list" :rows="4" label="加载会话列表" />
      </div>

      <UiState v-else-if="error && items.length === 0" variant="error" class="conversations-empty" :title="error">
        <template #description>会话列表加载失败，可以重试或稍后再来。</template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" @click="reload">重试</UiButton>
        </template>
      </UiState>

      <UiState v-else-if="items.length === 0" class="conversations-empty">
        暂无会话
        <template #description>当有人与你发起私信后，这里会显示最新线程和未读状态。</template>
        <template #actions>
          <UiButton :to="{ name: 'posts' }">去社区逛逛</UiButton>
        </template>
      </UiState>

      <template v-else>
        <div class="conversations-meta">
          <span><strong>{{ pendingCount }}</strong> 个对话待处理</span>
        </div>

        <div v-if="error" class="error conversations-inline-error">{{ error }}</div>

        <div class="conv-list">
          <RouterLink
            v-for="c in items"
            :key="c.conversationId"
            :to="`/messages/${encodeURIComponent(c.conversationId)}`"
            class="conv-card"
            :class="{ 'conv-card--unread': Number(c?.unreadCount || 0) > 0 }"
          >
            <UiAvatar :src="''" :name="shortParticipant(c?.otherUserId)" :size="44" />

            <div class="conv-card-body">
              <div class="conv-card-head">
                <span class="conv-card-title">{{ shortParticipant(c?.otherUserId) }}</span>
                <span v-if="c.lastMessage" class="conv-card-time">{{ formatTimeShort(c.lastMessage.createdAtEpochMs) }}</span>
              </div>

              <p class="conv-card-preview">
                {{ c.lastMessage?.content || '暂时还没有文本消息，打开线程可以继续交流。' }}
              </p>

              <div class="conv-card-foot">
                <span class="conv-card-status">{{ Number(c?.unreadCount || 0) > 0 ? '等待你的回复' : '线程已同步' }}</span>
                <span v-if="Number(c?.unreadCount || 0) > 0" class="conv-card-chip">{{ formatUnreadCount(c.unreadCount) }} 条未读</span>
              </div>
            </div>
          </RouterLink>
        </div>

        <div v-if="pageError" class="error conversations-inline-error">{{ pageError }}</div>

        <div v-if="loadingMore || hasMore" class="conversations-load-more">
          <UiButton v-if="loadingMore" variant="ghost" disabled>
            <LoaderCircle :size="14" aria-hidden="true" class="conversations-load-more-spinner" />
            正在加载…
          </UiButton>
          <UiButton
            v-else
            data-testid="load-more-conversations"
            variant="secondary"
            class="conversations-load-more-btn"
            @click="loadMore"
          >
            加载更多
          </UiButton>
        </div>
        <div v-else class="conversations-end-note">已经到底了</div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { LoaderCircle } from 'lucide-vue-next'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiState from '../components/ui/UiState.vue'
import { formatUnreadCount } from '../stores/inboxUnread'
import { formatConversationTime as formatTimeShort } from '../utils/time'
import { useConversationsFeed } from './useConversationsFeed'

const {
  items,
  loading,
  loadingMore,
  error,
  pageError,
  hasMore,
  pendingCount,
  reload,
  loadMore
} = useConversationsFeed()

function shortParticipant(value) {
  const raw = String(value || '').trim()
  if (!raw) return '社区成员'
  return `社区成员 ${raw.slice(0, 8)}`
}
</script>

<style scoped>
.conversations-page {
  max-width: 960px;
  margin: 0 auto;
  gap: var(--space-5);
}

.conversations-feed {
  display: grid;
  gap: var(--space-3);
}

.conversations-skeletons {
  padding: var(--space-2) 0;
}

.conversations-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  padding: 0 2px;
  font-size: var(--text-xs);
  color: var(--text-3);
}

.conversations-meta strong {
  color: var(--text-1);
}

.conversations-inline-error {
  font-size: var(--text-sm);
}

.conv-list {
  display: grid;
  gap: var(--space-3);
}

.conv-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  overflow: hidden;
  text-decoration: none;
  color: var(--text-1);
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.conv-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: transparent;
  transition: background-color var(--duration-fast) var(--ease-standard);
}

.conv-card:hover {
  border-color: var(--border-strong);
  background: color-mix(in srgb, var(--surface) 55%, var(--surface-2));
}

.conv-card:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.conv-card--unread::before {
  background: var(--accent);
}

.conv-card-body {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: var(--space-2);
}

.conv-card-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: var(--space-3);
}

.conv-card-title {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: 0;
  color: var(--text-1);
}

.conv-card-time {
  flex: none;
  font-size: 13px;
  color: var(--text-3);
  white-space: nowrap;
}

.conv-card-preview {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--text-2);
  line-height: 1.6;
  word-break: break-word;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.conv-card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.conv-card-status {
  font-size: 13px;
  color: var(--text-3);
}

.conv-card-chip {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--accent-weak);
  color: var(--accent-text);
  font-size: var(--text-xs);
  font-weight: 600;
}

.conversations-load-more {
  display: flex;
  justify-content: center;
  padding-top: var(--space-2);
}

.conversations-load-more-btn {
  min-width: 260px;
}

.conversations-load-more-spinner {
  animation: conversations-load-more-spin 0.8s linear infinite;
}

@keyframes conversations-load-more-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .conversations-load-more-spinner {
    animation: none;
  }
}

.conversations-end-note {
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
}

@media (max-width: 768px) {
  .conv-card {
    padding: var(--space-4);
  }

  .conv-card-foot {
    align-items: flex-start;
  }

  .conversations-load-more-btn {
    min-width: 0;
    width: 100%;
  }
}
</style>
