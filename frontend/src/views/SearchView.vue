<template>
  <div class="page search-page">
    <UiPageHeader>
      <template #title>搜索</template>
      <template #subtitle>输入关键词、分类和标签后查看结果。索引为最终一致，发帖或编辑后结果可能延迟数秒到数十秒；顶栏快捷键 {{ isMac ? '⌘' : 'Ctrl' }} K 可直接进入搜索。</template>
    </UiPageHeader>

    <section class="search-controls" aria-label="搜索条件">
      <div class="search-keyword-row">
        <div class="search-keyword-field">
          <UiInput
            v-model.trim="keyword"
            name="search-keyword"
            placeholder="输入关键词…"
            autocomplete="off"
            aria-label="搜索关键词"
            @keydown.enter="submitSearch"
          />
        </div>
        <UiButton class="search-submit" :disabled="loading" @click="submitSearch">
          {{ loading ? '搜索中…' : '搜索' }}
        </UiButton>
      </div>

      <div class="search-filter-row">
        <div class="search-category">
          <UiSelect
            :model-value="categoryId"
            :options="categoryOptions"
            label="分类筛选"
            placeholder="全部分类"
            :disabled="loading"
            clearable
            clear-label="清除分类筛选"
            @update:model-value="changeCategory"
          />
        </div>

        <div class="search-tag">
          <UiAutosuggestInput
            v-model.trim="tagDraft"
            name="search-tag-filter"
            placeholder="标签（可选）"
            autocomplete="off"
            :disabled="loading"
            :suggestions="tagSuggestNames"
            :commit-on-enter="false"
            :commit-on-blur="true"
            @keydown.enter="submitSearch"
            @commit="commitTag"
          />
        </div>

        <UiButton variant="ghost" class="search-clear" :disabled="loading" @click="clearFilters">清空筛选</UiButton>
      </div>

      <div class="search-note">
        <p class="search-active-summary">
          <template v-if="keyword || tagDraft || categoryId">
            当前聚焦
            {{ keyword ? `“${keyword}”` : '全部关键词' }}
            <template v-if="categoryId"> · {{ categoryLabel(categoryId) }}</template>
            <template v-if="tagDraft"> · #{{ normalizeTag(tagDraft) }}</template>
          </template>
          <template v-else>尚未添加限定词，正在浏览全部讨论范围。</template>
        </p>
        <p class="search-help">索引为最终一致，发帖或编辑后结果可能延迟数秒到数十秒。</p>
      </div>
    </section>

    <div class="search-feed">
      <div v-if="loading && items.length === 0" class="search-skeletons">
        <UiSkeleton v-for="i in 3" :key="i" variant="card" />
      </div>

      <UiState v-if="error && items.length === 0" variant="error" :title="error">
        <template #description>搜索失败，可以重试或调整关键词后再试。</template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" @click="reload">重试</UiButton>
        </template>
      </UiState>
      <div v-else-if="error" class="error search-inline-error">{{ error }}</div>

      <UiState v-if="!loading && items.length === 0 && !error">
        暂无结果
        <template #description>换个关键词试试，或回到帖子列表浏览。</template>
        <template #actions>
          <UiButton variant="secondary" @click="router.push({ name: 'posts' })">回到帖子</UiButton>
          <UiButton variant="ghost" :disabled="loading" @click="clearSearch">清空</UiButton>
        </template>
      </UiState>

      <template v-if="items.length > 0">
        <div class="search-results-meta">
          <span><strong>{{ items.length }}</strong> 条结果已加载</span>
          <span v-if="keyword">关键词 · “{{ keyword }}”</span>
        </div>

        <div class="search-result-list">
          <article
            v-for="it in items"
            :key="it.postId"
            class="search-card"
            role="link"
            tabindex="0"
            @keydown.enter="onCardEnter($event, it)"
            @click="openResult(it)"
          >
            <div class="search-card-head">
              <div class="search-card-taxonomy">
                <button
                  v-if="it.categoryId"
                  type="button"
                  class="search-card-category"
                  :title="`筛选分类 ${categoryLabel(it.categoryId)}`"
                  @click.stop="changeCategory(it.categoryId)"
                >
                  {{ categoryLabel(it.categoryId) }}
                </button>
                <button
                  v-for="t in (Array.isArray(it.tags) ? it.tags : [])"
                  :key="t"
                  type="button"
                  class="search-card-tag"
                  :title="`筛选标签 ${t}`"
                  @click.stop="commitTag(t)"
                >
                  #{{ t }}
                </button>
              </div>
              <span class="search-card-score">匹配度 {{ Number(it.score || 0).toFixed(2) }}</span>
            </div>

            <h2 class="search-card-title" v-html="titleHtml(it)"></h2>
            <div v-if="contentHtml(it)" class="search-card-snippet" v-html="contentHtml(it)"></div>

            <div
              v-if="describeSearchActivity(it)"
              class="search-card-activity"
              :title="describeSearchActivity(it)?.copy || ''"
            >
              <div class="search-card-activity-head">
                <UiAvatar
                  :src="it.lastReplyUser?.headerUrl || ''"
                  :name="it.lastReplyUser?.username || ''"
                  :size="18"
                />
                <span class="search-card-activity-label">
                  {{ describeSearchActivity(it)?.label }}
                </span>
              </div>
              <div class="search-card-activity-copy">{{ describeSearchActivity(it)?.copy }}</div>
            </div>

            <div class="search-card-meta">
              <span class="search-card-author">
                <UiAvatar :src="it.author?.headerUrl || ''" :name="it.author?.username || ''" :size="18" />
                <span class="search-card-author-name">{{ it.author?.username || `成员 ${it.userId || '—'}` }}</span>
              </span>
              <span aria-hidden="true">·</span>
              <span>{{ formatTimeAgo(it.lastActivityTime || it.createTime) }}</span>
              <span>{{ Number(it.commentCount || 0) }} 回复</span>
              <span>{{ Number(it.likeCount || 0) }} 赞</span>
            </div>
          </article>
        </div>

        <div v-if="pageError" class="error search-inline-error">{{ pageError }}</div>

        <div v-if="loadingMore || hasNext" class="search-load-more">
          <UiButton v-if="loadingMore" variant="ghost" disabled>
            <LoaderCircle :size="14" aria-hidden="true" class="search-load-more-spinner" />
            正在加载…
          </UiButton>
          <UiButton v-else variant="secondary" class="search-load-more-btn" @click="loadMore">加载更多</UiButton>
        </div>
        <div v-if="!hasNext" class="search-end-note">没有更多结果了</div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { LoaderCircle } from 'lucide-vue-next'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiAutosuggestInput from '../components/ui/UiAutosuggestInput.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiSelect from '../components/ui/UiSelect.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiState from '../components/ui/UiState.vue'
