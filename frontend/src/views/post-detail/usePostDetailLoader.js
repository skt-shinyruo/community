import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useSocialPrefsStore } from '../../stores/socialPrefs'
import { useTaxonomyStore } from '../../stores/taxonomy'
import { usePostMetaCacheStore } from '../../stores/postMetaCache'
import { formatTime } from '../../utils/time'
import { createLatestRequestTracker } from '../../utils/latestRequest'
import { markPostRead } from '../../utils/readTracker'
import { scrollToAnchor } from '../../utils/scrollToAnchor'
import { normalizeOpaqueId, sameOpaqueId } from '../../utils/opaqueId'
import { showErrorToast, showToast } from '../../ui/toastService'
import { createWriteAttempt } from '../../api/writeAttempt'
import { getUserProfile } from '../../api/services/userService'
import { setLike, followUser, unfollowUser, getFollowStatus } from '../../api/services/socialService'
import { bookmarkPost, unbookmarkPost } from '../../api/services/bookmarkService'
import { blockUser, unblockUser } from '../../api/services/blockService'
import {
  getPostDetail,
  listComments as apiListComments,
  listReplies as apiListReplies,
  addComment as apiAddComment,
  updatePost as apiUpdatePost,
  deletePostByAuthor as apiDeletePostByAuthor,
  updateComment as apiUpdateComment,
  moderationTop,
  moderationWonderful,
  moderationDelete
} from '../../api/services/postService'
import {
  buildQuotePreview,
  composeReplyContent
} from '../postDetailState'
import { usePostDetailDrafts } from './usePostDetailDrafts'
import { isWithinEditWindow } from './usePostDetailInteractions'
import { isDangerConfirmation, resolvePostDetailConfirmation } from './usePostDetailModeration'

function normalizeCommentCursorPage(raw) {
  const page = raw && typeof raw === 'object' ? raw : {}
  return {
    items: Array.isArray(page.items) ? page.items : [],
    nextCursor: page.nextCursor == null ? '' : String(page.nextCursor)
  }
}

function collectThreadHydrationIds(items, { includeReplyToUserId = false } = {}) {
  const userIds = []
  const entityIds = []
  const seenUsers = new Set()
  const seenEntities = new Set()

  for (const item of Array.isArray(items) ? items : []) {
    const userId = normalizeOpaqueId(item?.userId)
    const entityId = normalizeOpaqueId(item?.id)
    const replyToUserId = includeReplyToUserId ? normalizeOpaqueId(item?.replyToUserId) : ''

    if (userId && !seenUsers.has(userId)) {
      seenUsers.add(userId)
      userIds.push(userId)
    }
    if (replyToUserId && !seenUsers.has(replyToUserId)) {
      seenUsers.add(replyToUserId)
      userIds.push(replyToUserId)
    }
    if (entityId && !seenEntities.has(entityId)) {
      seenEntities.add(entityId)
      entityIds.push(entityId)
    }
    if (userIds.length >= 200 && entityIds.length >= 200) break
  }

  return { userIds, entityIds }
}

function hydrateCommentItem(raw, { users = {}, counts = {}, statuses = {} } = {}) {
  const commentId = normalizeOpaqueId(raw?.id)
  const userId = normalizeOpaqueId(raw?.userId)
  const likeCount = counts?.[commentId]
  const liked = statuses?.[commentId]

  return {
    ...raw,
    user: users?.[userId] || null,
    likeCount: typeof likeCount === 'number' ? likeCount : 0,
    liked: !!liked,
    _likeLoading: false,
    _replying: false,
    _replyDraft: '',
    _replyError: '',
    _replySubmitting: false,
    _replyParentCommentId: '',
    _replyQuote: null,
    _repliesExpanded: false,
    _replies: [],
    _repliesPage: 0,
    _repliesSize: 5,
    _repliesNextCursor: '',
    _repliesCursorHistory: [''],
    _repliesLoading: false,
    _repliesError: ''
  }
}

function hydrateReplyItem(raw, { users = {}, counts = {}, statuses = {} } = {}) {
  const replyId = normalizeOpaqueId(raw?.id)
  const userId = normalizeOpaqueId(raw?.userId)
  const replyToUserId = normalizeOpaqueId(raw?.replyToUserId)
  const likeCount = counts?.[replyId]
  const liked = statuses?.[replyId]

  return {
    ...raw,
    user: users?.[userId] || null,
    replyToUserId,
    targetUserId: replyToUserId,
    targetUser: replyToUserId ? (users?.[replyToUserId] || null) : null,
    likeCount: typeof likeCount === 'number' ? likeCount : 0,
    liked: !!liked,
    _likeLoading: false
  }
}

