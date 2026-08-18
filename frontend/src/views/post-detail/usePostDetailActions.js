// @ts-check
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSocialPrefsStore } from '../../stores/socialPrefs'
import { usePostMetaCacheStore } from '../../stores/postMetaCache'
import { createLatestRequestTracker } from '../../utils/latestRequest'
import { normalizeOpaqueId, sameOpaqueId } from '../../utils/opaqueId'
import { showErrorToast, showToast } from '../../ui/toastService'
import { setLike, followUser, unfollowUser, getFollowStatus } from '../../api/services/socialService'
import { bookmarkPost, unbookmarkPost } from '../../api/services/bookmarkService'
import { blockUser, unblockUser } from '../../api/services/blockService'
import {
  deletePostByAuthor,
  moderationDelete,
  moderationTop,
  moderationWonderful,
  updateComment,
  updatePost
} from '../../api/services/postService'

const CONFIRMATION_COPY = {
  top: ['确认置顶', (postId) => `是否将帖子 #${postId} 置顶？`],
  wonderful: ['确认加精', (postId) => `是否将帖子 #${postId} 加精？`],
  delete: ['确认删除', (postId) => `是否删除帖子 #${postId}？删除后列表将不再展示。`],
  authorDelete: ['确认删除', (postId) => `是否删除帖子 #${postId}？该操作会将帖子标记为已删除。`]
}

function isDangerConfirmation(type) {
  return type === 'delete' || type === 'authorDelete'
}

function resolveConfirmation(type, postId) {
  const [title, message] = CONFIRMATION_COPY[type] || ['确认操作', () => '是否继续？']
  return { title, message: message(postId) }
}

function isWithinEditWindow(createTime, windowMs) {
  const createdAt = new Date(createTime).getTime()
  if (!Number.isFinite(createdAt) || createdAt <= 0) return false
  return Date.now() - createdAt <= windowMs
}

