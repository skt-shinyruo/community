<!-- 通知详情页：按 topic 展示通知列表，并支持标记已读。 -->
<template>
  <div class="page notice-detail-page">
    <div v-if="error && items.length > 0" class="error notice-detail-banner">{{ error }}</div>

    <UiCard class="notice-detail-shell">
      <div class="notice-detail-shell-head">
        <UiPageHeader>
          <template #title>
            {{
              topic === 'comment'
                ? '评论通知'
                : topic === 'like'
                  ? '点赞通知'
                  : topic === 'follow'
                    ? '关注通知'
                    : topic === 'moderation'
                      ? '治理通知'
                      : '通知详情'
            }}
          </template>
          <template #subtitle>
            {{
              topic === 'comment'
                ? '回到需要你继续阅读或回复的评论线程。'
                : topic === 'like'
                  ? '集中查看哪些内容最近收到了新的认可。'
                  : topic === 'follow'
                    ? '查看最近新增的关注与社交变化。'
                    : topic === 'moderation'
                      ? '查看治理动作和处理结果的最新更新。'
                      : '查看这一类通知的详细记录。'
            }}
          </template>
          <template #actions>
            <UiButton variant="secondary" @click="markAllRead" :disabled="loading || items.length === 0">标记本页已读</UiButton>
            <UiButton variant="secondary" @click="refresh" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
            <RouterLink class="btn ghost" to="/notices">返回通知汇总</RouterLink>
          </template>
        </UiPageHeader>
      </div>

      <div class="notice-detail-toolbar">
        <UiPagination :page="page" :has-next="hasNext" :disabled="loading" @prev="prevPage" @next="nextPage" />
      </div>

      <UiState v-if="error && items.length === 0" variant="error" class="notice-detail-state">{{ error }}</UiState>
      <div v-else-if="loading && items.length === 0" class="muted notice-detail-state">正在加载通知…</div>
      <UiState v-else-if="items.length === 0" class="notice-detail-state">
        暂无通知
        <template #description>这一类通知当前没有更多记录，稍后可以刷新再看。</template>
      </UiState>

      <div v-else class="notice-feed">
        <article v-for="n in items" :key="n.id" class="notice-card" :class="{ unread: Number(n?.status || 0) !== 1 }">
          <div class="notice-card-head">
            <div class="notice-card-copy">
              <div class="notice-card-eyebrow">{{ noticeEyebrow(n) }}</div>
              <h3 class="notice-card-title">{{ noticeTitle(n) }}</h3>
            </div>
            <div class="notice-card-time">{{ formatTime(n.createTime) }}</div>
          </div>

          <p class="notice-card-body">{{ noticeBody(n) }}</p>

          <div class="notice-card-meta">
            <span class="notice-state-pill" :class="{ unread: Number(n?.status || 0) !== 1 }">
              {{ Number(n?.status || 0) === 1 ? '已读' : '未读' }}
            </span>
            <span v-if="noticeActorId(n)">{{ shortMemberLabel(noticeActorId(n)) }}</span>
            <span v-if="noticePostId(n)">可返回帖子查看上下文</span>
          </div>

          <div class="notice-card-actions" v-if="noticePostId(n)">
            <RouterLink class="btn secondary" :to="`/posts/${noticePostId(n)}`">查看相关帖子</RouterLink>
          </div>
        </article>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { listNotices, markRead } from '../api/services/noticeService'
import { safeJsonParse } from '../utils/safeJson'
import { formatTime } from '../utils/time'
import { normalizeOpaqueId, normalizeOpaqueIds } from '../utils/opaqueId'
import { createLatestRequestTracker } from '../utils/latestRequest'
import { identityScope } from '../stores/identityScope'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiState from '../components/ui/UiState.vue'

const props = defineProps({ topic: String })
const auth = useAuthStore()

const topic = computed(() => String(props.topic || ''))
const page = ref(0)
const size = ref(10)

const loading = ref(false)
const error = ref('')
const items = ref([])

const hasNext = ref(true)
const loadRequestTracker = createLatestRequestTracker({
  getScope: () => `${identityScope(auth)}:${topic.value}`
})
const markReadRequestTracker = createLatestRequestTracker({
  getScope: () => `${identityScope(auth)}:${topic.value}`
})

const NOTICE_KINDS = {
  COMMENT_CREATED: {
    eyebrow: '评论动态',
    title: '有人回复了你的内容',
    body: '有人在帖子或评论线程里与你互动，可以返回原帖继续阅读上下文。'
  },
  LIKE_CREATED: {
    eyebrow: '点赞动态',
    title: '你的内容收到了新的点赞',
    body: '这说明你的内容正在被更多人看见，也适合回到原帖继续跟进讨论。'
  },
  FOLLOW_CREATED: {
    eyebrow: '关注动态',
    title: '你收到了新的关注',
    body: '新的关注通常意味着有人开始留意你的公开发言和动态。'
  },
  MODERATION_ACTION_APPLIED: {
    eyebrow: '治理动态',
    title: '治理状态有更新',
    body: '如果这条通知涉及帖子或内容治理，建议回到相关页面查看更完整的结果。'
  }
}

function noticeKind(msg) {
  return NOTICE_KINDS[safeJsonParse(msg?.content, null)?.type] || null
}

function noticeEyebrow(n) {
  return noticeKind(n)?.eyebrow || '通知'
}

