import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { listBookmarks } from '../api/services/bookmarkService'
import { useAuthStore } from '../stores/auth'
import { useSocialPrefsStore } from '../stores/socialPrefs'
import { useTaxonomyStore } from '../stores/taxonomy'
import { normalizeOpaqueId } from '../utils/opaqueId'

export function useBookmarksFeed() {
  const router = useRouter()
  const taxonomy = useTaxonomyStore()
  const prefs = useSocialPrefsStore()
  const auth = useAuthStore()

  const items = ref([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const error = ref('')
  const pageError = ref('')

  const page = ref(0)
  const size = 10
  const hasNext = ref(true)
  let requestGeneration = 0

  const sessionScope = computed(() => [
    auth.tokenGeneration,
    normalizeOpaqueId(auth.userId),
    auth.authed ? 'authenticated' : 'anonymous'
  ].join(':'))

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
    const generation = ++requestGeneration
    const scope = sessionScope.value
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

      if (generation !== requestGeneration || scope !== sessionScope.value) return

      const resp = await listBookmarks({ page: targetPage, size })
      if (generation !== requestGeneration || scope !== sessionScope.value) return

      const raw = Array.isArray(resp?.data) ? resp.data : []
      const filtered = prefs.blockedSet.size > 0 ? raw.filter((p) => !prefs.blockedSet.has(normalizeOpaqueId(p?.userId))) : raw

      hasNext.value = raw.length >= size
      if (append && raw.length === 0) return
      page.value = targetPage
      items.value = append ? [...items.value, ...filtered] : filtered
    } catch (e) {
      if (generation !== requestGeneration || scope !== sessionScope.value) return
      if (append) pageError.value = e?.message || '加载更多失败'
      else error.value = e?.message || '加载失败'
    } finally {
      if (generation === requestGeneration) {
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
      requestGeneration += 1
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
    requestGeneration += 1
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
