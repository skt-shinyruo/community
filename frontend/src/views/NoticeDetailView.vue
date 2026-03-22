<!-- 通知详情页：按 topic 展示通知列表，并支持标记已读。 -->
<template>
  <div class="page notice-detail-page">
    <UiCard flat class="notice-detail-hero">
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
          <UiButton variant="secondary" @click="load" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
          <RouterLink class="btn ghost" to="/notices">返回通知汇总</RouterLink>
        </template>
      </UiPageHeader>

      <div class="notice-detail-hero-grid">
        <div class="notice-detail-hero-card">
          <span class="notice-detail-hero-label">本页通知</span>
          <strong>{{ items.length }}</strong>
          <p>保留同一主题里的阅读顺序和状态，方便你按上下文逐条处理。</p>
        </div>
        <div class="notice-detail-hero-card">
          <span class="notice-detail-hero-label">当前页码</span>
          <strong>{{ page + 1 }}</strong>
          <p>翻页时会继续沿用当前主题，不需要在不同消息类型之间频繁切换。</p>
        </div>
      </div>
    </UiCard>

    <div v-if="error && items.length > 0" class="error notice-detail-banner">{{ error }}</div>

    <UiCard class="notice-detail-shell">
      <div class="notice-detail-shell-head">
        <div>
          <div class="notice-detail-eyebrow">Threaded Notices</div>
          <h2>按时间阅读</h2>
          <p>每条通知都保留自己的时间和状态，并提供返回帖子或原始内容的入口。</p>
        </div>
      </div>

      <div class="notice-detail-toolbar">
        <UiPagination :page="page" :has-next="hasNext" @prev="prevPage" @next="nextPage" />
      </div>

      <UiEmpty v-if="error && items.length === 0" type="error" class="notice-detail-state">{{ error }}</UiEmpty>
      <div v-else-if="loading && items.length === 0" class="muted notice-detail-state">正在加载通知…</div>
      <UiEmpty v-else-if="items.length === 0" class="notice-detail-state">
        暂无通知
        <template #description>这一类通知当前没有更多记录，稍后可以刷新再看。</template>
      </UiEmpty>

      <div v-else class="notice-feed">
        <article v-for="n in items" :key="n.id" class="notice-card" :class="{ unread: Number(n?.status || 0) !== 1 }">
          <div class="notice-card-head">
            <div class="notice-card-copy">
              <div class="notice-card-eyebrow">
                {{
                  safeJsonParse(n?.content, null)?.type === 'COMMENT_CREATED'
                    ? '评论动态'
                    : safeJsonParse(n?.content, null)?.type === 'LIKE_CREATED'
                      ? '点赞动态'
                      : safeJsonParse(n?.content, null)?.type === 'FOLLOW_CREATED'
                        ? '关注动态'
                        : safeJsonParse(n?.content, null)?.type === 'MODERATION_ACTION_APPLIED'
                          ? '治理动态'
                          : '通知'
                }}
              </div>
              <h3 class="notice-card-title">
                {{
                  safeJsonParse(n?.content, null)?.type === 'COMMENT_CREATED'
                    ? '有人回复了你的内容'
                    : safeJsonParse(n?.content, null)?.type === 'LIKE_CREATED'
                      ? '你的内容收到了新的点赞'
                      : safeJsonParse(n?.content, null)?.type === 'FOLLOW_CREATED'
                        ? '你收到了新的关注'
                        : safeJsonParse(n?.content, null)?.type === 'MODERATION_ACTION_APPLIED'
                          ? '治理状态有更新'
                          : '查看这条通知'
                }}
              </h3>
            </div>
            <div class="notice-card-time">{{ formatTime(n.createTime) }}</div>
          </div>

          <p class="notice-card-body">
            {{
              safeJsonParse(n?.content, null)?.type === 'COMMENT_CREATED'
                ? '有人在帖子或评论线程里与你互动，可以返回原帖继续阅读上下文。'
                : safeJsonParse(n?.content, null)?.type === 'LIKE_CREATED'
                  ? '这说明你的内容正在被更多人看见，也适合回到原帖继续跟进讨论。'
                  : safeJsonParse(n?.content, null)?.type === 'FOLLOW_CREATED'
                    ? '新的关注通常意味着有人开始留意你的公开发言和动态。'
                    : safeJsonParse(n?.content, null)?.type === 'MODERATION_ACTION_APPLIED'
                      ? '如果这条通知涉及帖子或内容治理，建议回到相关页面查看更完整的结果。'
                      : formatNotice(n)
            }}
          </p>

          <div class="notice-card-meta">
            <span class="notice-state-pill" :class="{ unread: Number(n?.status || 0) !== 1 }">
              {{ Number(n?.status || 0) === 1 ? '已读' : '未读' }}
            </span>
            <span v-if="safeJsonParse(n?.content, null)?.payload?.actorUserId">成员 #{{ safeJsonParse(n?.content, null)?.payload?.actorUserId }}</span>
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
import { computed, onMounted, ref } from 'vue'
import { listNotices, markRead } from '../api/services/noticeService'
import { safeJsonParse } from '../utils/safeJson'
import { formatTime } from '../utils/time'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'