function noticeTitle(n) {
  return noticeKind(n)?.title || '查看这条通知'
}

function noticeBody(n) {
  const kind = noticeKind(n)
  if (kind) return kind.body
  const type = safeJsonParse(n?.content, null)?.type || ''
  return `通知：${type || 'unknown'}`
}

function noticeActorId(n) {
  return safeJsonParse(n?.content, null)?.payload?.actorUserId || ''
}

function noticePostId(msg) {
  const raw = safeJsonParse(msg?.content, null)
  const type = raw?.type || ''
  const payload = raw?.payload || {}
  const pid = normalizeOpaqueId(payload?.postId)
  if (pid) return pid
  if (type === 'MODERATION_ACTION_APPLIED' && Number(payload?.targetType || 0) === 1) {
    return normalizeOpaqueId(payload?.targetId)
  }
  return ''
}

function shortMemberLabel(value) {
  const raw = String(value || '').trim()
  if (!raw) return '社区成员'
  return `社区成员 ${raw.slice(0, 8)}`
}

async function load(targetPage = page.value) {
  const token = loadRequestTracker.begin()
  const requestedTopic = topic.value
  error.value = ''
  loading.value = true
  try {
    const { data } = await listNotices(requestedTopic, { page: targetPage, size: size.value })
    if (!loadRequestTracker.isCurrent(token)) return
    const nextItems = Array.isArray(data) ? data : []
    hasNext.value = nextItems.length >= Number(size.value || 10)
    if (targetPage > page.value && nextItems.length === 0) {
      return
    }
    page.value = targetPage
    items.value = nextItems
  } catch (e) {
    if (!loadRequestTracker.isCurrent(token)) return
    error.value = e?.message || '加载失败'
  } finally {
    if (loadRequestTracker.isCurrent(token)) {
      loading.value = false
    }
  }
}

async function markAllRead() {
  if (loading.value || items.value.length === 0) return
  const token = markReadRequestTracker.begin()
  const requestedPage = page.value
  error.value = ''
  loading.value = true
  try {
    const ids = normalizeOpaqueIds(items.value.map((x) => x?.id))
    await markRead(ids)
    if (!markReadRequestTracker.isCurrent(token)) return
    await load(requestedPage)
  } catch (e) {
    if (!markReadRequestTracker.isCurrent(token)) return
    error.value = e?.message || '标记已读失败'
  } finally {
    if (markReadRequestTracker.isCurrent(token)) {
      loading.value = false
    }
  }
}

async function nextPage() {
  if (loading.value || !hasNext.value) return
  await load(page.value + 1)
}

async function prevPage() {
  if (loading.value) return
  await load(Math.max(0, page.value - 1))
}

async function refresh() {
  await load(page.value)
}

function resetForViewScope() {
  loadRequestTracker.invalidate()
  markReadRequestTracker.invalidate()
  page.value = 0
  hasNext.value = true
  loading.value = false
  error.value = ''
  items.value = []
  if (auth.authed && topic.value) load(0)
}

watch(() => `${identityScope(auth)}:${topic.value}`, resetForViewScope)
onMounted(() => {
  if (auth.authed && topic.value) load(0)
})
onBeforeUnmount(() => {
  loadRequestTracker.invalidate()
  markReadRequestTracker.invalidate()
})
</script>

<style scoped>
.notice-detail-page {
  max-width: 980px;
  margin: 0 auto;
  gap: var(--space-5);
}

.notice-card-eyebrow {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 700;
}

.notice-detail-banner {
  margin-top: -6px;
}

.notice-detail-shell {
  padding: 0;
  overflow: hidden;
}

.notice-detail-shell-head {
  padding: 22px 24px 12px;
}

.notice-detail-shell-head :deep(.page-header) {
  gap: 0;
}

.notice-detail-shell-head :deep(.page-header-subtitle) {
  margin: 4px 0 0;
}

.notice-detail-toolbar {
  padding: 0 24px 18px;
  border-bottom: 1px solid var(--border);
}

.notice-detail-state {
  padding: 48px 24px;
}

.notice-feed {
  display: grid;
}

.notice-card {
  padding: 22px 24px;
  border-bottom: 1px solid var(--border);
  display: grid;
  gap: 14px;
}

.notice-card:last-child {
  border-bottom: none;
}

.notice-card.unread {
  background: color-mix(in srgb, var(--surface) 90%, var(--accent-weak) 10%);
}

.notice-card-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.notice-card-copy {
  display: grid;
  gap: 6px;
}

.notice-card-title {
  margin: 0;
  font-size: 1.05rem;
  line-height: 1.35;
}

.notice-card-time {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}

.notice-card-body {
  margin: 0;
  color: var(--text-2);
  line-height: 1.7;
}

.notice-card-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--text-3);
}

.notice-state-pill {
  border-radius: 999px;
  padding: 4px 9px;
  background: color-mix(in srgb, var(--surface) 82%, var(--bg) 18%);
  color: var(--text-2);
  font-weight: 700;
}

.notice-state-pill.unread {
  background: color-mix(in srgb, var(--accent) 18%, white 82%);
  color: var(--accent);
}

.notice-card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

@media (max-width: 768px) {
  .notice-detail-shell-head,
  .notice-detail-toolbar,
  .notice-card {
    padding-left: 18px;
    padding-right: 18px;
  }

  .notice-card-head {
    flex-direction: column;
  }
}
</style>
