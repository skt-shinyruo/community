<template>
  <div class="page search-page">
    <UiPageHeader>
      <template #title>搜索</template>
      <template #subtitle>输入关键词、分类和标签后查看结果。索引为最终一致，发帖或编辑后结果可能延迟数秒到数十秒；顶栏快捷键 {{ isMac ? '⌘' : 'Ctrl' }} K 可直接进入搜索。</template>
    </UiPageHeader>

    <section class="search-workbench">
      <section class="ui-toolbar" aria-label="页面工具栏">
        <div class="ui-toolbar-leading">
          <div class="search-searchbar">
            <input
              v-model.trim="keyword"
              name="search-keyword"
              placeholder="输入关键词…"
              autocomplete="off"
              class="input"
              @keydown.enter="submitSearch"
            />
            <UiButton @click="submitSearch" :disabled="loading" class="search-submit-btn">
              {{ loading ? '搜索中…' : '搜索' }}
            </UiButton>
          </div>
        </div>

        <div class="ui-toolbar-filters">
          <select
            name="search-category-filter"
            class="input search-select"
            :disabled="loading"
            :value="String(categoryId || '')"
            aria-label="分类筛选"
            @change="changeCategory($event.target.value || '')"
          >
            <option
              v-for="option in categoryOptions"
              :key="String(option.value)"
              :value="option.value"
              :disabled="option.disabled"
            >
              {{ option.label }}
            </option>
          </select>

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
        </div>

        <div class="ui-toolbar-actions">
          <UiButton variant="ghost" @click="clearFilters" :disabled="loading">清空筛选</UiButton>
        </div>
      </section>

      <div class="search-toolbar-note">
        <div class="search-active-summary">
          <template v-if="keyword || tagDraft || categoryId">
            当前聚焦
            {{ keyword ? `“${keyword}”` : '全部关键词' }}
            <template v-if="categoryId"> · {{ categoryLabel(categoryId) }}</template>
            <template v-if="tagDraft"> · #{{ normalizeTag(tagDraft) }}</template>
          </template>
          <template v-else>尚未添加限定词，正在浏览全部讨论范围。</template>
        </div>

        <div class="muted search-help">索引为最终一致，发帖或编辑后结果可能延迟数秒到数十秒。</div>
      </div>
    </section>

    <UiState v-if="error && items.length === 0" variant="error" class="search-state">{{ error }}</UiState>
    <div v-else-if="error" class="error search-state">{{ error }}</div>

    <!-- Results Feed -->
    <div class="search-results">
      <div v-if="items.length > 0" class="search-results-head">
        <div class="search-results-title">搜索结果</div>
        <div class="search-results-meta">
          <span>{{ items.length }} 条</span>
          <span>第 {{ page + 1 }} 页</span>
          <span v-if="keyword">关键词 · “{{ keyword }}”</span>
        </div>
      </div>

      <UiState v-if="!loading && items.length === 0 && !error">
        暂无结果
        <template #description>换个关键词试试，或回到帖子列表浏览。</template>
        <template #actions>
          <UiButton variant="secondary" @click="router.push({ name: 'posts' })">回到帖子</UiButton>
          <UiButton variant="ghost" @click="clearSearch" :disabled="loading">清空</UiButton>
        </template>
      </UiState>
      <div v-else-if="loading && items.length === 0" class="muted">加载中…</div>

      <div v-else class="search-result-list">
        <article
          v-for="it in items"
          :key="it.postId"
          class="search-result-card"
          role="link"
          tabindex="0"
          @keydown.enter="router.push(`/posts/${it.postId}`)"
          @click="router.push(`/posts/${it.postId}`)"
        >
          <div class="search-result-head">
            <div class="search-result-taxonomy">
              <UiButton
                v-if="it.categoryId"
                class="search-taxonomy-btn"
                variant="ghost"
                :aria-label="`筛选分类 ${categoryLabel(it.categoryId)}`"
                @click.stop="changeCategory(it.categoryId)"
              >
                <span class="tag topic-category">{{ categoryLabel(it.categoryId) }}</span>
              </UiButton>

              <UiButton
                v-for="t in (Array.isArray(it.tags) ? it.tags : [])"
                :key="t"
                class="search-taxonomy-btn"
                variant="ghost"
                :aria-label="`筛选标签 ${t}`"
                @click.stop="commitTag(t)"
              >
                <span class="tag">#{{ t }}</span>
              </UiButton>
            </div>
            <div class="search-result-score">
              <span class="search-result-score-label">匹配度</span>
              <strong>S {{ Number(it.score || 0).toFixed(2) }}</strong>
            </div>
          </div>

          <div class="search-result-kicker">讨论线程</div>
          <div class="search-result-title" v-html="titleHtml(it)"></div>
          <div class="search-result-snippet" v-if="contentHtml(it)" v-html="contentHtml(it)"></div>
          <div v-if="describeSearchActivity(it)" class="search-result-activity" :title="describeSearchActivity(it)?.copy || ''">
            <div class="search-result-activity-head">
              <UiAvatar
                :src="it.lastReplyUser?.headerUrl || ''"
                :name="it.lastReplyUser?.username || ''"
                :size="18"
              />
              <span class="search-result-activity-label">
                {{ describeSearchActivity(it)?.label }}
              </span>
            </div>
            <div class="search-result-activity-copy">{{ describeSearchActivity(it)?.copy }}</div>
          </div>
          <div class="search-result-context">
            <div class="search-result-author">
              <UiAvatar :src="it.author?.headerUrl || ''" :name="it.author?.username || ''" :size="18" />
              <span>{{ it.author?.username || `成员 ${it.userId || '—'}` }}</span>
            </div>
            <span>{{ formatTimeAgo(it.lastActivityTime || it.createTime) }}</span>
            <span>{{ Number(it.commentCount || 0) }} 回复</span>
            <span>{{ Number(it.likeCount || 0) }} 赞</span>
          </div>

          <div class="search-result-foot">
            <span>帖子 #{{ it.postId }}</span>
            <span>{{ categoryLabel(it.categoryId) || '未分类讨论' }}</span>
            <span>可继续阅读</span>
          </div>
        </article>
      </div>
    </div>
    
    <!-- Pagination (Simple) -->
    <div class="search-pagination" v-if="items.length > 0 || page > 0">
       <UiButton variant="secondary" @click="loadPreviousPage" :disabled="page <= 0 || loading">上一页</UiButton>
       <UiButton variant="secondary" @click="loadNextPage" :disabled="!hasNext || loading">下一页</UiButton>
    </div>

  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { formatTimeAgo } from '../utils/time'
