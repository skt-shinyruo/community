<template>
  <div class="page notices-page">
    <UiPageHeader>
      <template #title>通知</template>
      <template #subtitle>查看评论、点赞、关注和治理提醒。</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="loading" @click="reload">刷新</UiButton>
      </template>
    </UiPageHeader>

    <div class="notices-feed">
      <div v-if="loading && items.length === 0" class="notices-skeletons">
        <UiSkeleton variant="list" :rows="4" />
      </div>

      <UiState v-if="error && items.length === 0" variant="error" :title="error">
        <template #description>通知汇总加载失败，可以重试或稍后再来。</template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" @click="reload">重试</UiButton>
        </template>
      </UiState>
      <div v-else-if="error" class="error notices-inline-error" role="alert">{{ error }}</div>

      <UiState v-if="!loading && !error && items.length === 0">
        暂无通知
        <template #description>当有人与你互动，或系统需要提醒你时，这里会按主题出现新的通知流。</template>
        <template #actions>
          <UiButton :to="{ name: 'posts' }">浏览帖子</UiButton>
        </template>
      </UiState>

      <template v-if="items.length > 0">
        <p class="notices-list-meta">{{ pendingTopicCount }} 个主题需要处理</p>
        <div class="notices-list">
          <RouterLink
            v-for="it in items"
            :key="it.topic"
            :to="`/notices/${it.topic}`"
            class="notice-topic-card"
            :class="{ unread: noticeUnreadCount(it) > 0 }"
          >
            <div class="notice-topic-icon" :class="`topic-${topicIconKey(it.topic)}`" aria-hidden="true">
              <component :is="topicIcon(it.topic)" :size="20" />
            </div>

            <div class="notice-topic-copy">
              <div class="notice-topic-title-row">
                <span class="notice-topic-title">{{ noticeTopicPresentation(it.topic).title }}</span>
                <UiBadge v-if="noticeUnreadCount(it) > 0" variant="accent">未读 {{ noticeUnreadCount(it) }}</UiBadge>
              </div>
              <div class="notice-topic-sub">{{ noticeTopicPresentation(it.topic).description }}</div>
              <div class="notice-topic-meta">共 {{ Number(it?.noticeCount || 0) }} 条</div>
            </div>

            <div class="notice-topic-tail" aria-hidden="true">
              <span class="notice-topic-open">打开通知</span>
              <ChevronRight :size="16" />
            </div>
          </RouterLink>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { Bell, ChevronRight, Heart, MessageCircle, Shield, UserPlus } from 'lucide-vue-next'
import UiBadge from '../components/ui/UiBadge.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiState from '../components/ui/UiState.vue'
import {
  noticeTopicPresentation,
  noticeUnreadCount,
  useNoticesSummaryState
} from './notices/useNoticesSummaryState'

const { items, loading, error, pendingTopicCount, reload } = useNoticesSummaryState()

const TOPIC_ICONS = {
  like: Heart,
  comment: MessageCircle,
  follow: UserPlus,
  moderation: Shield
}

function topicIconKey(topic) {
  const key = String(topic || '')
  return TOPIC_ICONS[key] ? key : 'fallback'
}

function topicIcon(topic) {
  return TOPIC_ICONS[topicIconKey(topic)] || Bell
}
</script>

<style scoped>
.notices-page {
  max-width: 980px;
  margin: 0 auto;
  gap: var(--space-5);
}

.notices-feed {
  display: grid;
  gap: var(--space-3);
}

.notices-skeletons {
  display: grid;
  gap: var(--space-3);
}

.notices-inline-error {
  font-size: var(--text-sm);
}

.notices-list-meta {
  margin: 0;
  font-size: 13px;
  color: var(--text-3);
}

.notices-list {
  display: grid;
  gap: var(--space-3);
}

.notice-topic-card {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  text-decoration: none;
  color: var(--text-1);
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.notice-topic-card:hover {
  border-color: var(--border-strong);
  background: color-mix(in srgb, var(--surface) 55%, var(--surface-2));
}

.notice-topic-card.unread {
  box-shadow: inset 3px 0 0 0 var(--accent);
}

.notice-topic-card:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.notice-topic-card.unread:focus-visible {
  box-shadow: inset 3px 0 0 0 var(--accent), var(--focus-ring);
}

.notice-topic-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.notice-topic-icon.topic-like {
  background: var(--danger-weak);
  color: var(--danger);
}

.notice-topic-icon.topic-comment {
  background: var(--accent-weak);
  color: var(--accent-text);
}

.notice-topic-icon.topic-follow {
  background: var(--success-weak);
  color: var(--success);
}

.notice-topic-icon.topic-moderation {
  background: var(--warning-weak);
  color: var(--warning);
}

.notice-topic-icon.topic-fallback {
  background: var(--surface-2);
  color: var(--text-2);
}

.notice-topic-copy {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: var(--space-2);
}

.notice-topic-title-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.notice-topic-title {
  font-size: var(--text-md);
  font-weight: 700;
  color: var(--text-1);
}

.notice-topic-sub {
  font-size: var(--text-sm);
  color: var(--text-2);
  line-height: 1.6;
}

.notice-topic-meta {
  font-size: 13px;
  color: var(--text-3);
}

.notice-topic-tail {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex: none;
  color: var(--accent-text);
}

.notice-topic-open {
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .notice-topic-card {
    padding: var(--space-4);
  }
}
</style>