import { formatTimeAgo } from '../utils/time'
import { describeSearchActivity } from './searchResultSurface'
import { normalizeSearchTag, useSearchPageState } from './search/useSearchPageState'

const router = useRouter()

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad|iPod/i.test(navigator.platform || '')
const {
  keyword,
  categoryId,
  tagDraft,
  tagSuggestNames,
  categoryOptions,
  categoryLabel,
  loading,
  loadingMore,
  error,
  pageError,
  items,
  hasNext,
  submitSearch,
  changeCategory,
  commitTag,
  clearFilters,
  clearSearch,
  reload,
  loadMore
} = useSearchPageState()
const normalizeTag = normalizeSearchTag

// 高亮内容安全渲染：默认转义所有标签，仅放行 <em> 与 </em>。
function escapeHtml(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function emOnlyHtml(text) {
  const escaped = escapeHtml(text)
  return escaped.replace(/&lt;\/?em&gt;/g, (m) => (m === '&lt;em&gt;' ? '<em>' : '</em>'))
}

function titleHtml(item) {
  return emOnlyHtml(item?.highlightedTitle || item?.title || '')
}

function contentHtml(item) {
  const content = item?.highlightedContent || ''
  return content ? emOnlyHtml(content) : ''
}

function openResult(item) {
  router.push(`/posts/${item.postId}`)
}

// 键盘打开只响应卡片自身获得焦点时的 Enter；嵌套按钮（分类/标签）的 Enter
// 走原生 click，不重复触发打开。
function onCardEnter(event, item) {
  if (event?.target !== event?.currentTarget) return
  openResult(item)
}
</script>

<style scoped>
.search-page {
  max-width: 1000px;
}

.search-controls {
  display: grid;
  gap: var(--space-3);
}

.search-keyword-row {
  display: flex;
  gap: var(--space-3);
}

.search-keyword-field {
  flex: 1;
  min-width: 0;
}

.search-submit {
  flex: none;
  min-width: 88px;
}

.search-filter-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.search-category {
  flex: 0 0 220px;
}

.search-tag {
  flex: 1;
  min-width: 240px;
}

.search-clear {
  flex: none;
}

.search-note {
  display: grid;
  gap: var(--space-1);
}

.search-active-summary {
  margin: 0;
  color: var(--text-2);
  font-size: 13px;
  line-height: 1.6;
}

.search-help {
  margin: 0;
  color: var(--text-3);
  font-size: var(--text-xs);
  line-height: 1.6;
}

.search-feed {
  display: grid;
  gap: var(--space-3);
}

.search-skeletons {
  display: grid;
  gap: var(--space-3);
}

.search-inline-error {
  font-size: var(--text-sm);
}

.search-results-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  padding: 0 2px;
  font-size: var(--text-xs);
  color: var(--text-3);
}