import UiAvatar from '../components/ui/UiAvatar.vue'
import { emOnlyHtml } from '../utils/highlight'
import { describeSearchActivity } from './searchResultSurface'
import UiAutosuggestInput from '../components/ui/UiAutosuggestInput.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiState from '../components/ui/UiState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
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
  page,
  loading,
  error,
  items,
  hasNext,
  submitSearch,
  changeCategory,
  commitTag,
  clearFilters,
  clearSearch,
  loadNextPage,
  loadPreviousPage
} = useSearchPageState()
const normalizeTag = normalizeSearchTag

function titleHtml(item) {
  return emOnlyHtml(item?.highlightedTitle || item?.title || '')
}

function contentHtml(item) {
  const content = item?.highlightedContent || ''
  return content ? emOnlyHtml(content) : ''
}

</script>

<style scoped>
.search-page {
  max-width: 980px;
}

.search-workbench {
  display: grid;
  gap: 12px;
  padding: 18px 20px;
  border-radius: 22px;
  border: 1px solid color-mix(in srgb, var(--border) 74%, transparent 26%);
  background: linear-gradient(180deg, color-mix(in srgb, var(--surface) 95%, #fff 5%), var(--surface));
  box-shadow: none;
}

.search-result-kicker {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-3);
}

.search-active-summary {
  color: var(--text-2);
  font-size: 13px;
  line-height: 1.6;
}

.search-toolbar-note {
  display: grid;
  gap: 4px;
}

.search-searchbar {
  display: flex;
  gap: 12px;
}

.search-submit-btn {
  min-width: 104px;
}

.search-filters {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.search-select {
  width: auto;
  min-width: 160px;
  height: 38px;
  font-size: 13px;
}

.search-tag {
  min-width: 220px;
  flex: 1;
}

.search-help {
  font-size: 12px;
  line-height: 1.6;
}

.search-state {
  margin-top: 12px;
}

.search-results {
  margin-top: 18px;
  display: grid;
  gap: 14px;
}

.search-results-head {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-bottom: 8px;
  border-bottom: 1px solid color-mix(in srgb, var(--border) 70%, transparent 30%);
}

.search-results-title {
  font-family: "Iowan Old Style", "Palatino Linotype", "Book Antiqua", Georgia, serif;
  font-size: clamp(24px, 3vw, 34px);
  line-height: 1.1;
  color: var(--text-1);
}

.search-results-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  color: var(--text-3);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.search-result-list {
  display: grid;
  gap: 14px;
}

.search-result-card {
  display: grid;
  gap: 12px;
  padding: 18px 20px;
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent 24%);
  border-radius: 22px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--surface) 96%, white 4%), var(--surface));
  box-shadow: none;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.search-result-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
  border-color: var(--border-strong);
}

