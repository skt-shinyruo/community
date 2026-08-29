import { computed, reactive, readonly, ref, unref, watch } from 'vue'
import { blockUser, unblockUser } from '../api/services/blockService'
import { followUser, getFollowStatus, unfollowUser } from '../api/services/socialService'
import { getUserProfile, listUserRecentComments, listUserRecentPosts } from '../api/services/userService'
import { useAuthStore } from '../stores/auth'
import { usePostMetaCacheStore } from '../stores/postMetaCache'
import { useSocialPrefsStore } from '../stores/socialPrefs'
import { useTaxonomyStore } from '../stores/taxonomy'
import { showToast } from '../ui/toastService'
import { normalizeOpaqueId, sameOpaqueId } from '../utils/opaqueId'
import { settleNamedRequests } from '../utils/settledRequests'
import { buildCanonicalConversationId } from './conversationDetailState'
import { buildProfileTimeline, collectTimelineUserIds } from './userProfileTimeline'

export function buildProfileWalletAsset({ authed, isSelf } = {}) {
  if (authed && isSelf) {
    return {
      valueText: '仅自己可见',
      chipText: '仅自己可见',
      description: '资产明细只在钱包页向本人展示。'
    }
  }

  return {
    valueText: '未公开',
    chipText: '未公开',
    description: '该成员未公开资产信息。'
  }
}

export function describeFollowStatusText({ followStatus, followStatusState = 'idle', authed, isSelf } = {}) {
  if (isSelf) return '这是你的主页'
  if (followStatus === true) return '你已关注'
  if (authed) {
    if (followStatus === false && followStatusState === 'ready') return '公开可关注'
    if (followStatusState === 'error') return '关系暂不可用'
    return '关系查询中'
  }
  return '公开可见'
}

export function buildCommunityNextSteps({ authed, isSelf, userId } = {}) {
  if (authed && isSelf) {
    return [
      { key: 'settings', label: '编辑资料', to: { name: 'settings' }, variant: 'secondary' },
      { key: 'posts', label: '回到讨论区', to: { name: 'posts' }, variant: 'ghost' },
      { key: 'wallet', label: '查看钱包', to: { name: 'wallet' }, variant: 'ghost' }
    ]
  }

  return [
    { key: 'posts', label: '去讨论区看看', to: { name: 'posts' }, variant: 'secondary' },
    { key: 'followees', label: '查看关注', to: { name: 'followees', params: { userId: String(userId || '') } }, variant: 'ghost' },
    { key: 'followers', label: '查看粉丝', to: { name: 'followers', params: { userId: String(userId || '') } }, variant: 'ghost' }
  ]
}

