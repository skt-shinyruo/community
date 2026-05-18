<template>
  <div class="page search-page">
    <UiPageHeader>
      <template #title>搜索</template>
      <template #subtitle>输入关键词、分类和标签后查看结果。索引为最终一致，发帖或编辑后结果可能延迟数秒到数十秒；顶栏快捷键 {{ isMac ? '⌘' : 'Ctrl' }} K 可直接进入搜索。</template>
    </UiPageHeader>

    <section class="search-workbench">
      <UiToolbar>
        <template #leading>
          <div class="search-searchbar">
            <UiInput
              v-model.trim="keyword"
              name="search-keyword"
              placeholder="输入关键词…"
              autocomplete="off"
              @keydown.enter="onSearch"
            />
            <UiButton @click="onSearch" :disabled="loading" class="search-submit-btn">
              {{ loading ? '搜索中…' : '搜索' }}
            </UiButton>
          </div>
        </template>

        <template #filters>
          <UiSelect
            name="search-category-filter"
            class="search-select"
            :disabled="loading"
            :model-value="String(categoryId || '')"
            aria-label="分类筛选"
            :options="categoryOptions"
            placeholder="全部分类"
            @update:modelValue="replaceQuery({ categoryId: $event || '' })"
          />

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
              @keydown.enter="onSearch"
              @commit="commitTag"
            />
          </div>
        </template>

        <template #actions>
          <UiButton variant="ghost" @click="clearFilters" :disabled="loading">清空筛选</UiButton>
        </template>
      </UiToolbar>

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
                @click.stop="replaceQuery({ categoryId: it.categoryId })"
              >
                <span class="tag topic-category">{{ categoryLabel(it.categoryId) }}</span>
              </UiButton>

              <UiButton
                v-for="t in (Array.isArray(it.tags) ? it.tags : [])"
                :key="t"
                class="search-taxonomy-btn"
                variant="ghost"
                :aria-label="`筛选标签 ${t}`"
                @click.stop="replaceQuery({ tag: t })"
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
          <div v-if="searchActivity(it)" class="search-result-activity" :title="searchActivity(it)?.copy || ''">
            <div class="search-result-activity-head">
              <UiAvatar
                :src="it.lastReplyUser?.headerUrl || ''"
                :name="it.lastReplyUser?.username || ''"
                :size="18"
              />
              <span class="search-result-activity-label">
                {{ searchActivity(it)?.label }}
              </span>
            </div>
            <div class="search-result-activity-copy">{{ searchActivity(it)?.copy }}</div>
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
       <UiButton variant="secondary" @click="prevPage" :disabled="page <= 0 || loading">上一页</UiButton>
       <UiButton variant="secondary" @click="nextPage" :disabled="!hasNext || loading">下一页</UiButton>
    </div>

  </div>
</template>

	<script setup>
	import { computed, onMounted, ref, watch } from 'vue'
	import { useRoute, useRouter } from 'vue-router'
	import { searchPosts } from '../api/services/searchService'
	import { batchPostSummaries } from '../api/services/postService'
	import { suggestTags as apiSuggestTags } from '../api/services/taxonomyService'
	import { usePostMetaCacheStore } from '../stores/postMetaCache'
	import { formatTimeAgo } from '../utils/time'
	import { normalizeOpaqueId } from '../utils/opaqueId'
	import UiAvatar from '../components/ui/UiAvatar.vue'
import { emOnlyHtml } from '../utils/highlight'
import { useTaxonomyStore } from '../stores/taxonomy'
import { applySearchHydration, applySearchSummaries, collectSearchHydrationIds, describeSearchActivity } from './searchResultSurface'
import { createLatestRequestTracker } from '../utils/latestRequest'
import UiAutosuggestInput from '../components/ui/UiAutosuggestInput.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiState from '../components/ui/UiState.vue'
import UiSelect from '../components/ui/UiSelect.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'

