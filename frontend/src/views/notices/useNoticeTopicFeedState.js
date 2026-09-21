// 通知主题详情页状态：按 topic 的追加式消费流、主题全量标记已读与壳层未读角标联动。
// 身份或 topic 变化时重置并丢弃过期响应；标记已读把当前主题（含未加载页）的未读全部清零，
// 可用性以服务端主题未读数为准（计数失败时回退到已加载项判定），成功后本地翻转已加载项
// 为已读（结果立即可见，走静默更新），并触发 inboxUnread 刷新，不引入轮询。

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { useInboxUnreadStore } from '../../stores/inboxUnread'
import { identityScope } from '../../stores/identityScope'
import { listNotices, markTopicRead, unreadCount } from '../../api/services/noticeService'
import { safeJsonParse } from '../../utils/safeJson'
import { mergeAppendedById } from '../../utils/mergeById'
import { normalizeOpaqueId } from '../../utils/opaqueId'
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
  CommentCreated: {
    title: '有人回复了你的内容',
    body: '有人在帖子或评论线程里与你互动，可以返回原帖继续阅读上下文。'
  },
  LikeCreated: {
    title: '你的内容收到了新的点赞',
    body: '这说明你的内容正在被更多人看见，也适合回到原帖继续跟进讨论。'
  },
  FollowCreated: {
    title: '你收到了新的关注',
    body: '新的关注通常意味着有人开始留意你的公开发言和动态。'
  },
  ModerationActionApplied: {
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
  return { title: '查看这条通知', body: '这条通知的详细内容暂时无法展示，稍后可以再回来看看。' }
}

export function noticePostId(notice) {
  const raw = safeJsonParse(notice?.content, null)
  const type = String(raw?.type || '')
  const payload = raw?.payload || {}
  const pid = normalizeOpaqueId(payload?.postId)
  if (pid) return pid
  if (type === 'ModerationActionApplied' && Number(payload?.targetType || 0) === 1) {
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
  const items = ref(/** @type {Array<Record<string, unknown>>} */ ([]))
  const topicUnread = ref(0)

  const loadRequestTracker = createLatestRequestTracker({
    getScope: () => `${identityScope(auth)}:${topic.value}`
  })
  const markReadRequestTracker = createLatestRequestTracker({
    getScope: () => `${identityScope(auth)}:${topic.value}`
  })

  const policy = computed(() => TOPIC_POLICY[topic.value] || FALLBACK_TOPIC_POLICY)
  const hasUnread = computed(() => topicUnread.value > 0 || items.value.some((n) => !isNoticeRead(n)))

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

  async function load(append = false, targetPage = page.value) {
    if (!auth.authed || !topic.value) return
    const token = loadRequestTracker.begin()
    const requestedTopic = topic.value
    if (append) {
      loadingMore.value = true
      pageError.value = ''
    } else {
      loading.value = true
      error.value = ''
    }
    try {
      /** @type {Array<Promise<{ data: unknown[], traceId: string }> | Promise<{ data: number, traceId: string } | null>>} */
      const requests = [listNotices(requestedTopic, { page: targetPage, size })]
      if (!append) requests.push(unreadCount(requestedTopic).catch(() => null))
      const [listResult, unreadResult] = /** @type {[
        { data: unknown[], traceId: string },
        { data: number, traceId: string } | null
      ]} */ (await Promise.all(requests))
      if (!loadRequestTracker.isCurrent(token)) return
      if (unreadResult) topicUnread.value = unreadResult.data
      const nextItems = /** @type {Array<Record<string, unknown>>} */ (Array.isArray(listResult.data) ? listResult.data : [])
      hasNext.value = nextItems.length >= size
      if (append && nextItems.length === 0) return
      page.value = targetPage
      items.value = append ? mergeAppendedById(items.value, nextItems) : nextItems
    } catch (e) {
      if (!loadRequestTracker.isCurrent(token)) return
      if (append) pageError.value = e?.message || '加载更多失败'
      else error.value = e?.message || '加载通知失败'
    } finally {
      if (loadRequestTracker.isCurrent(token)) {
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
    error.value = ''
    markingRead.value = true
    try {
      await markTopicRead(topic.value)
      if (!markReadRequestTracker.isCurrent(token)) return
      topicUnread.value = 0
      items.value = items.value.map((n) => (isNoticeRead(n) ? n : { ...n, status: 1 }))
      // 已读操作后刷新壳层未读角标（不依赖轮询）。
      void inboxUnread.refresh()
    } catch (e) {
      if (!markReadRequestTracker.isCurrent(token)) return
      error.value = e?.message || '标记已读失败'
    } finally {
      if (markReadRequestTracker.isCurrent(token)) {
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
    topicUnread.value = 0
    if (auth.authed && topic.value) load(false, 0)
  }

  watch(() => `${identityScope(auth)}:${topic.value}`, resetForViewScope)
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
