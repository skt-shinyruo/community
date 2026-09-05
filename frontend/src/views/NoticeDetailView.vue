<template>
  <div class="page notice-detail-page">
    <nav class="notice-detail-nav" aria-label="页面层级">
      <UiButton variant="ghost" class="notice-detail-back" :to="{ name: 'notices' }">
        <ArrowLeft :size="16" aria-hidden="true" />
        返回通知汇总
      </UiButton>
    </nav>

    <UiPageHeader>
      <template #title>{{ policy.title }}</template>
      <template #subtitle>{{ policy.subtitle }}</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="loading || markingRead || !hasUnread" @click="markAllRead">标记已读</UiButton>
        <UiButton variant="secondary" :disabled="loading || markingRead" @click="reload">刷新</UiButton>
      </template>
    </UiPageHeader>

    <div class="notice-feed">
      <div v-if="loading && cards.length === 0" class="notice-detail-skeletons">
        <UiSkeleton variant="list" :rows="4" />
      </div>

      <UiState v-if="error && cards.length === 0" variant="error" :title="error">
        <template #description>通知列表加载失败，可以重试或返回通知汇总。</template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" @click="reload">重试</UiButton>
        </template>
      </UiState>
      <div v-else-if="error" class="error notice-detail-inline-error" role="alert">{{ error }}</div>

      <UiState v-if="!loading && !error && cards.length === 0">
        暂无通知
        <template #description>这一类通知当前没有更多记录，稍后可以刷新再看。</template>
        <template #actions>
          <UiButton variant="secondary" :to="{ name: 'notices' }">返回通知汇总</UiButton>
        </template>
      </UiState>

      <div v-if="cards.length > 0" class="notice-list">
        <article v-for="n in cards" :key="n.id" class="notice-card" :class="{ unread: !n.read }">
          <div class="notice-card-head">
            <h2 class="notice-card-title">{{ n.title }}</h2>
            <div class="notice-card-time">{{ formatTime(n.createTime) }}</div>
          </div>

          <p class="notice-card-body">{{ n.body }}</p>

          <div class="notice-card-meta">
            <UiBadge :variant="n.read ? 'default' : 'accent'">{{ n.read ? '已读' : '未读' }}</UiBadge>
            <span v-if="n.actorLabel">{{ n.actorLabel }}</span>
            <span v-if="n.postId">可返回帖子查看上下文</span>
          </div>

          <div v-if="n.postId" class="notice-card-actions">
            <UiButton variant="secondary" :to="`/posts/${n.postId}`">查看相关帖子</UiButton>
          </div>
        </article>
      </div>

      <div v-if="pageError" class="error notice-detail-inline-error" role="alert">{{ pageError }}</div>

      <div v-if="loadingMore || (hasNext && cards.length > 0)" class="notice-detail-load-more">
        <UiButton v-if="loadingMore" variant="ghost" disabled>
          <LoaderCircle :size="14" aria-hidden="true" class="notice-detail-load-more-spinner" />
          正在加载…
        </UiButton>
        <UiButton v-else variant="secondary" class="notice-detail-load-more-btn" @click="loadMore">加载更多</UiButton>
      </div>
      <div v-if="!hasNext && cards.length > 0" class="notice-detail-end-note">已经到底了</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowLeft, LoaderCircle } from 'lucide-vue-next'
import UiBadge from '../components/ui/UiBadge.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiState from '../components/ui/UiState.vue'
import { formatTime } from '../utils/time'
import { useNoticeTopicFeedState } from './notices/useNoticeTopicFeedState'

const props = defineProps({ topic: String })

const {
  cards,
  error,
  hasNext,
  hasUnread,
  loading,
  loadingMore,
  loadMore,
  markAllRead,
  markingRead,
  pageError,
  policy,
  reload
} = useNoticeTopicFeedState({ topic: computed(() => String(props.topic || '')) })
</script>

<style scoped>
.notice-detail-page {
  max-width: 980px;
  margin: 0 auto;
  gap: var(--space-5);
}

.notice-detail-nav {
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

.notice-detail-back {
  flex: none;
}

.notice-feed {
  display: grid;
  gap: var(--space-3);
}

.notice-detail-skeletons {
  display: grid;
  gap: var(--space-3);
}

.notice-detail-inline-error {
  font-size: var(--text-sm);
}

.notice-list {
  display: grid;
  gap: var(--space-3);
}

.notice-card {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-6);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.notice-card.unread {
  box-shadow: inset 3px 0 0 0 var(--accent);
}

.notice-card-head {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  align-items: flex-start;
}

.notice-card-title {
  margin: 0;
  font-size: 19px;
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: 0;
  color: var(--text-1);
}

.notice-card-time {
  font-size: var(--text-xs);
  color: var(--text-3);
  white-space: nowrap;
}

.notice-card-body {
  margin: 0;
  color: var(--text-2);
  font-size: var(--text-sm);
  line-height: 1.7;
}

.notice-card-meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--text-3);
}

.notice-card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.notice-detail-load-more {
  display: flex;
  justify-content: center;
  padding-top: var(--space-2);
}

.notice-detail-load-more-btn {
  min-width: 260px;
}

.notice-detail-load-more-spinner {
  animation: notice-detail-load-more-spin 0.8s linear infinite;
}

@keyframes notice-detail-load-more-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .notice-detail-load-more-spinner {
    animation: none;
  }
}

.notice-detail-end-note {
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
}

@media (max-width: 768px) {
  .notice-card {
    padding: var(--space-4);
  }

  .notice-card-head {
    flex-direction: column;
  }

  .notice-detail-load-more-btn {
    min-width: 0;
    width: 100%;
  }
}
</style>