const emit = defineEmits(['trace'])
const route = useRoute()
const router = useRouter()
const postMetaCache = usePostMetaCacheStore()

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad|iPod/i.test(navigator.platform || '')

	const keyword = ref('')
	const categoryId = ref('')
	const tagDraft = ref('')
		const tagSuggestNames = ref([])
	const page = ref(0)
	const size = ref(10)
	const loading = ref(false)
	const error = ref('')
	const items = ref([])
	const hasNext = computed(() => items.value.length === Number(size.value))
	const searchRequestTracker = createLatestRequestTracker()

	const taxonomy = useTaxonomyStore()
	const categories = computed(() => (Array.isArray(taxonomy.categories) ? taxonomy.categories : []))
	const categoryOptions = computed(() => [
	  { label: '全部分类', value: '' },
	  ...categories.value.map((category) => ({
	    label: category.name,
	    value: String(category.id)
	  }))
	])

		function categoryLabel(id) {
		  const cid = normalizeOpaqueId(id)
		  if (!cid) return ''
		  const c = taxonomy.categoriesById.get(cid)
		  return c?.name || `分类#${cid}`
		}

	function titleHtml(it) {
	  return emOnlyHtml(it?.highlightedTitle || it?.title || '')
	}
	function contentHtml(it) {
	  const c = it?.highlightedContent || ''
	  return c ? emOnlyHtml(c) : ''
	}

		function normalizeCategoryId(value) {
		  return normalizeOpaqueId(value)
		}

	function normalizeTag(value) {
	  let s = String(value || '').trim()
	  if (s.startsWith('#')) s = s.slice(1).trim()
	  return s
	}

	function replaceQuery(partial) {
	  const next = { ...(route.query || {}) }
	
	  if (Object.prototype.hasOwnProperty.call(partial, 'q')) {
	    const q = String(partial.q || '').trim()
	    if (!q) delete next.q
	    else next.q = q
	  }
	
	  if (Object.prototype.hasOwnProperty.call(partial, 'categoryId')) {
	    const cid = normalizeCategoryId(partial.categoryId)
	    if (!cid) delete next.categoryId
	    else next.categoryId = String(cid)
	  }
	
	  if (Object.prototype.hasOwnProperty.call(partial, 'tag')) {
	    const t = normalizeTag(partial.tag)
	    if (!t) delete next.tag
	    else next.tag = t
	  }
	
	  router.replace({ name: 'search', query: next })
	}

	function commitTag() {
	  const next = normalizeTag(tagDraft.value)
	  tagDraft.value = next
	  page.value = 0
	  replaceQuery({ tag: next })
	  run()
	}

	async function resolveSearchItems(data) {
	  const baseItems = Array.isArray(data) ? data : []
	  let summaries = []
	  let users = {}
	  let likeCounts = {}

	  const [summaryResult] = await Promise.allSettled([
	    batchPostSummaries(baseItems.map((item) => item?.postId))
	  ])

	  if (summaryResult.status === 'fulfilled') {
	    summaries = Array.isArray(summaryResult.value?.data) ? summaryResult.value.data : []
	  }

	  const merged = applySearchSummaries(baseItems, summaries)
	  const { userIds, postIds } = collectSearchHydrationIds(merged)
	  const [userResult, likeCountResult] = await Promise.allSettled([
	    postMetaCache.ensureUserSummaries(userIds),
	    postMetaCache.ensureLikeCounts(1, postIds)
	  ])

	  if (userResult.status === 'fulfilled') {
	    users = userResult.value || {}
	  }
	  if (likeCountResult.status === 'fulfilled') {
	    likeCounts = likeCountResult.value || {}
	  }

	  return applySearchHydration(merged, { users, likeCounts })
	}

	async function run() {
	  const token = searchRequestTracker.begin()
	  error.value = ''
	  loading.value = true
	  try {
	    const { data, traceId } = await searchPosts({
	      keyword: keyword.value,
	      categoryId: normalizeCategoryId(categoryId.value),
	      tag: normalizeTag(tagDraft.value),
	      page: page.value,
	      size: size.value
	    })
	    if (!searchRequestTracker.isCurrent(token)) return
	    const nextItems = await resolveSearchItems(data)
	    if (!searchRequestTracker.isCurrent(token)) return
	    items.value = nextItems
	    emit('trace', traceId || '')
	  } catch (e) {
	    if (!searchRequestTracker.isCurrent(token)) return
	    error.value = e?.message || '搜索失败'
	  } finally {
	    if (searchRequestTracker.isCurrent(token)) {
	      loading.value = false
	    }
	  }
	}

	async function onSearch() {
	  page.value = 0
	  replaceQuery({ q: keyword.value, categoryId: categoryId.value, tag: tagDraft.value })
	  await run()
	}

	function clearFilters() {
	  categoryId.value = ''
	  tagDraft.value = ''
	  page.value = 0
	  replaceQuery({ categoryId: '', tag: '' })
	  run()
	}

	function clearSearch() {
	  searchRequestTracker.invalidate()
	  keyword.value = ''
	  page.value = 0
	  items.value = []
	  error.value = ''
	  loading.value = false
	  categoryId.value = ''
	  tagDraft.value = ''
	  tagSuggestNames.value = []
	  router.replace({ name: 'search', query: {} })
	}

