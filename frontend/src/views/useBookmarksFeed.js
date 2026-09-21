import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { listBookmarks } from '../api/services/bookmarkService'
import { useAuthStore } from '../stores/auth'
import { identityScope } from '../stores/identityScope'
import { useSocialPrefsStore } from '../stores/socialPrefs'
import { useTaxonomyStore } from '../stores/taxonomy'
import { normalizeOpaqueId } from '../utils/opaqueId'
import { mergeAppendedById } from '../utils/mergeById'
import { createLatestRequestTracker } from '../utils/latestRequest'

export function useBookmarksFeed() {
  const router = useRouter()
  const taxonomy = useTaxonomyStore()
  const prefs = useSocialPrefsStore()
  const auth = useAuthStore()

  const items = ref(/** @type {Array<Record<string, unknown>>} */ ([]))
  const loading = ref(false)
  const loadingMore = ref(false)
  const error = ref('')
  const pageError = ref('')

  const page = ref(0)
  const size = 10
  const hasNext = ref(true)

  const sessionScope = computed(() => identityScope(auth))
  const loadTracker = createLatestRequestTracker({ getScope: () => sessionScope.value })

  function categoryLabel(id) {
    const cid = normalizeOpaqueId(id)
    if (!cid) return ''
    const c = taxonomy.categoriesById.get(cid)
    return c?.name || `分类#${cid}`
  }

  function openPost(p) {
    if (!p) return
    router.push({ name: 'postDetail', params: { postId: String(p.id) } })
  }

  async function load(append = false, targetPage = page.value) {
    if (!auth.authed) return
    const requestHandle = loadTracker.begin()
    if (append) loadingMore.value = true
    else {
      loading.value = true
      loadingMore.value = false
    }

    if (append) pageError.value = ''
    else error.value = ''
    try {
      await taxonomy.ensureCategories()
      await prefs.ensureBlocked()

      if (!loadTracker.isCurrent(requestHandle)) return

      const resp = await listBookmarks({ page: targetPage, size })
      if (!loadTracker.isCurrent(requestHandle)) return

      const raw = Array.isArray(resp?.data) ? resp.data : []
      const filtered = prefs.blockedSet.size > 0 ? raw.filter((p) => !prefs.blockedSet.has(normalizeOpaqueId(p?.userId))) : raw

      hasNext.value = raw.length >= size
      if (append && raw.length === 0) return
      page.value = targetPage
      items.value = append ? mergeAppendedById(items.value, filtered) : filtered
    } catch (e) {
      if (!loadTracker.isCurrent(requestHandle)) return
      if (append) pageError.value = e?.message || '加载更多失败'
      else error.value = e?.message || '加载失败'
    } finally {
      if (loadTracker.isCurrent(requestHandle)) {
        loading.value = false
        loadingMore.value = false
      }
    }
  }

  async function reload() {
    pageError.value = ''
    await load(false, 0)
  }

  async function loadMore() {
    if (loading.value || loadingMore.value || !hasNext.value) return
    await load(true, page.value + 1)
  }

  onMounted(reload)
  watch(
    sessionScope,
    () => {
      loadTracker.invalidate()
      items.value = []
      page.value = 0
      hasNext.value = true
      loading.value = false
      loadingMore.value = false
      error.value = ''
      pageError.value = ''
      if (auth.authed) reload()
    }
  )
  onBeforeUnmount(() => {
    loadTracker.invalidate()
  })

  return {
    items,
    page,
    hasNext,
    loading,
    loadingMore,
    error,
    pageError,
    categoryLabel,
    openPost,
    reload,
    loadMore
  }
}
