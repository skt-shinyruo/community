import { computed, inject, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useSocialPrefsStore } from '../../stores/socialPrefs'
import { listPosts, createPost as apiCreatePost } from '../../api/services/postService'
import { setLike } from '../../api/services/socialService'
import { suggestTags as apiSuggestTags } from '../../api/services/taxonomyService'
import {
  canJumpToLastSeenDivider,
  findLastSeenDividerIndex,
  hasLastSeenDivider,
  isDefaultLatestFeedView,
  resolveAppendPageAfterLoad
} from '../postsFeedState'
import {
  collectPostsHydrationIds,
  commitComposerTagDraft
} from '../postsViewState'
import { formatTime, formatTimeAgo } from '../../utils/time'
import { getPostReadAt, getPostsListBaselineAt, markPostRead, touchPostsListSeen } from '../../utils/readTracker'
import { normalizeOpaqueId } from '../../utils/opaqueId'
import { useTaxonomyStore } from '../../stores/taxonomy'
import { usePostMetaCacheStore } from '../../stores/postMetaCache'
import {
  POSTS_FILTER,
  POSTS_FILTER_OPTIONS,
  POSTS_ORDER,
  POSTS_ORDER_OPTIONS,
  normalizePostsCategoryId,
  normalizePostsFilter,
  normalizePostsOrder,
  normalizePostsSubscribed
} from '../../router/navigation'
import { buildComposerCategoryOptions } from './usePostComposer'