export function usePostDetailLoader(emit) {

  const route = useRoute()
  const router = useRouter()
  const auth = useAuthStore()
  const prefs = useSocialPrefsStore()
  const authed = computed(() => !!auth.accessToken)
  const taxonomy = useTaxonomyStore()
  const postMetaCache = usePostMetaCacheStore()

  function categoryLabel(id) {
    const cid = normalizeOpaqueId(id)
    if (!cid) return ''
    const c = taxonomy.categoriesById.get(cid)
    return c?.name || `分类#${cid}`
  }

  const postId = computed(() => normalizeOpaqueId(route.params.postId))

  const post = ref(null)
  const postAuthor = ref(null)
  const loading = ref(false)
  const error = ref('')

  const actionLoading = ref(false)
  const reportOpen = ref(false)

  const meUserId = computed(() => normalizeOpaqueId(auth.userId))
  const followStatus = ref(null) // Boolean|null
  const postRequestTracker = createLatestRequestTracker()
  const commentsRequestTracker = createLatestRequestTracker()
  const followStatusRequestTracker = createLatestRequestTracker()

  function captureViewScope() {
    return {
      authGeneration: auth.tokenGeneration,
      postId: normalizeOpaqueId(postId.value)
    }
  }

  function isCurrentViewScope(scope) {
    return scope?.authGeneration === auth.tokenGeneration
      && sameOpaqueId(scope?.postId, postId.value)
  }

  const isBlockedAuthor = computed(() => {
    const uid = normalizeOpaqueId(post.value?.userId)
    if (!uid) return false
    return prefs.blockedSet.has(uid)
  })

  const canEditPost = computed(() => {
    if (!authed.value || !post.value) return false
    if (!sameOpaqueId(post.value.userId, meUserId.value)) return false
    if (Number(post.value.status || 0) === 2) return false
    return isWithinEditWindow(post.value.createTime, 24 * 3600 * 1000)
  })

  const newComment = ref('')
  const commenting = ref(false)
  const commentError = ref('')
  const commentAttempt = createWriteAttempt()
  const replyAttempts = new Map()
  const {
    safeStorageGet,
    safeStorageSet,
    commentDraftKey,
    replyDraftKey,
    setNewComment,
    setReplyDraft: persistReplyDraft
  } = usePostDetailDrafts(postId, newComment, meUserId)

  function replyAttemptKey(comment) {
    return [meUserId.value, postId.value, normalizeOpaqueId(comment?.id)].join(':')
  }

  function replyAttemptRecord(comment) {
    const key = replyAttemptKey(comment)
    let record = replyAttempts.get(key)
    if (!record) {
      record = { attempt: createWriteAttempt(), signature: '' }
      replyAttempts.set(key, record)
    }
    return record
  }

  function bindReplyAttempt(comment, content, parentCommentId) {
    const record = replyAttemptRecord(comment)
    const signature = JSON.stringify([
      normalizeOpaqueId(parentCommentId),
      String(content || '')
    ])
    if (record.signature && record.signature !== signature) {
      record.attempt.changeIntent()
    }
    record.signature = signature
    return record.attempt
  }

  function changeReplyIntent(comment) {
    const record = replyAttemptRecord(comment)
    record.attempt.changeIntent()
    record.signature = ''
  }

  function setReplyDraft(comment, value) {
    changeReplyIntent(comment)
    persistReplyDraft(comment, value)
  }

  function commentAnchorId(id) {
    return `c-${normalizeOpaqueId(id)}`
  }

  function replyAnchorId(id) {
    return `r-${normalizeOpaqueId(id)}`
  }

  function clearReplyQuote(c) {
    if (!c) return
    c._replyQuote = null
  }

  const followStatusText = computed(() => (followStatus.value === null ? '-' : followStatus.value ? '已关注' : '未关注'))

  const comments = ref([])
  const commentsPage = ref(0)
  const commentsSize = ref(10)
  const commentsNextCursor = ref('')
  const commentsCursorHistory = ref([''])
  const commentsLoading = ref(false)
  const commentsError = ref('')
  const commentsHasNext = computed(() => !!commentsNextCursor.value)

  const confirmOpen = ref(false)
  const confirmTitle = ref('')
  const confirmMessage = ref('')
  const confirmAction = ref('') // top|wonderful|delete|authorDelete

  const confirmVariant = computed(() => (isDangerConfirmation(confirmAction.value) ? 'danger' : 'primary'))
  const confirmOkText = computed(() => (isDangerConfirmation(confirmAction.value) ? '删除' : '确认'))

  function closeConfirm() {
    confirmOpen.value = false
    confirmTitle.value = ''
    confirmMessage.value = ''
    confirmAction.value = ''
  }

  function confirmModeration(type) {
    if (!post.value) return
    confirmAction.value = type
    confirmOpen.value = true
    const confirmation = resolvePostDetailConfirmation(type, post.value.id)
    confirmTitle.value = confirmation.title
    confirmMessage.value = confirmation.message
  }

  function confirmAuthorDelete() {
    confirmModeration('authorDelete')
  }

  async function runConfirm() {
    const type = confirmAction.value
    const scope = captureViewScope()
    const targetPostId = normalizeOpaqueId(post.value?.id)
    closeConfirm()
    if (!type || !targetPostId) return
    actionLoading.value = true
    try {
      if (type === 'top') {
        const r = await moderationTop(targetPostId)
        if (!isCurrentViewScope(scope)) return
        emit('trace', r?.traceId || '')
      } else if (type === 'wonderful') {
        const r = await moderationWonderful(targetPostId)
        if (!isCurrentViewScope(scope)) return
        emit('trace', r?.traceId || '')
      } else if (type === 'delete') {
        const r = await moderationDelete(targetPostId)
        if (!isCurrentViewScope(scope)) return
        emit('trace', r?.traceId || '')
      } else if (type === 'authorDelete') {
        const r = await apiDeletePostByAuthor(targetPostId)
        if (!isCurrentViewScope(scope)) return
        emit('trace', r?.traceId || '')
        router.push({ name: 'posts' })
        return
      }
      await reload()
    } catch (e) {
      if (!isCurrentViewScope(scope)) return
      error.value = e?.message || '管理操作失败'
    } finally {
      if (isCurrentViewScope(scope)) actionLoading.value = false
    }
  }

  async function loadPost() {
    const token = postRequestTracker.begin()
    error.value = ''
    loading.value = true
    try {
      const resp = await getPostDetail(postId.value)
      if (!postRequestTracker.isCurrent(token)) return
      post.value = resp?.data || null
      emit('trace', resp?.traceId || '')

      applyPostLikeOverlay()

      if (post.value?.userId) {
        postAuthor.value = await getUserProfile(post.value.userId).catch(() => null)
        if (!postRequestTracker.isCurrent(token)) return
      } else {
        postAuthor.value = null
      }
    } catch (e) {
      if (!postRequestTracker.isCurrent(token)) return
      error.value = e?.message || '加载失败'
    } finally {
      if (postRequestTracker.isCurrent(token)) {
        loading.value = false
      }
    }
  }

  function applyPostLikeOverlay() {
    if (!post.value) return
    const pid = normalizeOpaqueId(postId.value)
    if (!pid) return

    // 计数是全局读模型：短 TTL 覆盖用于减轻“写后刷新读旧投影”的感知不一致。
    const cachedCount = postMetaCache.getLikeCount(1, pid)
    if (typeof cachedCount === 'number') {
      post.value.likeCount = cachedCount
    }

    // liked 与登录态相关：未登录时强制为 false，避免跨账号/退出登录后误展示。
    if (!authed.value) {
      post.value.liked = false
      return
    }
    const cachedLiked = postMetaCache.getLikeStatus(1, pid)
    if (typeof cachedLiked === 'boolean') {
      post.value.liked = cachedLiked
    }
  }

  async function loadFollowStatus() {
    const expectedUserId = normalizeOpaqueId(post.value?.userId)
    if (!authed.value || !expectedUserId || sameOpaqueId(expectedUserId, meUserId.value)) {
      followStatus.value = null
      return
    }
    const token = followStatusRequestTracker.begin()
    const authGeneration = auth.tokenGeneration
    try {
      const resp = await getFollowStatus(3, expectedUserId, { force: true })
      if (!followStatusRequestTracker.isCurrent(token)) return
      if (auth.tokenGeneration !== authGeneration) return
      if (!sameOpaqueId(post.value?.userId, expectedUserId)) return
      emit('trace', resp?.traceId || '')
      followStatus.value = resp?.data ?? null
    } catch {
      if (!followStatusRequestTracker.isCurrent(token)) return
      followStatus.value = null
    }
  }

  async function togglePostLike() {
    if (!authed.value || !post.value) return
    const scope = captureViewScope()
    const targetPostId = normalizeOpaqueId(post.value.id)
    actionLoading.value = true
    try {
      const resp = await setLike({
        entityType: 1,
        entityId: targetPostId,
        liked: null
      })
      if (!isCurrentViewScope(scope)) return
      emit('trace', resp?.traceId || '')
      if (typeof resp?.data?.likeCount === 'number') {
        post.value.likeCount = resp.data.likeCount
        postMetaCache.setLikeCount(1, targetPostId, post.value.likeCount)
      }
      if (typeof resp?.data?.liked === 'boolean') {
        post.value.liked = resp.data.liked
        postMetaCache.setLikeStatus(1, targetPostId, post.value.liked)
      }
    } catch (e) {
      if (!isCurrentViewScope(scope)) return
      error.value = e?.message || '点赞操作失败'
    } finally {
      if (isCurrentViewScope(scope)) actionLoading.value = false
    }
  }

  async function follow(doFollow) {
    if (!authed.value || !post.value || !post.value.userId || sameOpaqueId(post.value.userId, meUserId.value)) return
    const scope = captureViewScope()
    const targetUserId = normalizeOpaqueId(post.value.userId)
    actionLoading.value = true
    try {
      if (doFollow) {
        const r = await followUser(3, targetUserId)
        if (!isCurrentViewScope(scope)) return
        emit('trace', r?.traceId || '')
        followStatus.value = true
      } else {
        const r = await unfollowUser(3, targetUserId)
        if (!isCurrentViewScope(scope)) return
        emit('trace', r?.traceId || '')
        followStatus.value = false
      }
    } catch (e) {
      if (!isCurrentViewScope(scope)) return
      error.value = e?.message || '关注操作失败'
    } finally {
      if (isCurrentViewScope(scope)) actionLoading.value = false
    }
  }

  function isBlockedUser(userId) {
    return prefs.blockedSet.has(normalizeOpaqueId(userId))
  }

  async function toggleBookmark() {
    if (!authed.value || !post.value) return
    const scope = captureViewScope()
    const targetPostId = normalizeOpaqueId(post.value.id)
    const wasBookmarked = !!post.value.bookmarked
    actionLoading.value = true
    try {
      if (wasBookmarked) {
        const r = await unbookmarkPost(targetPostId)
        if (!isCurrentViewScope(scope)) return
        emit('trace', r?.traceId || '')
        post.value.bookmarked = false
      } else {
        const r = await bookmarkPost(targetPostId)
        if (!isCurrentViewScope(scope)) return
        emit('trace', r?.traceId || '')
        post.value.bookmarked = true
      }
    } catch (e) {
      if (!isCurrentViewScope(scope)) return
      error.value = e?.message || '收藏操作失败'
    } finally {
      if (isCurrentViewScope(scope)) actionLoading.value = false
    }
  }

  function openReportPost() {
    if (!authed.value) return
    reportOpen.value = true
  }

  async function toggleBlockAuthor() {
    if (!authed.value || !post.value) return
    const uid = normalizeOpaqueId(post.value.userId)
    if (!uid || sameOpaqueId(uid, meUserId.value)) return
    const scope = captureViewScope()
    const wasBlocked = isBlockedAuthor.value
    actionLoading.value = true
    try {
      if (wasBlocked) {
        await unblockUser(uid)
        if (!isCurrentViewScope(scope)) return
        showToast({ type: 'success', text: '已解除屏蔽' })
      } else {
        await blockUser(uid)
        if (!isCurrentViewScope(scope)) return
        showToast({ type: 'success', text: '已屏蔽该用户' })
      }
      await prefs.ensureBlocked(true)
    } catch (e) {
      if (!isCurrentViewScope(scope)) return
      error.value = e?.message || '屏蔽操作失败'
    } finally {
      if (isCurrentViewScope(scope)) actionLoading.value = false
    }
  }

  function canEditComment(c) {
    if (!authed.value) return false
    const uid = normalizeOpaqueId(c?.userId)
    if (!uid || !sameOpaqueId(uid, meUserId.value)) return false
    if (Number(c?.status || 0) !== 0) return false
    return isWithinEditWindow(c?.createTime, 15 * 60 * 1000)
  }

  const editOpen = ref(false)
  const editMode = ref('post') // post | comment
  const editInitialTitle = ref('')
  const editInitialContent = ref('')
  const editInitialBlocks = ref([])
  const editCommentId = ref('')

  function closeEdit() {
    editOpen.value = false
    editMode.value = 'post'
    editInitialTitle.value = ''
    editInitialContent.value = ''
    editInitialBlocks.value = []
    editCommentId.value = ''
  }

  function openEditPost() {
    if (!post.value || !canEditPost.value) return
    editMode.value = 'post'
    editInitialTitle.value = String(post.value.title || '')
    editInitialContent.value = ''
    editInitialBlocks.value = Array.isArray(post.value.blocks) ? post.value.blocks : []
    editCommentId.value = ''
    editOpen.value = true
  }

  function openEditComment(c) {
    if (!c || !canEditComment(c)) return
    const cid = normalizeOpaqueId(c?.id)
    if (!cid) return
    editMode.value = 'comment'
    editInitialTitle.value = ''
    editInitialContent.value = String(c?.content || '')
    editInitialBlocks.value = []
    editCommentId.value = cid
    editOpen.value = true
  }

  async function submitEdit(payload) {
    if (!post.value) return
    const scope = captureViewScope()
    const targetPostId = normalizeOpaqueId(post.value.id)
    const targetMode = editMode.value
    const targetCommentId = normalizeOpaqueId(editCommentId.value)
    actionLoading.value = true
    try {
      if (targetMode === 'post') {
        const r = await apiUpdatePost(targetPostId, {
          title: String(payload?.title || '').trim(),
          blocks: Array.isArray(payload?.blocks) ? payload.blocks : [],
          categoryId: post.value.categoryId,
          tags: Array.isArray(post.value.tags) ? post.value.tags : []
        })
        if (!isCurrentViewScope(scope)) return
        emit('trace', r?.traceId || '')
        const q = String(payload?.title || '').trim()
        showToast({
          type: 'success',
          title: '已保存',
          text: '帖子已更新。搜索结果更新为最终一致，可能延迟数秒到数十秒。',
          duration: 6000,
          actionText: '去搜索',
          onAction: () => router.push({ name: 'search', query: q ? { q } : {} })
        })
        closeEdit()
        await loadPost()
      } else {
        const r = await apiUpdateComment(targetPostId, targetCommentId, { content: String(payload?.content || '').trim() })
        if (!isCurrentViewScope(scope)) return
        emit('trace', r?.traceId || '')
        showToast({ type: 'success', text: '已保存' })
        closeEdit()
        await loadComments()
      }
    } catch (e) {
      if (!isCurrentViewScope(scope)) return
      showErrorToast(e, { type: 'error', text: e?.message || '保存失败' })
    } finally {
      if (isCurrentViewScope(scope)) actionLoading.value = false
    }
  }

  async function maybeScrollFromRoute() {
    // 1) 优先使用 hash（例如：#c-123 / #r-456）
    const rawHash = String(route.hash || '').trim()
    const anchor = rawHash.startsWith('#') ? rawHash.slice(1) : ''
    if (anchor) {
      await nextTick()
      if (scrollToAnchor(anchor)) return
    }

    // 2) query 模式（便于“回复定位”：?commentId=1&replyId=2）
    const commentId = normalizeOpaqueId(route.query?.commentId)
    const replyId = normalizeOpaqueId(route.query?.replyId)
    if (!commentId) return

    await nextTick()
    scrollToAnchor(commentAnchorId(commentId))

    if (!replyId) return

    const c = comments.value.find((x) => sameOpaqueId(x?.id, commentId))
    if (!c) return

    if (!c._repliesExpanded) c._repliesExpanded = true
    if (Array.isArray(c._replies) && c._replies.length === 0) {
      await loadReplies(c, 0, { reset: true })
    }

    await nextTick()
    scrollToAnchor(replyAnchorId(replyId))
  }

  function resetCommentsCursorPaging() {
    commentsPage.value = 0
    commentsNextCursor.value = ''
    commentsCursorHistory.value = ['']
  }

  function currentCommentsCursor(targetPage = commentsPage.value) {
    return String(commentsCursorHistory.value[targetPage] || '')
  }

  function currentRepliesCursor(c, targetPage = c?._repliesPage) {
    if (!Array.isArray(c?._repliesCursorHistory)) return ''
    return String(c._repliesCursorHistory[targetPage] || '')
  }

  async function loadComments(targetPage = commentsPage.value, { reset = false } = {}) {
    const token = commentsRequestTracker.begin()
    const requestedPage = Math.max(0, Number(targetPage || 0))
    commentsError.value = ''
    commentsLoading.value = true
    try {
      const cursor = reset ? '' : currentCommentsCursor(requestedPage)
      const resp = await apiListComments(postId.value, { cursor, size: commentsSize.value })
      if (!commentsRequestTracker.isCurrent(token)) return
      emit('trace', resp?.traceId || '')
      const page = normalizeCommentCursorPage(resp?.data)
      const raw = page.items
      if (requestedPage > commentsPage.value && raw.length === 0) {
        commentsCursorHistory.value = commentsCursorHistory.value.slice(0, commentsPage.value + 1)
        commentsNextCursor.value = ''
        return
      }
      const { userIds, entityIds: commentIds } = collectThreadHydrationIds(raw)

      let users = {}
      let counts = {}
      let statuses = {}
      const hydrationTasks = [
        postMetaCache.ensureUserSummaries(userIds),
        postMetaCache.ensureLikeCounts(2, commentIds)
      ]
      if (authed.value) {
        hydrationTasks.push(postMetaCache.ensureLikeStatuses(2, commentIds))
      }
      const [usersResult, countsResult, statusesResult] = await Promise.allSettled(hydrationTasks)
      if (!commentsRequestTracker.isCurrent(token)) return
      if (usersResult?.status === 'fulfilled') {
        users = usersResult.value || {}
      }
      if (countsResult?.status === 'fulfilled') {
        counts = countsResult.value || {}
      }
      if (statusesResult?.status === 'fulfilled') {
        statuses = statusesResult.value || {}
      }

      const nextCursor = page.nextCursor
      const history = (reset ? [''] : commentsCursorHistory.value).slice(0, requestedPage + 1)
      if (nextCursor) {
        history[requestedPage + 1] = nextCursor
      }
      commentsCursorHistory.value = history
      commentsNextCursor.value = nextCursor
      commentsPage.value = requestedPage
      comments.value = raw.map((c) => hydrateCommentItem(c, { users, counts, statuses }))
      await maybeScrollFromRoute()
    } catch (e) {
      if (!commentsRequestTracker.isCurrent(token)) return
      commentsError.value = e?.message || '加载评论失败'
    } finally {
      if (commentsRequestTracker.isCurrent(token)) {
        commentsLoading.value = false
      }
    }
  }

  function repliesHasNext(c) {
    return !!String(c?._repliesNextCursor || '')
  }

  async function loadReplies(c, targetPage = c?._repliesPage, { reset = false } = {}) {
    if (!c) return
    const requestedPage = Math.max(0, Number(targetPage || 0))
    c._repliesError = ''
    c._repliesLoading = true
    try {
      const cursor = reset ? '' : currentRepliesCursor(c, requestedPage)
      const resp = await apiListReplies(postId.value, c.id, { cursor, size: c._repliesSize })
      emit('trace', resp?.traceId || '')
      const page = normalizeCommentCursorPage(resp?.data)
      const raw = page.items
      if (requestedPage > c._repliesPage && raw.length === 0) {
        c._repliesCursorHistory = c._repliesCursorHistory.slice(0, c._repliesPage + 1)
        c._repliesNextCursor = ''
        return
      }
      const { userIds, entityIds: replyIds } = collectThreadHydrationIds(raw, { includeReplyToUserId: true })

      let users = {}
      let counts = {}
      let statuses = {}
      const hydrationTasks = [
        postMetaCache.ensureUserSummaries(userIds),
        postMetaCache.ensureLikeCounts(2, replyIds)
      ]
      if (authed.value) {
        hydrationTasks.push(postMetaCache.ensureLikeStatuses(2, replyIds))
      }
      const [usersResult, countsResult, statusesResult] = await Promise.allSettled(hydrationTasks)
      if (usersResult?.status === 'fulfilled') {
        users = usersResult.value || {}
      }
      if (countsResult?.status === 'fulfilled') {
        counts = countsResult.value || {}
      }
      if (statusesResult?.status === 'fulfilled') {
        statuses = statusesResult.value || {}
      }

      const nextCursor = page.nextCursor
      const currentHistory = reset || !Array.isArray(c._repliesCursorHistory) ? [''] : c._repliesCursorHistory
      const history = currentHistory.slice(0, requestedPage + 1)
      if (nextCursor) {
        history[requestedPage + 1] = nextCursor
      }
      c._repliesCursorHistory = history
      c._repliesNextCursor = nextCursor
      c._repliesPage = requestedPage
      c._replies = raw.map((r) => hydrateReplyItem(r, { users, counts, statuses }))
    } catch (e) {
      c._repliesError = e?.message || '加载回复失败'
    } finally {
      c._repliesLoading = false
    }
  }

  async function toggleReplies(c) {
    if (!c) return
    c._repliesExpanded = !c._repliesExpanded
    if (c._repliesExpanded && c._replies.length === 0) {
      await loadReplies(c, 0, { reset: true })
    }
  }

  async function reloadReplies(c) {
    if (!c) return
    await loadReplies(c, 0, { reset: true })
  }

  async function nextRepliesPage(c) {
    if (!c || c._repliesLoading || !repliesHasNext(c)) return
    await loadReplies(c, c._repliesPage + 1)
  }

  async function prevRepliesPage(c) {
    if (!c || c._repliesLoading) return
    await loadReplies(c, Math.max(0, c._repliesPage - 1))
  }

  function startReply(c, reply) {
    if (!authed.value || !c) return
    c._replying = true
    c._replyError = ''

    // 恢复草稿（按 postId + commentId 隔离）。
    c._replyDraft = safeStorageGet(replyDraftKey(c.id))
    c._replyParentCommentId = normalizeOpaqueId(reply?.id || c.id)

    // 引用内容：回复回复时引用 reply；回复评论时引用 comment。
    if (reply && reply.userId) {
      c._replyQuote = {
        sourceType: 'reply',
        sourceId: normalizeOpaqueId(reply.id),
        userId: normalizeOpaqueId(reply.userId),
        username: String(reply.user?.username || ''),
        raw: String(reply.content || ''),
        preview: buildQuotePreview(reply.content)
      }
    } else {
      c._replyQuote = {
        sourceType: 'comment',
        sourceId: normalizeOpaqueId(c.id),
        userId: normalizeOpaqueId(c.userId),
        username: String(c.user?.username || ''),
        raw: String(c.content || ''),
        preview: buildQuotePreview(c.content)
      }
    }
  }

  function cancelReply(c) {
    if (!c) return
    const record = replyAttemptRecord(c)
    record.attempt.cancel()
    record.signature = ''
    c._replying = false
    c._replyError = ''
    c._replySubmitting = false
    c._replyParentCommentId = ''
    c._replyQuote = null
  }

  async function submitReply(c) {
    if (!authed.value || !c) return
    c._replyError = ''
    if (!String(c._replyDraft || '').trim()) {
      c._replyError = '回复内容不能为空'
      return
    }
    const scope = captureViewScope()
    const targetCommentId = normalizeOpaqueId(c.id)
    const content = composeReplyContent(c._replyDraft, c._replyQuote)
    const parentCommentId = normalizeOpaqueId(c._replyParentCommentId)
    c._replySubmitting = true
    try {
      const record = replyAttemptRecord(c)
      const attempt = bindReplyAttempt(c, content, parentCommentId)
      const resp = await apiAddComment(scope.postId, {
        content,
        parentCommentId
      }, { writeAttempt: attempt })
      if (!isCurrentViewScope(scope)) return
      emit('trace', resp?.traceId || '')
      c._replyDraft = ''
      attempt.succeed()
      record.signature = ''
      safeStorageSet(replyDraftKey(c.id), '')
      c._replying = false
      c._replyParentCommentId = ''
      c._replyQuote = null
      if (post.value) {
        post.value.commentCount = Number(post.value.commentCount || 0) + 1
      }
      if (!c._repliesExpanded) {
        c._repliesExpanded = true
      }
      await loadReplies(c, 0, { reset: true })
    } catch (e) {
      if (!isCurrentViewScope(scope)) return
      c._replyError = e?.message || '回复失败'
    } finally {
      if (isCurrentViewScope(scope)) c._replySubmitting = false
    }
  }

  async function toggleCommentLike(c) {
    if (!authed.value || !c) return
    const scope = captureViewScope()
    const targetCommentId = normalizeOpaqueId(c.id)
    c._likeLoading = true
    try {
      const resp = await setLike({
        entityType: 2,
        entityId: targetCommentId,
        liked: null
      })
      if (!isCurrentViewScope(scope) || !comments.value.includes(c)) return
      emit('trace', resp?.traceId || '')
      if (typeof resp?.data?.likeCount === 'number') {
        c.likeCount = resp.data.likeCount
        postMetaCache.setLikeCount(2, targetCommentId, c.likeCount)
      }
      if (typeof resp?.data?.liked === 'boolean') {
        c.liked = resp.data.liked
        postMetaCache.setLikeStatus(2, targetCommentId, c.liked)
      }
    } catch (e) {
      if (!isCurrentViewScope(scope) || !comments.value.includes(c)) return
      commentsError.value = e?.message || '点赞失败'
    } finally {
      if (isCurrentViewScope(scope) && comments.value.includes(c)) c._likeLoading = false
    }
  }

  async function toggleReplyLike(c, r) {
    if (!authed.value || !c || !r) return
    const scope = captureViewScope()
    const targetReplyId = normalizeOpaqueId(r.id)
    r._likeLoading = true
    try {
      const resp = await setLike({
        entityType: 2,
        entityId: targetReplyId,
        liked: null
      })
      if (!isCurrentViewScope(scope) || !comments.value.includes(c) || !c._replies.includes(r)) return
      emit('trace', resp?.traceId || '')
      if (typeof resp?.data?.likeCount === 'number') {
        r.likeCount = resp.data.likeCount
        postMetaCache.setLikeCount(2, targetReplyId, r.likeCount)
      }
      if (typeof resp?.data?.liked === 'boolean') {
        r.liked = resp.data.liked
        postMetaCache.setLikeStatus(2, targetReplyId, r.liked)
      }
    } catch (e) {
      if (!isCurrentViewScope(scope) || !comments.value.includes(c) || !c._replies.includes(r)) return
      c._repliesError = e?.message || '点赞失败'
    } finally {
      if (isCurrentViewScope(scope) && comments.value.includes(c) && c._replies.includes(r)) r._likeLoading = false
    }
  }

  async function addComment() {
    commentError.value = ''
    if (!String(newComment.value || '').trim()) {
      commentError.value = '评论不能为空'
      return
    }
    const scope = captureViewScope()
    const content = String(newComment.value)
    commenting.value = true
    try {
      const resp = await apiAddComment(scope.postId, { content }, { writeAttempt: commentAttempt })
      if (!isCurrentViewScope(scope)) return
      emit('trace', resp?.traceId || '')
      setNewComment('')
      commentAttempt.succeed()
      await loadComments(0, { reset: true })
      await loadPost()
    } catch (e) {
      if (!isCurrentViewScope(scope)) return
      commentError.value = e?.message || '评论失败'
    } finally {
      if (isCurrentViewScope(scope)) commenting.value = false
    }
  }

  async function nextCommentsPage() {
    if (!commentsHasNext.value || commentsLoading.value) return
    await loadComments(commentsPage.value + 1)
  }

  async function prevCommentsPage() {
    if (commentsLoading.value) return
    await loadComments(Math.max(0, commentsPage.value - 1))
  }

  async function reloadComments() {
    await loadComments(0, { reset: true })
  }

  async function reload() {
    if (authed.value) {
      try {
        await prefs.ensureBlocked()
      } catch {
        // ignore：拉黑列表失败不阻塞页面加载
      }
    } else {
      prefs.clear()
    }
    await loadPost()
    await loadComments()
    await loadFollowStatus()
  }

  watch(
    () => [route.hash, route.query?.commentId, route.query?.replyId],
    () => {
      if (commentsLoading.value) return
      maybeScrollFromRoute()
    }
  )

  watch(
    () => auth.tokenGeneration,
    () => {
      commentAttempt.cancel()
      postRequestTracker.invalidate()
      commentsRequestTracker.invalidate()
      followStatusRequestTracker.invalidate()
      postMetaCache.clearLikeStatuses()
      actionLoading.value = false
      commenting.value = false
      reportOpen.value = false
      closeEdit()
      closeConfirm()
      followStatus.value = null
      if (post.value) {
        post.value.liked = false
        post.value.bookmarked = false
      }
      for (const comment of comments.value) {
        comment.liked = false
        comment._replying = false
        comment._replyDraft = ''
        comment._replyQuote = null
        comment._replySubmitting = false
        comment._likeLoading = false
        for (const reply of Array.isArray(comment?._replies) ? comment._replies : []) {
          reply.liked = false
          reply._likeLoading = false
        }
      }
      newComment.value = safeStorageGet(commentDraftKey())
      reload()
    }
  )

  watch(
    () => route.params.postId,
    () => {
      commentAttempt.cancel()
      postRequestTracker.invalidate()
      commentsRequestTracker.invalidate()
      followStatusRequestTracker.invalidate()
      post.value = null
      postAuthor.value = null
      comments.value = []
      resetCommentsCursorPaging()
      followStatus.value = null
      reportOpen.value = false
      actionLoading.value = false
      commenting.value = false
      closeEdit()
      closeConfirm()
      // 恢复当前帖子草稿（进入新帖子时才触发）
      newComment.value = safeStorageGet(commentDraftKey())
      if (authed.value) markPostRead(postId.value, { identityId: meUserId.value })
      reload()
    }
  )

  watch(newComment, () => commentAttempt.changeIntent())

  onBeforeUnmount(() => {
    commentAttempt.cancel()
    postRequestTracker.invalidate()
    commentsRequestTracker.invalidate()
    followStatusRequestTracker.invalidate()
  })

  onMounted(() => {
    taxonomy.ensureCategories()
    newComment.value = safeStorageGet(commentDraftKey())
    if (authed.value) markPostRead(postId.value, { identityId: meUserId.value })
    reload()
  })

    return {
      auth,
      authed,
      categoryLabel,
      postId,
      post,
      postAuthor,
      loading,
      error,
      actionLoading,
      reportOpen,
      meUserId,
      followStatus,
      isBlockedAuthor,
      canEditPost,
      newComment,
      commenting,
      commentError,
      setNewComment,
      setReplyDraft,
      commentAnchorId,
      replyAnchorId,
      clearReplyQuote,
      followStatusText,
      comments,
      commentsPage,
      commentsHasNext,
      commentsLoading,
      commentsError,
      confirmOpen,
      confirmTitle,
      confirmMessage,
      confirmVariant,
      confirmOkText,
      closeConfirm,
      confirmModeration,
      confirmAuthorDelete,
      runConfirm,
      loadPost,
      loadComments,
      reloadComments,
      nextCommentsPage,
      prevCommentsPage,
      reload,
      togglePostLike,
      follow,
      isBlockedUser,
      toggleBookmark,
      openReportPost,
      toggleBlockAuthor,
      canEditComment,
      editOpen,
      editMode,
      editInitialTitle,
      editInitialContent,
      editInitialBlocks,
      closeEdit,
      openEditPost,
      openEditComment,
      submitEdit,
      maybeScrollFromRoute,
      repliesHasNext,
      loadReplies,
      toggleReplies,
      reloadReplies,
      nextRepliesPage,
      prevRepliesPage,
      startReply,
      cancelReply,
      submitReply,
      toggleCommentLike,
      toggleReplyLike,
      addComment,
      sameOpaqueId,
      formatTime
    }

}
