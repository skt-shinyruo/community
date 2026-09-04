import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useSocialPrefsStore } from '../../stores/socialPrefs'
import { listBoardFeed, listGlobalFeed, createPost as apiCreatePost, batchPostSummaries } from '../../api/services/postService'
import { searchPosts as apiSearchPosts } from '../../api/services/searchService'
import { setLike } from '../../api/services/socialService'
import { suggestTags as apiSuggestTags } from '../../api/services/taxonomyService'
import {
  collectPostsHydrationIds,
  commitComposerTagDraft,
  parsePostsRouteQuery,
  resolvePostsFeedPlan,
  serializePostsRouteQuery
} from '../postsViewState'
import { getPostReadAt, getPostsListBaselineAt, markPostRead, touchPostsListSeen } from '../../utils/readTracker'
import { normalizeOpaqueId } from '../../utils/opaqueId'
import { useTaxonomyStore } from '../../stores/taxonomy'
import { usePostMetaCacheStore } from '../../stores/postMetaCache'
import { createWriteAttempt } from '../../api/writeAttempt'
import { useTagSuggestions } from '../../composables/useTagSuggestions'
import { showErrorToast, showToast } from '../../ui/toastService'

function buildComposerCategoryOptions(categories) {
  return [
    { label: '不选择', value: '' },
    ...(Array.isArray(categories) ? categories : []).map((category) => ({
      label: category.name,
      value: String(category.id)
    }))
  ]
}

export function findLastSeenDividerIndex(items, baselineAt, getActivityAt = (item) => item?.activityAt) {
  const baseline = Number(baselineAt || 0)
  if (!Number.isFinite(baseline) || baseline <= 0) return -1

  const list = Array.isArray(items) ? items : []
  for (let i = 0; i < list.length; i += 1) {
    const activityAt = Number(getActivityAt(list[i]) || 0)
    if (activityAt > 0 && activityAt <= baseline) return i
  }
  return -1
}

export function hasLastSeenDivider({ isLatestFeedView, dividerIndex, itemsLength } = {}) {
  const count = Number(itemsLength || 0)
  return !!isLatestFeedView && Number(dividerIndex) > 0 && Number(dividerIndex) < count
}

export function canJumpToLastSeenDivider({
  isLatestFeedView,
  newSinceLastSeenCount,
  newHintDismissed,
  dividerIndex,
  itemsLength
} = {}) {
  return (
    !!isLatestFeedView &&
    Number(newSinceLastSeenCount || 0) > 0 &&
    newHintDismissed !== true &&
    hasLastSeenDivider({ isLatestFeedView, dividerIndex, itemsLength })
  )
}