export function usePostDetailActions({
  auth,
  authed,
  postId,
  post,
  meUserId,
  error,
  captureViewScope,
  isCurrentViewScope,
  refreshPost,
  refreshComments,
  reloadPage
}) {
  const router = useRouter()
  const prefs = useSocialPrefsStore()
  const postMetaCache = usePostMetaCacheStore()
  const followStatusRequestTracker = createLatestRequestTracker()

  const loading = ref(false)
  const followStatus = ref(null)
  const reportOpen = ref(false)

  const isOwner = computed(() => sameOpaqueId(post.value?.userId, meUserId.value))
  const isBlockedAuthor = computed(() => {
    const userId = normalizeOpaqueId(post.value?.userId)
    return !!userId && prefs.blockedSet.has(userId)
  })
  const canEditPost = computed(() => {
    if (!authed.value || !post.value || !isOwner.value) return false
    if (Number(post.value.status || 0) === 2) return false
    return isWithinEditWindow(post.value.createTime, 24 * 3600 * 1000)
  })

  const confirmationOpen = ref(false)
  const confirmationTitle = ref('')
  const confirmationMessage = ref('')
  const confirmationAction = ref('')
  const confirmationVariant = computed(() => (
    isDangerConfirmation(confirmationAction.value) ? 'danger' : 'primary'
  ))
  const confirmationOkText = computed(() => (
    isDangerConfirmation(confirmationAction.value) ? '删除' : '确认'
  ))

  const editorOpen = ref(false)
  const editorMode = ref('post')
  const editorInitialTitle = ref('')
  const editorInitialContent = ref('')
  const editorInitialBlocks = ref([])
  const editorCommentId = ref('')

  function applyPostLikeOverlay() {
    if (!post.value) return
    const currentPostId = normalizeOpaqueId(postId.value)
    if (!currentPostId) return

    const cachedCount = postMetaCache.getLikeCount(1, currentPostId)
    if (typeof cachedCount === 'number') post.value.likeCount = cachedCount

    if (!authed.value) {
      post.value.liked = false
      return
    }
    const cachedLiked = postMetaCache.getLikeStatus(1, currentPostId)
    if (typeof cachedLiked === 'boolean') post.value.liked = cachedLiked
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
      followStatus.value = resp?.data ?? null
    } catch {
      if (followStatusRequestTracker.isCurrent(token)) followStatus.value = null
    }
  }

  async function toggleLike() {
    if (!authed.value || !post.value) return
    const scope = captureViewScope()
    const targetPostId = normalizeOpaqueId(post.value.id)
    loading.value = true
    try {
      const resp = await setLike({ entityType: 1, entityId: targetPostId, liked: null })
      if (!isCurrentViewScope(scope)) return
      const likeData = resp?.data && typeof resp.data === 'object'
        ? /** @type {Record<string, any>} */ (resp.data)
        : {}
      if (typeof likeData.likeCount === 'number') {
        post.value.likeCount = likeData.likeCount
        postMetaCache.setLikeCount(1, targetPostId, post.value.likeCount)
      }
      if (typeof likeData.liked === 'boolean') {
        post.value.liked = likeData.liked
        postMetaCache.setLikeStatus(1, targetPostId, post.value.liked)
      }
    } catch (cause) {
      if (isCurrentViewScope(scope)) error.value = cause?.message || '点赞操作失败'
    } finally {
      if (isCurrentViewScope(scope)) loading.value = false
    }
  }

  async function follow(doFollow) {
    if (!authed.value || !post.value?.userId || isOwner.value) return
    const scope = captureViewScope()
    const targetUserId = normalizeOpaqueId(post.value.userId)
    loading.value = true
    try {
      if (doFollow) await followUser(3, targetUserId)
      else await unfollowUser(3, targetUserId)
      if (!isCurrentViewScope(scope)) return
      followStatus.value = doFollow
    } catch (cause) {
      if (isCurrentViewScope(scope)) error.value = cause?.message || '关注操作失败'
    } finally {
      if (isCurrentViewScope(scope)) loading.value = false
    }
  }

  async function toggleBookmark() {
    if (!authed.value || !post.value) return
    const scope = captureViewScope()
    const targetPostId = normalizeOpaqueId(post.value.id)
    const wasBookmarked = !!post.value.bookmarked
    loading.value = true
    try {
      if (wasBookmarked) await unbookmarkPost(targetPostId)
      else await bookmarkPost(targetPostId)
      if (!isCurrentViewScope(scope)) return
      post.value.bookmarked = !wasBookmarked
    } catch (cause) {
      if (isCurrentViewScope(scope)) error.value = cause?.message || '收藏操作失败'
    } finally {
      if (isCurrentViewScope(scope)) loading.value = false
    }
  }

  function openReport() {
    if (authed.value) reportOpen.value = true
  }

  function closeReport() {
    reportOpen.value = false
  }

  async function toggleBlockAuthor() {
    if (!authed.value || !post.value) return
    const userId = normalizeOpaqueId(post.value.userId)
    if (!userId || sameOpaqueId(userId, meUserId.value)) return
    const scope = captureViewScope()
    const wasBlocked = isBlockedAuthor.value
    loading.value = true
    try {
      if (wasBlocked) {
        await unblockUser(userId)
        if (!isCurrentViewScope(scope)) return
        showToast({ type: 'success', text: '已解除屏蔽' })
      } else {
        await blockUser(userId)
        if (!isCurrentViewScope(scope)) return
        showToast({ type: 'success', text: '已屏蔽该用户' })
      }
      await prefs.ensureBlocked(true)
    } catch (cause) {
      if (isCurrentViewScope(scope)) error.value = cause?.message || '屏蔽操作失败'
    } finally {
      if (isCurrentViewScope(scope)) loading.value = false
    }
  }

  function canEditComment(comment) {
    if (!authed.value) return false
    const userId = normalizeOpaqueId(comment?.userId)
    if (!userId || !sameOpaqueId(userId, meUserId.value)) return false
    if (Number(comment?.status || 0) !== 0) return false
    return isWithinEditWindow(comment?.createTime, 15 * 60 * 1000)
  }

  function closeConfirmation() {
    confirmationOpen.value = false
    confirmationTitle.value = ''
    confirmationMessage.value = ''
    confirmationAction.value = ''
  }

  function confirmModeration(type) {
    if (!post.value) return
    confirmationAction.value = type
    confirmationOpen.value = true
    const confirmation = resolveConfirmation(type, post.value.id)
    confirmationTitle.value = confirmation.title
    confirmationMessage.value = confirmation.message
  }

  function confirmAuthorDelete() {
    confirmModeration('authorDelete')
  }

  async function runConfirmation() {
    const type = confirmationAction.value
    const scope = captureViewScope()
    const targetPostId = normalizeOpaqueId(post.value?.id)
    closeConfirmation()
    if (!type || !targetPostId) return
    loading.value = true
    try {
      if (type === 'top') await moderationTop(targetPostId)
      else if (type === 'wonderful') await moderationWonderful(targetPostId)
      else if (type === 'delete') await moderationDelete(targetPostId)
      else if (type === 'authorDelete') await deletePostByAuthor(targetPostId)
      if (!isCurrentViewScope(scope)) return
      if (type === 'authorDelete') {
        router.push({ name: 'posts' })
        return
      }
      await reloadPage()
    } catch (cause) {
      if (isCurrentViewScope(scope)) error.value = cause?.message || '管理操作失败'
    } finally {
      if (isCurrentViewScope(scope)) loading.value = false
    }
  }

  function closeEditor() {
    editorOpen.value = false
    editorMode.value = 'post'
    editorInitialTitle.value = ''
    editorInitialContent.value = ''
    editorInitialBlocks.value = []
    editorCommentId.value = ''
  }

  function openPostEditor() {
    if (!post.value || !canEditPost.value) return
    editorMode.value = 'post'
    editorInitialTitle.value = String(post.value.title || '')
    editorInitialContent.value = ''
    editorInitialBlocks.value = Array.isArray(post.value.blocks) ? post.value.blocks : []
    editorCommentId.value = ''
    editorOpen.value = true
  }

  function openCommentEditor(comment) {
    if (!comment || !canEditComment(comment)) return
    const commentId = normalizeOpaqueId(comment.id)
    if (!commentId) return
    editorMode.value = 'comment'
    editorInitialTitle.value = ''
    editorInitialContent.value = String(comment.content || '')
    editorInitialBlocks.value = []
    editorCommentId.value = commentId
    editorOpen.value = true
  }

  async function submitEditor(payload) {
    if (!post.value) return
    const scope = captureViewScope()
    const targetPostId = normalizeOpaqueId(post.value.id)
    const targetMode = editorMode.value
    const targetCommentId = normalizeOpaqueId(editorCommentId.value)
    loading.value = true
    try {
      if (targetMode === 'post') {
        await updatePost(targetPostId, {
          title: String(payload?.title || '').trim(),
          blocks: Array.isArray(payload?.blocks) ? payload.blocks : [],
          categoryId: post.value.categoryId,
          tags: Array.isArray(post.value.tags) ? post.value.tags : []
        })
        if (!isCurrentViewScope(scope)) return
        const query = String(payload?.title || '').trim()
        showToast({
          type: 'success',
          title: '已保存',
          text: '帖子已更新。搜索结果更新为最终一致，可能延迟数秒到数十秒。',
          duration: 6000,
          actionText: '去搜索',
          onAction: () => router.push({ name: 'search', query: query ? { q: query } : {} })
        })
        closeEditor()
        await refreshPost()
      } else {
        await updateComment(targetPostId, targetCommentId, {
          content: String(payload?.content || '').trim()
        })
        if (!isCurrentViewScope(scope)) return
        showToast({ type: 'success', text: '已保存' })
        closeEditor()
        await refreshComments()
      }
    } catch (cause) {
      if (isCurrentViewScope(scope)) {
        showErrorToast(cause, { type: 'error', text: cause?.message || '保存失败' })
      }
    } finally {
      if (isCurrentViewScope(scope)) loading.value = false
    }
  }

  function resetForIdentity() {
    followStatusRequestTracker.invalidate()
    postMetaCache.clearLikeStatuses()
    loading.value = false
    followStatus.value = null
    reportOpen.value = false
    closeEditor()
    closeConfirmation()
    if (post.value) {
      post.value.liked = false
      post.value.bookmarked = false
    }
  }

  function resetForPost() {
    followStatusRequestTracker.invalidate()
    loading.value = false
    followStatus.value = null
    reportOpen.value = false
    closeEditor()
    closeConfirmation()
  }

  const confirmation = reactive({
    open: confirmationOpen,
    title: confirmationTitle,
    message: confirmationMessage,
    variant: confirmationVariant,
    okText: confirmationOkText,
    close: closeConfirmation,
    run: runConfirmation
  })

  const editor = reactive({
    open: editorOpen,
    mode: editorMode,
    initialTitle: editorInitialTitle,
    initialContent: editorInitialContent,
    initialBlocks: editorInitialBlocks,
    close: closeEditor,
    submit: submitEditor
  })

  const report = reactive({
    open: reportOpen,
    openDialog: openReport,
    close: closeReport
  })

  const commentEditing = {
    canEdit: canEditComment,
    open: openCommentEditor
  }

  const model = reactive({
    loading,
    followStatus,
    isOwner,
    isBlockedAuthor,
    canEditPost,
    canModerate: computed(() => auth.isAdminOrModerator),
    confirmation,
    editor,
    report,
    commentEditing,
    toggleLike,
    follow,
    toggleBookmark,
    toggleBlockAuthor,
    confirmModeration,
    confirmAuthorDelete,
    openPostEditor
  })

  return {
    model,
    applyPostLikeOverlay,
    loadFollowStatus,
    resetForIdentity,
    resetForPost,
    dispose: () => followStatusRequestTracker.invalidate()
  }
}
