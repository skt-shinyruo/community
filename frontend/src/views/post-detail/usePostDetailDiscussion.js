// @ts-check
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useSocialPrefsStore } from '../../stores/socialPrefs'
import { usePostMetaCacheStore } from '../../stores/postMetaCache'
import { createLatestRequestTracker } from '../../utils/latestRequest'
import { normalizeOpaqueId, sameOpaqueId } from '../../utils/opaqueId'
import { safeStorageGet, safeStorageRemove, safeStorageSet } from '../../utils/safeStorage'
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

// 深链定位时最多自动追加的页数：commentId / replyId 不在首屏时按“加载更多”逐页补齐，
// 有界兜底避免无限翻页。
const MAX_DEEP_LINK_LOADS = 10

function normalizeCommentCursorPage(raw) {
  const page = raw && typeof raw === 'object' ? raw : {}
  return {
    items: Array.isArray(page.items) ? page.items : [],
    nextCursor: page.nextCursor == null ? '' : String(page.nextCursor)
  }
}

// 滚动定位工具：用于评论/回复锚点定位（hash 或 query），并提供短暂高亮提示。
export function scrollToAnchor(anchorId, options = {}) {
  if (typeof document === 'undefined') return false

  const id = String(anchorId || '').trim()
  if (!id) return false

  const {
    behavior = 'smooth',
    block = 'center',
    highlightClass = 'anchor-highlight',
    highlightMs = 1600
  } = options || {}

  const el = document.getElementById(id)
  if (!el) return false

  try {
    el.scrollIntoView({ behavior, block })
  } catch {
    try {
      el.scrollIntoView()
    } catch {
      return false
    }
  }

  if (highlightClass) {
    el.classList.add(highlightClass)
    window.setTimeout(() => el.classList.remove(highlightClass), Number(highlightMs || 0))
  }

  return true
}

