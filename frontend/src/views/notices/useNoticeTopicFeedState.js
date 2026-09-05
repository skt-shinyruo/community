// 通知主题详情页状态：按 topic 的追加式消费流、标记已读与壳层未读角标联动。
// 身份或 topic 变化时重置并丢弃过期响应；已读操作成功后本地翻转为已读（结果立即可见，
// 走静默更新），并触发 inboxUnread 刷新，不引入轮询。

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { useInboxUnreadStore } from '../../stores/inboxUnread'
import { listNotices, markRead } from '../../api/services/noticeService'
import { safeJsonParse } from '../../utils/safeJson'
import { normalizeOpaqueId, normalizeOpaqueIds } from '../../utils/opaqueId'
import { createLatestRequestTracker } from '../../utils/latestRequest'

const TOPIC_POLICY = Object.freeze({
  comment: {
    title: '评论通知',
    subtitle: '回到需要你继续阅读或回复的评论线程。'
  },
  like: {
    title: '点赞通知',
    subtitle: '集中查看哪些内容最近收到了新的认可。'
  },
  follow: {
    title: '关注通知',
    subtitle: '查看最近新增的关注与社交变化。'
  },
  moderation: {
    title: '治理通知',
    subtitle: '查看治理动作和处理结果的最新更新。'
  }
})

const FALLBACK_TOPIC_POLICY = Object.freeze({
  title: '通知详情',
  subtitle: '查看这一类通知的详细记录。'
})

const NOTICE_TYPE_PRESENTATION = Object.freeze({
  COMMENT_CREATED: {
    title: '有人回复了你的内容',
    body: '有人在帖子或评论线程里与你互动，可以返回原帖继续阅读上下文。'
  },
  LIKE_CREATED: {
    title: '你的内容收到了新的点赞',
    body: '这说明你的内容正在被更多人看见，也适合回到原帖继续跟进讨论。'
  },
  FOLLOW_CREATED: {
    title: '你收到了新的关注',
    body: '新的关注通常意味着有人开始留意你的公开发言和动态。'
  },
  MODERATION_ACTION_APPLIED: {
    title: '治理状态有更新',
    body: '如果这条通知涉及帖子或内容治理，建议回到相关页面查看更完整的结果。'
  }
})

export function isNoticeRead(notice) {
  return Number(notice?.status || 0) === 1
}

export function describeNoticeContent(notice) {
  const raw = safeJsonParse(notice?.content, null)
  const type = String(raw?.type || '')
  const known = NOTICE_TYPE_PRESENTATION[type]
  if (known) return known
  return { title: '查看这条通知', body: `通知：${type || 'unknown'}` }
}

export function noticePostId(notice) {
  const raw = safeJsonParse(notice?.content, null)
  const type = String(raw?.type || '')
  const payload = raw?.payload || {}
  const pid = normalizeOpaqueId(payload?.postId)
  if (pid) return pid
  if (type === 'MODERATION_ACTION_APPLIED' && Number(payload?.targetType || 0) === 1) {
    return normalizeOpaqueId(payload?.targetId)
  }
  return ''
}

export function noticeActorLabel(notice) {
  const raw = safeJsonParse(notice?.content, null)
  const value = String(raw?.payload?.actorUserId || '').trim()
  if (!value) return ''
  return `社区成员 ${value.slice(0, 8)}`
}

