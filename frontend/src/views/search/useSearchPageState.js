import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchPosts } from '../../api/services/searchService'
import { batchPostSummaries } from '../../api/services/postService'
import { suggestTags as apiSuggestTags } from '../../api/services/taxonomyService'
import { useTagSuggestions } from '../../composables/useTagSuggestions'
import { usePostMetaCacheStore } from '../../stores/postMetaCache'
import { useTaxonomyStore } from '../../stores/taxonomy'
import { createLatestRequestTracker } from '../../utils/latestRequest'
import { normalizeOpaqueId } from '../../utils/opaqueId'
import {
  applySearchHydration,
  applySearchSummaries,
  collectSearchHydrationIds
} from '../searchResultSurface'

export function normalizeSearchTag(value) {
  let tag = String(value || '').trim()
  if (tag.startsWith('#')) tag = tag.slice(1).trim()
  return tag
}

export function parseSearchRouteQuery(query = {}) {
  return {
    keyword: typeof query?.q === 'string' ? query.q : '',
    categoryId: normalizeOpaqueId(query?.categoryId),
    tag: normalizeSearchTag(query?.tag)
  }
}

export function serializeSearchRouteQuery(currentQuery = {}, changes = {}) {
  const next = { ...currentQuery }

  if (Object.prototype.hasOwnProperty.call(changes, 'keyword')) {
    const keyword = String(changes.keyword || '').trim()
    if (keyword) next.q = keyword
    else delete next.q
  }
  if (Object.prototype.hasOwnProperty.call(changes, 'categoryId')) {
    const categoryId = normalizeOpaqueId(changes.categoryId)
    if (categoryId) next.categoryId = categoryId
    else delete next.categoryId
  }
  if (Object.prototype.hasOwnProperty.call(changes, 'tag')) {
    const tag = normalizeSearchTag(changes.tag)
    if (tag) next.tag = tag
    else delete next.tag
  }

  return next
}

