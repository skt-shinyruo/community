<template>
  <div class="page" style="max-width: 860px; margin: 0 auto">
    <UiPageHeader>
      <template #title>搜索</template>
      <template #subtitle>支持帖子关键词高亮；管理员可重建索引。</template>
      <template #actions>
        <UiButton variant="secondary" v-if="auth.isAdmin" @click="openReindexConfirm" :disabled="loading">
          {{ loading ? '处理中…' : '重建索引' }}
        </UiButton>
      </template>
    </UiPageHeader>

	    <!-- Search Box -->
	    <UiCard class="search-card">
	      <div class="row search-row">
	        <UiInput
	          v-model.trim="keyword"
	          placeholder="输入关键词…"
	          autocomplete="off"
	          @keydown.enter="onSearch"
	        />
	        <UiButton @click="onSearch" :disabled="loading" style="min-width: 96px">
	          {{ loading ? '搜索中…' : '搜索' }}
	        </UiButton>
	      </div>
	      <div class="row search-filters">
	        <select
	          class="input search-select"
	          :disabled="loading"
	          :value="String(categoryId || '')"
	          aria-label="分类筛选"
	          @change="replaceQuery({ categoryId: $event?.target?.value || '' })"
	        >
	          <option value="">全部分类</option>
	          <option v-for="c in categories" :key="c.id" :value="String(c.id)">{{ c.name }}</option>
	        </select>
	
	        <div class="search-tag">
	          <UiInput
	            v-model.trim="tagDraft"
	            placeholder="标签（可选）"
	            autocomplete="off"
	            :disabled="loading"
	            :list="tagSuggestNames.length > 0 ? tagDatalistId : null"
	            @keydown.enter="onSearch"
	            @blur="commitTag"
	          />
	          <datalist v-if="tagSuggestNames.length > 0" :id="tagDatalistId">
	            <option v-for="t in tagSuggestNames" :key="t" :value="t"></option>
	          </datalist>
	        </div>

	        <UiButton variant="ghost" @click="clearFilters" :disabled="loading">
	          清空筛选
	        </UiButton>
	      </div>
	      <div class="muted" style="font-size: 12px; margin-top: 10px">
	        提示：也可使用顶栏快捷键 {{ isMac ? '⌘' : 'Ctrl' }} K 进行全局搜索。
	      </div>
	      <div class="muted" style="font-size: 12px; margin-top: 6px">
	        说明：搜索索引为最终一致，发帖/编辑后结果可能延迟数秒到数十秒；如长时间未更新，可尝试刷新或联系管理员重建索引/排查消费滞后。
	      </div>
	    </UiCard>

    <UiEmpty v-if="error && items.length === 0" type="error" style="margin-top: 12px">{{ error }}</UiEmpty>
    <div v-else-if="error" class="error" style="margin-top: 12px">{{ error }}</div>

    <!-- Results Feed -->
    <div class="stack" style="margin-top: 24px; gap: 16px">
      <UiEmpty v-if="!loading && items.length === 0 && !error" type="search">
        暂无结果
        <template #description>换个关键词试试，或回到帖子列表浏览。</template>
        <template #actions>
          <UiButton variant="secondary" @click="router.push({ name: 'posts' })">回到帖子</UiButton>
          <UiButton variant="ghost" @click="clearSearch" :disabled="loading">清空</UiButton>
        </template>
      </UiEmpty>
      <div v-else-if="loading && items.length === 0" class="muted">加载中…</div>

	      <div v-else class="topic-list">
	        <div
	          v-for="it in items"
	          :key="it.postId"
	          class="topic-row topic-row--search"
	          role="link"
	          tabindex="0"
	          @keydown.enter="router.push(`/posts/${it.postId}`)"
	          @click="router.push(`/posts/${it.postId}`)"
	        >
	          <div class="topic-main">
	            <div class="topic-title-row">
	              <div class="topic-title" v-html="titleHtml(it)"></div>
	            </div>
	            <div
	              v-if="(Number(it.categoryId || 0) > 0) || (Array.isArray(it.tags) && it.tags.length > 0)"
	              class="topic-taxonomy"
	              @click.stop
	            >
	              <button
	                v-if="Number(it.categoryId || 0) > 0"
	                class="topic-taxonomy-btn"
	                type="button"
	                :aria-label="`筛选分类 ${categoryLabel(it.categoryId)}`"
	                @click="replaceQuery({ categoryId: it.categoryId })"
	              >
	                <span class="tag topic-category">{{ categoryLabel(it.categoryId) }}</span>
	              </button>
	
	              <button
	                v-for="t in (Array.isArray(it.tags) ? it.tags : [])"
	                :key="t"
	                class="topic-taxonomy-btn"
	                type="button"
	                :aria-label="`筛选标签 ${t}`"
	                @click="replaceQuery({ tag: t })"
	              >
	                <span class="tag">#{{ t }}</span>
	              </button>
	            </div>
	            <div class="topic-snippet" v-if="contentHtml(it)" v-html="contentHtml(it)"></div>
	            <div class="topic-meta">
	              <span>搜索结果</span>
	            </div>
	          </div>

          <div class="topic-stats" @click.stop aria-label="搜索元信息">
            <div class="topic-stat" title="相关度">S {{ Number(it.score || 0).toFixed(2) }}</div>
            <div class="topic-stat" title="帖子 ID">#{{ it.postId }}</div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Pagination (Simple) -->
    <div style="margin-top: 24px; display: flex; justify-content: center; gap: 12px" v-if="items.length > 0 || page > 0">
       <UiButton variant="secondary" @click="prevPage" :disabled="page <= 0 || loading">上一页</UiButton>
       <UiButton variant="secondary" @click="nextPage" :disabled="!hasNext || loading">下一页</UiButton>
    </div>

    <div v-if="reindexConfirmOpen" class="modal-mask" @click.self="reindexConfirmOpen = false">
      <div class="modal-card card">
        <div class="stack">
          <div style="font-weight: 700">重建索引</div>
          <div class="muted">此操作可能耗时较长，会对搜索/下游产生负载，是否继续？</div>

          <div class="row" style="justify-content: flex-end">
            <UiButton variant="secondary" @click="reindexConfirmOpen = false">取消</UiButton>
            <UiButton @click="onConfirmReindex" :disabled="loading">继续</UiButton>
          </div>

          <div class="muted" style="font-size: 12px">
            提示：该操作仅管理员可执行；若失败可查看错误提示或进入 Ops Console 获取引导。
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

	<script setup>
	import { computed, inject, onMounted, ref, watch } from 'vue'
	import { useRoute, useRouter } from 'vue-router'
	import { useAuthStore } from '../stores/auth'
	import { searchPosts, reindex } from '../api/services/searchService'
	import { suggestTags as apiSuggestTags } from '../api/services/taxonomyService'
	import { emOnlyHtml } from '../utils/highlight'
	import { useTaxonomyStore } from '../stores/taxonomy'
	import UiCard from '../components/ui/UiCard.vue'
	import UiPageHeader from '../components/ui/UiPageHeader.vue'
	import UiInput from '../components/ui/UiInput.vue'
	import UiButton from '../components/ui/UiButton.vue'
	import UiEmpty from '../components/ui/UiEmpty.vue'

