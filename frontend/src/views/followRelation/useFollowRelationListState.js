import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { listFollowees, listFollowers, followUser, unfollowUser } from '../../api/services/socialService'
import { normalizeOpaqueId } from '../../utils/opaqueId'
import { createLatestRequestTracker } from '../../utils/latestRequest'
import { hydrateFollowRelations } from '../followRelationHydration'

const PAGE_SIZE = 10

const RELATION_POLICIES = Object.freeze({
  followees: Object.freeze({
    list: listFollowees,
    title: '关注',
    subtitle: '查看这位成员正在持续关注的公开身份与关系变化。',
    emptyTitle: '暂无关注',
    emptyDescription: '当前没有可显示的关注关系，稍后刷新再看即可。',
    errorDescription: '关注列表加载失败，可以重试或稍后再来。',
    pill: '已关注',
    summary: '可以继续查看对方主页、公开动态与社交状态。'
  }),
  followers: Object.freeze({
    list: listFollowers,
    title: '粉丝',
    subtitle: '查看哪些成员正在关注这位用户的公开发言与社区存在感。',
    emptyTitle: '暂无粉丝',
    emptyDescription: '当前没有可显示的粉丝关系，稍后刷新再看即可。',
    errorDescription: '粉丝列表加载失败，可以重试或稍后再来。',
    pill: '新关注者',
    summary: '对方正在关注这位成员的公开动态，也可以继续查看其个人主页。'
  })
})

export function useFollowRelationListState({ relationKind, profileUserId }) {
  const router = useRouter()
  const auth = useAuthStore()
  const policy = computed(() => RELATION_POLICIES[relationKind.value])
  const authed = computed(() => auth.authed)
  const meId = computed(() => normalizeOpaqueId(auth.userId))
  const userId = computed(() => normalizeOpaqueId(profileUserId.value))

  const nextCursor = ref('')
  const loading = ref(false)
  const loadingMore = ref(false)
  const error = ref('')
  const pageError = ref('')
  const items = ref([])
  const hasNext = ref(true)
  const mutatingTargetIds = ref(new Set())
  let mutationGeneration = 0
  const activeMutations = new Map()

  const viewScope = computed(() => [
    userId.value,
    auth.tokenGeneration,
    meId.value,
    authed.value ? 'authenticated' : 'anonymous'
  ].join(':'))
  const requestTracker = createLatestRequestTracker({ getScope: () => viewScope.value })

  async function load({ append = false } = {}) {
    const targetUserId = userId.value
    if (!targetUserId) return
    const requestCursor = append ? nextCursor.value : ''
    const request = requestTracker.begin()
    const viewer = { authed: authed.value, viewerUserId: meId.value }
    if (append) {
      loadingMore.value = true
      pageError.value = ''
    } else {
      loading.value = true
      error.value = ''
    }
    try {
      const { data } = await policy.value.list(targetUserId, {
        cursor: requestCursor,
        size: PAGE_SIZE
      })
      if (!requestTracker.isCurrent(request)) return

      const nextItems = Array.isArray(data?.items) ? data.items : []
      const hydrated = await hydrateFollowRelations(nextItems, viewer)
      if (!requestTracker.isCurrent(request)) return

      const cursor = data?.hasNext === true && data?.nextCursor
        ? String(data.nextCursor)
        : ''
      nextCursor.value = cursor
      hasNext.value = Boolean(cursor)
      items.value = append ? [...items.value, ...hydrated] : hydrated
    } catch (e) {
      if (!requestTracker.isCurrent(request)) return
      if (append) pageError.value = e?.message || '加载更多失败'
      else error.value = e?.message || '加载失败'
    } finally {
      if (requestTracker.isCurrent(request)) {
        loading.value = false
        loadingMore.value = false
      }
    }
  }

  function isMutating(targetId) {
    return mutatingTargetIds.value.has(normalizeOpaqueId(targetId))
  }

  function beginMutation(targetId) {
    const normalizedTargetId = normalizeOpaqueId(targetId)
    if (!normalizedTargetId || mutatingTargetIds.value.has(normalizedTargetId)) return null
    const token = ++mutationGeneration
    activeMutations.set(normalizedTargetId, token)
    mutatingTargetIds.value = new Set([...mutatingTargetIds.value, normalizedTargetId])
    return { normalizedTargetId, token, scope: viewScope.value }
  }

  function currentMutationItem(mutation) {
    if (mutation.scope !== viewScope.value || activeMutations.get(mutation.normalizedTargetId) !== mutation.token) {
      return null
    }
    return items.value.find((item) => normalizeOpaqueId(item?.targetId) === mutation.normalizedTargetId) || null
  }

  function finishMutation(mutation) {
    if (activeMutations.get(mutation.normalizedTargetId) !== mutation.token) return
    activeMutations.delete(mutation.normalizedTargetId)
    const next = new Set(mutatingTargetIds.value)
    next.delete(mutation.normalizedTargetId)
    mutatingTargetIds.value = next
  }

  async function mutate(item, mutateRelation, nextFollowed, fallbackMessage) {
    if (!authed.value) return
    const mutation = beginMutation(item?.targetId)
    if (!mutation) return
    try {
      await mutateRelation(3, mutation.normalizedTargetId)
      const currentItem = currentMutationItem(mutation)
      if (!currentItem) return
      currentItem.hasFollowed = nextFollowed
    } catch (e) {
      if (!currentMutationItem(mutation)) return
      error.value = e?.message || fallbackMessage
    } finally {
      finishMutation(mutation)
    }
  }

  const doFollow = (item) => mutate(item, followUser, true, '关注失败')
  const doUnfollow = (item) => mutate(item, unfollowUser, false, '取关失败')

  const reload = () => load({ append: false })

  async function loadMore() {
    if (loading.value || loadingMore.value || !hasNext.value) return
    await load({ append: true })
  }

  function openProfile(item) {
    const targetId = normalizeOpaqueId(item?.targetId)
    if (!targetId) return
    router.push({ name: 'userProfile', params: { userId: targetId } })
  }

  onMounted(() => load())
  watch(viewScope, () => {
    requestTracker.invalidate()
    activeMutations.clear()
    mutatingTargetIds.value = new Set()
    nextCursor.value = ''
    items.value = []
    hasNext.value = true
    loading.value = false
    loadingMore.value = false
    error.value = ''
    pageError.value = ''
    if (userId.value) load()
  })
  onBeforeUnmount(() => {
    requestTracker.invalidate()
    activeMutations.clear()
  })

  return {
    authed,
    doFollow,
    doUnfollow,
    error,
    hasNext,
    isMutating,
    items,
    load,
    loading,
    loadingMore,
    loadMore,
    meId,
    nextCursor,
    openProfile,
    pageError,
    policy,
    reload
  }
}
