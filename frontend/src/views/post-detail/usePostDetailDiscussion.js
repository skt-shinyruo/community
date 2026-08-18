// @ts-check
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useSocialPrefsStore } from '../../stores/socialPrefs'
import { usePostMetaCacheStore } from '../../stores/postMetaCache'
import { createLatestRequestTracker } from '../../utils/latestRequest'
import { normalizeOpaqueId, sameOpaqueId } from '../../utils/opaqueId'
import { scrollToAnchor } from '../../utils/scrollToAnchor'
import { createWriteAttempt } from '../../api/writeAttempt'
import { setLike } from '../../api/services/socialService'
import {
  addComment as apiAddComment,
  listComments as apiListComments,
  listReplies as apiListReplies
} from '../../api/services/postService'
import { buildQuotePreview, composeReplyContent } from '../postDetailState'
import { usePostDetailDrafts } from './usePostDetailDrafts'

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

export function usePostDetailDiscussion({
  authed,
  postId,
  meUserId,
  post,
  captureViewScope,
  isCurrentViewScope,
  refreshPost
}) {
  const route = useRoute()
  const prefs = useSocialPrefsStore()
  const postMetaCache = usePostMetaCacheStore()
  const commentsRequestTracker = createLatestRequestTracker()

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

  const comments = ref(/** @type {Array<Record<string, any>>} */ ([]))
  const commentsPage = ref(0)
  const commentsSize = 10
  const commentsNextCursor = ref('')
  const commentsCursorHistory = ref([''])
  const commentsLoading = ref(false)
  const commentsError = ref('')
  const commentsHasNext = computed(() => !!commentsNextCursor.value)

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

  function cancelReplyAttempts() {
    for (const record of replyAttempts.values()) record.attempt.cancel()
    replyAttempts.clear()
  }

  function setReplyDraft(comment, value) {
    const record = replyAttemptRecord(comment)
    record.attempt.changeIntent()
    record.signature = ''
    persistReplyDraft(comment, value)
  }

  function commentAnchorId(id) {
    return `c-${normalizeOpaqueId(id)}`
  }

  function replyAnchorId(id) {
    return `r-${normalizeOpaqueId(id)}`
  }

  function clearReplyQuote(comment) {
    if (comment) comment._replyQuote = null
  }

  function isBlockedUser(userId) {
    return prefs.blockedSet.has(normalizeOpaqueId(userId))
  }

  function resetCommentsCursorPaging() {
    commentsPage.value = 0
    commentsNextCursor.value = ''
    commentsCursorHistory.value = ['']
  }

  function currentCommentsCursor(targetPage = commentsPage.value) {
    return String(commentsCursorHistory.value[targetPage] || '')
  }

  function currentRepliesCursor(comment, targetPage = comment?._repliesPage) {
    if (!Array.isArray(comment?._repliesCursorHistory)) return ''
    return String(comment._repliesCursorHistory[targetPage] || '')
  }

  async function maybeScrollFromRoute() {
    const rawHash = String(route.hash || '').trim()
    const anchor = rawHash.startsWith('#') ? rawHash.slice(1) : ''
    if (anchor) {
      await nextTick()
      if (scrollToAnchor(anchor)) return
    }

    const commentId = normalizeOpaqueId(route.query?.commentId)
    const replyId = normalizeOpaqueId(route.query?.replyId)
    if (!commentId) return

    await nextTick()
    scrollToAnchor(commentAnchorId(commentId))

    if (!replyId) return
    const comment = comments.value.find((item) => sameOpaqueId(item?.id, commentId))
    if (!comment) return

    if (!comment._repliesExpanded) comment._repliesExpanded = true
    if (Array.isArray(comment._replies) && comment._replies.length === 0) {
      await loadReplies(comment, 0, { reset: true })
    }

    await nextTick()
    scrollToAnchor(replyAnchorId(replyId))
  }

  async function loadComments(targetPage = commentsPage.value, { reset = false } = {}) {
    const token = commentsRequestTracker.begin()
    const requestedPage = Math.max(0, Number(targetPage || 0))
    commentsError.value = ''
    commentsLoading.value = true
    try {
      const cursor = reset ? '' : currentCommentsCursor(requestedPage)
      const resp = await apiListComments(postId.value, { cursor, size: commentsSize })
      if (!commentsRequestTracker.isCurrent(token)) return
      const page = normalizeCommentCursorPage(resp?.data)
      const raw = page.items
      if (requestedPage > commentsPage.value && raw.length === 0) {
        commentsCursorHistory.value = commentsCursorHistory.value.slice(0, commentsPage.value + 1)
        commentsNextCursor.value = ''
        return
      }
      const { userIds, entityIds: commentIds } = collectThreadHydrationIds(raw)
      const hydrationTasks = [
        postMetaCache.ensureUserSummaries(userIds),
        postMetaCache.ensureLikeCounts(2, commentIds)
      ]
      if (authed.value) hydrationTasks.push(postMetaCache.ensureLikeStatuses(2, commentIds))
      const [usersResult, countsResult, statusesResult] = await Promise.allSettled(hydrationTasks)
      if (!commentsRequestTracker.isCurrent(token)) return

      const users = usersResult?.status === 'fulfilled' ? (usersResult.value || {}) : {}
      const counts = countsResult?.status === 'fulfilled' ? (countsResult.value || {}) : {}
      const statuses = statusesResult?.status === 'fulfilled' ? (statusesResult.value || {}) : {}
      const history = (reset ? [''] : commentsCursorHistory.value).slice(0, requestedPage + 1)
      if (page.nextCursor) history[requestedPage + 1] = page.nextCursor
      commentsCursorHistory.value = history
      commentsNextCursor.value = page.nextCursor
      commentsPage.value = requestedPage
      comments.value = raw.map((comment) => hydrateCommentItem(comment, { users, counts, statuses }))
      await maybeScrollFromRoute()
    } catch (error) {
      if (!commentsRequestTracker.isCurrent(token)) return
      commentsError.value = error?.message || '加载评论失败'
    } finally {
      if (commentsRequestTracker.isCurrent(token)) commentsLoading.value = false
    }
  }

  function repliesHasNext(comment) {
    return !!String(comment?._repliesNextCursor || '')
  }

  async function loadReplies(comment, targetPage = comment?._repliesPage, { reset = false } = {}) {
    if (!comment) return
    const scope = captureViewScope()
    const requestedPage = Math.max(0, Number(targetPage || 0))
    comment._repliesError = ''
    comment._repliesLoading = true
    try {
      const cursor = reset ? '' : currentRepliesCursor(comment, requestedPage)
      const resp = await apiListReplies(postId.value, comment.id, { cursor, size: comment._repliesSize })
      if (!isCurrentViewScope(scope)) return
      const page = normalizeCommentCursorPage(resp?.data)
      const raw = page.items
      if (requestedPage > comment._repliesPage && raw.length === 0) {
        comment._repliesCursorHistory = comment._repliesCursorHistory.slice(0, comment._repliesPage + 1)
        comment._repliesNextCursor = ''
        return
      }
      const { userIds, entityIds: replyIds } = collectThreadHydrationIds(raw, { includeReplyToUserId: true })
      const hydrationTasks = [
        postMetaCache.ensureUserSummaries(userIds),
        postMetaCache.ensureLikeCounts(2, replyIds)
      ]
      if (authed.value) hydrationTasks.push(postMetaCache.ensureLikeStatuses(2, replyIds))
      const [usersResult, countsResult, statusesResult] = await Promise.allSettled(hydrationTasks)
      if (!isCurrentViewScope(scope)) return

      const users = usersResult?.status === 'fulfilled' ? (usersResult.value || {}) : {}
      const counts = countsResult?.status === 'fulfilled' ? (countsResult.value || {}) : {}
      const statuses = statusesResult?.status === 'fulfilled' ? (statusesResult.value || {}) : {}
      const history = (reset || !Array.isArray(comment._repliesCursorHistory)
        ? ['']
        : comment._repliesCursorHistory).slice(0, requestedPage + 1)
      if (page.nextCursor) history[requestedPage + 1] = page.nextCursor
      comment._repliesCursorHistory = history
      comment._repliesNextCursor = page.nextCursor
      comment._repliesPage = requestedPage
      comment._replies = raw.map((reply) => hydrateReplyItem(reply, { users, counts, statuses }))
    } catch (error) {
      if (isCurrentViewScope(scope)) comment._repliesError = error?.message || '加载回复失败'
    } finally {
      if (isCurrentViewScope(scope)) comment._repliesLoading = false
    }
  }

  async function toggleReplies(comment) {
    if (!comment) return
    comment._repliesExpanded = !comment._repliesExpanded
    if (comment._repliesExpanded && comment._replies.length === 0) {
      await loadReplies(comment, 0, { reset: true })
    }
  }

  async function nextRepliesPage(comment) {
    if (!comment || comment._repliesLoading || !repliesHasNext(comment)) return
    await loadReplies(comment, comment._repliesPage + 1)
  }

  async function prevRepliesPage(comment) {
    if (!comment || comment._repliesLoading) return
    await loadReplies(comment, Math.max(0, comment._repliesPage - 1))
  }

  function startReply(comment, reply) {
    if (!authed.value || !comment) return
    comment._replying = true
    comment._replyError = ''
    comment._replyDraft = safeStorageGet(replyDraftKey(comment.id))
    comment._replyParentCommentId = normalizeOpaqueId(reply?.id || comment.id)

    const source = reply?.userId ? reply : comment
    comment._replyQuote = {
      sourceType: reply?.userId ? 'reply' : 'comment',
      sourceId: normalizeOpaqueId(source.id),
      userId: normalizeOpaqueId(source.userId),
      username: String(source.user?.username || ''),
      raw: String(source.content || ''),
      preview: buildQuotePreview(source.content)
    }
  }

  function cancelReply(comment) {
    if (!comment) return
    const record = replyAttemptRecord(comment)
    record.attempt.cancel()
    record.signature = ''
    comment._replying = false
    comment._replyError = ''
    comment._replySubmitting = false
    comment._replyParentCommentId = ''
    comment._replyQuote = null
  }

  async function submitReply(comment) {
    if (!authed.value || !comment) return
    comment._replyError = ''
    if (!String(comment._replyDraft || '').trim()) {
      comment._replyError = '回复内容不能为空'
      return
    }
    const scope = captureViewScope()
    const content = composeReplyContent(comment._replyDraft, comment._replyQuote)
    const parentCommentId = normalizeOpaqueId(comment._replyParentCommentId)
    comment._replySubmitting = true
    try {
      const record = replyAttemptRecord(comment)
      const attempt = bindReplyAttempt(comment, content, parentCommentId)
      await apiAddComment(scope.postId, { content, parentCommentId }, { writeAttempt: attempt })
      if (!isCurrentViewScope(scope)) return
      comment._replyDraft = ''
      attempt.succeed()
      record.signature = ''
      safeStorageSet(replyDraftKey(comment.id), '')
      comment._replying = false
      comment._replyParentCommentId = ''
      comment._replyQuote = null
      if (post.value) post.value.commentCount = Number(post.value.commentCount || 0) + 1
      if (!comment._repliesExpanded) comment._repliesExpanded = true
      await loadReplies(comment, 0, { reset: true })
    } catch (error) {
      if (!isCurrentViewScope(scope)) return
      comment._replyError = error?.message || '回复失败'
    } finally {
      if (isCurrentViewScope(scope)) comment._replySubmitting = false
    }
  }

  async function toggleCommentLike(comment) {
    if (!authed.value || !comment) return
    const scope = captureViewScope()
    const targetCommentId = normalizeOpaqueId(comment.id)
    comment._likeLoading = true
    try {
      const resp = await setLike({ entityType: 2, entityId: targetCommentId, liked: null })
      if (!isCurrentViewScope(scope) || !comments.value.includes(comment)) return
      const likeData = resp?.data && typeof resp.data === 'object'
        ? /** @type {Record<string, any>} */ (resp.data)
        : {}
      if (typeof likeData.likeCount === 'number') {
        comment.likeCount = likeData.likeCount
        postMetaCache.setLikeCount(2, targetCommentId, comment.likeCount)
      }
      if (typeof likeData.liked === 'boolean') {
        comment.liked = likeData.liked
        postMetaCache.setLikeStatus(2, targetCommentId, comment.liked)
      }
    } catch (error) {
      if (!isCurrentViewScope(scope) || !comments.value.includes(comment)) return
      commentsError.value = error?.message || '点赞失败'
    } finally {
      if (isCurrentViewScope(scope) && comments.value.includes(comment)) comment._likeLoading = false
    }
  }

  async function toggleReplyLike(comment, reply) {
    if (!authed.value || !comment || !reply) return
    const scope = captureViewScope()
    const targetReplyId = normalizeOpaqueId(reply.id)
    reply._likeLoading = true
    try {
      const resp = await setLike({ entityType: 2, entityId: targetReplyId, liked: null })
      if (!isCurrentViewScope(scope) || !comments.value.includes(comment) || !comment._replies.includes(reply)) return
      const likeData = resp?.data && typeof resp.data === 'object'
        ? /** @type {Record<string, any>} */ (resp.data)
        : {}
      if (typeof likeData.likeCount === 'number') {
        reply.likeCount = likeData.likeCount
        postMetaCache.setLikeCount(2, targetReplyId, reply.likeCount)
      }
      if (typeof likeData.liked === 'boolean') {
        reply.liked = likeData.liked
        postMetaCache.setLikeStatus(2, targetReplyId, reply.liked)
      }
    } catch (error) {
      if (!isCurrentViewScope(scope) || !comments.value.includes(comment) || !comment._replies.includes(reply)) return
      comment._repliesError = error?.message || '点赞失败'
    } finally {
      if (isCurrentViewScope(scope) && comments.value.includes(comment) && comment._replies.includes(reply)) {
        reply._likeLoading = false
      }
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
      await apiAddComment(scope.postId, { content }, { writeAttempt: commentAttempt })
      if (!isCurrentViewScope(scope)) return
      setNewComment('')
      commentAttempt.succeed()
      await loadComments(0, { reset: true })
      await refreshPost()
    } catch (error) {
      if (!isCurrentViewScope(scope)) return
      commentError.value = error?.message || '评论失败'
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

  function restoreDraft() {
    newComment.value = safeStorageGet(commentDraftKey())
  }

  function resetForIdentity() {
    commentAttempt.cancel()
    cancelReplyAttempts()
    commentsRequestTracker.invalidate()
    commenting.value = false
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
    restoreDraft()
  }

  function resetForPost() {
    commentAttempt.cancel()
    cancelReplyAttempts()
    commentsRequestTracker.invalidate()
    comments.value = []
    resetCommentsCursorPaging()
    commenting.value = false
    commentError.value = ''
    restoreDraft()
  }

  function dispose() {
    commentAttempt.cancel()
    cancelReplyAttempts()
    commentsRequestTracker.invalidate()
  }

  const composer = reactive({
    draft: newComment,
    submitting: commenting,
    error: commentError,
    setDraft: setNewComment,
    submit: addComment
  })

  const model = reactive({
    comments,
    page: commentsPage,
    hasNext: commentsHasNext,
    loading: commentsLoading,
    error: commentsError,
    composer,
    reload: reloadComments,
    nextPage: nextCommentsPage,
    prevPage: prevCommentsPage,
    commentAnchorId,
    replyAnchorId,
    clearReplyQuote,
    setReplyDraft,
    isBlockedUser,
    repliesHasNext,
    toggleReplies,
    nextRepliesPage,
    prevRepliesPage,
    startReply,
    cancelReply,
    submitReply,
    toggleCommentLike,
    toggleReplyLike
  })

  watch(newComment, () => commentAttempt.changeIntent())
  watch(
    () => [route.hash, route.query?.commentId, route.query?.replyId],
    () => {
      if (!commentsLoading.value) maybeScrollFromRoute()
    }
  )

  return {
    model,
    load: loadComments,
    restoreDraft,
    resetForIdentity,
    resetForPost,
    dispose
  }
}