// 草稿语义：空草稿清掉存储键，非空草稿落盘。
function persistDraft(key, value) {
  const v = String(value || '')
  if (v) safeStorageSet(key, v)
  else safeStorageRemove(key)
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

  function commentDraftKey() {
    return `community.draft.posts.${String(meUserId?.value || 'anonymous')}.${String(postId.value || '')}.comment`
  }

  function replyDraftKey(commentId) {
    return `community.draft.posts.${String(meUserId?.value || 'anonymous')}.${String(postId.value || '')}.reply.${normalizeOpaqueId(commentId)}`
  }

  function setNewComment(v) {
    newComment.value = String(v || '')
    persistDraft(commentDraftKey(), newComment.value)
  }

  function persistReplyDraft(comment, value) {
    if (!comment) return
    comment.ui.replyEditor.draft = String(value || '')
    persistDraft(replyDraftKey(comment.id), comment.ui.replyEditor.draft)
  }

  const comments = ref(/** @type {Array<Record<string, any>>} */ ([]))
  const commentsSize = 10
  const commentsNextCursor = ref('')
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

  function canReport(entry) {
    if (!authed.value) return false
    const userId = normalizeOpaqueId(entry?.userId)
    return !!userId && !sameOpaqueId(userId, meUserId.value)
  }

  function findComment(commentId) {
    return comments.value.find((item) => sameOpaqueId(item?.id, commentId)) || null
  }

  function findReply(comment, replyId) {
    if (!comment) return null
    return comment.ui.replyList.items.find((item) => sameOpaqueId(item?.id, replyId)) || null
  }

  async function hydrateThreadPage(raw, { includeReplyToUserId = false } = {}) {
    const { userIds, entityIds } = collectThreadHydrationIds(raw, { includeReplyToUserId })
    const hydrationTasks = [
      postMetaCache.ensureUserSummaries(userIds),
      postMetaCache.ensureLikeCounts(2, entityIds)
    ]
    if (authed.value) hydrationTasks.push(postMetaCache.ensureLikeStatuses(2, entityIds))
    const [usersResult, countsResult, statusesResult] = await Promise.allSettled(hydrationTasks)
    return {
      users: usersResult?.status === 'fulfilled' ? (usersResult.value || {}) : {},
      counts: countsResult?.status === 'fulfilled' ? (countsResult.value || {}) : {},
      statuses: statusesResult?.status === 'fulfilled' ? (statusesResult.value || {}) : {}
    }
  }

  function mergeAppended(existing, fresh) {
    const seen = new Set(existing.map((item) => normalizeOpaqueId(item?.id)))
    const additions = fresh.filter((item) => {
      const id = normalizeOpaqueId(item?.id)
      if (!id || seen.has(id)) return false
      seen.add(id)
      return true
    })
    return [...existing, ...additions]
  }

  function mergePrepended(existing, fresh) {
    const freshIds = new Set(fresh.map((item) => normalizeOpaqueId(item?.id)))
    const rest = existing.filter((item) => !freshIds.has(normalizeOpaqueId(item?.id)))
    return [...fresh, ...rest]
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

    // 追加分页下，深链目标不在已加载页时自动继续加载，直到命中或读完。
    let comment = findComment(commentId)
    let loads = 0
    while (!comment && commentsHasNext.value && loads < MAX_DEEP_LINK_LOADS) {
      loads += 1
      await loadComments({ append: true })
      comment = findComment(commentId)
    }
    if (!comment) return

    await nextTick()
    scrollToAnchor(commentAnchorId(commentId))
    if (!replyId) return

    if (!comment.ui.replyList.expanded) comment.ui.replyList.expanded = true
    if (!comment.ui.replyList.loaded && !comment.ui.replyList.loading) {
      await loadReplies(comment, { reset: true })
    }
    let reply = findReply(comment, replyId)
    loads = 0
    while (!reply && repliesHasNext(comment) && loads < MAX_DEEP_LINK_LOADS) {
      loads += 1
      await loadReplies(comment, { append: true })
      reply = findReply(comment, replyId)
    }
    if (!reply) return

    await nextTick()
    scrollToAnchor(replyAnchorId(replyId))
  }

  async function loadComments({ append = false, reset = false } = {}) {
    const token = commentsRequestTracker.begin()
    const cursor = append && !reset ? commentsNextCursor.value : ''
    commentsError.value = ''
    commentsLoading.value = true
    try {
      const resp = await apiListComments(postId.value, { cursor, size: commentsSize })
      if (!commentsRequestTracker.isCurrent(token)) return
      const page = normalizeCommentCursorPage(resp?.data)
      const { users, counts, statuses } = await hydrateThreadPage(page.items)
      if (!commentsRequestTracker.isCurrent(token)) return

      const fresh = page.items.map((comment) => hydrateCommentItem(comment, { users, counts, statuses }))
      if (append && !reset) {
        comments.value = mergeAppended(comments.value, fresh)
      } else {
        comments.value = fresh
      }
      commentsNextCursor.value = page.nextCursor
      if (!append || reset) await maybeScrollFromRoute()
    } catch (error) {
      if (!commentsRequestTracker.isCurrent(token)) return
      commentsError.value = error?.message || '加载评论失败'
    } finally {
      if (commentsRequestTracker.isCurrent(token)) commentsLoading.value = false
    }
  }

  // 发布评论后的静默插入：重新读取第一页并按 id 归并到现有列表头部，
  // 不清空用户已追加加载的页，然后把视图定位到新评论。
  async function prependLatestComments({ revealId = '' } = {}) {
    const token = commentsRequestTracker.begin()
    try {
      const resp = await apiListComments(postId.value, { cursor: '', size: commentsSize })
      if (!commentsRequestTracker.isCurrent(token)) return
      const page = normalizeCommentCursorPage(resp?.data)
      const { users, counts, statuses } = await hydrateThreadPage(page.items)
      if (!commentsRequestTracker.isCurrent(token)) return

      const fresh = page.items.map((comment) => hydrateCommentItem(comment, { users, counts, statuses }))
      const hadItems = comments.value.length > 0
      comments.value = mergePrepended(comments.value, fresh)
      if (!hadItems) commentsNextCursor.value = page.nextCursor

      const targetId = normalizeOpaqueId(revealId)
      if (targetId && fresh.some((item) => sameOpaqueId(item?.id, targetId))) {
        await nextTick()
        scrollToAnchor(commentAnchorId(targetId))
      }
    } catch (error) {
      if (commentsRequestTracker.isCurrent(token)) {
        commentsError.value = error?.message || '加载评论失败'
      }
    }
  }

  function repliesHasNext(comment) {
    return !!String(comment?.ui.replyList.nextCursor || '')
  }

  async function loadReplies(comment, { append = false, reset = false } = {}) {
    if (!comment) return
    const replyList = comment.ui.replyList
    const scope = captureViewScope()
    const cursor = append && !reset ? String(replyList.nextCursor || '') : ''
    replyList.error = ''
    replyList.loading = true
    try {
      const resp = await apiListReplies(postId.value, comment.id, { cursor, size: replyList.size })
      if (!isCurrentViewScope(scope)) return
      const page = normalizeCommentCursorPage(resp?.data)
      const { users, counts, statuses } = await hydrateThreadPage(page.items, { includeReplyToUserId: true })
      if (!isCurrentViewScope(scope)) return

      const fresh = page.items.map((reply) => hydrateReplyItem(reply, { users, counts, statuses }))
      if (append && !reset) {
        replyList.items = mergeAppended(replyList.items, fresh)
      } else {
        replyList.items = fresh
      }
      replyList.nextCursor = page.nextCursor
      replyList.loaded = true
    } catch (error) {
      if (isCurrentViewScope(scope)) replyList.error = error?.message || '加载回复失败'
    } finally {
      if (isCurrentViewScope(scope)) replyList.loading = false
    }
  }

  // 发布回复后的静默插入：与评论同样的头部归并，定位到新回复。
  async function prependLatestReplies(comment, { revealId = '' } = {}) {
    if (!comment) return
    const replyList = comment.ui.replyList
    const scope = captureViewScope()
    try {
      const resp = await apiListReplies(postId.value, comment.id, { cursor: '', size: replyList.size })
      if (!isCurrentViewScope(scope)) return
      const page = normalizeCommentCursorPage(resp?.data)
      const { users, counts, statuses } = await hydrateThreadPage(page.items, { includeReplyToUserId: true })
      if (!isCurrentViewScope(scope)) return

      const fresh = page.items.map((reply) => hydrateReplyItem(reply, { users, counts, statuses }))
      const hadItems = replyList.items.length > 0
      replyList.items = mergePrepended(replyList.items, fresh)
      replyList.loaded = true
      if (!hadItems) replyList.nextCursor = page.nextCursor

      const targetId = normalizeOpaqueId(revealId)
      if (targetId && fresh.some((item) => sameOpaqueId(item?.id, targetId))) {
        await nextTick()
        scrollToAnchor(replyAnchorId(targetId))
      }
    } catch (error) {
      if (isCurrentViewScope(scope)) replyList.error = error?.message || '加载回复失败'
    }
  }

  async function toggleReplies(comment) {
    if (!comment) return
    const replyList = comment.ui.replyList
    replyList.expanded = !replyList.expanded
    if (replyList.expanded && !replyList.loaded && !replyList.loading) {
      await loadReplies(comment, { reset: true })
    }
  }

  async function loadMoreComments() {
    if (!commentsHasNext.value || commentsLoading.value) return
    await loadComments({ append: true })
  }

  async function loadMoreReplies(comment) {
    if (!comment || comment.ui.replyList.loading || !repliesHasNext(comment)) return
    await loadReplies(comment, { append: true })
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
      const resp = await apiAddComment(scope.postId, { content, parentCommentId }, { writeAttempt: attempt })
      if (!isCurrentViewScope(scope)) return
      editor.draft = ''
      attempt.succeed()
      record.signature = ''
      safeStorageRemove(replyDraftKey(comment.id))
      editor.open = false
      editor.parentCommentId = ''
      editor.quote = null
      if (post.value) post.value.commentCount = Number(post.value.commentCount || 0) + 1
      if (!comment.ui.replyList.expanded) comment.ui.replyList.expanded = true
      const newReplyId = normalizeOpaqueId(/** @type {Record<string, any>} */ (resp?.data)?.commentId)
      await prependLatestReplies(comment, { revealId: newReplyId })
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
      const resp = await apiAddComment(scope.postId, { content }, { writeAttempt: commentAttempt })
      if (!isCurrentViewScope(scope)) return
      setNewComment('')
      commentAttempt.succeed()
      const newCommentId = normalizeOpaqueId(/** @type {Record<string, any>} */ (resp?.data)?.commentId)
      await prependLatestComments({ revealId: newCommentId })
      await refreshPost()
    } catch (error) {
      if (!isCurrentViewScope(scope)) return
      commentError.value = error?.message || '评论失败'
    } finally {
      if (isCurrentViewScope(scope)) commenting.value = false
    }
  }

  async function reloadComments() {
    await loadComments({ reset: true })
  }

  // 评论/回复编辑保存后的静默更新：直接在已加载列表中原位替换内容，
  // 不触发整页刷新，也不弹成功 toast（结果在当前屏幕可见）。
  function applyCommentEdit(commentId, content) {
    const targetId = normalizeOpaqueId(commentId)
    if (!targetId) return false
    const apply = (entry) => {
      entry.content = String(content || '')
      entry.editCount = Number(entry.editCount || 0) + 1
    }
    for (const comment of comments.value) {
      if (sameOpaqueId(comment.id, targetId)) {
        apply(comment)
        return true
      }
      for (const reply of comment.ui.replyList.items) {
        if (sameOpaqueId(reply.id, targetId)) {
          apply(reply)
          return true
        }
      }
    }
    return false
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
    commentsNextCursor.value = ''
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
    hasNext: commentsHasNext,
    loading: commentsLoading,
    error: commentsError,
    composer,
    reload: reloadComments,
    loadMore: loadMoreComments,
    commentAnchorId,
    replyAnchorId,
    clearReplyQuote,
    setReplyDraft,
    isBlockedUser,
    canReport,
    applyCommentEdit,
    repliesHasNext,
    toggleReplies,
    loadMoreReplies,
    reloadReplies: (comment) => loadReplies(comment, { reset: true }),
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
    load: () => loadComments({ reset: true }),
    restoreDraft,
    resetForIdentity,
    resetForPost,
    dispose
  }
}