.search-results-meta strong {
  color: var(--text-1);
}

.search-result-list {
  display: grid;
  gap: var(--space-3);
}

.search-card {
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

.search-card:hover {
  border-color: var(--border-strong);
  background: color-mix(in srgb, var(--surface) 55%, var(--surface-2));
}

.search-card:focus-visible {
  box-shadow: var(--focus-ring);
}

.search-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.search-card-taxonomy {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  min-width: 0;
}

.search-card-category {
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
  cursor: pointer;
  transition:
    color var(--duration-fast) var(--ease-standard),
    border-color var(--duration-fast) var(--ease-standard);
}

.search-card-category:hover {
  color: var(--text-1);
  border-color: var(--border-strong);
}

.search-card-category:focus-visible {
  box-shadow: var(--focus-ring);
}

.search-card-tag {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 var(--space-1);
  border: 0;
  background: transparent;
  color: var(--text-3);
  font-size: var(--text-xs);
  font-weight: 500;
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-standard);
}

.search-card-tag:hover {
  color: var(--text-1);
}

.search-card-tag:focus-visible {
  box-shadow: var(--focus-ring);
  border-radius: var(--radius-sm);
}

.search-card-score {
  flex: none;
  color: var(--text-3);
  font-size: var(--text-xs);
}

.search-card-title {
  margin: 0;
  max-width: 34ch;
  font-size: 19px;
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: 0;
  color: var(--text-1);
  word-break: break-word;
}

.search-card-snippet {
  max-width: 70ch;
  color: var(--text-2);
  font-size: var(--text-sm);
  line-height: 1.7;
  word-break: break-word;
}

.search-card-title :deep(em),
.search-card-snippet :deep(em) {
  font-style: normal;
  background: var(--accent-weak);
  color: var(--accent-text);
}

.search-card-activity {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface-2);
}

.search-card-activity-head {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-2);
  font-size: var(--text-xs);
}

.search-card-activity-label {
  font-weight: 600;
}

.search-card-activity-copy {
  color: var(--text-2);
  font-size: var(--text-sm);
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.search-card-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  padding-top: var(--space-3);
  border-top: 1px solid var(--border);
  font-size: 13px;
  color: var(--text-3);
}

.search-card-author {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-2);
}

.search-card-author-name {
  font-weight: 600;
}

.search-load-more {
  display: flex;
  justify-content: center;
  padding-top: var(--space-2);
}

.search-load-more-btn {
  min-width: 260px;
}

.search-load-more-spinner {
  animation: search-load-more-spin 0.8s linear infinite;
}

@keyframes search-load-more-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .search-load-more-spinner {
    animation: none;
  }
}

.search-end-note {
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
}

@media (max-width: 768px) {
  .search-keyword-row {
    flex-direction: column;
  }

  .search-submit {
    width: 100%;
  }

  .search-filter-row {
    align-items: stretch;
    flex-direction: column;
    flex-wrap: nowrap;
  }

  .search-category,
  .search-tag {
    flex: none;
    min-width: 0;
    width: 100%;
  }

  .search-clear {
    align-self: flex-start;
  }

  .search-card {
    padding: var(--space-4);
  }

  .search-card-title {
    max-width: none;
  }

  .search-load-more-btn {
    min-width: 0;
    width: 100%;
  }
}
</style>