const emit = defineEmits(['trace'])
const props = defineProps({ topic: String })

const topic = computed(() => String(props.topic || ''))
const page = ref(0)
const size = ref(10)

const loading = ref(false)
const error = ref('')
const items = ref([])

const hasNext = computed(() => items.value.length === Number(size.value || 10))

function formatNotice(msg) {
  const raw = safeJsonParse(msg?.content, null)
  const type = raw?.type || ''
  const payload = raw?.payload || {}
  if (type === 'COMMENT_CREATED') {
    return payload?.postId ? `有人在帖子 ${payload.postId} 下回复了你，建议回到原帖继续阅读上下文。` : '有人回复了你的内容。'
  }
  if (type === 'LIKE_CREATED') {
    return '有人对你的内容表达了认可，可以回到原帖看看这次互动发生在什么位置。'
  }
  if (type === 'FOLLOW_CREATED') {
    return '你收到了新的关注，对方开始留意你的公开动态。'
  }
  if (type === 'MODERATION_ACTION_APPLIED') {
    const action = payload?.action ?? '-'
    const reason = payload?.reason ?? ''
    const duration = payload?.durationSeconds
    const extra = duration ? ` duration=${duration}s` : ''
    const targetType = payload?.targetType ?? '-'
    const targetId = payload?.targetId ?? '-'
    return `治理结果已更新：动作=${action}${extra ? ` · ${extra.trim()}` : ''} · 目标=${targetType}/${targetId}${reason ? ` · 原因=${reason}` : ''}`
  }
  return `通知：${type || 'unknown'}`
}

function noticePostId(msg) {
  const raw = safeJsonParse(msg?.content, null)
  const type = raw?.type || ''
  const payload = raw?.payload || {}
  const pid = payload?.postId
  if (pid) return Number(pid)
  if (type === 'MODERATION_ACTION_APPLIED' && Number(payload?.targetType || 0) === 1) {
    return Number(payload?.targetId || 0)
  }
  return 0
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    const { data, traceId } = await listNotices(topic.value, { page: page.value, size: size.value })
    items.value = data
    emit('trace', traceId || '')
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function markAllRead() {
  if (items.value.length === 0) return
  error.value = ''
  loading.value = true
  try {
    const ids = items.value.map((x) => x?.id).filter((x) => typeof x === 'number' && x > 0)
    const { traceId } = await markRead(ids)
    emit('trace', traceId || '')
    await load()
  } catch (e) {
    error.value = e?.message || '标记已读失败'
  } finally {
    loading.value = false
  }
}

async function nextPage() {
  if (!hasNext.value) return
  page.value += 1
  await load()
}

async function prevPage() {
  page.value = Math.max(0, page.value - 1)
  await load()
}

onMounted(load)
</script>

<style scoped>
.notice-detail-page {
  max-width: 980px;
  margin: 0 auto;
  gap: var(--space-5);
}

.notice-detail-hero {
  display: grid;
  gap: var(--space-4);
}

.notice-detail-hero-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.notice-detail-hero-card {
  padding: 18px 20px;
  border-radius: var(--radius-lg);
  border: 1px solid color-mix(in srgb, var(--border) 84%, var(--accent) 16%);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--surface) 92%, white 8%), var(--surface));
  display: grid;
  gap: 6px;
}

.notice-detail-hero-card strong {
  font-size: clamp(1.75rem, 3vw, 2.3rem);
  line-height: 1;
}

.notice-detail-hero-card p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.55;
}

.notice-detail-hero-label,
.notice-detail-eyebrow,
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

.notice-detail-shell-head h2 {
  margin: 6px 0 4px;
  font-size: 1.15rem;
}

.notice-detail-shell-head p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.55;
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
  .notice-detail-hero-grid {
    grid-template-columns: 1fr;
  }

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