async function nextPage() {
  if (!hasNext.value) return
  page.value += 1
  await run()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

	async function prevPage() {
	  page.value = Math.max(0, page.value - 1)
	  await run()
	  window.scrollTo({ top: 0, behavior: 'smooth' })
	}

function searchActivity(item) {
  return describeSearchActivity(item)
}

	function syncFromRoute() {
	  const q = typeof route.query?.q === 'string' ? route.query.q : ''
	  const cid = normalizeCategoryId(route.query?.categoryId)
	  const t = normalizeTag(route.query?.tag)
	
	  const hasAny = !!q || !!cid || !!t
	  if (!hasAny) {
	    searchRequestTracker.invalidate()
	    keyword.value = ''
	    categoryId.value = ''
	    tagDraft.value = ''
	    items.value = []
	    error.value = ''
	    loading.value = false
	    page.value = 0
	    return
	  }
	
	  let changed = false
	  if (q !== keyword.value) {
	    keyword.value = q
	    changed = true
	  }
		  const cidStr = cid ? String(cid) : ''
	  if (cidStr !== String(categoryId.value || '')) {
	    categoryId.value = cidStr
	    changed = true
	  }
	  if (t !== tagDraft.value) {
	    tagDraft.value = t
	    changed = true
	  }
	
	  if (changed) {
	    page.value = 0
	    run()
	  }
	}

	let suggestTimer = 0
	let suggestToken = 0

	watch(tagDraft, (v) => {
	  if (suggestTimer) window.clearTimeout(suggestTimer)
	  const q = String(v || '').trim()
	  if (!q) {
	    tagSuggestNames.value = (Array.isArray(taxonomy.hotTags) ? taxonomy.hotTags : [])
	      .map((x) => x?.name)
	      .filter(Boolean)
	      .slice(0, 10)
	    return
	  }
	  const token = ++suggestToken
	  suggestTimer = window.setTimeout(async () => {
	    try {
	      const resp = await apiSuggestTags({ q, limit: 10 })
	      if (token !== suggestToken) return
	      tagSuggestNames.value = (Array.isArray(resp?.data) ? resp.data : []).map((x) => x?.name).filter(Boolean)
	    } catch {
	      // ignore
	    }
	  }, 180)
	})

	onMounted(() => {
	  taxonomy.ensureCategories()
	  taxonomy.ensureHotTags(10)
	  syncFromRoute()
	})

	watch(
	  () => `${route.query?.q || ''}__${route.query?.categoryId || ''}__${route.query?.tag || ''}`,
	  syncFromRoute
	)
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
}

.search-select :deep(.ui-select-trigger) {
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
