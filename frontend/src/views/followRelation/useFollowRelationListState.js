import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { listFollowees, listFollowers, followUser, unfollowUser } from '../../api/services/socialService'
import { normalizeOpaqueId } from '../../utils/opaqueId'
import { createLatestRequestTracker } from '../../utils/latestRequest'
import { hydrateFollowRelations } from '../followRelationHydration'

const RELATION_POLICIES = Object.freeze({
  followees: Object.freeze({
    list: listFollowees,
    title: '关注',
    subtitle: '查看这位成员正在持续关注的公开身份与关系变化。',
    loadingText: '正在加载关注关系…',
    emptyDescription: '当前没有可显示的关注关系，稍后刷新再看即可。',
    pill: '已关注',
    summary: '可以继续查看对方主页、公开动态与社交状态。'
  }),
  followers: Object.freeze({
    list: listFollowers,
    title: '粉丝',
    subtitle: '查看哪些成员正在关注这位用户的公开发言与社区存在感。',
    loadingText: '正在加载粉丝关系…',
    emptyDescription: '当前没有可显示的粉丝关系，稍后刷新再看即可。',
    pill: '新关注者',
    summary: '对方正在关注这位成员的公开动态，也可以继续查看其个人主页。'
  })
})

export function useFollowRelationListState({ relationKind, profileUserId, emitTrace = () => {} }) {
  const auth = useAuthStore()
  const policy = computed(() => RELATION_POLICIES[relationKind.value])
  const authed = computed(() => auth.authed)
  const meId = computed(() => normalizeOpaqueId(auth.userId))
  const userId = computed(() => normalizeOpaqueId(profileUserId.value))

  const page = ref(0)
  const size = ref(10)
  const cursorHistory = ref([''])
  const loading = ref(false)
  const error = ref('')
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

  async function load(targetPage = page.value) {
    const targetUserId = userId.value
    if (!targetUserId) return
    const targetCursor = cursorHistory.value[targetPage]
    if (targetPage < 0 || targetCursor == null) return
    const request = requestTracker.begin()
    const viewer = { authed: authed.value, viewerUserId: meId.value }
    error.value = ''
    loading.value = true
    try {
      const { data, traceId } = await policy.value.list(targetUserId, {
        cursor: targetCursor,
        size: size.value
      })
      if (!requestTracker.isCurrent(request)) return

      const nextItems = Array.isArray(data?.items) ? data.items : []
      const hydrated = await hydrateFollowRelations(nextItems, viewer)
      if (!requestTracker.isCurrent(request)) return

      const nextCursor = data?.hasNext === true && data?.nextCursor
        ? String(data.nextCursor)
        : ''
      emitTrace(traceId || '')
      if (targetPage > page.value && nextItems.length === 0) {
        hasNext.value = false
        cursorHistory.value = cursorHistory.value.slice(0, targetPage)
        return
      }
      const nextHistory = cursorHistory.value.slice(0, targetPage + 1)
      if (nextCursor) nextHistory[targetPage + 1] = nextCursor
      cursorHistory.value = nextHistory
      hasNext.value = Boolean(nextCursor)
      page.value = targetPage
      items.value = hydrated
    } catch (e) {
      if (!requestTracker.isCurrent(request)) return
      error.value = e?.message || '加载失败'
    } finally {
      if (requestTracker.isCurrent(request)) loading.value = false
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
      const result = await mutateRelation(3, mutation.normalizedTargetId)
      const currentItem = currentMutationItem(mutation)
      if (!currentItem) return
      emitTrace(result?.traceId || '')
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

  async function nextPage() {
    if (loading.value || !hasNext.value) return
    await load(page.value + 1)
  }

  async function prevPage() {
    if (loading.value) return
    await load(Math.max(0, page.value - 1))
  }

  const refresh = () => load(page.value)

  onMounted(() => load(0))
  watch(viewScope, () => {
    requestTracker.invalidate()
    activeMutations.clear()
    mutatingTargetIds.value = new Set()
    page.value = 0
    cursorHistory.value = ['']
    items.value = []
    hasNext.value = true
    loading.value = false
    error.value = ''
    if (userId.value) load(0)
  })
  onBeforeUnmount(() => {
    requestTracker.invalidate()
    activeMutations.clear()
  })

  return {
    authed,
    cursorHistory,
    doFollow,
    doUnfollow,
    error,
    hasNext,
    isMutating,
    items,
    load,
    loading,
    meId,
    nextPage,
    page,
    policy,
    prevPage,
    refresh
  }
}
