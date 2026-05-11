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
import { showToast } from '../../ui/toastService'
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
  collectCommentHydrationIds,
  collectReplyHydrationIds,
  composeReplyContent,
  hydratePostComment,
  hydratePostReply
} from '../postDetailState'
import { usePostDetailDrafts } from './usePostDetailDrafts'
import { isWithinEditWindow } from './usePostDetailInteractions'
import { isDangerConfirmation, resolvePostDetailConfirmation } from './usePostDetailModeration'

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
  const {
    safeStorageGet,
    safeStorageSet,
    commentDraftKey,
    replyDraftKey,
    setNewComment,
    setReplyDraft
  } = usePostDetailDrafts(postId, newComment)

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
  const commentsLoading = ref(false)
  const commentsError = ref('')
  const commentsHasNext = computed(() => comments.value.length === Number(commentsSize.value || 10))

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
    closeConfirm()
    if (!type || !post.value) return
    actionLoading.value = true
    try {
      if (type === 'top') {
        const r = await moderationTop(post.value.id)
        emit('trace', r?.traceId || '')
      } else if (type === 'wonderful') {
        const r = await moderationWonderful(post.value.id)
        emit('trace', r?.traceId || '')
      } else if (type === 'delete') {
        const r = await moderationDelete(post.value.id)
        emit('trace', r?.traceId || '')
      } else if (type === 'authorDelete') {
        const r = await apiDeletePostByAuthor(post.value.id)
        emit('trace', r?.traceId || '')
        router.push({ name: 'posts' })
        return
      }
      await reload()
    } catch (e) {
      error.value = e?.message || '管理操作失败'
    } finally {
      actionLoading.value = false
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
    try {
      const resp = await getFollowStatus(3, expectedUserId, { force: true })
      if (!followStatusRequestTracker.isCurrent(token)) return
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
    actionLoading.value = true
    try {
      const resp = await setLike({
        entityType: 1,
        entityId: postId.value,
        entityUserId: post.value.userId,
        postId: postId.value,
        liked: null
      })
      emit('trace', resp?.traceId || '')
      if (typeof resp?.data?.likeCount === 'number') {
        post.value.likeCount = resp.data.likeCount
        postMetaCache.setLikeCount(1, postId.value, post.value.likeCount)
      }
      if (typeof resp?.data?.liked === 'boolean') {
        post.value.liked = resp.data.liked
        postMetaCache.setLikeStatus(1, postId.value, post.value.liked)
      }
    } catch (e) {
      error.value = e?.message || '点赞操作失败'
    } finally {
      actionLoading.value = false
    }
  }

  async function follow(doFollow) {
    if (!authed.value || !post.value || !post.value.userId || sameOpaqueId(post.value.userId, meUserId.value)) return
    actionLoading.value = true
    try {
      if (doFollow) {
        const r = await followUser(3, post.value.userId, post.value.userId)
        emit('trace', r?.traceId || '')
        followStatus.value = true
      } else {
        const r = await unfollowUser(3, post.value.userId)
        emit('trace', r?.traceId || '')
        followStatus.value = false
      }
    } catch (e) {
      error.value = e?.message || '关注操作失败'
    } finally {
      actionLoading.value = false
    }
  }

  function isBlockedUser(userId) {
    return prefs.blockedSet.has(normalizeOpaqueId(userId))
  }

  async function toggleBookmark() {
    if (!authed.value || !post.value) return
    actionLoading.value = true
    try {
      if (post.value.bookmarked) {
        const r = await unbookmarkPost(post.value.id)
        emit('trace', r?.traceId || '')
        post.value.bookmarked = false
      } else {
        const r = await bookmarkPost(post.value.id)
        emit('trace', r?.traceId || '')
        post.value.bookmarked = true
      }
    } catch (e) {
      error.value = e?.message || '收藏操作失败'
    } finally {
      actionLoading.value = false
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
    actionLoading.value = true
    try {
      if (isBlockedAuthor.value) {
        await unblockUser(uid)
        showToast({ type: 'success', text: '已解除屏蔽' })
      } else {
        await blockUser(uid)
        showToast({ type: 'success', text: '已屏蔽该用户' })
      }
      await prefs.ensureBlocked(true)
    } catch (e) {
      error.value = e?.message || '屏蔽操作失败'
    } finally {
      actionLoading.value = false
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
    actionLoading.value = true
    try {
      if (editMode.value === 'post') {
        const r = await apiUpdatePost(post.value.id, {
          title: String(payload?.title || '').trim(),
          blocks: Array.isArray(payload?.blocks) ? payload.blocks : [],
          categoryId: post.value.categoryId,
          tags: Array.isArray(post.value.tags) ? post.value.tags : []
        })
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
        const cid = editCommentId.value
        const r = await apiUpdateComment(post.value.id, cid, { content: String(payload?.content || '').trim() })
        emit('trace', r?.traceId || '')
        showToast({ type: 'success', text: '已保存' })
        closeEdit()
        await loadComments()
      }
    } catch (e) {
      showToast({ type: 'error', text: e?.message || '保存失败' })
    } finally {
      actionLoading.value = false
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
      c._repliesPage = 0
      await loadReplies(c)
    }

    await nextTick()
    scrollToAnchor(replyAnchorId(replyId))
  }

  async function loadComments() {
    const token = commentsRequestTracker.begin()
    commentsError.value = ''
    commentsLoading.value = true
    try {
      const resp = await apiListComments(postId.value, { page: commentsPage.value, size: commentsSize.value })
      if (!commentsRequestTracker.isCurrent(token)) return
      emit('trace', resp?.traceId || '')
      const raw = Array.isArray(resp?.data) ? resp.data : []
      const { userIds, entityIds: commentIds } = collectCommentHydrationIds(raw)

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

      comments.value = raw.map((c) => hydratePostComment(c, { users, counts, statuses }))
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
    return Array.isArray(c?._replies) && c._replies.length === Number(c?._repliesSize || 5)
  }

  async function loadReplies(c) {
    if (!c) return
    c._repliesError = ''
    c._repliesLoading = true
    try {
      const resp = await apiListReplies(postId.value, c.id, { page: c._repliesPage, size: c._repliesSize })
      emit('trace', resp?.traceId || '')
      const raw = Array.isArray(resp?.data) ? resp.data : []
      const { userIds, entityIds: replyIds } = collectReplyHydrationIds(raw)

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

      c._replies = raw.map((r) => hydratePostReply(r, { users, counts, statuses }))
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
      c._repliesPage = 0
      await loadReplies(c)
    }
  }

  async function reloadReplies(c) {
    if (!c) return
    c._repliesPage = 0
    await loadReplies(c)
  }

  async function nextRepliesPage(c) {
    if (!c || c._repliesLoading || !repliesHasNext(c)) return
    c._repliesPage += 1
    await loadReplies(c)
  }

  async function prevRepliesPage(c) {
    if (!c || c._repliesLoading) return
    c._repliesPage = Math.max(0, c._repliesPage - 1)
    await loadReplies(c)
  }

  function startReply(c, reply) {
    if (!authed.value || !c) return
    c._replying = true
    c._replyError = ''

    // 恢复草稿（按 postId + commentId 隔离）。
    c._replyDraft = safeStorageGet(replyDraftKey(c.id))

    // 引用内容：回复回复时引用 reply；回复评论时引用 comment。
    if (reply && reply.userId) {
      c._replyTargetId = normalizeOpaqueId(reply.userId)
      c._replyTargetUser = reply.user || null
      c._replyQuote = {
        sourceType: 'reply',
        sourceId: normalizeOpaqueId(reply.id),
        userId: normalizeOpaqueId(reply.userId),
        username: String(reply.user?.username || ''),
        raw: String(reply.content || ''),
        preview: buildQuotePreview(reply.content)
      }
    } else {
      c._replyTargetId = normalizeOpaqueId(c.userId)
      c._replyTargetUser = c.user || null
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
    c._replying = false
    c._replyError = ''
    c._replySubmitting = false
    c._replyTargetId = ''
    c._replyTargetUser = null
    c._replyQuote = null
  }

  async function submitReply(c) {
    if (!authed.value || !c) return
    c._replyError = ''
    if (!String(c._replyDraft || '').trim()) {
      c._replyError = '回复内容不能为空'
      return
    }
    c._replySubmitting = true
    try {
      const resp = await apiAddComment(postId.value, {
        content: composeReplyContent(c._replyDraft, c._replyQuote),
        entityType: 2,
        entityId: c.id,
        targetId: c._replyTargetId || undefined
      })
      emit('trace', resp?.traceId || '')
      c._replyDraft = ''
      safeStorageSet(replyDraftKey(c.id), '')
      c._replying = false
      c._replyQuote = null
      if (post.value) {
        post.value.commentCount = Number(post.value.commentCount || 0) + 1
      }
      if (!c._repliesExpanded) {
        c._repliesExpanded = true
      }
      c._repliesPage = 0
      await loadReplies(c)
    } catch (e) {
      c._replyError = e?.message || '回复失败'
    } finally {
      c._replySubmitting = false
    }
  }

  async function toggleCommentLike(c) {
    if (!authed.value || !c) return
    c._likeLoading = true
    try {
      const resp = await setLike({
        entityType: 2,
        entityId: c.id,
        entityUserId: c.userId,
        postId: postId.value,
        liked: null
      })
      emit('trace', resp?.traceId || '')
      if (typeof resp?.data?.likeCount === 'number') {
        c.likeCount = resp.data.likeCount
        postMetaCache.setLikeCount(2, c.id, c.likeCount)
      }
      if (typeof resp?.data?.liked === 'boolean') {
        c.liked = resp.data.liked
        postMetaCache.setLikeStatus(2, c.id, c.liked)
      }
    } catch (e) {
      commentsError.value = e?.message || '点赞失败'
    } finally {
      c._likeLoading = false
    }
  }

  async function toggleReplyLike(c, r) {
    if (!authed.value || !c || !r) return
    r._likeLoading = true
    try {
      const resp = await setLike({
        entityType: 2,
        entityId: r.id,
        entityUserId: r.userId,
        postId: postId.value,
        liked: null
      })
      emit('trace', resp?.traceId || '')
      if (typeof resp?.data?.likeCount === 'number') {
        r.likeCount = resp.data.likeCount
        postMetaCache.setLikeCount(2, r.id, r.likeCount)
      }
      if (typeof resp?.data?.liked === 'boolean') {
        r.liked = resp.data.liked
        postMetaCache.setLikeStatus(2, r.id, r.liked)
      }
    } catch (e) {
      c._repliesError = e?.message || '点赞失败'
    } finally {
      r._likeLoading = false
    }
  }

  async function addComment() {
    commentError.value = ''
    if (!String(newComment.value || '').trim()) {
      commentError.value = '评论不能为空'
      return
    }
    commenting.value = true
    try {
      const resp = await apiAddComment(postId.value, { content: newComment.value })
      emit('trace', resp?.traceId || '')
      setNewComment('')
      commentsPage.value = 0
      await loadComments()
      await loadPost()
    } catch (e) {
      commentError.value = e?.message || '评论失败'
    } finally {
      commenting.value = false
    }
  }

  async function nextCommentsPage() {
    if (!commentsHasNext.value || commentsLoading.value) return
    commentsPage.value += 1
    await loadComments()
  }

  async function prevCommentsPage() {
    if (commentsLoading.value) return
    commentsPage.value = Math.max(0, commentsPage.value - 1)
    await loadComments()
  }

  async function reloadComments() {
    commentsPage.value = 0
    await loadComments()
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
    () => auth.accessToken,
    () => {
      // 点赞状态与登录态强相关：切换账号/退出登录时清理覆盖，避免误展示。
      postMetaCache.clearLikeStatuses()
      applyPostLikeOverlay()
    }
  )

  watch(
    () => route.params.postId,
    () => {
      postRequestTracker.invalidate()
      commentsRequestTracker.invalidate()
      followStatusRequestTracker.invalidate()
      post.value = null
      postAuthor.value = null
      comments.value = []
      commentsPage.value = 0
      followStatus.value = null
      reportOpen.value = false
      closeEdit()
      closeConfirm()
      // 恢复当前帖子草稿（进入新帖子时才触发）
      newComment.value = safeStorageGet(commentDraftKey())
      if (authed.value) markPostRead(postId.value)
      reload()
    }
  )

  onBeforeUnmount(() => {
    postRequestTracker.invalidate()
    commentsRequestTracker.invalidate()
    followStatusRequestTracker.invalidate()
  })

  onMounted(() => {
    taxonomy.ensureCategories()
    newComment.value = safeStorageGet(commentDraftKey())
    if (authed.value) markPostRead(postId.value)
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