export function usePostsFeed(emit) {
  const showToast = inject('showToast', () => {})

  const auth = useAuthStore()
  const route = useRoute()
  const router = useRouter()
  const authed = computed(() => !!auth.accessToken)
  const me = computed(() => auth.me || {})
  const postMetaCache = usePostMetaCacheStore()

  const order = computed(() => normalizePostsOrder(route.query?.order))
  const filter = computed(() => normalizePostsFilter(route.query?.type))
  const subscribed = computed(() => normalizePostsSubscribed(route.query?.subscribed))
  const categoryId = computed(() => normalizePostsCategoryId(route.query?.categoryId))
  const tag = computed(() => {
    const raw = String(route.query?.tag || '').trim()
    return raw.startsWith('#') ? raw.slice(1).trim() : raw
  })

  const orderOptions = POSTS_ORDER_OPTIONS
  const filterOptions = POSTS_FILTER_OPTIONS

  const taxonomy = useTaxonomyStore()
  const categories = computed(() => (Array.isArray(taxonomy.categories) ? taxonomy.categories : []))
  const composerCategoryOptions = computed(() => buildComposerCategoryOptions(categories.value))

  function categoryLabel(id) {
    const cid = normalizeOpaqueId(id)
    if (!cid) return ''
    const c = taxonomy.categoriesById.get(cid)
    return c?.name || `分类#${cid}`
  }

  const showClear = computed(
    () =>
      order.value !== POSTS_ORDER.LATEST ||
      filter.value !== POSTS_FILTER.ALL ||
      subscribed.value === true ||
      !!categoryId.value ||
      !!tag.value
  )

  const socialPrefs = useSocialPrefsStore()
  const blockedSet = computed(() => socialPrefs.blockedSet)
  const blockedHiddenCount = ref(0)

  function goLogin() {
    router.push({ name: 'login', query: { redirect: route.fullPath || '/posts' } })
  }

  function replaceQuery(partial) {
    const next = { ...(route.query || {}) }

    if (Object.prototype.hasOwnProperty.call(partial, 'order')) {
      const nextOrder = normalizePostsOrder(partial.order)
      if (nextOrder === POSTS_ORDER.LATEST) delete next.order
      else next.order = nextOrder
    }

    if (Object.prototype.hasOwnProperty.call(partial, 'filter')) {
      const nextFilter = normalizePostsFilter(partial.filter)
      if (!nextFilter) delete next.type
      else next.type = nextFilter
    }

    if (Object.prototype.hasOwnProperty.call(partial, 'categoryId')) {
      const nextCategoryId = normalizePostsCategoryId(partial.categoryId)
      if (!nextCategoryId) delete next.categoryId
      else next.categoryId = String(nextCategoryId)
    }

    if (Object.prototype.hasOwnProperty.call(partial, 'tag')) {
      let nextTag = String(partial.tag || '').trim()
      if (nextTag.startsWith('#')) nextTag = nextTag.slice(1).trim()
      if (!nextTag) delete next.tag
      else next.tag = nextTag
    }

    if (Object.prototype.hasOwnProperty.call(partial, 'subscribed')) {
      const nextSubscribed = normalizePostsSubscribed(partial.subscribed)
      if (!nextSubscribed) delete next.subscribed
      else next.subscribed = '1'
    }

    router.replace({ name: 'posts', query: next })
  }

  function setOrder(v) {
    replaceQuery({ order: v })
  }

  function setFilter(v) {
    replaceQuery({ filter: v })
  }

  function setCategoryId(v) {
    replaceQuery({ categoryId: v })
  }

  function setTag(v) {
    replaceQuery({ tag: v })
  }

  function setSubscribed(v) {
    replaceQuery({ subscribed: v })
  }

  function clearQuery() {
    replaceQuery({ order: POSTS_ORDER.LATEST, filter: POSTS_FILTER.ALL, subscribed: false, categoryId: '', tag: '' })
  }

  const page = ref(0)
  const size = ref(10)
  const items = ref([])
  // We assume hasNext if last fetch returned full size
  const hasNext = ref(true)
  const loading = ref(false)
  const error = ref('')

  // Publish interaction
  const isPublishFocused = ref(false)
  const newTitle = ref('')
  const newContent = ref('')
  const newCategoryId = ref('')
  const newTagDraft = ref('')
  const newTags = ref([])
  const newTagError = ref('')

  const composerTagSuggest = ref([])
  const composerTagSuggestNames = computed(() =>
    (Array.isArray(composerTagSuggest.value) ? composerTagSuggest.value : []).map((tag) => String(tag?.name || '').trim()).filter(Boolean)
  )
  const creating = ref(false)
  const createError = ref('')

  const seenBaselineAt = ref(0)
  let touchedLatestOnce = false

  let lastLoadToken = 0

  function toMs(v) {
    if (!v) return 0
    const t = new Date(v).getTime()
    return Number.isFinite(t) ? t : 0
  }

  function activityTime(p) {
    return p?.lastActivityTime || p?.lastReplyTime || p?.createTime || null
  }

  function activityUserId(p) {
    const v = normalizeOpaqueId(p?.lastReplyUserId)
    if (v) return v
    return normalizeOpaqueId(p?.userId)
  }

  function activityUser(p) {
    return p?.lastReplyAuthor || p?.author || null
  }

  function commitNewTags() {
    const result = commitComposerTagDraft(newTags.value, newTagDraft.value)
    newTags.value = result.tags
    newTagError.value = result.error
    newTagDraft.value = result.draft
  }

  function onTagDraftKeydown(e) {
    const key = String(e?.key || '')
    if (key === ',' || key === '，') {
      e?.preventDefault?.()
      commitNewTags()
    }
  }

  function removeNewTag(t) {
    const key = String(t || '').toLowerCase()
    newTags.value = (Array.isArray(newTags.value) ? newTags.value : []).filter((x) => String(x || '').toLowerCase() !== key)
  }

  let suggestTimer = 0
  let suggestToken = 0

  watch(newTagDraft, (v) => {
    if (suggestTimer) window.clearTimeout(suggestTimer)
    const q = String(v || '').trim()
    if (!q) {
      composerTagSuggest.value = (Array.isArray(taxonomy.hotTags) ? taxonomy.hotTags : []).slice(0, 8)
      return
    }
    const token = ++suggestToken
    suggestTimer = window.setTimeout(async () => {
      try {
        const resp = await apiSuggestTags({ q, limit: 8 })
        if (token !== suggestToken) return
        composerTagSuggest.value = Array.isArray(resp?.data) ? resp.data : []
      } catch {
        // ignore：suggest 失败时不阻塞发帖
      }
    }, 180)
  })

  watch(isPublishFocused, (v) => {
    if (!v) return
    // 在分类页发帖时，默认带上当前分类
    if (!newCategoryId.value && categoryId.value) {
      newCategoryId.value = String(categoryId.value)
    }
  })

  const tagSuggestions = computed(() =>
    (Array.isArray(taxonomy.hotTags) ? taxonomy.hotTags : [])
      .map((t) => t?.name)
      .filter(Boolean)
      .slice(0, 12)
  )

  const lastSeenDividerRef = ref(null)
  const newHintDismissed = ref(false)

  const isDefaultLatestFeed = computed(() =>
    isDefaultLatestFeedView({
      order: order.value,
      filter: filter.value,
      subscribed: subscribed.value,
      categoryId: categoryId.value,
      tag: tag.value,
      page: page.value
    })
  )

  const newSinceLastSeenCount = computed(() => {
    if (!isDefaultLatestFeed.value) return 0
    const baseline = Number(seenBaselineAt.value || 0)
    if (!baseline) return 0
    return (Array.isArray(items.value) ? items.value : []).reduce((acc, p) => {
      return toMs(activityTime(p)) > baseline ? acc + 1 : acc
    }, 0)
  })

  const newDividerIndex = computed(() => {
    if (!isDefaultLatestFeed.value) return -1
    return findLastSeenDividerIndex(items.value, seenBaselineAt.value, (item) => toMs(activityTime(item)))
  })

  const shouldShowLastSeenDivider = computed(() =>
    hasLastSeenDivider({
      isLatestFeedView: isDefaultLatestFeed.value,
      dividerIndex: newDividerIndex.value,
      itemsLength: items.value?.length || 0
    })
  )

  const shouldShowNewHint = computed(
    () => isDefaultLatestFeed.value && newSinceLastSeenCount.value > 0 && !newHintDismissed.value
  )

  const canJumpToLastSeen = computed(() =>
    canJumpToLastSeenDivider({
      isLatestFeedView: isDefaultLatestFeed.value,
      newSinceLastSeenCount: newSinceLastSeenCount.value,
      newHintDismissed: newHintDismissed.value,
      dividerIndex: newDividerIndex.value,
      itemsLength: items.value?.length || 0
    })
  )

  function getLastSeenDividerEl() {
    const r = lastSeenDividerRef.value
    if (Array.isArray(r)) return r[0] || null
    return r
  }

  function scrollToLastSeenDivider() {
    const el = getLastSeenDividerEl()
    if (!el || typeof el.scrollIntoView !== 'function') return
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  function isUnread(p) {
    if (!authed.value || !p) return false
    const last = toMs(activityTime(p))
    if (!last) return false
    const readAt = getPostReadAt(p?.id)
    const baseline = readAt > 0 ? readAt : seenBaselineAt.value
    return last > Number(baseline || 0)
  }

  function openPost(p) {
    if (!p) return
    if (authed.value) markPostRead(p?.id)
    router.push({ name: 'postDetail', params: { postId: String(p.id) } })
  }

  async function hydrateBatch(list, token) {
    if (!Array.isArray(list) || list.length === 0) return

    const { userIds, postIds } = collectPostsHydrationIds(list)
    let users = {}
    let counts = {}
    let statuses = {}

    try {
      users = await postMetaCache.ensureUserSummaries(userIds)
    } catch {
      users = {}
    }
    try {
      counts = await postMetaCache.ensureLikeCounts(1, postIds)
    } catch {
      counts = {}
    }
    if (authed.value) {
      try {
        statuses = await postMetaCache.ensureLikeStatuses(1, postIds)
      } catch {
        statuses = {}
      }
    }

    if (token !== lastLoadToken) return

    for (const p of list) {
      if (!p) continue
      const uid = normalizeOpaqueId(p?.userId)
      const lr = normalizeOpaqueId(p?.lastReplyUserId)
      const pid = normalizeOpaqueId(p?.id)

      p.author = users?.[uid] || null
      p.lastReplyAuthor = lr ? (users?.[lr] || null) : null

      const likeCount = counts?.[pid]
      p.likeCount = typeof likeCount === 'number' ? likeCount : 0

      const liked = statuses?.[pid]
      p.liked = !!liked
    }
  }

  function scheduleHydrate(list, token) {
    if (!Array.isArray(list) || list.length === 0) return

    // 让首屏先渲染：异步补水作者/点赞数，避免阻塞列表展示。
    setTimeout(() => {
      if (token !== lastLoadToken) return
      hydrateBatch(list, token)
    }, 0)
  }

  function applyFilter(list) {
    const ft = filter.value
    if (!ft) return list
    if (ft === POSTS_FILTER.UNREAD) return list.filter((p) => isUnread(p))
    if (ft === POSTS_FILTER.TOP) return list.filter((p) => Number(p?.type || 0) === 1)
    if (ft === POSTS_FILTER.WONDERFUL) return list.filter((p) => Number(p?.status || 0) === 1)
    return list
  }

  async function load(append = false) {
    if (append && loading.value) return false

    const token = ++lastLoadToken
    if (!append) error.value = ''
    loading.value = true
    try {
      if (authed.value) {
        try {
          await socialPrefs.ensureBlocked()
        } catch {
          // ignore：拉黑列表失败不阻塞列表查询
        }
      } else {
        socialPrefs.clear()
      }

      if (subscribed.value === true && !authed.value) {
        showToast({ type: 'warning', text: '登录后可查看订阅内容' })
        setSubscribed(false)
        return false
      }

      const resp = await listPosts({
        order: order.value,
        page: page.value,
        size: size.value,
        subscribed: subscribed.value === true,
        categoryId: categoryId.value,
        tag: tag.value
      })
      emit('trace', resp?.traceId || '')

      if (token !== lastLoadToken) return

      const base = (Array.isArray(resp?.data) ? resp.data : []).map((p) => ({
        ...p,
        author: p?.author || null,
        liked: !!p?.liked,
        likeCount: Number(p?.likeCount || 0)
      }))

      const afterBlocked = blockedSet.value.size > 0 ? base.filter((p) => !blockedSet.value.has(normalizeOpaqueId(p?.userId))) : base
      blockedHiddenCount.value = Math.max(0, base.length - afterBlocked.length)
      const newItems = applyFilter(afterBlocked)
      
      if ((resp?.data || []).length < size.value) hasNext.value = false
      else hasNext.value = true
      
      if (append) {
         items.value = [...items.value, ...newItems]
      } else {
         items.value = newItems
      }

      scheduleHydrate(newItems, token)
      return true
    } catch (e) {
      if (token !== lastLoadToken) return
      if (!append) error.value = e?.message || '加载失败'
      else showToast({ type: 'error', text: '加载更多失败' })
      return false
    } finally {
      if (token === lastLoadToken) {
        loading.value = false
      }
    }
  }

  async function loadMore() {
    const previousPage = page.value
    page.value = resolveAppendPageAfterLoad({ previousPage, didLoadSucceed: true })
    const didLoadSucceed = await load(true)
    page.value = resolveAppendPageAfterLoad({ previousPage, didLoadSucceed })
  }

  async function reload() {
    page.value = 0
    hasNext.value = true
    await load(false)
  }

  async function togglePostLike(p) {
    if (!authed.value || !p) return showToast({ type: 'warning', text: '请先登录' })
    try {
       const resp = await setLike({
        entityType: 1,
        entityId: p.id,
        entityUserId: p.userId,
        postId: p.id,
        liked: null
      })
       emit('trace', resp?.traceId || '')
       if (typeof resp?.data?.likeCount === 'number') {
         p.likeCount = resp.data.likeCount
         postMetaCache.setLikeCount(1, p.id, p.likeCount)
       }
       if (typeof resp?.data?.liked === 'boolean') {
         p.liked = resp.data.liked
         postMetaCache.setLikeStatus(1, p.id, p.liked)
       }
    } catch (e) {
      showToast({ type: 'error', text: e?.message || '点赞失败' })
    }
  }

  async function createPost() {
    createError.value = ''
    commitNewTags()
    if (newTagError.value) {
      createError.value = newTagError.value
      return
    }
    if (!newTitle.value || !newContent.value) {
      createError.value = '请填写完整内容'
      return
    }
    creating.value = true
    try {
      const cid = normalizeOpaqueId(newCategoryId.value)
      const resp = await apiCreatePost({
        title: newTitle.value,
        content: newContent.value,
        categoryId: cid || undefined,
        tags: newTags.value
      })
      emit('trace', resp?.traceId || '')

      const createdPostId = normalizeOpaqueId(resp?.data?.postId)
      const hasPostId = !!createdPostId
      showToast({
        type: 'success',
        title: '发布成功',
        text: '你的帖子已发布。搜索/通知为最终一致，结果可能延迟数秒到数十秒。',
        duration: 6000,
        actionText: hasPostId ? '立即查看帖子' : '',
        onAction: hasPostId ? () => router.push({ name: 'postDetail', params: { postId: String(createdPostId) } }) : null
      })
      
      newTitle.value = ''
      newContent.value = ''
      newCategoryId.value = ''
      newTagDraft.value = ''
      newTags.value = []
      newTagError.value = ''
      isPublishFocused.value = false 
      await reload()
    } catch (e) {
      createError.value = e?.message || '发布失败'
    } finally {
      creating.value = false
    }
  }

  watch(
    () => auth.accessToken,
    () => {
      // 点赞状态与登录态相关：切换账号/退出登录时清理缓存并刷新 UI。
      postMetaCache.clearLikeStatuses()
      if (!authed.value) {
        for (const p of Array.isArray(items.value) ? items.value : []) {
          if (p) p.liked = false
        }
      }
      scheduleHydrate(items.value, lastLoadToken)
    }
  )

  watch([order, filter, subscribed, categoryId, tag], () => {
    newHintDismissed.value = false
    reload()
  })

  onMounted(() => {
    taxonomy.ensureCategories()
    taxonomy.ensureHotTags(12)
    seenBaselineAt.value = getPostsListBaselineAt()
    if (isDefaultLatestFeed.value && !touchedLatestOnce) {
      touchedLatestOnce = true
      seenBaselineAt.value = touchPostsListSeen()
    }
    reload()
  })

  watch(isDefaultLatestFeed, (v) => {
    if (v && !touchedLatestOnce) {
      touchedLatestOnce = true
      seenBaselineAt.value = touchPostsListSeen()
    }
  })

  return {
    auth,
    authed,
    me,
    router,
    order,
    filter,
    subscribed,
    categoryId,
    tag,
    orderOptions,
    filterOptions,
    taxonomy,
    categories,
    composerCategoryOptions,
    categoryLabel,
    showClear,
    blockedSet,
    blockedHiddenCount,
    goLogin,
    setOrder,
    setFilter,
    setCategoryId,
    setTag,
    setSubscribed,
    clearQuery,
    page,
    size,
    items,
    hasNext,
    loading,
    error,
    isPublishFocused,
    newTitle,
    newContent,
    newCategoryId,
    newTagDraft,
    newTags,
    newTagError,
    composerTagSuggest,
    composerTagSuggestNames,
    creating,
    createError,
    seenBaselineAt,
    toMs,
    activityTime,
    activityUserId,
    activityUser,
    commitNewTags,
    onTagDraftKeydown,
    removeNewTag,
    tagSuggestions,
    lastSeenDividerRef,
    newHintDismissed,
    isDefaultLatestFeed,
    newSinceLastSeenCount,
    newDividerIndex,
    shouldShowLastSeenDivider,
    shouldShowNewHint,
    canJumpToLastSeen,
    getLastSeenDividerEl,
    scrollToLastSeenDivider,
    isUnread,
    openPost,
    load,
    loadMore,
    reload,
    togglePostLike,
    createPost,
    formatTime,
    formatTimeAgo
  }
}
