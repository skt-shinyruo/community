// @ts-check
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useSocialPrefsStore } from '../../stores/socialPrefs'
import { useTaxonomyStore } from '../../stores/taxonomy'
import { createLatestRequestTracker } from '../../utils/latestRequest'
import { markPostRead } from '../../utils/readTracker'
import { normalizeOpaqueId, sameOpaqueId } from '../../utils/opaqueId'
import { getUserProfile } from '../../api/services/userService'
import { getPostDetail } from '../../api/services/postService'
import { usePostDetailActions } from './usePostDetailActions'
import { usePostDetailDiscussion } from './usePostDetailDiscussion'

export function usePostDetailLoader(emit) {
  const route = useRoute()
  const auth = useAuthStore()
  const prefs = useSocialPrefsStore()
  const taxonomy = useTaxonomyStore()

  const authed = computed(() => !!auth.accessToken)
  const postId = computed(() => normalizeOpaqueId(route.params.postId))
  const meUserId = computed(() => normalizeOpaqueId(auth.userId))
  const post = ref(/** @type {Record<string, any> | null} */ (null))
  const postAuthor = ref(/** @type {Record<string, any> | null} */ (null))
  const loading = ref(false)
  const error = ref('')
  const postRequestTracker = createLatestRequestTracker()

  function emitTrace(traceId) {
    emit('trace', traceId || '')
  }

  function categoryLabel(id) {
    const categoryId = normalizeOpaqueId(id)
    if (!categoryId) return ''
    return taxonomy.categoriesById.get(categoryId)?.name || `分类#${categoryId}`
  }

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

  let actions
  async function loadPost() {
    const token = postRequestTracker.begin()
    error.value = ''
    loading.value = true
    try {
      const resp = await getPostDetail(postId.value)
      if (!postRequestTracker.isCurrent(token)) return
      post.value = resp?.data || null
      emitTrace(resp?.traceId)
      actions.applyPostLikeOverlay()

      if (post.value?.userId) {
        postAuthor.value = await getUserProfile(post.value.userId).catch(() => null)
        if (!postRequestTracker.isCurrent(token)) return
      } else {
        postAuthor.value = null
      }
    } catch (cause) {
      if (postRequestTracker.isCurrent(token)) error.value = cause?.message || '加载失败'
    } finally {
      if (postRequestTracker.isCurrent(token)) loading.value = false
    }
  }

  const discussion = usePostDetailDiscussion({
    authed,
    postId,
    meUserId,
    post,
    emitTrace,
    captureViewScope,
    isCurrentViewScope,
    refreshPost: loadPost
  })

  async function reload() {
    const scope = captureViewScope()
    if (authed.value) {
      try {
        await prefs.ensureBlocked()
      } catch {
        // The blocked-user list is optional context and must not prevent reading the post.
      }
    } else {
      prefs.clear()
    }
    if (!isCurrentViewScope(scope)) return
    await loadPost()
    if (!isCurrentViewScope(scope)) return
    await discussion.load()
    if (!isCurrentViewScope(scope)) return
    await actions.loadFollowStatus()
  }

  actions = usePostDetailActions({
    auth,
    authed,
    postId,
    post,
    meUserId,
    error,
    emitTrace,
    captureViewScope,
    isCurrentViewScope,
    refreshPost: loadPost,
    refreshComments: discussion.load,
    reloadPage: reload
  })

  const page = reactive({
    authed,
    postId,
    post,
    postAuthor,
    loading,
    error,
    categoryLabel,
    reload
  })

  watch(
    () => auth.tokenGeneration,
    () => {
      postRequestTracker.invalidate()
      discussion.resetForIdentity()
      actions.resetForIdentity()
      reload()
    }
  )

  watch(
    () => route.params.postId,
    () => {
      postRequestTracker.invalidate()
      post.value = null
      postAuthor.value = null
      discussion.resetForPost()
      actions.resetForPost()
      if (authed.value) markPostRead(postId.value, { identityId: meUserId.value })
      reload()
    }
  )

  onBeforeUnmount(() => {
    postRequestTracker.invalidate()
    discussion.dispose()
    actions.dispose()
  })

  onMounted(() => {
    taxonomy.ensureCategories()
    discussion.restoreDraft()
    if (authed.value) markPostRead(postId.value, { identityId: meUserId.value })
    reload()
  })

  return {
    page,
    postActions: actions.model,
    discussion: discussion.model
  }
}