export function useNoticeTopicFeedState({ topic }) {
  const auth = useAuthStore()
  const inboxUnread = useInboxUnreadStore()

  const page = ref(0)
  const size = 10
  const hasNext = ref(true)

  const loading = ref(false)
  const loadingMore = ref(false)
  const markingRead = ref(false)
  const error = ref('')
  const pageError = ref('')
  const items = ref([])

  const loadRequestTracker = createLatestRequestTracker()
  const markReadRequestTracker = createLatestRequestTracker()

  const policy = computed(() => TOPIC_POLICY[topic.value] || FALLBACK_TOPIC_POLICY)
  const hasUnread = computed(() => items.value.some((n) => !isNoticeRead(n)))

  const cards = computed(() => items.value.map((notice) => {
    const presentation = describeNoticeContent(notice)
    return {
      id: String(notice?.id || ''),
      createTime: notice?.createTime,
      read: isNoticeRead(notice),
      title: presentation.title,
      body: presentation.body,
      actorLabel: noticeActorLabel(notice),
      postId: noticePostId(notice)
    }
  }))

  function currentViewScope() {
    return `${auth.tokenGeneration}:${String(auth.userId || '')}:${topic.value}`
  }

  function isCurrentRequest(tracker, token, viewScope) {
    return tracker.isCurrent(token) && currentViewScope() === viewScope
  }

  async function load(append = false, targetPage = page.value) {
    if (!auth.authed || !topic.value) return
    const token = loadRequestTracker.begin()
    const viewScope = currentViewScope()
    const requestedTopic = topic.value
    if (append) {
      loadingMore.value = true
      pageError.value = ''
    } else {
      loading.value = true
      error.value = ''
    }
    try {
      const { data } = await listNotices(requestedTopic, { page: targetPage, size })
      if (!isCurrentRequest(loadRequestTracker, token, viewScope)) return
      const nextItems = Array.isArray(data) ? data : []
      hasNext.value = nextItems.length >= size
      if (append && nextItems.length === 0) return
      page.value = targetPage
      items.value = append ? [...items.value, ...nextItems] : nextItems
    } catch (e) {
      if (!isCurrentRequest(loadRequestTracker, token, viewScope)) return
      if (append) pageError.value = e?.message || '加载更多失败'
      else error.value = e?.message || '加载通知失败'
    } finally {
      if (isCurrentRequest(loadRequestTracker, token, viewScope)) {
        loading.value = false
        loadingMore.value = false
      }
    }
  }

  async function reload() {
    pageError.value = ''
    await load(false, 0)
  }

  async function loadMore() {
    if (loading.value || loadingMore.value || !hasNext.value) return
    await load(true, page.value + 1)
  }

  async function markAllRead() {
    if (loading.value || markingRead.value || !hasUnread.value) return
    const token = markReadRequestTracker.begin()
    const viewScope = currentViewScope()
    error.value = ''
    markingRead.value = true
    try {
      const ids = normalizeOpaqueIds(items.value.filter((n) => !isNoticeRead(n)).map((x) => x?.id))
      await markRead(ids)
      if (!isCurrentRequest(markReadRequestTracker, token, viewScope)) return
      const readIds = new Set(ids)
      items.value = items.value.map((n) => (readIds.has(normalizeOpaqueId(n?.id)) ? { ...n, status: 1 } : n))
      // 已读操作后刷新壳层未读角标（不依赖轮询）。
      void inboxUnread.refresh()
    } catch (e) {
      if (!isCurrentRequest(markReadRequestTracker, token, viewScope)) return
      error.value = e?.message || '标记已读失败'
    } finally {
      if (isCurrentRequest(markReadRequestTracker, token, viewScope)) {
        markingRead.value = false
      }
    }
  }

  function resetForViewScope() {
    loadRequestTracker.invalidate()
    markReadRequestTracker.invalidate()
    page.value = 0
    hasNext.value = true
    loading.value = false
    loadingMore.value = false
    markingRead.value = false
    error.value = ''
    pageError.value = ''
    items.value = []
    if (auth.authed && topic.value) load(false, 0)
  }

  watch(currentViewScope, resetForViewScope)
  onMounted(() => {
    if (auth.authed && topic.value) load(false, 0)
  })
  onBeforeUnmount(() => {
    loadRequestTracker.invalidate()
    markReadRequestTracker.invalidate()
  })

  return {
    cards,
    error,
    hasNext,
    hasUnread,
    loading,
    loadingMore,
    loadMore,
    markAllRead,
    markingRead,
    pageError,
    policy,
    reload
  }
}