export function useUserProfilePage({ userId: userIdSource }) {
  const auth = useAuthStore()
  const postMetaCache = usePostMetaCacheStore()
  const prefs = useSocialPrefsStore()
  const taxonomy = useTaxonomyStore()

  const profile = ref(null)
  const recentPosts = ref([])
  const recentComments = ref([])
  const timelineUsers = ref({})
  const loading = ref(false)
  const error = ref('')
  const actionLoading = ref(false)
  const followStatus = ref(null)
  const followStatusState = ref('idle')
  const reportOpen = ref(false)
  let reloadGeneration = 0
  let actionGeneration = 0
  let mounted = false
  let stopViewScopeWatch = null
  let stopAuthScopeWatch = null

  const userId = computed(() => normalizeOpaqueId(unref(userIdSource)))
  const authed = computed(() => Boolean(auth.accessToken))
  const meUserId = computed(() => normalizeOpaqueId(auth.userId))
  const isSelfProfile = computed(() => sameOpaqueId(meUserId.value, userId.value))
  const isBlocked = computed(() => prefs.blockedSet.has(userId.value))
  const authScope = computed(() => [
    auth.tokenGeneration,
    meUserId.value,
    authed.value ? 'authenticated' : 'anonymous'
  ].join(':'))
  const viewScope = computed(() => `${userId.value}:${authScope.value}`)

  const joinedYear = computed(() => {
    const timestamp = profile.value?.createTime
    if (!timestamp) return ''
    const year = new Date(timestamp).getFullYear()
    return Number.isFinite(year) ? String(year) : ''
  })
  const profileName = computed(() => profile.value?.username || `成员 ${profile.value?.id || userId.value || ''}`)
  const privateMessageTo = computed(() => {
    const conversationId = buildCanonicalConversationId(meUserId.value, userId.value)
    return conversationId ? `/messages/${conversationId}` : '/messages'
  })
  const showUserLevel = computed(() => profile.value?.showUserLevel === true)
  const walletAsset = computed(() => buildProfileWalletAsset({
    profile: profile.value,
    authed: authed.value,
    isSelf: isSelfProfile.value
  }))
  const followStatusText = computed(() => describeFollowStatusText({
    followStatus: followStatus.value,
    followStatusState: followStatusState.value,
    authed: authed.value,
    isSelf: isSelfProfile.value
  }))
  const communityNextSteps = computed(() => buildCommunityNextSteps({
    authed: authed.value,
    isSelf: isSelfProfile.value,
    userId: userId.value
  }))
  const profileTimeline = computed(() => buildProfileTimeline({
    posts: recentPosts.value,
    comments: recentComments.value,
    usersById: timelineUsers.value,
    limit: 6
  }))

  function reloadIsCurrent(generation, scope) {
    return generation === reloadGeneration && scope === viewScope.value
  }

  function clearViewState() {
    profile.value = null
    recentPosts.value = []
    recentComments.value = []
    timelineUsers.value = {}
    followStatus.value = null
    followStatusState.value = 'idle'
    loading.value = false
    error.value = ''
    reportOpen.value = false
  }

  async function reload() {
    const targetId = userId.value
    if (!targetId) {
      reloadGeneration += 1
      clearViewState()
      return
    }

    const scope = viewScope.value
    const generation = ++reloadGeneration
    const loadFollowStatus = authed.value
      && Boolean(meUserId.value)
      && !sameOpaqueId(meUserId.value, targetId)

    error.value = ''
    loading.value = true
    followStatus.value = null
    followStatusState.value = loadFollowStatus ? 'loading' : 'idle'

    try {
      const outcome = await settleNamedRequests({
        profile: () => getUserProfile(targetId, { force: true }),
        posts: () => listUserRecentPosts(targetId, { page: 0, size: 3 }),
        comments: () => listUserRecentComments(targetId, { page: 0, size: 3 }),
        follow: () => loadFollowStatus
          ? getFollowStatus(3, targetId, { force: true })
          : null
      })
      if (!reloadIsCurrent(generation, scope)) return

      const profileResult = outcome.results.profile
      if (!profileResult.ok) throw profileResult.error
      const postsResult = outcome.results.posts
      const commentsResult = outcome.results.comments
      const nextFollowResult = outcome.results.follow
      const nextPosts = postsResult.ok && Array.isArray(postsResult.value?.data) ? postsResult.value.data : []
      const nextComments = commentsResult.ok && Array.isArray(commentsResult.value?.data) ? commentsResult.value.data : []

      let nextTimelineUsers = {}
      const timelineUserIds = collectTimelineUserIds({ posts: nextPosts, comments: nextComments })
      if (timelineUserIds.length > 0) {
        try {
          nextTimelineUsers = await postMetaCache.ensureUserSummaries(timelineUserIds)
        } catch {}
      }
      if (!reloadIsCurrent(generation, scope)) return

      profile.value = profileResult.value
      recentPosts.value = nextPosts
      recentComments.value = nextComments
      timelineUsers.value = nextTimelineUsers
      if (loadFollowStatus) {
        const nextStatus = nextFollowResult.ok ? nextFollowResult.value?.data : null
        followStatus.value = typeof nextStatus === 'boolean' ? nextStatus : null
        followStatusState.value = typeof nextStatus === 'boolean' ? 'ready' : 'error'
      } else {
        followStatus.value = null
        followStatusState.value = 'idle'
      }

    } catch (cause) {
      if (reloadIsCurrent(generation, scope)) error.value = cause?.message || '加载失败'
    } finally {
      if (reloadIsCurrent(generation, scope)) loading.value = false
    }
  }

  function beginAction() {
    const targetId = userId.value
    if (
      actionLoading.value
      || !targetId
      || !authed.value
      || !meUserId.value
      || sameOpaqueId(meUserId.value, targetId)
    ) return null

    actionLoading.value = true
    return {
      generation: ++actionGeneration,
      targetId,
      authScope: authScope.value,
      viewScope: viewScope.value
    }
  }

  function actionIsCurrent(action) {
    return action.generation === actionGeneration && action.viewScope === viewScope.value
  }

  function finishAction(action) {
    if (action.generation === actionGeneration) actionLoading.value = false
  }

  async function setFollowing(following) {
    const action = beginAction()
    if (!action) return
    try {
      if (following) await followUser(3, action.targetId)
      else await unfollowUser(3, action.targetId)
      if (!actionIsCurrent(action)) return
      await reload()
    } catch (cause) {
      if (actionIsCurrent(action)) error.value = cause?.message || '关注操作失败'
    } finally {
      finishAction(action)
    }
  }

  async function toggleBlocked() {
    const action = beginAction()
    if (!action) return
    const wasBlocked = prefs.blockedSet.has(action.targetId)
    try {
      await (wasBlocked ? unblockUser(action.targetId) : blockUser(action.targetId))
      if (action.authScope === authScope.value) await prefs.ensureBlocked(true)
      if (!actionIsCurrent(action)) return
      showToast({ type: 'success', text: wasBlocked ? '已解除屏蔽' : '已屏蔽该用户' })
    } catch (cause) {
      if (actionIsCurrent(action)) error.value = cause?.message || '操作失败'
    } finally {
      finishAction(action)
    }
  }

  function mount() {
    if (mounted) return
    mounted = true
    stopViewScopeWatch = watch(viewScope, () => {
      reloadGeneration += 1
      actionGeneration += 1
      actionLoading.value = false
      clearViewState()
      if (userId.value) void reload()
    })
    stopAuthScopeWatch = watch(authScope, () => {
      if (authed.value) Promise.resolve(prefs.ensureBlocked(true)).catch(() => {})
      else prefs.clear()
    }, { immediate: true })
    taxonomy.ensureCategories()
    return reload()
  }

  function unmount() {
    if (!mounted) return
    mounted = false
    stopViewScopeWatch?.()
    stopAuthScopeWatch?.()
    stopViewScopeWatch = null
    stopAuthScopeWatch = null
    reloadGeneration += 1
    actionGeneration += 1
  }

  const model = readonly(reactive({
    userId,
    profile,
    recentPosts,
    recentComments,
    loading,
    error,
    authed,
    meUserId,
    actionLoading,
    followStatus,
    followStatusState,
    reportOpen,
    isBlocked,
    joinedYear,
    profileName,
    isSelfProfile,
    privateMessageTo,
    showUserLevel,
    walletAsset,
    followStatusText,
    communityNextSteps,
    profileTimeline
  }))
  const actions = {
    reload,
    follow: () => setFollowing(true),
    unfollow: () => setFollowing(false),
    toggleBlocked,
    openReport: () => { reportOpen.value = true },
    closeReport: () => { reportOpen.value = false }
  }

  return { model, actions, lifecycle: { mount, unmount } }
}