export function useSearchPageState() {
  const route = useRoute()
  const router = useRouter()
  const taxonomy = useTaxonomyStore()
  const postMetaCache = usePostMetaCacheStore()
  const searchRequestTracker = createLatestRequestTracker()

  const keyword = ref('')
  const categoryId = ref('')
  const tagDraft = ref('')
  const page = ref(0)
  const pageSize = 10
  const loading = ref(false)
  const loadingMore = ref(false)
  const error = ref('')
  const pageError = ref('')
  const items = ref([])
  const hasNext = ref(false)

  const { suggestions: suggestedTags } = useTagSuggestions({
    query: tagDraft,
    hotTags: computed(() => taxonomy.hotTags),
    suggest: apiSuggestTags,
    limit: 10
  })
  const tagSuggestNames = computed(() =>
    suggestedTags.value.map((tag) => tag?.name).filter(Boolean)
  )
  const categories = computed(() => (Array.isArray(taxonomy.categories) ? taxonomy.categories : []))
  const categoryOptions = computed(() => [
    { label: '全部分类', value: '' },
    ...categories.value.map((category) => ({
      label: category.name,
      value: String(category.id)
    }))
  ])

  function categoryLabel(id) {
    const normalizedId = normalizeOpaqueId(id)
    if (!normalizedId) return ''
    return taxonomy.categoriesById.get(normalizedId)?.name || `分类#${normalizedId}`
  }

  function replaceSearchRoute(changes) {
    router.replace({
      name: 'search',
      query: serializeSearchRouteQuery(route.query, changes)
    })
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
    if (userResult.status === 'fulfilled') users = userResult.value || {}
    if (likeCountResult.status === 'fulfilled') likeCounts = likeCountResult.value || {}

    return applySearchHydration(merged, { users, likeCounts })
  }

  // 追加式分页：fresh 加载（搜索/筛选/重试）替换整份结果，append 加载把下一页
  // 接到已有结果尾部；append 失败只记 pageError，保留已加载列表并由「加载更多」重试。
  async function loadSearchPage(targetPage = page.value, { append = false } = {}) {
    const token = searchRequestTracker.begin()
    const requestedPage = Math.max(0, Number(targetPage || 0))
    if (append) {
      pageError.value = ''
      loadingMore.value = true
    } else {
      error.value = ''
      loading.value = true
    }
    try {
      const { data } = await searchPosts({
        keyword: keyword.value,
        categoryId: normalizeOpaqueId(categoryId.value),
        tag: normalizeSearchTag(tagDraft.value),
        page: requestedPage,
        size: pageSize
      })
      if (!searchRequestTracker.isCurrent(token)) return false
      const rawItems = Array.isArray(data) ? data : []
      const nextItems = await resolveSearchItems(rawItems)
      if (!searchRequestTracker.isCurrent(token)) return false

      hasNext.value = rawItems.length >= pageSize
      if (requestedPage > page.value && rawItems.length === 0) return false
      page.value = requestedPage
      items.value = append ? [...items.value, ...nextItems] : nextItems
      return true
    } catch (cause) {
      if (!searchRequestTracker.isCurrent(token)) return false
      if (append) pageError.value = cause?.message || '加载更多失败'
      else error.value = cause?.message || '搜索失败'
      return false
    } finally {
      if (searchRequestTracker.isCurrent(token)) {
        loading.value = false
        loadingMore.value = false
      }
    }
  }

  async function submitSearch() {
    replaceSearchRoute({
      keyword: keyword.value,
      categoryId: categoryId.value,
      tag: tagDraft.value
    })
    await loadSearchPage(0)
  }

  async function changeCategory(value) {
    categoryId.value = normalizeOpaqueId(value)
    replaceSearchRoute({ categoryId: categoryId.value })
    await loadSearchPage(0)
  }

  async function commitTag(value = tagDraft.value) {
    tagDraft.value = normalizeSearchTag(value)
    replaceSearchRoute({ tag: tagDraft.value })
    await loadSearchPage(0)
  }

  async function clearFilters() {
    categoryId.value = ''
    tagDraft.value = ''
    replaceSearchRoute({ categoryId: '', tag: '' })
    await loadSearchPage(0)
  }

  function resetSearchState({ clearCriteria = false } = {}) {
    searchRequestTracker.invalidate()
    if (clearCriteria) {
      keyword.value = ''
      categoryId.value = ''
      tagDraft.value = ''
    }
    page.value = 0
    items.value = []
    hasNext.value = false
    error.value = ''
    pageError.value = ''
    loading.value = false
    loadingMore.value = false
  }

  function clearSearch() {
    resetSearchState({ clearCriteria: true })
    router.replace({ name: 'search', query: {} })
  }

  async function reload() {
    await loadSearchPage(0)
  }

  async function loadMore() {
    if (loading.value || loadingMore.value || !hasNext.value) return
    await loadSearchPage(page.value + 1, { append: true })
  }

  function applyRouteSearch() {
    const criteria = parseSearchRouteQuery(route.query)
    if (!criteria.keyword && !criteria.categoryId && !criteria.tag) {
      resetSearchState({ clearCriteria: true })
      return
    }

    const changed = criteria.keyword !== keyword.value
      || criteria.categoryId !== categoryId.value
      || criteria.tag !== tagDraft.value
    keyword.value = criteria.keyword
    categoryId.value = criteria.categoryId
    tagDraft.value = criteria.tag
    if (changed) loadSearchPage(0)
  }

  onMounted(() => {
    taxonomy.ensureCategories()
    taxonomy.ensureHotTags(10)
    applyRouteSearch()
  })
  watch(
    [() => route.query?.q, () => route.query?.categoryId, () => route.query?.tag],
    applyRouteSearch
  )
  onUnmounted(() => searchRequestTracker.invalidate())

  return {
    keyword,
    categoryId,
    tagDraft,
    tagSuggestNames,
    categoryOptions,
    categoryLabel,
    page,
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
    loadMore,
    applyRouteSearch
  }
}
