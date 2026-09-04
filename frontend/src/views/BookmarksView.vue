<template>
  <div class="page bookmarks-page">
    <UiPageHeader>
      <template #title>我的收藏</template>
      <template #subtitle>把值得回来的帖子整理成一份更像阅读清单的个人列表。</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="loading" @click="reload">刷新</UiButton>
      </template>
    </UiPageHeader>

    <div class="bookmarks-feed">
      <div v-if="loading && items.length === 0" class="bookmarks-skeletons">
        <UiSkeleton v-for="i in 3" :key="i" variant="card" />
      </div>

      <UiState v-if="error && items.length === 0" variant="error" :title="error">
        <template #description>收藏列表加载失败，可以重试或稍后再来。</template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" @click="reload">重试</UiButton>
        </template>
      </UiState>
      <div v-else-if="error" class="error bookmarks-inline-error">{{ error }}</div>

      <UiState v-if="!loading && !error && items.length === 0">
        暂无收藏
        <template #description>你收藏过的帖子会出现在这里，适合作为稍后继续阅读的个人清单。</template>
        <template #actions>
          <UiButton :to="{ name: 'posts' }">浏览帖子</UiButton>
        </template>
      </UiState>

      <div v-if="items.length > 0" class="bookmarks-list">
        <article
          v-for="p in items"
          :key="p.id"
          class="bookmark-card"
          role="link"
          tabindex="0"
          @keydown.enter="onCardEnter($event, p)"
          @click="openPost(p)"
        >
          <div class="bookmark-card-head">
            <div class="bookmark-card-taxonomy">
              <UiBadge v-if="p.type === 1" variant="accent">置顶</UiBadge>
              <UiBadge v-if="p.status === 1" variant="success">精华</UiBadge>
              <RouterLink
                v-if="p.categoryId"
                class="bookmark-card-category"
                :title="`查看分类 ${categoryLabel(p.categoryId)}`"
                :to="{ name: 'posts', query: { categoryId: String(p.categoryId) } }"
                @click.stop
              >
                {{ categoryLabel(p.categoryId) }}
              </RouterLink>
              <RouterLink
                v-for="t in (Array.isArray(p.tags) ? p.tags : [])"
                :key="t"
                class="bookmark-card-tag"
                :title="`查看标签 ${t}`"
                :to="{ name: 'posts', query: { tag: t } }"
                @click.stop
              >
                #{{ t }}
              </RouterLink>
            </div>
            <div class="bookmark-activity" :title="formatTime(activityTime(p))">
              最近活跃 {{ formatTimeAgo(activityTime(p)) }}
            </div>
          </div>

          <h2 class="bookmark-card-title">{{ p.title }}</h2>

          <p v-if="p.preview" class="bookmark-card-snippet">
            {{ p.preview.slice(0, 140) }}{{ (p.preview?.length || 0) > 140 ? '…' : '' }}
          </p>

          <div class="bookmark-card-foot">
            <span class="bookmark-card-stat">{{ Number(p.commentCount || 0) }} 回复</span>
            <span class="bookmark-card-open">打开帖子</span>
          </div>
        </article>
      </div>

      <div v-if="pageError" class="error bookmarks-inline-error">{{ pageError }}</div>

      <div class="bookmarks-load-more" v-if="loadingMore || (hasNext && items.length > 0)">
        <UiButton v-if="loadingMore" variant="ghost" disabled>
          <LoaderCircle :size="14" aria-hidden="true" class="bookmarks-load-more-spinner" />
          正在加载…
        </UiButton>
        <UiButton v-else variant="secondary" class="bookmarks-load-more-btn" @click="loadMore">加载更多</UiButton>
      </div>
      <div v-if="!hasNext && items.length > 0" class="bookmarks-end-note">已经到底了</div>
    </div>
  </div>
</template>

<script setup>
import { LoaderCircle } from 'lucide-vue-next'
import UiBadge from '../components/ui/UiBadge.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiState from '../components/ui/UiState.vue'
import { formatTime, formatTimeAgo } from '../utils/time'
import { useBookmarksFeed } from './useBookmarksFeed'

const {
  items,
  hasNext,
  loading,
  loadingMore,
  error,
  pageError,
  categoryLabel,
  openPost,
  reload,
  loadMore
} = useBookmarksFeed()

function activityTime(p) {
  return p?.lastActivityTime || p?.createTime || null
}

// 键盘打开只响应卡片自身获得焦点时的 Enter；嵌套链接（分类/标签）的 Enter
// 走原生导航，不重复触发打开。
function onCardEnter(event, post) {
  if (event?.target !== event?.currentTarget) return
  openPost(post)
}
</script>

<style scoped>
.bookmarks-page {
  max-width: 1000px;
  margin: 0 auto;
  gap: var(--space-5);
}

.bookmarks-feed {
  display: grid;
  gap: var(--space-3);
}

.bookmarks-skeletons {
  display: grid;
  gap: var(--space-3);
}

.bookmarks-inline-error {
  font-size: var(--text-sm);
}

.bookmarks-list {
  display: grid;
  gap: var(--space-3);
}

.bookmark-card {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-6);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.bookmark-card:hover {
  border-color: var(--border-strong);
  background: color-mix(in srgb, var(--surface) 55%, var(--surface-2));
}

.bookmark-card:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.bookmark-card-head,
.bookmark-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.bookmark-card-taxonomy {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  min-width: 0;
}

.bookmark-card-category {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 var(--space-2);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  color: var(--text-2);
  font-size: var(--text-xs);
  font-weight: 500;
  text-decoration: none;
  transition:
    color var(--duration-fast) var(--ease-standard),
    border-color var(--duration-fast) var(--ease-standard);
}

.bookmark-card-category:hover {
  color: var(--text-1);
  border-color: var(--border-strong);
}

.bookmark-card-category:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.bookmark-card-tag {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 var(--space-1);
  color: var(--text-3);
  font-size: var(--text-xs);
  font-weight: 500;
  text-decoration: none;
  transition: color var(--duration-fast) var(--ease-standard);
}

.bookmark-card-tag:hover {
  color: var(--text-1);
}

.bookmark-card-tag:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
  border-radius: var(--radius-sm);
}

.bookmark-card-title {
  margin: 0;
  max-width: 34ch;
  font-size: 19px;
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: 0;
  color: var(--text-1);
  word-break: break-word;
}

.bookmark-card-snippet {
  margin: 0;
  max-width: 70ch;
  color: var(--text-2);
  font-size: var(--text-sm);
  line-height: 1.7;
  word-break: break-word;
}

.bookmark-activity,
.bookmark-card-stat {
  font-size: 13px;
  color: var(--text-3);
}

.bookmark-card-foot {
  padding-top: var(--space-3);
  border-top: 1px solid var(--border);
}

.bookmark-card-open {
  font-size: 13px;
  font-weight: 700;
  color: var(--accent-text);
  white-space: nowrap;
}

.bookmarks-load-more {
  display: flex;
  justify-content: center;
  padding-top: var(--space-2);
}

.bookmarks-load-more-btn {
  min-width: 260px;
}

.bookmarks-load-more-spinner {
  animation: bookmarks-load-more-spin 0.8s linear infinite;
}

@keyframes bookmarks-load-more-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .bookmarks-load-more-spinner {
    animation: none;
  }
}

.bookmarks-end-note {
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
}

@media (max-width: 768px) {
  .bookmark-card {
    padding: var(--space-4);
  }

  .bookmark-card-head {
    align-items: flex-start;
  }

  .bookmarks-load-more-btn {
    min-width: 0;
    width: 100%;
  }
}
</style>
