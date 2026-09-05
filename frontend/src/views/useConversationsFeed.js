// 私信会话列表的页面流程：游标追加分页、会话 scope 竞态丢弃和壳层未读角标同步。
// 首载 / 刷新成功后触发 inboxUnread.refresh()，让侧边栏与移动端私信入口角标
// 和用户刚看到的列表已读语义收敛到同一份服务端事实；角标刷新走静默后台语义。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { listImConversationPage } from '../api/services/imCoreChatService'
import { useAuthStore } from '../stores/auth'
import { useInboxUnreadStore } from '../stores/inboxUnread'
import { identityScope } from '../stores/identityScope'
import { createLatestRequestTracker } from '../utils/latestRequest'
import { mergeConversations } from './conversationDetailState'

export function useConversationsFeed() {
  const auth = useAuthStore()
  const inboxUnread = useInboxUnreadStore()

  const items = ref([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const error = ref('')
  const pageError = ref('')
  const nextCursor = ref('')
  const hasMore = ref(false)
  const requestTracker = createLatestRequestTracker({ getScope: () => identityScope(auth) })

  const pendingCount = computed(
    () => items.value.filter((c) => Number(c?.unreadCount || 0) > 0).length
  )

  function applyPage(page, { append = false } = {}) {
    const incoming = Array.isArray(page?.items) ? page.items : []
    items.value = mergeConversations(append ? items.value : [], incoming)
    nextCursor.value = String(page?.nextCursor || '')
    hasMore.value = Boolean(page?.hasMore && nextCursor.value)
  }

  async function reload() {
    const requestHandle = requestTracker.begin()
    error.value = ''
    pageError.value = ''
    loadingMore.value = false
    loading.value = true
    try {
      const page = await listImConversationPage({ cursor: '', size: 20 })
      if (!requestTracker.isCurrent(requestHandle)) return
      applyPage(page)
      void inboxUnread.refresh()
    } catch (e) {
      if (!requestTracker.isCurrent(requestHandle)) return
      error.value = e?.message || '加载会话失败'
    } finally {
      if (requestTracker.isCurrent(requestHandle)) {
        loading.value = false
      }
    }
  }

  async function loadMore() {
    if (loading.value || loadingMore.value || !hasMore.value || !nextCursor.value) return

    const requestHandle = requestTracker.begin()
    const cursor = nextCursor.value
    loadingMore.value = true
    pageError.value = ''
    try {
      const page = await listImConversationPage({ cursor, size: 20 })
      if (!requestTracker.isCurrent(requestHandle)) return
      applyPage(page, { append: true })
    } catch (e) {
      if (!requestTracker.isCurrent(requestHandle)) return
      pageError.value = e?.message || '加载会话失败'
    } finally {
      if (requestTracker.isCurrent(requestHandle)) {
        loadingMore.value = false
      }
    }
  }

  function resetForIdentity() {
    requestTracker.invalidate()
    loading.value = false
    loadingMore.value = false
    error.value = ''
    pageError.value = ''
    items.value = []
    nextCursor.value = ''
    hasMore.value = false
    if (auth.authed) void reload()
  }

  watch(() => identityScope(auth), resetForIdentity)
  onMounted(() => {
    if (auth.authed) void reload()
  })
  onBeforeUnmount(() => {
    requestTracker.invalidate()
  })

  return {
    items,
    loading,
    loadingMore,
    error,
    pageError,
    hasMore,
    pendingCount,
    reload,
    loadMore
  }
}
