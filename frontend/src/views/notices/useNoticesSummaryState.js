// 通知汇总页状态：按主题聚合的收件箱列表，按身份 scope 隔离，丢弃过期响应。

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { identityScope } from '../../stores/identityScope'
import { topicSummary } from '../../api/services/noticeService'
import { createLatestRequestTracker } from '../../utils/latestRequest'

const TOPIC_PRESENTATION = Object.freeze({
  like: { title: '点赞', description: '有人对你的内容表达了认可。' },
  comment: { title: '评论', description: '有人回复了你的帖子或评论。' },
  follow: { title: '关注', description: '有人开始关注你的公开动态。' },
  moderation: { title: '治理', description: '治理状态或处理结果发生了更新。' }
})

export function noticeTopicPresentation(topic) {
  const key = String(topic || '')
  return TOPIC_PRESENTATION[key] || { title: key || '其他', description: '查看这一类通知的最新动态。' }
}

export function noticeUnreadCount(item) {
  const n = Number(item?.unreadCount || 0)
  return Number.isFinite(n) && n > 0 ? n : 0
}

export function useNoticesSummaryState() {
  const auth = useAuthStore()
  const loading = ref(false)
  const error = ref('')
  const items = ref([])
  const loadRequestTracker = createLatestRequestTracker({ getScope: () => identityScope(auth) })

  const pendingTopicCount = computed(() => items.value.filter((it) => noticeUnreadCount(it) > 0).length)

  async function load() {
    const token = loadRequestTracker.begin()
    error.value = ''
    loading.value = true
    try {
      const { data } = await topicSummary()
      if (!loadRequestTracker.isCurrent(token)) return
      items.value = Array.isArray(data) ? data : []
    } catch (e) {
      if (!loadRequestTracker.isCurrent(token)) return
      error.value = e?.message || '加载通知失败'
    } finally {
      if (loadRequestTracker.isCurrent(token)) {
        loading.value = false
      }
    }
  }

  async function reload() {
    await load()
  }

  function resetForIdentity() {
    loadRequestTracker.invalidate()
    loading.value = false
    error.value = ''
    items.value = []
    if (auth.authed) load()
  }

  watch(() => identityScope(auth), resetForIdentity)
  onMounted(() => {
    if (auth.authed) load()
  })
  onBeforeUnmount(() => loadRequestTracker.invalidate())

  return {
    items,
    loading,
    error,
    pendingTopicCount,
    reload
  }
}