.search-result-head,
.search-result-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.search-result-taxonomy {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.search-taxonomy-btn {
  min-height: 0;
  height: auto;
  border: none;
  background: transparent;
  padding: 0;
  box-shadow: none;
}

.search-result-score {
  display: grid;
  justify-items: end;
  color: var(--text-3);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.search-result-score strong {
  font-size: 15px;
  letter-spacing: normal;
  color: var(--text-1);
}

.search-result-score-label {
  color: var(--text-3);
}

.search-result-title {
  font-family: "Iowan Old Style", "Palatino Linotype", "Book Antiqua", Georgia, serif;
  font-size: clamp(21px, 2.2vw, 27px);
  line-height: 1.18;
  color: var(--text-1);
  font-weight: 800;
  max-width: 28ch;
}

.search-result-snippet {
  color: var(--text-2);
  font-size: 14px;
  line-height: 1.7;
  max-width: 64ch;
}

.search-result-context {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--text-3);
}

.search-result-activity {
  display: grid;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 18px;
  border: 1px solid color-mix(in srgb, var(--border) 72%, transparent 28%);
  background: color-mix(in srgb, var(--surface) 88%, var(--bg) 12%);
}

.search-result-activity-head {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-2);
  font-size: 12px;
}

.search-result-activity-label {
  font-weight: 600;
}

.search-result-activity-copy {
  color: var(--text-2);
  font-size: 14px;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.search-result-author {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-2);
}

.search-result-snippet :deep(em),
.search-result-title :deep(em) {
  font-style: normal;
  background: color-mix(in srgb, var(--accent) 18%, transparent 82%);
  color: inherit;
  box-shadow: inset 0 -0.45em 0 color-mix(in srgb, var(--accent) 14%, transparent 86%);
}

.search-result-foot {
  color: var(--text-3);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.search-pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
  gap: 12px;
}

@media (max-width: 768px) {
  .search-searchbar {
    flex-direction: column;
  }

  .search-submit-btn {
    width: 100%;
  }

  .search-workbench {
    padding: 18px;
  }

  .search-result-card {
    padding: 18px;
  }

  .search-result-title {
    max-width: none;
  }
}

html[data-theme='dark'] .search-workbench,
html[data-theme='dark'] .search-result-card {
  border-color: #2f2f2f;
  background: linear-gradient(180deg, #151515, #0f0f0f);
  box-shadow: none;
}

html[data-theme='dark'] .search-result-card:hover {
  box-shadow: 0 12px 20px rgba(0, 0, 0, 0.2);
}

html[data-theme='dark'] .search-result-activity {
  border-color: #2d2d2d;
  background: color-mix(in srgb, var(--surface) 92%, black 8%);
}
</style>
