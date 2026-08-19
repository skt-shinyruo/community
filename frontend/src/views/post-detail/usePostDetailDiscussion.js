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
import {
  buildQuotePreview,
  collectThreadHydrationIds,
  composeReplyContent,
  hydrateCommentItem,
  hydrateReplyItem
} from '../postDetailState'
import { usePostDetailDrafts } from './usePostDetailDrafts'

function normalizeCommentCursorPage(raw) {
  const page = raw && typeof raw === 'object' ? raw : {}
  return {
    items: Array.isArray(page.items) ? page.items : [],
    nextCursor: page.nextCursor == null ? '' : String(page.nextCursor)
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
    if (comment) comment.ui.replyEditor.quote = null
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

  function currentRepliesCursor(comment, targetPage = comment?.ui.replyList.page) {
    if (!Array.isArray(comment?.ui.replyList.cursorHistory)) return ''
    return String(comment.ui.replyList.cursorHistory[targetPage] || '')
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

    if (!comment.ui.replyList.expanded) comment.ui.replyList.expanded = true
    if (comment.ui.replyList.items.length === 0) {
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
    return !!String(comment?.ui.replyList.nextCursor || '')
  }

  async function loadReplies(comment, targetPage = comment?.ui.replyList.page, { reset = false } = {}) {
    if (!comment) return
    const replyList = comment.ui.replyList
    const scope = captureViewScope()
    const requestedPage = Math.max(0, Number(targetPage || 0))
    replyList.error = ''
    replyList.loading = true
    try {
      const cursor = reset ? '' : currentRepliesCursor(comment, requestedPage)
      const resp = await apiListReplies(postId.value, comment.id, { cursor, size: replyList.size })
      if (!isCurrentViewScope(scope)) return
      const page = normalizeCommentCursorPage(resp?.data)
      const raw = page.items
      if (requestedPage > replyList.page && raw.length === 0) {
        replyList.cursorHistory = replyList.cursorHistory.slice(0, replyList.page + 1)
        replyList.nextCursor = ''
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
      const history = (reset || !Array.isArray(replyList.cursorHistory)
        ? ['']
        : replyList.cursorHistory).slice(0, requestedPage + 1)
      if (page.nextCursor) history[requestedPage + 1] = page.nextCursor
      replyList.cursorHistory = history
      replyList.nextCursor = page.nextCursor
      replyList.page = requestedPage
      replyList.items = raw.map((reply) => hydrateReplyItem(reply, { users, counts, statuses }))
    } catch (error) {
      if (isCurrentViewScope(scope)) replyList.error = error?.message || '加载回复失败'
    } finally {
      if (isCurrentViewScope(scope)) replyList.loading = false
    }
  }

  async function toggleReplies(comment) {
    if (!comment) return
    const replyList = comment.ui.replyList
    replyList.expanded = !replyList.expanded
    if (replyList.expanded && replyList.items.length === 0) {
      await loadReplies(comment, 0, { reset: true })
    }
  }

  async function nextRepliesPage(comment) {
    if (!comment || comment.ui.replyList.loading || !repliesHasNext(comment)) return
    await loadReplies(comment, comment.ui.replyList.page + 1)
  }

  async function prevRepliesPage(comment) {
    if (!comment || comment.ui.replyList.loading) return
    await loadReplies(comment, Math.max(0, comment.ui.replyList.page - 1))
  }

  function startReply(comment, reply) {
    if (!authed.value || !comment) return
    const editor = comment.ui.replyEditor
    editor.open = true
    editor.error = ''
    editor.draft = safeStorageGet(replyDraftKey(comment.id))
    editor.parentCommentId = normalizeOpaqueId(reply?.id || comment.id)

    const source = reply?.userId ? reply : comment
    editor.quote = {
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
    Object.assign(comment.ui.replyEditor, {
      open: false,
      error: '',
      submitting: false,
      parentCommentId: '',
      quote: null
    })
  }

  async function submitReply(comment) {
    if (!authed.value || !comment) return
    const editor = comment.ui.replyEditor
    editor.error = ''
    if (!String(editor.draft || '').trim()) {
      editor.error = '回复内容不能为空'
      return
    }
    const scope = captureViewScope()
    const content = composeReplyContent(editor.draft, editor.quote)
    const parentCommentId = normalizeOpaqueId(editor.parentCommentId)
    editor.submitting = true
    try {
      const record = replyAttemptRecord(comment)
      const attempt = bindReplyAttempt(comment, content, parentCommentId)
      await apiAddComment(scope.postId, { content, parentCommentId }, { writeAttempt: attempt })
      if (!isCurrentViewScope(scope)) return
      editor.draft = ''
      attempt.succeed()
      record.signature = ''
      safeStorageSet(replyDraftKey(comment.id), '')
      editor.open = false
      editor.parentCommentId = ''
      editor.quote = null
      if (post.value) post.value.commentCount = Number(post.value.commentCount || 0) + 1
      if (!comment.ui.replyList.expanded) comment.ui.replyList.expanded = true
      await loadReplies(comment, 0, { reset: true })
    } catch (error) {
      if (!isCurrentViewScope(scope)) return
      editor.error = error?.message || '回复失败'
    } finally {
      if (isCurrentViewScope(scope)) editor.submitting = false
    }
  }

  async function toggleCommentLike(comment) {
    if (!authed.value || !comment) return
    const like = comment.ui.like
    if (like.loading) return
    const scope = captureViewScope()
    const targetCommentId = normalizeOpaqueId(comment.id)
    const previous = { liked: like.liked, count: like.count }
    like.loading = true
    like.error = ''
    like.liked = !previous.liked
    like.count = Math.max(0, previous.count + (like.liked ? 1 : -1))
    try {
      const resp = await setLike({ entityType: 2, entityId: targetCommentId, liked: null })
      if (!isCurrentViewScope(scope) || !comments.value.includes(comment)) return
      const likeData = resp?.data && typeof resp.data === 'object'
        ? /** @type {Record<string, any>} */ (resp.data)
        : {}
      if (typeof likeData.likeCount === 'number') {
        like.count = likeData.likeCount
        postMetaCache.setLikeCount(2, targetCommentId, like.count)
      }
      if (typeof likeData.liked === 'boolean') {
        like.liked = likeData.liked
        postMetaCache.setLikeStatus(2, targetCommentId, like.liked)
      }
    } catch (error) {
      if (!isCurrentViewScope(scope) || !comments.value.includes(comment)) return
      like.liked = previous.liked
      like.count = previous.count
      like.error = error?.message || '点赞失败'
    } finally {
      if (isCurrentViewScope(scope) && comments.value.includes(comment)) like.loading = false
    }
  }

  async function toggleReplyLike(comment, reply) {
    if (!authed.value || !comment || !reply) return
    const like = reply.ui.like
    if (like.loading) return
    const scope = captureViewScope()
    const targetReplyId = normalizeOpaqueId(reply.id)
    const previous = { liked: like.liked, count: like.count }
    like.loading = true
    like.error = ''
    like.liked = !previous.liked
    like.count = Math.max(0, previous.count + (like.liked ? 1 : -1))
    try {
      const resp = await setLike({ entityType: 2, entityId: targetReplyId, liked: null })
      if (!isCurrentViewScope(scope) || !comments.value.includes(comment) || !comment.ui.replyList.items.includes(reply)) return
      const likeData = resp?.data && typeof resp.data === 'object'
        ? /** @type {Record<string, any>} */ (resp.data)
        : {}
      if (typeof likeData.likeCount === 'number') {
        like.count = likeData.likeCount
        postMetaCache.setLikeCount(2, targetReplyId, like.count)
      }
      if (typeof likeData.liked === 'boolean') {
        like.liked = likeData.liked
        postMetaCache.setLikeStatus(2, targetReplyId, like.liked)
      }
    } catch (error) {
      if (!isCurrentViewScope(scope) || !comments.value.includes(comment) || !comment.ui.replyList.items.includes(reply)) return
      like.liked = previous.liked
      like.count = previous.count
      like.error = error?.message || '点赞失败'
    } finally {
      if (isCurrentViewScope(scope) && comments.value.includes(comment) && comment.ui.replyList.items.includes(reply)) {
        like.loading = false
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
      comment.ui.like.liked = false
      Object.assign(comment.ui.replyEditor, {
        open: false,
        draft: '',
        error: '',
        submitting: false,
        parentCommentId: '',
        quote: null
      })
      comment.ui.like.loading = false
      for (const reply of comment.ui.replyList.items) {
        reply.ui.like.liked = false
        reply.ui.like.loading = false
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