const emit = defineEmits(['trace'])
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const showToast = inject('showToast', () => {})

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad|iPod/i.test(navigator.platform || '')

	const keyword = ref('')
	const categoryId = ref('')
	const tagDraft = ref('')
	const tagDatalistId = 'search-tag-suggest'
	const tagSuggestNames = ref([])
	const page = ref(0)
	const size = ref(10)
	const loading = ref(false)
	const error = ref('')
	const items = ref([])
	const hasNext = computed(() => items.value.length === Number(size.value))
	const reindexConfirmOpen = ref(false)

	const taxonomy = useTaxonomyStore()
	const categories = computed(() => (Array.isArray(taxonomy.categories) ? taxonomy.categories : []))

	function categoryLabel(id) {
	  const cid = Number(id || 0)
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
	  const n = Number(value || 0)
	  return Number.isFinite(n) && n > 0 ? n : 0
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

	async function run() {
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
	    items.value = data
	    emit('trace', traceId || '')
	  } catch (e) {
	    error.value = e?.message || '搜索失败'
	  } finally {
	    loading.value = false
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
	  keyword.value = ''
	  page.value = 0
	  items.value = []
	  error.value = ''
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

async function onReindex() {
  try {
    const { data, traceId } = await reindex()
    emit('trace', traceId || '')
    const count = Number(data?.indexedCount || 0)
    const jobId = String(data?.jobId || '').trim()
    showToast({ type: 'success', title: '重建完成', text: `已处理 ${count} 条${jobId ? `（jobId=${jobId}）` : ''}` })
  } catch (e) {
    const code = e?.code
    if (code === 403) {
      showToast({ type: 'error', title: '重建失败', text: e?.message || '无权限' })
    } else {
      showToast({ type: 'error', title: '重建失败', text: e?.message || '请求失败' })
    }
  }
}

function openReindexConfirm() {
  reindexConfirmOpen.value = true
}
async function onConfirmReindex() {
  reindexConfirmOpen.value = false
  await onReindex()
}

	function syncFromRoute() {
	  const q = typeof route.query?.q === 'string' ? route.query.q : ''
	  const cid = normalizeCategoryId(route.query?.categoryId)
	  const t = normalizeTag(route.query?.tag)
	
	  const hasAny = !!q || cid > 0 || !!t
	  if (!hasAny) {
	    keyword.value = ''
	    categoryId.value = ''
	    tagDraft.value = ''
	    items.value = []
	    error.value = ''
	    page.value = 0
	    return
	  }
	
	  let changed = false
	  if (q !== keyword.value) {
	    keyword.value = q
	    changed = true
	  }
	  const cidStr = cid > 0 ? String(cid) : ''
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
.search-card {
  margin-top: 12px;
  padding: 16px;
}

	.search-row {
	  gap: 12px;
	}

	.search-filters {
	  margin-top: 12px;
	  gap: 12px;
	  flex-wrap: wrap;
	  align-items: center;
	}

	.search-select {
	  height: 38px;
	  min-width: 160px;
	  font-size: 13px;
	}

	.search-tag {
	  min-width: 220px;
	  flex: 1;
	}
	</style>