export function usePostsFeed() {
  const auth = useAuthStore()
  const route = useRoute()
  const router = useRouter()
  const authed = computed(() => !!auth.accessToken)
  const me = computed(() => auth.me || {})
  const readIdentityId = computed(() => normalizeOpaqueId(auth.userId) || 'anonymous')
  const postMetaCache = usePostMetaCacheStore()

  const routeQuery = computed(() => parsePostsRouteQuery(route.query))
  const categoryId = computed(() => routeQuery.value.categoryId)
  const order = computed(() => routeQuery.value.order)
  const tag = computed(() => routeQuery.value.tag)

  const taxonomy = useTaxonomyStore()
  const categories = computed(() => (Array.isArray(taxonomy.categories) ? taxonomy.categories : []))
  const composerCategoryOptions = computed(() => buildComposerCategoryOptions(categories.value))

  function categoryLabel(id) {
    const cid = normalizeOpaqueId(id)
    if (!cid) return ''
    const c = taxonomy.categoriesById.get(cid)
    return c?.name || `分类#${cid}`
  }

  const showClear = computed(() => !!(categoryId.value || tag.value) || order.value !== 'latest')

  const socialPrefs = useSocialPrefsStore()
  const blockedSet = computed(() => socialPrefs.blockedSet)
  const blockedHiddenCount = ref(0)

  function goLogin() {
    router.push({ name: 'login', query: { redirect: route.fullPath || '/posts' } })
  }

  function replaceQuery(changes) {
    router.replace({ name: 'posts', query: serializePostsRouteQuery(route.query, changes) })
  }

  function setOrder(v) {
    replaceQuery({ order: v })
  }

  function setCategoryId(v) {
    replaceQuery({ categoryId: v })
  }

  function setTag(v) {
    replaceQuery({ tag: v })
  }

  function clearTag() {
    replaceQuery({ tag: '' })
  }

  function clearQuery() {
    replaceQuery({ categoryId: '', tag: '', order: 'latest' })
  }

  const nextCursor = ref('')
  const searchPage = ref(0)
  const pageSize = 10
  const items = ref([])
  const hasNext = ref(true)
  const loading = ref(false)
  const error = ref('')

  // Publish interaction
  const isPublishFocused = ref(false)
  const newTitle = ref('')
  const newBlocks = ref([{ type: 'paragraph', text: '' }])
  const newCategoryId = ref('')
  const newTagDraft = ref('')
  const newTags = ref([])
  const newTagError = ref('')

  const { suggestions: composerTagSuggest } = useTagSuggestions({
    query: newTagDraft,
    hotTags: computed(() => taxonomy.hotTags),
    suggest: apiSuggestTags,
    limit: 8
  })
  const composerTagSuggestNames = computed(() =>
    (Array.isArray(composerTagSuggest.value) ? composerTagSuggest.value : []).map((tag) => String(tag?.name || '').trim()).filter(Boolean)
  )
  const creating = ref(false)
  const createError = ref('')
  const createAttempt = createWriteAttempt()

  const seenBaselineAt = ref(0)
  let touchedLatestIdentity = ''

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

  function resetComposerDraft() {
    createError.value = ''
    newTitle.value = ''
    newBlocks.value = [{ type: 'paragraph', text: '' }]
    newCategoryId.value = ''
    newTagDraft.value = ''
    newTags.value = []
    newTagError.value = ''
  }

  function closeComposer() {
    createAttempt.cancel()
    resetComposerDraft()
    isPublishFocused.value = false
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

  function isMediaBlock(block) {
    return ['image', 'video', 'file'].includes(String(block?.type || '').toLowerCase())
  }

  function hasLocalMediaSelection(block) {
    return !!(
      block?.selectedFile ||
      block?.file ||
      block?.previewUrl ||
      block?.localPreviewUrl ||
      block?.uploadId ||
      block?.uploadError ||
      block?.error
    )
  }

  function validateMediaBlocks() {
    const blocks = Array.isArray(newBlocks.value) ? newBlocks.value : []
    for (const block of blocks) {
      if (!isMediaBlock(block)) continue

      const state = String(block?.uploadState || '').toLowerCase()
      const hasAsset = !!normalizeOpaqueId(block?.assetId)

      if (state === 'uploading' || state === 'pending') return '媒体仍在上传，请等待上传完成后再发布'
      if (state === 'failed') return '媒体上传失败，请重试或移除后再发布'
      if (state === 'completed' && !hasAsset) return '媒体上传失败，请重试或移除后再发布'
      if (!hasAsset && hasLocalMediaSelection(block)) return '媒体仍在上传，请等待上传完成后再发布'
    }
    return ''
  }

  function publishableBlocks() {
    return (Array.isArray(newBlocks.value) ? newBlocks.value : [])
      .filter((b) => {
        const type = String(b?.type || '').toLowerCase()
        if (['paragraph', 'code'].includes(type)) return String(b?.text || '').trim()
        if (['image', 'video', 'file'].includes(type)) return normalizeOpaqueId(b?.assetId)
        return false
      })
      .map((b) => {
        const type = String(b?.type || '').toLowerCase()
        const clean = { type }
        if (b?.text != null && (type === 'paragraph' || type === 'code')) clean.text = String(b.text)
        if (isMediaBlock(b)) clean.assetId = normalizeOpaqueId(b.assetId)
        if (type === 'code' && b?.language) clean.language = String(b.language)
        if ((type === 'image' || type === 'video') && b?.caption) clean.caption = String(b.caption)
        if (type === 'file' && b?.displayName) clean.displayName = String(b.displayName)
        if (b?.metadata && typeof b.metadata === 'object') clean.metadata = b.metadata
        return clean
      })
  }

  function createCommand() {
    const categoryId = normalizeOpaqueId(newCategoryId.value)
    return {
      title: newTitle.value,
      blocks: publishableBlocks(),
      categoryId: categoryId || undefined,
      tags: [...newTags.value]
    }
  }

  function createIntent(command = createCommand()) {
    return JSON.stringify(command)
  }

  watch(isPublishFocused, (v) => {
    if (!v) return
    // 在分类筛选视图发帖时，默认带上当前分类
    if (!newCategoryId.value && categoryId.value) {
      newCategoryId.value = String(categoryId.value)
    }
  })

  const lastSeenDividerRef = ref(null)
  const newHintDismissed = ref(false)

  const isDefaultLatestFeed = computed(() => order.value === 'latest' && !categoryId.value && !tag.value)

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
    const readAt = getPostReadAt(p?.id, { identityId: readIdentityId.value })
    const baseline = readAt > 0 ? readAt : seenBaselineAt.value
    return last > Number(baseline || 0)
  }

  function openPost(p) {
    if (!p) return
    if (authed.value) markPostRead(p?.id, { identityId: readIdentityId.value })
    router.push({ name: 'postDetail', params: { postId: String(p.id) } })
  }

  function openUserProfile(userId) {
    router.push({ name: 'userProfile', params: { userId: String(userId) } })
  }

  async function hydrateBatch(list, token) {
    if (!Array.isArray(list) || list.length === 0) return

    const { userIds, postIds } = collectPostsHydrationIds(list)
    let users
    let counts
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

  async function ensureBlockedReady() {
    if (authed.value) {
      try {
        await socialPrefs.ensureBlocked()
      } catch {
        // ignore：拉黑列表失败不阻塞列表查询
      }
    } else {
      socialPrefs.clear()
    }
  }

  function applyBlockedFilter(base) {
    const afterBlocked = blockedSet.value.size > 0 ? base.filter((p) => !blockedSet.value.has(normalizeOpaqueId(p?.userId))) : base
    blockedHiddenCount.value = Math.max(0, base.length - afterBlocked.length)
    return afterBlocked
  }

  async function loadFeedStack(cursor = '') {
    const append = !!cursor
    if (append && loading.value) return false

    const token = ++lastLoadToken
    if (!append) error.value = ''
    loading.value = true
    try {
      await ensureBlockedReady()

      const resp = categoryId.value
        ? await listBoardFeed(categoryId.value, { cursor, size: pageSize })
        : await listGlobalFeed({ cursor, size: pageSize })
      if (token !== lastLoadToken) return

      const pageData = resp?.data && typeof resp.data === 'object' ? resp.data : {}
      const base = (Array.isArray(pageData.items) ? pageData.items : []).map((p) => ({
        ...p,
        author: p?.author || null,
        liked: !!p?.liked,
        likeCount: Number(p?.likeCount || 0)
      }))

      const newItems = applyBlockedFilter(base)

      const responseNextCursor = String(pageData.nextCursor || '')
      hasNext.value = !!responseNextCursor

      if (append) {
        items.value = [...items.value, ...newItems]
      } else {
        items.value = newItems
      }

      nextCursor.value = responseNextCursor

      scheduleHydrate(newItems, token)
      return true
    } catch (e) {
      if (token !== lastLoadToken) return
      if (!append) error.value = e?.message || '加载失败'
      else showErrorToast(e, { type: 'error', text: '加载更多失败' }, showToast)
      return false
    } finally {
      if (token === lastLoadToken) {
        loading.value = false
      }
    }
  }

  // tag 过滤走搜索栈（最终一致）：搜索结果只携带瘦投影，按命中顺序批量
  // 回补完整摘要，再与 feed 栈共用作者/点赞补水和拉黑过滤。
  async function loadSearchStack({ append = false } = {}) {
    if (append && loading.value) return false

    const token = ++lastLoadToken
    const requestedPage = append ? searchPage.value + 1 : 0
    if (!append) error.value = ''
    loading.value = true
    try {
      await ensureBlockedReady()

      const resp = await apiSearchPosts({
        categoryId: categoryId.value || undefined,
        tag: tag.value,
        page: requestedPage,
        size: pageSize
      })
      if (token !== lastLoadToken) return

      const hits = Array.isArray(resp?.data) ? resp.data : []
      const hitIds = hits.map((hit) => normalizeOpaqueId(hit?.postId)).filter(Boolean)
      let summaries = []
      if (hitIds.length > 0) {
        try {
          const summaryResp = await batchPostSummaries(hitIds)
          summaries = Array.isArray(summaryResp?.data) ? summaryResp.data : []
        } catch {
          summaries = []
        }
      }
      if (token !== lastLoadToken) return

      const summaryById = new Map()
      for (const summary of summaries) {
        const id = normalizeOpaqueId(summary?.id)
        if (id) summaryById.set(id, summary)
      }
      const base = hits
        .map((hit) => {
          const hitId = normalizeOpaqueId(hit?.postId)
          const source = summaryById.get(hitId) || {
            id: hitId,
            userId: hit?.userId,
            title: hit?.title,
            createTime: hit?.createTime,
            categoryId: hit?.categoryId,
            tags: hit?.tags
          }
          return {
            ...source,
            author: source?.author || null,
            liked: !!source?.liked,
            likeCount: Number(source?.likeCount || 0)
          }
        })
        .filter((p) => normalizeOpaqueId(p?.id))

      const newItems = applyBlockedFilter(base)

      hasNext.value = hits.length >= pageSize
      searchPage.value = requestedPage
      nextCursor.value = ''

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
      else showErrorToast(e, { type: 'error', text: '加载更多失败' }, showToast)
      return false
    } finally {
      if (token === lastLoadToken) {
        loading.value = false
      }
    }
  }

  function feedPlan() {
    return resolvePostsFeedPlan({ categoryId: categoryId.value, tag: tag.value })
  }

  async function loadMore() {
    if (loading.value || !hasNext.value) return
    if (feedPlan().source === 'search') {
      await loadSearchStack({ append: true })
      return
    }
    await loadFeedStack(nextCursor.value)
  }

  async function reload() {
    if (feedPlan().source === 'search') {
      await loadSearchStack({ append: false })
      return
    }
    await loadFeedStack('')
  }

  async function togglePostLike(p) {
    if (!authed.value || !p) return showToast({ type: 'warning', text: '请先登录' })
    const authGeneration = auth.tokenGeneration
    try {
       const resp = await setLike({
        entityType: 1,
        entityId: p.id,
        liked: null
      })
       if (auth.tokenGeneration !== authGeneration) return
       if (typeof resp?.data?.likeCount === 'number') {
         p.likeCount = resp.data.likeCount
         postMetaCache.setLikeCount(1, p.id, p.likeCount)
       }
       if (typeof resp?.data?.liked === 'boolean') {
         p.liked = resp.data.liked
         postMetaCache.setLikeStatus(1, p.id, p.liked)
       }
    } catch (e) {
      if (auth.tokenGeneration !== authGeneration) return
      showErrorToast(e, { type: 'error', text: e?.message || '点赞失败' }, showToast)
    }
  }

  async function createPost() {
    createError.value = ''
    commitNewTags()
    if (newTagError.value) {
      createError.value = newTagError.value
      return
    }
    const mediaError = validateMediaBlocks()
    if (mediaError) {
      createError.value = mediaError
      return
    }
    const command = createCommand()
    if (!command.title || command.blocks.length === 0) {
      createError.value = '请填写完整内容'
      return
    }
    creating.value = true
    const authGeneration = auth.tokenGeneration
    const requestedIntent = createIntent(command)
    try {
      const resp = await apiCreatePost(command, { writeAttempt: createAttempt })
      if (auth.tokenGeneration !== authGeneration || requestedIntent !== createIntent()) return
      const createdPostId = normalizeOpaqueId(resp?.data?.postId)
      createAttempt.succeed()
      const hasPostId = !!createdPostId
      showToast({
        type: 'success',
        title: '发布成功',
        text: '你的帖子已发布。搜索/通知为最终一致，结果可能延迟数秒到数十秒。',
        duration: 6000,
        actionText: hasPostId ? '立即查看帖子' : '',
        onAction: hasPostId ? () => router.push({ name: 'postDetail', params: { postId: String(createdPostId) } }) : null
      })
      
      resetComposerDraft()
      isPublishFocused.value = false 
      await reload()
    } catch (e) {
      if (auth.tokenGeneration !== authGeneration || requestedIntent !== createIntent()) return
      createError.value = e?.message || '发布失败'
    } finally {
      if (auth.tokenGeneration === authGeneration) {
        creating.value = false
      }
    }
  }

  watch(
    () => auth.tokenGeneration,
    () => {
      // Feed filtering and interaction overlays are identity-bound.
      lastLoadToken += 1
      loading.value = false
      creating.value = false
      postMetaCache.clearLikeStatuses()
      for (const p of Array.isArray(items.value) ? items.value : []) {
        if (p) p.liked = false
      }
      composerTagSuggest.value = []
      closeComposer()
      if (!authed.value) socialPrefs.clear()
      seenBaselineAt.value = getPostsListBaselineAt({ identityId: readIdentityId.value })
      if (isDefaultLatestFeed.value && touchedLatestIdentity !== readIdentityId.value) {
        touchedLatestIdentity = readIdentityId.value
        seenBaselineAt.value = touchPostsListSeen({ identityId: readIdentityId.value })
      }
      reload()
    }
  )

  watch(
    [newTitle, newBlocks, newCategoryId, newTagDraft, newTags],
    () => createAttempt.changeIntent(),
    { deep: true }
  )

  watch([categoryId, tag, order], () => {
    newHintDismissed.value = false
    reload()
  })

  onMounted(() => {
    taxonomy.ensureCategories()
    taxonomy.ensureHotTags(12)
    // 旧 boardId 链接归一为 categoryId；序列化不触发查询值变化，不会二次加载。
    if (route.query?.boardId !== undefined) {
      router.replace({ name: 'posts', query: serializePostsRouteQuery(route.query, {}) })
    }
    seenBaselineAt.value = getPostsListBaselineAt({ identityId: readIdentityId.value })
    if (isDefaultLatestFeed.value && touchedLatestIdentity !== readIdentityId.value) {
      touchedLatestIdentity = readIdentityId.value
      seenBaselineAt.value = touchPostsListSeen({ identityId: readIdentityId.value })
    }
    reload()
  })

  watch(isDefaultLatestFeed, (v) => {
    if (v && touchedLatestIdentity !== readIdentityId.value) {
      touchedLatestIdentity = readIdentityId.value
      seenBaselineAt.value = touchPostsListSeen({ identityId: readIdentityId.value })
    }
  })

  const session = { authed, me, goLogin }
  const scope = {
    categoryId,
    order,
    tag,
    categories,
    categoryLabel,
    showClear,
    setOrder,
    setCategoryId,
    setTag,
    clearTag,
    clearQuery
  }
  const feed = {
    items,
    hasNext,
    loading,
    error,
    blockedHiddenCount,
    activityTime,
    activityUserId,
    activityUser,
    openUserProfile,
    openPost,
    loadMore,
    reload,
    togglePostLike
  }
  const unread = {
    lastSeenDividerRef,
    newHintDismissed,
    newSinceLastSeenCount,
    newDividerIndex,
    shouldShowLastSeenDivider,
    shouldShowNewHint,
    canJumpToLastSeen,
    scrollToLastSeenDivider,
    isUnread
  }
  const composer = {
    isPublishFocused,
    newTitle,
    newBlocks,
    newCategoryId,
    composerCategoryOptions,
    newTagDraft,
    newTags,
    newTagError,
    composerTagSuggestNames,
    creating,
    createError,
    closeComposer,
    commitNewTags,
    onTagDraftKeydown,
    removeNewTag,
    createPost
  }

  return { session, scope, feed, unread, composer }
}
